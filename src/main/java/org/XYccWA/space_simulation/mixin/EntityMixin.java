package org.XYccWA.space_simulation.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        entity.setNoGravity(true);
    }

    private double customPitch = 0.0;

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void onTurn(double yawChange, double pitchChange, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player) {
            ci.cancel();

            // 获取当前yaw和pitch值（使用双精度）
            double currentYaw = entity.getYRot();
            double currentPitch = customPitch;

            // 判断玩家是否倒立（pitch接近±180°）
            boolean isInverted = Math.abs(Math.abs(currentPitch) - 180) < 90;

            // 根据是否倒立决定是否反转水平旋转方向
            double newYawChange = isInverted ? -yawChange : yawChange;

            // 更新yaw和pitch（使用双精度计算）
            double newYaw = currentYaw + newYawChange;
            double newPitch = currentPitch + pitchChange;

            // 限制pitch范围在-180到180度之间
            newPitch = Mth.wrapDegrees(newPitch);

            // 更新存储值
            entity.setYRot((float)newYaw);
            customPitch = newPitch;
            entity.setXRot((float)newPitch);
        }
    }
}
