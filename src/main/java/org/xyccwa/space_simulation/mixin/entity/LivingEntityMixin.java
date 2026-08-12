package org.xyccwa.space_simulation.mixin.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xyccwa.space_simulation.api.EntityRotation;

/**
 * 禁用原版坠落伤害：1.21.1 的伤害链路是
 * {@code Entity.move -> checkFallDamage -> Block.fallOn(默认实现) -> entity.causeFallDamage -> hurt}。
 * {@code causeFallDamage} 是所有坠落伤害（普通地面、蜂蜜/史莱姆/干草减伤、滴水石、铁砧等）的唯一汇聚点，
 * 在此 HEAD 取消即可整体关闭，且不影响 fallDistance 记账、落地音效粒子与方块交互。
 *
 * 与 {@code EntityMixin.spaceSim$noSuffocation} 同理：只对带四元数朝向的飞行玩家（hasOrientation()）
 * 生效，原版生物仍正常受坠落伤害。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void spaceSim$noFallDamage(float distance, float damageMultiplier, DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (((EntityRotation) (Object) this).hasOrientation()) {
            cir.setReturnValue(false);
        }
    }
}
