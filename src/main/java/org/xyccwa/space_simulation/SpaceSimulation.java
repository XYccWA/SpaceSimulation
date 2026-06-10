package org.xyccwa.space_simulation;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.common.Mod;
import org.xyccwa.space_simulation.config.SpaceSimulationConfig;
import org.xyccwa.space_simulation.damage.PlayerAccelerationDamage;
import org.xyccwa.space_simulation.modBlock.SpaceSimulationBlock;
import org.xyccwa.space_simulation.modItem.SpaceSimulationCreativeTab;
import org.xyccwa.space_simulation.modItem.SpaceSimulationItem;
import org.xyccwa.space_simulation.worldgen.ModStructurePlacements;

@Mod(SpaceSimulation.MOD_ID)
public class SpaceSimulation {
    public static final String MOD_ID = "space_simulation";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final ResourceKey<DamageType> HIGH_G_FORCE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "high_g_force"));

    public static final ResourceKey<DamageType> SUSTAINED_G_FORCE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(MOD_ID, "sustained_g_force"));


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
        // 创建伤害处理器
        PlayerAccelerationDamage damageHandler = new PlayerAccelerationDamage();


        NeoForge.EVENT_BUS.register(damageHandler);
        ModStructurePlacements.register(modEventBus);
    }
}
