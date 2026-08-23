package org.xyccwa.space_simulation.rapierfix;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;

import java.util.IdentityHashMap;

/**
 * 约束朝向注册表（rapierfix）。
 *
 * rapier 的 joint motor 误差 = (anchor2−anchor1)·axis − target（rapier 源码
 * motor_linear：`rhs += (dist − target_pos) * erp_inv_dt`，dist 为两锚点沿轴距离）。
 * 重基后锚点距离 = 刚体引擎内坐标（≈0 附近），因此 target 也必须为引擎域小量。
 * simulated 传的 target = R⁻¹·T（世界目标点旋转到约束朝向系，量级 2048 万）——
 * 正确目标应为 R⁻¹·(T−O) = R⁻¹·T − R⁻¹·O。R（约束朝向）不在 setMotor 调用点，
 * 故在约束工厂（create）创建时记录：约束对象 → 朝向，setMotor 时减 R⁻¹·O 的轴分量。
 */
public final class RapierJointRotationRegistry {

    private static final IdentityHashMap<Object, Quaterniond> ROTATIONS = new IdentityHashMap<>();

    private RapierJointRotationRegistry() {
    }

    /** 记录约束对象与其 frame1 朝向（拖拽/装配约束） */
    public static void record(final Object joint, final Quaterniondc rotation) {
        if (rotation != null) {
            ROTATIONS.put(joint, new Quaterniond(rotation));
        }
    }

    /** 约束移除时清理 */
    public static void forget(final Object joint) {
        ROTATIONS.remove(joint);
    }

    /** @return 约束朝向；未记录时返回 null */
    public static Quaterniond rotationFor(final Object joint) {
        return ROTATIONS.get(joint);
    }
}
