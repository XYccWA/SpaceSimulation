package org.xyccwa.space_simulation.pointlight;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 点光源系统对外API
 * 供太阳能板等模组调用，获取基于点光源的亮度值
 */
public class PointLightAPI {

    private static PointLightEngine engine;

    public static void init() {
        engine = new PointLightEngine();
    }

    /**
     * 获取指定位置的点光源亮度值（0-15）
     * @param level 世界实例
     * @param pos 方块位置
     * @return 亮度值，0=阴影，15=被照亮
     */
    public static int getLightLevel(Level level, BlockPos pos) {
        if (engine == null) {
            init();
        }
        return engine.getLightLevel(level, pos);
    }

    /**
     * 判断指定位置是否被点光源照亮
     */
    public static boolean isLit(Level level, BlockPos pos) {
        return getLightLevel(level, pos) >= 15;
    }

    /**
     * 判断指定位置是否在阴影中
     */
    public static boolean isInShadow(Level level, BlockPos pos) {
        return getLightLevel(level, pos) == 0;
    }

    /**
     * 当地形改变时调用，清除缓存
     */
    public static void onBlockChange(Level level, BlockPos pos) {
        if (engine != null) {
            engine.invalidateCache(level, pos);
        }
    }
}