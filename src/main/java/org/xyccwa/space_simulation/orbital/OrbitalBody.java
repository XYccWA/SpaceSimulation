package org.xyccwa.space_simulation.orbital;

import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.xyccwa.space_simulation.api.EntityRotation;
import org.xyccwa.space_simulation.util.FlightPhysics;

/**
 * 玩家一 tick 的轨道物理，客户端预测与服务端权威共用同一份实现（预测能对账的前提）。
 *
 * 顺序：输入掩码 → 机体推力（按朝向转到世界坐标）→ 引力段速度 Verlet → 推力段 + 限速
 * → 用 move() 真正移动（碰撞由原版处理）→ 碰撞轴速度清零。
 *
 * 调用方负责：客户端记录 (seq, pos, vel, thrust) 供对账/重放；服务端下发权威状态。
 */
public final class OrbitalBody {

    private OrbitalBody() {
    }

    /**
     * 积分并移动玩家实体。调用方需保证该玩家处于本系统模拟状态（非乘客/非旁观/主世界）。
     *
     * 速度以调用方维护的 velocityInOut 为准，而不是读实体上的 deltaMovement：
     * 原版 {@code LivingEntity.aiStep} 会把 |分量| &lt; 0.003 的速度清零，而本模组的引力在
     * 数十万格外每 tick 只增加 ~1e-4 格/tick，会被这条规则反复抹掉，导致轨道被压成直线
     * （实测：径向速度永远累积不起来，玩家沿切线匀速飞出去）。所以双方各自保存一份权威速度，
     * 每 tick 用它积分，再把结果写回实体。
     *
     * @param thrustOut      输出本 tick 使用的推力加速度（块/tick²）
     * @param velocityInOut  输入当前速度、输出积分后的速度（块/tick）
     */
    public static void step(Player self, EntityRotation rot, int mask,
                            double[] thrustOut, double[] velocityInOut) {
        OrbitalSteering.thrust(rot, mask, thrustOut);

        double[] pos = {self.getX(), self.getY(), self.getZ()};
        double[] outPos = new double[3];
        double[] outVel = new double[3];

        OrbitalPhysics.stepGravity(pos, velocityInOut, outPos, outVel);
        double refSpeed = OrbitalPhysics.speed(outVel);
        OrbitalPhysics.applyThrust(outVel, refSpeed, thrustOut, FlightPhysics.MAX_SPEED);

        velocityInOut[0] = outVel[0];
        velocityInOut[1] = outVel[1];
        velocityInOut[2] = outVel[2];

        Vec3 delta = new Vec3(outVel[0], outVel[1], outVel[2]);
        self.setDeltaMovement(delta);
        self.move(MoverType.SELF, delta);

        // 碰撞轴向清零：贴墙/落地时不保留"穿墙分量"（与旧版无引力飞行一致）
        Vec3 after = self.getDeltaMovement();
        double ax = self.horizontalCollision ? 0.0 : after.x;
        double ay = self.verticalCollision ? 0.0 : after.y;
        double az = self.horizontalCollision ? 0.0 : after.z;
        self.setDeltaMovement(ax, ay, az);
        self.resetFallDistance();
    }
}
