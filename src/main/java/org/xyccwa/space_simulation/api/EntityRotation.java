package org.xyccwa.space_simulation.api;

import org.joml.Quaternionf;

/**
 * 6DOF 旋转支持。由 EntityMixin 注入到 Entity 上，任何实体（玩家）都可强转成该接口使用。
 * 用四元数存储完整朝向（滚转/偏航/俯仰），从根本上避免万向节死锁。
 */
public interface EntityRotation {
    /** 该实体是否启用了 6DOF 旋转（本 mod 中所有玩家都启用）。 */
    boolean hasOrientation();

    /** 当前朝向四元数（副本）。 */
    Quaternionf getOrientation();

    /** 上一 tick 的朝向四元数（副本），用于渲染插值。 */
    Quaternionf getPrevOrientation();

    /** 设置朝向，内部会更新上一朝向并同步欧拉角。传入值会被拷贝并归一化。 */
    void setOrientation(Quaternionf orientation);

    /** 用 partialTick 在上一朝向和当前朝向之间做 slerp 插值，用于平滑渲染。 */
    Quaternionf getInterpolatedOrientation(float partialTick);

    /**
     * 渲染专用平滑朝向（指数缓动后的显示朝向）。
     * 每次渲染帧调用 {@link #smoothOrientation(float)} 更新后，供相机/模型读取。
     * 逻辑（物理方向、准星）仍用 {@link #getOrientation()}，保持零滞后响应。
     */
    Quaternionf getSmoothedOrientation();

    /**
     * 把显示朝向朝当前朝向做指数缓动（slerp 一步），alpha 由帧时间计算，
     * 帧率无关。低帧率下把逐帧突变铺平成连续滑动，消除"固定角度步进"的顿挫感。
     */
    void smoothOrientation(float alpha);

    /** 客户端移动输入掩码（位标志，见 FlightPhysics.MOVE_*）。 */
    int getMoveMask();

    void setMoveMask(int moveMask);

    /** 从四元数推导 yaw/pitch 并写回实体的欧拉角字段，供原版代码读取。 */
    void syncEulerAngles();
}
