package org.XYccWA.space_simulation.player;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.XYccWA.space_simulation.SpaceSimulationMod;

@Mod.EventBusSubscriber(modid = SpaceSimulationMod.MOD_ID)
public class PlayerSizeHandler {

    @SubscribeEvent
    public static void onEntitySize(EntityEvent.Size event) {
        // 判断是否为玩家实体
        if (event.getEntity() instanceof Player) {
            // 修改宽度和高度（单位：格）
            // 原版玩家：宽 0.6，高 1.8
            float newWidth = 1.0f;   // 你可以改成其他值
            float newHeight = 1.0f;  // 例如改成 2 格高
            EntityDimensions newSize = EntityDimensions.fixed(newWidth, newHeight);

            event.setNewSize(newSize, true);
            event.setNewEyeHeight(newHeight * 0.5f); // 调整眼睛高度（可选）
        }
    }
}
