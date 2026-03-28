// src/main/java/org/XYccWA/space_simulation/capability/AttachCapabilitiesEvent.java
package org.XYccWA.space_simulation.capability;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.XYccWA.space_simulation.SpaceSimulationMod;

@Mod.EventBusSubscriber(modid = SpaceSimulationMod.MOD_ID)
public class AttachCapabilitiesEventHandler {
    @SubscribeEvent
    public static void onAttachCapabilitiesToPlayer(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(CapabilityHandler.ID, new FuelRemainingProvider());
        }
    }
}
