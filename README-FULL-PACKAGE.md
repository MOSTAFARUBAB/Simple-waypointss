# Simple Waypoints — Vanilla Beacon Beam package

This is the full source project for Minecraft Java 26.3 + Fabric. The `Beam3D.java` source calls Minecraft's native `BeaconRenderer.submitBeaconBeam(...)` so the waypoint beam uses the vanilla beacon texture/animation instead of the old custom solid geometry.

## Build the installable mod on Windows

1. Install/use **Java 25** and Gradle 9.6+ (or open this project in an IDE that has a compatible Gradle setup).
2. Extract the entire ZIP to a normal folder.
3. Double-click `BUILD-3D-BEAM-WINDOWS.bat`.
4. Wait for `BUILD SUCCESSFUL`.
5. The script copies the 3D-beam JAR into `READY-TO-INSTALL`.
6. Copy that JAR into `.minecraft/mods` and remove older `simple-waypoints` JARs first. Keep Fabric API installed.

If Windows says Gradle is missing, open a terminal in this folder and run:

```bat
gradle clean build -PwithBeam=true
```

Then select the normal `*-3d-beam-*.jar` from `build/libs` (not a `sources` JAR).

## Important

The ZIP contains source and a build helper, not a precompiled JAR. This environment cannot compile Minecraft 26.3 because it only has Java 21 and no Gradle installation. The build script is intended to produce the installable JAR on your Java 25/Gradle setup. The native rendering change still needs an in-game visual check.
