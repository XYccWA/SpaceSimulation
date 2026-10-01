package org.xyccwa.space_simulation.mixin.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link LivingEntity#die(DamageSource)}（protected）。
 * 太阳表面处死需要无视游戏模式（创造/旁观）直接触发死亡流程：
 * 这些模式下任何 {@code hurt()} 都会因 isInvulnerableTo 恒 true 而失败，只能直接调用 die()。
 */
@Mixin(LivingEntity.class)
public interface LivingEntityDeathInvoker {

    @Invoker("die")
    void spaceSim$invokeDie(DamageSource source);
}
