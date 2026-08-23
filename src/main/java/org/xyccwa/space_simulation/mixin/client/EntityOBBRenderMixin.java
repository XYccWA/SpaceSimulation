package org.xyccwa.space_simulation.mixin.client;

import cn.anecansaitin.hitboxapi.api.client.collider.CollisionRenderUtil;
import cn.anecansaitin.hitboxapi.api.common.collider.ICollider;
import cn.anecansaitin.hitboxapi.api.common.collider.IOBB;
import cn.anecansaitin.hitboxapi.api.common.collider.local.ILocalOBB;
import cn.anecansaitin.hitboxapi.client.collider.render.EntityOBBRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.xyccwa.space_simulation.api.EntityRotation;

/**
 * 修复远距离（如 450000 格）绿盒碰撞箱抖动。
 *
 * 抖动根源：hitboxapi 的 {@link EntityOBBRender#render} 用"OBB 全局中心 - 实体位置"
 * 计算渲染偏移，而 OBB 全局中心来自 {@code EntityCoordinateConverter} 的 float 位置
 * （字段是 {@code Vector3f}，update() 里 Vec3 double → float 直接截断）。在 450000 格处
 * float 的 ULP≈0.03125，实体移动时 (float)entity.x 的量化残差在 ±0.0156 内跳变，
 * 绿盒相对实体每 tick 蠕动/抖动（约十几像素）。
 *
 * 修复：对本 mod 的旋转玩家，绕开 converter 的 float 全局位置，直接用纯本地量：
 * - 渲染偏移 = localCenter（PlayerHitbox.update 已把它预旋转成世界系偏移，是 <2 格的
 *   float 相对量，与实体绝对位置无关，任意距离精确到 1e-7 格）；
 * - 渲染旋转 = localRotation（converter rotation 恒 identity，localRotation 即朝向 q）。
 * 非本 mod 实体走原库路径，行为不变。
 */
@Mixin(EntityOBBRender.class)
public abstract class EntityOBBRenderMixin {

    @Inject(method = "render(Lnet/minecraft/world/entity/Entity;"
            + "Lcn/anecansaitin/hitboxapi/api/common/collider/ICollider;"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void spaceSim$render(Entity entity, ICollider<Entity, ?> collider, PoseStack poseStack,
            VertexConsumer buffer, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (!(entity instanceof EntityRotation er) || !er.hasOrientation()) {
            return; // 非本 mod 旋转实体：走库的原版渲染路径
        }
        if (!(collider instanceof ILocalOBB local)) {
            return;
        }
        IOBB obb = (IOBB) local; // ILocalOBB extends IOBB
        Vector3f center = local.getLocalCenter();
        Vector3f half = obb.getHalfExtents();
        CollisionRenderUtil.renderOBB(poseStack, buffer,
                center.x, center.y, center.z,
                local.getLocalRotation(),
                half.x, half.y, half.z,
                red, green, blue, alpha);
        ci.cancel();
    }
}
