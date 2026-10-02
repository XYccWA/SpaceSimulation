package org.xyccwa.space_simulation.orbital;

/**
 * 每 tick 一步的数值积分器（速度 Verlet，辛格式），客户端预测与服务端权威共用。
 *
 * 单步 dt = 1 tick，分两段：
 *   1) 引力段（{@link #stepGravity}）：速度 Verlet，二阶辛格式，对开普勒轨道有界能量误差；
 *   2) 推力段（{@link #applyThrust}）：把视角方向的推力加速度加到引力速度上，再做限速。
 *
 * 推力是控制输入、量级远小于一步引力的时间尺度，用半隐式（先加速后移位）足够；
 * 限速只限制"推力贡献"，因此自由落体获得的高速不会被砍掉（见 clampThrustSpeed）。
 *
 * 碰撞不在这里处理：调用方拿到预测位置后用 {@code move()} 实际移动并回收碰撞轴速度。
 */
public final class OrbitalPhysics {

    private OrbitalPhysics() {
    }

    /**
     * 引力段：速度 Verlet 一步（不含推力），写入 outPos / outVel。
     *
     * @param pos    当前位置（块）
     * @param vel    当前速度（块/tick）
     * @param outPos 输出位置（可与 pos 同数组）
     * @param outVel 输出速度（可与 vel 同数组）
     */
    public static void stepGravity(double[] pos, double[] vel, double[] outPos, double[] outVel) {
        double[] a1 = new double[3];
        Gravity.acceleration(pos[0], pos[1], pos[2], a1);

        double vhx = vel[0] + a1[0] * 0.5;
        double vhy = vel[1] + a1[1] * 0.5;
        double vhz = vel[2] + a1[2] * 0.5;

        double[] a2 = new double[3];
        Gravity.acceleration(pos[0] + vhx, pos[1] + vhy, pos[2] + vhz, a2);

        double vx = vhx + a2[0] * 0.5;
        double vy = vhy + a2[1] * 0.5;
        double vz = vhz + a2[2] * 0.5;

        outPos[0] = pos[0] + vx;
        outPos[1] = pos[1] + vy;
        outPos[2] = pos[2] + vz;
        outVel[0] = vx;
        outVel[1] = vy;
        outVel[2] = vz;
    }

    /**
     * 推力段：vel += thrust，然后把速度限制到 max(refSpeed, maxSpeed)。
     *
     * 限速语义：推力不能把速度推过常规上限（10 块/tick），但引力已经给到的速度
     * （refSpeed，自由落体）不被砍掉——否则近太阳自由落体会被人为限速、轨道失真。
     *
     * @param vel      引力段速度（原地修改）
     * @param refSpeed 引力段速度大小
     * @param thrust   推力加速度（块/tick²）
     * @param maxSpeed 常规速度上限
     * @return 限速后的速度大小
     */
    public static double applyThrust(double[] vel, double refSpeed, double[] thrust, double maxSpeed) {
        vel[0] += thrust[0];
        vel[1] += thrust[1];
        vel[2] += thrust[2];
        double sp2 = vel[0] * vel[0] + vel[1] * vel[1] + vel[2] * vel[2];
        double limit = Math.max(refSpeed, maxSpeed);
        if (sp2 > limit * limit && sp2 > 0.0) {
            double scale = limit / Math.sqrt(sp2);
            vel[0] *= scale;
            vel[1] *= scale;
            vel[2] *= scale;
            sp2 = limit * limit;
        }
        return Math.sqrt(sp2);
    }

    /** 速度大小。 */
    public static double speed(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }
}
