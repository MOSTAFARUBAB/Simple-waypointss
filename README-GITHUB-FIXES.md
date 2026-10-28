# Simple Waypoints — GitHub build and world-isolation fixes

This source patch makes two targeted fixes:

1. GitHub Actions now runs only the `-PwithBeam=true` build and uploads only the `*-3d-beam-*.jar` artifact. The job fails if that JAR is not produced.
2. Waypoint ownership now includes the single-player save path (or multiplayer server address) as well as the dimension, so waypoints from different worlds/servers no longer render together.

## Apply on GitHub

Upload/replace the changed files in your repository:
- `.github/workflows/build.yml`
- `src/main/java/com/example/waypoints/WaypointsMod.java`

Commit the changes. In Actions, download the artifact named `simple-waypoints-3d-beam`.

## Important notes

- This is a source-code package, not a compiled mod JAR. The GitHub Actions build still needs to run.
- Existing waypoints saved before this fix use only a dimension ID and cannot safely be assigned to a specific world automatically. Those legacy entries may not appear after the fix; recreate them in the intended world.
- The `Beam3D.java` in this project still uses custom geometry. This patch fixes the build selection and cross-world waypoint leakage; it does **not** yet prove or implement a fully native vanilla beacon texture. The resulting beam must still be tested in-game.
