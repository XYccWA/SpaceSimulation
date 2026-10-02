package org.xyccwa.space_simulation.orbital;

import org.joml.Vector3f;
import org.xyccwa.space_simulation.api.EntityRotation;
import org.xyccwa.space_simulation.util.FlightPhysics;

/**
 * 输入掩码 + 朝向 → 世界坐标推力加速度（块/tick²）。客户端预测与服务端权威共用同一实现。
 *
 * 机体坐标系约定与四元数一致：left=+X, up=+Y, forward=+Z，与旧版 PlayerMixin 内的算法完全相同
 * （保证"无引力"老路径与"有引力"新路径的手感一致）。
 */
public final class OrbitalSteering {

    private OrbitalSteering() {
    }

    /** 把输入掩码按机体朝向转到世界坐标，写入 out[0..2]（长度 = ACCELERATION）。无输入时写零。 */
    public static void thrust(EntityRotation rot, int mask, double[] out) {
        float strafe = ((mask & FlightPhysics.MOVE_LEFT) != 0 ? 1 : 0)
                - ((mask & FlightPhysics.MOVE_RIGHT) != 0 ? 1 : 0);
        float vertical = ((mask & FlightPhysics.MOVE_UP) != 0 ? 1 : 0)
                - ((mask & FlightPhysics.MOVE_DOWN) != 0 ? 1 : 0);
        float forward = ((mask & FlightPhysics.MOVE_FORWARD) != 0 ? 1 : 0)
                - ((mask & FlightPhysics.MOVE_BACK) != 0 ? 1 : 0);

        if (strafe == 0.0F && vertical == 0.0F && forward == 0.0F) {
            out[0] = 0.0;
            out[1] = 0.0;
            out[2] = 0.0;
            return;
        }
        Vector3f bodyDir = new Vector3f(strafe, vertical, forward);
        bodyDir.normalize().rotate(rot.getOrientation());
        out[0] = bodyDir.x * FlightPhysics.ACCELERATION;
        out[1] = bodyDir.y * FlightPhysics.ACCELERATION;
        out[2] = bodyDir.z * FlightPhysics.ACCELERATION;
    }
}
