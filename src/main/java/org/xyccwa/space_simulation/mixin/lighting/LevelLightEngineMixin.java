package org.xyccwa.space_simulation.mixin.lighting;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.xyccwa.space_simulation.lighting.FullBrightnessLightLayer;
import org.xyccwa.space_simulation.lighting.LightingSettings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;

/**
 * 原版光照总入口（客户端与服务端共用）：让每处光照都返回最大值 15，并且不再做光照计算。
 *
 * <p>读取端（全部返回 15）：
 * <ul>
 *   <li>{@code getLayerListener} → 常 15 的 {@link FullBrightnessLightLayer}，
 *       覆盖 {@code BlockAndTintGetter.getBrightness}（方块渲染、实体渲染、寻路）等所有路径。</li>
 *   <li>{@code getRawBrightness} → 15，覆盖 {@code BlockAndTintGetter.getRawBrightness} /
 *       {@code LevelReader.getMaxLocalRawBrightness} / {@code getLightLevelDependentMagicValue}
 *       （生物生成、作物生长、实体亮度）。</li>
 * </ul>
 *
 * <p>计算端（全部停摆）：
 * <ul>
 *   <li>{@code checkBlock}：方块变化不再登记待检节点（原版最高频的光照入口）。</li>
 *   <li>{@code updateSectionStatus} / {@code queueSectionData}：不再维护区段光照数据层，省掉每个区段 2KB 的分配。</li>
 *   <li>{@code propagateLightSources}：不再扫描区块光源。</li>
 *   <li>{@code runLightUpdates}：不执行 BFS 传播（客户端原本每帧调用一次）。</li>
 *   <li>{@code hasLightWork}：永远无待办，避免调度器空转。</li>
 * </ul>
 *
 * <p>保留 {@code setLightEnabled}：客户端 {@code LevelRenderer.compileSections} 依赖
 * {@code lightOnInSection} 判断区块能否编译，而它读的正是 {@code setLightEnabled} 维护的集合。
 *
 * <p>服务端 {@code ThreadedLevelLightEngine} 覆写了其中若干方法（异步投递任务），
 * 由 {@code ThreadedLevelLightEngineMixin} 一并拦截。
 */
@Mixin(LevelLightEngine.class)
public class LevelLightEngineMixin {

    @Inject(method = "getLayerListener", at = @At("HEAD"), cancellable = true)
    private void spaceSimulation$getLayerListener(LightLayer type, CallbackInfoReturnable<LayerLightEventListener> cir) {
        if (LightingSettings.fullBrightness()) {
            cir.setReturnValue(FullBrightnessLightLayer.INSTANCE);
        }
    }

    @Inject(method = "getRawBrightness", at = @At("HEAD"), cancellable = true)
    private void spaceSimulation$getRawBrightness(BlockPos blockPos, int amount, CallbackInfoReturnable<Integer> cir) {
        if (LightingSettings.fullBrightness()) {
            cir.setReturnValue(FullBrightnessLightLayer.MAX_LIGHT);
        }
    }

    @Inject(method = "checkBlock", at = @At("HEAD"), cancellable = true)
    private void spaceSimulation$checkBlock(BlockPos pos, CallbackInfo ci) {
        if (LightingSettings.fullBrightness()) {
            ci.cancel();
        }
    }

    @Inject(method = "updateSectionStatus", at = @At("HEAD"), cancellable = true)
    private void spaceSimulation$updateSectionStatus(SectionPos pos, boolean isEmpty, CallbackInfo ci) {
        if (LightingSettings.fullBrightness()) {
            ci.cancel();
        }
    }

    @Inject(method = "queueSectionData", at = @At("HEAD"), cancellable = true)
    private void spaceSimulation$queueSectionData(LightLayer lightLayer, SectionPos sectionPos, @Nullable DataLayer dataLayer, CallbackInfo ci) {
        if (LightingSettings.fullBrightness()) {
            ci.cancel();
        }
    }

    @Inject(method = "propagateLightSources", at = @At("HEAD"), cancellable = true)
    private void spaceSimulation$propagateLightSources(ChunkPos chunkPos, CallbackInfo ci) {
        if (LightingSettings.fullBrightness()) {
            ci.cancel();
        }
    }

    @Inject(method = "runLightUpdates", at = @At("HEAD"), cancellable = true)
    private void spaceSimulation$runLightUpdates(CallbackInfoReturnable<Integer> cir) {
        if (LightingSettings.fullBrightness()) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "hasLightWork", at = @At("HEAD"), cancellable = true)
    private void spaceSimulation$hasLightWork(CallbackInfoReturnable<Boolean> cir) {
        if (LightingSettings.fullBrightness()) {
            cir.setReturnValue(false);
        }
    }
}
