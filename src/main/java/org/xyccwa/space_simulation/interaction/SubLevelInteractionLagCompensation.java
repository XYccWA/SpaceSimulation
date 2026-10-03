package org.xyccwa.space_simulation.interaction;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;
import org.xyccwa.space_simulation.asteroid.entity.AsteroidEntityifier;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;
import org.xyccwa.space_simulation.diagnostic.InteractionDiagnostics;

/**
 * 子层级方块交互的**延迟补偿**（挖掘 / 放置共用）。
 *
 * <h2>问题</h2>
 * 服务端的交互距离校验是 {@code Player#canInteractWithBlock(pos, 1.0)}：范围 = 4.5 + 1.0 格。
 * sable 对子层级方块有专门处理（把眼睛逆变换到子层级局部空间再比距离），但它读的是
 * <b>服务端当刻的逻辑位姿 + 服务端权威眼位</b>。而客户端的本地预测永远领先服务端权威位置
 * 若干 tick，小行星又以约 2.4 格/tick 移动 —— 服务端算出的距离比玩家所见多出
 * "Δ tick × 速度"，顶穿 6 格阈值，服务端判为 too far；客户端本地预测随后回滚，
 * 表现为"破坏一帧后方块又出现"。
 *
 * <h2>为什么不是"回溯历史"</h2>
 * 客户端位置位于服务端的<b>未来</b>，权威位置历史（过去）里没有对应行，回溯只会匹配到最新一行
 * （即服务端当前位置），等于没有补偿。因此几何必须直接采信客户端上报的位置。
 *
 * <h2>做法</h2>
 * 只在原判定失败后、且方块确实属于 sable 子层级时：
 * <ol>
 *   <li>取客户端最近上报的位置（{@link InteractionTimeline}，要求新鲜且与权威位置偏差在钳制上限内，
 *       偏差上限 = {@code subLevelInteractionLagTicks × MAX_SPEED}）；不可信时不采信（但会记日志，便于调参）；</li>
 *   <li>用该眼位、在"当刻及前 2 tick"三个候选位姿里取最小距离 —— 多取两 tick 是为了覆盖
 *       Sable 客户端插值位姿相对服务端当刻位姿的滞后；我们实体化的小行星位姿是解析的
 *       （{@link AsteroidEntityifier#poseAt}），可以直接精确求出，其它子层级退回
 *       {@code logicalPose()/lastPose()}；</li>
 *   <li>通过就放行。</li>
 * </ol>
 * 本类**只放行、从不拒绝**：任何异常、缺少数据、钳制不通过都返回 false，交互继续走 sable
 * 与原版判定，因此最坏情况等价于没有补偿。
 */
public final class SubLevelInteractionLagCompensation {

    private SubLevelInteractionLagCompensation() {
    }

    /**
     * @return true = 在玩家画面/准星所处的时间轴上这次交互合法（调用方应直接放行）
     */
    public static boolean allow(Player player, BlockPos pos, double slop) {
        try {
            if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return false;
            }
            if (!SpaceSimulationConfig.subLevelInteractionLagCompensation.get()) {
                return false;
            }

            final Level level = player.level();
            final SubLevel sub = Sable.HELPER.getContaining(level, pos);
            if (sub == null) {
                return false; // 普通世界方块：完全不干预
            }

            final double range = player.blockInteractionRange() + slop;
            final double rangeSq = range * range;
            final Vec3 eye = player.getEyePosition();

            // 服务端当刻已经通过：交给 sable 自己的判定，不重复干预
            final double currentDist2 = distanceSq(sub.logicalPose(), eye, pos);
            if (currentDist2 < rangeSq) {
                return false;
            }

            final int maxLag = SpaceSimulationConfig.subLevelInteractionLagTicks.get();
            if (maxLag <= 0) {
                return false;
            }
            final InteractionTimeline.ClientSample sample = InteractionTimeline.clientSample(serverPlayer, maxLag);
            if (sample == null) {
                InteractionDiagnostics.reportLagAttempt(player, pos, Math.sqrt(currentDist2), range,
                        false, Double.NaN, -1, Double.NaN, Double.NaN);
                return false;
            }
            if (!sample.trusted()) {
                InteractionDiagnostics.reportLagAttempt(player, pos, Math.sqrt(currentDist2), range,
                        false, Double.NaN, sample.ageTicks(), sample.deviation(), sample.limit());
                return false;
            }

            final long now = level.getGameTime();
            final Vec3 clientEye = new Vec3(sample.x(), sample.y() + player.getEyeHeight(), sample.z());
            final double clientDist2 = minDistanceSq(sub, clientEye, pos, now);
            final boolean allowed = clientDist2 < rangeSq;
            InteractionDiagnostics.reportLagAttempt(player, pos, Math.sqrt(currentDist2), range,
                    allowed, Math.sqrt(clientDist2), sample.ageTicks(), sample.deviation(), sample.limit());
            return allowed;
        } catch (Throwable ignored) {
            // 补偿永不允许影响正常交互
            return false;
        }
    }

    /**
     * 用给定眼位，在"当刻及前 2 tick"三个候选位姿里取最小距离。
     *
     * <p>为什么多取两 tick：客户端看到的是 Sable 的插值位姿，通常落后服务端当刻位姿 1~2 tick，
     * 仅用当刻位姿会残留 2.4~4.8 格的偏差（阈值余量只有 1.5 格）。
     */
    private static double minDistanceSq(SubLevel sub, Vec3 eye, BlockPos pos, long now) {
        double best = Double.MAX_VALUE;
        for (int back = 0; back <= 2; back++) {
            Pose3dc pose = AsteroidEntityifier.poseAt(sub, now - back);
            if (pose == null) {
                pose = back == 0 ? sub.logicalPose() : sub.lastPose();
            }
            best = Math.min(best, distanceSq(pose, eye, pos));
        }
        return best;
    }

    /** 与 sable 判定同源的几何：把眼位逆变换到子层级局部空间，再取方块 AABB 到它的距离平方。 */
    private static double distanceSq(Pose3dc pose, Vec3 eye, BlockPos pos) {
        final Vector3dc local = pose.transformPositionInverse(JOMLConversion.toJOML(eye));
        return new AABB(pos).distanceToSqr(new Vec3(local.x(), local.y(), local.z()));
    }
}
