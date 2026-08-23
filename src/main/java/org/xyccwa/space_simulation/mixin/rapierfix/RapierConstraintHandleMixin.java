package org.xyccwa.space_simulation.mixin.rapierfix;

import dev.ryanhcode.sable.api.physics.constraint.ConstraintJointAxis;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.xyccwa.space_simulation.rapierfix.RapierJointRotationRegistry;
import org.xyccwa.space_simulation.rapierfix.RapierOriginManager;

/**
 * rapierfix：约束电机目标重基。
 *
 * rapier 的 joint motor 误差 = (anchor2−anchor1)·axis − target（rapier 源码
 * motor_linear：`rhs += (dist − target_pos) * erp_inv_dt`，dist 为两锚点沿轴距离）。
 * 重基后锚点距离 = 刚体引擎内坐标（≈0 附近），因此 target 也必须是引擎域小量。
 * simulated 传的 target = R⁻¹·T（世界目标点旋转到约束 frame1 系，量级 2048 万）——
 * 正确目标 = R⁻¹·(T−O) = R⁻¹·T − R⁻¹·O。R 在约束创建时由
 * {@link RapierJointRotationRegistry} 记录，此处减 R⁻¹·O 的对应轴分量；
 * 无朝向记录时退化为减 O 的分量（R≈I 近似）。
 *
 * 角轴（ANGULAR_*）是角度目标（相对量），不平移。
 */
@Mixin(targets = "dev.ryanhcode.sable.physics.impl.rapier.constraint.RapierConstraintHandle")
public abstract class RapierConstraintHandleMixin {

    private static final Logger LOGGER = LogManager.getLogger("space_simulation");

    private static String fmt(double v) {
        return String.format("%.1f", v);
    }

    @ModifyArgs(
            method = "setMotor",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/physics/impl/rapier/Rapier3D;setConstraintMotor(JJIDDDZD)V")
    )
    private void spaceSim$setMotor(final Args args) {
        if (!RapierOriginManager.enabled()) {
            return;
        }
        final ConstraintJointAxis axis = ConstraintJointAxis.values()[args.get(2)];
        if (axis != ConstraintJointAxis.LINEAR_X
                && axis != ConstraintJointAxis.LINEAR_Y
                && axis != ConstraintJointAxis.LINEAR_Z) {
            return; // 角轴：角度目标，相对量
        }
        final long scene = args.get(0);
        final Vector3d origin = RapierOriginManager.originFor(scene);
        double offset;
        final Quaterniond rotation = RapierJointRotationRegistry.rotationFor(this);
        if (rotation != null) {
            // 正确目标 = R⁻¹·(T−O) = R⁻¹·T − R⁻¹·O：把 O 旋转到 frame1 系再取轴分量
            final Vector3d rotatedOrigin = rotation.transformInverse(new Vector3d(origin));
            offset = switch (axis) {
                case LINEAR_X -> rotatedOrigin.x;
                case LINEAR_Y -> rotatedOrigin.y;
                case LINEAR_Z -> rotatedOrigin.z;
                default -> 0.0;
            };
        } else {
            // 无朝向记录（非拖拽类约束）：R≈I 退化
            offset = switch (axis) {
                case LINEAR_X -> origin.x;
                case LINEAR_Y -> origin.y;
                case LINEAR_Z -> origin.z;
                default -> 0.0;
            };
        }
        final double target = args.get(3);
        final double corrected = target - offset;
        if (LOGGER.isDebugEnabled() && Math.abs(offset) > 1_000_000.0) {
            LOGGER.debug("[rapierfix] motor {} target {} -> {} (offset {}, R={})",
                    axis, fmt(target), fmt(corrected), fmt(offset),
                    rotation != null ? "present" : "none");
        }
        args.set(3, corrected);
    }

    /** 约束移除时清理朝向记录 */
    @Inject(method = "remove", at = @At("HEAD"))
    private void spaceSim$forgetRotation(final CallbackInfo ci) {
        RapierJointRotationRegistry.forget(this);
    }
}
