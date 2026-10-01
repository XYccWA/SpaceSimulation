package org.xyccwa.space_simulation.mixin.lighting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xyccwa.space_simulation.lighting.LightingSettings;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LightEngine;

/**
 * 光照计算的前置判定：原版每次方块变化都会比较两个方块的遮光与发光属性，
 * 用来决定是否需要更新光照（{@code LevelChunk.setBlockState} / {@code ProtoChunk.setBlockState}）。
 * 全亮模式下光照恒为 15，这个判定没有任何意义，直接返回 false 省掉
 * {@code getLightBlock} 与体素形状比较。
 */
@Mixin(LightEngine.class)
public class LightEngineMixin {

    @Inject(method = "hasDifferentLightProperties", at = @At("HEAD"), cancellable = true)
    private static void spaceSimulation$hasDifferentLightProperties(
            BlockGetter level, BlockPos pos, BlockState state1, BlockState state2, CallbackInfoReturnable<Boolean> cir) {
        if (LightingSettings.fullBrightness()) {
            cir.setReturnValue(false);
        }
    }
}
