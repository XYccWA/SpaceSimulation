package org.xyccwa.space_simulation.asteroid.entity;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.region.SubLevelRegionFile;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.asteroid.AsteroidOrbit;
import org.xyccwa.space_simulation.asteroid.AsteroidProximityService;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;

import java.io.File;
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
 * <h2>持久化策略（2026-10 起）</h2>
 * <b>只有被玩家改动过的小行星才落盘</b>，判据是 {@link AsteroidPersistence} 里的改动标记：
 * <ul>
 *   <li><b>未改动</b>：卸载时直接 {@code removeSubLevel}，存档不堆积 5 万方块的结构副本，
 *       再次进入时按同一编号从结构模板重建（内容与丢弃前等价）。</li>
 *   <li><b>已改动</b>：卸载走 sable 原生存储链路 —— {@code saveAll()}（分配落盘指针并写盘）
 *       → {@code moveToUnloaded}（转入 holding，UNLOADED 不删数据）→ {@code saveAll()}（holding 落盘），
 *       并把指针记进台账。重新进入时优先按台账指针 {@code snatchAndLoad} 取回，
 *       指针失效则按 {@code display_name} 扫描存储目录兜底；取回的是**改动后的方块**，不是结构模板。</li>
 * </ul>
 * 轨道位置仍由解析开普勒解每 tick 决定（{@link #drive}），落盘位姿只在取回瞬间用于定位。
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

    /** 暂存区需要常驻加载的区块半径（区块数）——结构最大 23 格，48 格覆盖足够。 */
    private static final int STAGING_CHUNK_RADIUS = 2;

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
        /** 解析位姿/速度缓存（唯一真值）：由 ensureAnalytic 按 tick 刷新，同一 tick 内幂等。 */
        long analyticTick = Long.MIN_VALUE;
        final double[] analyticPos = new double[3];
        final Quaterniond analyticQuat = new Quaterniond();
        final Vector3d analyticVel = new Vector3d();
        final Vector3d analyticAngVel = new Vector3d();
        /** 上次"物理与解析不符"日志的游戏刻度（节流用）。 */
        long lastDriftLogTick = Long.MIN_VALUE;

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
     * 启动对账：清理"上一会话不该留下"的 {@code asteroid_} 子层级。
     *
     * <p>sable 只在子层级带强制加载票时才会把它自动恢复回来，而我们卸载时已经清掉票，
     * 所以正常情况下这里扫不到任何东西。真扫到时只有两种可能：
     * <ul>
     *   <li><b>未改动过的残留</b>（上次崩溃/停服流程没走完）→ 删除，连 sable 存储副本一起清掉，
     *       避免存档里堆积与结构模板等价的结构副本；</li>
     *   <li><b>已改动过的副本</b> → 保留，等 {@link #materialize} 按编号认领（玩家靠近时才重新挂进活动表）。</li>
     * </ul>
     *
     * @return 清理数量
     */
    public static int reconcileStored(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return 0;
        }
        AsteroidPersistence persistence = AsteroidPersistence.getOrLoad(level);
        Map<Long, Instance> active = map(level);
        List<ServerSubLevel> drop = new ArrayList<>();
        int kept = 0;
        for (ServerSubLevel sub : container.getAllSubLevels()) {
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
            if (ours) {
                continue;
            }
            long id = parseId(name);
            if (id >= 0 && persistence.isChanged(id)) {
                kept++;
                continue;
            }
            drop.add(sub);
        }
        for (ServerSubLevel sub : drop) {
            removeAndDelete(level, container, sub);
            totalPurged++;
        }
        if (!drop.isEmpty() || kept > 0) {
            SpaceSimulation.LOGGER.info(
                    "[Entityify] 启动对账（维度 {}）：清理未改动残留 {} 个 · 保留待认领的已改动副本 {} 个",
                    level.dimension().location(), drop.size(), kept);
        }
        return drop.size();
    }

    /** 卸载本维度全部活动小行星（服务器停止/世界卸载前调用）：已改动的落盘，未改动的丢弃。 */
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

        // 0) 持久化认领：sable 可能已经把上次的副本（含玩家改动）恢复进容器，或它还在 holding/磁盘里
        //    等我们按指针取回 —— 两种情况都绝不能再从结构模板重建，否则改动被抹掉。
        ServerSubLevel existing = findByName(level, NAME_PREFIX + id);
        if (existing != null) {
            return adopt(level, id, tick, existing);
        }
        if (AsteroidPersistence.getOrLoad(level).isChanged(id)) {
            ServerSubLevel recovered = recoverStored(level, id);
            if (recovered != null) {
                return adopt(level, id, tick, recovered);
            }
            SpaceSimulation.LOGGER.warn(
                    "[Entityify] id={} 台账标记为已改动，但 sable 存储里没找到副本，回退为结构模板重建（改动丢失）", id);
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
            inst.analyticTick = tick;
            inst.analyticPos[0] = p[0];
            inst.analyticPos[1] = p[1];
            inst.analyticPos[2] = p[2];
            inst.analyticQuat.set(spinQuaternion(inst, tick));
            inst.analyticAngVel.set(inst.spinAxis).mul(inst.spinRate * 20.0);
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
            if (AsteroidPersistence.getOrLoad(level).isChanged(id)) {
                storeCopy(level, container, inst);
            } else {
                discardCopy(level, container, inst);
            }
        }
        return true;
    }

    /**
     * 卸载一颗**未改动**的小行星：内容与结构模板重建等价，所以直接丢弃。
     *
     * <p>若它在活跃期间恰好赶上过世界自动保存（sable 会把活跃子层级全部落盘），磁盘上就留着一份
     * 永远不会再被加载的副本 —— 这里顺手把它标记删除并落盘，避免存档随探索无界增长。
     * 从未被序列化过的（指针为空）不触发任何保存，开销为零。
     */
    private static void discardCopy(ServerLevel level, ServerSubLevelContainer container, Instance inst) {
        SubLevelHoldingChunkMap holding = container.getHoldingChunkMap();
        boolean hadCopy = inst.sub.getLastSerializationPointer() != null;
        if (hadCopy) {
            try {
                holding.queueDeletion(inst.sub);
            } catch (Throwable t) {
                SpaceSimulation.LOGGER.warn("[Entityify] id=" + inst.id + " queueDeletion 失败（忽略）", t);
            }
        }
        removeQuietly(level, container, inst.sub);
        if (hadCopy) {
            try {
                holding.saveAll();
            } catch (Throwable t) {
                SpaceSimulation.LOGGER.warn("[Entityify] id=" + inst.id + " 清理未改动副本落盘失败（忽略）", t);
            }
        }
    }

    // ---------- 持久化：落盘 / 取回 / 认领 ----------

    /**
     * 把一颗**已被改动**的小行星转成 sable 持有副本并落盘。
     *
     * <p>顺序不能变：先 {@code saveAll()} 让 sable 给活跃子层级分配落盘指针（否则指针与 ticket 都是空的），
     * 再 {@code moveToUnloaded} 把数据放进 holding chunk（用 UNLOADED，sable 不会删存储），
     * 最后再 {@code saveAll()} 把 holding 写盘并 flush。两次 saveAll 之间逻辑位置不变，
     * 因此指针不会在 chunk 间迁移，台账记下的指针与最终落盘位置一致。
     */
    private static void storeCopy(ServerLevel level, ServerSubLevelContainer container, Instance inst) {
        AsteroidPersistence persistence = AsteroidPersistence.getOrLoad(level);
        SubLevelHoldingChunkMap holding = container.getHoldingChunkMap();
        java.util.UUID uuid = inst.sub.getUniqueId();
        long startedNs = System.nanoTime();
        try {
            ChunkPos at = new ChunkPos(BlockPos.containing(
                    inst.sub.logicalPose().position().x(),
                    inst.sub.logicalPose().position().y(),
                    inst.sub.logicalPose().position().z()));

            // sable 只在"子层级还没有落盘指针"时才会给 holding 副本分配新指针、并把指针登记进
            // holding chunk 的指针表；若指针已存在且 chunk 相同，它只覆盖数据文件、指针表里仍然没有记录
            // → 重启后 snatch 会因为"holding chunk 里没有这个 uuid"而失败。所以先清掉旧指针，
            // 强制它走"新分配 + 登记"分支（旧数据文件由 sable 的指针迁移逻辑清理）。
            inst.sub.setLastSerializationPointer(null);

            // 转入 holding（UNLOADED：数据保留），再立刻落盘（写数据文件 + 指针表 + flush）
            holding.moveToUnloaded(inst.sub, at);
            holding.saveAll();

            GlobalSavedSubLevelPointer pointer = findPointer(holding.getStorage(), at, uuid);
            if (pointer != null) {
                persistence.rememberStored(inst.id, uuid, pointer.chunkPos().x, pointer.chunkPos().z,
                        pointer.storageIndex(), pointer.subLevelIndex());
            } else {
                persistence.forgetStored(inst.id);
                SpaceSimulation.LOGGER.warn("[Entityify] id={} 落盘后在 chunk {} 里没找到指针，"
                        + "重新加载时将回退为按名字扫描存储目录", inst.id, at);
            }
            // 强制加载票已随 UNLOADED 失去意义；显式清掉，避免 sable 下次启动把它自动拉回来
            // （我们要按需取回，否则玩家一进世界就被恢复出一堆白占 plot 的旧副本）。
            try {
                container.removeForceLoadTicket(inst.sub, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
            } catch (Throwable ignored) {
            }
            SpaceSimulation.LOGGER.info("[Entityify] id={} 改动已落盘（{} ms，指针 {}）",
                    inst.id, (System.nanoTime() - startedNs) / 1_000_000L, pointer);
        } catch (Throwable t) {
            SpaceSimulation.LOGGER.error("[Entityify] id=" + inst.id + " 落盘失败，退回普通卸载（改动可能丢失）", t);
            removeQuietly(level, container, inst.sub);
        }
    }

    /** 落盘之后从该 chunk 的 holding 表里读回子层级的指针（此时文件里已是最终位置）。 */
    @Nullable
    private static GlobalSavedSubLevelPointer findPointer(SubLevelStorage storage, ChunkPos pos, java.util.UUID uuid) {
        SubLevelHoldingChunk chunk = storage.attemptLoadHoldingChunk(pos);
        if (chunk == null) {
            return null;
        }
        for (SavedSubLevelPointer local : chunk.getSubLevelPointers()) {
            SubLevelData data = storage.attemptLoadSubLevel(pos, local);
            if (data != null && uuid.equals(data.uuid())) {
                return new GlobalSavedSubLevelPointer(pos, local.storageIndex(), local.subLevelIndex());
            }
        }
        return null;
    }

    /** 容器里是否已有一颗叫该名字的子层级（sable 从持久化恢复出来的）。 */
    @Nullable
    public static ServerSubLevel findByName(ServerLevel level, String name) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        for (ServerSubLevel sub : container.getAllSubLevels()) {
            if (name.equals(sub.getName())) {
                return sub;
            }
        }
        return null;
    }

    /** 该子层级对应的小行星编号；-1 = 它不在活动表里（装配期、别人的子层级、或已卸载）。 */
    public static long idOf(ServerSubLevel sub) {
        for (Map<Long, Instance> instances : BY_LEVEL.values()) {
            for (Instance inst : instances.values()) {
                if (inst.sub == sub) {
                    return inst.id;
                }
            }
        }
        return -1L;
    }

    /** 该方块位置落在哪颗活动小行星的 plot 内；-1 = 不在任何一颗内。 */
    public static long idAt(ServerLevel level, BlockPos pos) {
        for (Instance inst : map(level).values()) {
            BoundingBox3ic bounds = inst.sub.getPlot().getBoundingBox();
            if (bounds.contains(pos.getX(), pos.getY(), pos.getZ())) {
                return inst.id;
            }
        }
        return -1L;
    }

    /**
     * 认领一颗**已经存在**的小行星子层级（持久化副本），把它重新挂进活动表并按解析轨道定位。
     *
     * <p>方块内容取自该子层级自身（含玩家改动），绝不重新落块。
     */
    private static String adopt(ServerLevel level, long id, long tick, ServerSubLevel sub) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return "无法获取 sable 子层级容器（sable 未加载或世界未初始化）";
        }
        AsteroidOrbit orbit = AsteroidProximityService.universe().orbitOf(id);
        if (orbit == null) {
            return "该小行星的轨道未定义（数据包可能已变更）";
        }
        double[] p = orbit.positionAt(tick);
        sub.logicalPose().position().set(p[0], p[1], p[2]);

        container.addForceLoadTicket(sub, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        sub.setName(NAME_PREFIX + id);

        BoundingBox3ic plot = sub.getPlot().getBoundingBox();
        double spinPerTick = Math.toRadians(SpaceSimulationConfig.asteroidSpinDegPerSecond.get()) / 20.0;
        Instance inst = new Instance(id, sub, orbit.structure,
                new BlockPos(plot.minX(), plot.minY(), plot.minZ()),
                new BlockPos(plot.maxX(), plot.maxY(), plot.maxZ()),
                spinAxisOf(id), spinPerTick, tick);
        inst.analyticTick = tick;
        inst.analyticPos[0] = p[0];
        inst.analyticPos[1] = p[1];
        inst.analyticPos[2] = p[2];
        inst.analyticQuat.set(spinQuaternion(inst, tick));
        inst.analyticAngVel.set(inst.spinAxis).mul(inst.spinRate * 20.0);
        map(level).put(id, inst);
        totalMaterialized++;
        SpaceSimulation.LOGGER.info("[Entityify] id={} 认领持久化副本：plot 包围盒 {}，位姿已重置到解析轨道",
                id, plot);
        return null;
    }

    /**
     * 从 sable 存储里取回一颗**已改动**小行星的副本：先按台账指针 {@code snatchAndLoad}（快），
     * 指针失效（chunk 迁移、文件被删）时回退到按 {@code display_name} 全量扫描（慢，但只走一次）。
     *
     * @return 取回并已挂进容器的子层级；null = 没找到（调用方回退结构模板重建）
     */
    @Nullable
    private static ServerSubLevel recoverStored(ServerLevel level, long id) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) {
            return null;
        }
        AsteroidPersistence persistence = AsteroidPersistence.getOrLoad(level);
        AsteroidPersistence.Stored stored = persistence.stored(id);
        if (stored != null) {
            GlobalSavedSubLevelPointer pointer = new GlobalSavedSubLevelPointer(
                    new ChunkPos(stored.chunkX(), stored.chunkZ()), stored.storageIndex(), stored.subLevelIndex());
            try {
                container.getHoldingChunkMap().snatchAndLoad(pointer, stored.uuid());
                ServerSubLevel got = serverSubLevel(container, stored.uuid());
                if (got != null) {
                    return got;
                }
            } catch (Throwable t) {
                SpaceSimulation.LOGGER.warn("[Entityify] id=" + id + " 台账指针取回异常，回退扫描", t);
            }
            SpaceSimulation.LOGGER.warn("[Entityify] id={} 台账指针 {} 已失效，改为扫描 sable 存储", id, pointer);
            persistence.forgetStored(id);
        }
        return scanStoredByName(container, id, persistence);
    }

    @Nullable
    private static ServerSubLevel serverSubLevel(ServerSubLevelContainer container, java.util.UUID uuid) {
        SubLevel sub = container.getSubLevel(uuid);
        return sub instanceof ServerSubLevel server ? server : null;
    }

    /**
     * 兜底扫描：遍历 {@code <world>/sublevels/} 的 region 文件，按 {@code display_name == asteroid_<id>} 找副本。
     *
     * <p>与 sable 自带 {@code /sable storage find} 同一条路径（region 文件 → holding chunk → 指针 → 子层级数据）。
     * 每颗只在台账指针失效时才走，代价是读一批 region 索引与数据。
     */
    @Nullable
    private static ServerSubLevel scanStoredByName(ServerSubLevelContainer container, long id,
                                                   AsteroidPersistence persistence) {
        SubLevelStorage storage = container.getHoldingChunkMap().getStorage();
        File[] regions = storage.getFolder().toFile()
                .listFiles((dir, name) -> name.endsWith(SubLevelRegionFile.FILE_EXTENSION));
        if (regions == null || regions.length == 0) {
            return null;
        }
        String wanted = NAME_PREFIX + id;
        long startedNs = System.nanoTime();
        for (File region : regions) {
            String base = region.getName();
            base = base.substring(0, base.length() - SubLevelRegionFile.FILE_EXTENSION.length());
            String[] parts = base.split("\\.");
            if (parts.length != 3) {
                continue;
            }
            int regionX;
            int regionZ;
            try {
                regionX = Integer.parseInt(parts[1]);
                regionZ = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                continue;
            }
            for (int localX = 0; localX < SubLevelRegionFile.SIDE_LENGTH; localX++) {
                for (int localZ = 0; localZ < SubLevelRegionFile.SIDE_LENGTH; localZ++) {
                    ChunkPos chunkPos = new ChunkPos(
                            regionX * SubLevelRegionFile.SIDE_LENGTH + localX,
                            regionZ * SubLevelRegionFile.SIDE_LENGTH + localZ);
                    SubLevelHoldingChunk chunk = storage.attemptLoadHoldingChunk(chunkPos);
                    if (chunk == null) {
                        continue;
                    }
                    for (SavedSubLevelPointer local : chunk.getSubLevelPointers()) {
                        SubLevelData data = storage.attemptLoadSubLevel(chunkPos, local);
                        if (data == null || !data.fullTag().contains("display_name")) {
                            continue;
                        }
                        if (!wanted.equals(data.fullTag().getString("display_name"))) {
                            continue;
                        }
                        GlobalSavedSubLevelPointer pointer = new GlobalSavedSubLevelPointer(
                                chunkPos, local.storageIndex(), local.subLevelIndex());
                        try {
                            container.getHoldingChunkMap().snatchAndLoad(pointer, data.uuid());
                        } catch (Throwable t) {
                            SpaceSimulation.LOGGER.error("[Entityify] id=" + id + " 扫描命中但取回失败", t);
                            continue;
                        }
                        ServerSubLevel got = serverSubLevel(container, data.uuid());
                        if (got != null) {
                            persistence.rememberStored(id, data.uuid(), chunkPos.x, chunkPos.z,
                                    local.storageIndex(), local.subLevelIndex());
                            SpaceSimulation.LOGGER.info("[Entityify] id={} 扫描找回副本（{} ms）",
                                    id, (System.nanoTime() - startedNs) / 1_000_000L);
                            return got;
                        }
                    }
                }
            }
        }
        SpaceSimulation.LOGGER.warn("[Entityify] id={} 扫描 {} 个 region 未找到副本（{} ms）",
                id, regions.length, (System.nanoTime() - startedNs) / 1_000_000L);
        return null;
    }

    /** 彻底删除一个子层级：REMOVED 会连同 sable 存储里的副本一起清掉（未改动的残留用它）。 */
    private static void removeAndDelete(ServerLevel level, ServerSubLevelContainer container, ServerSubLevel sub) {
        try {
            container.removeForceLoadTicket(sub, SubLevelLoadingTicketType.COMMAND_FORCED, Unit.INSTANCE);
        } catch (Throwable t) {
            SpaceSimulation.LOGGER.warn("[Entityify] removeForceLoadTicket 失败（忽略）", t);
        }
        try {
            if (!sub.isRemoved()) {
                container.removeSubLevel(sub, SubLevelRemovalReason.REMOVED);
            }
        } catch (Throwable t) {
            SpaceSimulation.LOGGER.warn("[Entityify] removeSubLevel(REMOVED) 失败（忽略）", t);
        }
    }

    /** 从 {@code asteroid_<id>} 名字里解析编号；-1 表示名字不合法。 */
    private static long parseId(String name) {
        try {
            return Long.parseLong(name.substring(NAME_PREFIX.length()));
        } catch (RuntimeException e) {
            return -1L;
        }
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

    /** 单颗驱动：解析位姿 → logicalPose/速度字段 → 物理体（物理体未就绪时安全跳过）。 */
    public static void drive(ServerLevel level, Instance inst, long tick) {
        ensureAnalytic(inst, tick);
        applyAnalytic(inst);

        RigidBodyHandle handle = RigidBodyHandle.of(inst.sub);
        if (handle == null || !handle.isValid()) {
            inst.physicsFailTicks++; // 物理体尚未注册：下个 tick 再试
            return;
        }
        try {
            handle.teleport(inst.sub.logicalPose().position(), inst.analyticQuat);
            // 这是一颗"运动学驱动"的小行星：位姿完全由解析轨道决定，物理引擎不该再给它速度。
            // teleport 不清速度，速度一旦残留，每个物理子步都会把刚体推离解析位置，而 sable
            // 会把引擎位姿写回 logicalPose（渲染/交互/网络同步都用它）→ 客户端抽搐。
            resetEngineVelocity(level, inst.sub);
            inst.physicsFailTicks = 0;
        } catch (RuntimeException e) {
            SpaceSimulation.LOGGER.warn("[Entityify] id={} teleport 失败（第 {} 次）: {}",
                    inst.id, inst.physicsFailTicks + 1, e.toString());
            SpaceSimulation.LOGGER.warn("[Entityify] teleport 异常堆栈", e);
            inst.physicsFailTicks++;
        }
    }

    /**
     * 把某个子层级的位姿与速度恢复成解析值（幂等，可在每个物理子步之后调用）。
     *
     * <p>sable 的 {@code SubLevelPhysicsSystem#updatePose} 在每个物理子步末尾都会：
     * ① 用**引擎位姿**覆盖 {@code logicalPose}；② 用"本步位姿 − 上一 tick 位姿"重算
     * {@code latestLinearVelocity / latestAngularVelocity}（m/s、rad/s）。这两个速度会随快照
     * 下发给客户端，供其插值与外推。我们的小行星是纯解析驱动的运动学体：引擎里没有速度、
     * 位姿也不该漂移；不纠正的话，那两个速度会在 0 与"一 tick 的解析位移×20"之间跳动，
     * 客户端据此外推就会偶发抽搐。这里把位姿与两个速度一次恢复成解析值。
     *
     * <p>只对我们的活动小行星生效；任何异常都直接放弃，退回 sable 的原行为。
     */
    public static void restoreAnalyticState(ServerSubLevel sub) {
        try {
            Instance inst = find(sub);
            if (inst == null || sub.getLevel() == null) {
                return;
            }
            ensureAnalytic(inst, sub.getLevel().getGameTime());
            applyAnalytic(inst);
        } catch (Throwable ignored) {
            // 纠正失败就让 sable 的原行为生效
        }
    }

    /** 活动表里找这个子层级（null = 不是我们的小行星）。 */
    @Nullable
    private static Instance find(SubLevel sub) {
        for (Map<Long, Instance> instances : BY_LEVEL.values()) {
            for (Instance inst : instances.values()) {
                if (inst.sub == sub) {
                    return inst;
                }
            }
        }
        return null;
    }

    /** 解析该 tick 的位姿与速度并缓存；同一 tick 内重复调用直接返回（顺带做一次不符诊断）。 */
    private static void ensureAnalytic(Instance inst, long tick) {
        if (tick == inst.analyticTick) {
            return;
        }
        AsteroidOrbit orbit = AsteroidProximityService.universe().orbitOf(inst.id);
        if (orbit == null) {
            return;
        }
        double[] p = orbit.positionAt(tick);
        if (inst.analyticTick != Long.MIN_VALUE) {
            // 解析速度：格/tick → m/s（20 tick/s）；角速度：rad/tick → rad/s
            inst.analyticVel.set((p[0] - inst.analyticPos[0]) * 20.0,
                    (p[1] - inst.analyticPos[1]) * 20.0,
                    (p[2] - inst.analyticPos[2]) * 20.0);
            inst.analyticAngVel.set(inst.spinAxis).mul(inst.spinRate * 20.0);
            reportMismatch(inst, tick, p);
        }
        inst.analyticPos[0] = p[0];
        inst.analyticPos[1] = p[1];
        inst.analyticPos[2] = p[2];
        inst.analyticQuat.set(spinQuaternion(inst, tick));
        inst.analyticTick = tick;
    }

    /** 把缓存的解析位姿与速度写回该子层级（渲染/交互/网络同步读的就是它们）。 */
    private static void applyAnalytic(Instance inst) {
        Pose3d live = inst.sub.logicalPose();
        live.position().set(inst.analyticPos[0], inst.analyticPos[1], inst.analyticPos[2]);
        live.orientation().set(inst.analyticQuat);
        inst.sub.latestLinearVelocity.set(inst.analyticVel);
        inst.sub.latestAngularVelocity.set(inst.analyticAngVel);
    }

    /** 自转四元数：绕固定轴、等角速度（tick 的确定函数）。 */
    private static Quaterniond spinQuaternion(Instance inst, long tick) {
        Quaterniond q = new Quaterniond();
        if (inst.spinRate != 0.0) {
            q.rotationAxis(inst.spinRate * tick, inst.spinAxis.x, inst.spinAxis.y, inst.spinAxis.z);
        }
        return q;
    }

    /**
     * 诊断：比较"物理系统写回的值"（位姿、线速度、角速度）与解析值。三者都正常时完全静默。
     */
    private static void reportMismatch(Instance inst, long tick, double[] analyticPos) {
        Pose3d live = inst.sub.logicalPose();
        double drift = Math.sqrt(sq(live.position().x - analyticPos[0])
                + sq(live.position().y - analyticPos[1])
                + sq(live.position().z - analyticPos[2]));
        double dLin = inst.sub.latestLinearVelocity.distance(inst.analyticVel);
        double dAng = inst.sub.latestAngularVelocity.distance(inst.analyticAngVel);
        if (drift < 0.05 && dLin < 2.0 && dAng < 0.05) {
            return;
        }
        if (tick - inst.lastDriftLogTick < 40L) {
            return;
        }
        inst.lastDriftLogTick = tick;
        SpaceSimulation.LOGGER.info(
                "[Entityify] id={} 物理与解析不符：位姿差 {} 格，线速度 {} vs {} m/s，角速度 {} vs {} rad/s（已按解析值纠正）",
                inst.id, fmt(drift),
                fmt(inst.sub.latestLinearVelocity.length()), fmt(inst.analyticVel.length()),
                fmt(inst.sub.latestAngularVelocity.length()), fmt(inst.analyticAngVel.length()));
    }

    /** 清零刚体在物理引擎里的线速度与角速度（{@code PhysicsPipeline#resetVelocity}）。 */
    private static void resetEngineVelocity(ServerLevel level, ServerSubLevel sub) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container != null) {
            container.physicsSystem().getPipeline().resetVelocity(sub);
        }
    }

    private static double sq(double v) {
        return v * v;
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    /**
     * 解析回溯：某颗小行星在指定 tick 的逻辑位姿。
     *
     * <p>交互判定的延迟补偿（{@code SubLevelInteractionLagCompensation}）需要"客户端点击那一刻"
     * 的小行星位姿。位置来自开普勒轨道、自转是绕固定轴的等角速度，二者都是 tick 的确定函数，
     * 因此任意 tick 都能精确求出，不需要插值近似 —— 这是本模组小行星相对普通子层级的便利。
     *
     * @param sub  目标子层级
     * @param tick 目标游戏刻度（与 {@link #drive} 使用同一时间基准）
     * @return 该 tick 的位姿；null 表示这个子层级不是我们实体化的小行星（调用方应退回当前位姿）
     */
    @Nullable
    public static Pose3d poseAt(SubLevel sub, long tick) {
        if (!(sub instanceof ServerSubLevel server)) {
            return null;
        }
        for (Map<Long, Instance> instances : BY_LEVEL.values()) {
            for (Instance inst : instances.values()) {
                if (inst.sub != server) {
                    continue;
                }
                AsteroidOrbit orbit = AsteroidProximityService.universe().orbitOf(inst.id);
                if (orbit == null) {
                    return null;
                }
                double[] p = orbit.positionAt(tick);
                Quaterniond q = new Quaterniond();
                if (inst.spinRate != 0.0) {
                    q.rotationAxis(inst.spinRate * tick, inst.spinAxis.x, inst.spinAxis.y, inst.spinAxis.z);
                }
                Pose3d pose = new Pose3d(sub.logicalPose());
                pose.position().set(p[0], p[1], p[2]);
                pose.orientation().set(q);
                return pose;
            }
        }
        return null;
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
