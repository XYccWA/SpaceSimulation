# SpaceSimulation

[简体中文](README.md) | **English**

**Turn the Overworld into space.** A **NeoForge 1.21.1** mod: quaternion 6DOF flight physics in a zero-gravity star system, a sun at the world centre, real Newtonian orbital mechanics, tens of millions of procedurally generated asteroids, and a materials chain from space ore to aerospace alloys.

`Minecraft 1.21.1` · `NeoForge 21.1.234` · `v0.3.1-beta` · `GPL-3.0`

## Features

### The Overworld as space

The Overworld is redefined as a space dimension: no skylight, no gravity, no drag, no pressure (dimension type + the Sable dimension physics datapack `dimension_physics/overworld.json`: `base_gravity=[0,0,0]`, `base_pressure=0`, `universal_drag=0`). Black void sky, no natural mob spawning, fluids that do not flow, and players, mobs, items and arrows all floating freely. The world spans Y −512 to 1024. The mod's own gravity does not come from the dimension physics but from the sun at the world origin (below).

### The sun at the world centre

- **Seed-derived radius.** The sun is a sphere centred on the world origin `(0,0,0)` whose radius is deterministically derived from the Overworld seed, between 50,000 and 100,000 blocks (`SunRadius`). The server judgement and the client rendering share the same formula, and the actual radius is sent to clients on login — so the sun surface you see *is* the sun surface that kills you.
- **Heat shell and instant death.** The shell `(R, 1.1R]` deals heat damage every 10 ticks, escalating with proximity (1 point at the outer edge → 20 points at the surface, growing with the square of closeness); at `d ≤ R` the entity is killed outright, creative and spectator mode included.
- **Spawns no longer hang in the void.** The old 5,000,000–10,000,000-block "safe ring" is gone. The world spawn point is corrected into the initial-orbit band, logging in or respawning inside the sun has a fallback, and a player entering the world for the first time is placed straight into an initial orbit by the orbital system (below).

### Orbital mechanics (players and mobs)

