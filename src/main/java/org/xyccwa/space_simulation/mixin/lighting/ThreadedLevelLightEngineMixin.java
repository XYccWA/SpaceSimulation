package org.xyccwa.space_simulation.mixin.lighting;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.xyccwa.space_simulation.lighting.LightingSettings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;

/**
 * 服务端光照引擎：{@code ThreadedLevelLightEngine} 覆写了父类的若干方法，
 * 把工作包装成任务投递到区块排序线程池（每个方块变化都会投递一条消息）。
 * 这里直接取消这些高频投递，让主线程与光照线程池都零开销。
 *
 * <p>被取消的方法（父类实现已由 {@code LevelLightEngineMixin} 置空，这里再省掉消息投递本身）：
 * {@code checkBlock}（每方块变化）、{@code updateSectionStatus}（世界生成时每区段变化）、
 * {@code propagateLightSources}（区块加载的光源扫描）、{@code queueSectionData}（读盘光照数据）。
 *
 * <p>刻意<b>不</b>取消 {@code tryScheduleUpdate} / {@code runUpdate} / {@code initializeLight} /
 * {@code lightChunk} / {@code updateChunkStatus}：
 * 任务队列仍需运转（{@code ChunkMap} 用 {@code waitForPendingTasks} 的完成作为区块发送依赖），
 * 区块卸载时的清理任务也要照常执行；其中真正昂贵的光照传播已由父类侧置空。
 */
@Mixin(ThreadedLevelLightEngine.class)
public class ThreadedLevelLightEngineMixin {

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
}
