package org.xyccwa.space_simulation.mixin.entity;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 LivingEntity.jumping（protected，声明在 LivingEntity 而非 Player）。
 * PlayerMixin 的 @Mixin 目标是 Player，无法直接 @Shadow 继承来的字段，经此 accessor 读取。
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Accessor("jumping")
    boolean spaceSim$isJumping();
}
