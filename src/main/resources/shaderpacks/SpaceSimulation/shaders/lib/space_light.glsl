#include "/lib/space_settings.glsl"

/*
 * SpaceSimulation 内建光影包 —— 世界中心太阳的径向光照与阴影。
 *
 * 太阳固定在世界原点 (0,0,0)，光向外辐射。渲染上采用「局部平行光近似」：
 * 玩家到太阳的距离（数十万~数百万格）远大于光照/阴影的作用半径（几十~几百格），
 * 因此局部范围内光线方向的变化量远小于一个像素，受光方向直接取 normalize(sunPosition)。
 *
 * 但太阳**不是一个点光源**：它的半径有 20~50 万格，在小行星带看过去张角可达 6°~30°。
 * 这个角半径（SUN_ANGULAR_SIN）决定了两件事：
 *   ① 明暗交界是柔和的（正对太阳时侧面不会全黑，背光面也有一点过渡）；
 *   ② 阴影边缘有半影（PCSS），半影宽度随遮挡物离接收面的距离增大。
 *
 * 坐标空间：光照在 view space 下计算（与 Iris 的 sunPosition 空间一致），
 * 因此传入的法线也必须是 view space（gl_NormalMatrix * gl_Normal）。
 * 阴影采样在 player space（相机相对世界坐标）下进行，与 Iris 官方写法
 * shadowModelView * vec4(feetPlayerPos, 1.0) 一致。
 *
 * sunPosition 由模组 mixin（CelestialUniformsMixin）接管为「从相机指向世界中心」
 * 的方向 —— 纯方向、不含绝对坐标，因此在百万格坐标下没有浮点精度问题。
 */

uniform vec3 sunPosition;      // 模组接管：view space 的「指向太阳」方向 × 100
uniform float spaceSunDistance; // 模组注入：相机到世界中心的距离（格），距离衰减用
uniform sampler2D shadowtex0;  // Iris shadow pass 写入的深度图
uniform mat4 shadowModelView;  // 模组接管：径向光方向的阴影视图矩阵
uniform mat4 shadowProjection;
uniform mat4 gbufferModelViewInverse; // view space → player space（相机相对世界坐标）

const vec3  SUN_COLOR   = vec3(1.00, 0.96, 0.90);
const float SHADOW_BIAS = 0.0012; // 消除自阴影（shadow acne）

/** 从片元指向世界中心太阳的单位方向（view space）。 */
vec3 spaceToSun() {
    vec3 d = sunPosition;
    float l = length(d);
    return l > 1.0E-6 ? d / l : vec3(0.0, 1.0, 0.0);
}

/**
 * view space 位置 → player space（相机相对世界坐标），阴影采样用。
 *
 * 在片元着色器里做这一步是有意的：视空间→player 空间是线性变换，插值后再变换与
 * 逐顶点变换等价；而把该变换放在 fsh 可以让 gbuffers 的顶点着色器**完全不依赖**
 * Iris 注入的 uniform（Iris 注入的 uniform 需显式声明，vsh 里手写声明曾出现
 * compile error C1503，fsh 走本 lib 的声明则一直正常）。
 */
vec3 spacePlayerPos(vec3 viewPos) {
    return (gbufferModelViewInverse * vec4(viewPos, 1.0)).xyz;
}

/**
 * 相机到世界中心（太阳）的距离，单位格。
 *
 * 来自模组 mixin 注册的专用 uniform {@code spaceSunDistance}（见 CelestialUniformsMixin）：
 * 距离在 Java 侧用 double 算好后每帧直接注入。不复用 Iris 的 {@code cameraPosition}
 * （该值会以 30000 格为周期折回，对它取 length 得到的是完全错误的距离）。
 *
 * 相机与片元的距离差（几十~几百格）相对到太阳的数十万~数百万格完全可以忽略。
 * 若该 uniform 未被注册（例如换用了其它光影包），其值为 0，衰减会自动退化为「无衰减」。
 */
float spaceDistanceToSun() {
    return spaceSunDistance;
}

/**
 * 距离衰减系数：距离越远、亮度越低。
 *
 * 以 SUN_DISTANCE_REFERENCE 为基准（该处系数为 1），按 SUN_DISTANCE_FALLOFF 次幂下降：
 * 指数 1.0 = 反比例（1/d，本包默认），2.0 = 平方反比。基准距离以内钳到 1，避免靠近太阳过曝；
 * 指数设为 0 可完全关闭衰减。
 */
