package org.xyccwa.space_simulation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class SpaceSimulationConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue useUnifiedSpawn;
    public static final ModConfigSpec.DoubleValue sustainedGThreshold;
    public static final ModConfigSpec.DoubleValue highGravityAccelerationThreshold;
    public static final ModConfigSpec.IntValue sutainedGDuration;


    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("Spawn Settings");

            useUnifiedSpawn = builder.comment("Whether to use a unified spawn point (all players spawn in the same location)")
                    .define("useUnifiedSpawn", true);

        builder.pop();

        builder.push("Damage Settings");

            sustainedGThreshold = builder.comment("The threshold for sustained G force damage")
                    .defineInRange("sustainedGThreshold", 98.1, 0.0, 300.0);

            sutainedGDuration = builder.comment("How many ticks G force must stay above sustainedGThreshold before damage starts (20 ticks = 1 second)")
                    .defineInRange("sustainedGForceDamage", 20, 0, 200);

            highGravityAccelerationThreshold = builder.comment("The threshold for high gravity acceleration damage")
                    .defineInRange("highGravityAccelerationThreshold", 294.3, 0.0, 1000.0);

        builder.pop();

        SPEC = builder.build();
    }
}
