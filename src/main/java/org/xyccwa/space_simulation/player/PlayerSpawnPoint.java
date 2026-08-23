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

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * 玩家出生点 / 重生点管理。
 *
 * 历史教训（2026-08 实测修复）：
 * 1. 玩家登录/重生时绝不允许出现在太阳（世界原点，半径=solarKillRadius）内部，
 *    否则会被 SunKillHandler 秒杀，且因重生点缺失/无效回退到"太阳内的世界出生点"
 *    形成"重生→太阳→死亡"死循环；
 * 2. 原实现把 {@code isSpawnPointSet}/{@code unifiedSpawnPoint}/{@code processedPlayers}
 *    做成 static 且跨世界不重置：单机同 JVM 内切换世界后，新世界会跳过出生点设置
 *    和玩家处理，玩家直接出生在默认 (8,64,8)（太阳内）并被跳过传送 → 秒杀循环；
 * 3. 原实现用 {@code player.teleportTo(...)} 传送：它只发位置包等客户端 ACK，
 *    服务器实体位置不变。客户端加载 5M 格外的地形时 ACK 长期不到，服务器按
 *    旧位置（太阳内）判杀。修复：先 {@code moveTo} 立即移动服务器位置（权威），
 *    再 {@code connection.teleport} 通知客户端，不依赖 ACK 时序。
 *
 * 保证不变量：
 * - 世界出生点（重生 fallback 安全网）永不在太阳内；
 * - 玩家登录/重生时若身处太阳内，立即被传送到圆环安全区并重设个人重生点；
 * - 登录/重生后有短暂保护期（SPAWN_GRACE_TICKS），SunKillHandler 不击杀。
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

    /** 登录/重生后的太阳击杀保护期（tick，100 = 5 秒） */
    public static final int SPAWN_GRACE_TICKS = 100;

    private static final Random random = new Random();

    /** 当前已处理的世界（static 缓存按世界实例重置，防止单机切换世界串号） */
    private static ServerLevel cachedLevel = null;
    /** 当前世界的统一出生点（世界出生点或圆环随机点，必然远离太阳） */
    private static BlockPos unifiedSpawnPoint = null;

    /** 登录/重生保护期：UUID -> 所在世界 gameTime 截止值（过期即清理） */
    private static final Map<UUID, Long> spawnGraceUntil = new HashMap<>();

    /** 在圆环区域内随机生成一个出生点 */
    private static BlockPos generateRingPoint() {
        double radius = INNER_RADIUS + random.nextDouble() * (OUTER_RADIUS - INNER_RADIUS);
        double angle = random.nextDouble() * 2 * Math.PI;
        int randomY = MIN_Y + random.nextInt(MAX_Y - MIN_Y + 1);
        return new BlockPos((int) (radius * Math.cos(angle)), randomY, (int) (radius * Math.sin(angle)));
    }

    /** 三维位置是否在太阳击杀球体内（与 SunKillHandler 同一判定） */
    public static boolean isInsideSun(double x, double y, double z) {
        double killRadius = SpaceSimulationConfig.solarKillRadius.get();
        if (killRadius <= 0) return false;
        return x * x + y * y + z * z < killRadius * killRadius;
    }

    public static boolean isInsideSun(BlockPos pos) {
        return isInsideSun(pos.getX(), pos.getY(), pos.getZ());
    }

    /** 授予登录/重生保护期（期间 SunKillHandler 不击杀） */
    public static void grantSpawnGrace(ServerPlayer player) {
        spawnGraceUntil.put(player.getUUID(), player.serverLevel().getGameTime() + SPAWN_GRACE_TICKS);
    }

    /** 是否处于登录/重生保护期内 */
    public static boolean isWithinSpawnGrace(ServerPlayer player) {
        Long until = spawnGraceUntil.get(player.getUUID());
        if (until == null) return false;
        if (player.serverLevel().getGameTime() >= until) {
            spawnGraceUntil.remove(player.getUUID());
            return false;
        }
        return true;
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
        if (isInsideSun(worldSpawn)) {
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

        grantSpawnGrace(player);

        boolean useUnified = SpaceSimulationConfig.useUnifiedSpawn.get();
        BlockPos target = useUnified
                ? (unifiedSpawnPoint != null ? unifiedSpawnPoint : generateRingPoint())
                : generateRingPoint();

        if (isInsideSun(player.blockPosition())) {
            // 登录位置在太阳内部（旧档出生点、未修正的世界出生点等）：
            // 立即拉出太阳 + 重设个人重生点，杜绝"登录即死"
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

        grantSpawnGrace(player);

        // 兜底：无论什么原因（重生点缺失/无效、世界出生点仍在太阳内等）重生到了
        // 太阳内部，立即打断"重生→死亡"循环：重设个人重生点并传送到圆环安全区
        if (isInsideSun(player.blockPosition())) {
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
