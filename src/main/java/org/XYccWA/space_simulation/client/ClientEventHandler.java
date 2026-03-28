// src/main/java/org/XYccWA/space_simulation/client/ClientEventHandler.java
package org.XYccWA.space_simulation.client;

import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.XYccWA.space_simulation.SpaceSimulationMod;
import org.XYccWA.space_simulation.gui.overlay.FuelOverlay;

@Mod.EventBusSubscriber(modid = SpaceSimulationMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientEventHandler {
    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("fuel_overlay", FuelOverlay.HUD_FUEL);
    }
}
