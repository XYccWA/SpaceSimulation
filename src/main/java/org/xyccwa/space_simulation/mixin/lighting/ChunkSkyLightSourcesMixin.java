package org.xyccwa.space_simulation.mixin.lighting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xyccwa.space_simulation.lighting.LightingSettings;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;

/**
 * 天空光照源高度表（每个区块 16×16 列、逐格向下扫描找遮挡面）。
 *
 * <p>它只服务于 {@code SkyLightEngine} 的光照传播；全亮模式下传播已停摆，
 * 于是区块生成时的 {@code fillFrom}（全区块扫描）与每次方块变化时的 {@code update}
 * 都变成纯浪费，这里一并取消。
 *
 * <p>该表保持初始全零状态是安全的：{@code getLowestSourceY} 等读取方（仅 SkyLightEngine）
 * 也能处理该状态（返回 {@code NEGATIVE_INFINITY}）。
 */
@Mixin(ChunkSkyLightSources.class)
public class ChunkSkyLightSourcesMixin {

    @Inject(method = "fillFrom", at = @At("HEAD"), cancellable = true)
    private void spaceSimulation$fillFrom(ChunkAccess chunk, CallbackInfo ci) {
        if (LightingSettings.fullBrightness()) {
            ci.cancel();
        }
    }

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void spaceSimulation$update(BlockGetter level, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (LightingSettings.fullBrightness()) {
            cir.setReturnValue(false);
        }
    }
}
