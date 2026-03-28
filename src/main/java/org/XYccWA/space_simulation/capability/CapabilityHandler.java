// src/main/java/org/XYccWA/space_simulation/capability/CapabilityHandler.java
package org.XYccWA.space_simulation.capability;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.XYccWA.space_simulation.SpaceSimulationMod;

@Mod.EventBusSubscriber(modid = SpaceSimulationMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CapabilityHandler {
    public static final Capability<FuelRemainingCapability> FUEL_REMAINING =
            CapabilityManager.get(new CapabilityToken<>() {});

    public static final ResourceLocation ID = new ResourceLocation(SpaceSimulationMod.MOD_ID, "fuel_remaining");

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(FuelRemainingCapability.class);
    }
}
