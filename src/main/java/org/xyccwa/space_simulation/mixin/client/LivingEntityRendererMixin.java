package org.xyccwa.space_simulation.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.xyccwa.space_simulation.api.EntityRotation;

/**
 * 玩家模型跟随四元数朝向（整身滚转/偏航/俯仰）。
 *
 * setupRotations 里第一个 Y 轴旋转（原本是 Y(180 - bodyYaw)）替换为 q·Y(180°)，
 * 使模型在 scale(-1,-1,1) 之后正确朝向。头部的 netHeadYaw/headPitch 置 0，因为整身已由 q 定向。
 * 通过 LivingEntityRenderer 基类混入，自动覆盖 PlayerRenderer（单人也影响其他玩家实体）。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Unique
    private LivingEntity spaceSim$renderEntity;
    @Unique
    private float spaceSim$renderPartialTick;

    @Inject(method = "setupRotations", at = @At("HEAD"))
    private void spaceSim$setupRotationsHead(LivingEntity entity, PoseStack poseStack, float ageInTicks, float yRot, float partialTicks, float scale, CallbackInfo ci) {
        this.spaceSim$renderEntity = entity;
        this.spaceSim$renderPartialTick = partialTicks;
    }

    @ModifyArg(
            method = "setupRotations",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V", ordinal = 0),
            index = 0)
    private Quaternionf spaceSim$bodyRotation(Quaternionf original) {
        LivingEntity entity = this.spaceSim$renderEntity;
        if (entity instanceof EntityRotation er && er.hasOrientation() && !entity.hasPose(Pose.SLEEPING)) {
            // 本地玩家：用平滑显示朝向，与平滑相机完全同步，转向/滚转时模型和相机一起缓动，消除逐帧顿挫。
            // 远程玩家：朝向来自 20Hz 网络同步，仍用 prev→current slerp 插值补足到每帧平滑。
            Quaternionf q = (entity instanceof LocalPlayer)
                    ? er.getSmoothedOrientation()
                    : er.getInterpolatedOrientation(this.spaceSim$renderPartialTick);
            return new Quaternionf(q).mul(new Quaternionf().rotationY((float) Math.PI));
        }
        return original;
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void spaceSim$setupAnim(EntityModel model, Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        if (((EntityRotation) entity).hasOrientation()) {
            model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, 0.0F, 0.0F);
        } else {
            model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
    }
}
