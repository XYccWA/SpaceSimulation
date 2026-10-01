package org.xyccwa.space_simulation.asteroid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 程序化小行星宇宙 —— 编号 → 轨道根数的确定性生成器（多环带质点系统）。
 *
 * 数据包驱动：每个环带（asteroid_belt）定义自己的半径区间、倾角区间、离心率上限、
 * 总量（由密度换算）与类型权重表；本类把它们编译成"档结构"，并提供 编号 → 轨道 的
 * O(1) 确定性派生。全程零存储、零文件；同一 (seed, id) 跨会话/跨端逐位一致。
 *
 * 编号空间（全局 id，0 ≤ id < totalCount）：
 *   带 b 占据连续区间 [idBase[b], idBase[b] + cellCount[b]×k[b])
 *   带内：id = idBase[b] + localCellKey × k[b] + ringIdx
 *   单元（轨道环）= (aIdx, eIdx, iIdx, oIdx)，ω ≡ 0
 *   环内 K 颗：相位 M0 按环内序号均匀细分（ringIdx ∈ [0, K)），承载总数量
 *
 * 带顺序 = 内缘半径升序（构造时排序），因此 id 空间与 cellKey 空间都是确定的：
 * 相同数据包 + 相同种子 → 相同编号 → 相同轨道。
 *
 * 每带档数：a 档按 (outer−inner)/(2×PROBE_RADIUS) 取整（≥1），保证 dA 与检索半径匹配；
 * e/i/Ω 档数固定为 E_BINS/I_BINS/O_BINS。
 *
 * 要点：
 * 1. 身份 = id（确定性）；轨道根数由（带索引 + 档坐标 + 种子）派生，相位由 ringIdx 派生。
 * 2. ω ≡ 0（消除 (ω, 相位) 一维简并；轨道面方向由 Ω、i 完全决定）。
 * 3. M0 不参与身份：相位只是随时间演化的量（运动），身份永远是 id。
 * 4. 半长轴每档内按体积均匀（cbrt）采样；e/i/Ω 每档内均匀抖动；M0 在环内按序号细分。
 * 5. 引力参数 μ：全局统一（由最内带内缘半径 + 全局内缘周期反推开普勒第三定律）；
 *    某个带若在数据包里覆盖 inner_orbit_period_ticks，则该带用自己的 μ（自成引力系统）。
 * 6. 类型/变体：由类型权重表 + 64 位哈希确定性抽样，写入 {@link AsteroidOrbit} 的
 *    预留字段（type/variant/structure）。
 *
 * 本类不依赖任何 Minecraft 类（纯 double/long/String），便于独立数值验证。
 */
public final class AsteroidUniverse {

    private static final double TWO_PI = 2.0 * Math.PI;
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    /** 检索/实体化分档的最大半径（预加载半径，块）——档粒度的设计基准。 */
    public static final double PROBE_RADIUS = 10_000.0;

    /** 离心率档数（0..maxEccentricity；e 窗口全展开仅 5 档）。 */
    public static final int E_BINS = 5;
    /** 倾角档数（0..maxInclinationRad）。 */
    public static final int I_BINS = 8;
    /** 升交点经度档数（0..2π；方位窗口约 ±16° → 覆盖 ~8 档）。 */
    public static final int O_BINS = 96;

    /** 环带（编译后的档结构）。 */
    public static final class Belt {
        /** 带索引（= id 空间顺序，内缘半径升序）。 */
        public final int index;
        /** 带显示名（数据包 name）。 */
        public final String name;
        /** 内缘半径（块）。 */
        public final double innerRadius;
        /** 外缘半径（块）。 */
        public final double outerRadius;
        /** 倾角下限（弧度）。 */
        public final double minInclinationRad;
        /** 倾角上限（弧度）。 */
        public final double maxInclinationRad;
        /** 离心率上限。 */
        public final double maxEccentricity;
        /** a 档数。 */
        public final int aBins;
        /** 单元总数 = aBins×eBins×iBins×oBins。 */
        public final long cellCount;
        /** 每环颗数（相位细分数）。 */
        public final int k;
        /** 该带小行星总数 = cellCount × k。 */
        public final long totalCount;
        /** 该带 cellKey 的全局起点。 */
        public final long cellBase;
        /** 该带 id 的全局起点。 */
        public final long idBase;
        /** 该带引力参数 μ（块³/tick²）。 */
        public final double mu;
        /** 该带内缘周期覆盖（tick）；≤ 0 表示沿用全局 μ。 */
        public final long periodTicksOverride;
        private final double innerR3;
        private final double dr3;
        private final double dA;
        private final double dE;
        private final double dI;
        private final double dO;
        /** 该带出现的类型（全局类型表索引）。 */
        public final int[] typeIndices;
        /** 类型累积权重（末项 = 1.0）。 */
        public final double[] typeCumulative;

