package org.xyccwa.space_simulation.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.xyccwa.space_simulation.asteroid.AsteroidOrbit;
import org.xyccwa.space_simulation.asteroid.AsteroidProximity;
import org.xyccwa.space_simulation.asteroid.AsteroidProximityLoader;
import org.xyccwa.space_simulation.asteroid.AsteroidUniverse;
import org.xyccwa.space_simulation.asteroid.AsteroidUniverseSource;

import java.util.Locale;
import java.util.Set;

/**
 * /asteroid 命令 —— 程序化小行星系统的查询入口（当前仅质点查询，无任何表现形式）。
 *
 * 用法：
 *   /asteroid meta                系统概况（总数/环带/种子/μ/周期公式/网格结构）
 *   /asteroid info <index>        按编号查询：六轨道根数 + 当前时刻的轨道量 + 世界坐标 + 与玩家距离
 *   /asteroid near <radius>       一次性查询：玩家附近 radius 块内的全部小行星（精确距离过滤）
 *   /asteroid loader              加载逻辑实测：预加载索引（分帧）+ 强加载每 tick 检索状态
 */
public final class AsteroidCommand {

    private final AsteroidUniverse universe;

    /** 加载逻辑实例（懒创建，实测用）。 */
    private AsteroidProximityLoader loader;

    public AsteroidCommand() {
        this.universe = AsteroidUniverseSource.fromConfig();
    }

    private AsteroidProximityLoader loader() {
        if (loader == null) {
            loader = new AsteroidProximityLoader(universe, 10000.0, 2000.0, 600);
        }
        return loader;
    }

