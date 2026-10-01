package org.xyccwa.space_simulation.asteroid;

/**
 * 小行星带定义（纯数值输入，无 Minecraft 依赖）—— 数据包 asteroid_belt 解析 + 校验后的投影。
 *
 * 由数据包层（asteroid.data）产出，交给 {@link AsteroidUniverse} 构造运行时档结构：
 *   - 半径区间 [innerRadius, outerRadius]（块，自世界原点/太阳起算），带间不得重叠；
 *   - 倾角区间 [minInclinationRad, maxInclinationRad]（由 "max_deg" 或 "altitude" 两种写法换算）；
 *   - 离心率上限（椭圆约束）；
 *   - 该带请求的小行星总数（由 density 的 total / mean_spacing 两种写法换算）；
 *   - 类型权重表（全局类型索引 + 归一化累积分布，末项 = 1.0）。
 *
 * 不可变；本类不引用任何 Minecraft 类型，便于独立数值验证。
 */
public final class AsteroidBeltDef {

    /** 环带显示名（数据包 name 字段）。 */
    public final String name;
    /** 环带内缘半径（块）。 */
    public final double innerRadius;
    /** 环带外缘半径（块）。 */
    public final double outerRadius;
    /** 倾角下限（弧度）。 */
    public final double minInclinationRad;
    /** 倾角上限（弧度）。 */
    public final double maxInclinationRad;
    /** 离心率上限（0 ≤ e < 1）。 */
    public final double maxEccentricity;
    /** 该带请求的小行星总数（≥ 1；实际 = cellCount × k，向上取整）。 */
    public final long totalCount;
    /** 该带出现的类型（全局类型表索引，与 typeCumulative 等长）。 */
    public final int[] typeIndices;
    /** 类型累积权重（单调递增，末项 = 1.0）。 */
    public final double[] typeCumulative;
    /** 该带内缘公转周期覆盖（tick）；≤ 0 表示沿用全局 μ。 */
    public final long periodTicksOverride;

    public AsteroidBeltDef(String name, double innerRadius, double outerRadius,
                           double minInclinationRad, double maxInclinationRad,
                           double maxEccentricity, long totalCount,
                           int[] typeIndices, double[] typeCumulative,
                           long periodTicksOverride) {
        this.name = name == null || name.isBlank() ? "未命名环带" : name;
        this.innerRadius = innerRadius;
        this.outerRadius = outerRadius;
        this.minInclinationRad = minInclinationRad;
        this.maxInclinationRad = maxInclinationRad;
        this.maxEccentricity = maxEccentricity;
        this.totalCount = Math.max(1L, totalCount);
        this.typeIndices = typeIndices == null ? new int[0] : typeIndices;
        this.typeCumulative = typeCumulative == null ? new double[0] : typeCumulative;
        this.periodTicksOverride = periodTicksOverride;
    }
}
