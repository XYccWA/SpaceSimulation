package org.xyccwa.space_simulation.asteroid.entity;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.asteroid.AsteroidOrbit;
import org.xyccwa.space_simulation.asteroid.AsteroidProximityLoader;
import org.xyccwa.space_simulation.asteroid.AsteroidProximityService;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 小行星实体化服务（自动窗口 + 受保护门面）。
 *
 * <p>sable 是可选依赖：本类先用**资源探测**判断 sable 是否在运行时类路径上（不加载类，
 * 避免 mixin 报 "loaded too early"），再决定是否触碰 {@link AsteroidEntityifier}
 * —— 这样 sable 缺失时整个实体化层静默停用，模组照常加载。
 *
 * <h2>自动窗口（本类职责）</h2>
 * 以 {@link AsteroidProximityLoader#strongSet()}（强载半径内、每 tick 重算的精确集合）为唯一真值：
 * <ul>
 *   <li><b>装载</b>：集合内尚未实体化的颗进入候选表；按节流间隔（asteroidEntityifyLoadIntervalTicks）
 *       每轮最多实体化 asteroidEntityifyLoadsPerRound 颗，**离玩家最近者优先**，且不超过
 *       asteroidEntityifyMaxLoaded 上限。</li>
 *   <li><b>卸载</b>：活动实例若连续 asteroidEntityifyUnloadGraceTicks 不在强载集合中则卸载；
 *       预载索引分帧重建期间**不再冻结卸载**，改为把宽限期加长 {@link #REBUILD_GRACE_BONUS_TICKS}
 *       （重建期间集合可能不完整，加长 grace 已足够防误卸；冻结会让名额被旧实例长期占住）。</li>
 *   <li><b>不消费 poll 事件</b>：/asteroid loader 命令会消费同一批事件，因此这里只做集合差集，
 *       与命令互不干扰、天然幂等。</li>
 * </ul>
 *
 * <p>另外负责：每个维度首次 tick 对账持久化副本（{@link AsteroidEntityifier#reconcileStored}）、
 * 服务器停止前把已改动的小行星落盘并保存持久化台账。
 */
public final class AsteroidEntityifyService {

    private static final boolean SABLE_PRESENT = detectSable();

    /** 起步清理延迟（tick）：等 sable 把存档里的子层级恢复完再清残留。 */
    private static final long PURGE_DELAY_TICKS = 200L;

    /**
     * 预载索引分帧重建期间额外放宽的卸载宽限（tick，20 秒）。
     *
     * <p>重建会用新位置的相位窗口整轮替换旧索引，期间强载集合可能暂时不完整 —— 这是当初
     * "重建期间一律不卸载"的理由。但每次大位移（传送）都会触发重建，冻结卸载会让旧实例
     * 长期占住实体化名额（实测单颗滞留 200~4000 tick）。现在改为不冻结，只把宽限期加长。
     */
    private static final int REBUILD_GRACE_BONUS_TICKS = 400;

    /** 每个维度的自动窗口状态。 */
    private static final Map<ResourceKey<Level>, AutoState> STATES = new HashMap<>();

    private AsteroidEntityifyService() {}

    private static boolean detectSable() {
        try {
            return AsteroidEntityifyService.class.getClassLoader()
                    .getResource("dev/ryanhcode/sable/Sable.class") != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** sable 是否在运行时类路径上。 */
    public static boolean sablePresent() {
        return SABLE_PRESENT;
    }

    /** 实体化功能是否开启（配置）。 */
    public static boolean enabled() {
        try {
            return SpaceSimulationConfig.asteroidEntityifyEnabled.get();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 自动窗口状态（命令回显用）。 */
    public static final class AutoState {
        /** 待实体化候选：id → 首次成为候选的 tick。 */
        final Map<Long, Long> pendingSince = new LinkedHashMap<>();
        /** 活动实例最后一次出现在强载集合中的 tick。 */
        final Map<Long, Long> lastSeenStrong = new HashMap<>();
        boolean purged;
        long nextLoadTick;
        /** 本会话累计装入 / 卸下 / 起步清理。 */
        int totalLoaded;
        int totalUnloaded;
        int lastPurged;

        public int pendingCount() {
            return pendingSince.size();
        }

        public int totalLoaded() {
            return totalLoaded;
        }

        public int totalUnloaded() {
            return totalUnloaded;
        }

        public int lastPurged() {
            return lastPurged;
        }
    }

    public static AutoState state(ServerLevel level) {
        return STATES.computeIfAbsent(level.dimension(), k -> new AutoState());
    }

    // ---------- 每 tick ----------

    /** 服务端每 tick 驱动（ServerTickEvent.Post）。 */
    public static void tick(ServerTickEvent.Post event) {
        if (!SABLE_PRESENT || !enabled()) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        if (level == null) {
            return;
        }
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            return; // 无玩家：不评估窗口，保留现状
        }
        long tick = level.getGameTime();
        AutoState st = state(level);

        // 起步对账：sable 从存档恢复子层级有时间差，太早扫描会漏掉它们，因此推迟到进世界约 10 秒后
        if (!st.purged && tick >= PURGE_DELAY_TICKS) {
            st.purged = true;
            st.lastPurged = AsteroidEntityifier.reconcileStored(level);
        }

        AsteroidProximityLoader loader = AsteroidProximityService.loader();

        // 1) 每 tick 驱动已实体化小行星的位姿（轨道位置 + 自转）
        AsteroidEntityifier.tick(level, tick);

        // 2) 强载集合快照（每 tick 重算；拷贝，勿改内部集合）
        Set<Long> cur = new HashSet<>(loader.strongSet());

        ServerPlayer p = players.get(0);
        double px = p.getX();
        double py = p.getY();
        double pz = p.getZ();

        // 3) 卸载：迟滞；索引重建期间集合可能不完整 —— 不再冻结卸载，只把宽限期加长
        int unloaded = 0;
        List<AsteroidEntityifier.Instance> active = AsteroidEntityifier.all(level);
        Set<Long> activeIds = new HashSet<>();
        int grace = unloadGraceTicks();
        if (loader.indexBuilding()) {
            grace += REBUILD_GRACE_BONUS_TICKS;
        }
        for (AsteroidEntityifier.Instance inst : active) {
            activeIds.add(inst.id);
            if (cur.contains(inst.id)) {
                st.lastSeenStrong.put(inst.id, tick);
                continue;
            }
            Long seen = st.lastSeenStrong.get(inst.id);
            if (seen == null) {
                // 从未登记过的实例（命令路径 materialize 的、或强载集合早于本表更新的）：
                // 以实体化时刻为起点并回写。若沿用 getOrDefault(id, tick)，tick-lastSeen 恒为 0 → 永不卸载
                seen = inst.spawnedTick;
                st.lastSeenStrong.put(inst.id, seen);
            }
            if (tick - seen > grace) {
                AsteroidEntityifier.dematerialize(level, inst.id);
                st.lastSeenStrong.remove(inst.id);
                unloaded++;
                SpaceSimulation.LOGGER.info("[Entityify] id={} 离开强载范围，卸载（存活 {} tick）",
                        inst.id, tick - inst.spawnedTick);
            }
        }
        st.totalUnloaded += unloaded;
        st.lastSeenStrong.keySet().retainAll(activeIds);

        // 4) 候选表维护：进入强载但尚未实体化的颗
        st.pendingSince.keySet().removeIf(id -> !cur.contains(id) || AsteroidEntityifier.isMaterialized(level, id));
        for (long id : cur) {
            if (!AsteroidEntityifier.isMaterialized(level, id)) {
                st.pendingSince.putIfAbsent(id, tick);
            }
        }

        // 5) 装载：节流 + 每轮上限 + 最近优先
        int cap = maxLoaded();
        int interval = loadIntervalTicks();
        if (tick >= st.nextLoadTick && !st.pendingSince.isEmpty()
                && AsteroidEntityifier.count(level) < cap) {
            st.nextLoadTick = tick + interval;
            double radius2 = entityifyRadius() * entityifyRadius();
            List<Map.Entry<Long, Double>> cand = new ArrayList<>();
            for (long id : st.pendingSince.keySet()) {
                AsteroidOrbit orbit = AsteroidProximityService.universe().orbitOf(id);
                double[] pos = orbit.positionAt(tick);
                double dx = pos[0] - px, dy = pos[1] - py, dz = pos[2] - pz;
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 > radius2) {
                    continue; // 二次过滤：强载半径之外再按配置半径收窄（候选保留，玩家靠近后自然入选）
                }
                cand.add(Map.entry(id, d2));
            }
            cand.sort(java.util.Comparator.comparingDouble(Map.Entry::getValue));
            int budget = Math.min(loadsPerRound(), cand.size());
            int loaded = 0;
            for (int i = 0; i < budget; i++) {
                if (AsteroidEntityifier.count(level) >= cap) {
                    break;
                }
                long id = cand.get(i).getKey();
                String err = AsteroidEntityifier.materialize(level, id, tick);
                st.pendingSince.remove(id);
                if (err == null) {
                    st.lastSeenStrong.put(id, tick);
                    loaded++;
                } else {
                    SpaceSimulation.LOGGER.warn("[Entityify] 自动实体化 #{} 失败：{}", id, err);
                }
            }
            st.totalLoaded += loaded;
        }
    }

    /** 服务器停止前：已改动的落盘（走 sable holding），未改动的直接卸载；随后补存持久化台账。 */
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        if (!SABLE_PRESENT) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            int n = AsteroidEntityifier.dematerializeAll(level);
            if (n > 0) {
                SpaceSimulation.LOGGER.info("[Entityify] 停服卸载维度 {} 的活动小行星 {} 颗（改动已落盘）",
                        level.dimension().location(), n);
            }
            // 世界保存可能先于本事件发生，而落盘指针/改动标记正是刚才写的 —— 主动补一次，不等下次自动保存。
            try {
                level.getDataStorage().save();
            } catch (Throwable t) {
                SpaceSimulation.LOGGER.warn("[Entityify] 停服保存小行星持久化台账失败", t);
            }
        }
        STATES.clear();
    }

    // ---------- 配置读取 ----------

    private static int maxLoaded() {
        try {
            return SpaceSimulationConfig.asteroidEntityifyMaxLoaded.get();
        } catch (Throwable t) {
            return 24;
        }
    }

    private static int loadIntervalTicks() {
        try {
            return Math.max(1, SpaceSimulationConfig.asteroidEntityifyLoadIntervalTicks.get());
        } catch (Throwable t) {
            return 20;
        }
    }

    private static int loadsPerRound() {
        try {
            return Math.max(1, SpaceSimulationConfig.asteroidEntityifyLoadsPerRound.get());
        } catch (Throwable t) {
            return 1;
        }
    }

    private static int unloadGraceTicks() {
        try {
            return Math.max(0, SpaceSimulationConfig.asteroidEntityifyUnloadGraceTicks.get());
        } catch (Throwable t) {
            return 100;
        }
    }

    private static double entityifyRadius() {
        try {
            return Math.max(16.0, SpaceSimulationConfig.asteroidEntityifyRadius.get());
        } catch (Throwable t) {
            return 1000.0;
        }
    }

    /** 供命令回显的一行状态。 */
    public static String describe(ServerLevel level) {
        AutoState st = state(level);
        return String.format(Locale.ROOT,
                "自动窗口：活动 %d/%d 颗 · 候选 %d 颗 · 本会话装入 %d / 卸下 %d · 起步清理 %d 颗 · 上次实体化耗时 %d ms",
                AsteroidEntityifier.count(level), maxLoaded(), st.pendingCount(),
                st.totalLoaded(), st.totalUnloaded(), st.lastPurged, AsteroidEntityifier.lastMaterializeMs());
    }
}
