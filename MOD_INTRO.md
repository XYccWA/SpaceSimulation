# SpaceSimulation

**Turn the Overworld into space.** A NeoForge 1.21.1 mod that replaces the vanilla overworld with a zero-gravity
star system: a sun at the world centre, procedural asteroid belts millions of blocks out, and quaternion 6DOF
flight physics on foot.

`Minecraft 1.21.1` · `NeoForge 21.1.234` · `v0.3.0-beta` · `GPL-3.0`

## What it does

The Overworld is redefined as a space dimension: no skylight, black void sky, no gravity, no drag, no pressure
(dimension type + Sable dimension physics datapack). Everything — players, mobs, items, arrows — floats freely.

**The Sun at the world centre.** A shader-rendered star sphere sits at (0,0,0) with a seed-derived radius of
50,000–100,000 blocks (rendered and server-authoritative from the same formula, synced to clients). Flying into
its outer 10% shell inflicts escalating heat damage; touching the surface kills. Players who log in or respawn
inside the sun are moved to a safe ring 5–10 million blocks out.

**6DOF quaternion flight.** Movement is driven by quaternions instead of Euler angles, so pitch, yaw and roll are
fully free — no ±90° clamp, no gimbal lock. `Q`/`E` roll left/right, view smoothing keeps the crosshair aligned
with the physics direction, and top speed is 10 blocks/tick (200 m/s). Rotated OBB hitboxes follow your body
rotation (`F3+B` to view).

**Acceleration damage.** Hard impacts (a sudden single-tick velocity drop) and sustained high-G overloads
(30 G instant / 10 G sustained windows) hurt, and vanilla fall damage is disabled in flight mode. Detection is
client-measured, server-authoritative, so singleplayer and multiplayer behave identically.

**Procedural asteroid belts.** A datapack-driven, fully procedural asteroid system: zero storage, zero files.
Each asteroid has an index identity and a deterministic Kepler orbit derived from a seed (`/asteroid info <index>`),
grouped into belts defined by `asteroid_belt` / `asteroid_type` JSON with weighted types and structure variants.
A per-tick proximity service keeps nearby asteroids loaded (index preload + strong-load radius), and `/asteroid`
exposes meta, belts, info, near, loader and reload.

**Materials from space.** 20 ore sands and 19 ingots (13 metals + 6 alloys such as Incoloy 890, tungsten–rhenium
and GH4061), extracted from asteroid ore blocks and smelted up the chain. Ore blocks are registered and
datagen-complete; world generation and the survival smelting chain are still work in progress.

**Lighting and rendering.** Optional full-brightness mode skips the light engine entirely for a large tick/frame
saving, and a built-in Iris shader pack renders the sun as a radial light source with real shadows cast from it
(deployed automatically on first launch; Iris is a soft dependency).

**Sable / Create Aeronautics integration.** The Sable plotyard (sub-level block storage) is relocated inside the
sun so physics coordinates stay in a low-magnitude range (f32 ULP ~4 mm instead of ~2 blocks), and Rapier scene
coordinates are float-rebased with a per-scene origin so sub-level physics stays precise out to millions of blocks.

## Commands

| Command | Purpose |
|---|---|
| `/asteroid meta` | System overview: belts, types, totals, seed, μ, datapack state |
| `/asteroid belts` | Belt list: radii, inclination, counts, type weights |
| `/asteroid info <index>` | Orbit elements and world position of one asteroid |
| `/asteroid near <radius>` | All asteroids within a radius of the player |
| `/asteroid loader` | Proximity loader status |
| `/asteroid reload` | Reload the asteroid datapack |

## Requirements

- NeoForge 21.1.234 for Minecraft 1.21.1
- Sable / Create Aeronautics (optional: sub-level physics integration)
- Iris + Sodium (optional: built-in shader pack)

## Status

`0.3.0-beta` — physics, sun, lighting and the asteroid system are playable. The survival material chain
(world-generated ores and smelting recipes) is not finished yet; materials are currently creative-only.
