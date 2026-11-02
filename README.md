# Simple Waypoints — Minecraft 26.3 / Fabric

This is the first source-project milestone. Press **B** to open the waypoint menu. **Add waypoint here** saves the player's current position; clicking an entry toggles its visibility. Waypoints are stored in `config/simple-waypoints.json` and include their dimension.

## Build
Requires JDK 25. Run `gradle build` (or generate/use a Gradle wrapper). The JAR will be in `build/libs/`.

## Not yet implemented
This first milestone does **not** yet include the 3D beacon beam, ground ring, floating world labels, rename/delete/color controls, or distance scaling. Those must be added and tested against the 26.3 world-rendering API before this can be called the complete Dawn-style replacement.
