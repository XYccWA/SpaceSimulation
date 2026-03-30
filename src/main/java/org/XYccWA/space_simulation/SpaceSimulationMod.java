package org.XYccWA.space_simulation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.XYccWA.space_simulation.block.SpaceSimulationModBlocks;
import org.XYccWA.space_simulation.capability.CapabilityHandler;
import org.XYccWA.space_simulation.command.FuelCommand;
import org.XYccWA.space_simulation.config.SpaceSimulationConfig;
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


    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MOD_ID, "network"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public SpaceSimulationMod(FMLJavaModLoadingContext modLoadingContext) {
        MinecraftForge.EVENT_BUS.register(this);
        // 注册事件总线
        IEventBus modEventBus = modLoadingContext.getModEventBus();
        // 注册配置文件
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SpaceSimulationConfig.SPEC);
        // 注册网络消息
        registerNetworkMessages();
        //流体
        ModFluids.register(modEventBus);
        ModFluidTypes.register(modEventBus);
        //物品
        SpaceSimulationModItems.ITEMS.register(modEventBus);
        //方块
        SpaceSimulationModBlocks.BLOCKS.register(modEventBus);
        //创造物品栏
        SpaceSimulationCreativeTab.CREATIVE_MODE_TABS.register(modEventBus);
        //指令
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

}

