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
    private float customPitch = 0;

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void onTurn(double p_19885_, double p_19886_, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity instanceof Player) {
            ci.cancel();

            // 获取当前pitch值
            float pitch = entity.getXRot();

            // 判断玩家是否倒立（pitch接近±180°）
            boolean isInverted = Math.abs(Math.abs(pitch) - 180) < 90;

            // 根据是否倒立决定是否反转水平旋转方向
            double yawChange = isInverted ? -p_19885_ : p_19885_;

            // 更新yaw和pitch
            entity.setYRot((float) (entity.getYRot() + yawChange));
            customPitch += p_19886_;
            customPitch = customPitch % 360;

            // 同步更新实体的xRot
            entity.setXRot(customPitch);
        }
    }
}
