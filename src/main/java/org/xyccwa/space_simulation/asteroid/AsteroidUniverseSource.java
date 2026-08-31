package org.xyccwa.space_simulation.asteroid;

import org.xyccwa.space_simulation.config.SpaceSimulationConfig;

/**
 * AsteroidUniverse 与配置之间的桥（唯一引用 Minecraft 配置类的位置）。
 * 把配置解密隔离在这儿，AsteroidUniverse/AsteroidOrbit 保持纯数值、可独立数学验证。
 */
public final class AsteroidUniverseSource {

    private AsteroidUniverseSource() {}

    /** 由 STARTUP 配置构造小行星宇宙（确定性参数全部来自配置文件）。 */
    public static AsteroidUniverse fromConfig() {
        return new AsteroidUniverse(
                SpaceSimulationConfig.asteroidSeed.get(),
                SpaceSimulationConfig.asteroidTotalCount.get(),
                SpaceSimulationConfig.asteroidInnerRadius.get(),
                SpaceSimulationConfig.asteroidOuterRadius.get(),
                SpaceSimulationConfig.asteroidMaxEccentricity.get(),
                SpaceSimulationConfig.asteroidMaxInclinationDeg.get(),
                SpaceSimulationConfig.asteroidInnerOrbitPeriodTicks.get());
    }
}