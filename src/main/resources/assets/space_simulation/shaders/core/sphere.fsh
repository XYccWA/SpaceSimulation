#version 150

// 太阳渲染(世界原点,半径 50000 格)片元着色器 —— 经典黄白球 + 光晕。
// 逐像素从 NDC 重建视线方向,做光线-球求交:
//   - 光球层:黄白渐变(中心金黄 -> fresnel 亮白边缘),3D 域噪声颗粒
//     (无经纬接缝/极点),缓慢自转/沸腾/脉动;
//   - 光晕:黄白色,从边缘向外平滑衰减,背景纯黑;
//   - 深度:光球层写真实深度(方块遮挡),光晕在远平面(被方块遮挡);
//   - 顶点只产生片元,方向逐像素重建,转动视角不扭曲;纹理固定世界空间。

uniform mat4 ProjMat;
uniform mat4 InvView;          // 相机 view 的逆,把交点转回世界空间
uniform vec2 ScreenSize;
uniform vec3 SphereCenterView; // 球心在 view 空间的坐标(CPU 端计算)
uniform float SphereRadius;
uniform float Time;            // 秒,驱动自转/沸腾/脉动

out vec4 fragColor;

// ---------- 3D value noise + fbm(球面域,无接缝无极点) ----------
float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(mix(hash13(i), hash13(i + vec3(1.0, 0.0, 0.0)), u.x),
            mix(hash13(i + vec3(0.0, 1.0, 0.0)), hash13(i + vec3(1.0, 1.0, 0.0)), u.x), u.y),
        mix(mix(hash13(i + vec3(0.0, 0.0, 1.0)), hash13(i + vec3(1.0, 0.0, 1.0)), u.x),
            mix(hash13(i + vec3(0.0, 1.0, 1.0)), hash13(i + vec3(1.0, 1.0, 1.0)), u.x), u.y),
        u.z);
}

float fbm3(vec3 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        v += amp * noise3(p);
        p = p * 2.03 + vec3(11.7, 7.3, 5.9);
        amp *= 0.5;
    }
    return v;
}

// Worley(蜂窝)噪声:返回到最近特征点的距离,特征点带缓慢时间漂移。
float worley3(vec3 p) {
    float dmin = 8.0;
    vec3 id = floor(p);
    float wob = Time * 0.05;
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            for (int k = -1; k <= 1; k++) {
                vec3 cell = id + vec3(float(i), float(j), float(k));
                vec3 fp = cell + vec3(hash13(cell), hash13(cell + 7.1), hash13(cell + 13.7));
                float ph = hash13(cell + 3.7) * 6.2831853;
                fp.x += 0.06 * sin(wob + ph);
                fp.y += 0.06 * cos(wob * 1.3 + ph);
                fp.z += 0.06 * sin(wob * 0.7 + ph * 1.7);
                float dx = p.x - fp.x, dy = p.y - fp.y, dz = p.z - fp.z;
                dmin = min(dmin, dx * dx + dy * dy + dz * dz);
            }
        }
    }
    return sqrt(dmin);
}

// 光晕亮度:反比例衰减,无限延伸(无截断/分界),背后无太阳相关结构。
// - 朝向太阳侧(b < 0):光晕环 = 0.85 * R / max(impact, R),起始接近日面亮度,
//   反比例衰减;
// - 背对太阳侧(b >= 0):均匀背景光晕 = 0.85 * R / max(D, R)(D = 玩家到球心
//   距离),不依赖 impact —— 背后是均匀的淡淡光晕,没有"与太阳等大的
//   对称亮斑/亮环";b=0 处两式相等(impact=D),严格连续。
float haloIntensity(vec3 oc, float b, float radius, float pulse) {
    float D = length(oc);
    if (b >= 0.0) {
        return 0.85 * radius / max(D, radius) * pulse;
    }
    float impact2 = dot(oc, oc) - b * b;
    float impact = sqrt(max(impact2, 0.0));
    return 0.85 * radius / max(impact, radius) * pulse;
}

