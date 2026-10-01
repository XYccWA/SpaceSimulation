package org.xyccwa.space_simulation.asteroid;

import org.xyccwa.space_simulation.config.SpaceSimulationConfig;

import java.util.List;

/**
 * AsteroidUniverse 与配置/数据包之间的桥（唯一引用 Minecraft 配置类的位置）。
 * 把配置读取隔离在这儿，AsteroidUniverse/AsteroidOrbit 保持纯数值、可独立数学验证。
 *
 * 环带的几何与分布参数来自数据包（asteroid_belt / asteroid_type）；
 * 这里只提供全局项：种子 asteroidSeed 与 μ 基准周期 asteroidInnerOrbitPeriodTicks。
 */
public final class AsteroidUniverseSource {

    private AsteroidUniverseSource() {}

    /** 由数据包解析结果构造宇宙（seed / 全局内缘周期来自 STARTUP 配置）。 */
    public static AsteroidUniverse build(AsteroidTypeTable types, List<AsteroidBeltDef> belts) {
        return new AsteroidUniverse(
                SpaceSimulationConfig.asteroidSeed.get(),
                types,
                belts,
                SpaceSimulationConfig.asteroidInnerOrbitPeriodTicks.get());
    }

    /**
     * 数据包缺失或全部无效时的内置回退：单带 + 空类型表，
     * 参数等价于本系统引入数据包之前的默认环带（保证系统始终可用）。
     */
    public static AsteroidUniverse fallback() {
        AsteroidBeltDef def = new AsteroidBeltDef(
                "默认环带",
                1_000_000.0, 2_000_000.0,
                0.0, Math.toRadians(15.0),
                0.35, 1_073_741_824L,
                new int[0], new double[0], 0L);
        return build(AsteroidTypeTable.EMPTY, List.of(def));
    }
}
