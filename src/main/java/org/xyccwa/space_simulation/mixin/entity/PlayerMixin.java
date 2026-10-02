package org.xyccwa.space_simulation.mixin.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.xyccwa.space_simulation.api.EntityRotation;
import org.xyccwa.space_simulation.orbital.Gravity;
import org.xyccwa.space_simulation.orbital.TwoBody;
import org.xyccwa.space_simulation.util.FlightPhysics;

/**
 * 玩家飞行物理：整体替换 {@code Player.travel}，分两条路径。
 *
 * 一、轨道力学开启（默认，主世界）：服务器权威 + 客户端预测。
 * - 服务器用自己的积分结果驱动位置（见 PlayerOrbitServer），并每 tick 把权威状态下发；
 *   客户端的移动包被忽略（见 ServerGamePacketListenerImplMixin），位置一律以服务器为准。
 * - 客户端同时跑同一套积分做本地预测（消除输入延迟），收到权威状态后按输入序号对账，
 *   误差超阈值才回滚重放（见 PlayerOrbitClient）。
 * - 两端用同一份 OrbitalBody/OrbitalPhysics/Gravity，输入来自客户端上报的掩码与朝向。
 *
 * 二、轨道力学关闭，或不在主世界 / 载具 / 死亡 / 旁观：旧的客户端权威牛顿飞行。
 * - 客户端本地玩家在 travel 内读按键位、按视角加速并移动，再经原版移动包上报服务器；
 *   服务器取消 travel 防止双重移动，客户端上其他玩家的副本不做本地物理。
 *
 * 朝向（四元数）与速度的存档也在本类：离线时不做实体 tick，靠存档里的轨道根数
 * 在登录时解析传播（见 OrbitalPersistence）。
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void spaceSim$onTravel(Vec3 travelVector, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!(self instanceof EntityRotation rot) || !rot.hasOrientation()) {
            return;
        }
        boolean client = self.level().isClientSide();
        // 载具/死亡/旁观：交回原版（载具与旁观逻辑不能被飞行物理取代）
        boolean orbital = Gravity.enabled()
                && self.level().dimension() == net.minecraft.world.level.Level.OVERWORLD
                && !self.isPassenger() && !self.isDeadOrDying() && !self.isSpectator();

        if (orbital) {
            // 新路径：双端跑同一套数值积分，服务器权威 + 客户端预测对账
            ci.cancel();
            if (client) {
                if (!self.isLocalPlayer()) {
                    return; // 其它玩家的副本：位置由服务器实体同步驱动
                }
                int mask = spaceSim$readMoveMask(self);
                rot.setMoveMask(mask);
                // 经间接层调用客户端类：通用 mixin 不能直接引用仅在客户端存在的类型
                org.xyccwa.space_simulation.orbital.OrbitalSideHooks.tickClient(self, rot, mask);
            } else {
                org.xyccwa.space_simulation.orbital.PlayerOrbitServer.tick(self, rot);
            }
            return;
        }

        // 旧路径（无引力，或非主世界/载具/旁观）：客户端权威的纯推力飞行
        if (self.isPassenger() || self.isDeadOrDying() || self.isSpectator()) {
            return; // 这些状态连取消都不做，完全交回原版
        }
        ci.cancel();
        if (!client) {
            return; // 服务器端：位置由客户端移动包驱动，取消 travel 防止双重移动
        }
        if (!self.isLocalPlayer()) {
            return; // 客户端上其他玩家的实体副本：位置由服务器实体同步驱动
        }
        spaceSim$legacyFlight(self, rot);
    }

    /** 读取本 tick 的输入位掩码（客户端本地按键）。 */
    private int spaceSim$readMoveMask(Player self) {
        int mask = 0;
        if (self.zza > 0.01F) mask |= FlightPhysics.MOVE_FORWARD;
        else if (self.zza < -0.01F) mask |= FlightPhysics.MOVE_BACK;
        if (self.xxa > 0.01F) mask |= FlightPhysics.MOVE_LEFT;
        else if (self.xxa < -0.01F) mask |= FlightPhysics.MOVE_RIGHT;
        // jumping 声明在 LivingEntity（非 Player），不能直接 @Shadow，经 accessor 暴露
        if (((LivingEntityAccessor) (Object) this).spaceSim$isJumping()) mask |= FlightPhysics.MOVE_UP;
        if (self.isShiftKeyDown()) mask |= FlightPhysics.MOVE_DOWN;
        return mask;
    }

    /** 无引力时的旧飞行物理（客户端权威、无阻力、按视角加速、限速 10 块/tick）。 */
    private void spaceSim$legacyFlight(Player self, EntityRotation rot) {
        int mask = spaceSim$readMoveMask(self);
        rot.setMoveMask(mask);

        // 机体坐标系输入：left=+X, up=+Y, forward=+Z（与四元数约定一致）
        float strafe = ((mask & FlightPhysics.MOVE_LEFT) != 0 ? 1 : 0) - ((mask & FlightPhysics.MOVE_RIGHT) != 0 ? 1 : 0);
        float vertical = ((mask & FlightPhysics.MOVE_UP) != 0 ? 1 : 0) - ((mask & FlightPhysics.MOVE_DOWN) != 0 ? 1 : 0);
        float forward = ((mask & FlightPhysics.MOVE_FORWARD) != 0 ? 1 : 0) - ((mask & FlightPhysics.MOVE_BACK) != 0 ? 1 : 0);

        Vector3f bodyDir = new Vector3f(strafe, vertical, forward);
        if (bodyDir.lengthSquared() > 0.0F) {
            bodyDir.normalize().rotate(rot.getOrientation());
        }

        // 无阻力：速度原样保留，只叠加视角方向加速度。不按键时匀速直线运动。
        Vec3 vel = self.getDeltaMovement();
        double vx = vel.x + bodyDir.x * FlightPhysics.ACCELERATION;
        double vy = vel.y + bodyDir.y * FlightPhysics.ACCELERATION;
        double vz = vel.z + bodyDir.z * FlightPhysics.ACCELERATION;

        double speedSqr = vx * vx + vy * vy + vz * vz;
        double maxSqr = (double) FlightPhysics.MAX_SPEED * FlightPhysics.MAX_SPEED;
        if (speedSqr > maxSqr) {
            double scale = FlightPhysics.MAX_SPEED / Math.sqrt(speedSqr);
            vx *= scale;
            vy *= scale;
            vz *= scale;
        }

        self.setDeltaMovement(vx, vy, vz);
        self.move(MoverType.SELF, new Vec3(vx, vy, vz));

        // 碰撞轴向清零，避免贴墙时速度穿透
        Vec3 after = self.getDeltaMovement();
        double ax = self.horizontalCollision ? 0.0 : after.x;
        double ay = self.verticalCollision ? 0.0 : after.y;
        double az = self.horizontalCollision ? 0.0 : after.z;
        self.setDeltaMovement(ax, ay, az);

        self.resetFallDistance();
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void spaceSim$saveData(CompoundTag tag, CallbackInfo ci) {
        if (this instanceof EntityRotation rot) {
            Quaternionf q = rot.getOrientation();
            tag.putFloat("space_sim_qx", q.x);
            tag.putFloat("space_sim_qy", q.y);
            tag.putFloat("space_sim_qz", q.z);
            tag.putFloat("space_sim_qw", q.w);
        }

        Player self = (Player) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        // 速度不存档是原版行为；轨道力学下速度是状态的一部分，必须存
        Vec3 v = self.getDeltaMovement();
        tag.putDouble("space_sim_vx", v.x);
        tag.putDouble("space_sim_vy", v.y);
        tag.putDouble("space_sim_vz", v.z);

        // 离线传播用：把此刻的二体轨道根数（含时刻）存档，登录时按服务器 tick 解析传播
        double mu = Gravity.mu();
        if (mu > 0.0) {
            TwoBody.Elements el = TwoBody.fromState(
                    new double[]{self.getX(), self.getY(), self.getZ()},
                    new double[]{v.x, v.y, v.z}, mu);
            if (el.valid) {
                tag.putBoolean("space_sim_orbit", true);
                tag.putDouble("space_sim_a", el.a);
                tag.putDouble("space_sim_e", el.e);
                tag.putDouble("space_sim_inc", el.inclination);
                tag.putDouble("space_sim_raan", el.raan);
                tag.putDouble("space_sim_argp", el.argPeriapsis);
                tag.putDouble("space_sim_m0", el.meanAnomaly0);
                tag.putDouble("space_sim_mu", mu);
                tag.putLong("space_sim_t0", self.level().getGameTime());
            } else {
                tag.putBoolean("space_sim_orbit", false);
            }
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void spaceSim$readData(CompoundTag tag, CallbackInfo ci) {
        if (this instanceof EntityRotation rot && tag.contains("space_sim_qx")) {
            Quaternionf q = new Quaternionf(
                    tag.getFloat("space_sim_qx"),
                    tag.getFloat("space_sim_qy"),
                    tag.getFloat("space_sim_qz"),
                    tag.getFloat("space_sim_qw"));
            rot.setOrientation(q);
        }
        Player self = (Player) (Object) this;
        if (tag.contains("space_sim_vx")) {
            self.setDeltaMovement(tag.getDouble("space_sim_vx"), tag.getDouble("space_sim_vy"), tag.getDouble("space_sim_vz"));
        }
        // 离线传播用：把存档里的轨道根数交给 OrbitalPersistence，登录事件里按 tick 解析传播
        if (self.level() != null && !self.level().isClientSide() && tag.getBoolean("space_sim_orbit")) {
            org.xyccwa.space_simulation.orbital.OrbitalPersistence.offerSavedOrbit(self.getUUID(),
                    new org.xyccwa.space_simulation.orbital.OrbitalPersistence.SavedOrbit(
                            tag.getDouble("space_sim_a"), tag.getDouble("space_sim_e"),
                            tag.getDouble("space_sim_inc"), tag.getDouble("space_sim_raan"),
                            tag.getDouble("space_sim_argp"), tag.getDouble("space_sim_m0"),
                            tag.getDouble("space_sim_mu"), tag.getLong("space_sim_t0")));
        }
    }
}