        Belt(int index, String name, double innerRadius, double outerRadius,
             double minInclinationRad, double maxInclinationRad, double maxEccentricity,
             int aBins, int k, long cellBase, long idBase, double mu,
             int[] typeIndices, double[] typeCumulative, long periodTicksOverride) {
            this.index = index;
            this.name = name;
            this.innerRadius = innerRadius;
            this.outerRadius = outerRadius;
            this.minInclinationRad = minInclinationRad;
            this.maxInclinationRad = maxInclinationRad;
            this.maxEccentricity = maxEccentricity;
            this.aBins = aBins;
            this.cellCount = (long) aBins * E_BINS * I_BINS * O_BINS;
            this.k = k;
            this.totalCount = this.cellCount * k;
            this.cellBase = cellBase;
            this.idBase = idBase;
            this.mu = mu;
            this.periodTicksOverride = periodTicksOverride;
            this.innerR3 = innerRadius * innerRadius * innerRadius;
            this.dr3 = outerRadius * outerRadius * outerRadius - this.innerR3;
            this.dA = (outerRadius - innerRadius) / aBins;
            this.dE = maxEccentricity / E_BINS;
            this.dI = maxInclinationRad / I_BINS;
            this.dO = TWO_PI / O_BINS;
            this.typeIndices = typeIndices;
            this.typeCumulative = typeCumulative;
        }

        /** 该带倾角跨度（弧度）。 */
        public double inclinationSpanRad() {
            return maxInclinationRad - minInclinationRad;
        }

        /** 该带 μ 来源描述（全局统一 / 带内覆盖周期）。 */
        public String periodTicksText() {
            if (periodTicksOverride > 0L) {
                return String.format("独立 μ：内缘周期 %,d tick 覆盖", periodTicksOverride);
            }
            return "全局统一 μ";
        }
    }

    public final long seed;
    /** 类型表（数据包 asteroid_type 的投影；数据包缺失时为空表）。 */
    public final AsteroidTypeTable types;
    /** 全部环带（内缘半径升序）。 */
    public final Belt[] belts;
    /** 全系统小行星总数。 */
    public final long totalCount;
    /** 最内带内缘半径（块）。 */
    public final double innerRadius;
    /** 最外带外缘半径（块）。 */
    public final double outerRadius;
    /** 全带最大离心率。 */
    public final double maxEccentricity;
    /** 全带最大倾角（弧度）。 */
    public final double maxInclinationRad;
    /** 全局引力参数 μ（块³/tick²）。 */
    public final double mu;
    /** 全局内缘公转周期（tick，μ 的基准）。 */
    public final long globalInnerOrbitPeriodTicks;

