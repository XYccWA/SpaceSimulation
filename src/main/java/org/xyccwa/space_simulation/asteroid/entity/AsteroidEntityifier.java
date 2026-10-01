package org.xyccwa.space_simulation.asteroid.entity;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.core.Vec3i;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.asteroid.AsteroidOrbit;
import org.xyccwa.space_simulation.asteroid.AsteroidProximityService;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * 小行星实体化 —— 把一颗程序化小行星变成真实的 sable 子层级（可看、可落、可挖）。
 *
 * <h2>Y 轴铁律（本类存在的理由）</h2>
 * 轨道位置半径 100 万~200 万格，Y 可达数十万格，而主世界高度域只有 [-512, 1024)。
 * 上一轮失败正是"方块放置跟随小行星 Y 轴位置 → 超出世界范围无法放置 → 子层级生成不出来"。
 * 本实现把两件事彻底分开：
 * <ol>
 *   <li><b>落块只发生在 sable plot 内</b>：结构模板写进 plot 的 embedded level（局部坐标 0..size），
 *       plot 的父世界落点由 sable 决定（XZ 在 plot 网格、Y = 世界高度中点），**永远在世界范围内**，
 *       与轨道坐标无关。因此轨道 Y 再大也不会影响落块。</li>
 *   <li><b>轨道坐标只进"逻辑位姿"</b>：{@code logicalPose.position} = 轨道位置，
 *       每 tick 用 {@link RigidBodyHandle#teleport} 把物理体推到该位置。</li>
 * </ol>
 * 此外 sable 每 tick 会把 globalBounds 的 Y 越出 {@code sub_level_remove_min/max} 的子层级直接删除，
 * 所以轨道竖直范围必须落在该窗口内 —— 该约束由 {@code AsteroidVerticalLimit} 在数据包层保证。
 *
 * <h2>持久化策略</h2>
 * 不落盘：离开窗口即 {@code removeSubLevel}，重新进入时按同一编号从结构模板重建。
 * 因此存档里不会堆积每个 5 万方块的结构副本，小行星也永远是"新"的。
 */
public final class AsteroidEntityifier {

    /** 连续多少 tick 物理体不可用才判定该颗失败并卸载。 */
    private static final int PHYSICS_FAIL_LIMIT = 100;

    /**
     * 结构落块的**固定安全暂存区**（世界坐标）。
     *
     * <p>为什么是固定点而不是小行星轨道位置：世界建造高度只有 [-512, 512)，而小行星轨道 Y 可达数千甚至
     * 数十万格 —— 在轨道坐标处 setBlock 必然越界失败（上一轮"无法生成子层级"的直接原因）。
     * 这里用 XZ=(2048, 200000)：在 plot 网格 [2048,133120) 之外、太阳球体之外、各环带（≥1e6）之内侧，
     * Y=0 恒在世界建造高度内。结构模板先落在这里，sable 随后把它们整体搬进 plot，
     * 暂存区在同一 tick 内即被清空，因此多颗小行星可复用同一暂存点。
     */
    private static final BlockPos STAGING_ORIGIN = new BlockPos(2048, 0, 200_000);

    /** 暂存区可用高度（块）——世界建造高度内留足余量。 */
    private static final int STAGING_Y_HEADROOM = 400;

    /** 暂存区需要常驻加载的区块半径（区块数）——结构最大约 47 格，留足余量。 */
    private static final int STAGING_CHUNK_RADIUS = 3;

    /** 子层级名前缀：用于识别"这是我们生成的小行星"（跨会话残留清理 / 孤儿接管）。 */
    public static final String NAME_PREFIX = "asteroid_";

    /** 每维度的活动实体化表（服务端主线程访问）。 */
    private static final Map<ResourceKey<Level>, Map<Long, Instance>> BY_LEVEL = new LinkedHashMap<>();

    /** 暂存区已 forceload 的维度（避免重复设置）。 */
    private static final java.util.Set<ResourceKey<Level>> STAGING_LOADED = new java.util.HashSet<>();

    private static long totalMaterialized = 0L;
    private static long totalFailed = 0L;
    private static long totalPurged = 0L;
    private static long lastMaterializeMs = 0L;

    private AsteroidEntityifier() {}

    /** 一颗已实体化的小行星。 */
    public static final class Instance {
        public final long id;
        public final ServerSubLevel sub;
        public final String structure;
        /** 结构在 plot 区（父世界坐标）中的包围盒。 */
        public final BlockPos structMin;
        public final BlockPos structMax;
        /** 自转轴（单位向量）与角速度（rad/tick）。 */
        public final Vector3d spinAxis;
        public final double spinRate;
        /** 实体化时的游戏刻度。 */
        public final long spawnedTick;
        /** 连续物理体不可用次数（创建后前几 tick 属正常）。 */
        int physicsFailTicks;
        /** 非空 = 待卸载原因。 */
        String deadReason;

        Instance(long id, ServerSubLevel sub, String structure, BlockPos structMin, BlockPos structMax,
                 Vector3d spinAxis, double spinRate, long spawnedTick) {
            this.id = id;
            this.sub = sub;
            this.structure = structure;
            this.structMin = structMin;
            this.structMax = structMax;
            this.spinAxis = spinAxis;
            this.spinRate = spinRate;
            this.spawnedTick = spawnedTick;
        }

        /** 结构尺寸（块）。 */
        public Vec3i size() {
            return new Vec3i(structMax.getX() - structMin.getX() + 1,
                    structMax.getY() - structMin.getY() + 1,
                    structMax.getZ() - structMin.getZ() + 1);
        }
    }

    // ---------- 查询 ----------

    private static Map<Long, Instance> map(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level.dimension(), k -> new LinkedHashMap<>());
    }

    public static boolean isMaterialized(ServerLevel level, long id) {
        return map(level).containsKey(id);
    }

    public static Instance get(ServerLevel level, long id) {
        return map(level).get(id);
    }

    public static int count(ServerLevel level) {
        return map(level).size();
    }

    public static List<Instance> all(ServerLevel level) {
        return new ArrayList<>(map(level).values());
    }

    public static long totalMaterialized() {
        return totalMaterialized;
    }

    public static long totalFailed() {
        return totalFailed;
    }

    public static long totalPurged() {
        return totalPurged;
    }

    /** 最近一次实体化耗时（毫秒）——用于观测主线程开销。 */
    public static long lastMaterializeMs() {
        return lastMaterializeMs;
    }

    // ---------- 暂存区常驻 ----------

    /**
     * 让暂存区常驻加载：结构落块的 setBlock 不再触发同步区块生成/加载，
     * 这是把单颗实体化耗时从"1 秒级"压下来的关键（每次 materialize 前调用，幂等）。
     */
    public static void ensureStagingLoaded(ServerLevel level) {
        if (!STAGING_LOADED.add(level.dimension())) {
            return;
        }
        int cx = STAGING_ORIGIN.getX() >> 4;
        int cz = STAGING_ORIGIN.getZ() >> 4;
        for (int x = -1; x <= STAGING_CHUNK_RADIUS; x++) {
            for (int z = -1; z <= STAGING_CHUNK_RADIUS; z++) {
                level.setChunkForced(cx + x, cz + z, true);
            }
        }
        SpaceSimulation.LOGGER.info("[Entityify] 暂存区常驻加载：区块 ({},{})..({},{})",
                cx - 1, cz - 1, cx + STAGING_CHUNK_RADIUS, cz + STAGING_CHUNK_RADIUS);
    }

    // ---------- 跨会话残留（孤儿）清理 ----------

    /**
     * 清除本维度里所有**不属于当前活动表**的 {@code asteroid_} 子层级。
     *
     * <p>sable 会把子层级连同强制加载 ticket 一起存档；重启后我们的活动表是空的，
     * 上一次会话残留的子层级会以"冻结的石头"形式留在世界里并占住 plot。
     * 本方法在每个维度首次 tick 时调用一次（幂等），把它们清掉；
     * 之后按需重新实体化即可。
     *
     * @return 清理数量
     */
    public static int purgeOrphans(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return 0;
        }
        Map<Long, Instance> active = map(level);
        List<ServerSubLevel> orphans = new ArrayList<>();
        for (dev.ryanhcode.sable.sublevel.SubLevel s : container.getAllSubLevels()) {
            if (!(s instanceof ServerSubLevel sub)) {
                continue;
            }
            String name = sub.getName();
            if (name == null || !name.startsWith(NAME_PREFIX)) {
                continue; // 不是我们生成的（例如玩家自己的飞船）
            }
            boolean ours = false;
            for (Instance inst : active.values()) {
                if (inst.sub == sub) {
                    ours = true;
                    break;
                }
            }
            if (!ours) {
                orphans.add(sub);
            }
        }
        for (ServerSubLevel sub : orphans) {
            removeQuietly(level, container, sub);
            totalPurged++;
        }
        if (!orphans.isEmpty()) {
            SpaceSimulation.LOGGER.info("[Entityify] 清理跨会话残留小行星子层级 {} 个（维度 {}）",
                    orphans.size(), level.dimension().location());
        }
        return orphans.size();
    }

    /** 卸载本维度全部活动小行星（服务器停止/世界卸载前调用，避免它们被存档）。 */
    public static int dematerializeAll(ServerLevel level) {
        Map<Long, Instance> m = map(level);
        if (m.isEmpty()) {
            return 0;
        }
        List<Long> ids = new ArrayList<>(m.keySet());
        for (long id : ids) {
            dematerialize(level, id);
        }
        return ids.size();
    }

    // ---------- 实体化 / 卸载 ----------

    /**
     * 实体化一颗小行星。
     *
     * @return null 表示成功（或已经实体化）；否则为失败原因（中文，可直接回显）。
     */
    public static String materialize(ServerLevel level, long id, long tick) {
        Map<Long, Instance> m = map(level);
        if (m.containsKey(id)) {
            return null; // 幂等
        }
        int cap = SpaceSimulationConfig.asteroidEntityifyMaxLoaded.get();
        if (m.size() >= cap) {
            return String.format(Locale.ROOT, "已达实体化上限 %d 颗（配置 asteroidEntityifyMaxLoaded）", cap);
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return "无法获取 sable 子层级容器（sable 未加载或世界未初始化）";
        }

        AsteroidOrbit orbit = AsteroidProximityService.universe().orbitOf(id);
        if (orbit.structure == null || orbit.structure.isBlank()) {
            return "该小行星没有结构模板（类型/变体未命中 asteroid_type 表）";
        }
        ResourceLocation structureId = ResourceLocation.tryParse(orbit.structure);
        if (structureId == null) {
            return "结构 id 非法：" + orbit.structure;
        }
        StructureTemplate template = level.getStructureManager().get(structureId).orElse(null);
        if (template == null) {
            return "结构模板缺失：" + structureId + "（应在 data/<ns>/structure/" + structureId.getPath() + ".nbt）";
        }
        Vec3i size = template.getSize();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            return "结构模板尺寸非法：" + size;
        }
        if (size.getY() > STAGING_Y_HEADROOM) {
            return "结构过高（" + size.getY() + " 块），超过暂存区可用高度 " + STAGING_Y_HEADROOM;
        }

        double[] p = orbit.positionAt(tick);
        BlockPos staging = STAGING_ORIGIN;
        ensureStagingLoaded(level);
        long startedNs = System.nanoTime();
        long placeNs = 0L;
        long collectNs = 0L;
        long assembleNs = 0L;
        ServerSubLevel sub = null;
        try {
            // 1) 落块：结构模板先放进**世界里的固定安全暂存区**（Y 恒为 STAGING_Y，绝不用轨道坐标，
            //    因此轨道 Y 再大也不影响落块 —— 这正是上一轮"落块跟随小行星 Y 轴 → 超出范围"的根治点）。
            StructurePlaceSettings settings = new StructurePlaceSettings();
            template.placeInWorld(level, staging, staging, settings, RandomSource.create(id), 2);
            placeNs = System.nanoTime() - startedNs;

            // 2) 收集真正落下的方块（逐格查空气，比按 palette 猜更可靠）与紧包围盒。
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            List<BlockPos> blocks = new ArrayList<>();
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (int y = 0; y < size.getY(); y++) {
                for (int x = 0; x < size.getX(); x++) {
                    for (int z = 0; z < size.getZ(); z++) {
                        cursor.set(staging.getX() + x, staging.getY() + y, staging.getZ() + z);
                        if (level.getBlockState(cursor).isAir()) {
                            continue;
                        }
                        BlockPos pos = cursor.immutable();
                        blocks.add(pos);
                        if (pos.getX() < minX) minX = pos.getX();
                        if (pos.getY() < minY) minY = pos.getY();
                        if (pos.getZ() < minZ) minZ = pos.getZ();
                        if (pos.getX() > maxX) maxX = pos.getX();
                        if (pos.getY() > maxY) maxY = pos.getY();
                        if (pos.getZ() > maxZ) maxZ = pos.getZ();
                    }
                }
            }
            if (blocks.isEmpty()) {
                totalFailed++;
                return "结构模板在暂存区没有落下任何方块：" + structureId;
            }
            collectNs = System.nanoTime() - startedNs - placeNs;
            BoundingBox3i bounds = new BoundingBox3i(minX, minY, minZ, maxX, maxY, maxZ);
            BlockPos anchor = new BlockPos((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);

            // 3) 交给 sable 官方装配：搬进 plot、建区块与包围盒、注册物理体、质心即旋转点。
            sub = SubLevelAssemblyHelper.assembleBlocks(level, anchor, blocks, bounds);
            assembleNs = System.nanoTime() - startedNs - placeNs - collectNs;
            if (sub == null) {
                totalFailed++;
                return "sable 装配返回空（无空闲 plot 或方块不可装配）";
            }

            // 4) 覆盖逻辑位姿为轨道位置 —— 轨道坐标（含巨大的 Y 与半径）只出现在这里。
            Pose3d live = sub.logicalPose();
            live.position().set(p[0], p[1], p[2]);
            live.orientation().identity();

            // 5) 强制加载 ticket：plot 物理区在太阳内部、远离玩家逻辑位置，
            //    没有 ticket 的子层级会被序列化卸载 → 客户端收不到方块。
            container.addForceLoadTicket(sub, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
            sub.setName(NAME_PREFIX + id);

            double spinPerTick = Math.toRadians(SpaceSimulationConfig.asteroidSpinDegPerSecond.get()) / 20.0;
            Instance inst = new Instance(id, sub, orbit.structure,
                    new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ),
                    spinAxisOf(id), spinPerTick, tick);
            m.put(id, inst);
            totalMaterialized++;
            lastMaterializeMs = (System.nanoTime() - startedNs) / 1_000_000L;

            SpaceSimulation.LOGGER.info(
                    "[Entityify] materialized id={} belt={} type={} variant={} structure={} blocks={} "
                            + "size={}x{}x{} pos=({}, {}, {}) 耗时 total={} ms (落块 {} / 收集 {} / 装配 {})",
                    id, orbit.beltIndex, orbit.type, orbit.variant, orbit.structure, blocks.size(),
                    size.getX(), size.getY(), size.getZ(),
                    String.format(Locale.ROOT, "%.1f", p[0]),
                    String.format(Locale.ROOT, "%.1f", p[1]),
                    String.format(Locale.ROOT, "%.1f", p[2]),
                    lastMaterializeMs,
                    placeNs / 1_000_000L, collectNs / 1_000_000L, assembleNs / 1_000_000L);
            return null;
        } catch (Throwable t) {
            totalFailed++;
            SpaceSimulation.LOGGER.error("[Entityify] 实体化 id=" + id + " 失败，回滚", t);
            if (sub != null) {
                removeQuietly(level, container, sub);
            }
            return "实体化失败：" + t;
        }
    }

    /** 卸载一颗小行星（移除成员 + 强制加载 ticket + 子层级）。返回是否确实卸载了。 */
    public static boolean dematerialize(ServerLevel level, long id) {
        Map<Long, Instance> m = map(level);
        Instance inst = m.remove(id);
        if (inst == null) {
            return false;
        }
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            removeQuietly(level, container, inst.sub);
        }
        return true;
    }

    private static void removeQuietly(ServerLevel level, ServerSubLevelContainer container, ServerSubLevel sub) {
        try {
            container.removeForceLoadTicket(sub, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        } catch (Throwable t) {
            SpaceSimulation.LOGGER.warn("[Entityify] removeForceLoadTicket 失败（忽略）", t);
        }
        try {
            if (sub.isRemoved()) {
                return; // sable 已经把它移除了，再调用 removeSubLevel 只会抛 "No sub-level at x, z"
            }
            // 用 SubLevel 重载：sable 自己按 plot 位置反算局部 plot 坐标，避免我们手算约定出错
            container.removeSubLevel(sub, SubLevelRemovalReason.UNLOADED);
        } catch (Throwable t) {
            SpaceSimulation.LOGGER.warn("[Entityify] removeSubLevel 失败（忽略）", t);
        }
    }

    // ---------- 每 tick 驱动 ----------

    /**
     * 驱动全部实体化小行星：逻辑位姿按轨道确定性求解，物理体用 teleport 跟随。
     *
     * <p>物理体在子层级创建后的下几个 tick 内才会被 sable 注册进物理管线，
     * 因此首次驱动失败是**正常**的：无效就跳过、下个 tick 重试，绝不因此卸载。
     * 只有连续失败超过 {@link #PHYSICS_FAIL_LIMIT} 次才判定该颗不可用并卸载（不崩服）。
     */
    public static void tick(ServerLevel level, long tick) {
        Map<Long, Instance> m = map(level);
        if (m.isEmpty()) {
            return;
        }
        List<Instance> dead = null;
        for (Instance inst : m.values()) {
            try {
                drive(level, inst, tick);
            } catch (RuntimeException e) {
                SpaceSimulation.LOGGER.warn("[Entityify] id={} 驱动异常（第 {} 次）: {}",
                        inst.id, inst.physicsFailTicks + 1, e.toString());
                SpaceSimulation.LOGGER.warn("[Entityify] 驱动异常堆栈", e);
                inst.physicsFailTicks++;
            }
            if (inst.sub.isRemoved()) {
                inst.deadReason = "sable 已移除该子层级（Y 越界 / 空包围盒等）";
            }
            if (inst.deadReason == null && inst.physicsFailTicks > PHYSICS_FAIL_LIMIT) {
                inst.deadReason = "物理体连续 " + inst.physicsFailTicks + " 次不可用";
            }
            if (inst.deadReason != null) {
                if (dead == null) dead = new ArrayList<>();
                dead.add(inst);
            }
        }
        if (dead != null) {
            for (Instance inst : dead) {
                SpaceSimulation.LOGGER.warn("[Entityify] id={} 卸载：{}", inst.id, inst.deadReason);
                inst.deadReason = null;
                dematerialize(level, inst.id);
            }
        }
    }

    /** 单颗驱动：轨道位置 + 自转 → logicalPose → 物理体（物理体未就绪时安全跳过）。 */
    public static void drive(ServerLevel level, Instance inst, long tick) {
        AsteroidOrbit orbit = AsteroidProximityService.universe().orbitOf(inst.id);
        double[] p = orbit.positionAt(tick);
        Quaterniond q = new Quaterniond();
        if (inst.spinRate != 0.0) {
            q.rotationAxis(inst.spinRate * tick, inst.spinAxis.x, inst.spinAxis.y, inst.spinAxis.z);
        }
        // 逻辑位姿始终更新（渲染/交互读的是它），物理体只是跟随者
        Pose3d live = inst.sub.logicalPose();
        live.position().set(p[0], p[1], p[2]);
        live.orientation().set(q);

        RigidBodyHandle handle = RigidBodyHandle.of(inst.sub);
        if (handle == null || !handle.isValid()) {
            inst.physicsFailTicks++; // 物理体尚未注册：下个 tick 再试
            return;
        }
        try {
            handle.teleport(live.position(), q);
            inst.physicsFailTicks = 0;
        } catch (RuntimeException e) {
            SpaceSimulation.LOGGER.warn("[Entityify] id={} teleport 失败（第 {} 次）: {}",
                    inst.id, inst.physicsFailTicks + 1, e.toString());
            SpaceSimulation.LOGGER.warn("[Entityify] teleport 异常堆栈", e);
            inst.physicsFailTicks++;
        }
    }

    /** 由编号确定性派生自转轴（单位向量，避免南北极对齐的退化）。 */
    private static Vector3d spinAxisOf(long id) {
        long h = id * 0x9E3779B97F4A7C15L + 0x5EED20260825L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        Vector3d v = new Vector3d(
                ((h & 0xFFFF) / 65535.0) - 0.5,
                (((h >>> 16) & 0xFFFF) / 65535.0) - 0.5 + 0.35,
                (((h >>> 32) & 0xFFFF) / 65535.0) - 0.5);
        if (v.lengthSquared() < 1.0e-6) {
            v.set(0.2, 1.0, 0.1);
        }
        return v.normalize();
    }
}
