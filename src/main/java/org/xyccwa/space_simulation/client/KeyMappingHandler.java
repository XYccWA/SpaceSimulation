package org.xyccwa.space_simulation.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import org.xyccwa.space_simulation.SpaceSimulation;


@EventBusSubscriber(modid = SpaceSimulation.MOD_ID, value = Dist.CLIENT)
public class KeyMappingHandler {

    /** Q：左滚转 */
    public static final KeyMapping ROLL_LEFT = new KeyMapping(
            "key.space_simulation.roll_left",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Q,
            "key.categories.movement");

    /** E：右滚转 */
    public static final KeyMapping ROLL_RIGHT = new KeyMapping(
            "key.space_simulation.roll_right",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_E,
            "key.categories.movement");

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ROLL_LEFT);
        event.register(ROLL_RIGHT);
    }
}
