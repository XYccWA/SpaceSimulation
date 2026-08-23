package org.xyccwa.space_simulation.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.xyccwa.space_simulation.api.EntityRotation;

/**
 * 让相机完全跟随玩家的四元数朝向，滚转在屏幕上真实可见。
 *
 * 1.21.1 的 Camera 内部本来就存 Quaternionf（setRotation 用 rotationYXZ 推导 forwards/up/left），
 * 但用的是欧拉角推导。本混入在 setup 末尾用玩家四元数直接覆盖 rotation 并重算 forwards/up/left：
 *   - 第一/三人称：rotation = q·Y(π)，使相机前向 = 玩家前向；
 *   - 前视第二人称（F5 镜像视角）：rotation = q，使相机前向 = 玩家前向的相反方向。
 * 之所以在 setup 末尾覆盖（而不是替换 setRotation），是因为 detached 相机在 move() 里要按
 * 原版 rotation 做后退/缩放定位，几何必须保持正确；覆盖放最后只影响最终朝向。
 *
 * 本地玩家读取平滑显示朝向（getSmoothedOrientation，ClientTickHandler 每帧指数缓动），
 * 远程玩家读取网络插值（getInterpolatedOrientation），两者都保证逐帧连续，消除"固定角度步进"顿挫。
 *
 * 另：眼球高度改为沿机体 up 轴偏移，滚转/翻转后第一人称视线仍对准眼睛位置。
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Unique
    private boolean spaceSim$frontView = false;
    @Unique
    private float spaceSim$partialTick = 0.0F;
    @Unique
    private BlockGetter spaceSim$level;
    @Unique
    private boolean spaceSim$detached = false;
    /** 相机碰撞退让量（格）。near plane=0.05，取 0.1 防贴面 z-fighting。 */
    @Unique
    private static final double CAMERA_CLIP_MARGIN = 0.1;

    @Shadow
    @Final
    private Entity entity;
    @Shadow
    @Final
    private Vector3f forwards;
    @Shadow
    @Final
    private Vector3f up;
    @Shadow
    @Final
    private Vector3f left;
    @Shadow
    @Final
    private Quaternionf rotation;
    @Shadow
    private Vec3 position;
    @Shadow
    @Final
    private BlockPos.MutableBlockPos blockPosition;
    @Shadow
    private float xRot;
    @Shadow
    private float yRot;
    @Shadow
    private float eyeHeight;
    @Shadow
    private float eyeHeightOld;

    @Inject(method = "setup", at = @At("HEAD"))
    private void spaceSim$setupHead(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        this.spaceSim$frontView = detached && thirdPersonReverse;
        this.spaceSim$partialTick = partialTick;
        this.spaceSim$level = level;
        this.spaceSim$detached = detached;
    }

    /**
     * 把眼睛位置从沿世界 Y 改为沿机体 up 轴偏移；第一人称下做一次相机碰撞（防穿模）。
     *
     * 滚转/翻转后眼睛沿 bodyUp 偏移（最多 1.62 格），可超出自身碰撞盒深入方块内部，
     * 而原版第一人称相机无碰撞。这里从旋转机体中心（在盒内=方块外）向期望眼睛位置
     * 沿 bodyUp 做一条 ClipContext 射线，命中则拉回墙面并退让 0.1 格（仿原版 getMaxZoom）。
     * 仅第一人称（非 detached）、非旁观者时生效；第三人称的 move(-getMaxZoom()) 走
     * setPosition(Vec3) 重载不被本 @Redirect 截获，且以其自身射线继续做后退碰撞。
     *
     * 朝向选择：本地玩家（第一人称）用 getOrientation()（严格当前朝向）——与拾取射线起点
     * （EntityMixin.spaceSim$onGetEyePosition 同为 current）严格重合，且与 setupTail 的相机
     * 朝向（current）同源，滚转/倒飞下画面中心 ≡ 射线。不能用 getSmoothedOrientation()：
     * 平滑朝向在滚转/倒飞时落后实际姿态，眼位偏移沿\"半转的 up 轴\"，相机位置与射线起点
     * 分离，准星所指与实际命中不一致（实测）。远程玩家用插值（网络 20Hz 补帧，视觉平滑，
     * 不参与本地瞄准）。
     */
    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void spaceSim$eyePosition(Camera instance, double x, double y, double z) {
        if (this.entity instanceof EntityRotation er && er.hasOrientation()) {
            float pt = this.spaceSim$partialTick;
            double ex = Mth.lerp((double) pt, this.entity.xo, this.entity.getX());
            double ey = Mth.lerp((double) pt, this.entity.yo, this.entity.getY());
            double ez = Mth.lerp((double) pt, this.entity.zo, this.entity.getZ());
            float eh = Mth.lerp(pt, this.eyeHeightOld, this.eyeHeight);
            // 本地玩家=严格当前朝向（与射线起点/相机朝向同源）；远程玩家=插值（网络 20Hz 补帧）。
            Quaternionf eyeQ = (this.entity instanceof LocalPlayer)
                    ? er.getOrientation()
                    : er.getInterpolatedOrientation(pt);
            Vector3f bodyUp = new Vector3f(0.0F, 1.0F, 0.0F).rotate(eyeQ);
            Vec3 desired = new Vec3(ex + bodyUp.x * eh, ey + bodyUp.y * eh, ez + bodyUp.z * eh);

            // 相机碰撞：仅第一人称（非 detached）、非旁观者（旁观者可穿墙看内部）。
            // 锚点 = 旋转机体中心 position + bodyUp*hh：与 desired 同轴（射线恰沿 bodyUp 直线，
            // 无假命中），且在碰撞盒内=方块外，是已知安全起点。
            if (!this.spaceSim$detached
                    && !(this.entity instanceof Player p && p.isSpectator())
                    && this.spaceSim$level != null) {
                float hh = this.entity.getBbHeight() * 0.5F;
                Vec3 anchor = new Vec3(ex + bodyUp.x * hh, ey + bodyUp.y * hh, ez + bodyUp.z * hh);
                Vec3 dir = desired.subtract(anchor);
                // eh>hh 保证方向朝外（常见姿态 eh>hh）；零向量时跳过，防 normalize 得 NaN 相机消失
                if (eh > hh + 1.0E-4F && dir.lengthSqr() > 1.0E-8) {
                    HitResult hit = this.spaceSim$level.clip(new ClipContext(
                            anchor, desired, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.entity));
                    if (hit.getType() != HitResult.Type.MISS) {
                        desired = hit.getLocation().subtract(dir.normalize().scale(CAMERA_CLIP_MARGIN));
                    }
                }
            }

            this.position = desired;
            this.blockPosition.set(desired.x, desired.y, desired.z);
        } else {
            this.position = new Vec3(x, y, z);
            this.blockPosition.set(x, y, z);
        }
    }

    /**
     * setup 末尾用四元数覆盖最终相机朝向。第一/三人称 = q·Y(π)，前视第二人称 = q。
     * 用 getOrientation()（当前朝向）而不是插值：相机应零滞后跟随鼠标/滚转输入，
     * 插值留给模型（LivingEntityRenderer 里 prev→current slerp）。
     */
    @Inject(method = "setup", at = @At("TAIL"))
    private void spaceSim$setupTail(CallbackInfo ci) {
        boolean front = this.spaceSim$frontView;
        this.spaceSim$frontView = false; // 复位，避免残留到下一帧
        if (this.entity instanceof EntityRotation er && er.hasOrientation()) {
            // 准星对齐关键：画面中心（准星）的像素方向必须与拾取/瞄准射线方向（getViewVector，
            // 已注入为 getOrientation() 当前实际朝向）严格一致，否则准星指向的方块 ≠ 实际瞄准方块。
            // 本地玩家第一人称必须用 getOrientation()（零滞后实际朝向），不能用 getSmoothedOrientation()
            // （平滑缓动在俯仰/滚转时严格滞后，导致准星背离瞄准点且永不收敛）。
            // 远程玩家朝向来自 20Hz 网络同步，用插值补足到每帧（准星横竖方向同样由 getViewVector 控制）。
            Quaternionf q = (this.entity instanceof LocalPlayer)
                    ? er.getOrientation()
                    : er.getInterpolatedOrientation(this.spaceSim$partialTick);
            Quaternionf rot = new Quaternionf(q);
            if (!front) {
                rot.mul(new Quaternionf().rotationY((float) Math.PI));
            }
            this.rotation.set(rot).normalize();
            new Vector3f(0.0F, 0.0F, -1.0F).rotate(this.rotation, this.forwards);
            new Vector3f(0.0F, 1.0F, 0.0F).rotate(this.rotation, this.up);
            new Vector3f(-1.0F, 0.0F, 0.0F).rotate(this.rotation, this.left);
            // 回写欧拉角，供读取相机 getYRot/getXRot 的原版逻辑保持一致
            this.yRot = (float) Math.toDegrees(Math.atan2(-this.forwards.x, this.forwards.z));
            this.xRot = (float) Math.toDegrees(Math.asin(-this.forwards.y));
        }
    }
}
