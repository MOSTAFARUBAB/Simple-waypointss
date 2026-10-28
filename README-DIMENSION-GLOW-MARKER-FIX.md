# Simple Waypoints — Dimension Detection, Beam Glow and Marker Update

## Fixes
- Normalizes Minecraft 26.3 dimension key strings so Overworld/Nether/End filters match saved waypoints, including records saved with the older `ResourceKey[...]` string representation.
- Uses the normalized dimension for new waypoint creation and HUD filtering.
- Increases the separation between the beacon beam's bright inner core and outer glow.
- Changes the waypoint icon from a changing first-letter square to a consistent diamond marker.

## Apply
Replace these files in your GitHub repository:
- `src/main/java/com/example/waypoints/WaypointsMod.java`
- `src/main/java/com/example/waypoints/WaypointScreen.java` (included unchanged so the current dimension selector remains in the package)
- `src/main/java/com/example/waypoints/beam/Beam3D.java` (included unchanged; native beacon renderer retained)

Then commit and run:
`gradle clean build --no-daemon -PwithBeam=true`

Build not yet verified in this environment. Test Overworld -> Nether -> End menu filters and check glow in-game.
