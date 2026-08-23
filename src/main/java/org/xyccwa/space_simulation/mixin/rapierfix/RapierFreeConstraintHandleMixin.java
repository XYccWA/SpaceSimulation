package org.xyccwa.space_simulation.mixin.rapierfix;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xyccwa.space_simulation.rapierfix.RapierJointRotationRegistry;

/**
 * 约束工厂朝向记录（rapierfix）：create 返回时记录"约束对象 → frame1 朝向"，
 * 供 {@link RapierConstraintHandleMixin} 的 setMotor 计算 R⁻¹·O 修正。
 */
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.constraint.free.RapierFreeConstraintHandle")
public abstract class RapierFreeConstraintHandleMixin {

    @Inject(method = "create", at = @At("RETURN"))
    private static void spaceSim$record(final ServerLevel level, final PhysicsPipelineBody bodyA,
                                        final PhysicsPipelineBody bodyB, final FreeConstraintConfiguration config,
                                        final CallbackInfoReturnable<Object> cir) {
        RapierJointRotationRegistry.record(cir.getReturnValue(), config.orientation());
    }
}
