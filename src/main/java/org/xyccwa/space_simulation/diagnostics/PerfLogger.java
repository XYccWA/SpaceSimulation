package org.xyccwa.space_simulation.diagnostics;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.asteroid.entity.AsteroidEntityifier;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;

/**
 * 低频性能统计（默认每 1200 tick = 60 秒一行）。
 *
 * 由来：太空场景里服务器 tick 常常"没慢到触发原版 Can't keep up（落后 2 秒）"、客户端帧也没慢到
 * 触发 profiler 警告（100 ms），日志里因此**完全没有证据可查**——玩家明显感到不流畅，但任何一份
 * 日志看上去都是正常的。这里补上唯一缺的量化指标：**服务器 tick 的计算耗时**（Pre→Post，不含 tick
 * 循环的 sleep）与窗口内峰值，外加实体 / 区块 / 活动小行星 / 堆内存，用来判断服务器是否已经贴着
 * 50 ms 的预算在跑（例如 avg 35 ms 就意味着只剩 30% 余量，任何一次实体化都会把帧节奏打乱）。
 *
 * 开关：space_simulation-startup.toml → ["Diagnostics"] perfStatsEnabled（默认开，60 秒一行）。
 */
public final class PerfLogger {

    /** 统计窗口长度（tick）；1200 tick = 60 秒 */
    private static final int WINDOW_TICKS = 1200;

    /** 本 tick 的计算起点（Pre 事件写入） */
    private static long tickStartNanos;

    /** 本窗口的起点（首个 Post 事件写入） */
    private static long windowStartNanos;

    /** 本窗口内全部 tick 的计算耗时累计与最大值 */
    private static long sumNanos;
    private static long maxNanos;

    /** 本窗口已统计的 tick 数 */
    private static int ticks;

    private PerfLogger() {
    }

    /** 服务器 tick 开始：记录起点，Post 时相减得到本 tick 的服务器计算耗时（不含 sleep） */
    public static void onTickPre(ServerTickEvent.Pre event) {
        tickStartNanos = System.nanoTime();
    }

    /** 服务器 tick 结束：累计耗时；窗口满则输出一行并复位 */
    public static void onTickPost(ServerTickEvent.Post event) {
        if (!SpaceSimulationConfig.perfStatsEnabled.get()) {
            return;
        }
        final long now = System.nanoTime();
        if (tickStartNanos != 0L) {
            final long elapsed = now - tickStartNanos;
            tickStartNanos = 0L;
            sumNanos += elapsed;
            if (elapsed > maxNanos) {
                maxNanos = elapsed;
            }
        }
        if (windowStartNanos == 0L) {
            windowStartNanos = now;
            return;
        }
        if (++ticks < WINDOW_TICKS) {
            return;
        }

        final double seconds = (now - windowStartNanos) / 1.0e9;
        final double avgMs = sumNanos / 1.0e6 / ticks;
        final double maxMs = maxNanos / 1.0e6;
        final double tps = ticks / Math.max(1.0e-9, seconds);

        final MinecraftServer server = event.getServer();
        final ServerLevel level = server.overworld();
        int asteroids = -1;
        int entities = -1;
        int chunks = -1;
        try {
            asteroids = AsteroidEntityifier.all(level).size();
            int counted = 0;
            for (final Entity ignored : level.getAllEntities()) {
                counted++;
            }
            entities = counted;
            chunks = level.getChunkSource().getLoadedChunksCount();
        } catch (final Throwable ignored) {
            // 统计失败不能影响 tick 本身
        }

        final Runtime rt = Runtime.getRuntime();
        SpaceSimulation.LOGGER.info(
                "[perf] {} ticks in {} s ({} tps): server tick avg {} ms / max {} ms | overworld entities {} chunks {} asteroids {} | heap {} / {} MB",
                ticks,
                String.format("%.1f", seconds),
                String.format("%.2f", tps),
                String.format("%.2f", avgMs),
                String.format("%.2f", maxMs),
                entities,
                chunks,
                asteroids,
                (rt.totalMemory() - rt.freeMemory()) / 1048576L,
                rt.maxMemory() / 1048576L);

        windowStartNanos = now;
        sumNanos = 0L;
        maxNanos = 0L;
        ticks = 0;
    }
}
