package org.xyccwa.space_simulation.mixin.rapierfix;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.xyccwa.space_simulation.rapierfix.RapierOriginManager;

import java.util.HashMap;
import java.util.Map;

/**
 * rapierfix：场景级浮点原点重基（sable Rapier 物理精度修复）。
 *
 * 在 {@code RapierPhysicsPipeline}（sable_rapier 模块，不在编译 classpath，故用
 * 字符串目标）的所有**世界域坐标**进出原生引擎的边界上做 ±O 平移，使原生 f32 引擎
 * 内的坐标量级远离世界原点（玩家活动区 500~2000 万块，f32 ULP 0.25~1 块）。
 *
 * **动态重基**：玩家偏离场景原点超过阈值（100 万块）时，把场景整体重基到玩家新区域
 * ——重传全部已加载的 global 地形 chunk、重定位所有子层级刚体（teleport）；
 * plot 局部域内容（octree/COM/约束锚点）不受原点影响，无需重传。
 *
 * 平移范围（原生 Rust 源码核实的域语义）：
 * - 必须平移（世界域量）：刚体 pose（createSubLevel/teleportObject）、
 *   global 父世界地形 chunk（addChunk/removeChunk/changeBlock）、
 *   约束电机线性轴目标（setConstraintMotor，见 RapierConstraintHandleMixin）
 * - 不平移（原生侧已精确处理）：plot 局部域 i32 坐标、f64 COM（setCenterOfMass/
 *   setMassProperties）、f64 约束锚点（joints）、相对 COM 的碰撞点回读、速度/力/角电机
 *
 * 已知限制（尝试性修复范围外）：
 * - Create 机构（KinematicContraption）与 RapierBoxHandle 未重基，重基时也不迁移——
 *   机构/盒体在玩家远离原点后与飞船物理域不一致（不会互碰，无崩溃）；
 * - 玩家在 500 万~2000 万块处的**渲染**抖动（float 矩阵）属渲染层，本方案不覆盖。
 *
 * 隔离：本类属于独立 mixin 配置 space_simulation.rapier.mixins.json；
 * 配置 rapierRebaseEnabled=false 时所有包装原样透传；删除 mods.toml 的 [[mixins]] 块
 * + mixin/rapierfix 包 + rapierfix 包即可整体移除（无编译期依赖）。
 */
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline")
public abstract class RapierPhysicsPipelineMixin {

    private static final Logger LOGGER = LogManager.getLogger("space_simulation");

    @Shadow
    @Final
    private ServerLevel level;

    /** 目标类 protected 方法（返回 long，无需引用 rapier 包类型）——取得场景句柄 */
    @Shadow
    protected abstract long getSceneHandle();

    /** 活跃子层级：id → ServerSubLevel（重基时重定位刚体用） */
    @Shadow
    @Final
    private Int2ObjectMap<dev.ryanhcode.sable.sublevel.ServerSubLevel> activeSubLevels;

    /** 目标类方法（编译 classpath 无目标类，用 @Shadow 声明；重基重传/重定位时调用） */
    @Shadow
    public abstract void handleChunkSectionRemoval(int x, int y, int z);

    @Shadow
    public abstract void handleChunkSectionAddition(LevelChunkSection section, int x, int y, int z, boolean uploadDataIfGlobal);

    @Shadow
    public abstract void teleport(dev.ryanhcode.sable.api.physics.PhysicsPipelineBody body,
                                  org.joml.Vector3dc position, org.joml.Quaterniondc orientation);

    private static java.lang.reflect.Method sceneHandleMethod;

    private long spaceSim$sceneHandle() {
        return this.getSceneHandle();
    }

