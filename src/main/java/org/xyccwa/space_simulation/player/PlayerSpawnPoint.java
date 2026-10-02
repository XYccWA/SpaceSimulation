package org.xyccwa.space_simulation.player;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;
import org.xyccwa.space_simulation.orbital.Gravity;
import org.xyccwa.space_simulation.orbital.OrbitalSpawn;
import org.xyccwa.space_simulation.util.SunRadius;

/**
 * 出生点 / 重生点管理（2026-10 重构：删除 500–1000 万格的"圆环出生点"）。
 *
 * 玩家现在不在地面上出生：首次进入世界（以及可选的死亡重生）由 {@link OrbitalSpawn}
 * 直接放进一条初始轨道。本类只剩两件事：
 *
 * 1. 世界出生点（共享重生 fallback）不能落在太阳里，也不该留在旧存档的"圆环"位置，
 *    否则原版重生会把玩家丢进太阳或丢到几十万格外的虚空；
 * 2. 登录/重生在太阳内时兜底（初始轨道功能关闭时把玩家挪出球体；开启时交给轨道分配）。
 *
 * 历史教训（保留）：
 * - 玩家登录/重生时绝不允许出现在太阳（世界原点）内部；
 * - static 缓存必须按世界实例重置，否则单机切换世界会串号；
 * - 传送用 {@code moveTo} 立即移动服务器实体（权威），再用 {@code connection.teleport} 通知客户端，
 *   不依赖客户端 ACK 时序。
 */
@EventBusSubscriber(modid = SpaceSimulation.MOD_ID)
public class PlayerSpawnPoint {

    public static final Logger LOGGER = LogManager.getLogger(SpaceSimulation.MOD_ID);

    /** 世界出生点允许的最大半径（块）：超过它说明是旧"圆环出生点"，需要纠正回轨道带。 */
    private static final double MAX_SANE_SPAWN_RADIUS = 3_000_000.0;

    /** 三维位置是否在太阳球体内（球心世界原点，半径由世界种子派生，见 SunRadius） */
    private static boolean isInsideSun(ServerLevel level, double x, double y, double z) {
        double sunRadius = SunRadius.forServer(level.getServer());
        if (sunRadius <= 0) return false;
        return x * x + y * y + z * z < sunRadius * sunRadius;
    }

    /** 初始轨道带内的赤道面出生点（轨道未启用时退回太阳球体之外）。 */
    private static BlockPos orbitalSpawnPoint(ServerLevel level) {
        double r = Gravity.enabled() ? OrbitalSpawn.worldSpawnRadius() : 0.0;
        if (r <= 0.0) {
            double sunRadius = SunRadius.forServer(level.getServer());
            r = Math.max(1.0, sunRadius * 2.0);
        }
        return BlockPos.containing(r, 0.0, 0.0);
    }

    /** 服务器权威传送：先 moveTo 立即改服务器位置，再 connection.teleport 通知客户端。 */
    private static void teleportPlayerTo(ServerPlayer player, BlockPos target) {
        double x = target.getX() + 0.5;
        double y = target.getY();
        double z = target.getZ() + 0.5;
        player.setRespawnPosition(Level.OVERWORLD, target, 0f, true, false);
        player.moveTo(x, y, z, player.getYRot(), player.getXRot());
        player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
    }

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;

        // 世界出生点是所有"重生 fallback"的安全网：既不能在太阳内，也不该留在旧的圆环位置
        BlockPos worldSpawn = level.getSharedSpawnPos();
        double dist = Math.sqrt(worldSpawn.distSqr(BlockPos.ZERO));
        boolean insideSun = isInsideSun(level, worldSpawn.getX(), worldSpawn.getY(), worldSpawn.getZ());
        if (insideSun || dist > MAX_SANE_SPAWN_RADIUS) {
            BlockPos target = orbitalSpawnPoint(level);
            level.setDefaultSpawnPos(target, 0f);
            LOGGER.info("[出生点] 世界出生点 ({}, {}, {}) {} → 改为初始轨道带 ({}, {}, {})",
                    worldSpawn.getX(), worldSpawn.getY(), worldSpawn.getZ(),
                    insideSun ? "在太阳内" : "在旧的圆环位置",
                    target.getX(), target.getY(), target.getZ());
        } else {
            LOGGER.info("[出生点] 世界出生点安全：({}, {}, {})",
                    worldSpawn.getX(), worldSpawn.getY(), worldSpawn.getZ());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        if (level.isClientSide()) return;

        if (!isInsideSun(level, player.getX(), player.getY(), player.getZ())) return;

        if (SpaceSimulationConfig.orbitalSpawnEnabled.get()) {
            // 初始轨道功能负责搬运（见 OrbitalPersistence 登录流程）
            LOGGER.info("[出生点] {} 登录在太阳内 → 交给初始轨道分配",
                    player.getName().getString());
            return;
        }
        BlockPos target = orbitalSpawnPoint(level);
        teleportPlayerTo(player, target);
        LOGGER.info("[出生点] {} 登录在太阳内，已挪到 ({}, {}, {})",
                player.getName().getString(), target.getX(), target.getY(), target.getZ());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        if (level.isClientSide()) return;

        if (!isInsideSun(level, player.getX(), player.getY(), player.getZ())) return;
        if (SpaceSimulationConfig.orbitalSpawnOnRespawn.get()) {
            LOGGER.info("[出生点] {} 重生在太阳内 → 交给初始轨道分配",
                    player.getName().getString());
            return;
        }
        BlockPos target = orbitalSpawnPoint(level);
        teleportPlayerTo(player, target);
        LOGGER.info("[出生点] {} 重生在太阳内，已挪到 ({}, {}, {})",
                player.getName().getString(), target.getX(), target.getY(), target.getZ());
    }
}
