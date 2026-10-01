package org.xyccwa.space_simulation.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.xyccwa.space_simulation.util.SunRadius;

public class SpaceSimulationConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue useUnifiedSpawn;
    public static final ModConfigSpec.DoubleValue sustainedGThreshold;
    public static final ModConfigSpec.DoubleValue highGravityAccelerationThreshold;
    public static final ModConfigSpec.IntValue sutainedGDuration;
    public static final ModConfigSpec.DoubleValue plotyardMinSunRadius;
    public static final ModConfigSpec.BooleanValue rapierRebaseEnabled;
    public static final ModConfigSpec.DoubleValue rapierRebaseOriginX;
    public static final ModConfigSpec.DoubleValue rapierRebaseOriginY;
    public static final ModConfigSpec.DoubleValue rapierRebaseOriginZ;

    public static final ModConfigSpec.LongValue asteroidSeed;
    public static final ModConfigSpec.LongValue asteroidInnerOrbitPeriodTicks;
    public static final ModConfigSpec.DoubleValue asteroidMaxAbsY;
    public static final ModConfigSpec.BooleanValue asteroidEntityifyEnabled;
    public static final ModConfigSpec.DoubleValue asteroidEntityifyRadius;
    public static final ModConfigSpec.IntValue asteroidEntityifyMaxLoaded;
    public static final ModConfigSpec.IntValue asteroidEntityifyLoadIntervalTicks;
    public static final ModConfigSpec.IntValue asteroidEntityifyLoadsPerRound;
    public static final ModConfigSpec.IntValue asteroidEntityifyUnloadGraceTicks;
    public static final ModConfigSpec.DoubleValue asteroidSpinDegPerSecond;

    public static final ModConfigSpec.BooleanValue fullBrightness;

    public static final ModConfigSpec.BooleanValue builtInShaderPack;
    public static final ModConfigSpec.BooleanValue autoEnableShaderPack;


    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("Lighting Settings");

            fullBrightness = builder.comment(
                            "Full-brightness lighting: every light query returns the maximum level 15 and the vanilla " +
                            "light engine stops computing entirely (no BFS propagation, no per-block lighting updates). " +
                            "Rendering, entity lighting, mob spawning and crop growth all behave as if everything were " +
                            "fully lit; the light engine's CPU cost (a major server tick and client frame hotspot) " +
                            "drops to zero. Set to false to restore vanilla lighting.")
                    .define("fullBrightness", true);

        builder.pop();

        builder.push("Shader Pack (Iris)");

            builtInShaderPack = builder.comment(
                            "Deploy the built-in Iris shader pack to <gameDir>/shaderpacks/SpaceSimulation on startup. " +
                            "The pack renders the sun at the world centre as a radial light source and casts real " +
                            "shadows from it (the sun's direction at any point is normalize(cameraPos), which is " +
                            "accurate locally because the shadow range is tiny compared to the distance to the sun). " +
                            "Requires the Iris mod; without Iris this option does nothing. " +
                            "Set to false to never write the pack to disk.")
                    .define("builtInShaderPack", true);

            autoEnableShaderPack = builder.comment(
                            "Automatically select the built-in shader pack in the Iris config on first launch. " +
                            "Only applies when no other shader pack is currently selected, so an existing choice " +
                            "is never overwritten. Requires builtInShaderPack = true.")
                    .define("autoEnableShaderPack", true);

        builder.pop();

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

        builder.push("Sable PlotYard");

            plotyardMinSunRadius = builder.comment(
                            "Upper bound (in blocks) for the sun radius used to size Sable's plotyard (sub-level block storage) grid. " +
                            "The grid is always fitted inside min(this value, SunRadius.MIN_RADIUS = " + (long) SunRadius.MIN_RADIUS + "), and is never allowed to grow " +
                            "past the smallest possible sun radius, so the whole plot grid is guaranteed to stay inside the sun sphere " +
                            "(world origin) in every world. Lower this value to shrink the grid (fewer plots); higher values are clamped.")
                    .defineInRange("plotyardMinSunRadius", SunRadius.MIN_RADIUS, 1.0, Double.MAX_VALUE);

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

        builder.push("Asteroid System");

            asteroidSeed = builder.comment(
                    "Seed of the procedural asteroid universe. Changing it deterministically re-generates " +
                            "every orbit (same seed + same index = same orbit, across sessions and machines). " +
                            "Belt geometry/density/types come from the datapack (asteroid_belt / asteroid_type).")
                    .defineInRange("asteroidSeed", 0x5EED20260825L, Long.MIN_VALUE, Long.MAX_VALUE);

            asteroidInnerOrbitPeriodTicks = builder.comment(
                    "Orbital period at the innermost belt's inner radius in game ticks (20 ticks = 1 second). " +
                            "The system-wide gravitational parameter mu is derived from this by Kepler's third law: " +
                            "mu = (2*PI/T)^2 * innerRadius^3, so orbits follow T = 2*PI*sqrt(a^3/mu). " +
                            "A belt may override its own inner_orbit_period_ticks in the datapack.")
                    .defineInRange("asteroidInnerOrbitPeriodTicks", 62_830_000L, 1L, Long.MAX_VALUE);

            asteroidMaxAbsY = builder.comment(
                    "Maximum |Y| (blocks) an asteroid orbit may reach — a hard constraint of materialisation. " +
                            "Sable hard-deletes a sub-level whose bounding box leaves its Y window " +
                            "(run/config/sable-common.toml: sub_level_remove_min..sub_level_remove_max, default " +
                            "-10000..100000) with 'extreme Y coordinate range, removing'. Asteroid orbits are centred " +
                            "on the world origin and stay symmetric in Y, so the usable half-range is " +
                            "min(-remove_min, remove_max) minus a margin for the structure itself. 0 = auto: read " +
                            "Sable's live window (10000 if Sable is absent). Datapack belts requesting more are " +
                            "clamped with a warning. Widen Sable's sub_level_remove_min to allow thicker belts.")
                    .defineInRange("asteroidMaxAbsY", 0.0, 0.0, 1.0e9);

            asteroidEntityifyEnabled = builder.comment(
                    "Materialise asteroids as Sable sub-levels (real blocks you can see, land on and mine) when a " +
                            "player comes close. Blocks are always written inside the Sable plot (whose Y is the world " +
                            "height midpoint), never at the asteroid's orbital coordinates; the orbit only drives the " +
                            "sub-level's logical pose.")
                    .define("asteroidEntityifyEnabled", true);

            asteroidEntityifyRadius = builder.comment(
                    "Radius (blocks) around a player inside which asteroids are materialised as Sable sub-levels. " +
                            "This is a second filter on top of the loader's strong-load radius (2000 blocks, which decides " +
                            "which asteroids are candidates at all): only asteroids within min(strong-load radius, this value) " +
                            "are materialised, nearest first.")
                    .defineInRange("asteroidEntityifyRadius", 1_500.0, 16.0, 20_000.0);

            asteroidEntityifyMaxLoaded = builder.comment(
                    "Hard cap on the number of simultaneously materialised asteroid sub-levels (each one holds a " +
                            "Sable plot with a full structure inside).")
                    .defineInRange("asteroidEntityifyMaxLoaded", 8, 1, 512);

            asteroidEntityifyLoadIntervalTicks = builder.comment(
                    "Throttle: ticks between two materialisation rounds. Materialising one asteroid costs tens to hundreds " +
                            "of milliseconds on the server thread, so rounds are spaced out.")
                    .defineInRange("asteroidEntityifyLoadIntervalTicks", 100, 1, 1200);

            asteroidEntityifyLoadsPerRound = builder.comment(
                    "How many asteroids may be materialised in a single round (see load interval).")
                    .defineInRange("asteroidEntityifyLoadsPerRound", 1, 1, 16);

            asteroidEntityifyUnloadGraceTicks = builder.comment(
                    "An active asteroid is dematerialised only after it has been outside the strong-load radius for this " +
                            "many ticks. Prevents thrashing while the preload index is being rebuilt.")
                    .defineInRange("asteroidEntityifyUnloadGraceTicks", 200, 0, 2400);

            asteroidSpinDegPerSecond = builder.comment(
                    "Asteroid self-rotation in degrees per second (20 ticks = 1 second). 0 disables the spin.")
                    .defineInRange("asteroidSpinDegPerSecond", 3.0, 0.0, 3600.0);

        builder.pop();

        SPEC = builder.build();
    }
}
