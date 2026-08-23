package org.xyccwa.space_simulation.mixin.rapierfix;

import dev.ryanhcode.sable.api.physics.constraint.FixedConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.GenericConstraintConfiguration;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xyccwa.space_simulation.rapierfix.RapierJointRotationRegistry;

/**
 * 约束工厂朝向记录（rapierfix）：在约束创建（create）返回时记录"约束对象 → frame1
 * 朝向"，供 {@link RapierConstraintHandleMixin} 在 setMotor 时计算 R⁻¹·O 修正
 * （rapier motor 误差 = 锚点距离 − target，重基后 target 需为引擎域量）。
 */
public final class RapierJointRotationMixin {

    private RapierJointRotationMixin() {
    }

    @Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.constraint.free.RapierFreeConstraintHandle")
    public abstract static class Free {
        @Inject(method = "create", at = @At("RETURN"))
        private static void spaceSim$record(final ServerLevel level,
                                            final dev.ryanhcode.sable.api.physics.PhysicsPipelineBody bodyA,
                                            final dev.ryanhcode.sable.api.physics.PhysicsPipelineBody bodyB,
                                            final FreeConstraintConfiguration config,
                                            final CallbackInfoReturnable<Object> cir) {
            RapierJointRotationRegistry.record(cir.getReturnValue(), config.orientation());
        }
    }

    @Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.constraint.generic.RapierGenericConstraintHandle")
    public abstract static class Generic {
        @Inject(method = "create", at = @At("RETURN"))
        private static void spaceSim$record(final ServerLevel level,
                                            final dev.ryanhcode.sable.api.physics.PhysicsPipelineBody bodyA,
                                            final dev.ryanhcode.sable.api.physics.PhysicsPipelineBody bodyB,
                                            final GenericConstraintConfiguration config,
                                            final CallbackInfoReturnable<Object> cir) {
            RapierJointRotationRegistry.record(cir.getReturnValue(), config.orientation1());
        }
    }

    @Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.constraint.fixed.RapierFixedConstraintHandle")
    public abstract static class Fixed {
        @Inject(method = "create", at = @At("RETURN"))
        private static void spaceSim$record(final ServerLevel level,
                                            final dev.ryanhcode.sable.api.physics.PhysicsPipelineBody bodyA,
                                            final dev.ryanhcode.sable.api.physics.PhysicsPipelineBody bodyB,
                                            final FixedConstraintConfiguration config,
                                            final CallbackInfoReturnable<Object> cir) {
            RapierJointRotationRegistry.record(cir.getReturnValue(), config.orientation());
        }
    }
}
