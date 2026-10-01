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
import org.xyccwa.space_simulation.util.SunRadius;

import java.util.Random;

/**
 * 玩家出生点 / 重生点管理。
 *
 * 历史教训（2026-08 实测修复）：
 * 1. 玩家登录/重生时绝不允许出现在太阳（世界原点，半径由世界种子派生）内部：
 *    太阳球体是世界中心的主体，出生在球内既看不到正常天空，也会让默认出生点
 *    （原版在原点附近）落在球体深处；
 * 2. 原实现把 {@code isSpawnPointSet}/{@code unifiedSpawnPoint}/{@code processedPlayers}
 *    做成 static 且跨世界不重置：单机同 JVM 内切换世界后，新世界会跳过出生点设置
 *    和玩家处理，玩家直接出生在默认 (8,64,8)（太阳内）并被跳过传送；
 * 3. 原实现用 {@code player.teleportTo(...)} 传送：它只发位置包等客户端 ACK，
 *    服务器实体位置不变。客户端加载 5M 格外的地形时 ACK 长期不到，服务器按
 *    旧位置（太阳内）判定。修复：先 {@code moveTo} 立即移动服务器位置（权威），
 *    再 {@code connection.teleport} 通知客户端，不依赖 ACK 时序。
 *
 * 保证不变量：
 * - 世界出生点（重生 fallback 安全网）永不在太阳内；
 * - 玩家登录/重生时若身处太阳内，立即被传送到圆环安全区并重设个人重生点。
 */
@EventBusSubscriber(modid = SpaceSimulation.MOD_ID)
public class PlayerSpawnPoint {

    public static final Logger LOGGER = LogManager.getLogger(SpaceSimulation.MOD_ID);

    /** 圆环内半径 / 外半径（格，世界原点为圆心） */
    private static final int INNER_RADIUS = 5000000;
    private static final int OUTER_RADIUS = 10000000;
    /** 出生点 Y 范围 */
    private static final int MIN_Y = -10;
    private static final int MAX_Y = 10;

    private static final Random random = new Random();

    /** 当前已处理的世界（static 缓存按世界实例重置，防止单机切换世界串号） */
    private static ServerLevel cachedLevel = null;
    /** 当前世界的统一出生点（世界出生点或圆环随机点，必然远离太阳） */
    private static BlockPos unifiedSpawnPoint = null;

    /** 在圆环区域内随机生成一个出生点 */
    private static BlockPos generateRingPoint() {
        double radius = INNER_RADIUS + random.nextDouble() * (OUTER_RADIUS - INNER_RADIUS);
        double angle = random.nextDouble() * 2 * Math.PI;
        int randomY = MIN_Y + random.nextInt(MAX_Y - MIN_Y + 1);
        return new BlockPos((int) (radius * Math.cos(angle)), randomY, (int) (radius * Math.sin(angle)));
    }

    /** 三维位置是否在太阳球体内（球心世界原点，半径由世界种子派生，见 SunRadius） */
    private static boolean isInsideSun(ServerLevel level, BlockPos pos) {
        double sunRadius = SunRadius.forServer(level.getServer());
        if (sunRadius <= 0) return false;
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        return x * x + y * y + z * z < sunRadius * sunRadius;
    }

    /**
     * 服务器权威传送：先 {@code moveTo} 立即移动服务器实体位置（不等客户端 ACK，
     * 服务器判定立刻以新位置为准），再 {@code connection.teleport} 通知客户端。
     */
    private static void teleportPlayerTo(ServerPlayer player, BlockPos target) {
        double x = target.getX() + 0.5;
        double y = target.getY();
        double z = target.getZ() + 0.5;
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        player.moveTo(x, y, z, yaw, pitch);
        player.connection.teleport(x, y, z, yaw, pitch);
    }

    /** 把玩家的个人重生点强制设为指定位置（forced：即使位置无效也重生到该处） */
    private static void setRespawnPosition(ServerPlayer player, BlockPos pos) {
        player.setRespawnPosition(Level.OVERWORLD, pos, 0f, true, false);
    }

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.OVERWORLD) return;

        // 世界切换时重置 static 缓存（单机同 JVM 内可能连续打开多个世界）
        if (cachedLevel != level) {
            cachedLevel = level;
            unifiedSpawnPoint = null;
        }

        // 世界出生点是所有"重生 fallback"的安全网：绝不允许留在太阳内部
        BlockPos worldSpawn = level.getSharedSpawnPos();
        if (isInsideSun(level, worldSpawn)) {
            unifiedSpawnPoint = generateRingPoint();
            level.setDefaultSpawnPos(unifiedSpawnPoint, 0f);
            LOGGER.info("World spawn was inside the sun; moved to unified spawn at ({}, {}, {})",
                    unifiedSpawnPoint.getX(), unifiedSpawnPoint.getY(), unifiedSpawnPoint.getZ());
        } else {
            unifiedSpawnPoint = worldSpawn;
            LOGGER.info("World spawn safe at ({}, {}, {})",
                    worldSpawn.getX(), worldSpawn.getY(), worldSpawn.getZ());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        if (level.isClientSide()) return;

        boolean useUnified = SpaceSimulationConfig.useUnifiedSpawn.get();
        BlockPos target = useUnified
                ? (unifiedSpawnPoint != null ? unifiedSpawnPoint : generateRingPoint())
                : generateRingPoint();

        if (isInsideSun(level, player.blockPosition())) {
            // 登录位置在太阳内部（旧档出生点、未修正的世界出生点等）：
            // 立即拉出太阳 + 重设个人重生点
            setRespawnPosition(player, target);
            teleportPlayerTo(player, target);
            LOGGER.info("Player {} logged in inside the sun at ({}, {}, {}); teleported to ({}, {}, {})",
                    player.getName().getString(),
                    player.getX(), player.getY(), player.getZ(),
                    target.getX(), target.getY(), target.getZ());
        } else if (player.getRespawnPosition() == null) {
            // 首次进入且已在安全位置：只设置个人重生点（死亡后回到此处）
            setRespawnPosition(player, target);
            LOGGER.info("Set respawn position for {} at ({}, {}, {})",
                    player.getName().getString(),
                    target.getX(), target.getY(), target.getZ());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        if (level.isClientSide()) return;

        // 兜底：无论什么原因（重生点缺失/无效、世界出生点仍在太阳内等）重生到了
        // 太阳内部，立即拉回圆环安全区并重设个人重生点
        if (isInsideSun(level, player.blockPosition())) {
            BlockPos target = unifiedSpawnPoint != null ? unifiedSpawnPoint : generateRingPoint();
            setRespawnPosition(player, target);
            teleportPlayerTo(player, target);
            LOGGER.info("Player {} respawned inside the sun at ({}, {}, {}); teleported to ({}, {}, {})",
                    player.getName().getString(),
                    player.getX(), player.getY(), player.getZ(),
                    target.getX(), target.getY(), target.getZ());
        }
    }
}
