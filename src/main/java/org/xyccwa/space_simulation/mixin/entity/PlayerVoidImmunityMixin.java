package org.xyccwa.space_simulation.mixin.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家免疫虚空伤害。
 *
 * 1.21.1 链路：{@code Entity.baseTick -> Entity.checkBelowWorld()}
 * （Y &lt; minBuildHeight - 64 时触发）{@code -> LivingEntity.onBelowWorld()}
 * {@code -> hurt(damageSources().fellOutOfWorld(), 4.0F)}，每 tick 结算一次，玩家数次内即死亡。
 * {@code Player} 未覆写 {@code onBelowWorld}，故注入 {@link LivingEntity} 层并在 HEAD 处对玩家取消，
 * 其它生物/实体行为保持原版。
 *
 * 取消伤害后若放任其继续下坠，玩家会脱离区块加载范围、越掉越远而无法返回，
 * 因此在触发线处同时清除向下的垂直速度：玩家停在边界上，仍可自行飞回。
 */
@Mixin(LivingEntity.class)
public abstract class PlayerVoidImmunityMixin {

    @Inject(method = "onBelowWorld", at = @At("HEAD"), cancellable = true)
    private void spaceSim$noVoidDamageForPlayer(CallbackInfo ci) {
        if (!(((Object) this) instanceof Player player)) {
            return;
        }
        ci.cancel();
        Vec3 vel = player.getDeltaMovement();
        if (vel.y < 0.0) {
            player.setDeltaMovement(vel.x, 0.0, vel.z);
        }
    }
}
