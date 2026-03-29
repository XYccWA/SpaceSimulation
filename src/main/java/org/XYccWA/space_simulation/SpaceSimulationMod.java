package org.XYccWA.space_simulation;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.XYccWA.space_simulation.block.SpaceSimulationModBlocks;
import org.XYccWA.space_simulation.capability.CapabilityHandler;
import org.XYccWA.space_simulation.command.FuelCommand;
import org.XYccWA.space_simulation.fluid.ModFluidTypes;
import org.XYccWA.space_simulation.fluid.ModFluids;
import org.XYccWA.space_simulation.item.SpaceSimulationCreativeTab;
import org.XYccWA.space_simulation.item.SpaceSimulationModItems;
import org.XYccWA.space_simulation.network.FuelDataSyncPacket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;

@Mod(SpaceSimulationMod.MOD_ID)
public class SpaceSimulationMod {
    public static final String MOD_ID = "space_simulation";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static final int SPAWN_DISTANCE = 15000000;
    private static final int MAX_Y = 300;
    private static final int MIN_Y = -300;
    private static final Random random = new Random();

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MOD_ID, "network"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public SpaceSimulationMod(FMLJavaModLoadingContext modLoadingContext) {
        MinecraftForge.EVENT_BUS.register(this);

        IEventBus modEventBus = modLoadingContext.getModEventBus();

        // 注册网络消息
        registerNetworkMessages();

        ModFluids.register(modEventBus);
        ModFluidTypes.register(modEventBus);
        SpaceSimulationModItems.ITEMS.register(modEventBus);
        SpaceSimulationModBlocks.BLOCKS.register(modEventBus);
        SpaceSimulationCreativeTab.CREATIVE_MODE_TABS.register(modEventBus);

        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> {
            FuelCommand.register(event.getDispatcher());
        });
    }

    private void registerNetworkMessages() {
        int id = 0;
        NETWORK.messageBuilder(FuelDataSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FuelDataSyncPacket::encode)
                .decoder(FuelDataSyncPacket::decode)
                .consumerMainThread(FuelDataSyncPacket::handle)
                .add();
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            // 同步燃料数据到客户端
            CapabilityHandler.syncFuelData(player);
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
