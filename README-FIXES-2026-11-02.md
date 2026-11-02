# Simple Waypoints fixes (2026-11-02)

Based on the uploaded `Simple-Waypoints-World-Perspective-Label-Fix(1).zip` project.

Changes:
- Keep the native `BeaconRenderer.submitBeaconBeam(...)` path, with Normal inner/outer radii set to 0.20/0.25 (vanilla-style dimensions); Thin and Thick remain available.
- Keep the separate 3D ground-ring renderer and make its band wider and slightly lifted above the waypoint block surface. Ring renderer failures are now printed instead of being silently swallowed.
- Waypoint label size grows with distance, is independent of FOV/zoom, and is capped at 1.25x for the default setting (1.5x for Large; 1.0x for Small).
- Remove the Logo size chip and its click handler from the waypoint list menu.

Build the native 3D beam + ring variant in GitHub Actions with:
`gradle clean build --no-daemon -PwithBeam=true`

This source package has been ZIP-integrity checked, but it has not been compiled or tested in-game in this environment.
