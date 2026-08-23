package org.xyccwa.space_simulation.sable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;

/**
 * plotyard（sable 子层级实际方块存储区）放置策略。
 *
 * 目标：把 plotyard 从默认的 (10000, 10000) plot（≈ 20,480,000 块，f32 ULP=2 块，
 * 物理精度灾难性丢失）移动到太阳（世界原点球体）内部，同时**避开世界原点**：
 *
 * 1. 太阳内部：整个网格完全位于半径 {@link #MIN_SUN_RADIUS}（plotyardMinSunRadius
 *    配置，默认 200,000）的球内，任何世界（太阳 ≥ 最小）都不越界；
 * 2. 避开原点：网格块坐标从 2048 起（origin=1 plot），保证 (0,0,0) 不在 plotgrid 内。
 *    这是硬约束——多个 mod（simulated 的 PhysicsStaff 拖动 / PhysicsAssembler 装配）
 *    用 (0,0,0) 作为"世界锚点"哨兵传给约束配置，sable 的 validateAnchors 会拒绝
 *    "锚点在 plotgrid 内但 body 不是子层级"的约束（实测：plotyard 覆盖原点后，
 *    用航空学调试工具拖动子层级直接崩溃）。
 *
 * 数学：网格块范围 [2048, (1+2^L)·2048)，最远角点距离 = (1+2^L)·2048×√2，
 * 须 ≤ 设计半径；L 从 7 向下取满足条件最大值（默认 200,000 → L=6，4096 个 plot，
 * 角点 188,260 块，f32 ULP ≈ 0.03 块 ≈ 3 cm，较默认 2 块提升约 64 倍）。
 *
 * 说明：子层级方块的实际存储位置（plotyard）与玩家看到的逻辑位置（logicalPose）
 * 完全解耦（Pose3dc.transformPosition 编码映射），移动 plotyard 不影响飞船显示、
 * 玩家交互与存档（sable 以 plot 相对坐标持久化）。
 */
public final class PlotYardPlacement {

    private static final Logger LOGGER = LogManager.getLogger("space_simulation");

    /** 单 plot 边长（块）= 2^7 chunks × 16，sable DEFAULT_LOG_PLOT_SIZE = 7 */
    public static final int PLOT_BLOCK_SIZE = 2048;

    /** sable 默认网格 logSideLength 上限（2^7 × 2^7 = 16384 个 plot） */
    public static final int DEFAULT_LOG_SIDE_LENGTH = 7;

    /** 最小太阳半径设计基准（块）：plotyardMinSunRadius 配置默认值，用户指定 */
    public static final double MIN_SUN_RADIUS = 200_000.0;

    /** √2 预计算 */
    private static final double SQRT2 = Math.sqrt(2.0);

    /** 网格首个 plot 的全局坐标（1）：块坐标从 2048 起，避开世界原点 */
    private static final int ORIGIN = 1;

    /** 最小网格（L=1，2×2 plots）所需的最小设计半径：角点 ≈ 8,688 */
    private static final double MIN_RADIUS_FOR_PLACEMENT = (1.0 + (1L << 1)) * PLOT_BLOCK_SIZE * SQRT2;

    /** 放置结果快照（static 缓存：配置为 STARTUP 型，运行期不变） */
    private static Placement cached;

    private PlotYardPlacement() {
    }

    /**
     * 放置参数。
     *
     * @param logSideLength 网格对数边长（1..7）；applied=false 时为 0（无效）
     * @param originX       网格原点 plot 坐标（applied=false 时为 0）
     * @param originZ       网格原点 plot 坐标（applied=false 时为 0）
     * @param applied       false = 无法放入太阳（设计半径过小），调用方应保持 sable 默认参数
     */
    public record Placement(int logSideLength, int originX, int originZ, boolean applied) {
    }

    /**
     * @return 当前世界的 plotyard 放置参数（首次调用计算并缓存，多维度复用）
     */
    public static Placement current() {
        if (cached == null) {
            cached = compute();
        }
        return cached;
    }

    private static Placement compute() {
        // 太阳最小大小由 plotyardMinSunRadius 定义：网格必须完全位于该半径球内
        final double designRadius = SpaceSimulationConfig.plotyardMinSunRadius.get();
        final double sunRadius = SpaceSimulationConfig.solarKillRadius.get();

        if (designRadius < MIN_RADIUS_FOR_PLACEMENT) {
            LOGGER.warn("[PlotYard] plotyardMinSunRadius {} < minimum {} for the smallest grid (2×2 plots): keeping sable default plotyard (20,480,000 blocks).",
                    format(designRadius), format(MIN_RADIUS_FOR_PLACEMENT));
            return new Placement(0, 0, 0, false);
        }

        if (sunRadius <= 0) {
            LOGGER.warn("[PlotYard] solarKillRadius={} (sun disabled): plot grid is placed inside the design-minimum sun but the kill boundary is disabled.",
                    format(sunRadius));
        } else if (sunRadius < designRadius) {
            LOGGER.warn("[PlotYard] actual solarKillRadius={} < plotyardMinSunRadius={}: the plot grid fits the design-minimum sun but may extend past the actual sun boundary. Raise solarKillRadius to at least {} to guarantee the grid stays inside the actual sun.",
                    format(sunRadius), format(designRadius), format(designRadius));
        }

        // 网格块范围 [2048, (1+2^L)·2048)，角点距离 = (1+2^L)·2048×√2 ≤ designRadius
        int logSideLength = 0;
        for (int l = DEFAULT_LOG_SIDE_LENGTH; l >= 1; l--) {
            final double cornerDistance = (1.0 + (double) (1L << l)) * PLOT_BLOCK_SIZE * SQRT2;
            if (cornerDistance <= designRadius) {
                logSideLength = l;
                break;
            }
        }
        if (logSideLength == 0) {
            LOGGER.warn("[PlotYard] no grid size fits radius {}: keeping sable default plotyard.",
                    format(designRadius));
            return new Placement(0, 0, 0, false);
        }

        final long gridMax = (1L << logSideLength) * PLOT_BLOCK_SIZE + PLOT_BLOCK_SIZE; // 最大块坐标（开区间）
        final double cornerDistance = gridMax * SQRT2;

        LOGGER.info("[PlotYard] sun minimum={} (actual solarKillRadius={}) → logSideLength={}, origin=({}, {}) plot; "
                        + "grid block range [{}, {})×[{}, {}), farthest corner {} blocks from origin (design sun {}), "
                        + "world origin (0,0,0) excluded",
                format(designRadius), format(sunRadius),
                logSideLength, ORIGIN, ORIGIN,
                PLOT_BLOCK_SIZE, gridMax, PLOT_BLOCK_SIZE, gridMax,
                format(cornerDistance), format(designRadius));

        return new Placement(logSideLength, ORIGIN, ORIGIN, true);
    }

    private static String format(double v) {
        return String.format("%.1f", v);
    }
}
