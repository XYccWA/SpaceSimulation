package org.XYccWA.space_simulation.player;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.XYccWA.space_simulation.SpaceSimulationMod;
import org.XYccWA.space_simulation.capability.CapabilityHandler;
import org.XYccWA.space_simulation.config.SpaceSimulationConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;

@Mod.EventBusSubscriber(modid = SpaceSimulationMod.MOD_ID)
public class PlayerSpawnPoint {

    public static final Logger LOGGER = LogManager.getLogger(SpaceSimulationMod.MOD_ID);

    private static final int INNER_RADIUS = 8000000;  // 圆环内半径
    private static final int OUTER_RADIUS = 10000000;  // 圆环外半径
    private static final int MAX_Y = 100;              // 最大高度
    private static final int MIN_Y = -100;             // 最小高度
    private static boolean firstPlayerLogged = false;  // 用于记录第一个玩家是否登录
    private static final Random random = new Random();

    // 存储新存档的统一出生点
    private static BlockPos unifiedSpawnPoint = null;
    private static boolean isSpawnPointSet = false;

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        // 只在服务器端主世界加载时处理
        if (!event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel &&
                ((ServerLevel) event.getLevel()).dimension() == ServerLevel.OVERWORLD) {

            ServerLevel level = (ServerLevel) event.getLevel();

            // 检查是否使用统一出生点
            if (SpaceSimulationConfig.useUnifiedSpawn.get()) {
                // 计算随机Y坐标
                int randomY = MIN_Y + random.nextInt(MAX_Y - MIN_Y + 1);

                // 在圆环区域内随机生成出生点
                double radius = INNER_RADIUS + random.nextDouble() * (OUTER_RADIUS - INNER_RADIUS);
                double angle = random.nextDouble() * 2 * Math.PI;

                // 转换为笛卡尔坐标
                double x = radius * Math.cos(angle);
                double z = radius * Math.sin(angle);

                // 创建出生点位置
                unifiedSpawnPoint = new BlockPos((int)x, randomY, (int)z);
                isSpawnPointSet = true;

                // 设置世界的出生点
                level.setDefaultSpawnPos(unifiedSpawnPoint, 0f);

                LOGGER.info("Set unified spawn position for new world at ({}, {}, {})",
                        (int)x, randomY, (int)z);
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
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            // 同步燃料数据到客户端
            CapabilityHandler.syncFuelData(player);

            // 检查是否使用统一出生点
            if (SpaceSimulationConfig.useUnifiedSpawn.get()) {
                // 使用统一出生点模式
                if (isSpawnPointSet && unifiedSpawnPoint != null) {
                    // 检查玩家是否是首次登录（没有设置过个人出生点）
                    if (player.getRespawnPosition() == null) {
                        // 检查是否为第一个登录的玩家
                        if (!firstPlayerLogged) {
                            // 标记已有玩家登录
                            firstPlayerLogged = true;

                            // 保存出生点数据以便后续重生使用
                            player.setRespawnPosition(player.level().dimension(),
                                    unifiedSpawnPoint,
                                    0f,
                                    true,
                                    false);

                            // 立即传送玩家到出生点
                            player.teleportTo(unifiedSpawnPoint.getX(), unifiedSpawnPoint.getY(), unifiedSpawnPoint.getZ());

                            player.getServer().getCommands().performPrefixedCommand(
                                    player.createCommandSourceStack(),
                                    "place structure minecraft:ancient_city ~ ~ ~"
                            );

                            LOGGER.info("First player {} logged in. Set spawn position to unified spawn at ({}, {}, {})",
                                    player.getName().getString(),
                                    unifiedSpawnPoint.getX(),
                                    unifiedSpawnPoint.getY(),
                                    unifiedSpawnPoint.getZ());
                        } else {
                            // 非第一个玩家登录的处理逻辑
                            player.setRespawnPosition(player.level().dimension(),
                                    unifiedSpawnPoint,
                                    0f,
                                    true,
                                    false);
                            player.teleportTo(unifiedSpawnPoint.getX(), unifiedSpawnPoint.getY(), unifiedSpawnPoint.getZ());
                            LOGGER.info("Player {} logged in. Set spawn position to unified spawn at ({}, {}, {})",
                                    player.getName().getString(),
                                    unifiedSpawnPoint.getX(),
                                    unifiedSpawnPoint.getY(),
                                    unifiedSpawnPoint.getZ());
                        }
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
                    BlockPos spawnPos = new BlockPos((int)x, randomY, (int)z);

                    // 保存出生点数据以便后续重生使用
                    player.setRespawnPosition(player.level().dimension(),
                            spawnPos,
                            0f,
                            true,
                            false);

                    // 立即传送玩家到出生点
                    player.teleportTo(x, randomY, z);

                    player.getServer().getCommands().performPrefixedCommand(
                            player.createCommandSourceStack(),
                            "place structure minecraft:ancient_city ~ ~ ~"
                    );

                    LOGGER.info("Set spawn position for player {} at ({}, {}, {})",
                            player.getName().getString(), (int)x, randomY, (int)z);
                }
            }
        }
    }
}
