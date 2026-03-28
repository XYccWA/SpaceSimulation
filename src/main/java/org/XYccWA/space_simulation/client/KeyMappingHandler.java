package org.XYccWA.space_simulation.client;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.XYccWA.space_simulation.SpaceSimulationMod;


@Mod.EventBusSubscriber(modid = SpaceSimulationMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeyMappingHandler {
    public static final KeyMapping TOGGLE_MODE_KEY = new KeyMapping(
            "key.space_simulation.toggle_mode",
            GLFW.GLFW_KEY_R,
            "key.categories.space_simulation"
    );

    public static final KeyMapping GEAR_UP_KEY = new KeyMapping(
            "key.space_simulation.gear_up",
            GLFW.GLFW_KEY_LEFT_SHIFT,
            "key.categories.space_simulation"
    );

    public static final KeyMapping GEAR_DOWN_KEY = new KeyMapping(
            "key.space_simulation.gear_down",
            GLFW.GLFW_KEY_LEFT_CONTROL,
            "key.categories.space_simulation"
    );

    public static final KeyMapping SPACE_KEY = new KeyMapping(
            "key.space_simulation.space",
            GLFW.GLFW_KEY_SPACE,
            "key.categories.space_simulation"
    );

    public static final KeyMapping C_KEY = new KeyMapping(
            "key.space_simulation.c",
            GLFW.GLFW_KEY_C,
            "key.categories.space_simulation"
    );

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_MODE_KEY);
        event.register(GEAR_UP_KEY);
        event.register(GEAR_DOWN_KEY);
        event.register(SPACE_KEY);
        event.register(C_KEY);
    }
}

