package org.xyccwa.space_simulation.orbital;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;
import org.xyccwa.space_simulation.network.OrbitalParamsPayload;
import org.xyccwa.space_simulation.util.SunRadius;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家离线期间的轨道保持。
 *
 * 玩家下线后实体不再 tick（原版行为），因此不做数值积分，而是把"下线那一刻的轨道根数 +
 * 服务器 tick 时刻"存档（见 PlayerMixin.addAdditionalSaveData）。下次登录时按
 * 当前 tick 与存档时刻之差解析传播（{@link TwoBody}），得到位置与速度，再归位并下发基准
 * 状态给客户端。等价于"人不在，轨道照跑"。
 *
 * 只在服务器未关闭/未暂停时时间才前进；单机暂停时 game time 不动，传播量为零。
 */
@EventBusSubscriber(modid = SpaceSimulation.MOD_ID)
public final class OrbitalPersistence {

    private OrbitalPersistence() {
    }

    /** 存档里读出的轨道根数（等登录后再传播）。 */
    public record SavedOrbit(double a, double e, double inclination, double raan,
                             double argPeriapsis, double meanAnomaly0, double mu, long t0) {
    }

    /** 实体 NBT 读取阶段（构造 ServerPlayer 时）暂存，登录事件里消费。 */
    private static final Map<UUID, SavedOrbit> PENDING = new ConcurrentHashMap<>();

    /** 实体加载时由 PlayerMixin.readAdditionalSaveData 调用。 */
    public static void offerSavedOrbit(UUID id, SavedOrbit orbit) {
        if (id != null && orbit != null) {
            PENDING.put(id, orbit);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // 客户端预测必须与服务器用同一个 μ，先下发参数（多人游戏里客户端读不到服务器配置）
        PacketDistributor.sendToPlayer(player, new OrbitalParamsPayload(Gravity.enabled(), Gravity.mu()));

        if (!Gravity.enabled()) return;
        if (player.serverLevel().dimension() != Level.OVERWORLD) return;

        if (spaceSim$insideSun(player)) {
            // 登录点在太阳内：不能留在球心，直接分配一条初始轨道（没开初始轨道时才原地清速度）
            player.setDeltaMovement(Vec3.ZERO);
            SavedOrbit pendingInside = PENDING.remove(player.getUUID());
            if (pendingInside == null && OrbitalSpawn.assign(player, "登录点在太阳内")) {
                return;
            }
            PlayerOrbitServer.markReset(player);
            return;
        }

        SavedOrbit saved = PENDING.remove(player.getUUID());
        if (saved == null) {
            // 首次进入世界（新玩家 / 旧存档第一次带本功能）：按配置范围随机分配一条初始轨道
            if (OrbitalSpawn.assign(player, "首次进入世界")) {
                return;
            }
            // 未启用初始轨道：以当前位置为基准
            PlayerOrbitServer.markReset(player);
            return;
        }
        if (Math.abs(saved.mu - Gravity.mu()) > 1e-9 * Math.max(1.0, Gravity.mu())) {
            SpaceSimulation.LOGGER.warn("[轨道] {} 的存档 μ={} 与当前 μ={} 不一致，跳过离线传播",
                    player.getName().getString(), saved.mu, Gravity.mu());
            PlayerOrbitServer.markReset(player);
            return;
        }

        long now = player.serverLevel().getGameTime();
        long dt = Math.max(0L, now - saved.t0());
        TwoBody.Elements el = TwoBody.Elements.of(saved.a(), saved.e(), saved.inclination(),
                saved.raan(), saved.argPeriapsis(), saved.meanAnomaly0());
        double[][] state = TwoBody.stateAt(el, Gravity.mu(), dt);
        if (state == null) {
            PlayerOrbitServer.markReset(player);
            return;
        }
        double[] pos = state[0];
        double[] vel = state[1];
        double r = Math.sqrt(pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2]);
        double sunRadius = SunRadius.forServer(player.getServer());
        if (r < sunRadius) {
            // 传播结果落进太阳（近日点过低）：不把人直接送死，保持存档位置与速度
            SpaceSimulation.LOGGER.warn("[轨道] {} 的离线传播落点在太阳内（r={} < R={}），保持原位置",
                    player.getName().getString(), (long) r, (long) sunRadius);
            PlayerOrbitServer.markReset(player);
            return;
        }

        PlayerOrbitServer.placeAt(player, pos[0], pos[1], pos[2], vel[0], vel[1], vel[2]);
        SpaceSimulation.LOGGER.info("[轨道] {} 离线 {} tick 后回到轨道（r={}，|v|={}）",
                player.getName().getString(), dt, (long) r, String.format("%.4f", Math.sqrt(
                        vel[0] * vel[0] + vel[1] * vel[1] + vel[2] * vel[2])));
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        PlayerOrbitServer.forget(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.setDeltaMovement(Vec3.ZERO);
        // 死亡重生：默认重新分配一条初始轨道，避免落在虚空里的世界出生点静止
        if (SpaceSimulationConfig.orbitalSpawnOnRespawn.get()
                && OrbitalSpawn.assign(player, "死亡重生")) {
            return;
        }
        PlayerOrbitServer.markReset(player);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    /** 位置是否在太阳球体内（球心世界原点，半径由世界种子派生）。 */
    private static boolean spaceSim$insideSun(ServerPlayer player) {
        double sunRadius = SunRadius.forServer(player.getServer());
        if (sunRadius <= 0) return false;
        double x = player.getX(), y = player.getY(), z = player.getZ();
        return x * x + y * y + z * z < sunRadius * sunRadius;
    }
}
