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
import org.xyccwa.space_simulation.util.FlightPhysics;

/**
 * 飞行物理：替换 Player.travel，向视角方向施加加速度。
 *
 * 客户端权威（client-authoritative）：
 * - 客户端本地玩家（LocalPlayer）在 travel 内读取按键位（xxa/zza/jumping/shiftKeyDown，
 *   由 LocalPlayer.aiStep 在当前 tick 内先设置好），执行飞行物理并移动；
 *   随后 LocalPlayer 通过原版移动包把新位置发给服务器。
 * - 服务器端取消 travel：原版 movePlayer 处理器已把客户端移动包中的位移通过
 *   entity.move(...) 应用并校验（moved-too-quickly 阈值 100 blocks/tick，远超本模组
 *   最大速度 1 block/tick）。若服务器再跑一遍飞行物理，会与原版位置同步叠加成双重移动。
 * - 客户端上其他玩家的实体副本不做本地物理，位置由服务器实体同步驱动。
 *
 * moveMask 仍随 PlayerControlPayload 发给服务器，目前仅作信息/后续校验用，不参与服务器物理。
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void spaceSim$onTravel(Vec3 travelVector, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!(self instanceof EntityRotation rot) || !rot.hasOrientation()) {
            return;
        }
        ci.cancel();
        // 服务器端：位置由客户端移动包驱动，取消 travel 防止双重移动
        if (!self.level().isClientSide()) {
            return;
        }
        // 客户端上其他玩家的实体副本：位置由服务器实体同步驱动，不做本地物理
        if (!self.isLocalPlayer()) {
            return;
        }

        int mask = 0;
        if (self.zza > 0.01F) mask |= FlightPhysics.MOVE_FORWARD;
        else if (self.zza < -0.01F) mask |= FlightPhysics.MOVE_BACK;
        if (self.xxa > 0.01F) mask |= FlightPhysics.MOVE_LEFT;
        else if (self.xxa < -0.01F) mask |= FlightPhysics.MOVE_RIGHT;
        // jumping 声明在 LivingEntity（非 Player），不能直接 @Shadow，经 accessor 暴露
        if (((LivingEntityAccessor) (Object) this).spaceSim$isJumping()) mask |= FlightPhysics.MOVE_UP;
        if (self.isShiftKeyDown()) mask |= FlightPhysics.MOVE_DOWN;
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
    }
}
