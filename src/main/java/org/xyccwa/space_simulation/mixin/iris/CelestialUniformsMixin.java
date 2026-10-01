package org.xyccwa.space_simulation.mixin.iris;

import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.uniforms.CelestialUniforms;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xyccwa.space_simulation.client.RadialSunDirection;

/**
 * 把 Iris 的天体方向接管为「世界中心太阳」的径向方向。
 *
 * <p>本模组的太阳固定在<b>世界原点</b>、光向外辐射：场景里任何一点的受光方向都等于
 * 「该点指向原点」的方向，而不是原版那种随世界时间转动的固定方向。
 *
 * <p><b>坐标空间（勿改）</b>：Iris 的 {@link CelestialUniforms} 有两个不同空间的接口，
 * 由 {@code getCelestialPosition}（乘 {@code gbufferModelView} → view space）与
 * {@code getCelestialPositionInWorldSpace}（不乘 → world space）实现：
 * <ul>
 *   <li>{@code getSunPosition()} / {@code getMoonPosition()} / {@code getShadowLightPosition()}
 *       —— <b>view space</b>，供 {@code sunPosition} / {@code shadowLightPosition} uniform 使用；</li>
 *   <li>{@code getShadowLightPositionInWorldSpace()} —— <b>world space</b>。</li>
 * </ul>
 * 因此这里必须分别注入对应的空间，否则光影包里的受光方向会整体错位。
 *
 * <p>{@code isDay()} 恒为 {@code true}：太空没有昼夜交替，避免 Iris 在「夜晚」切到月亮方向。
 *
 * <p>仅在<b>本模组内建光影包</b>启用时生效（{@link RadialSunDirection#enabled()}），
 * 用户启用其它光影包时行为完全不变。
 */
@Mixin(value = CelestialUniforms.class, remap = false)
public class CelestialUniformsMixin {

    /** 世界空间方向 → view space（与 Iris {@code getCelestialPosition} 的空间一致）。 */
    private static Vector4f spaceSimulation$toViewSpace(Vector4f worldDir) {
        Matrix4fc captured = CapturedRenderingState.INSTANCE.getGbufferModelView();
        if (captured == null) {
            // 视图矩阵尚不可用（极早期帧）：退回世界空间，至少不崩溃
            return worldDir;
        }
        Matrix4f modelView = new Matrix4f(captured);
        Vector4f v = new Vector4f(worldDir);
        // w = 0：方向向量，平移分量不参与
        modelView.transform(v);
        return v;
    }

    @Inject(method = "getSunPosition", at = @At("HEAD"), cancellable = true, remap = false)
    private void spaceSimulation$radialSunPosition(CallbackInfoReturnable<Vector4f> cir) {
        Vector4f world = RadialSunDirection.worldToSun();
        if (world != null) {
            cir.setReturnValue(spaceSimulation$toViewSpace(world));
        }
    }

    @Inject(method = "getMoonPosition", at = @At("HEAD"), cancellable = true, remap = false)
    private void spaceSimulation$radialMoonPosition(CallbackInfoReturnable<Vector4f> cir) {
        Vector4f world = RadialSunDirection.worldToSun();
        if (world != null) {
            cir.setReturnValue(spaceSimulation$toViewSpace(world));
        }
    }

    @Inject(method = "getShadowLightPositionInWorldSpace", at = @At("HEAD"), cancellable = true, remap = false)
    private void spaceSimulation$radialShadowLightInWorldSpace(CallbackInfoReturnable<Vector4f> cir) {
        Vector4f world = RadialSunDirection.worldToSun();
        if (world != null) {
            cir.setReturnValue(world);
        }
    }

    @Inject(method = "isDay", at = @At("HEAD"), cancellable = true, remap = false)
    private static void spaceSimulation$alwaysDay(CallbackInfoReturnable<Boolean> cir) {
        if (RadialSunDirection.enabled()) {
            cir.setReturnValue(Boolean.TRUE);
        }
    }
}
