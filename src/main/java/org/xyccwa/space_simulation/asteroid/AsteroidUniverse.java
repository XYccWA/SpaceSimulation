package org.xyccwa.space_simulation.asteroid;

/**
 * 程序化小行星宇宙 —— 编号 → 轨道根数的确定性生成器（质点系统）。
 *
 * 编号 = 单元键(cellKey) × 每环颗数(K) + 环内序号：检索（半径 5000~10000 格、持续监测）的
 * 粒度基础，总量（千万~十亿级）与检索粒度解耦：
 *
 *   单元（轨道环）= (半长轴档 aIdx, 离心率档 eIdx, 倾角档 iIdx, 升交点档 oIdx)，ω ≡ 0
 *   每环 K 颗：相位 M0 按环内序号均匀细分（ringIdx ∈ [0, K)），承载总数量
 *   完整小行星 id = cellKey × K + ringIdx，0 ≤ id < CELL_COUNT × K = totalCount
 *
 * 要点：
 * 1. 身份 = id（确定性）：轨道环根数由单元键 + 种子确定性派生；相位由 ringIdx 确定性派生。
 *    任意编号 O(1) 按需计算，全局零存储、零文件；同一 (seed, id) 跨会话/跨端逐位一致。
 * 2. ω ≡ 0（消除 (ω, 相位) 一维简并；轨道面方向由 Ω、i 完全决定）。
 * 3. M0 不参与身份：相位只是随时间演化的量（运动），身份永远是 id —— 不存在
 *    “相位参与存在性判定导致小行星消失/涌现”的缺陷。
 * 4. 半长轴每档内按体积均匀（cbrt）采样；e/i/Ω 每档内均匀抖动；M0 在环内按序号细分。
 * 5. 档数与检索半径匹配（PROBE_RADIUS=10000）：dA≈2·PROBE_RADIUS、方位档使窗口逆推
 *    只枚举几十~几千个候选单元（见 AsteroidProximity）。
 * 6. 引力参数 μ 由内缘周期反推（开普勒第三定律），全系统统一。
 *
 * 本类不依赖任何 Minecraft 类（纯 double/long 数值），便于独立数值验证。
 */
public final class AsteroidUniverse {

    private static final double TWO_PI = 2.0 * Math.PI;
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    /** 检索/实体化分档的最大半径（预加载半径，块）——档粒度的设计基准。 */
    public static final double PROBE_RADIUS = 10_000.0;

    /** 半长轴档数（dA = (outer−inner)/A_BINS ≈ 2×PROBE_RADIUS）。 */
    public static final int A_BINS = 100;
    /** 离心率档数（0..maxEccentricity；e 窗口全展开仅 5 档）。 */
    public static final int E_BINS = 5;
    /** 倾角档数（0..maxInclinationRad）。 */
    public static final int I_BINS = 8;
    /** 升交点经度档数（0..2π；方位窗口约 ±16° → 覆盖 ~8 档）。 */
    public static final int O_BINS = 96;

    /** 轨道环总数（单元数）。 */
    public static final long CELL_COUNT = (long) A_BINS * E_BINS * I_BINS * O_BINS;

    public final long seed;
    /** 每环颗数（相位细分数）。 */
    public final int k;
    /** 实际小行星总数 = CELL_COUNT × k。 */
    public final long totalCount;
    /** 环带内缘半径（块，自世界原点/太阳起算）。 */
    public final double innerRadius;
    /** 环带外缘半径（块）。 */
    public final double outerRadius;
    /** 最大离心率（椭圆约束）。 */
    public final double maxEccentricity;
    /** 最大轨道倾角（弧度）。 */
    public final double maxInclinationRad;
    /** 引力参数 μ（块³/tick²）。 */
    public final double mu;

    private final double innerR3;
    private final double dr3;
    private final double dA; // 每档半长轴宽度
    private final double dE; // 每档离心率宽度
    private final double dI; // 每档倾角宽度
    private final double dO; // 每档升交点经度宽度

    /**
     * @param requestedTotal 请求的小行星总数（实际 = CELL_COUNT × k，向上取整）
     * @param innerOrbitPeriodTicks 内缘半径处公转周期（tick），据此按开普勒第三定律
     *                             反推 μ = (2π/T)²·R内³
     */
    public AsteroidUniverse(long seed, long requestedTotal,
                            double innerRadius, double outerRadius,
                            double maxEccentricity, double maxInclinationDeg,
                            long innerOrbitPeriodTicks) {
        this.seed = seed;
        long kk = Math.max(1L, (requestedTotal + CELL_COUNT - 1) / CELL_COUNT);
        this.k = (int) Math.min(kk, Integer.MAX_VALUE);
        this.totalCount = CELL_COUNT * this.k;
        this.innerRadius = innerRadius;
        this.outerRadius = outerRadius;
        this.maxEccentricity = maxEccentricity;
        this.maxInclinationRad = Math.toRadians(maxInclinationDeg);
        this.mu = Math.pow(TWO_PI / innerOrbitPeriodTicks, 2.0)
                * innerRadius * innerRadius * innerRadius;
        this.innerR3 = innerRadius * innerRadius * innerRadius;
        this.dr3 = outerRadius * outerRadius * outerRadius - innerR3;
        this.dA = (outerRadius - innerRadius) / A_BINS;
        this.dE = maxEccentricity / E_BINS;
        this.dI = this.maxInclinationRad / I_BINS;
        this.dO = TWO_PI / O_BINS;
    }