void main() {
    vec2 ndc = gl_FragCoord.xy / ScreenSize * 2.0 - 1.0;
    vec4 farPt = inverse(ProjMat) * vec4(ndc, 1.0, 1.0);
    vec3 dir = normalize(farPt.xyz / farPt.w);

    vec3 center = SphereCenterView;
    float radius = SphereRadius;

    vec3 oc = -center;
    float b = dot(oc, dir);
    float c = dot(oc, oc) - radius * radius;
    float h = b * b - c;

    // 全局缓慢脉动
    float pulse = 1.0 + 0.03 * sin(Time * 0.9) * sin(Time * 0.37 + 1.7);

    // 恒星光谱颜色:随半径变化(与真实恒星一致)
    // 小(200000)= 红矮星偏红,中(350000)= 太阳金黄,大(500000)= 蓝白巨星
    float starT = clamp((radius - 200000.0) / 300000.0, 0.0, 1.0);
    vec3 redStar = vec3(1.0, 0.55, 0.25);
    vec3 yellowStar = vec3(1.0, 0.94, 0.62);
    vec3 blueStar = vec3(0.82, 0.88, 1.0);
    vec3 starCol = starT < 0.5
            ? mix(redStar, yellowStar, starT * 2.0)
            : mix(yellowStar, blueStar, (starT - 0.5) * 2.0);

    vec3 col = vec3(0.0);

    if (h >= 0.0) {
        // ================= 光球层(黄白) =================
        float t = -b - sqrt(h); // 近交点
        if (t < 0.05) {
            t = -b + sqrt(h);
            if (t < 0.05) {
                // 日面在玩家背后(如玩家在光晕内背对太阳):不画日面,
                // 回退到光晕,避免背后出现与太阳等大的空洞
                float halo = haloIntensity(oc, b, radius, pulse);
                col = starCol * halo;
                gl_FragDepth = 1.0;
                fragColor = vec4(col, 1.0);
                return;
            }
        }
        vec3 hit = dir * t;
        vec3 n = normalize(hit - center);

        // 深度(真实球面深度,参与遮挡)
        vec4 clip = ProjMat * vec4(hit, 1.0);
        float depth = (clip.z / clip.w + 1.0) * 0.5;
        gl_FragDepth = clamp(depth, 0.0, 1.0);

        // 表面采样(世界空间,缓慢自转)
        vec4 hw = InvView * vec4(hit, 1.0);
        vec3 surfWorld = normalize(hw.xyz / hw.w);
        float rot = Time * 0.012;
        float ca = cos(rot);
        float sa = sin(rot);
        vec3 rs = vec3(surfWorld.x * ca + surfWorld.z * sa, surfWorld.y,
                       -surfWorld.x * sa + surfWorld.z * ca);

        // 3D 沸腾域(极慢平移 + 胞体呼吸)
        vec3 q = rs * 3.0 + vec3(Time * 0.05, -Time * 0.035, Time * 0.04);
        vec3 w = vec3(fbm3(q), fbm3(q + 11.7), fbm3(q + 23.3)) - 0.5;
        float breath = 0.85 + 0.30 * fbm3(rs * 1.5 + vec3(0.0, Time * 0.018, Time * 0.014));
        float cell = fbm3(q + w * 1.1 * breath);

        // ===== 恒星色:中心 = 光谱色,边缘提亮偏白 =====
        float mu = abs(dot(dir, n)); // 1 = 日心,0 = 边缘
        vec3 centerCol = starCol;
        vec3 edgeCol = mix(starCol, vec3(1.0), 0.55);
        vec3 base = mix(centerCol, edgeCol, pow(1.0 - mu, 2.0));
        base *= 1.0 - 0.15 * pow(1.0 - mu, 2.0); // 轻微临边昏暗

        // 温和颗粒(亮白 / 恒星暗色,表面质感,缓慢演化)
        float wDist = worley3(rs * 14.0);
        float cellCore = 1.0 - smoothstep(0.30, 0.65, wDist);
        float lane = smoothstep(0.55, 0.85, wDist);
        float granCell = 0.62 + 0.60 * cellCore - 0.22 * lane;
        granCell = pow(max(granCell, 0.05), 1.15);
        float g = clamp(granCell * 0.85 + cell * 0.15, 0.0, 1.0);
        vec3 brightGran = mix(vec3(1.0), starCol, 0.25);
        vec3 darkGran = starCol * 0.8;
        base = mix(base, mix(darkGran, brightGran, g), 0.30);

        base *= pulse;
        col = base;
    } else {
        // ================= 光晕(球体外任意方向) =================
        float halo = haloIntensity(oc, b, radius, pulse);
        col = starCol * halo;
        // 光晕片元深度固定远平面:与天空同深度可显示,被方块遮挡
        gl_FragDepth = 1.0;
    }

    fragColor = vec4(col, 1.0);
}