float spaceDistanceAttenuation() {
    if (SUN_DISTANCE_FALLOFF <= 0.0) {
        return 1.0;
    }
    float d = spaceDistanceToSun();
    float ratio = clamp(SUN_DISTANCE_REFERENCE / max(d, 1.0), 0.0, 1.0);
    return pow(ratio, SUN_DISTANCE_FALLOFF);
}

/** 16 点采样盘（8 + 8 双环），PCSS 的遮挡物搜索与半影滤波共用。 */
const vec2 SPACE_DISK[16] = vec2[16](
    vec2( 0.0000,  1.0000), vec2( 0.7071,  0.7071), vec2( 1.0000,  0.0000), vec2( 0.7071, -0.7071),
    vec2( 0.0000, -1.0000), vec2(-0.7071, -0.7071), vec2(-1.0000,  0.0000), vec2(-0.7071,  0.7071),
    vec2( 0.3827,  0.9239), vec2( 0.9239,  0.3827), vec2( 0.9239, -0.3827), vec2( 0.3827, -0.9239),
    vec2(-0.3827, -0.9239), vec2(-0.9239, -0.3827), vec2(-0.9239,  0.3827), vec2(-0.3827,  0.9239)
);

/**
 * 采样盘旋转抖动（dither）：用屏幕像素坐标生成一个稳定的伪随机角度，
 * 把规则的双环采样图案打散成噪声，从而消除阴影边缘的锯齿状台阶（aliasing）。
 * 用屏幕坐标而不是阴影 UV 或时间，是为了让噪声在静止画面下保持稳定、不闪烁。
 */
mat2 spaceDitherRotation() {
    float angle = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) * 6.28318530718;
    float c = cos(angle);
    float s = sin(angle);
    return mat2(c, s, -s, c); // 列主序，等价于 [[c, -s], [s, c]]
}

/**
 * 阴影采样（PCSS：Percentage-Closer Soft Shadows）。
 *
 * 太阳是球面光源，所以阴影边缘不是硬边而是半影：
 *   1) 在阴影图上搜索遮挡物，求其平均深度；
 *   2) 半影宽度 ∝ (接收面深度 − 遮挡物深度) × 光源角半径 —— 遮挡物离接收面越远，影子越糊；
 *   3) 以该宽度做 PCF 滤波。
 *
 * 深度换算：正交阴影投影下，UV 空间与世界距离成正比；令深度跨度与横向跨度同量级
 * （≈ 2×shadowDistance），则半影宽度可直接写成 `深度差 × SUN_ANGULAR_SIN`，
 * 无需引入 near/far 常量。
 *
 * @return 1.0 = 完全受光，0.0 = 完全处于本影
 */
float spaceShadow(vec3 feetPlayerPos) {
    vec3 shadowViewPos = (shadowModelView * vec4(feetPlayerPos, 1.0)).xyz;
    vec4 shadowClipPos = shadowProjection * vec4(shadowViewPos, 1.0);

    // 深度偏移放在裁剪空间，避免影响后面按 UV 采样的半影计算
    shadowClipPos.z -= SHADOW_BIAS * shadowClipPos.w;

    vec3 shadowNDCPos = shadowClipPos.xyz / shadowClipPos.w;
    vec3 shadowScreenPos = shadowNDCPos * 0.5 + 0.5;

    // 阴影图范围之外视为全亮（例如超出 shadowDistance 的远景）
    if (shadowScreenPos.x < 0.0 || shadowScreenPos.x > 1.0 ||
        shadowScreenPos.y < 0.0 || shadowScreenPos.y > 1.0 ||
        shadowScreenPos.z < 0.0 || shadowScreenPos.z > 1.0) {
        return 1.0;
    }

    float receiverZ = shadowScreenPos.z;
    vec2 uv = shadowScreenPos.xy;

#if SHADOW_QUALITY == 0
    // 阴影质量 0：硬边阴影，单次采样（最快）
    return step(receiverZ, texture(shadowtex0, uv).r);
#else
    // 每像素旋转采样盘：消除规则采样导致的阴影边缘锯齿
    mat2 ditherRot = spaceDitherRotation();

    // ---- 1) 遮挡物搜索 ----
    float blockerSum = 0.0;
    float blockerCount = 0.0;
    for (int i = 0; i < SPACE_BLOCKER_SAMPLES; i++) {
        float d = texture(shadowtex0, uv + ditherRot * (SPACE_DISK[i] * SPACE_BLOCKER_SEARCH_RADIUS)).r;
        if (d < receiverZ) {
            blockerSum += d;
            blockerCount += 1.0;
        }
    }
    if (blockerCount < 0.5) {
        return 1.0; // 邻域内没有遮挡物 → 完全受光
    }
    float blockerZ = blockerSum / blockerCount;

    // ---- 2) 半影宽度：光源角半径越大、遮挡物越远，阴影越柔和 ----
    float penumbra = (receiverZ - blockerZ) * SUN_ANGULAR_SIN;
    penumbra = clamp(penumbra, SPACE_PENUMBRA_MIN, SPACE_PENUMBRA_MAX);

    // ---- 3) 半影滤波 ----
    float sum = 0.0;
    for (int i = 0; i < SPACE_PCF_SAMPLES; i++) {
        sum += step(receiverZ, texture(shadowtex0, uv + ditherRot * (SPACE_DISK[i] * penumbra)).r);
    }
    return sum / float(SPACE_PCF_SAMPLES);
#endif
}

