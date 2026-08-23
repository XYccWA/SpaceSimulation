package org.xyccwa.space_simulation.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class SpaceSimulationConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue useUnifiedSpawn;
    public static final ModConfigSpec.DoubleValue sustainedGThreshold;
    public static final ModConfigSpec.DoubleValue highGravityAccelerationThreshold;
    public static final ModConfigSpec.IntValue sutainedGDuration;
    public static final ModConfigSpec.DoubleValue solarKillRadius;
    public static final ModConfigSpec.DoubleValue plotyardMinSunRadius;
    public static final ModConfigSpec.BooleanValue rapierRebaseEnabled;
    public static final ModConfigSpec.DoubleValue rapierRebaseOriginX;
    public static final ModConfigSpec.DoubleValue rapierRebaseOriginY;
    public static final ModConfigSpec.DoubleValue rapierRebaseOriginZ;


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

        builder.push("Sun Settings");

            solarKillRadius = builder.comment("The kill radius of the sun (in blocks, measured from the world origin). " +
                            "Players entering this sphere take lethal damage, including creative and spectator modes. " +
                            "Set to 0 to disable.")
                    .defineInRange("solarKillRadius", 150000.0, 0.0, Double.MAX_VALUE);

        builder.pop();

        builder.push("Sable PlotYard");

            plotyardMinSunRadius = builder.comment(
                            "The design minimum sun radius (in blocks) used to place Sable's plotyard (sub-level block storage) " +
                            "inside the sun sphere (world origin). The whole plot grid is guaranteed to stay within this sphere, " +
                            "so worlds whose sun is at least this large never leak sub-level blocks past the sun boundary. " +
                            "If the actual solarKillRadius is smaller, the grid is shrunk (fewer plots) to fit.")
                    .defineInRange("plotyardMinSunRadius", 200000.0, 1.0, Double.MAX_VALUE);

        builder.pop();

        builder.push("Sable Rapier Fix");

            rapierRebaseEnabled = builder.comment(
                            "Scene-level floating-origin rebasing for Sable's Rapier physics: all world coordinates sent to the " +
                            "native engine are shifted by a per-scene origin, so the engine's f32 math stays precise at 5M-20M block " +
                            "coordinates (where f32 ULP = 0.5-2 blocks). Java-side poses/rendering/network stay in world-frame doubles. " +
                            "Set to false to disable entirely (original sable behavior).")
                    .define("rapierRebaseEnabled", true);

            rapierRebaseOriginX = builder.comment(
                            "Explicit physics scene origin X (world blocks, auto-aligned to chunks). 0 = auto: the origin is set from " +
                            "the first physics object's position. For best precision keep all sub-levels within ~500K blocks of the origin.")
                    .defineInRange("rapierRebaseOriginX", 0.0, -2_100_000_000.0, 2_100_000_000.0);

            rapierRebaseOriginY = builder.comment("Explicit physics scene origin Y (world blocks). 0 = auto.")
                    .defineInRange("rapierRebaseOriginY", 0.0, -2_100_000_000.0, 2_100_000_000.0);

            rapierRebaseOriginZ = builder.comment("Explicit physics scene origin Z (world blocks). 0 = auto.")
                    .defineInRange("rapierRebaseOriginZ", 0.0, -2_100_000_000.0, 2_100_000_000.0);

        builder.pop();

        SPEC = builder.build();
    }
}
