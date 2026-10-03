# SpaceSimulation

**Turn the Overworld into space.** A NeoForge 1.21.1 mod that replaces the vanilla overworld with a zero-gravity
star system: a sun at the world centre, procedural asteroid belts millions of blocks out, and quaternion 6DOF
flight physics on foot.

`Minecraft 1.21.1` · `NeoForge 21.1.234` · `v0.3.2-beta` · `GPL-3.0`

## What it does

The Overworld is redefined as a space dimension: no skylight, black void sky, no gravity, no drag, no pressure
(dimension type + Sable dimension physics datapack). Everything — players, mobs, items, arrows — floats freely.

**The Sun at the world centre.** A shader-rendered star sphere sits at (0,0,0) with a seed-derived radius of
50,000–100,000 blocks (rendered and server-authoritative from the same formula, synced to clients). Flying into
its outer 10% shell inflicts escalating heat damage; touching the surface kills. Players who log in or respawn
inside the sun are dropped straight into an initial orbit instead of the void.

**6DOF quaternion flight.** Movement is driven by quaternions instead of Euler angles, so pitch, yaw and roll are
fully free — no ±90° clamp, no gimbal lock. `Q`/`E` roll left/right, view smoothing keeps the crosshair aligned
with the physics direction, and top speed is 10 blocks/tick (200 m/s). Rotated OBB hitboxes follow your body
rotation (`F3+B` to view).

**Acceleration damage.** Hard impacts (a sudden single-tick velocity drop) and sustained high-G overloads
(30 G instant / 10 G sustained windows) hurt, and vanilla fall damage is disabled in flight mode. Detection is
client-measured, server-authoritative, so singleplayer and multiplayer behave identically.

**Orbital mechanics for players and mobs.** A Newtonian inverse-square gravity field centred on the sun, integrated
numerically every tick (velocity Verlet) for the player and for every other entity. `mu` defaults to `6.25e6`
blocks³/tick², i.e. a circular speed of 2.5 blocks/tick (50 m/s) at the inner belt's 1,000,000-block radius,
1.12 blocks/tick at 5,000,000 blocks and 11.2 blocks/tick (224 m/s) at the sun's surface — tune it under
`Orbital Mechanics` in the config. Player motion is **server-authoritative**: the server integrates from the input
mask the client reports, ignores the client's position packets and streams the authoritative state back; the client
predicts locally with the same integrator and reconciles against the server by input sequence number (rewind and
replay only when the prediction drifts past `orbitalCorrectionBlocks`). Two vanilla behaviours had to be neutralised
for this to work at all: `ServerGamePacketListenerImpl.tick` rolls the server-side player back to its pre-tick
position right after `player.doTick` (the authoritative position is re-asserted at the end of the connection tick),
and `LivingEntity.aiStep` zeroes velocity components below 0.003 blocks/tick (both sides therefore keep their own
authoritative velocity instead of reading `deltaMovement`). While offline the player is not ticked: the osculating
Kepler elements are stored with the logout tick and propagated analytically (elliptic *and* hyperbolic) on the next
login, so the orbit keeps running without them. The first time a player enters a world they are **assigned a random
initial orbit** inside the configured band (`orbitalSpawnRadiusMin`–`orbitalSpawnRadiusMax`, default 1.0M–1.5M
blocks, i.e. the inner asteroid belt) with a bounded inclination so the whole orbit stays inside the same thin disc
as the belts (`orbitalSpawnMaxAbsY`, default 8000 blocks) and a small eccentricity (`orbitalSpawnMaxEccentricity`,
default 0.15); death respawns get a fresh orbit too (`orbitalSpawnOnRespawn`). The old 5–10M block "spawn ring" is
gone — the world spawn point itself is corrected into the orbital band on load.
`/orbit info` reports the current state and
`/orbit circular <radius> [inclinationDeg]` drops you into a circular orbit (inclination measured from the
equatorial XZ plane: 0 = equatorial, 90 = polar); `F3` shows radius, speed, orbital elements and the
prediction-correction counter.

