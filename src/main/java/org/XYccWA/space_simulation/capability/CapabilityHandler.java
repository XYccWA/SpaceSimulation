// src/main/java/org/XYccWA/space_simulation/capability/CapabilityHandler.java
package org.XYccWA.space_simulation.capability;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.XYccWA.space_simulation.SpaceSimulationMod;
import org.XYccWA.space_simulation.network.FuelDataSyncPacket;

@Mod.EventBusSubscriber(modid = SpaceSimulationMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CapabilityHandler {
    public static final Capability<FuelRemainingCapability> FUEL_REMAINING =
            CapabilityManager.get(new CapabilityToken<>() {});

    public static final ResourceLocation ID = new ResourceLocation(SpaceSimulationMod.MOD_ID, "fuel_remaining");

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(FuelRemainingCapability.class);
    }

    public static void syncFuelData(Player player) {
        if (!player.level().isClientSide()) {
            player.getCapability(FUEL_REMAINING).ifPresent(fuel -> {
                SpaceSimulationMod.NETWORK.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) player),
                        new FuelDataSyncPacket(fuel.getFuelRemaining(), fuel.getMaxFuel()));
            });
        }
    }
}
