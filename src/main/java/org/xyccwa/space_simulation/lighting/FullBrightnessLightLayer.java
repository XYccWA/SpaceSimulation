package org.xyccwa.space_simulation.lighting;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightEventListener;

/**
 * 全亮光照监听器：光照等级恒为最大值 15，并且不产生任何光照更新工作。
 *
 * <p>原版 {@code LevelLightEngine.getLayerListener(LightLayer)} 返回的是真正的光照引擎
 * （{@code BlockLightEngine}/{@code SkyLightEngine}），其 {@code getLightValue} 需要查询
 * 光照数据层并可能触发 BFS 光照传播。这里替换成常 15 的实现后，所有读取光照的路径
 * （方块渲染 {@code LevelRenderer.getLightColor}、实体渲染 {@code EntityRenderer.getBlockLightLevel}、
 * {@code LevelReader.getMaxLocalRawBrightness} 等）都直接得到 15，且不再有任何计算。
 *
 * <p>{@code getDataLayerData} 返回 null 表示“无光照数据层”，这是原版对未加载区块的合法返回值，
 * 网络包 {@code ClientboundLightUpdatePacketData} 与存档序列化都能正确处理 null。
 */
public enum FullBrightnessLightLayer implements LayerLightEventListener {
    INSTANCE;

    /** 光照等级最大值（原版 LightEngine.MAX_LEVEL）。 */
    public static final int MAX_LIGHT = 15;

    @Nullable
    @Override
    public DataLayer getDataLayerData(SectionPos sectionPos) {
        return null;
    }

    @Override
    public int getLightValue(BlockPos levelPos) {
        return MAX_LIGHT;
    }

    @Override
    public void checkBlock(BlockPos pos) {
        // 无光照计算：忽略方块变化
    }

    @Override
    public boolean hasLightWork() {
        return false;
    }

    @Override
    public int runLightUpdates() {
        return 0;
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean isQueueEmpty) {
        // 无光照计算：忽略区段状态变化
    }

    @Override
    public void setLightEnabled(ChunkPos chunkPos, boolean lightEnabled) {
        // 无光照计算：忽略光照开关
    }

    @Override
    public void propagateLightSources(ChunkPos chunkPos) {
        // 无光照计算：忽略光源扫描
    }
}
