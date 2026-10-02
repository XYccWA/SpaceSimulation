package org.xyccwa.space_simulation.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.xyccwa.space_simulation.orbital.Gravity;
import org.xyccwa.space_simulation.orbital.PlayerOrbitServer;
import org.xyccwa.space_simulation.orbital.TwoBody;

import java.util.Locale;

/**
 * /orbit 命令 —— 玩家轨道力学的查询与调试入口。
 *
 *   /orbit info                            当前状态：μ、半径、速度、六根数、周期、近/远心点
 *   /orbit circular &lt;radius&gt; [inclDeg]    把执行者放进该半径的圆轨道（倾角默认 0，即赤道面）
 *   /orbit mu                              当前引力参数（配置 / 服务端下发后）
 *
 * circular 只用于测试与观察：它把玩家放到 (radius, 0, 0) 并给出垂直于半径的圆轨道速度
 * sqrt(μ/r)，方向取 +Z（倾角 0 时即赤道面顺行）。
 */
public final class OrbitCommand {

    private OrbitCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("orbit")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("info")
                                .executes(ctx -> info(ctx.getSource())))
                        .then(Commands.literal("mu")
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                                            "轨道引力参数 μ = %.6e 块³/tick²（%s）",
                                            Gravity.mu(), Gravity.enabled() ? "启用" : "禁用")), false);
                                    return 1;
                                }))
                        .then(Commands.literal("circular")
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(2.0, 1.0e9))
                                        .executes(ctx -> circular(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "radius"), 0.0))
                                        .then(Commands.argument("inclinationDeg", DoubleArgumentType.doubleArg(-90.0, 90.0))
                                                .executes(ctx -> circular(ctx.getSource(),
                                                        DoubleArgumentType.getDouble(ctx, "radius"),
                                                        DoubleArgumentType.getDouble(ctx, "inclinationDeg")))))));
    }

    private static int info(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("该命令只能由玩家执行"));
            return 0;
        }
        double mu = Gravity.mu();
        double[] r = {player.getX(), player.getY(), player.getZ()};
        double[] v = {player.getDeltaMovement().x, player.getDeltaMovement().y, player.getDeltaMovement().z};
        double radius = Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]);
        double speed = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "μ = %.4e 块³/tick²　半径 r = %.0f 块　速度 = %.4f 块/tick（%.1f m/s）",
                mu, radius, speed, speed * 20.0)), false);
        if (mu <= 0.0) return 1;
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "该半径处：圆轨道速度 %.4f　逃逸速度 %.4f 块/tick",
                Math.sqrt(mu / Math.max(1.0, radius)), Math.sqrt(2.0 * mu / Math.max(1.0, radius)))), false);

        TwoBody.Elements el = TwoBody.fromState(r, v, mu);
        if (!el.valid) {
            source.sendSuccess(() -> Component.literal("当前状态是退化轨道（径向/抛物线/原点），无法给出根数"), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "轨道：%s　a = %.0f　e = %.4f　i = %.2f°　Ω = %.2f°　ω = %.2f°",
                el.hyperbolic ? "双曲（逃逸）" : "椭圆",
                el.a, el.e, Math.toDegrees(el.inclination), Math.toDegrees(el.raan), Math.toDegrees(el.argPeriapsis))), false);
        if (!el.hyperbolic) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "近心点 %.0f 块　远心点 %.0f 块　周期 %.0f tick（%.2f 小时）",
                    el.periapsis(), el.apoapsis(), el.periodTicks(mu), el.periodTicks(mu) / 72000.0)), false);
        }
        return 1;
    }

    private static int circular(CommandSourceStack source, double radius, double inclinationDeg) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("该命令只能由玩家执行"));
            return 0;
        }
        if (!Gravity.enabled()) {
            source.sendFailure(Component.literal("轨道力学未启用（配置 Orbital Mechanics）"));
            return 0;
        }
        if (player.serverLevel().dimension() != Level.OVERWORLD) {
            source.sendFailure(Component.literal("轨道力学只在主世界生效"));
            return 0;
        }
        double mu = Gravity.mu();
        double inc = Math.toRadians(inclinationDeg);
        double speed = Math.sqrt(mu / radius);
        // 升交点在 +X 轴、倾角从赤道面（XZ 平面）量起：位置 (R,0,0)，
        // i=0 时速度沿 +Z（赤道面顺行，y 恒定不变）；i=90° 时速度沿 -Y（过极点，y 才会来回变）。
        double vx = 0.0;
        double vy = -speed * Math.sin(inc);
        double vz = speed * Math.cos(inc);
        PlayerOrbitServer.placeAt(player, radius, 0.0, 0.0, vx, vy, vz);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "已进入圆轨道：r = %.0f 块　v = %.4f 块/tick（%.1f m/s）　倾角 %.1f°　周期 %.0f tick",
                radius, speed, speed * 20.0, inclinationDeg,
                2 * Math.PI * Math.sqrt(radius * radius * radius / mu))), true);
        return 1;
    }
}
