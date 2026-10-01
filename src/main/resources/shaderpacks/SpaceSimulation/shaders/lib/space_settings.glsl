/*
 * SpaceSimulation 内建光影包 —— 全部可调选项与阴影常量。
 *
 * 所有用到它们的 program 都必须 include 本文件：Iris 要求同一个选项若在多个文件里定义，
 * 其默认值必须完全一致，集中在这里定义是满足该约束的最稳妥方式。
 *
 * 选项语法（OptiFine/Iris 通用）：`#define 名称 默认值 // [可选值列表] 说明`，
 * 值列表用空格分隔；带值列表的 numeric const 同样会出现在光影设置界面。
 * 在 shaders.properties 的 `sliders = ...` 里列出的选项会显示为滑块，其余为按钮。
 *
 * 注意：本文件刻意**不使用 include guard**。`#ifndef X_INCLUDED` 这类守卫会被 Iris
 * 识别成布尔选项，在光影设置界面多出无意义的开关；而本包的 include 结构没有菱形依赖
 * （每个编译单元内各文件只展开一次），不加守卫是安全的。
 */

// ==================== 光照 ====================

// 太阳角半径（正弦值）。太阳是半径 20~50 万格的球面光源，玩家在百万格外的
// 小行星带看它约占 6°~30°，即 sin ≈ 0.1~0.5。这个「光源不是一个点」的事实同时决定了两件事：
//   ① 明暗交界的柔和程度（值越大，正对太阳时侧面越不会全黑）；
//   ② 阴影半影的宽度（值越大，阴影边缘越柔）。
#define SUN_ANGULAR_SIN 0.30 // [0.05 0.15 0.30 0.45] Sun angular radius: larger = softer shading and wider shadow penumbra

// 太阳直射光强度
#define SUN_INTENSITY 1.15 // [0.80 1.00 1.15 1.40 1.80] Sunlight intensity

// 距离衰减强度档位。
//
// 亮度系数 = clamp(基准距离 / 实际距离, 0, 1)^指数，即反比例衰减：
// 基准距离以内为满亮，更远按 1/d 下降。档位决定「基准距离」。
//
// ⚠ 用**整数档位**而不是浮点参数：实测本环境下 #define 浮点选项取值不可靠（会被替换成
// 值列表首项，导致衰减被静默短路），而**整数选项经实机验证工作正常**
// （调试开关 SPACE_DEBUG_DISTANCE 就是整数选项）。这里按档位在编译期展开成一个
// 不带可选值列表的 const，取值完全确定。
//
// 档位对应的衰减表现（反比例，1/d）：
//   档位   基准距离   100万     200万     2000万
//   1      300万      1.00      1.00      0.15
//   2      150万      1.00      0.75      0.075   ← 默认
//   3       80万      1.00      0.40      0.04
//   4       40万      0.40      0.20      0.02
#define SUN_FALLOFF_LEVEL 2 // [0 1 2 3 4] Distance falloff: 0=off 1=gentle 2=normal 3=strong 4=harsh

#if SUN_FALLOFF_LEVEL == 0
    const float SUN_DISTANCE_REFERENCE = 1.0e12;   // 极大 → 实际不衰减
#elif SUN_FALLOFF_LEVEL == 1
    const float SUN_DISTANCE_REFERENCE = 3000000.0;
#elif SUN_FALLOFF_LEVEL == 2
    const float SUN_DISTANCE_REFERENCE = 1500000.0;
#elif SUN_FALLOFF_LEVEL == 3
    const float SUN_DISTANCE_REFERENCE = 800000.0;
#else
    const float SUN_DISTANCE_REFERENCE = 400000.0;
#endif

// 衰减指数固定为 1.0（反比例）。太阳半径 5~10 万格属大面光源，
// 辐照度随距离下降比方反比慢，故反比例比平方反比更贴合观感。
const float SUN_DISTANCE_FALLOFF = 1.0;

// 调试可视化：把 uniform / 常量的实际取值直接画到屏幕上。
//   0 = 关闭（正常光照）
//   1 = 距离灰度：spaceSunDistance / 2000 万格（黑 ~ 白）
//   2 = 衰减系数灰度（0 ~ 1）
//   3 = 方向映射到 RGB：sunPosition 归一化后按 (d*0.5+0.5) 上色（恒浅绿 = 未绑定）
//   4 = length(sunPosition) 灰度（恒黑 = 未绑定）
//   5 = 衰减参数自检：R = 基准距离/300万（档1=白、档2=中灰、档3~4 更暗），G = 衰减指数/3
#define SPACE_DEBUG_DISTANCE 0 // [0 1 2 3 4 5] Debug: 0=off 1=dist 2=atten 3=dir 4=sunLen 5=params

