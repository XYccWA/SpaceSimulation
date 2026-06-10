// ClientTickHandler.java
package org.xyccwa.space_simulation.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import org.xyccwa.space_simulation.player.PlayerAcceleration;
import org.xyccwa.space_simulation.SpaceSimulation;

@EventBusSubscriber(value = Dist.CLIENT,modid = SpaceSimulation.MOD_ID)
public class ClientTickHandler {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            // 每个 tick 更新玩家的运动数据
            PlayerAcceleration.updatePlayer(mc.player);
        }
    }
}