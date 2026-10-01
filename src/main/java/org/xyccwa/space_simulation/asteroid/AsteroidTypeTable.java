package org.xyccwa.space_simulation.asteroid;

/**
 * 小行星类型表（纯数值，无 Minecraft 依赖）—— 数据包 asteroid_type 的运行时投影。
 *
 * 每种类型：
 *   id        —— 资源位置字符串（如 "space_simulation:stone"），身份标识；
 *   name      —— 显示名（可空则回退到 id）；
 *   variants  —— 变体列表（结构 id + 名称 + 权重），权重归一化成累积分布 [0,1)，
 *                抽样时由 64 位哈希映射的 unit 值二分定位。
 *
 * 本类不可变、线程安全；数据包重载时整体替换（旧实例继续被旧宇宙引用，直到宇宙一并替换）。
 */
public final class AsteroidTypeTable {

    /** 类型总数（全局索引 0..size-1）。 */
    public final int size;
    /** 类型 id（资源位置字符串）。 */
    public final String[] ids;
    /** 类型显示名（与 ids 等长，永不为 null）。 */
    public final String[] names;
    /** [type] → 变体结构 id 数组（可空数组表示该类型无结构引用）。 */
    public final String[][] variantStructures;
    /** [type] → 变体显示名数组（与 variantStructures 等长）。 */
    public final String[][] variantNames;
    /** [type] → 变体累积权重（长度 = 变体数，单调递增，末项 = 1.0）。 */
    public final double[][] variantCumulative;

    /** 空表（数据包缺失/全部解析失败时的安全回退：类型 id 为空串）。 */
    public static final AsteroidTypeTable EMPTY = new AsteroidTypeTable(
            new String[0], new String[0], new String[0][], new String[0][], new double[0][]);

    public AsteroidTypeTable(String[] ids, String[] names,
                             String[][] variantStructures, String[][] variantNames,
                             double[][] variantCumulative) {
        this.ids = ids;
        this.names = names;
        this.variantStructures = variantStructures;
        this.variantNames = variantNames;
        this.variantCumulative = variantCumulative;
        this.size = ids.length;
    }

    /** 类型 id → 全局索引；找不到返回 -1。 */
    public int indexOf(String id) {
        for (int i = 0; i < size; i++) {
            if (ids[i].equals(id)) return i;
        }
        return -1;
    }

    /** 类型 id 对应的显示名；未知 id 原样返回。 */
    public String nameOf(String id) {
        int i = indexOf(id);
        return i < 0 ? id : names[i];
    }

    /** 变体抽样：unit ∈ [0,1) → 变体序号（权重为空时返回 0）。 */
    public static int pickVariant(double[] cumulative, double unit) {
        if (cumulative == null || cumulative.length == 0) return 0;
        int lo = 0, hi = cumulative.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (unit < cumulative[mid]) hi = mid; else lo = mid + 1;
        }
        return lo;
    }
}