/**
 * 完整光照 = 环境项 + 太阳直射项 × 阴影。
 *
 * 太阳按**球面光源**处理：把硬边的 max(N·L, 0) 换成在 ±SUN_ANGULAR_SIN 区间内的
 * 平滑过渡，于是正对太阳时侧面保有一定亮度、背光面也不会死黑。
 *
 * @param albedo        表面反照率（已含顶点色/AO）
 * @param viewNormal    view space 法线（gl_NormalMatrix * gl_Normal）
 * @param feetPlayerPos 相机相对世界坐标（player space）
 */
vec3 spaceLighting(vec3 albedo, vec3 viewNormal, vec3 feetPlayerPos) {
#if SPACE_DEBUG_DISTANCE == 1
    // 调试：距离映射到灰度（0 ~ 2000 万格）。整屏全黑 = spaceSunDistance 读到 0
    return vec3(clamp(spaceDistanceToSun() / 20000000.0, 0.0, 1.0));
#elif SPACE_DEBUG_DISTANCE == 2
    // 调试：衰减系数映射到灰度（0 ~ 1）
    return vec3(spaceDistanceAttenuation());
#elif SPACE_DEBUG_DISTANCE == 3
    // 调试：太阳方向映射到 RGB。恒为浅绿 = sunPosition 未绑定（读到 0，回退 +Y）
    return spaceToSun() * 0.5 + 0.5;
#elif SPACE_DEBUG_DISTANCE == 4
    // 调试：sunPosition 的长度。恒黑 = 该 uniform 读到 0
    return vec3(clamp(length(sunPosition) / 200.0, 0.0, 1.0));
#elif SPACE_DEBUG_DISTANCE == 5
    // 调试：衰减参数自检。R = 基准距离/300万（档1=白, 档2=中灰, 档3/4 更暗），G = 衰减指数/3
    return vec3(clamp(SUN_DISTANCE_REFERENCE / 3000000.0, 0.0, 1.0),
                clamp(SUN_DISTANCE_FALLOFF / 3.0, 0.0, 1.0),
                0.0);
#endif

    vec3 L = spaceToSun();

    float nlen = length(viewNormal);
    float ndl;
    if (nlen > 1.0E-4) {
        float cosTheta = dot(viewNormal / nlen, L);
        // 球面光源：光源张角 ±sinA，漫反射在該区间内线性过渡
        ndl = clamp((cosTheta + SUN_ANGULAR_SIN) / (1.0 + SUN_ANGULAR_SIN), 0.0, 1.0);
    } else {
        // 部分几何（粒子、线框）不提供有效法线：按正对太阳处理，避免整片变黑
        ndl = 1.0;
    }

    float shadow = 1.0;
    if (ndl > 0.0) {
        shadow = spaceShadow(feetPlayerPos);
    }

    // 距离衰减只作用于太阳直射光。环境光代表星光/天体反照这类背景辐射，
    // 物理上不随到太阳的距离衰减；保持恒定也能避免远处完全死黑。
    float attenuation = spaceDistanceAttenuation();

    vec3 ambientColor = vec3(SPACE_AMBIENT_R, SPACE_AMBIENT_G, SPACE_AMBIENT_B);
    vec3 ambient = albedo * ambientColor * SPACE_AMBIENT;
    vec3 direct  = albedo * SUN_COLOR * SUN_INTENSITY * ndl * shadow * attenuation;
    return ambient + direct;
}
