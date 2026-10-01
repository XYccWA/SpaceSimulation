package org.xyccwa.space_simulation.asteroid;

import org.xyccwa.space_simulation.config.SpaceSimulationConfig;

/**
 * 小行星竖直（Y）域上限 —— 实体化的硬约束。
 *
 * <p>为什么存在：小行星的实体化载体是 sable 子层级，而 sable 每 tick 在
 * {@code ServerSubLevel.tick()} 里检查子层级 globalBounds 的 Y：
 * <pre>
 *   if (bounds.minY() &lt; SableConfig.SUB_LEVEL_REMOVE_MIN   // 默认 -10000
 *    || bounds.maxY() &gt; SableConfig.SUB_LEVEL_REMOVE_MAX)  // 默认 100000
 *       -&gt; "Sub-level {} has an extreme Y coordinate range, removing"
 * </pre>
 * 越界即被 sable 直接删除 —— 表现就是"子层级生成不出来"。
 *
 * <p>另一头：小行星轨道以世界原点（太阳）为中心，y = r·sin(i)·sin(u) 关于 0 正负对称，
 * 单颗轨道在整个周期内既会到 +|y| 也会到 −|y|。因此可用的**对称**半幅不是 100000，
 * 而是 min(−remove_min, remove_max) 再扣掉结构半高余量。默认窗口下即 ≈ 9.5K 块。
 *
 * <p>数据包环带若请求更大的竖直范围，{@code AsteroidDataLoader} 会把倾角上限压到该约束内并告警；
 * 想要更厚的带，把 {@code run/config/sable-common.toml} 的 {@code sub_level_remove_min} 放宽
 * （例如 -100000），本类会自动读到新窗口（配置项 asteroidMaxAbsY = 0 即自动）。
 */
public final class AsteroidVerticalLimit {

    /** 结构半高 + 安全余量（块）：子层级包围盒必须整块落在窗口内。 */
    public static final double STRUCTURE_MARGIN = 512.0;

    /** sable 缺失时的假定窗口（= sable 默认值），用于纯服务端/测试环境。 */
    private static final double FALLBACK_MIN = -10_000.0;
    private static final double FALLBACK_MAX = 100_000.0;

    private AsteroidVerticalLimit() {}

    /**
     * 允许的 |轨道 Y| 上限（块，&gt; 0）。
     *
     * <p>配置 asteroidMaxAbsY &gt; 0 时直接采用；否则自动读取 sable 的
     * sub_level_remove_min/max（配置未加载或 sable 未安装时用默认窗口）。
     */
    public static double maxAbsY() {
        double configured = 0.0;
        try {
            configured = SpaceSimulationConfig.asteroidMaxAbsY.get();
        } catch (Throwable ignored) {
            // 配置尚未加载（极早期调用）——走自动
        }
        if (configured > 0.0) {
            return configured;
        }
        double[] window = sableWindow();
        double half = Math.min(-window[0], window[1]);
        return Math.max(0.0, half - STRUCTURE_MARGIN);
    }

    /** sable 的当前 Y 窗口 {min, max}（反射读取，sable 未安装时返回默认窗口）。 */
    public static double[] sableWindow() {
        try {
            Class<?> cfg = Class.forName("dev.ryanhcode.sable.SableConfig");
            Object minV = cfg.getField("SUB_LEVEL_REMOVE_MIN").get(null);
            Object maxV = cfg.getField("SUB_LEVEL_REMOVE_MAX").get(null);
            double min = ((Number) minV.getClass().getMethod("getAsDouble").invoke(minV)).doubleValue();
            double max = ((Number) maxV.getClass().getMethod("getAsDouble").invoke(maxV)).doubleValue();
            if (min < max) {
                return new double[]{min, max};
            }
        } catch (Throwable ignored) {
            // sable 未安装 / 类结构变化 —— 用默认窗口，不影响主流程
        }
        return new double[]{FALLBACK_MIN, FALLBACK_MAX};
    }

    /** 当前窗口的可读描述（用于日志/命令回显）。 */
    public static String describe() {
        double[] w = sableWindow();
        return String.format(java.util.Locale.ROOT,
                "sable Y 窗口 [%.0f, %.0f] → 允许 |轨道Y| ≤ %.0f 块",
                w[0], w[1], maxAbsY());
    }
}
