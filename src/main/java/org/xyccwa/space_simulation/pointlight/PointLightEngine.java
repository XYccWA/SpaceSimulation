package org.xyccwa.space_simulation.pointlight;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class PointLightEngine {

    private final PointLightCache cache;
    private final RayTracer rayTracer;

    public PointLightEngine() {
        this.cache = new PointLightCache();
        this.rayTracer = new RayTracer();
    }

    /**
     * 获取亮度值（带缓存）
     */
    public int getLightLevel(Level level, BlockPos pos) {
        // 检查缓存
        int cached = cache.get(level, pos);
        if (cached != -1) {
            return cached;
        }

        // 计算实际亮度
        int result = computeLightLevel(level, pos);

        // 存入缓存
        cache.put(level, pos, result);
        return result;
    }

    /**
     * 实际计算亮度（无缓存）
     */
    private int computeLightLevel(Level level, BlockPos pos) {
        BlockPos origin = new BlockPos(0, 0, 0);

        // 射线检测：从原点到目标位置
        boolean occluded = rayTracer.isOccluded(level, origin, pos);

        return occluded ? 0 : 15;
    }

    /**
     * 清除指定位置附近的缓存
     * 当地形改变时应调用此方法
     */
    public void invalidateCache(Level level, BlockPos changedPos) {
        cache.invalidateAround(level, changedPos);
    }
}