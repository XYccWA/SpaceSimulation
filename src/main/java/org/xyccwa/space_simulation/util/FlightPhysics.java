package org.xyccwa.space_simulation.util;

/**
 * 飞行物理参数与移动输入位掩码。
 * 客户端与服务器在 Player.travel 中跑同一套确定性模拟，保证位置同步。
 */
public final class FlightPhysics {
    private FlightPhysics() {
    }

    /** 每 tick 沿视角方向施加的加速度（方块/tick²）。0.06 = 24 m/s²（换算：1 方块/tick² = 400 m/s²）。 */
    public static final float ACCELERATION = 0.06F;
    /** 最大飞行速度（方块/tick）。10 = 200 m/s（换算：1 方块/tick = 20 m/s）。 */
    public static final float MAX_SPEED = 10.0F;
    /** 滚转速度（弧度/tick），Q/E 按住时每秒约 5°*20 = 100°。 */
    public static final float ROLL_SPEED = (float) Math.toRadians(5.0);

    // 移动输入位掩码
    public static final int MOVE_FORWARD = 1 << 0;
    public static final int MOVE_BACK = 1 << 1;
    public static final int MOVE_LEFT = 1 << 2;
    public static final int MOVE_RIGHT = 1 << 3;
    public static final int MOVE_UP = 1 << 4;
    public static final int MOVE_DOWN = 1 << 5;
}