**Procedural asteroid belts.** A datapack-driven, fully procedural asteroid system: zero storage, zero files —
except for the asteroids you actually modify. Each asteroid has an index identity and a deterministic Kepler orbit
derived from a seed (`/asteroid info <index>`), grouped into belts defined by `asteroid_belt` / `asteroid_type` JSON
with weighted types and structure variants. A per-tick proximity service keeps nearby asteroids loaded (index
preload + strong-load radius) and materialises the closest ones inside a 1,000-block window (up to 24 at a time) as
Sable sub-levels you can see, land on and mine. **Modification persistence**: once you mine, place or blast a block
on an asteroid it is flagged as changed, stored through Sable's native sub-level storage when it unloads, and
restored on your next approach — you get your modified blocks back, while untouched asteroids are still rebuilt
instantly at no save cost. **Block interaction lag compensation**: the server eye position inherently trails the
client by 1–3 ticks, which used to break mining and placing while flying past an asteroid; the server now judges
the geometry from the client-reported position, clamps it against the authoritative position, and takes the best of
three analytic poses (`subLevelInteractionLagCompensation`, on by default). `/asteroid` exposes meta, belts, info,
near, loader, entity, persist and reload.

**Materials from space.** 26 ore blocks, 26 ore sands, 26 powders and 19 ingots (13 metals + 6 alloys such as
Incoloy 890, tungsten–rhenium and GH4061). This release adds the six gap-filling ores — cobaltite (cobalt),
spodumene (lithium), zircon (zirconium), monazite (rare earths), uraninite (uranium) and thorite (thorium) — with
complete textures and models, and switches ore blocks to drop themselves (sands are meant to come from
crushing/beneficiation later). Ore blocks are registered and datagen-complete; world generation, the
crushing/beneficiation steps and the smelting chain are still work in progress.

**Lighting and rendering.** Optional full-brightness mode skips the light engine entirely for a large tick/frame
saving, and a built-in Iris shader pack renders the sun as a radial light source with real shadows cast from it
(deployed automatically on first launch; Iris is a soft dependency).

**Sable / Create Aeronautics integration.** The Sable plotyard (sub-level block storage) is relocated inside the
sun so physics coordinates stay in a low-magnitude range (f32 ULP ~4 mm instead of ~2 blocks), Rapier scene
coordinates are float-rebased with a per-scene origin so sub-level physics stays precise out to millions of blocks,
and modified asteroids persist through Sable's own holding storage instead of a bespoke format.

## Commands

| Command | Purpose |
|---|---|
| `/asteroid meta` | System overview: belts, types, totals, seed, μ, datapack state |
| `/asteroid belts` | Belt list: radii, inclination, counts, type weights |
| `/asteroid info <index>` | Orbit elements and world position of one asteroid |
| `/asteroid near <radius>` | All asteroids within a radius of the player |
| `/asteroid loader` | Proximity loader status |
| `/asteroid persist [index]` | Persistence ledger: changed asteroids and their stored-copy pointers |
| `/asteroid persist <index> unload` | Force-unload and store that asteroid (verify recovery) |
| `/asteroid reload` | Reload the asteroid datapack |
| `/orbit info` | Your orbital state: mu, radius, speed, elements, period, apsides |
| `/orbit circular <radius> [inclinationDeg]` | Enter a circular orbit (0° = equatorial, 90° = polar) |
| `/orbit mu` | Current gravitational parameter |

## Requirements

- NeoForge 21.1.234 for Minecraft 1.21.1
- Sable / Create Aeronautics (optional: sub-level physics integration, persistence)
- Iris + Sodium (optional: built-in shader pack)

## Status

`0.3.2-beta` — physics, orbital mechanics, sun, lighting, the asteroid system and asteroid modification
persistence are playable. The survival material chain (world-generated ores, crushing/beneficiation and smelting
recipes) is not finished yet; materials are currently creative-only. The full tech-tree design is written down in
`docs/tech-tree-design.md` but not implemented.