// 环境光强度。用户裁定取极低值：环境光恒定会稀释距离衰减的观感
// （衰减只作用于太阳直射光，环境光若偏大，远处仍会被"托"亮、看不出变暗）。
// 0.001 相当于几乎不补光，背光面与极远处会接近纯黑；0.0 则完全不加环境光。
#define SPACE_AMBIENT 0.001 // [0.0 0.001 0.01 0.03 0.06 0.11 0.18] Ambient light level

// 环境光颜色（冷色，代表星光/反射光）
#define SPACE_AMBIENT_R 0.62 // [0.40 0.50 0.62 0.75 0.90] Ambient light red
#define SPACE_AMBIENT_G 0.70 // [0.45 0.58 0.70 0.82 0.95] Ambient light green
#define SPACE_AMBIENT_B 0.86 // [0.55 0.68 0.86 0.95 1.00] Ambient light blue

// ==================== 阴影 ====================

// 阴影质量档：0 = 硬边（最快）；1/2/3 = PCSS 软阴影，遮挡物搜索与滤波采样数递增。
#define SHADOW_QUALITY 2 // [0 1 2 3] Shadow quality: 0 = hard edges (fastest), 3 = softest (slowest)

// 阴影图分辨率（Iris 常量，必须全局一致）
const int shadowMapResolution = 2048; // [1024 2048 3072 4096 8192] Shadow map resolution

// 阴影渲染距离（格）。阴影图覆盖以相机为中心、边长 2×该值 的正交盒；
// 本模组的「局部平行光近似」只在这个范围内成立，故此值同时决定阴影有效半径与精度
// （范围越小，同样分辨率下每纹素覆盖的世界距离越小、阴影越锐利）。
const float shadowDistance = 128.0; // [64.0 96.0 128.0 192.0 256.0] Shadow render distance in blocks
const float shadowDistanceRenderMul = 1.0;

// 阴影网格对齐间隔（Iris 常量）。Iris 用它把阴影视图矩阵的平移量化到世界网格，
// 从而让阴影图纹素稳定、不随相机连续移动而"游泳"。
//
// **该值必须等于一个阴影纹素对应的世界大小**（= 2×shadowDistance / shadowMapResolution）：
// 量化间隔决定相机每移动多远阴影图整格跳变一次，若它大于纹素，跳变量就会超过一个纹素，
// 表现为方块阴影随移动明显抖动。Iris 的默认值（约 2 格）是本包纹素（0.125 格）的 16 倍，
// 因此必须显式设置。
const float shadowIntervalSize = 2.0 * shadowDistance / float(shadowMapResolution);

// ==================== 由质量档派生的采样数 ====================
// 用 const int 而不是 #define：带值列表的 const 才会进入光影设置界面，这些内部常量
// 不带值列表注释，因此不会污染选项列表。

#if SHADOW_QUALITY == 0
    const int SPACE_BLOCKER_SAMPLES = 1;
    const int SPACE_PCF_SAMPLES = 1;
#elif SHADOW_QUALITY == 1
    const int SPACE_BLOCKER_SAMPLES = 4;
    const int SPACE_PCF_SAMPLES = 4;
#elif SHADOW_QUALITY == 2
    const int SPACE_BLOCKER_SAMPLES = 8;
    const int SPACE_PCF_SAMPLES = 8;
#else
    const int SPACE_BLOCKER_SAMPLES = 16;
    const int SPACE_PCF_SAMPLES = 16;
#endif

// 遮挡物搜索半径（UV 空间，约 3 个纹素）
const float SPACE_BLOCKER_SEARCH_RADIUS = 3.0 / float(shadowMapResolution);
// 半影宽度上下限（UV 空间）：下限避免采样点重合，上限避免过度模糊
const float SPACE_PENUMBRA_MIN = 1.0 / float(shadowMapResolution);
const float SPACE_PENUMBRA_MAX = 16.0 / float(shadowMapResolution);
