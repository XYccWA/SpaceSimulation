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
    public static final ModConfigSpec.BooleanValue asteroidMuFollowPlayerOrbital;
    public static final ModConfigSpec.DoubleValue asteroidMaxAbsY;
    public static final ModConfigSpec.BooleanValue asteroidEntityifyEnabled;
    public static final ModConfigSpec.DoubleValue asteroidEntityifyRadius;
    public static final ModConfigSpec.IntValue asteroidEntityifyMaxLoaded;
    public static final ModConfigSpec.IntValue asteroidEntityifyLoadIntervalTicks;
    public static final ModConfigSpec.IntValue asteroidEntityifyLoadsPerRound;
    public static final ModConfigSpec.IntValue asteroidEntityifyUnloadGraceTicks;
    public static final ModConfigSpec.DoubleValue asteroidSpinDegPerSecond;
    public static final ModConfigSpec.BooleanValue subLevelInteractionLagCompensation;
    public static final ModConfigSpec.IntValue subLevelInteractionLagTicks;

    public static final ModConfigSpec.BooleanValue fullBrightness;

    public static final ModConfigSpec.BooleanValue orbitalMechanicsEnabled;
    public static final ModConfigSpec.DoubleValue orbitalMu;
    public static final ModConfigSpec.DoubleValue orbitalCorrectionBlocks;
    public static final ModConfigSpec.BooleanValue orbitalSpawnEnabled;
    public static final ModConfigSpec.DoubleValue orbitalSpawnRadiusMin;
    public static final ModConfigSpec.DoubleValue orbitalSpawnRadiusMax;
    public static final ModConfigSpec.DoubleValue orbitalSpawnMaxAbsY;
    public static final ModConfigSpec.DoubleValue orbitalSpawnMaxEccentricity;
    public static final ModConfigSpec.BooleanValue orbitalSpawnOnRespawn;

    public static final ModConfigSpec.BooleanValue builtInShaderPack;
    public static final ModConfigSpec.BooleanValue autoEnableShaderPack;

    public static final ModConfigSpec.BooleanValue perfStatsEnabled;


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

        builder.push("Orbital Mechanics");

            orbitalMechanicsEnabled = builder.comment(
                            "Give players (and every other entity) real orbital mechanics: a Newtonian inverse-square " +
                            "gravity field centred on the sun at the world origin, integrated numerically each tick. " +
                            "Both client and server run the same integrator; the server is authoritative and corrects " +
                            "the client when its prediction drifts. Set to false to restore the old gravity-free " +
                            "Newtonian flight (client-authoritative, straight lines).")
                    .define("orbitalMechanicsEnabled", true);

            orbitalMu = builder.comment(
                            "Gravitational parameter mu = G*M of the sun, in blocks^3/tick^2 (1 block = 1 m, 1 tick = 1/20 s). " +
                            "Circular orbital speed at radius r is sqrt(mu/r) blocks/tick (x20 for m/s) and the period is " +
                            "2*PI*sqrt(r^3/mu) ticks. With the default 6.25e6: circular speed is 2.5 blocks/tick (50 m/s) " +
                            "at r = 1,000,000 blocks (the inner belt's inner edge, i.e. the low end of the initial-orbit " +
                            "band), 1.12 blocks/tick at 5,000,000 blocks, and 11.2 blocks/tick (224 m/s) at the sun's " +
                            "surface (50k blocks). " +
                            "This field is independent of the asteroid belts' prescribed Kepler orbits unless " +
                            "asteroidMuFollowPlayerOrbital makes them follow it; it drives dynamically integrated bodies " +
                            "(players and entities). 0 disables gravity.")
                    .defineInRange("orbitalMu", 6.25e6, 0.0, 1.0e15);

            orbitalCorrectionBlocks = builder.comment(
                            "Client-side prediction threshold (blocks): when the authoritative server state differs from " +
                            "the client's recorded prediction for that input sequence by more than this, the client rewinds " +
                            "and replays. Smaller = tighter server authority, more rubber-banding on bad connections.")
                    .defineInRange("orbitalCorrectionBlocks", 1.0, 0.05, 64.0);

            orbitalSpawnEnabled = builder.comment(
                            "First time a player enters the world, put them into a randomly generated circular-ish orbit " +
                            "inside the configured band instead of leaving them standing at the vanilla spawn point. " +
                            "The elements are stored in the player's NBT, so later logins resume the same orbit (propagated " +
                            "analytically while they were offline).")
                    .define("orbitalSpawnEnabled", true);

            orbitalSpawnRadiusMin = builder.comment(
                            "Inner bound (blocks from the sun) of the initial orbit. Default 1,000,000 = the inner edge of " +
                            "the inner asteroid belt, where a circular orbit runs at 2.5 blocks/tick (50 m/s).")
                    .defineInRange("orbitalSpawnRadiusMin", 1_000_000.0, 1_000.0, 1.0e9);

            orbitalSpawnRadiusMax = builder.comment(
                            "Outer bound (blocks from the sun) of the initial orbit. Must be >= orbitalSpawnRadiusMin.")
                    .defineInRange("orbitalSpawnRadiusMax", 1_500_000.0, 1_000.0, 1.0e9);

            orbitalSpawnMaxAbsY = builder.comment(
                            "Maximum |Y| (blocks) the initial orbit may reach. The inclination is derived from it per " +
                            "player (i_max = asin(maxAbsY / apoapsis)), so the start orbit stays inside the same thin disc " +
                            "as the asteroid belts (datapack altitude +/-8000..9000) instead of climbing hundreds of " +
                            "thousands of blocks above the world height where there is nothing to see.")
                    .defineInRange("orbitalSpawnMaxAbsY", 8_000.0, 0.0, 1.0e6);

            orbitalSpawnMaxEccentricity = builder.comment(
                            "Maximum eccentricity of the initial orbit (0 = perfectly circular, 0.15 = apsides differ by 15%).")
                    .defineInRange("orbitalSpawnMaxEccentricity", 0.15, 0.0, 0.9);

            orbitalSpawnOnRespawn = builder.comment(
                            "Also assign a fresh orbit when a player respawns after death, instead of dropping them back at " +
                            "the (shared) world spawn point in the void.")
                    .define("orbitalSpawnOnRespawn", true);

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
                            "A belt may override its own inner_orbit_period_ticks in the datapack. " +
                            "Only used when asteroidMuFollowPlayerOrbital = false.")
                    .defineInRange("asteroidInnerOrbitPeriodTicks", 62_830_000L, 1L, Long.MAX_VALUE);

            asteroidMuFollowPlayerOrbital = builder.comment(
                    "Use the player's gravity parameter (orbitalMu) for asteroid orbits, so asteroids share the same " +
                            "gravity field and speed scale as the player's own orbit — without this, asteroid orbits " +
                            "kept their own, far slower mu (derived from asteroidInnerOrbitPeriodTicks) and flying " +
                            "alongside one was impossible. " +
                            "When true, asteroidInnerOrbitPeriodTicks and any belt-level inner_orbit_period_ticks are " +
                            "ignored (a warning is logged for belt overrides). Set to false to restore the independent mu.")
                    .define("asteroidMuFollowPlayerOrbital", true);

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
                            "are materialised, nearest first. Keep this clearly below the strong-load radius: a 2000-block " +
                            "sphere in a dense belt holds well over asteroidEntityifyMaxLoaded asteroids, so the auto-window " +
                            "filled every slot and /asteroid tp could no longer materialise anything.")
                    .defineInRange("asteroidEntityifyRadius", 1_000.0, 16.0, 20_000.0);

            asteroidEntityifyMaxLoaded = builder.comment(
                    "Hard cap on the number of simultaneously materialised asteroid sub-levels (each one holds a " +
                            "Sable plot with a full structure inside).")
                    .defineInRange("asteroidEntityifyMaxLoaded", 24, 1, 512);

            asteroidEntityifyLoadIntervalTicks = builder.comment(
                    "Throttle: ticks between two materialisation rounds. Materialising one asteroid costs tens to hundreds " +
                            "of milliseconds on the server thread, so rounds are spaced out.")
                    .defineInRange("asteroidEntityifyLoadIntervalTicks", 40, 1, 1200);

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

            subLevelInteractionLagCompensation = builder.comment(
                    "Lag compensation for block interactions on Sable sub-levels (mining and placing). " +
                            "The server's authoritative player position always trails the client's predicted one by the " +
                            "prediction pipeline depth; at 2.4 blocks/tick that is several blocks, which the server-side " +
                            "interaction range check (Player#canInteractWithBlock, 4.5 + 1.0 blocks) counted as real " +
                            "distance and rejected. Without this the block visually breaks on the client and is then " +
                            "restored by the server's block update. " +
                            "When enabled, a failed check is retried from the player's own point of view: the client's last " +
                            "reported position (vanilla ServerboundMovePlayerPacket, accepted only while fresh and within " +
                            "the deviation limit below) is used as the eye position, together with the asteroid's " +
                            "analytically evaluated pose for the current and the previous two ticks (the client renders " +
                            "Sable's interpolated pose, which trails the server pose). " +
                            "It only ever grants an interaction, never denies one. Set to false to restore the raw check.")
                    .define("subLevelInteractionLagCompensation", true);

            subLevelInteractionLagTicks = builder.comment(
                    "Deviation limit for the client's reported position, expressed in ticks of travel at the flight " +
                            "speed cap (10 blocks/tick): a report is trusted only while it is no further than " +
                            "subLevelInteractionLagTicks * 10 blocks from the server's authoritative position, and no " +
                            "older than 20 ticks. Larger values tolerate faster flight and worse connections; " +
                            "0 disables the compensation entirely.")
                    .defineInRange("subLevelInteractionLagTicks", 4, 0, 40);

        builder.pop();

        builder.push("Diagnostics");

            perfStatsEnabled = builder.comment(
                            "Log one line of server performance stats every 1200 ticks (60 seconds): server tick time " +
                            "average/maximum, TPS, overworld entity count, loaded chunk count, active asteroid count and " +
                            "heap usage. Needed to quantify stutter that never reaches the vanilla \"Can't keep up\" " +
                            "threshold (2 s behind) and never trips the client frame profiler (100 ms), which leaves no " +
                            "evidence in the log at all. Set to false to silence it.")
                    .define("perfStatsEnabled", true);

        builder.pop();

        SPEC = builder.build();
    }
}
