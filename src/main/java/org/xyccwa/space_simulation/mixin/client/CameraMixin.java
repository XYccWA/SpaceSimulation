package org.xyccwa.space_simulation.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
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
    }

    /** 把眼睛位置从沿世界 Y 改为沿机体 up 轴偏移。 */
    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void spaceSim$eyePosition(Camera instance, double x, double y, double z) {
        if (this.entity instanceof EntityRotation er && er.hasOrientation()) {
            float pt = this.spaceSim$partialTick;
            double ex = Mth.lerp((double) pt, this.entity.xo, this.entity.getX());
            double ey = Mth.lerp((double) pt, this.entity.yo, this.entity.getY());
            double ez = Mth.lerp((double) pt, this.entity.zo, this.entity.getZ());
            float eh = Mth.lerp(pt, this.eyeHeightOld, this.eyeHeight);
            // 本地玩家用平滑显示朝向（与平滑相机/模型一致）；远程玩家用插值（网络 20Hz）。
            Quaternionf eyeQ = (this.entity instanceof LocalPlayer)
                    ? er.getSmoothedOrientation()
                    : er.getInterpolatedOrientation(pt);
            Vector3f bodyUp = new Vector3f(0.0F, 1.0F, 0.0F).rotate(eyeQ);
            this.position = new Vec3(ex + bodyUp.x * eh, ey + bodyUp.y * eh, ez + bodyUp.z * eh);
            this.blockPosition.set(this.position.x, this.position.y, this.position.z);
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
            // 本地玩家读平滑显示朝向（视角缓动）；远程玩家用网络插值，避免 20Hz 步进
            Quaternionf q = (this.entity instanceof LocalPlayer)
                    ? er.getSmoothedOrientation()
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
