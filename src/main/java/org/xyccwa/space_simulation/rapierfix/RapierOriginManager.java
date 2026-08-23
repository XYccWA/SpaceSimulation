package org.xyccwa.space_simulation.rapierfix;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3d;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rapier 原生引擎的"场景级浮点原点重基"状态管理（rapierfix）。
 *
 * 问题：sable 的原生 rapier 引擎（标准 rapier3d crate，Real=f32）直接以**父世界坐标**
 * 存储刚体平移。本项目玩家活动区在小行星带圆环（半径 500~1000 万块，跨度 2000 万块），
 * f32 在 500 万~2000 万量级的 ULP = 0.25~1 块：飞船低速运动位移被吞、刚体间相对距离
 * 与碰撞判定量子化、出现静止偏移/步进式卡顿。
 *
 * 修复：每个物理场景（sceneHandle）维护一个"场景原点" O（对齐 16 块），所有进入
 * 原生引擎的**世界域坐标**减去 O、回读时加回 O。**O 是动态的**：玩家偏离 O 超过
 * 阈值（{@link #REBASE_THRESHOLD}，100 万块）时，场景整体重基到玩家新区域——
 * 由于玩家活动区是环绕原点的圆环（跨度 2000 万块），固定 O 无法覆盖，必须动态跟随。
 *
 * 重基（一次性，由 RapierPhysicsPipelineMixin 在物理帧末执行）：
 * - 移除全部已记录的 global 父世界地形 chunk（旧 O 域）；
 * - 更新 origin 到新 O；重传 chunk（新 O 域）；teleport 重定位刚体（世界位姿不变，
 *   set_position 保留速度）；plot 局部域内容（octree/COM/约束锚点）不受原点影响，无需重传。
 *
 * 平移只作用于"世界域量"：刚体 pose、global chunk、约束电机线性轴目标。不平移（原生
 * 侧已用 f64/整数精确处理）：plot 局部域 i32 坐标、f64 COM、f64 约束锚点、相对 COM 的
 * 碰撞点、速度/力/角电机。
 *
 * 隔离：本类属于独立 mixin 配置 space_simulation.rapier.mixins.json 的 rapierfix 包；
 * 通过配置 rapierRebaseEnabled=false 一键关闭（所有包装原样透传），或删除
 * mods.toml 的 [[mixins]] 块 + 本包 + mixin/rapierfix 包整体移除（无残留依赖）。
 */
public final class RapierOriginManager {

    private static final Logger LOGGER = LogManager.getLogger("space_simulation");

    /** 对齐粒度：16 块（chunk 边长），保证整数坐标平移后可整除 */
    private static final int ALIGN = 16;

    /** 玩家偏离原点超过该阈值（块）时触发场景重基（测试可调小） */
    public static final double REBASE_THRESHOLD = 100000.0;

    /** 每场景状态：原点 + 世界域内容记录 */
    private static final class SceneState {
        volatile Vector3d origin;
        final Set<Long> globalChunks = ConcurrentHashMap.newKeySet(); // 世界 section 坐标（packed）
        final Set<Integer> bodies = ConcurrentHashMap.newKeySet();    // 刚体 id

        SceneState(final Vector3d origin) {
            this.origin = origin;
        }
    }

    private static final Map<Long, SceneState> SCENES = new ConcurrentHashMap<>();

    private RapierOriginManager() {
    }

    /** @return 重基功能是否启用（配置 rapierRebaseEnabled） */
    public static boolean enabled() {
        return SpaceSimulationConfig.rapierRebaseEnabled.get();
    }

    /**
     * 取得（必要时按给定世界坐标自动设定）场景原点。
     * 自动设定：首个世界域坐标（刚体 pose / global 地形 chunk / 约束电机目标）向下对齐 16 块；
     * 配置 rapierRebaseOriginX/Y/Z 任一非 0 时优先使用配置值。
     *
     * @param scene 场景句柄（JNI sceneHandle）
     * @param autoX 用于自动设定的世界 X（仅当原点尚未设定时生效）
     * @return 该场景的原点（世界块坐标，16 的倍数）
     */
    public static Vector3d originFor(final long scene, final double autoX, final double autoY, final double autoZ) {
        return SCENES.computeIfAbsent(scene, s -> {
            final double cx = SpaceSimulationConfig.rapierRebaseOriginX.get();
            final double cy = SpaceSimulationConfig.rapierRebaseOriginY.get();
            final double cz = SpaceSimulationConfig.rapierRebaseOriginZ.get();
            final Vector3d origin;
            if (cx != 0.0 || cy != 0.0 || cz != 0.0) {
                origin = new Vector3d(align(cx), align(cy), align(cz));
            } else {
                origin = new Vector3d(align(autoX), align(autoY), align(autoZ));
            }
            LOGGER.info("[rapierfix] scene {} origin set to ({}, {}, {}) (first world coord {}, {}, {})",
                    scene, format(origin.x), format(origin.y), format(origin.z),
                    format(autoX), format(autoY), format(autoZ));
            return new SceneState(origin);
        }).origin;
    }

    /** @return 场景原点；未设定时为 ZERO（等价于不重基） */
    public static Vector3d originFor(final long scene) {
        final SceneState state = SCENES.get(scene);
        return state != null ? state.origin : new Vector3d();
    }

    /** 场景 → 世界坐标（+O），就地修改 */
    public static void toWorld(final long scene, final Vector3d pos) {
        final Vector3d origin = originFor(scene);
        pos.add(origin.x, origin.y, origin.z);
    }

    /**
     * 平移 7 元位姿数组（[x,y,z,qx,qy,qz,qw]）为场景坐标。
     * 仅平移位置分量；未启用时返回原数组。
     */
    public static double[] shiftPose(final long scene, final double[] pose) {
        if (!enabled()) {
            return pose;
        }
        final Vector3d origin = originFor(scene, pose[0], pose[1], pose[2]);
        return new double[]{
                pose[0] - origin.x, pose[1] - origin.y, pose[2] - origin.z,
                pose[3], pose[4], pose[5], pose[6]
        };
    }

    /**
     * 世界坐标 → 场景坐标（−O），**只读**：原点未设定时不触发自动设定也不平移。
     * 用于单分量调用点（如约束电机目标），其场景原点必然已由刚体/地形创建时设定。
     */
    public static double toSceneReadOnly(final long scene, final double world) {
        final SceneState state = SCENES.get(scene);
        return state != null ? world - state.origin.x : world;
    }

    /** 记录一个 global（父世界地形）chunk 的世界 section 坐标（重基时需重传） */
    public static void recordGlobalChunk(final long scene, final int x, final int y, final int z) {
        final SceneState state = SCENES.get(scene);
        if (state != null) {
            state.globalChunks.add(pack(x, y, z));
        }
    }

    /** 移除一个 global chunk 记录 */
    public static void forgetGlobalChunk(final long scene, final int x, final int y, final int z) {
        final SceneState state = SCENES.get(scene);
        if (state != null) {
            state.globalChunks.remove(pack(x, y, z));
        }
    }

    /** @return 该场景已记录的所有 global chunk 世界 section 坐标（x,y,z 三元组迭代） */
    public static Iterable<int[]> globalChunks(final long scene) {
        final SceneState state = SCENES.get(scene);
        return () -> (state == null ? Set.<Long>of() : state.globalChunks).stream()
                .map(RapierOriginManager::unpack).iterator();
    }

    /** 记录一个子层级刚体 id（重基时需重定位） */
    public static void recordBody(final long scene, final int id) {
        final SceneState state = SCENES.get(scene);
        if (state != null) {
            state.bodies.add(id);
        }
    }

    /** @return 该场景所有刚体 id */
    public static Iterable<Integer> bodies(final long scene) {
        final SceneState state = SCENES.get(scene);
        return state != null ? state.bodies : Set.of();
    }

    /**
     * @return 给定世界坐标是否偏离当前原点超过重基阈值（需要触发重基）
     */
    public static boolean shouldRebase(final long scene, final double wx, final double wy, final double wz) {
        final SceneState state = SCENES.get(scene);
        if (state == null) {
            return false;
        }
        final Vector3d o = state.origin;
        return Math.abs(wx - o.x) > REBASE_THRESHOLD
                || Math.abs(wy - o.y) > REBASE_THRESHOLD
                || Math.abs(wz - o.z) > REBASE_THRESHOLD;
    }

    /** @return 按给定世界坐标对齐 16 块的新原点 */
    public static Vector3d newOriginFor(final double wx, final double wy, final double wz) {
        return new Vector3d(align(wx), align(wy), align(wz));
    }

    /** 更新场景原点（一次性重基流程；调用方负责先迁移 chunk/刚体内容再更新） */
    public static void updateOrigin(final long scene, final Vector3d newOrigin) {
        final SceneState state = SCENES.get(scene);
        if (state != null) {
            state.origin = newOrigin;
        }
    }

    private static long pack(final int x, final int y, final int z) {
        return ((long) x & 0x1FFFFFL) << 42 | ((long) y & 0xFFFFFL) << 20 | ((long) z & 0x1FFFFFL);
    }

    private static int[] unpack(final long packed) {
        final int x = (int) (packed >> 42 & 0x1FFFFFL);
        final int y = (int) (packed >> 20 & 0xFFFFFL);
        final int z = (int) (packed & 0x1FFFFFL);
        // 符号扩展（21 位有符号）
        return new int[]{
                x >= 0x100000 ? x - 0x200000 : x,
                y >= 0x80000 ? y - 0x100000 : y,
                z >= 0x100000 ? z - 0x200000 : z
        };
    }

    private static String format(double v) {
        return String.format("%.1f", v);
    }

    /** @return 将 v 向下对齐到 ALIGN 的倍数 */
    private static double align(final double v) {
        return Math.floor(v / ALIGN) * ALIGN;
    }
}
