# Rendering fixes (2026-10-29)

Focused changes in `WaypointsMod.java`:

- The HUD fallback beam now starts at the world's minimum build height and spans the active world's full build height instead of using a fixed 320-block segment anchored at the waypoint's Y coordinate.
- Waypoint pill labels use the renderer's effective FOV when a compatible `getFov(camera, partialTick, true)` method is available, so zoom/FOV effects can be reflected in projection; it falls back to the configured FOV if the method is unavailable.
- Moved the pill label closer to the waypoint (1.5 blocks above instead of 2.5).

Build with Java 25 as required by this project. This source ZIP has not been compiled or tested in Minecraft in this environment.
