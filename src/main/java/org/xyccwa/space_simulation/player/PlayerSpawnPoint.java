package org.xyccwa.space_simulation.player;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xyccwa.space_simulation.SpaceSimulation;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;

import java.util.Random;

@EventBusSubscriber(modid = SpaceSimulation.MOD_ID)
public class PlayerSpawnPoint {

    public static final Logger LOGGER = LogManager.getLogger(SpaceSimulation.MOD_ID);

    private static final int INNER_RADIUS = 5000000;      // 圆环内半径
    private static final int OUTER_RADIUS = 10000000;     // 圆环外半径
    private static final int MAX_Y = 10;              // 最大高度
    private static final int MIN_Y = -10;             // 最小高度

    private static final Random random = new Random();

    // 存储新存档的统一出生点
    private static BlockPos unifiedSpawnPoint = null;
    private static boolean isSpawnPointSet = false;

    // 防重入标志
    private static boolean isTeleporting = false;

    // 记录已经处理过的玩家，避免重复传送
    private static final java.util.Map<java.util.UUID, Boolean> processedPlayers = new java.util.HashMap<>();

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        // 只在服务器端主世界加载时处理
        if (!event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel &&
                ((ServerLevel) event.getLevel()).dimension() == ServerLevel.OVERWORLD) {

            ServerLevel level = (ServerLevel) event.getLevel();

            // 检查是否使用统一出生点
            if (SpaceSimulationConfig.useUnifiedSpawn.get()) {
                // 只在出生点未设置时才计算新的
                if (!isSpawnPointSet) {
                    // 计算随机Y坐标
                    int randomY = MIN_Y + random.nextInt(MAX_Y - MIN_Y + 1);

                    // 在圆环区域内随机生成出生点
                    double radius = INNER_RADIUS + random.nextDouble() * (OUTER_RADIUS - INNER_RADIUS);
                    double angle = random.nextDouble() * 2 * Math.PI;

                    // 转换为笛卡尔坐标
                    double x = radius * Math.cos(angle);
                    double z = radius * Math.sin(angle);

                    // 创建出生点位置
                    unifiedSpawnPoint = new BlockPos((int) x, randomY, (int) z);
                    isSpawnPointSet = true;

                    // 设置世界的出生点
                    level.setDefaultSpawnPos(unifiedSpawnPoint, 0f);

                    LOGGER.info("Set unified spawn position for new world at ({}, {}, {})",
                            (int) x, randomY, (int) z);
                } else {
                    LOGGER.info("Unified spawn position already set at ({}, {}, {})",
                            unifiedSpawnPoint.getX(), unifiedSpawnPoint.getY(), unifiedSpawnPoint.getZ());
                }
            } else {
                // 如果不是新存档，读取已设置的出生点
                unifiedSpawnPoint = level.getSharedSpawnPos();
                isSpawnPointSet = true;
                LOGGER.info("Loaded existing spawn position at ({}, {}, {})",
                        unifiedSpawnPoint.getX(), unifiedSpawnPoint.getY(), unifiedSpawnPoint.getZ());
            }

        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // 防重入检查
        if (isTeleporting) {
            LOGGER.debug("Skipping teleport - already in progress");
            return;
        }

        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            java.util.UUID playerId = player.getUUID();

            // 检查是否已经处理过这个玩家
            if (processedPlayers.containsKey(playerId)) {
                LOGGER.debug("Player {} already processed, skipping", player.getName().getString());
                return;
            }

            // 检查是否使用统一出生点
            if (SpaceSimulationConfig.useUnifiedSpawn.get()) {
                // 使用统一出生点模式
                if (isSpawnPointSet && unifiedSpawnPoint != null) {
                    // 检查玩家是否是首次登录（没有设置过个人出生点）
                    if (player.getRespawnPosition() == null) {
                        // 检查是否已经在目标位置附近
                        if (player.blockPosition().distSqr(unifiedSpawnPoint) <= 1) {
                            LOGGER.info("Player {} already at spawn position", player.getName().getString());
                            processedPlayers.put(playerId, true);
                            return;
                        }

                        // 保存出生点数据以便后续重生使用
                        player.setRespawnPosition(player.level().dimension(),
                                unifiedSpawnPoint,
                                0f,
                                true,
                                false);

                        LOGGER.info("Will teleport player {} to unified spawn at ({}, {}, {})",
                                player.getName().getString(),
                                unifiedSpawnPoint.getX(),
                                unifiedSpawnPoint.getY(),
                                unifiedSpawnPoint.getZ());

                        // 标记玩家为已处理
                        processedPlayers.put(playerId, true);

                        // 延迟到下一 tick 传送，避免事件循环
                        isTeleporting = true;
                        player.getServer().execute(() -> {
                            try {
                                if (player.isAlive() && !player.level().isClientSide()) {
                                    player.teleportTo(unifiedSpawnPoint.getX(),
                                            unifiedSpawnPoint.getY(),
                                            unifiedSpawnPoint.getZ());
                                    LOGGER.info("Successfully teleported player {} to unified spawn",
                                            player.getName().getString());
                                }
                            } finally {
                                isTeleporting = false;
                            }
                        });
                    }
                }
            } else {
                // 使用独立随机出生点模式
                if (player.getRespawnPosition() == null) {
                    // 计算随机Y坐标
                    int randomY = MIN_Y + random.nextInt(MAX_Y - MIN_Y + 1);

                    // 在圆环区域内随机生成出生点
                    double radius = INNER_RADIUS + random.nextDouble() * (OUTER_RADIUS - INNER_RADIUS);
                    double angle = random.nextDouble() * 2 * Math.PI;

                    // 转换为笛卡尔坐标
                    double x = radius * Math.cos(angle);
                    double z = radius * Math.sin(angle);

                    // 创建出生点位置
                    BlockPos spawnPos = new BlockPos((int) x, randomY, (int) z);

                    // 检查是否已经在目标位置附近
                    if (player.blockPosition().distSqr(spawnPos) <= 1) {
                        LOGGER.info("Player {} already at spawn position", player.getName().getString());
                        processedPlayers.put(playerId, true);
                        return;
                    }

                    // 保存出生点数据以便后续重生使用
                    player.setRespawnPosition(player.level().dimension(),
                            spawnPos,
                            0f,
                            true,
                            false);

                    LOGGER.info("Will teleport player {} to spawn at ({}, {}, {})",
                            player.getName().getString(), (int) x, randomY, (int) z);

                    // 标记玩家为已处理
                    processedPlayers.put(playerId, true);

                    // 延迟到下一 tick 传送
                    isTeleporting = true;
                    player.getServer().execute(() -> {
                        try {
                            if (player.isAlive() && !player.level().isClientSide()) {
                                player.teleportTo(x, randomY, z);
                                LOGGER.info("Successfully teleported player {} to spawn",
                                        player.getName().getString());
                            }
                        } finally {
                            isTeleporting = false;
                        }
                    });
                }
            }
        }
    }
}