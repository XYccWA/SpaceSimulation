package org.XYccWA.space_simulation.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {

    /**
     * 在实体每刻更新时，强制开启无重力标签。
     * 这会阻止 Entity 类中的基础重力逻辑（在 updateFluidOnEyes 或相关方法中）生效。
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        // 始终设置为无重力，具体的摩擦力我们在 move 方法中处理
        entity.setNoGravity(true);
    }
}
