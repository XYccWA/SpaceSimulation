package org.xyccwa.space_simulation.client;

import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import org.xyccwa.space_simulation.SpaceSimulation;


@EventBusSubscriber(modid = SpaceSimulation.MOD_ID)
public class KeyMappingHandler {
    public static final KeyMapping TOGGLE_MODE_KEY = new KeyMapping("key.space_simulation.toggle_mode", GLFW.GLFW_KEY_R, "key.categories.space_simulation");
    public static final KeyMapping GEAR_UP_KEY = new KeyMapping("key.space_simulation.gear_up", GLFW.GLFW_KEY_LEFT_SHIFT, "key.categories.space_simulation");
    public static final KeyMapping GEAR_DOWN_KEY = new KeyMapping("key.space_simulation.gear_down", GLFW.GLFW_KEY_LEFT_CONTROL, "key.categories.space_simulation");
    public static final KeyMapping UP_KEY = new KeyMapping("key.space_simulation.space", GLFW.GLFW_KEY_C, "key.categories.space_simulation");
    public static final KeyMapping DOWN_KEY = new KeyMapping("key.space_simulation.c", GLFW.GLFW_KEY_SPACE, "key.categories.space_simulation");
    public static final KeyMapping FORWARD_KEY = new KeyMapping("key.space_simulation.forward", GLFW.GLFW_KEY_W, "key.categories.space_simulation");
    public static final KeyMapping BACKWARD_KEY = new KeyMapping("key.space_simulation.backward", GLFW.GLFW_KEY_S, "key.categories.space_simulation");
    public static final KeyMapping LEFT_KEY = new KeyMapping("key.space_simulation.left", GLFW.GLFW_KEY_LEFT, "key.categories.space_simulation");
    public static final KeyMapping RIGHT_KEY = new KeyMapping("key.space_simulation.right", GLFW.GLFW_KEY_RIGHT, "key.categories.space_simulation");


    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_MODE_KEY);
        event.register(GEAR_UP_KEY);
        event.register(GEAR_DOWN_KEY);
        event.register(UP_KEY);
        event.register(DOWN_KEY);
        event.register(FORWARD_KEY);
        event.register(BACKWARD_KEY);
        event.register(LEFT_KEY);
        event.register(RIGHT_KEY);
    }
}

