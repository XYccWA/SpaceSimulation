package org.xyccwa.space_simulation.interaction;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.xyccwa.space_simulation.util.FlightPhysics;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 交互判定的时间轴数据：**服务端权威位置历史** + **客户端最近上报位置**。
 *
 * <p>为什么需要：客户端本地预测永远领先服务端权威位置若干 tick（上行包要等服务器下一次 tick
 * 才被处理，服务器往往在处理包之前已经跑过本 tick 的玩家 tick）。小行星以约 2.4 格/tick 移动，
 * 于是服务端眼位与"玩家点击方块时所处的位置"相差数格，sable 的交互距离判定因此失败。
 *
 * <p><b>方向要点</b>：客户端位置在服务端的**未来**，所以权威位置历史（过去）里找不到对应行 ——
 * 回溯历史无法还原它。因此判定采用"客户端上报位置做几何 + 权威数据做钳制"：
 * 客户端位置只被接受当它
 * <ol>
 *   <li>足够新鲜（不超过 {@link #CLIENT_SAMPLE_TTL_TICKS} tick）；且</li>
 *   <li>与服务端权威位置的偏差不超过 {@code maxLag × MAX_SPEED}（等价于"最多允许超前这么多 tick"）。</li>
 * </ol>
 * 这样客户端无法靠伪造坐标凭空拉近与方块的距离（偏差上限把它锁在玩家真实位置附近），
 * 同时判定几何与玩家画面/准星严格一致。
 */
public final class InteractionTimeline {

    /** 环形历史长度（tick）：只用于求"最新权威位置"与偏差钳制。 */
    private static final int HISTORY = 64;

    /** 客户端上报位置的有效期（tick）：超过就认为没有可用的客户端样本。 */
    private static final long CLIENT_SAMPLE_TTL_TICKS = 20L;

    private static final Map<UUID, Entry> ENTRIES = new ConcurrentHashMap<>();

    private InteractionTimeline() {
    }

    private static final class Entry {
        final long[] ticks = new long[HISTORY];
        final double[][] positions = new double[HISTORY][];
        long clientTick = Long.MIN_VALUE;
        double[] clientPosition;
    }

    private static Entry entry(Player player) {
        return ENTRIES.computeIfAbsent(player.getUUID(), k -> new Entry());
    }

    /** 服务端每 tick 调用一次：记录权威脚部位置。 */
    public static void recordAuthoritative(ServerPlayer player) {
        try {
            if (player.level() == null) {
                return;
            }
            final Entry e = entry(player);
            final long tick = player.level().getGameTime();
            final int idx = (int) (tick & (HISTORY - 1));
            e.ticks[idx] = tick;
            e.positions[idx] = new double[]{player.getX(), player.getY(), player.getZ()};
        } catch (Throwable ignored) {
            // 时间轴数据只是优化；失败就让判定退回原行为
        }
    }

    /** 收到客户端移动包时调用：缓存它上报的位置（仅含位置的包）。 */
    public static void recordClientPosition(ServerPlayer player, ServerboundMovePlayerPacket packet) {
        try {
            if (player == null || packet == null || !packet.hasPosition() || player.level() == null) {
                return;
            }
            final Entry e = entry(player);
            e.clientTick = player.level().getGameTime();
            e.clientPosition = new double[]{
                    packet.getX(player.getX()),
                    packet.getY(player.getY()),
                    packet.getZ(player.getZ())
            };
        } catch (Throwable ignored) {
            // 同上
        }
    }

    /** 断线/换维度时清理。 */
    public static void forget(@Nullable Player player) {
        if (player != null) {
            ENTRIES.remove(player.getUUID());
        }
    }

    /**
     * 取客户端时间轴样本（即使不可信也返回，便于诊断；只有完全没有上报时才返回 null）。
     *
     * @param maxLag 允许的超前上限（tick 当量，按 {@link FlightPhysics#MAX_SPEED} 折算成格）
     */
    public static @Nullable ClientSample clientSample(ServerPlayer player, int maxLag) {
        try {
            if (player.level() == null) {
                return null;
            }
            final Entry e = ENTRIES.get(player.getUUID());
            if (e == null || e.clientPosition == null) {
                return null;
            }
            final long now = player.level().getGameTime();
            final long age = now - e.clientTick;
            final double[] auth = latestPosition(e, now);
            if (auth == null) {
                return null; // 还没有权威历史（玩家刚进来/不在轨道模拟中）
            }
            final double[] c = e.clientPosition;
            final double dx = c[0] - auth[0];
            final double dy = c[1] - auth[1];
            final double dz = c[2] - auth[2];
            final double deviation = Math.sqrt(dx * dx + dy * dy + dz * dz);
            final double limit = Math.max(0, maxLag) * FlightPhysics.MAX_SPEED;
            final boolean trusted = age >= 0 && age <= CLIENT_SAMPLE_TTL_TICKS && deviation <= limit;
            return new ClientSample(c[0], c[1], c[2], deviation, (int) age, limit, trusted);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 历史里 tick ≤ now 的最新一行。 */
    @Nullable
    private static double[] latestPosition(Entry e, long now) {
        double[] best = null;
        long bestTick = Long.MIN_VALUE;
        for (int i = 0; i < HISTORY; i++) {
            final double[] p = e.positions[i];
            if (p == null) {
                continue;
            }
            final long t = e.ticks[i];
            if (t > now || t <= bestTick) {
                continue;
            }
            bestTick = t;
            best = p;
        }
        return best;
    }

    /**
     * 客户端上报的脚部位置 + 它与服务端权威位置的偏差。
     *
     * @param deviation 与最新权威位置的直线距离（格）
     * @param ageTicks  该上报距今天的 tick 数
     * @param limit     本次允许的偏差上限（格）
     * @param trusted   是否通过新鲜度与偏差钳制（不可信时判定不得采信，但仍可用于诊断）
     */
    public record ClientSample(double x, double y, double z, double deviation, int ageTicks, double limit,
                               boolean trusted) {
    }
}
