package org.xyccwa.space_simulation.lighting;

import org.xyccwa.space_simulation.config.SpaceSimulationConfig;

/**
 * 光照设置的运行时缓存。
 *
 * <p>mixin 的光照读取路径（方块渲染、实体渲染、生物生成判定）每帧会被调用数十万次，
 * 直接读 {@code ModConfigSpec.ConfigValue#get()} 会带来可测量的开销，因此这里把配置值
 * 缓存在静态字段里，只在配置加载/重载时刷新一次。
 */
public final class LightingSettings {

    /** 默认 true：与配置默认值保持一致，即使加载事件未触发也按“全亮”运行。 */
    private static volatile boolean fullBrightness = true;

    private LightingSettings() {
    }

    /**
     * 是否启用全亮光照（每处都返回 15，且不做任何光照计算）。
     */
    public static boolean fullBrightness() {
        return fullBrightness;
    }

    /**
     * 从配置刷新缓存值，由 ModConfigEvent.Loading / Reloading 调用。
     */
    public static void refresh() {
        if (SpaceSimulationConfig.SPEC.isLoaded()) {
            fullBrightness = SpaceSimulationConfig.fullBrightness.get();
        }
    }
}
