package org.xyccwa.space_simulation;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.common.Mod;
import org.xyccwa.space_simulation.attachment.SpaceSimulationAttachments;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;
import org.xyccwa.space_simulation.damage.PlayerAccelerationDamage;
import org.xyccwa.space_simulation.modBlock.SpaceSimulationBlock;
import org.xyccwa.space_simulation.modItem.SpaceSimulationCreativeTab;
import org.xyccwa.space_simulation.modItem.SpaceSimulationItem;
import org.xyccwa.space_simulation.network.ModPayloads;
import org.xyccwa.space_simulation.worldgen.ModStructurePlacements;

@Mod(SpaceSimulation.MOD_ID)
public class SpaceSimulation {
    public static final String MOD_ID = "space_simulation";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SpaceSimulation(IEventBus modEventBus) {
        ModContainer container = ModLoadingContext.get().getActiveContainer();
        //配置
        container.registerConfig(ModConfig.Type.STARTUP, SpaceSimulationConfig.SPEC);
        //方块
        SpaceSimulationBlock.BLOCKS.register(modEventBus);
        //物品
        SpaceSimulationItem.ITEMS.register(modEventBus);
        //创造模式物品栏
        SpaceSimulationCreativeTab.CREATIVE_MODE_TABS.register(modEventBus);
        //创建伤害处理器
        PlayerAccelerationDamage damageHandler = new PlayerAccelerationDamage();
        //注册数据
        SpaceSimulationAttachments.register(modEventBus);
        //注册网络包
        modEventBus.addListener(ModPayloads::register);


        NeoForge.EVENT_BUS.register(damageHandler);
        ModStructurePlacements.register(modEventBus);
    }

}
