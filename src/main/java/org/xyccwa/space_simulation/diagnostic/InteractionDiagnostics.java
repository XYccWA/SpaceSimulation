package org.xyccwa.space_simulation.diagnostic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.xyccwa.space_simulation.SpaceSimulation;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子层级交互延迟补偿的日志（只读）。
 *
 * <p>补偿本身见 {@code SubLevelInteractionLagCompensation}。这里在"服务端当刻判定失败、需要补偿"
 * 时留一行证据：当刻距离、客户端位置相对权威位置的偏差与新鲜度、还原出的距离、是否放行。
 * 无论补偿成败都会打印，便于区分"没有客户端位置"、"偏差超限"和"还原后仍然太远"三种情况。
 * 同一玩家、同一方块每 {@link #REPEAT_TICKS} tick 只打印一次（持续按住鼠标时判定会每 tick 调用）。
 */
public final class InteractionDiagnostics {

    /** 同一玩家、同一方块位置在这个 tick 数内只打印一次。 */
    private static final long REPEAT_TICKS = 40L;

    private static final Map<UUID, BlockPos> LAST_POS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();

    private InteractionDiagnostics() {
    }

    /**
     * 服务端当刻判定失败时调用。
     *
     * @param clientDistance 用客户端上报位置还原出的距离；{@code NaN} = 没有采信客户端位置
     * @param ageTicks       客户端位置距今 tick 数；负数 = 完全没有上报
     * @param deviation      客户端位置与服务端权威位置的偏差（格）
     * @param limit          本次允许的偏差上限（格）
     */
    public static void reportLagAttempt(Player player, BlockPos pos, double currentDistance, double range,
                                        boolean allowed, double clientDistance, int ageTicks,
                                        double deviation, double limit) {
        try {
            final Level level = player.level();
            if (level == null || level.isClientSide()) {
                return;
            }
            final long tick = level.getGameTime();
            final UUID id = player.getUUID();
            final Long lastTick = LAST_TICK.get(id);
            final BlockPos lastPos = LAST_POS.get(id);
            if (lastTick != null && pos.equals(lastPos) && tick - lastTick < REPEAT_TICKS) {
                return;
            }
            LAST_TICK.put(id, tick);
            LAST_POS.put(id, pos.immutable());

            final String head = String.format(Locale.ROOT,
                    "[InteractLag] player=%s pos=(%d,%d,%d) 当刻距离=%s 阈值=%s",
                    player.getName().getString(), pos.getX(), pos.getY(), pos.getZ(),
                    fmt(currentDistance), fmt(range));

            if (ageTicks < 0 || Double.isNaN(deviation)) {
                SpaceSimulation.LOGGER.info("{} | 补偿不可用：没有可用的客户端位置上报", head);
                return;
            }
            if (Double.isNaN(clientDistance)) {
                SpaceSimulation.LOGGER.info(
                        "{} | 补偿不可用：客户端位置偏差={}格 age={}tick 超过上限={}格",
                        head, fmt(deviation), ageTicks, fmt(limit));
                return;
            }
            SpaceSimulation.LOGGER.info(
                    "{} | 客户端时间轴：偏差={}格 age={}tick 上限={}格 还原距离={} 放行={}",
                    head, fmt(deviation), ageTicks, fmt(limit), fmt(clientDistance), allowed);
        } catch (Throwable ignored) {
            // 日志永远不允许影响游戏
        }
    }

    /** 断线/换维度时清理节流状态。 */
    public static void forget(Player player) {
        if (player == null) {
            return;
        }
        LAST_POS.remove(player.getUUID());
        LAST_TICK.remove(player.getUUID());
    }

    private static String fmt(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return String.valueOf(v);
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
