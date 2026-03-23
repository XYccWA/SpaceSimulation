package org.XYccWA.space_simulation.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        // 检查伤害源是否为虚空伤害
        if (source.typeHolder().is(DamageTypes.FELL_OUT_OF_WORLD)||source.typeHolder().is(DamageTypes.FALL)) {
            // 取消伤害事件
            cir.setReturnValue(false);
        }
    }

    private static final float RADIAN_CONVERTER = (float) (Math.PI / 180.0);
    private static final float DAMPING_FACTOR = 1.0F; // 阻尼系数

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void onTravel(Vec3 travelVector, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player) {
            Player player = (Player) entity;

            player.getAbilities().flying = false;
            player.getAbilities().mayfly = false;

            ci.cancel();

            float forward = player.zza;
            float strafe = player.xxa;

            float yaw = player.getYRot();
            float pitch = player.getXRot();

            // 使用预计算的弧度转换
            double yawRad = yaw * RADIAN_CONVERTER;
            double pitchRad = pitch * RADIAN_CONVERTER;

            Vec3 currentVelocity = entity.getDeltaMovement();
            Vec3 newVelocity;

            if (forward == 0 && strafe == 0) {
                newVelocity = currentVelocity.scale(DAMPING_FACTOR);
            } else {
                double x = -Math.sin(yawRad) * Math.cos(pitchRad) * forward + Math.cos(yawRad) * strafe;
                double y = -Math.sin(pitchRad) * forward;
                double z = Math.cos(yawRad) * Math.cos(pitchRad) * forward + Math.sin(yawRad) * strafe;

                Vec3 acceleration = new Vec3(x, y, z).normalize().scale(0.02F);
                newVelocity = currentVelocity.add(acceleration);
            }

            // 应用新速度
            if (newVelocity.lengthSqr() > 0.0001) {
                entity.setDeltaMovement(newVelocity);
                entity.move(MoverType.SELF, newVelocity);
            } else {
                // 速度极小时完全停止
                entity.setDeltaMovement(Vec3.ZERO);
            }
        }
    }
}

