package org.xyccwa.space_simulation.mixin.iris;

import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.state.StateUpdateNotifiers;
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder;
import net.irisshaders.iris.gl.uniform.FloatSupplier;
import net.irisshaders.iris.uniforms.CommonUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.xyccwa.space_simulation.client.RadialSunDirection;

/**
 * 向 Iris 注册专用 uniform {@code spaceSunDistance}（相机到世界中心的距离，格），
 * 供光影包做距离衰减。
 *
 * <p><b>为什么注入在 {@code addDynamicUniforms} 而不是 {@code CelestialUniforms.addCelestialUniforms}
 * （勿改）</b>：Iris 的 uniform 注册分两条路径 ——
 * <ul>
 *   <li>{@code CommonUniforms.addNonDynamicUniforms}：包含 {@code CameraUniforms}、
 *       {@code CelestialUniforms} 等，由 {@code IrisRenderingPipeline} 的
 *       {@code lambda$new$1(ProgramSet, UniformHolder)} 回调触发；</li>
 *   <li>{@code CommonUniforms.addDynamicUniforms}：由
 *       {@code ShaderCreator.create(...)} **直接调用**，是 gbuffers 各 program 必经之路。</li>
 * </ul>
 * 把注册挂在 {@code addDynamicUniforms} 的尾部，可以保证所有 program（gbuffers / composite /
 * deferred / shadow）都能拿到该 uniform，不依赖 nonDynamic 回调的触发条件。
 *
 * <p>{@code LocationalUniformHolder.uniform1f} 在 program 里找不到该 uniform location 时会
 * 静默跳过（不抛异常），所以即使用户启用其它光影包（不声明该 uniform）也完全安全。
 */
@Mixin(value = CommonUniforms.class, remap = false)
public class CommonUniformsMixin {

    @Inject(method = "addDynamicUniforms", at = @At("TAIL"), remap = false)
    private static void spaceSimulation$addSunDistanceUniform(DynamicUniformHolder holder, FogMode fogMode,
                                                              CallbackInfo ci) {
        FloatSupplier supplier = () -> (float) RadialSunDirection.sunDistance();
        holder.uniform1f("spaceSunDistance", supplier, StateUpdateNotifiers.phaseChangeNotifier);
    }
}
