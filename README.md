# Vanguard

Vanguard is a client-side rendering optimization mod for Minecraft 26.3. It reduces the
amount of work spent on entities, block entities, and particles that are invisible,
barely visible, or far outside the center of the screen.

Vanguard is available for Fabric, Quilt, and NeoForge. It is client-only; installing it on
a server has no effect.

## Features

- **Predictive frustum culling.** The entity frustum is extended backwards along the view
  direction based on camera movement and rotation, so entities entering the screen are
  already inside the cull volume. The extension is capped and scaled by rotation speed and
  movement speed.
- **Screen-space foveated LOD.** Entities far from the center of the screen or beyond the
  near-distance threshold use a cached render state. Animation and render-state extraction
  are refreshed every few frames rather than every frame. Distant peripheral entities also
  skip shadow rendering.
- **Occlusion culling.** Entities and block entities are tested with a conservative,
  allocation-free voxel walk from the camera. A target is hidden only when every sample ray
  is stopped by a full opaque block. Glass, leaves, fences, fluids, and the target's own
  block do not count as occluders. Results are cached briefly and the number of tests per
  frame is budgeted.
- **Particle culling.** Particles beyond a minimum distance that fall outside the configured
  field-of-view cone are not spawned.
- **F3 debug line.** The overlay reports total candidates, rendered entities, LOD-throttled
  entities, culled entities (including the occluded subset), culled particles, and culled
  block entities for the current frame.

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | 26.3 |
| Java | 25 |
| Fabric Loader | 0.20.1 or newer |
| Quilt Loader | Any loader that supports Fabric mods |
| NeoForge | 26.3.0.103 or newer |

No Fabric API or QSL dependency is required.

## Installation

1. Install the loader for your platform.
2. Place the matching jar in the instance's `mods` directory:

   - `vanguard-fabric-1.0.0+26.3.jar`
   - `vanguard-quilt-1.0.0+26.3.jar`
   - `vanguard-neoforge-1.0.0+26.3.jar`

3. Start the client.

The Quilt build uses the Fabric metadata format and a `ClientModInitializer` entry point,
because Quilt Loader loads Fabric-format mods natively.

## Configuration

The first launch writes `config/vanguard.json`. Edit the file and restart the client to
apply changes. Invalid or non-finite values are clamped to the ranges below.

| Key | Default | Range | Effect |
| --- | --- | --- | --- |
| `enabled` | `true` | | Master switch for all optimizations. |
| `predictiveCulling` | `true` | | Enables the extended entity frustum. |
| `predictionStrength` | `1.0` | 0.0–4.0 | Multiplier for the prediction offset. |
| `maxPredictionOffset` | `6.0` | 0.0–16.0 | Maximum backward frustum offset in blocks. |
| `occlusionCulling` | `true` | | Enables entity occlusion tests. |
| `occlusionBudget` | `24` | 0–128 | Maximum entity occlusion tests per frame. |
| `occlusionCacheMillis` | `120` | 0–2000 | How long an occlusion result is reused. |
| `occlusionMinDistance` | `4.0` | 0.0–32.0 | Targets closer than this are never occlusion-culled. |
| `occlusionMaxDistance` | `96.0` | 16.0–256.0 | Targets farther than this are never occlusion-culled. |
| `blockEntityOcclusion` | `true` | | Enables block entity occlusion tests. |
| `blockEntityOcclusionBudget` | `32` | 0–128 | Maximum block entity occlusion tests per frame. |
| `foveatedLod` | `true` | | Enables LOD state caching and shadow skipping. |
| `lodNearDistance` | `24.0` | 4.0–128.0 | Distance where LOD begins. |
| `lodFarDistance` | `64.0` | `lodNearDistance`–256.0 | Distance where the lowest LOD begins. |
| `lodFoveaRatio` | `0.7` | 0.1–2.0 | Eccentricity, relative to half-FOV, where peripheral LOD begins. |
| `animationRefreshFrames` | `4` | 2–20 | Frame interval between full render-state extractions for LOD entities. |
| `particleCulling` | `true` | | Enables particle culling. |
| `particleMinDistance` | `12.0` | 0.0–64.0 | Particles closer than this are never culled. |
| `particleFovRatio` | `1.0` | 0.1–3.0 | Angle limit, relative to half-FOV, for distant particles. |
| `debugLine` | `true` | | Adds the Vanguard line to the F3 overlay. |

Players, spectators, invisible or glowing entities, named entities, leashed entities,
vehicles, passengers, and entities the local player is riding are excluded from LOD and
occlusion culling.

## F3 overlay

The Vanguard line has this form:

```text
Vanguard  total: 128  rendered: 104  throttled: 9  culled: 24 (occluded: 11)  particles: 37  block entities: 6
```

`total` counts entities that reached the vanilla visibility test. `rendered` and `culled`
partition that total after Vanguard's occlusion test. `throttled` counts entities that
reused a cached LOD render state. The particle and block entity counters are independent
of the entity totals.

## Building

Java 25 and Gradle 9.x are required. From the repository root:

```bash
gradle :fabric:build :quilt:build :neoforge:build
```

Artifacts:

- `fabric/build/libs/vanguard-fabric-1.0.0+26.3.jar`
- `quilt/build/libs/vanguard-quilt-1.0.0+26.3.jar`
- `neoforge/build/libs/vanguard-neoforge-1.0.0+26.3.jar`

The common source set lives in `common/src/main/java`. Fabric and Quilt hooks are in
`common/src/fabric`, and NeoForge hooks are in `neoforge/src/main/java`.

## License

MIT. See [LICENSE](LICENSE).

Copyright (c) 2026 Hasaan Amer.
