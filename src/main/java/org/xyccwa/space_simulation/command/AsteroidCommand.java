package org.xyccwa.space_simulation.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.asteroid.AsteroidOrbit;
import org.xyccwa.space_simulation.asteroid.AsteroidProximity;
import org.xyccwa.space_simulation.asteroid.AsteroidProximityLoader;
import org.xyccwa.space_simulation.asteroid.AsteroidProximityService;
import org.xyccwa.space_simulation.asteroid.AsteroidUniverse;
import org.xyccwa.space_simulation.asteroid.AsteroidVerticalLimit;
import org.xyccwa.space_simulation.asteroid.data.AsteroidCatalog;
import org.xyccwa.space_simulation.asteroid.data.AsteroidDataLoader;
import org.xyccwa.space_simulation.asteroid.entity.AsteroidEntityifier;
import org.xyccwa.space_simulation.asteroid.entity.AsteroidEntityifyService;

import java.util.Locale;
import java.util.Set;

/**
 * /asteroid 命令 —— 程序化小行星系统的查询与实体化入口。
 *
 * 宇宙来自数据包（asteroid_belt / asteroid_type），每次执行时实时读取当前 catalog，
 * 因此 /reload 后命令立即反映新数据，不存在过期快照。
 *
 * 用法：
 *   /asteroid meta                系统概况（环带 / 类型 / 总数 / 种子 / μ / 数据包状态）
 *   /asteroid belts               环带列表（每带的半径、倾角、档数、总量、类型权重）
 *   /asteroid info &lt;index&gt;        按编号查询：所属带 + 类型/变体/结构 + 六根数 + 世界坐标
 *   /asteroid near &lt;radius&gt;       一次性查询：玩家附近 radius 块内的全部小行星
 *   /asteroid loader              加载逻辑实测：预加载索引（分帧）+ 强加载每 tick 检索状态
 *   /asteroid spawn &lt;index&gt;       实体化一颗小行星（sable 子层级），并回显 plot/位姿/包围盒
 *   /asteroid tp &lt;index&gt;          实体化并传送到该小行星附近（径向偏 80 格）观察
 *   /asteroid entity              当前实体化状态（数量 / 上限 / 每颗的 plot 与包围盒）
 *   /asteroid reload              手动重载数据包（与 /reload 同源）并回显解析诊断
 */
public final class AsteroidCommand {

    /** 每次执行时实时取当前宇宙（数据包 /reload 后立即生效）。 */
    private AsteroidUniverse u() {
        return AsteroidProximityService.universe();
    }

    /** 加载器单例（与 AsteroidProximityService 每 tick 驱动的为同一实例，命令只读状态）。 */
    private AsteroidProximityLoader loader() {
        return AsteroidProximityService.loader();
    }

