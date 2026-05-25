package org.xyccwa.space_simulation.pointlight;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.*;

/**
 * 缓存计算过的亮度值
 * 避免每帧对同一位置重复射线检测
 */
public class PointLightCache {

    // 使用 Map<维度, Map<位置, 亮度>>
    private final Map<Level, Map<Long, Integer>> cache = new ConcurrentHashMap<>();

    // 缓存有效期（ticks），默认 100 tick = 5 秒
    private static final int CACHE_TTL = 100;

    // 存储过期时间
    private final Map<Level, Map<Long, Integer>> expiry = new ConcurrentHashMap<>();

    private int tickCounter = 0;

    public int get(Level level, BlockPos pos) {
        Map<Long, Integer> levelCache = cache.get(level);
        if (levelCache == null) {
            return -1;
        }

        long key = pos.asLong();
        Integer value = levelCache.get(key);

        if (value == null) {
            return -1;
        }

        // 检查是否过期
        Map<Long, Integer> levelExpiry = expiry.get(level);
        if (levelExpiry != null) {
            Integer expireTick = levelExpiry.get(key);
            if (expireTick != null && expireTick < tickCounter) {
                // 已过期，移除
                levelCache.remove(key);
                levelExpiry.remove(key);
                return -1;
            }
        }

        return value;
    }

    public void put(Level level, BlockPos pos, int value) {
        Map<Long, Integer> levelCache = cache.computeIfAbsent(level, k -> new ConcurrentHashMap<>());
        Map<Long, Integer> levelExpiry = expiry.computeIfAbsent(level, k -> new ConcurrentHashMap<>());

        long key = pos.asLong();
        levelCache.put(key, value);
        levelExpiry.put(key, tickCounter + CACHE_TTL);
    }

    /**
     * 清除指定位置周围的缓存
     * 当地形改变时调用
     */
    public void invalidateAround(Level level, BlockPos center) {
        Map<Long, Integer> levelCache = cache.get(level);
        if (levelCache == null) {
            return;
        }

        Map<Long, Integer> levelExpiry = expiry.get(level);

        // 清除半径为 16 范围内的缓存（光线可能经过的区域）
        int radius = 16;
        BlockPos.betweenClosedStream(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius)
        ).forEach(pos -> {
            long key = pos.asLong();
            levelCache.remove(key);
            if (levelExpiry != null) {
                levelExpiry.remove(key);
            }
        });
    }

    /**
     * 每 tick 调用，更新计数器并使过期缓存失效
     */
    public void tick() {
        tickCounter++;

        // 每 100 tick 清理一次完全过期的缓存条目（可选优化）
        if (tickCounter % 100 == 0) {
            cleanup();
        }
    }

    private void cleanup() {
        for (Map.Entry<Level, Map<Long, Integer>> levelEntry : expiry.entrySet()) {
            Level level = levelEntry.getKey();
            Map<Long, Integer> levelExpiry = levelEntry.getValue();
            Map<Long, Integer> levelCache = cache.get(level);

            if (levelCache == null) continue;

            Iterator<Map.Entry<Long, Integer>> it = levelExpiry.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, Integer> entry = it.next();
                if (entry.getValue() < tickCounter) {
                    levelCache.remove(entry.getKey());
                    it.remove();
                }
            }
        }
    }

    /**
     * 清除所有缓存（用于世界卸载等场景）
     */
    public void clear(Level level) {
        cache.remove(level);
        expiry.remove(level);
    }

    public void clearAll() {
        cache.clear();
        expiry.clear();
    }
}