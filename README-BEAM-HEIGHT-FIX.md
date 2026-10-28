# Simple Waypoints — full-height 3D beam fix

This package is based on the project ZIP you supplied, not the earlier original project.

Changes:
- The 3D beam now starts at the world's minimum build Y and ends at the world's maximum build Y, instead of starting at the waypoint Y and extending only 512 blocks upward.
- Preserves the existing beam style and color logic.
- Keeps the world/server + dimension waypoint key logic already present in WaypointsMod.java.
- Keeps the GitHub Actions workflow building with `-PwithBeam=true` and uploading the 3D-beam artifact.
- Keeps the `dimension().toString()` compile fix.

In GitHub, upload/commit the updated `Beam3D.java` from this package at:
`src/main/java/com/example/waypoints/beam/Beam3D.java`

The full project is included as a convenience. Commit the changes and check GitHub Actions. This source patch has not been compiled in this environment, so if the Minecraft 26.3 mappings differ for `getMinY()` / `getMaxY()`, share the build log and I will adjust it.
