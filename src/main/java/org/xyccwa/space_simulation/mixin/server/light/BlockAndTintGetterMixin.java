package org.xyccwa.space_simulation.mixin.server.light;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;

@Mixin(BlockAndTintGetter.class)
public interface BlockAndTintGetterMixin {

    /**
     * 劫持亮度查询 - 直接返回最大亮度
     */
    @Inject(method = "getBrightness", at = @At("HEAD"), cancellable = true)
    private void onGetBrightness(LightLayer lightType, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (SpaceSimulationConfig.enableLightShortCircuit.get()) {
            cir.setReturnValue(SpaceSimulationConfig.SHORT_CIRCUIT_LIGHT_LEVEL.get());
        }
    }

    /**
     * 劫持原始亮度查询
     */
    @Inject(method = "getRawBrightness", at = @At("HEAD"), cancellable = true)
    private void onGetRawBrightness(BlockPos pos, int amount, CallbackInfoReturnable<Integer> cir) {
        if (SpaceSimulationConfig.enableLightShortCircuit.get()) {
            cir.setReturnValue(SpaceSimulationConfig.SHORT_CIRCUIT_LIGHT_LEVEL.get());
        }
    }
}