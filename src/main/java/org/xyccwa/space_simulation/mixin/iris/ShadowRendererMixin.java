package org.xyccwa.space_simulation.mixin.iris;

import com.mojang.blaze3d.vertex.PoseStack;

import net.irisshaders.iris.shadows.ShadowRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xyccwa.space_simulation.client.RadialShadowMatrices;

/**
 * 把 Iris 的阴影视图矩阵接管为「世界中心太阳」的径向方向。
 *
 * <p>{@code ShadowRenderer.createShadowModelView} 是 Iris 阴影视图矩阵（{@code MODELVIEW} 静态字段）
 * 的<b>唯一构造点</b>，且为 {@code public static}，签名
 * {@code (sunPathRotation, intervalSize, nearPlane, farPlane) -> PoseStack}。
 * 在这里返回自定义 PoseStack，Iris 后续的
 * {@code MODELVIEW = new Matrix4f(pose.last().pose())}、渲染 shadow pass、以及注入给光影包的
 * {@code shadowModelView} uniform 会全部跟着使用我们构造的矩阵，无需改动其它任何地方。
 *
 * <p>Iris 的类是模组类（非 Minecraft 混淆名），因此所有注入都必须 {@code remap = false}。
 */
@Mixin(value = ShadowRenderer.class, remap = false)
public class ShadowRendererMixin {

    @Inject(method = "createShadowModelView(FFFF)Lcom/mojang/blaze3d/vertex/PoseStack;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void spaceSimulation$radialShadowModelView(float sunPathRotation, float intervalSize,
                                                             float nearPlane, float farPlane,
                                                             CallbackInfoReturnable<PoseStack> cir) {
        PoseStack pose = RadialShadowMatrices.create(intervalSize);
        if (pose != null) {
            cir.setReturnValue(pose);
        }
        // pose == null：内建光影包未启用或相机不可用 → 保留 Iris 原版行为
    }
}