    /**
     * 子层级刚体创建：pose（世界域）→ 场景坐标；记录刚体 id 供重基迁移。
     */
    @ModifyArgs(
            method = "add",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;createSubLevel(JI[D)V")
    )
    private void spaceSim$createSubLevel(final Args args) {
        final long scene = args.get(0);
        final double[] pose = args.get(2);
        RapierOriginManager.recordBody(scene, args.get(1));
        args.set(2, RapierOriginManager.shiftPose(scene, pose));
    }

    /**
     * 刚体传送：位置（世界域）→ 场景坐标。
     */
    @ModifyArgs(
            method = "teleport",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;teleportObject(JIDDDDDDD)V")
    )
    private void spaceSim$teleportObject(final Args args) {
        if (!RapierOriginManager.enabled()) {
            return;
        }
        final long scene = args.get(0);
        final double x = args.get(2);
        final double y = args.get(3);
        final double z = args.get(4);
        final Vector3d origin = RapierOriginManager.originFor(scene, x, y, z);
        args.set(2, x - origin.x);
        args.set(3, y - origin.y);
        args.set(4, z - origin.z);
    }

    /**
     * 刚体位姿回读：场景坐标 → 世界坐标（+O 还原，Java 侧 logicalPose 保持世界域）。
     */
    @Inject(method = "readPose", at = @At("RETURN"))
    private void spaceSim$readPose(final dev.ryanhcode.sable.sublevel.ServerSubLevel subLevel, final Pose3d dest,
                                   final CallbackInfoReturnable<Pose3d> cir) {
        if (!RapierOriginManager.enabled()) {
            return;
        }
        // 诊断：引擎内坐标若仍达百万级，说明重基未生效（原点未设定或平移未执行）
        final double maxAbs = Math.max(Math.abs(dest.position().x),
                Math.max(Math.abs(dest.position().y), Math.abs(dest.position().z)));
        if (maxAbs > 1_000_000.0) {
            LOGGER.warn("[rapierfix] engine pose {} is {} blocks from scene origin — rebase NOT effective for this body",
                    dest.position(), format(maxAbs));
        }
        RapierOriginManager.toWorld(this.spaceSim$sceneHandle(), dest.position());
    }

    /**
     * 地形 chunk 上传：global=true（父世界地形，世界域）→ 场景坐标并记录；
     * global=false（plot 子层级，plotyard 局部域）保持原样。
     */
    @ModifyArgs(
            method = "handleChunkSectionAddition",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;addChunk(JIII[IZI)V")
    )
    private void spaceSim$addChunk(final Args args) {
        if (!RapierOriginManager.enabled()) {
            return;
        }
        final boolean global = args.get(5);
        final long scene = args.get(0);
        final int x = args.get(1);
        final int y = args.get(2);
        final int z = args.get(3);
        if (global) {
            RapierOriginManager.recordGlobalChunk(scene, x, y, z);
        } else {
            return; // plot chunk：plotyard 域，不平移
        }
        // chunk 坐标 → 块坐标用于自动设定原点；平移 chunk 坐标 = −O/16（O 对齐 16）
        final Vector3d origin = RapierOriginManager.originFor(scene,
                (double) (x << 4), (double) (y << 4), (double) (z << 4));
        args.set(1, (int) (x - origin.x / 16));
        args.set(2, (int) (y - origin.y / 16));
        args.set(3, (int) (z - origin.z / 16));
    }

    /**
     * 地形 chunk 移除：global=true 平移并移除记录；plot 保持。
     */
    @ModifyArgs(
            method = "handleChunkSectionRemoval",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;removeChunk(JIIIZ)V")
    )
    private void spaceSim$removeChunk(final Args args) {
        if (!RapierOriginManager.enabled()) {
            return;
        }
        final boolean global = args.get(4);
        final long scene = args.get(0);
        final int x = args.get(1);
        final int y = args.get(2);
        final int z = args.get(3);
        if (global) {
            RapierOriginManager.forgetGlobalChunk(scene, x, y, z);
        } else {
            return;
        }
        final Vector3d origin = RapierOriginManager.originFor(scene,
                (double) (x << 4), (double) (y << 4), (double) (z << 4));
        args.set(1, (int) (x - origin.x / 16));
        args.set(2, (int) (y - origin.y / 16));
        args.set(3, (int) (z - origin.z / 16));
    }

    /**
     * 方块变化：plotgrid 内的方块（plotyard 域）不平移；父世界方块（世界域）−O。
     */
    @ModifyArgs(
            method = "handleBlockChange",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;changeBlock(JIIII)V")
    )
    private void spaceSim$changeBlock(final Args args) {
        if (!RapierOriginManager.enabled()) {
            return;
        }
        final long scene = args.get(0);
        final int x = args.get(1);
        final int y = args.get(2);
        final int z = args.get(3);
        // plotgrid 内方块属于子层级存储区（plotyard 域），不平移
        final SubLevelContainer container = SubLevelContainer.getContainer(this.level);
        if (container != null && container.inBounds(x >> 4, z >> 4)) {
            return;
        }
        final Vector3d origin = RapierOriginManager.originFor(scene,
                (double) x, (double) y, (double) z);
        args.set(1, (int) (x - origin.x));
        args.set(2, (int) (y - origin.y));
        args.set(3, (int) (z - origin.z));
    }

    /**
     * 每个物理帧末（postPhysicsTicks，所有 substep 与 readPose 完成之后）：玩家偏离
     * 场景原点超过阈值时触发整体重基。选在物理帧末而非 tick() 开头，可保证重基不打断
     * 本帧物理事件、且所有刚体位姿已用统一 origin 写回——下一帧干净地用新 origin，
     * 消除重基瞬间的一帧抽搐（之前在 tick() 开头重基，本帧步进/readPose 会混用新旧 origin）。
     */
    @Inject(method = "postPhysicsTicks", at = @At("TAIL"))
    private void spaceSim$maybeRebase(final CallbackInfo ci) {
        if (!RapierOriginManager.enabled()) {
            return;
        }
        final long scene = this.spaceSim$sceneHandle();
        ServerPlayer player = null;
        for (final ServerPlayer p : this.level.players()) {
            player = p;
            break;
        }
        if (player == null) {
            return;
        }
        final double px = player.getX();
        final double py = player.getY();
        final double pz = player.getZ();
        if (!RapierOriginManager.shouldRebase(scene, px, py, pz)) {
            return;
        }
        this.spaceSim$rebase(scene, RapierOriginManager.newOriginFor(px, py, pz));
    }

    /**
     * 场景整体重基：先把全部 global 地形 chunk 从引擎移除（旧 O 域）、记录刚体世界位姿
     * 与速度，再更新原点，最后重传 chunk（新 O 域）并重定位刚体（teleport 后恢复原速度）。
     * plot 局部域内容（octree/COM/约束锚点）不受原点影响，无需重传。
     */
    private void spaceSim$rebase(final long scene, final Vector3d newOrigin) {
        final Vector3d oldOrigin = RapierOriginManager.originFor(scene);
        if (oldOrigin.equals(newOrigin)) {
            return;
        }
        LOGGER.info("[rapierfix] scene {} rebasing origin from ({}, {}, {}) to ({}, {}, {})",
                scene, format(oldOrigin.x), format(oldOrigin.y), format(oldOrigin.z),
                format(newOrigin.x), format(newOrigin.y), format(newOrigin.z));

        try {
            // 0) 快照全部 global chunk（第 1 步移除时会清空记录，重传必须基于快照）
            final java.util.List<int[]> chunks = new java.util.ArrayList<>();
            for (final int[] xyz : RapierOriginManager.globalChunks(scene)) {
                chunks.add(xyz);
            }

            // 1) 移除全部 global chunk（旧 O 域；包装会自动 -O_old 并清除记录）
            for (final int[] xyz : chunks) {
                try {
                    this.handleChunkSectionRemoval(xyz[0], xyz[1], xyz[2]);
                } catch (final Exception e) {
                    LOGGER.warn("[rapierfix] rebase: failed to remove chunk ({}, {}, {}), skipping", xyz[0], xyz[1], xyz[2], e);
                }
            }

            // 2) 记录全部刚体的世界位姿（logicalPose 为世界域 double；速度由 teleport 的
            //    set_position 保留，不在此重施——避免叠加导致速度翻倍）
            final Map<Integer, Pose3d> bodyPoses = new HashMap<>();
            for (final Integer id : RapierOriginManager.bodies(scene)) {
                final dev.ryanhcode.sable.sublevel.ServerSubLevel sub = this.activeSubLevels.get(id.intValue());
                if (sub != null && !sub.isRemoved()) {
                    bodyPoses.put(id, new Pose3d(sub.logicalPose()));
                } else {
                    LOGGER.debug("[rapierfix] rebase: body {} not in activeSubLevels, skipping", id);
                }
            }

            // 3) 更新原点（此后所有包装使用新 O）
            RapierOriginManager.updateOrigin(scene, newOrigin);

            // 4) 重传全部 global chunk（新 O 域；包装自动 -O_new 并重新记录）
            for (final int[] xyz : chunks) {
                try {
                    final LevelChunk chunk = this.level.getChunk(xyz[0], xyz[1]);
                    if (chunk == null || chunk.isEmpty()) {
                        continue;
                    }
                    final LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(xyz[1]));
                    if (section == null) {
                        continue;
                    }
                    this.handleChunkSectionAddition(section, xyz[0], xyz[1], xyz[2], true);
                } catch (final Exception e) {
                    LOGGER.warn("[rapierfix] rebase: failed to re-upload chunk ({}, {}, {}), skipping", xyz[0], xyz[1], xyz[2], e);
                }
            }

            // 5) 重定位刚体（teleport 自动 -O_new；set_position 保留速度，不需重施）。
            //    不同步 lastPose：客户端插值 renderPose = lerp(lastPose, logicalPose, pt)，
            //    重基只换引擎坐标系、刚体世界位姿不变（logicalPose 保持旧世界坐标），
            //    lastPose 保留旧值即可让插值连续；若强行把 lastPose 同步到 logicalPose，
            //    反而让插值两端重合 = 刚体视觉暂停一帧（"刹停→下一帧恢复"）。
            for (final Map.Entry<Integer, Pose3d> entry : bodyPoses.entrySet()) {
                final dev.ryanhcode.sable.sublevel.ServerSubLevel sub = this.activeSubLevels.get(entry.getKey().intValue());
                if (sub == null || sub.isRemoved()) {
                    continue;
                }
                this.teleport(sub, entry.getValue().position(), entry.getValue().orientation());
            }

            LOGGER.info("[rapierfix] scene {} rebase complete ({} chunks, {} bodies)",
                    scene, chunks.size(), bodyPoses.size());
        } catch (final Exception e) {
            LOGGER.error("[rapierfix] rebase FAILED for scene {}", scene, e);
        }
    }

    private static String format(double v) {
        return String.format("%.1f", v);
    }
}
