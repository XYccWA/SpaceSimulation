package org.xyccwa.space_simulation.mixin.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@link LivingEntity#die(DamageSource)}（protected）。
 * 太阳警戒线处死需要无视游戏模式（创造/旁观）直接触发死亡流程：
 * - 创造模式：普通伤害被 isInvulnerableTo 拦截，需借 bypasses_invulnerability 标签 + 巨额伤害；
 * - 旁观模式：isInvulnerableTo 恒 true，任何 hurt() 都无效，只能直接调用 die()。
 */
@Mixin(LivingEntity.class)
public interface LivingEntityDeathInvoker {

    @Invoker("die")
    void spaceSim$invokeDie(DamageSource source);
}
