package org.XYccWA.space_simulation;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Random;

@Mod(SpaceSimulationMod.MOD_ID)
public class SpaceSimulationMod {
    public static final String MOD_ID = "space_simulation";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static final int SPAWN_DISTANCE = 20000000;
    private static final int MAX_Y = 300;
    private static final int MIN_Y = -300;
    private static final Random random = new Random();

    public SpaceSimulationMod() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            // 检查玩家是否是首次登录（没有设置过出生点）
            if (player.getRespawnPosition() == null) {
                // 计算随机Y坐标
                int randomY = MIN_Y + random.nextInt(MAX_Y - MIN_Y + 1);

                // 创建出生点位置
                BlockPos spawnPos = new BlockPos(SPAWN_DISTANCE, randomY, 0);

                // 保存出生点数据以便后续重生使用
                player.setRespawnPosition(player.level().dimension(),
                        spawnPos,
                        0f,
                        true,
                        false);

                // 立即传送玩家到出生点
                player.teleportTo(SPAWN_DISTANCE, randomY, 0);

                LOGGER.info("Set spawn position for player {} at ({}, {}, {})",
                        player.getName().getString(), SPAWN_DISTANCE, randomY, 0);
            }
        }
    }
}
