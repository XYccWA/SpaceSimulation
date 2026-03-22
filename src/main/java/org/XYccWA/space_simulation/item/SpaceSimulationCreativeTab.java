package org.XYccWA.space_simulation.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.XYccWA.space_simulation.SpaceSimulationMod;

public class SpaceSimulationCreativeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SpaceSimulationMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> SPACE_SIMULATION_TAB = CREATIVE_MODE_TABS.register("space_simulation_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.space_simulation.creative_tab"))
            .icon(() -> new ItemStack(SpaceSimulationModItems.spacesuit_helmet.get()))
            .displayItems((parameters, output) -> {
                output.accept(SpaceSimulationModItems.spacesuit_helmet.get());
                output.accept(SpaceSimulationModItems.spacesuit_chestplate.get());
                output.accept(SpaceSimulationModItems.spacesuit_leggings.get());
                output.accept(SpaceSimulationModItems.spacesuit_boots.get());
            })
            .build());
}

