package org.xyccwa.space_simulation.mixin.rapierfix;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xyccwa.space_simulation.rapierfix.RapierJointRotationRegistry;

/**
 * 约束工厂朝向记录（rapierfix）：fixed 约束记录朝向。
 */
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.constraint.fixed.RapierFixedConstraintHandle")
public abstract class RapierFixedConstraintHandleMixin {

    @Inject(method = "create", at = @At("RETURN"))
    private static void spaceSim$record(final ServerLevel level, final PhysicsPipelineBody bodyA,
                                        final PhysicsPipelineBody bodyB, final FixedConstraintConfiguration config,
                                        final CallbackInfoReturnable<Object> cir) {
        RapierJointRotationRegistry.record(cir.getReturnValue(), config.orientation());
    }
}
