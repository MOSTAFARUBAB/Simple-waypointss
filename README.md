# Simple Waypoints — Beam Only

A client-side Fabric waypoint mod targeting Minecraft 26.3 and Java 25.

## World marker

Each visible waypoint is represented by a native Minecraft beacon beam:
- Vanilla `BeaconRenderer.submitBeaconBeam(...)` geometry and animated texture.
- Waypoint color is used for the beam.
- No floating names, distance text, logo/icon markers, or ground rings.
- No screen-space HUD waypoint rendering.

## Waypoint management

- Press **B** to create a waypoint.
- Press **J** to open the waypoint list.
- Waypoints are saved in `config/simple-waypoints.json`.
- Dimension and world/server filtering are retained.
- Beam visibility and beam thickness can be changed in the waypoint menu.

## Build

The GitHub Actions workflow builds the in-world beam variant:

```bash
gradle clean build --no-daemon -PwithBeam=true
```

The generated JAR is uploaded as the `simple-waypoints-beam-only` workflow artifact.

## Build status

This source edit has not been compiled or tested in-game here. Use GitHub Actions to verify the Minecraft 26.3/Fabric API compatibility. If compilation fails, share the complete build log.
