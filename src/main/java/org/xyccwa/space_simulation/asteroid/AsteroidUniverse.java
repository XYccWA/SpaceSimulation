package org.xyccwa.space_simulation.asteroid;

/**
 * 程序化小行星宇宙 —— 编号 → 轨道根数的确定性生成器（质点系统）。
 *
 * 设计要点（针对千万~十亿级规模）：
 * 1. 小行星身份 = 自身编号 index（long，0..totalCount-1），不依赖任何网格单元。
 * 2. 六根数由 SplitMix64 哈希从 (seed, index) 确定性派生：任意编号 O(1) 按需计算，
 *    全局零存储、零文件；同一 (seed, index) 跨会话、跨端输出逐位一致。
 * 3. 半长轴按体积均匀采样 a = ∛(R内³ + u·(R外³−R内³))：任意空间点局部密度恒定。
 * 4. M0 只是相位（随机 0..2π），随时间连续演化；身份永远是 index，不存在
 *    “相位参与存在性判定导致小行星消失/涌现”这类历史上出现过的缺陷。
 * 5. 引力参数 μ 由配置内缘周期反推（开普勒第三定律），全系统统一。
 *
 * 本类不依赖任何 Minecraft 类（纯 double 数值），便于独立数值验证。
 */
public final class AsteroidUniverse {

    private static final double TWO_PI = 2.0 * Math.PI;
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    public final long seed;
    /** 小行星总数（身份范围 0..totalCount-1）。 */
    public final long totalCount;
    /** 环带内缘半径（块，自世界原点/太阳起算）。 */
    public final double innerRadius;
    /** 环带外缘半径（块）。 */
    public final double outerRadius;
    /** 最大离心率（椭圆约束，0 ≤ e < 1）。 */
    public final double maxEccentricity;
    /** 最大轨道倾角（弧度）。 */
    public final double maxInclinationRad;
    /** 引力参数 μ（块³/tick²）。 */
    public final double mu;

    private final double innerR3;
    private final double dr3;

    /**
     * @param innerOrbitPeriodTicks 内缘半径处的公转周期（tick），据此按开普勒第三定律
     *                             反推 μ = (2π/T)²·R内³，使轨道速度/周期与游戏时间尺度匹配。
     */
    public AsteroidUniverse(long seed, long totalCount,
                            double innerRadius, double outerRadius,
                            double maxEccentricity, double maxInclinationDeg,
                            double innerOrbitPeriodTicks) {
        this.seed = seed;
        this.totalCount = totalCount;
        this.innerRadius = innerRadius;
        this.outerRadius = outerRadius;
        this.maxEccentricity = maxEccentricity;
        this.maxInclinationRad = Math.toRadians(maxInclinationDeg);
        this.mu = Math.pow(TWO_PI / innerOrbitPeriodTicks, 2.0)
                * innerRadius * innerRadius * innerRadius;
        this.innerR3 = innerRadius * innerRadius * innerRadius;
        this.dr3 = outerRadius * outerRadius * outerRadius - innerR3;
    }

    /** 编号 → 确定性轨道（O(1)、无状态、可复现）。 */
    public AsteroidOrbit orbitOf(long index) {
        long h = splitMix64(index ^ seed); // 身份哈希：相邻编号互不相关
        double uA = unit(splitMix64(h + 0x1111111111111111L));
        double uE = unit(splitMix64(h + 0x2222222222222222L));
        double uI = unit(splitMix64(h + 0x3333333333333333L));
        double uO = unit(splitMix64(h + 0x4444444444444444L));
        double uW = unit(splitMix64(h + 0x5555555555555555L));
        double uM = unit(splitMix64(h + 0x6666666666666666L));
        // 体积均匀采样半长轴（局部空间密度恒定）
        double a = Math.cbrt(innerR3 + uA * dr3);
        double e = uE * maxEccentricity;
        double i = uI * maxInclinationRad;
        double omega = TWO_PI * uO;
        double argP = TWO_PI * uW;
        double m0 = TWO_PI * uM;
        return new AsteroidOrbit(index, a, e, i, omega, argP, m0, mu);
    }

    /** SplitMix64 最终混合（确定性 64 位哈希）。 */
    private static long splitMix64(long x) {
        x += GOLDEN;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        x ^= (x >>> 31);
        return x;
    }

    /** 64 位哈希 → [0,1)：取高 53 位（2⁵³ = double 完整尾数，均匀）。 */
    private static double unit(long h) {
        return (h >>> 11) * (1.0 / 9007199254740992.0);
    }
}