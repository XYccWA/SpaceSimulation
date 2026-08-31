package org.xyccwa.space_simulation.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.xyccwa.space_simulation.asteroid.AsteroidOrbit;
import org.xyccwa.space_simulation.asteroid.AsteroidUniverse;
import org.xyccwa.space_simulation.asteroid.AsteroidUniverseSource;

import java.util.Locale;

/**
 * /asteroid 命令 —— 程序化小行星系统的查询入口（当前仅质点查询，无任何表现形式）。
 *
 * 用法：
 *   /asteroid meta                系统概况（总数/半径/种子/μ/周期公式）
 *   /asteroid info <index>        按编号查询：六轨道根数 + 当前时刻的轨道量 + 世界坐标 + 与玩家距离
 */
public final class AsteroidCommand {

    private final AsteroidUniverse universe;

    public AsteroidCommand() {
        this.universe = AsteroidUniverseSource.fromConfig();
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
        );
    }

    private int meta(CommandSourceStack source) {
        AsteroidUniverse u = universe;
        send(source, "[小行星系统] 程序化生成 · 确定性 · 零存储 · 质点模型");
        send(source, String.format(Locale.ROOT, "  总数: %,d（编号 0 ~ %,d，按需 O(1) 派生，不占任何存储）",
                u.totalCount, u.totalCount - 1));
        send(source, String.format(Locale.ROOT, "  种子: %,d", u.seed));
        send(source, String.format(Locale.ROOT, "  环带半径: %.0f ~ %.0f 块（半长轴体积均匀采样）", u.innerRadius, u.outerRadius));
        send(source, String.format(Locale.ROOT, "  离心率: 0 ~ %.3f；倾角: 0 ~ %.1f°", u.maxEccentricity,
                Math.toDegrees(u.maxInclinationRad)));
        send(source, String.format(Locale.ROOT, "  引力参数 μ = %.4e 块³/tick²（内缘周期 %.0f tick 反推）",
                u.mu, orbitPeriodAt(u.innerRadius)));
        send(source, "  轨道运动: 开普勒三定律（T = 2π√(a³/μ)，E − e·sinE = M）。中心天体 = 世界原点（太阳）。");
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

    private double orbitPeriodAt(double a) {
        return 2.0 * Math.PI / Math.sqrt(universe.mu / (a * a * a));
    }

    private static void send(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }
}