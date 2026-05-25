package org.xyccwa.space_simulation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class SpaceSimulationConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue useUnifiedSpawn;
    public static final ModConfigSpec.BooleanValue enableLightShortCircuit;
    public static final ModConfigSpec.IntValue SHORT_CIRCUIT_LIGHT_LEVEL;


    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("Spawn Settings");

            useUnifiedSpawn = builder.comment("Whether to use a unified spawn point (all players spawn in the same location)")
                    .define("useUnifiedSpawn", true);

        builder.pop();

        builder.push("Light Settings");

            enableLightShortCircuit = builder.comment("Whether to disable lighting updates")
                    .define("enableLightShortCircuit", true);

            SHORT_CIRCUIT_LIGHT_LEVEL = builder.comment("What is the overwrite light value?")
                    .defineInRange("SHORT_CIRCUIT_LIGHT_LEVEL", 15,0,15);

        builder.pop();
        SPEC = builder.build();
    }
}