    public static void register(RegisterCommandsEvent event) {
        AsteroidCommand cmd = new AsteroidCommand();
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("asteroid")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("meta")
                                .executes(ctx -> cmd.meta(ctx.getSource())))
                        .then(Commands.literal("belts")
                                .executes(ctx -> cmd.belts(ctx.getSource())))
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
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("index", LongArgumentType.longArg(0L))
                                        .executes(ctx -> cmd.spawn(ctx.getSource(),
                                                LongArgumentType.getLong(ctx, "index")))))
                        .then(Commands.literal("tp")
                                .then(Commands.argument("index", LongArgumentType.longArg(0L))
                                        .executes(ctx -> cmd.tp(ctx.getSource(),
                                                LongArgumentType.getLong(ctx, "index")))))
                        .then(Commands.literal("entity")
                                .executes(ctx -> cmd.entity(ctx.getSource())))
                        .then(Commands.literal("reload")
                                .executes(ctx -> cmd.reload(ctx.getSource())))
                        .then(Commands.literal("selftest")
                                .executes(ctx -> cmd.selftest(ctx.getSource())))
        );
    }

    private int meta(CommandSourceStack source) {
        AsteroidUniverse u = u();
        AsteroidCatalog c = AsteroidProximityService.catalog();
        send(source, "[小行星系统] 程序化生成 · 确定性 · 零存储 · 质点模型 · 数据包驱动");
        send(source, String.format(Locale.ROOT, "  总数: %,d（编号 0 ~ %,d，按需 O(1) 派生，不占任何存储）",
                u.totalCount, u.totalCount - 1));
        send(source, String.format(Locale.ROOT, "  环带: %d 个（来源: %s）",
                u.belts.length, c.fallback ? "内置回退" : "数据包"));
        send(source, String.format(Locale.ROOT, "  数据包: 环带文件 %d / 类型文件 %d / 警告 %d 条（/asteroid reload 手动重载）",
                c.beltFileCount, c.typeFileCount, c.errors.size()));
        for (AsteroidUniverse.Belt b : u.belts) {
            send(source, String.format(Locale.ROOT,
                    "    [%d] %s  %.0f ~ %.0f 块 · 倾角 %.2f°~%.2f° · e≤%.3f · a档 %d · 每环 %,d 颗 · 共 %,d 颗",
                    b.index, b.name, b.innerRadius, b.outerRadius,
                    Math.toDegrees(b.minInclinationRad), Math.toDegrees(b.maxInclinationRad),
                    b.maxEccentricity, b.aBins, b.k, b.totalCount));
        }
        send(source, String.format(Locale.ROOT, "  类型: %d 种（数据包 asteroid_type）", u.types.size));
        for (int i = 0; i < u.types.size; i++) {
            send(source, String.format(Locale.ROOT, "    %s（%s）· 变体 %d 个",
                    u.types.ids[i], u.types.names[i], u.types.variantStructures[i].length));
        }
        send(source, String.format(Locale.ROOT, "  种子: %,d", u.seed));
        send(source, String.format(Locale.ROOT, "  引力参数 μ = %.4e 块³/tick²（全局内缘周期 %,d tick 反推）",
                u.mu, u.globalInnerOrbitPeriodTicks));
        send(source, String.format(Locale.ROOT, "  档粒度设计基准: 预加载半径 %.0f 块（每带 a 档按跨度/2R 取整）",
                AsteroidUniverse.PROBE_RADIUS));
        send(source, "  轨道运动: 开普勒三定律（T = 2π√(a³/μ)，E − e·sinE = M）。中心天体 = 世界原点（太阳）。");
        if (!c.errors.isEmpty()) {
            send(source, String.format(Locale.ROOT, "  数据包警告 %d 条（详见日志 [小行星数据包]）:", c.errors.size()));
            int shown = 0;
            for (String e : c.errors) {
                if (shown >= 5) { send(source, "    ...（其余省略）"); break; }
                send(source, "    " + e);
                shown++;
            }
        }
        send(source, "  检索: /asteroid near <radius>；加载: /asteroid loader；环带: /asteroid belts");
        return 1;
    }

    /**
     * 服务端自检：把系统概况 / 类型表 / 每带样本轨道 / 每带一次球检索结果同时写入日志。
     * 不依赖玩家实体，可由控制台、命令方块或数据包 load 函数调用，便于自动化验证与排障。
     */
    private int selftest(CommandSourceStack source) {
        AsteroidUniverse u = u();
        AsteroidCatalog c = AsteroidProximityService.catalog();
        logBoth(source, "=== asteroid selftest begin ===");
        logBoth(source, String.format(Locale.ROOT,
                "source=%s beltFiles=%d typeFiles=%d belts=%d types=%d total=%,d warnings=%d",
                c.fallback ? "fallback" : "datapack", c.beltFileCount, c.typeFileCount,
                u.belts.length, u.types.size, u.totalCount, c.errors.size()));
        for (AsteroidUniverse.Belt b : u.belts) {
            logBoth(source, String.format(Locale.ROOT,
                    "belt[%d] %s r=%.0f~%.0f i=%.2f~%.2f deg e<=%.3f aBins=%d k=%,d cells=%,d count=%,d id=[%,d,%,d] mu=%.4e types=%d %s",
                    b.index, b.name, b.innerRadius, b.outerRadius,
                    Math.toDegrees(b.minInclinationRad), Math.toDegrees(b.maxInclinationRad),
                    b.maxEccentricity, b.aBins, b.k, b.cellCount, b.totalCount,
                    b.idBase, b.idBase + b.totalCount - 1, b.mu, b.typeIndices.length, b.periodTicksText()));
        }
        for (int i = 0; i < u.types.size; i++) {
            logBoth(source, String.format(Locale.ROOT, "type[%d] %s name=%s variants=%d",
                    i, u.types.ids[i], u.types.names[i], u.types.variantStructures[i].length));
        }
        // 样本轨道：全局首颗、每个带的首/末颗、全局末颗（去重）
        java.util.LinkedHashSet<Long> samples = new java.util.LinkedHashSet<>();
        samples.add(0L);
        for (AsteroidUniverse.Belt b : u.belts) {
            samples.add(b.idBase);
            samples.add(b.idBase + b.totalCount - 1L);
        }
        samples.add(u.totalCount - 1L);
        for (long id : samples) {
            AsteroidOrbit o = u.orbitOf(id);
            logBoth(source, String.format(Locale.ROOT,
                    "sample id=%,d belt=%d type=%s variant=%d structure=%s a=%,.1f e=%.4f i=%.2f deg Omega=%.2f deg T=%,.0f tick",
                    id, o.beltIndex, o.type, o.variant, o.structure,
                    o.a, o.e, Math.toDegrees(o.i), Math.toDegrees(o.omega), o.periodTicks()));
        }
        // 检索自检：每个带内缘处做一次半径 2000 的球查询
        long tick = source.getLevel().getGameTime();
        for (AsteroidUniverse.Belt b : u.belts) {
            long t0 = System.nanoTime();
            Set<Long> ids = AsteroidProximity.nearby(u, b.innerRadius, 0.0, 0.0, 2000.0, tick);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            logBoth(source, String.format(Locale.ROOT,
                    "near @(%.0f,0,0) r=2000 belt=%s hits=%,d time=%dms",
                    b.innerRadius, b.name, ids.size(), ms));
            int shown = 0;
            for (long id : ids) {
                if (shown++ >= 5) break;
                AsteroidOrbit o = u.orbitOf(id);
                double[] p = o.positionAt(tick);
                logBoth(source, String.format(Locale.ROOT,
                        "  hit id=%,d type=%s variant=%d structure=%s pos=(%,.1f,%,.1f,%,.1f)",
                        id, o.type, o.variant, o.structure, p[0], p[1], p[2]));
            }
        }
        logBoth(source, "=== asteroid selftest end ===");
        return 1;
    }

    /** 同时回显给命令源并写入日志（函数/控制台调用时日志是唯一可见出口）。 */
    private static void logBoth(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
        SpaceSimulation.LOGGER.info("[小行星自检] {}", text);
    }

    /** 手动重载数据包（与 /reload 同源，但立即回显解析诊断）。 */    private int reload(CommandSourceStack source) {
        AsteroidCatalog c = AsteroidDataLoader.loadNow(source.getServer().getResourceManager());
        AsteroidUniverse u = c.universe;
        send(source, String.format(Locale.ROOT,
                "[小行星 reload] 来源=%s · 环带文件 %d / 类型文件 %d · 环带 %d 个 · 总数 %,d",
                c.fallback ? "内置回退" : "数据包", c.beltFileCount, c.typeFileCount,
                u.belts.length, u.totalCount));
        for (AsteroidUniverse.Belt b : u.belts) {
            send(source, String.format(Locale.ROOT, "  [%d] %s  %.0f ~ %.0f 块 · 类型 %d 项 · 共 %,d 颗",
                    b.index, b.name, b.innerRadius, b.outerRadius, b.typeIndices.length, b.totalCount));
        }
        if (c.errors.isEmpty()) {
            send(source, "  无警告");
        } else {
            send(source, String.format(Locale.ROOT, "  警告 %d 条:", c.errors.size()));
            int shown = 0;
            for (String e : c.errors) {
                if (shown >= 10) { send(source, "    ...（其余见日志 [小行星数据包]）"); break; }
                send(source, "    " + e);
                shown++;
            }
        }
        return 1;
    }

    private int belts(CommandSourceStack source) {        AsteroidUniverse u = u();
        send(source, String.format(Locale.ROOT, "[小行星 belts] 共 %d 个环带（内缘半径升序 = 编号空间顺序）", u.belts.length));
        for (AsteroidUniverse.Belt b : u.belts) {
            send(source, String.format(Locale.ROOT,
                    "  [%d] %s  半径 %.0f ~ %.0f 块（%s）",
                    b.index, b.name, b.innerRadius, b.outerRadius, b.periodTicksText()));
            send(source, String.format(Locale.ROOT,
                    "      倾角 %.2f° ~ %.2f°；离心率 ≤ %.3f；a档 %d（dA≈%.0f）",
                    Math.toDegrees(b.minInclinationRad), Math.toDegrees(b.maxInclinationRad),
                    b.maxEccentricity, b.aBins, (b.outerRadius - b.innerRadius) / b.aBins));
            send(source, String.format(Locale.ROOT,
                    "      单元 %,d 环 × 每环 %,d 颗 = %,d 颗；编号 %,d ~ %,d；μ=%.4e",
                    b.cellCount, b.k, b.totalCount, b.idBase, b.idBase + b.totalCount - 1, b.mu));
            StringBuilder t = new StringBuilder();
            for (int i = 0; i < b.typeIndices.length; i++) {
                int gi = b.typeIndices[i];
                double w = i == 0 ? b.typeCumulative[0] : b.typeCumulative[i] - b.typeCumulative[i - 1];
                if (t.length() > 0) t.append(", ");
                t.append(u.types.ids[gi]).append(String.format(Locale.ROOT, "(%.3f)", w));
            }
            send(source, "      类型权重: " + (t.length() == 0 ? "（无，退化为基础质点）" : t));
        }
        return 1;
    }

    private int info(CommandSourceStack source, long index) {
        AsteroidUniverse u = u();
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
        send(source, String.format(Locale.ROOT, "  所属环带    [%d] %s", o.beltIndex, o.beltName));
        send(source, String.format(Locale.ROOT, "  类型        %s（%s）",
                o.type.isEmpty() ? "（无）" : o.type, u.types.nameOf(o.type)));
        send(source, String.format(Locale.ROOT, "  变体        #%d  结构 %s",
                o.variant, o.structure.isEmpty() ? "（无）" : o.structure));
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

    /** 一次性附近查询：玩家周围 radius 块内全部小行星（精确）。 */
    private int near(CommandSourceStack source, double radius) {
        if (source.getEntity() == null) {
            source.sendFailure(Component.literal("[小行星] near 需要由玩家（或实体）执行"));
            return 0;
        }
        AsteroidUniverse u = u();
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
            AsteroidOrbit o = u.orbitOf(id);
            double[] p = o.positionAt(tick);
            double dx = p[0] - px, dy = p[1] - py, dz = p[2] - pz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            send(source, String.format(Locale.ROOT, "  #%,d  [%s] 距离 %,.1f 块  位置 (%,.1f, %,.1f, %,.1f)",
                    id, o.type.isEmpty() ? "无类型" : o.type, dist, p[0], p[1], p[2]));
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
        if (L.indexBuilding()) {
            send(source, String.format(Locale.ROOT,
                    "  预载索引建设中: %d/%d 候选已精测（分帧 FRAME=%d，每 tick 分摊，不阻塞主线程）",
                    L.indexFrame(), L.indexTotal(), AsteroidProximityLoader.FRAME));
        } else {
            send(source, String.format(Locale.ROOT,
                    "  预载索引: %d 个真相交轨道环，当前预载颗数约 %,d", L.preloadCellCount(), L.preloadSet().size()));
        }
        send(source, String.format(Locale.ROOT,
                "  强加载: 当前 %.0f 块内 %,d 颗", L.strongRadius, L.strongCount()));
        send(source, String.format(Locale.ROOT,
                "  本次 update 耗时 %d ms（含重建分摊，索引重建按分帧 FRAME=%d 不阻塞主线程）",
                L.lastUpdateNs() / 1_000_000, AsteroidProximityLoader.FRAME));
        send(source, String.format(Locale.ROOT,
                "  进出事件 预载进 %d / 预载出 %d / 强载进 %d / 强载出 %d",
                inPre.length, leftPre.length, inStr.length, leftStr.length));
        send(source, String.format(Locale.ROOT,
                "  预载区间平移刷新间隔: 每 %d tick（强载不受节流，每 tick 从索引检索）", L.preloadIntervalTicks));
        return 1;
    }

    // ---------- 实体化（sable 子层级） ----------

    /** 实体化一颗小行星并回显落块区 / 逻辑位姿 / 包围盒。 */
    private int spawn(CommandSourceStack source, long index) {
        AsteroidUniverse u = u();
        if (!checkIndex(source, index, u)) {
            return 0;
        }
        if (!checkMaterializable(source)) {
            return 0;
        }
        ServerLevel level = source.getLevel();
        long tick = level.getGameTime();
        boolean already = AsteroidEntityifier.isMaterialized(level, index);
        String err = AsteroidEntityifier.materialize(level, index, tick);
        if (err != null) {
            source.sendFailure(Component.literal("[小行星] 实体化失败：" + err));
            return 0;
        }
        AsteroidEntityifier.Instance inst = AsteroidEntityifier.get(level, index);
        AsteroidOrbit o = u.orbitOf(index);
        double[] p = o.positionAt(tick);
        send(source, String.format(Locale.ROOT, "[小行星 spawn] #%,d %s%s · 结构 %s · 尺寸 %d×%d×%d",
                index, already ? "已实体化（幂等）" : "实体化完成",
                o.type.isEmpty() ? "" : " [" + o.type + " / 变体 " + o.variant + "]",
                o.structure, inst.size().getX(), inst.size().getY(), inst.size().getZ()));
        send(source, String.format(Locale.ROOT,
                "  落块暂存区（世界坐标，Y 恒在建造高度内）%s ~ %s —— 与轨道坐标无关，sable 随后整体搬进 plot",
                fmt(inst.structMin), fmt(inst.structMax)));
        send(source, String.format(Locale.ROOT,
                "  逻辑位姿 position = (%,.1f, %,.1f, %,.1f)（轨道位置；轨道 Y 只出现在这里）",
                p[0], p[1], p[2]));
        send(source, String.format(Locale.ROOT,
                "  该轨道最大 |Y| = %,.0f 块；垂直域约束：%s",
                o.a * (1.0 + o.e) * Math.sin(o.i), AsteroidVerticalLimit.describe()));
        send(source, "  进游戏观察：/asteroid tp " + index + "（传送到径向偏 80 格处）");
        return 1;
    }

    /** 实体化并把执行者传送到该小行星附近（径向偏 80 格，避免卡进结构内部）。 */
    private int tp(CommandSourceStack source, long index) {
        Entity entity = source.getEntity();
        if (entity == null) {
            source.sendFailure(Component.literal("[小行星] tp 需要由玩家（或实体）执行"));
            return 0;
        }
        AsteroidUniverse u = u();
        if (!checkIndex(source, index, u)) {
            return 0;
        }
        if (!checkMaterializable(source)) {
            return 0;
        }
        ServerLevel level = source.getLevel();
        long tick = level.getGameTime();
        String err = AsteroidEntityifier.materialize(level, index, tick);
        if (err != null) {
            source.sendFailure(Component.literal("[小行星] 实体化失败：" + err));
            return 0;
        }
        double[] p = u.orbitOf(index).positionAt(tick);
        double r = Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
        double k = r > 1.0 ? 80.0 / r : 0.0;
        double x = p[0] * (1.0 + k);
        double y = p[1] * (1.0 + k);
        double z = p[2] * (1.0 + k);
        // 落点自动朝向小行星质心：站立观看时它一定在视野正中（否则默认朝向可能背对它）
        double dx = p[0] - x, dy = p[1] - y, dz = p[2] - z;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        if (entity instanceof ServerPlayer sp) {
            sp.teleportTo(level, x, y, z, yaw, pitch);
        } else {
            entity.teleportTo(x, y, z);
        }
        send(source, String.format(Locale.ROOT,
                "[小行星 tp] #%,d 质心 (%,.1f, %,.1f, %,.1f) → 落点 (%,.1f, %,.1f, %,.1f)（径向偏 80 格）",
                index, p[0], p[1], p[2], x, y, z));
        return 1;
    }

    /** 实体化状态总览。 */
    private int entity(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        send(source, "[小行星 entity] sable 子层级实体化状态");
        send(source, String.format(Locale.ROOT,
                "  sable %s · 功能 %s · 当前 %d 颗 · 上限 %d 颗 · 历史累计 %d 颗 / 失败 %d 次",
                AsteroidEntityifyService.sablePresent() ? "已加载" : "未加载",
                AsteroidEntityifyService.enabled() ? "开启" : "关闭",
                AsteroidEntityifier.count(level),
                org.xyccwa.space_simulation.config.SpaceSimulationConfig.asteroidEntityifyMaxLoaded.get(),
                AsteroidEntityifier.totalMaterialized(), AsteroidEntityifier.totalFailed()));
        send(source, "  " + AsteroidVerticalLimit.describe());
        send(source, "  " + AsteroidEntityifyService.describe(level));
        int shown = 0;
        for (AsteroidEntityifier.Instance inst : AsteroidEntityifier.all(level)) {
            if (shown >= 24) {
                send(source, "  ...（其余省略）");
                break;
            }
            AsteroidOrbit o = u().orbitOf(inst.id);
            double[] p = o.positionAt(level.getGameTime());
            var pose = inst.sub.logicalPose();
            var bb = inst.sub.boundingBox();
            send(source, String.format(Locale.ROOT,
                    "  #%,d %s · 轨道位姿 (%,.0f, %,.0f, %,.0f) · 实际位姿 (%,.1f, %,.1f, %,.1f) · 自转 %.3f°/s",
                    inst.id, inst.structure, p[0], p[1], p[2],
                    pose.position().x(), pose.position().y(), pose.position().z(),
                    Math.toDegrees(inst.spinRate) * 20.0));
            send(source, String.format(Locale.ROOT,
                    "      plot 区块 %d 个 · plot 包围盒 %s · globalBounds (%,.0f,%,.0f,%,.0f)~(%,.0f,%,.0f,%,.0f) · 跟踪玩家 %d · removed=%s",
                    inst.sub.getPlot().getLoadedChunks().size(), inst.sub.getPlot().getBoundingBox(),
                    bb.minX(), bb.minY(), bb.minZ(), bb.maxX(), bb.maxY(), bb.maxZ(),
                    inst.sub.getTrackingPlayers().size(), inst.sub.isRemoved()));
            shown++;
        }
        return 1;
    }

    private boolean checkIndex(CommandSourceStack source, long index, AsteroidUniverse u) {
        if (index < 0L || index >= u.totalCount) {
            source.sendFailure(Component.literal(String.format(Locale.ROOT,
                    "[小行星] 编号越界：%d（有效 0 ~ %,d）", index, u.totalCount - 1)));
            return false;
        }
        return true;
    }

    private boolean checkMaterializable(CommandSourceStack source) {
        if (!AsteroidEntityifyService.sablePresent()) {
            source.sendFailure(Component.literal("[小行星] sable 未在运行时类路径上，无法实体化（子层级是 sable 提供的）"));
            return false;
        }
        if (!AsteroidEntityifyService.enabled()) {
            source.sendFailure(Component.literal("[小行星] 实体化已在配置中关闭（asteroidEntityifyEnabled=false）"));
            return false;
        }
        return true;
    }

    private static String fmt(net.minecraft.core.BlockPos pos) {
        return String.format(Locale.ROOT, "(%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ());
    }

    private static void send(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }
}
