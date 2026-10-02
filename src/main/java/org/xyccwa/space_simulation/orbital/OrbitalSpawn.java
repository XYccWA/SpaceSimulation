package org.xyccwa.space_simulation.orbital;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;

import java.util.Random;

/**
 * 首次进入世界时分配一条初始轨道（可配置范围内的随机轨道）。
 *
 * 触发时机（见 OrbitalPersistence）：
 *   - 登录时玩家存档里没有轨道根数（新玩家、旧存档第一次带本功能进入）；
 *   - 死亡重生（orbitalSpawnOnRespawn，默认开）——否则会掉在虚空中的世界出生点。
 * 之后根数随玩家 NBT 一起存档，再次登录走"离线解析传播"路径，不再重新分配。
 *
 * 随机源是"世界种子 + 玩家 UUID"的确定性混合：同一世界同一玩家只会得到同一条轨道，
 * 便于复现与联机一致；分配只发生一次，因此不会影响后续游戏。
 *
 * 轨道面：与小行星带同一片薄盘。**倾角在这里一律指相对 Minecraft 赤道面（XZ 平面，
 * y 为竖直轴）的夹角** —— 位置与速度直接在 Minecraft 坐标系里构造（先在 XZ 平面内取
 * 椭圆轨道上的点，再绕 X 轴倾斜 i、绕 Y 轴转方位角 φ），不走 TwoBody 的 (i, Ω) 参数化：
 * TwoBody 沿用天体力学约定（参考极轴是 z），直接拿它生成会把 i=0 变成"轨道在 XY 平面"
 * 即极轨道（实测踩过：起点被丢到 y = -107 万格）。存档与离线传播仍走 TwoBody，
 * 元素由状态反推（fromState），因此两边自洽。
 *
 * |y| 极值是远心点 r=a(1+e) 处的 r·sin(i)，取 i_max = asin(maxAbsY / (a(1+e))) 反推，
 * 保证整条轨道都落在 |y| ≤ maxAbsY 内（主世界高度只有 y ∈ [-512, 1024]，倾角一大
 * 就飞到虚空里什么也看不到）。
 */
public final class OrbitalSpawn {

    private OrbitalSpawn() {
    }

    /** 该玩家此刻是否应该分配初始轨道（配置开关 + 主世界）。 */
    public static boolean shouldAssign(ServerPlayer player) {
        if (!SpaceSimulationConfig.orbitalSpawnEnabled.get()) return false;
        if (!Gravity.enabled()) return false;
        return player.level() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD;
    }

    /**
     * 生成并应用一条初始轨道：把玩家放到轨道上一点，速度沿轨道切向。
     * 返回是否成功分配。
     */
    public static boolean assign(ServerPlayer player, String reason) {
        if (!shouldAssign(player)) return false;
        if (!(player.level() instanceof ServerLevel level)) return false;

        double mu = Gravity.mu();
        double rMin = SpaceSimulationConfig.orbitalSpawnRadiusMin.get();
        double rMax = Math.max(rMin, SpaceSimulationConfig.orbitalSpawnRadiusMax.get());
        double maxAbsY = SpaceSimulationConfig.orbitalSpawnMaxAbsY.get();
        double maxE = SpaceSimulationConfig.orbitalSpawnMaxEccentricity.get();

        Random rnd = new Random(mixSeed(level.getSeed(), player.getUUID().getMostSignificantBits(),
                player.getUUID().getLeastSignificantBits()));

        double a = rMin + rnd.nextDouble() * (rMax - rMin);
        double e = maxE <= 0.0 ? 0.0 : rnd.nextDouble() * maxE;
        double p = a * (1.0 - e * e);
        if (!(p > 0.0)) return false;

        // --- 1) 先在赤道面（XZ 平面）里取椭圆轨道上的一点与切向速度 ---
        double nu = rnd.nextDouble() * 2.0 * Math.PI;      // 真近点角（近心点起算）
        double r = p / (1.0 + e * Math.cos(nu));
        double k = Math.sqrt(mu / p);
        double[] pos = {r * Math.cos(nu), 0.0, r * Math.sin(nu)};
        double[] vel = {-k * Math.sin(nu), 0.0, k * (e + Math.cos(nu))};

        // --- 2) 绕 X 轴倾斜 incMc（相对赤道面），再绕 Y 轴转方位角 phi ---
        double apoapsis = a * (1.0 + e);
        double sinImax = apoapsis <= 0.0 ? 0.0 : Math.min(1.0, maxAbsY / apoapsis);
        double incMc = Math.asin(sinImax) * Math.sqrt(rnd.nextDouble());
        double phi = rnd.nextDouble() * 2.0 * Math.PI;
        rotateX(pos, incMc);
        rotateX(vel, incMc);
        rotateY(pos, phi);
        rotateY(vel, phi);

        double rr = Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2]);
        double speed = Math.sqrt(vel[0] * vel[0] + vel[1] * vel[1] + vel[2] * vel[2]);

        PlayerOrbitServer.placeAt(player, pos[0], pos[1], pos[2], vel[0], vel[1], vel[2]);
        // 个人重生点跟着走：否则死后仍会回到虚空里的旧出生点
        player.setRespawnPosition(Level.OVERWORLD,
                net.minecraft.core.BlockPos.containing(pos[0], pos[1], pos[2]), 0.0F, true, false);

        SpaceSimulation.LOGGER.info(
                "[轨道] {} 分配初始轨道（{}）：a={} e={} i={}° |y|≤{} → 起点 r={} y={} |v|={}（周期 {} tick）",
                player.getName().getString(), reason,
                String.format("%.0f", a), String.format("%.4f", e),
                String.format("%.3f", Math.toDegrees(incMc)), String.format("%.0f", maxAbsY),
                String.format("%.0f", rr), String.format("%.1f", pos[1]), String.format("%.4f", speed),
                String.format("%.0f", 2.0 * Math.PI * Math.sqrt(a * a * a / mu)));
        return true;
    }

    /** 绕 Minecraft 的 X 轴倾斜（y-z 平面内旋转）。 */
    private static void rotateX(double[] v, double angle) {
        if (angle == 0.0) return;
        double c = Math.cos(angle), s = Math.sin(angle);
        double y = v[1] * c - v[2] * s;
        double z = v[1] * s + v[2] * c;
        v[1] = y;
        v[2] = z;
    }

    /** 绕 Minecraft 的 Y 轴旋转（水平方位）。 */
    private static void rotateY(double[] v, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        double x = v[0] * c + v[2] * s;
        double z = -v[0] * s + v[2] * c;
        v[0] = x;
        v[2] = z;
    }

    /** 世界出生点的推荐位置：初始轨道带内、(rMin+rMax)/2 处的赤道面点。 */
    public static double worldSpawnRadius() {
        double rMin = SpaceSimulationConfig.orbitalSpawnRadiusMin.get();
        double rMax = Math.max(rMin, SpaceSimulationConfig.orbitalSpawnRadiusMax.get());
        return (rMin + rMax) * 0.5;
    }

    /** 世界种子 + 玩家 UUID → 确定性随机种子。 */
    private static long mixSeed(long worldSeed, long hi, long lo) {
        long h = worldSeed * 0x9E3779B97F4A7C15L;
        h ^= (hi + 0x165667B19E3779F9L) * 0xC2B2AE3D27D4EB4FL;
        h ^= (lo + 0x27D4EB2F165667C5L) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h;
    }
}

