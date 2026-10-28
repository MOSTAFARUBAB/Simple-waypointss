# World/server-specific waypoint fix

This patch is based on your uploaded Native Beacon Full Height project. It keeps `Beam3D.java`'s native `BeaconRenderer.submitBeaconBeam(...)` rendering and full-height range, and adds a saved `worldId` to each waypoint.

- Singleplayer waypoints are scoped by the local save's level name.
- Multiplayer waypoints are scoped by the server address.
- Both HUD and 3D-beam rendering filter by world/server and dimension.
- Existing waypoint entries without a `worldId` are retained in the JSON but will not show in a world; recreate those waypoints after installing this version.

Build on GitHub Actions with `gradle clean build --no-daemon -PwithBeam=true`. This source patch has not been compiled in this environment; if the Minecraft 26.3 mappings report a symbol error, share the log.
