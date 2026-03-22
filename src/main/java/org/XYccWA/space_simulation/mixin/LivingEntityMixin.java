package org.XYccWA.space_simulation.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void onTravel(Vec3 travelVector, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player) {
            Player player = (Player) entity;

            player.getAbilities().flying = false;
            player.getAbilities().mayfly = false;

            ci.cancel();

            // 获取输入
            float forward = player.zza;  // W/S
            float strafe = player.xxa;   // A/D

            // 获取视角角度
            float yaw = player.getYRot();
            float pitch = player.getXRot();

            // 计算移动方向（基于玩家视线）
            // 将角度转换为弧度
            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);

            // 计算基于视角的移动方向
            // 使用完整的3D旋转矩阵计算
            double x = -Math.sin(yawRad) * Math.cos(pitchRad) * forward + Math.cos(yawRad) * strafe;
            double y = -Math.sin(pitchRad) * forward;  // 修改这里，添加负号
            double z = Math.cos(yawRad) * Math.cos(pitchRad) * forward + Math.sin(yawRad) * strafe;

            // 获取当前速度
            Vec3 currentVelocity = entity.getDeltaMovement();

            // 计算新的移动增量（加速度）
            Vec3 acceleration = new Vec3(x, y, z).normalize().scale(0.02F);

            // 叠加加速度到当前速度
            Vec3 newVelocity = currentVelocity.add(acceleration);

            // 应用新速度
            entity.setDeltaMovement(newVelocity);
            entity.move(MoverType.SELF, entity.getDeltaMovement());
        }
    }
}