    // ---------- 单元键 / id 编解码 ----------

    /** 单元键 = ((aIdx·E_BINS + eIdx)·I_BINS + iIdx)·O_BINS + oIdx（可逆）。 */
    public static long packCell(int aIdx, int eIdx, int iIdx, int oIdx) {
        return ((((long) aIdx * E_BINS + eIdx) * I_BINS + iIdx) * O_BINS + oIdx);
    }

    /** 单元键 → {aIdx, eIdx, iIdx, oIdx}（O(1) 反解）。 */
    public static int[] unpackCell(long cellKey) {
        int oIdx = (int) (cellKey % O_BINS);
        long r = cellKey / O_BINS;
        int iIdx = (int) (r % I_BINS);
        r /= I_BINS;
        int eIdx = (int) (r % E_BINS);
        int aIdx = (int) (r / E_BINS);
        return new int[]{aIdx, eIdx, iIdx, oIdx};
    }

    /** 完整小行星 id = cellKey × k + ringIdx。 */
    public long idOf(long cellKey, int ringIdx) {
        return cellKey * k + ringIdx;
    }

    /** id → {cellKey, ringIdx}。 */
    public long[] splitId(long id) {
        return new long[]{id / k, id % k};
    }

    // ---------- 确定性派生 ----------

    /** 单元键 → 静态轨道环根数 {a, e, i(rad), Ω(rad), n(rad/tick)}（ω≡0；用于环-球几何测试）。 */
    public double[] cellElements(long cellKey) {
        int[] c = unpackCell(cellKey);
        long h = hashOf(c);
        double a = aOf(c, h);
        double e = (c[1] + unit(h + 0x2222222222222222L)) * dE;
        double i = (c[2] + unit(h + 0x3333333333333333L)) * dI;
        double omega = (c[3] + unit(h + 0x4444444444444444L)) * dO;
        double n = Math.sqrt(mu / (a * a * a));
        return new double[]{a, e, i, omega, n};
    }

    /** 环内序号 → 相位 M0（确定性，均匀细分：覆盖 [ringIdx/K·2π, (ringIdx+1)/K·2π)）。 */
    public double m0Of(int ringIdx) {
        long h = splitMix64(seed ^ 0x5EED000000000000L ^ ringIdx * 0x9E3779B97F4A7C15L);
        return TWO_PI * (ringIdx + unit(h)) / k;
    }

    /** 完整小行星 id → 轨道（确定性、O(1)、无状态）。 */
    public AsteroidOrbit orbitOf(long id) {
        long cellKey = id / k;
        int ringIdx = (int) (id % k);
        int[] c = unpackCell(cellKey);
        long h = hashOf(c);
        double a = aOf(c, h);
        double e = (c[1] + unit(h + 0x2222222222222222L)) * dE;
        double i = (c[2] + unit(h + 0x3333333333333333L)) * dI;
        double omega = (c[3] + unit(h + 0x4444444444444444L)) * dO;
        double m0 = m0Of(ringIdx);
        return new AsteroidOrbit(id, a, e, i, omega, 0.0, m0, mu);
    }

    /** 半长轴：档内体积均匀（cbrt 采样）。 */
    private double aOf(int[] c, long h) {
        double aLo = innerRadius + c[0] * dA;
        double aHi = aLo + dA;
        double u = unit(h + 0x1111111111111111L);
        return Math.cbrt(aLo * aLo * aLo + u * (aHi * aHi * aHi - aLo * aLo * aLo));
    }

    /** 档坐标 → 确定性哈希（跨档互不相关）。 */
    private long hashOf(int[] c) {
        long mix = packCell(c[0], c[1], c[2], c[3]);
        return splitMix64(seed ^ splitMix64(GOLDEN ^ mix));
    }

    /** SplitMix64 最终混合。 */
    private static long splitMix64(long x) {
        x += GOLDEN;
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        x ^= (x >>> 31);
        return x;
    }

    /** 64 位哈希 → [0,1)。 */
    private static double unit(long h) {
        return (h >>> 11) * (1.0 / 9007199254740992.0);
    }
}