# Native beacon beam — full world height fix

This patch is based on `Simple-Waypoints-Beacon-Build-Fixed(1).zip`, the project you identified as the version with the good vanilla-like beam.

Only `src/main/java/com/example/waypoints/beam/Beam3D.java` has been changed:
- Keeps `BeaconRenderer.submitBeaconBeam(...)`, the native Minecraft beacon renderer.
- Keeps the native beacon texture, animation, inner beam, and outer glow.
- Anchors the beam at `level.getMinY()` and uses `level.getMaxY() - level.getMinY()` as its height, so it spans the world build range instead of a fixed 512 blocks starting at the waypoint.
- Does not change waypoint persistence or world/dimension separation code.

Apply only the updated Beam3D.java in your current GitHub repo, then run `gradle clean build --no-daemon -PwithBeam=true` in Actions. This source change has not been compiled or tested in-game in this environment.
