package org.xyccwa.space_simulation.mixin.sable;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.xyccwa.space_simulation.asteroid.entity.AsteroidEntityifier;

/**
 * 小行星位姿/速度的时间轴纠正。
 *
 * <p>{@code SubLevelPhysicsSystem#updatePose} 在每个物理子步末尾：
 * ① 用**引擎位姿**覆盖子层级的 {@code logicalPose}（渲染、交互判定与网络同步都用它）；
 * ② 用"本步位姿 − 上一 tick 位姿"重算 {@code latestLinearVelocity/latestAngularVelocity}，
 * 这两个速度会随快照下发给客户端供其插值与外推。
 *
 * <p>我们的小行星是纯解析驱动的运动学体（位置来自开普勒轨道、自转是等角速度），引擎里没有
 * 速度、位姿也不该漂移。若不纠正，速度字段会在 0 与"一 tick 的解析位移×20"之间跳动，
 * 客户端据此外推就会出现偶发抽搐。这里在 sable 写完的**同一位置之后**立刻把三者恢复成解析值
 * （{@link AsteroidEntityifier#restoreAnalyticState}），任何读取者看到的都是解析状态。
 *
 * <p>只对我们的活动小行星生效（其它子层级、玩家自己的飞船一概不动），且异常静默。
 */
@Mixin(SubLevelPhysicsSystem.class)
public abstract class SubLevelPhysicsSystemMixin {

    @Inject(method = "updatePose", at = @At("TAIL"))
    private void spaceSim$restoreAnalyticAsteroidState(ServerSubLevel subLevel, CallbackInfo ci) {
        try {
            AsteroidEntityifier.restoreAnalyticState(subLevel);
        } catch (Throwable ignored) {
            // 纠正失败就让 sable 的原行为生效
        }
    }
}
