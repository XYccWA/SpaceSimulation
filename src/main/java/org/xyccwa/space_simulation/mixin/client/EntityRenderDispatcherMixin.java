package org.xyccwa.space_simulation.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.xyccwa.space_simulation.api.EntityRotation;

/**
 * 让 F3+B 调试命中盒的红片/蓝线跟随四元数朝向，同时保留 hitboxapi 的绿色碰撞盒。
 *
 * 1.21.1 的 renderHitbox 是 private static：白盒（getBoundingBox 已被 makeBoundingBox 注入
 * 返回旋转 AABB）与 hitboxapi 的绿盒（hitbox-api 在 renderVector 调用处 @Inject 渲染碰撞体）
 * 本来就正确，只有红色眼高薄片（世界水平面 @ 原版站姿眼高）与蓝色视线（起点=原版直立眼位）
 * 不随旋转。
 *
 * 因此不用 HEAD cancel（那会把 hitboxapi 的绿盒注入点一并跳过），改为 @Redirect 两个几何调用，
 * 仅对 hasOrientation() 的实体替换成机体系版本：
 *   - renderVector（蓝线）：起点改为 q·(0,eh,0)（机体系眼位），方向 getViewVector 已被注入旋转。
 *   - renderLineBox(double×6)（红片）：pushPose+mulPose(q) 后画机体系薄片
 *     [-hw,hw]×[eh±0.01]×[-hw,hw]；同签名的黄盒(乘客)按 green 通道=1 区分，落回原版。
 * renderHitbox 是 static、redirect handler 拿不到实体，用 HEAD @Inject 把实体暂存到
 * @Unique static 字段（渲染线程单线程、renderHitbox 不递归，安全）。
 * q=identity 时与原版逐像素重合，站立/正常飞行回归安全。
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    /** renderHitbox 是 static，redirect handler 拿不到实体；HEAD @Inject 暂存（渲染线程，非递归，安全）。 */
    @Unique
    private static Entity spaceSim$hitboxEntity = null;

    @Inject(method = "renderHitbox", at = @At("HEAD"))
    private static void spaceSim$hitboxHead(
            PoseStack poseStack, VertexConsumer buffer, Entity entity,
            float partialTick, float red, float green, float blue, CallbackInfo ci) {
        spaceSim$hitboxEntity = entity;
    }

    /**
     * 需要旋转视角线：有朝向且非睡觉。睡觉时 makeBoundingBox 对 SLEEPING 跳过旋转（小盒），
     * 落回原版保持姿势一致。
     */
    @Unique
    private static boolean spaceSim$isOriented(Entity entity) {
        return entity instanceof EntityRotation er && er.hasOrientation()
                && !(entity instanceof LivingEntity living && living.isSleeping());
    }

    /**
     * 蓝线：起点改为机体系眼位 q·(0,eh,0)。方向 getViewVector 已被 mod 注入为 q·(0,0,1)，
     * 无需再转。非朝向实体逐参数落回原版。
     */
    @Redirect(method = "renderHitbox",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderVector"
                            + "(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                            + "Lorg/joml/Vector3f;Lnet/minecraft/world/phys/Vec3;I)V"))
    private static void spaceSim$redirectVector(PoseStack poseStack, VertexConsumer buffer,
            Vector3f startPos, Vec3 vector, int color) {
        Entity entity = spaceSim$hitboxEntity;
        if (entity != null && entity instanceof EntityRotation er && spaceSim$isOriented(entity)) {
            float eh = entity.getEyeHeight();
            Vector3f start = new Vector3f(0.0F, eh, 0.0F).rotate(er.getOrientation());
            spaceSim$renderVector(poseStack, buffer, start, vector, color);
        } else {
            spaceSim$renderVector(poseStack, buffer, startPos, vector, color);
        }
    }

    /**
     * 红片（double×6 重载、颜色 1,0,0,1，green 通道=0）：换成机体系薄片。
     * 同签名还有黄盒(乘客，1,1,0,1)与多段实体(0.25,1,0,1)，green=1 均落回原版。
     */
    @Redirect(method = "renderHitbox",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLineBox"
                            + "(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;"
                            + "DDDDDDFFFF)V"))
    private static void spaceSim$redirectLineBox(PoseStack poseStack, VertexConsumer buffer,
            double x1, double y1, double z1, double x2, double y2, double z2,
            float red, float green, float blue, float alpha) {
        Entity entity = spaceSim$hitboxEntity;
        boolean isRedSlice = red == 1.0F && green == 0.0F; // 红片(1,0,0) vs 黄盒(1,1,0)/多段(0.25,1,0)
        if (isRedSlice && entity != null && entity instanceof EntityRotation er && spaceSim$isOriented(entity)) {
            float hw = entity.getBbWidth() * 0.5F;
            float eh = entity.getEyeHeight();
            poseStack.pushPose();
            poseStack.mulPose(er.getOrientation()); // poseStack 纯平移，绕实体位置旋转
            LevelRenderer.renderLineBox(poseStack, buffer,
                    (double) (-hw), (double) (eh - 0.01F), (double) (-hw),
                    (double) hw, (double) (eh + 0.01F), (double) hw,
                    1.0F, 0.0F, 0.0F, 1.0F);
            poseStack.popPose();
        } else {
            LevelRenderer.renderLineBox(poseStack, buffer, x1, y1, z1, x2, y2, z2, red, green, blue, alpha);
        }
    }

    /** 原版 renderVector 的内联副本（private static 目标，内联避免 @Shadow 签名风险）。 */
    @Unique
    private static void spaceSim$renderVector(PoseStack poseStack, VertexConsumer buffer, Vector3f startPos, Vec3 vector, int color) {
        PoseStack.Pose pose = poseStack.last();
        buffer.addVertex(pose, startPos)
                .setColor(color)
                .setNormal(pose, (float) vector.x, (float) vector.y, (float) vector.z);
        buffer.addVertex(pose,
                        (float) (startPos.x() + vector.x),
                        (float) (startPos.y() + vector.y),
                        (float) (startPos.z() + vector.z))
                .setColor(color)
                .setNormal(pose, (float) vector.x, (float) vector.y, (float) vector.z);
    }
}