    /**
     * @param defs 环带定义（至少一个；构造时按内缘半径升序排序）
     * @param globalInnerOrbitPeriodTicks 全局 μ 基准：最内带内缘半径处的公转周期（tick）
     */
    public AsteroidUniverse(long seed, AsteroidTypeTable types,
                            List<AsteroidBeltDef> defs, long globalInnerOrbitPeriodTicks) {
        if (defs == null || defs.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个小行星带定义");
        }
        this.seed = seed;
        this.types = types == null ? AsteroidTypeTable.EMPTY : types;
        this.globalInnerOrbitPeriodTicks = Math.max(1L, globalInnerOrbitPeriodTicks);

        List<AsteroidBeltDef> sorted = new ArrayList<>(defs);
        sorted.sort(Comparator.comparingDouble(d -> d.innerRadius));

        double rRef = sorted.get(0).innerRadius;
        this.mu = Math.pow(TWO_PI / this.globalInnerOrbitPeriodTicks, 2.0) * rRef * rRef * rRef;

        Belt[] arr = new Belt[sorted.size()];
        long cellCursor = 0L;
        long idCursor = 0L;
        for (int bi = 0; bi < sorted.size(); bi++) {
            AsteroidBeltDef d = sorted.get(bi);
            int aBins = Math.max(1, (int) Math.round(
                    (d.outerRadius - d.innerRadius) / (2.0 * PROBE_RADIUS)));
            long cellCount = (long) aBins * E_BINS * I_BINS * O_BINS;
            long kk = (d.totalCount + cellCount - 1L) / cellCount;
            int k = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, kk));
            double bandMu = d.periodTicksOverride > 0L
                    ? Math.pow(TWO_PI / d.periodTicksOverride, 2.0)
                        * d.innerRadius * d.innerRadius * d.innerRadius
                    : this.mu;
            arr[bi] = new Belt(bi, d.name, d.innerRadius, d.outerRadius,
                    d.minInclinationRad, d.maxInclinationRad, d.maxEccentricity,
                    aBins, k, cellCursor, idCursor, bandMu,
                    d.typeIndices, d.typeCumulative, d.periodTicksOverride);
            cellCursor += arr[bi].cellCount;
            idCursor += arr[bi].totalCount;
        }
        this.belts = arr;
        this.totalCount = idCursor;
        this.innerRadius = arr[0].innerRadius;
        this.outerRadius = arr[arr.length - 1].outerRadius;
        double e = 0, i = 0;
        for (Belt b : arr) {
            e = Math.max(e, b.maxEccentricity);
            i = Math.max(i, b.maxInclinationRad);
        }
        this.maxEccentricity = e;
        this.maxInclinationRad = i;
    }

    // ---------- 带定位 ----------

    /** 全局 cellKey → 带索引（cellBase 升序二分）。 */
    public int beltIndexOfCell(long cellKey) {
        int lo = 0, hi = belts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (cellKey >= belts[mid].cellBase) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    /** 全局 id → 带索引（idBase 升序二分）。 */
    public int beltIndexOfId(long id) {
        int lo = 0, hi = belts.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (id >= belts[mid].idBase) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    /** 全局 cellKey → 该环所在带的 k（每环颗数）。 */
    public int kOfCell(long cellKey) {
        return belts[beltIndexOfCell(cellKey)].k;
    }

    // ---------- 单元键 / id 编解码 ----------

    /** 带内单元键 = ((aIdx·E_BINS + eIdx)·I_BINS + iIdx)·O_BINS + oIdx（可逆）。 */
    public static long packLocal(int aIdx, int eIdx, int iIdx, int oIdx) {
        return ((((long) aIdx * E_BINS + eIdx) * I_BINS + iIdx) * O_BINS + oIdx);
    }

    /** 带内单元键 → {aIdx, eIdx, iIdx, oIdx}（O(1) 反解）。 */
    public static int[] unpackLocal(long localKey) {
        int oIdx = (int) (localKey % O_BINS);
        long r = localKey / O_BINS;
        int iIdx = (int) (r % I_BINS);
        r /= I_BINS;
        int eIdx = (int) (r % E_BINS);
        int aIdx = (int) (r / E_BINS);
        return new int[]{aIdx, eIdx, iIdx, oIdx};
    }

    /** 带内档坐标 → 全局 cellKey。 */
    public static long packCell(Belt belt, int aIdx, int eIdx, int iIdx, int oIdx) {
        return belt.cellBase + packLocal(aIdx, eIdx, iIdx, oIdx);
    }

    /** 全局 cellKey → 档坐标 {aIdx, eIdx, iIdx, oIdx}。 */
    public int[] unpackCell(long cellKey) {
        Belt b = belts[beltIndexOfCell(cellKey)];
        return unpackLocal(cellKey - b.cellBase);
    }

    /** 完整小行星 id = 带起点 + 带内单元键 × k + ringIdx（cellKey 为全局键）。 */
    public long idOf(long cellKey, int ringIdx) {
        Belt b = belts[beltIndexOfCell(cellKey)];
        return b.idBase + (cellKey - b.cellBase) * b.k + ringIdx;
    }

    /** id → {cellKey(全局), ringIdx}。 */
    public long[] splitId(long id) {
        Belt b = belts[beltIndexOfId(id)];
        long local = id - b.idBase;
        return new long[]{b.cellBase + local / b.k, local % b.k};
    }

    // ---------- 确定性派生 ----------

    /** 全局单元键 → 静态轨道环根数 {a, e, i(rad), Ω(rad), n(rad/tick)}（ω≡0；用于环-球几何测试）。 */
    public double[] cellElements(long cellKey) {
        Belt b = belts[beltIndexOfCell(cellKey)];
        int[] c = unpackLocal(cellKey - b.cellBase);
        long h = hashOf(b.index, c);
        double a = aOf(b, c, h);
        double e = (c[1] + unit(h + 0x2222222222222222L)) * b.dE;
        double i = b.minInclinationRad + (c[2] + unit(h + 0x3333333333333333L)) * b.dI;
        double omega = (c[3] + unit(h + 0x4444444444444444L)) * b.dO;
        double n = Math.sqrt(b.mu / (a * a * a));
        return new double[]{a, e, i, omega, n};
    }

    /** 某带的全局单元键 → 同上（供检索层按带循环，省一次定位）。 */
    public double[] cellElements(Belt b, long cellKey) {
        int[] c = unpackLocal(cellKey - b.cellBase);
        long h = hashOf(b.index, c);
        double a = aOf(b, c, h);
        double e = (c[1] + unit(h + 0x2222222222222222L)) * b.dE;
        double i = b.minInclinationRad + (c[2] + unit(h + 0x3333333333333333L)) * b.dI;
        double omega = (c[3] + unit(h + 0x4444444444444444L)) * b.dO;
        double n = Math.sqrt(b.mu / (a * a * a));
        return new double[]{a, e, i, omega, n};
    }

    /** 环内序号 → 相位 M0（确定性，均匀细分：覆盖 [ringIdx/k·2π, (ringIdx+1)/k·2π)）。 */
    public double m0Of(int ringIdx, int k) {
        long h = splitMix64(seed ^ 0x5EED000000000000L ^ (long) ringIdx * GOLDEN);
        return TWO_PI * (ringIdx + unit(h)) / k;
    }

    /** 完整小行星 id → 轨道（确定性、O(1)、无状态；含类型/变体抽样）。 */
    public AsteroidOrbit orbitOf(long id) {
        if (id < 0L || id >= totalCount) {
            throw new IndexOutOfBoundsException("小行星编号越界: " + id + " (有效 0 ~ " + (totalCount - 1) + ")");
        }
        Belt b = belts[beltIndexOfId(id)];
        long local = id - b.idBase;
        long localCellKey = local / b.k;
        int ringIdx = (int) (local % b.k);
        long cellKey = b.cellBase + localCellKey;
        int[] c = unpackLocal(localCellKey);
        long h = hashOf(b.index, c);
        double a = aOf(b, c, h);
        double e = (c[1] + unit(h + 0x2222222222222222L)) * b.dE;
        double i = b.minInclinationRad + (c[2] + unit(h + 0x3333333333333333L)) * b.dI;
        double omega = (c[3] + unit(h + 0x4444444444444444L)) * b.dO;
        double m0 = m0Of(ringIdx, b.k);

        // 类型/变体（独立哈希盐，与轨道根数解耦）
        String typeId = "";
        String structure = "";
        int variant = 0;
        if (b.typeIndices.length > 0) {
            double uType = unit(splitMix64(h ^ 0x7A11E5A11CE00001L));
            int ti = AsteroidTypeTable.pickVariant(b.typeCumulative, uType);
            int g = ti < b.typeIndices.length ? b.typeIndices[ti] : -1;
            if (g >= 0 && g < types.size) {
                typeId = types.ids[g];
                double[] vc = types.variantCumulative[g];
                String[] vs = types.variantStructures[g];
                if (vc.length > 0) {
                    variant = AsteroidTypeTable.pickVariant(vc, unit(splitMix64(h ^ 0x7A11E5A11CE00002L)));
                    if (variant >= 0 && variant < vs.length) structure = vs[variant];
                }
            }
        }
        return new AsteroidOrbit(id, a, e, i, omega, 0.0, m0, b.mu,
                typeId, variant, structure, b.index, b.name);
    }

    /** 半长轴：档内体积均匀（cbrt 采样）。 */
    private double aOf(Belt b, int[] c, long h) {
        double aLo = b.innerRadius + c[0] * b.dA;
        double aHi = aLo + b.dA;
        double u = unit(h + 0x1111111111111111L);
        return Math.cbrt(aLo * aLo * aLo + u * (aHi * aHi * aHi - aLo * aLo * aLo));
    }

    /** （带索引 + 档坐标）→ 确定性哈希（跨带、跨档互不相关）。 */
    private long hashOf(int beltIndex, int[] c) {
        long base = packLocal(c[0], c[1], c[2], c[3]);
        long mix = base * 0x100000001B3L + beltIndex;
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
