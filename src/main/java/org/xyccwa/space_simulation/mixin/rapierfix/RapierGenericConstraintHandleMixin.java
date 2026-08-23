package org.xyccwa.space_simulation.mixin.rapierfix;

import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xyccwa.space_simulation.rapierfix.RapierJointRotationRegistry;

/**
 * 约束工厂朝向记录（rapierfix）：generic 约束记录 frame1 朝向（orientation1）。
 */
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.constraint.generic.RapierGenericConstraintHandle")
public abstract class RapierGenericConstraintHandleMixin {

    @Inject(method = "create", at = @At("RETURN"))
    private static void spaceSim$record(final ServerLevel level, final PhysicsPipelineBody bodyA,
                                        final PhysicsPipelineBody bodyB, final GenericConstraintConfiguration config,
                                        final CallbackInfoReturnable<Object> cir) {
        RapierJointRotationRegistry.record(cir.getReturnValue(), config.orientation1());
    }
}
