package org.xyccwa.space_simulation.asteroid;

import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * AsteroidUniverse 与配置/数据包之间的桥（唯一引用 Minecraft 配置类的位置）。
 * 把配置读取隔离在这儿，AsteroidUniverse/AsteroidOrbit 保持纯数值、可独立数学验证。
 *
 * 环带的几何与分布参数来自数据包（asteroid_belt / asteroid_type）；
 * 这里只提供全局项：种子 asteroidSeed 与引力参数 μ。
 *
 * <h2>μ 的两种来源</h2>
 * <ul>
 *   <li><b>跟随玩家轨道（默认）</b>：直接用玩家的 {@code orbitalMu}，使小行星与玩家处于同一引力场、
 *       速度同量级（可伴飞）。此时 {@code asteroidInnerOrbitPeriodTicks} 与带级
 *       {@code inner_orbit_period_ticks} 覆盖都被忽略（后者会告警）。</li>
 *   <li><b>独立周期（旧行为）</b>：由 {@code asteroidInnerOrbitPeriodTicks} 在带内缘半径处反推 μ。</li>
 * </ul>
 * AsteroidUniverse 只接受"最内带内缘处的周期"，所以跟随模式下这里先把它换算：
 * {@code T = 2π·√(r³/μ)}。
 */
public final class AsteroidUniverseSource {

    private AsteroidUniverseSource() {}

    /** 由数据包解析结果构造宇宙（seed / μ 来自 STARTUP 配置）。 */
    public static AsteroidUniverse build(AsteroidTypeTable types, List<AsteroidBeltDef> belts) {
        long period = SpaceSimulationConfig.asteroidInnerOrbitPeriodTicks.get();
        List<AsteroidBeltDef> effective = belts;

        if (SpaceSimulationConfig.asteroidMuFollowPlayerOrbital.get()) {
            double mu = SpaceSimulationConfig.orbitalMu.get();
            double rRef = Double.MAX_VALUE;
            for (AsteroidBeltDef d : belts) {
                rRef = Math.min(rRef, d.innerRadius);
            }
            if (mu > 0.0 && rRef > 0.0 && rRef < Double.MAX_VALUE) {
                long t = Math.max(1L, Math.round(2.0 * Math.PI * Math.sqrt(rRef * rRef * rRef / mu)));
                SpaceSimulation.LOGGER.info(
                        "[小行星] μ 跟随玩家轨道：orbitalMu={} → 最内带内缘 {} 处周期 {} tick（配置值 {} 被忽略）",
                        mu, String.format("%.0f", rRef), t, period);
                period = t;

                boolean anyOverride = false;
                for (AsteroidBeltDef d : belts) {
                    if (d.periodTicksOverride > 0L) {
                        anyOverride = true;
                        break;
                    }
                }
                if (anyOverride) {
                    SpaceSimulation.LOGGER.warn(
                            "[小行星] 已启用 μ 跟随玩家轨道，带级 inner_orbit_period_ticks 覆盖被忽略（否则该带会脱离同一引力场）");
                    effective = new ArrayList<>(belts.size());
                    for (AsteroidBeltDef d : belts) {
                        effective.add(new AsteroidBeltDef(d.name, d.innerRadius, d.outerRadius,
                                d.minInclinationRad, d.maxInclinationRad, d.maxEccentricity,
                                d.totalCount, d.typeIndices, d.typeCumulative, 0L));
                    }
                }
            }
        }

        return new AsteroidUniverse(
                SpaceSimulationConfig.asteroidSeed.get(),
                types,
                effective,
                period);
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