    public static void register(RegisterCommandsEvent event) {
        AsteroidCommand cmd = new AsteroidCommand();
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("asteroid")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("meta")
                                .executes(ctx -> cmd.meta(ctx.getSource())))
                        .then(Commands.literal("info")
                                .then(Commands.argument("index", LongArgumentType.longArg(0L))
                                        .executes(ctx -> cmd.info(ctx.getSource(),
                                                LongArgumentType.getLong(ctx, "index")))))
                        .then(Commands.literal("near")
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0))
                                        .executes(ctx -> cmd.near(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "radius")))))
                        .then(Commands.literal("loader")
                                .executes(ctx -> cmd.loader(ctx.getSource())))
        );
    }

    private int meta(CommandSourceStack source) {
        AsteroidUniverse u = universe;
        send(source, "[小行星系统] 程序化生成 · 确定性 · 零存储 · 质点模型");
        send(source, String.format(Locale.ROOT, "  总数: %,d（编号 0 ~ %,d，按需 O(1) 派生，不占任何存储）",
                u.totalCount, u.totalCount - 1));
        send(source, String.format(Locale.ROOT, "  网格: a档 %,d × e档 %,d × i档 %,d × Ω档 %,d = 轨道环 %,d；每环 %,d 颗（相位细分）",
                AsteroidUniverse.A_BINS, AsteroidUniverse.E_BINS, AsteroidUniverse.I_BINS,
                AsteroidUniverse.O_BINS, AsteroidUniverse.CELL_COUNT, u.k));
        send(source, String.format(Locale.ROOT, "  种子: %,d", u.seed));
        send(source, String.format(Locale.ROOT, "  环带半径: %.0f ~ %.0f 块（半长轴体积均匀采样）", u.innerRadius, u.outerRadius));
        send(source, String.format(Locale.ROOT, "  离心率: 0 ~ %.3f；倾角: 0 ~ %.1f°", u.maxEccentricity,
                Math.toDegrees(u.maxInclinationRad)));
        send(source, String.format(Locale.ROOT, "  引力参数 μ = %.4e 块³/tick²（内缘周期 %.0f tick 反推）",
                u.mu, orbitPeriodAt(u.innerRadius)));
        send(source, String.format(Locale.ROOT, "  档粒度设计基准: 预加载半径 %.0f 块（dA≈2×R、Ω档≈方位窗口尺寸）",
                AsteroidUniverse.PROBE_RADIUS));
        send(source, "  轨道运动: 开普勒三定律（T = 2π√(a³/μ)，E − e·sinE = M）。中心天体 = 世界原点（太阳）。");
        send(source, "  近邻检索: /asteroid near <radius>；加载逻辑: /asteroid loader（预载索引分帧 + 强载每 tick，实体化前置）。");
        return 1;
    }

    private int info(CommandSourceStack source, long index) {
        AsteroidUniverse u = universe;
        if (index < 0 || index >= u.totalCount) {
            source.sendFailure(Component.literal(String.format(Locale.ROOT,
                    "[小行星] 编号 %,d 超出范围（有效 0 ~ %,d）", index, u.totalCount - 1)));
            return 0;
        }
        AsteroidOrbit o = u.orbitOf(index);
        long tick = source.getLevel().getGameTime();
        double[] pos = o.positionAt(tick);
        double r = o.radiusAt(tick);

        send(source, String.format(Locale.ROOT, "[小行星 #%,d] 开普勒椭圆轨道", index));
        send(source, String.format(Locale.ROOT, "  半长轴 a    = %,.1f 块", o.a));
        send(source, String.format(Locale.ROOT, "  离心率 e    = %.4f", o.e));
        send(source, String.format(Locale.ROOT, "  轨道倾角 i  = %.2f°", Math.toDegrees(o.i)));
        send(source, String.format(Locale.ROOT, "  升交点经度 Ω= %.2f°", Math.toDegrees(o.omega)));
        send(source, String.format(Locale.ROOT, "  近心点幅角 ω= %.2f°", Math.toDegrees(o.argP)));
        send(source, String.format(Locale.ROOT, "  M0（t=0 平近点角）= %.2f°", Math.toDegrees(o.m0)));
        send(source, String.format(Locale.ROOT, "  周期 T      = %,.0f tick ≈ %,.1f 天（开普勒第三定律）",
                o.periodTicks(), o.periodTicks() / 24000.0));

        send(source, String.format(Locale.ROOT, "  当前游戏刻度 tick = %,d", tick));
        double M = o.meanAnomalyAt(tick);
        double E = AsteroidOrbit.solveKepler(M, o.e);
        double nu = AsteroidOrbit.trueAnomaly(o.e, E);
        send(source, String.format(Locale.ROOT,
                "  平近点角 M = %.2f°；偏近点角 E = %.2f°；真近点角 ν = %.2f°",
                Math.toDegrees(M), Math.toDegrees(E), Math.toDegrees(nu)));
        send(source, String.format(Locale.ROOT,
                "  距中心 r = %,.1f 块（范围 a(1±e): %,.1f ~ %,.1f）",
                r, o.a * (1 - o.e), o.a * (1 + o.e)));
        send(source, String.format(Locale.ROOT, "  轨道速度  v = %.4f 块/tick（vis-viva）", o.speedAt(tick)));
        send(source, String.format(Locale.ROOT,
                "  世界坐标  (x=%,.1f, y=%,.1f, z=%,.1f)", pos[0], pos[1], pos[2]));
        send(source, String.format(Locale.ROOT, "  类型 type=“%s” 变体 variant=%d（实体化预留，当前空值）",
                o.type, o.variant));

        if (source.getEntity() != null) {
            double dx = source.getEntity().getX() - pos[0];
            double dy = source.getEntity().getY() - pos[1];
            double dz = source.getEntity().getZ() - pos[2];
            send(source, String.format(Locale.ROOT, "  与玩家距离 = %,.1f 块", Math.sqrt(dx * dx + dy * dy + dz * dz)));
        } else {
            send(source, "  与玩家距离 = （无执行实体）");
        }
        return 1;
    }

    /** 一次性附近查询：玩家周围 radius 块内全部小行星（精确）。 */
    private int near(CommandSourceStack source, double radius) {
        if (source.getEntity() == null) {
            source.sendFailure(Component.literal("[小行星] near 需要由玩家（或实体）执行"));
            return 0;
        }
        AsteroidUniverse u = universe;
        long tick = source.getLevel().getGameTime();
        double px = source.getEntity().getX();
        double py = source.getEntity().getY();
        double pz = source.getEntity().getZ();
        long t0 = System.nanoTime();
        Set<Long> ids = AsteroidProximity.nearby(u, px, py, pz, radius, tick);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        send(source, String.format(Locale.ROOT,
                "[小行星 near] 玩家 (%,.1f, %,.1f, %,.1f) 半径 %.0f 块内命中 %,d 颗（耗时 %d ms，tick=%,d）",
                px, py, pz, radius, ids.size(), ms, tick));
        int shown = 0;
        for (long id : ids) {
            if (shown >= 20) {
                send(source, "...（其余省略）");
                break;
            }
            double[] p = u.orbitOf(id).positionAt(tick);
            double dx = p[0] - px, dy = p[1] - py, dz = p[2] - pz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            send(source, String.format(Locale.ROOT, "  #%,d  距离 %,.1f 块  位置 (%,.1f, %,.1f, %,.1f)",
                    id, dist, p[0], p[1], p[2]));
            shown++;
        }
        return 1;
    }

    /** 加载逻辑实测：执行一次 loader.update 并输出索引/强载/耗时状态。 */
    private int loader(CommandSourceStack source) {
        if (source.getEntity() == null) {
            source.sendFailure(Component.literal("[小行星] loader 需要由玩家（或实体）执行"));
            return 0;
        }
        AsteroidProximityLoader L = loader();
        long tick = source.getLevel().getGameTime();
        double px = source.getEntity().getX();
        double py = source.getEntity().getY();
        double pz = source.getEntity().getZ();
        L.update(px, py, pz, tick);

        long[] inPre = L.pollEnteredPreload();
        long[] leftPre = L.pollLeftPreload();
        long[] inStr = L.pollEnteredStrong();
        long[] leftStr = L.pollLeftStrong();

        send(source, "[小行星 loader] 加载逻辑（无实体化）：预加载索引（分帧检索）+ 强加载每 tick 检索");
        send(source, String.format(Locale.ROOT,
                "  预加载范围 %.0f 块 / 强加载范围 %.0f 块（强加载 < 预加载）", L.preloadRadius, L.strongRadius));
        send(source, String.format(Locale.ROOT,
                "  预载索引: %d 个轨道环（真相交环），当前预载颗数约 %,d", L.preloadCellCount(), L.preloadSet().size()));
        send(source, String.format(Locale.ROOT,
                "  强加载: 当前 %.0f 块内 %,d 颗", L.strongRadius, L.strongCount()));
        send(source, String.format(Locale.ROOT,
                "  本次 update 耗时 %d ms（含重建分摊，索引重建按分帧 FRAME=%d 不阻塞主线程）",
                L.lastUpdateNs() / 1_000_000, AsteroidProximityLoader.FRAME));
        send(source, String.format(Locale.ROOT,
                "  进出事件 预载进 %d / 预载出 %d / 强载进 %d / 强载出 %d",
                inPre.length, leftPre.length, inStr.length, leftStr.length));
        send(source, String.format(Locale.ROOT,
                "  下次定期预载检索: %d tick 后（间隔 %d tick）", L.ticksUntilNextPreload(tick), L.preloadIntervalTicks));
        return 1;
    }

    private double orbitPeriodAt(double a) {
        return 2.0 * Math.PI / Math.sqrt(universe.mu / (a * a * a));
    }

    private static void send(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }
}