- **A real gravity field.** An inverse-square field centred on the sun at the world origin, integrated numerically every tick (velocity Verlet), driving the player and every other entity. `mu` defaults to `6.25e6` blocks³/tick² — a circular speed of 2.5 blocks/tick (50 m/s) at 1,000,000 blocks (the inner belt's inner edge), 1.12 blocks/tick at 5,000,000 blocks and 11.2 blocks/tick (224 m/s) at the sun's surface. Tune it under `Orbital Mechanics`; setting it to 0 disables gravity and restores the old gravity-free Newtonian flight.
- **Server-authoritative with client prediction.** The server integrates from the input mask the client reports, ignores the client's position packets and streams the authoritative state back; the client predicts locally with the same integrator and reconciles by input sequence number, rewinding and replaying only when the prediction drifts past `orbitalCorrectionBlocks` (1 block by default). Both sides run the same pure-math integrator and receive the same `mu` from the server, so multiplayer matches singleplayer.
- **No ticking while offline.** The osculating Kepler elements are stored with the logout tick and propagated analytically on the next login (elliptic *and* hyperbolic), so the orbit keeps running without the player.
- **Born into orbit.** The first time a player enters a world they are assigned a random orbit inside `orbitalSpawnRadiusMin`–`orbitalSpawnRadiusMax` (1.0M–1.5M blocks by default, i.e. the inner asteroid belt) with a bounded inclination and eccentricity so the whole orbit stays inside the same thin disc as the belts (|Y| ≤ 8000 blocks, e ≤ 0.15 by default); death respawns get a fresh orbit too.
- **Commands and HUD.** `/orbit info` reports mu, radius, speed, elements, period and apsides; `/orbit circular <radius> [inclinationDeg]` drops you into a circular orbit; the `F3` debug screen shows radius, speed, elements and the prediction-correction counter.
- Two vanilla behaviours had to be neutralised for server authority to actually hold: the connection tick rolls the server-side player back to its pre-tick position (the authoritative position is re-asserted at the end of the connection tick), and `LivingEntity.aiStep` zeroes velocity components below 0.003 blocks/tick (both sides therefore keep their own authoritative velocity instead of reading `deltaMovement`).

### Quaternion 6DOF flight

- Attitude is driven by **quaternions** instead of Euler angles, so pitch, yaw and roll are fully free — no ±90° clamp and no gimbal lock.
- `Q` rolls left, `E` rolls right (vanilla keys are rebound on first launch: drop `Q`→`DEL`, inventory `E`→`B`, and player customisation is respected).
- 24 m/s² of acceleration with a **top speed of 10 blocks/tick (200 m/s)**; exponential view smoothing keeps the crosshair aligned with the physics direction even at low framerates. Thrust (or braking) adds on top of the orbital gravity, so the correct way to fly is to accelerate along your orbit rather than fight the field.
- Collision boxes rotate with your body (the 1.8-block box hugs you while flying sideways), and a **true rotated OBB hitbox** is mounted through the [Hitbox API](https://www.curseforge.com/minecraft/mc-mods/hitbox-api) — press `F3+B` to view it.

### Acceleration damage

- **Impact shock**: a sudden single-tick velocity drop (crash or hard stop) damages you based on the deceleration.
- **Sustained high-G overload**: 30 G instant / 10 G sustained (defaults, configurable) with a debounce window.
- Detection is client-measured and server-authoritative, so singleplayer and multiplayer behave identically; vanilla fall damage is disabled in flight mode.
- **Players are immune to void damage**: falling out of the world is no longer a death sentence — your vertical speed is zeroed so you stop near the trigger line and can fly back; every other mob and entity keeps vanilla behaviour.

### Procedural asteroid belts

A **zero-storage, deterministic, datapack-driven** asteroid system:

- Every asteroid has an **index identity**, and its **Keplerian orbital elements** are derived from the seed and that index with `SplitMix64` — O(1) on demand. The same index yields the same orbit across sessions and machines, with no save-file cost whatsoever.
- Motion follows Kepler's laws (`T = 2π√(a³/μ)`, `E − e·sinE = M`). By default `mu` follows the player's gravitational parameter (`asteroidMuFollowPlayerOrbital`), so the belts live on the **same speed scale as your own orbit** — the circular speed at the inner belt's inner edge is the player's circular speed at that radius (2.5 blocks/tick, 50 m/s), so you can fly alongside an asteroid, slow down and land on it.
- **Belts** are defined by datapack JSON (`asteroid_belt` / `asteroid_type`): an inner belt at 1,000,000–1,400,000 blocks (carbonaceous/siliceous dominated) and an outer belt at 1,600,000–2,000,000 blocks (metal/rare-metal dominated), both between Y −8000 and 9000, each with its own density and **weighted types**. **15 types × 6 structure variants = 90 structure NBTs**, roughly 30 million asteroids in total (about 13.6M in the inner belt by mean spacing, a fixed 17M in the outer belt).
- **Proximity loading**: each tick anchors on online players, rebuilding the preload index frame-by-frame (50,000-block preload radius) while strong-loading within 2,000 blocks — without blocking the main thread.
- **Materialisation**: asteroids within the default 2,000 blocks are assembled as **Sable sub-levels** — real structures you can see, land on and mine (cap of 16, one round per 40 ticks, 200-tick grace before unloading, spinning at 3°/s). Without Sable installed the system degrades to pure point masses.

### Materials

A smelting chain of asteroid ore → ore sand → metal ingot → aerospace alloy ingot.

#### Ore sands (20)

- **Metallic**: chalcocite, kamacite, taenite, chromite, ilmenite, forsterite, wolframite, columbite, molybdenite, tantalite, rheniite
- **Siliceous**: olivine, pyroxene, plagioclase, quartz
- **Carbonaceous**: carbonaceous, phyllosilicate, carbonate, troilite, magnetite

#### Metal ingots (13)

Copper, iron, nickel, chromium, titanium, magnesium, tungsten, niobium, molybdenum, tantalum, rhenium, platinum, rhodium

#### Alloy ingots (6)

Iron–nickel, chromium–nickel–iron (Incoloy 890), tungsten–rhenium, nickel–rhenium, platinum–rhodium, GH4061

### Lighting and rendering

- **Full-brightness lighting** (on by default): every light query returns the maximum level and the vanilla light engine stops computing entirely (no BFS propagation, no per-block updates), removing a major server tick and client frame hotspot.
- **Built-in Iris shader pack**: deployed automatically to `shaderpacks/SpaceSimulation` on first launch. It renders the sun at the world centre as a **radial light source** with real shadows cast from it — across the shadow range the light direction varies by far less than a pixel, so locally it is exactly a directional light with direction `normalize(cameraPos)`. It is only auto-selected when no other shader pack is chosen, never overriding an existing choice, and does nothing without Iris.

### Sable / Create Aeronautics integration

- **Plotyard inside the sun**: Sable's sub-level block storage (plotyard) grid is placed inside the sun sphere so physics coordinates stay small (f32 precision improves from roughly 2 blocks to roughly 4 mm).
- **Rapier floating-origin rebasing**: world coordinates handed to the native physics engine are shifted by a per-scene origin, while Java-side poses, rendering and networking stay in world-frame doubles — sub-level physics stays accurate millions of blocks out (can be disabled entirely in the `Sable Rapier Fix` config). Re-uploads of the global chunks affected by a rebase are spread across physics frames (10 ms budget per frame), so a rebase no longer stalls the main thread.

## Commands

All commands require permission level 2.

| Command | Purpose |
|---|---|
| `/asteroid meta` | System overview: belts, types, totals, seed, gravitational parameter μ, datapack state |
| `/asteroid belts` | Belt list: radii, inclination, eccentricity, bins, counts, type weights |
| `/asteroid info <index>` | One asteroid by index: belt, type/variant/structure, orbital elements, current world position and speed |
| `/asteroid near <radius>` | All asteroids within a radius of the player (with timing) |
| `/asteroid loader` | Preload index and strong-load status, timing, enter/leave events |
| `/asteroid spawn <index>` | Materialise one asteroid as a Sable sub-level, echoing its staging area and logical pose |
| `/asteroid tp <index>` | Materialise and teleport next to that asteroid (80 blocks radially) |
| `/asteroid entity` | Materialisation status: count, cap, per-asteroid plot and bounding box |
| `/asteroid reload` | Reload the asteroid datapack and echo parse diagnostics |
| `/orbit info` | Your orbital state: mu, radius, speed, elements, period, apsides |
| `/orbit circular <radius> [inclinationDeg]` | Enter a circular orbit (0° = equatorial, 90° = polar) |
| `/orbit mu` | Current gravitational parameter |

## Configuration

The config file is `config/space_simulation-startup.toml`. Common options:

| Option | Default | Description |
|---|---|---|
| `fullBrightness` | `true` | Full-brightness lighting; set false to restore vanilla lighting |
| `orbitalMechanicsEnabled` | `true` | Real orbital mechanics for players and entities (false restores gravity-free flight) |
| `orbitalMu` | `6.25e6` | Solar gravitational parameter μ (blocks³/tick²); circular speed is `√(μ/r)`, 0 disables gravity |
| `orbitalCorrectionBlocks` | `1.0` | Prediction-vs-server threshold: beyond this the client rewinds and replays |
| `orbitalSpawnEnabled` / `orbitalSpawnOnRespawn` | `true` | Assign an initial orbit on first join / on death respawn |
| `orbitalSpawnRadiusMin` ~ `orbitalSpawnRadiusMax` | `1.0e6` ~ `1.5e6` | Initial orbit radius band (blocks) |
| `orbitalSpawnMaxAbsY` / `orbitalSpawnMaxEccentricity` | `8000` / `0.15` | Initial orbit maximum \|Y\| and eccentricity |
| `asteroidSeed` | fixed value | Asteroid universe seed; changing it deterministically regenerates every orbit |
| `asteroidMuFollowPlayerOrbital` | `true` | Asteroid orbits follow the player's μ (otherwise the independent `asteroidInnerOrbitPeriodTicks` is used) |
| `asteroidInnerOrbitPeriodTicks` | 62830000 | Only used when the above is `false`: period at the inner belt's inner radius |
| `asteroidEntityifyEnabled` | `true` | Materialise nearby asteroids as Sable sub-levels |
| `asteroidEntityifyRadius` / `MaxLoaded` / `LoadIntervalTicks` | 2000 / 16 / 40 | Materialisation radius, simultaneous cap, load throttling |
| `asteroidSpinDegPerSecond` | `3.0` | Asteroid self-rotation; 0 disables it |
| `rapierRebaseEnabled` | `true` | Rapier floating-origin rebasing (long-range precision fix) |
| `builtInShaderPack` / `autoEnableShaderPack` | `true` | Deploy / auto-enable the built-in Iris shader pack |
| `sustainedGThreshold` / `highGravityAccelerationThreshold` | 98.1 / 294.3 | Sustained and instant overload damage thresholds (m/s²) |
| `perfStatsEnabled` | `true` | One server performance line every 60 s (tick time, TPS, entities/chunks/asteroids, heap) |

## Datapacks

Belts and types are entirely datapack-defined and can be overridden in a world or modpack:

```
data/<namespace>/asteroid_belt/<name>.json      Belts: radius, altitude, density, eccentricity, type weights
data/<namespace>/asteroid_type/<name>.json      Types: display name, variant structure list
data/<namespace>/structure/<name>.nbt           Asteroid structures
```

Run `/asteroid reload` (or `/reload`) to apply changes immediately.

## Requirements

| Dependency | Type | Purpose |
|---|---|---|
| NeoForge 21.1.234 | Required | Mod framework |
| [Hitbox API](https://www.curseforge.com/minecraft/mc-mods/hitbox-api) 1.0.0+ | Required | Rotated OBB hitboxes |
| Sable 2.0.5+ (Create Aeronautics) | Optional | Asteroid materialisation as sub-levels, plotyard and long-range physics precision |
| Iris 1.8.0+ (+ Sodium) | Optional | Radial sunlight and shadows from the built-in shader pack |

## Building

JDK 21 is required.

```bash
./gradlew build                                      # produces build/libs/space_simulation-<version>.jar
./gradlew runClient                                  # launch the client in the dev environment
./gradlew runClient -PquickPlaySingleplayer=<world>   # load a singleplayer world directly
```

Pushing a `v*` tag triggers the GitHub Actions build and publishes a Release.

## Known issues

- **The survival material chain is unfinished**: ore world generation and smelting recipes are not wired up yet, so materials are currently creative-only (highest-priority TODO).
- **Ore sand items have no texture/model yet** (they show as black-and-purple blocks): all 20 `*_sand` items are registered and translated, but their textures and models are still missing; the 13 metal and 6 alloy ingots are complete.
- **Orbital tuning is still in progress**: μ, the initial-orbit band and the correction threshold are all configurable, and the speed scale and handling may still change.

## License

Licensed under **GPL-3.0**.
