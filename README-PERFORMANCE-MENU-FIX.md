Simple Waypoints — menu, beam width, and performance update

Changes:
- Adds menu toggles for waypoint labels and beams, plus Normal / Thick / Extra Thick beam width.
- Makes the native beacon beam wider using the renderer's inner/outer radii.
- Limits 3D beams to waypoints within 192 blocks and at most 24 beams, reducing render work.
- Uses the current context's already-filtered waypoint list for 3D extraction.
- Hides the floating waypoint marker when the player is within 2.5 blocks, preventing the very close-up label in the supplied screenshot from cluttering the view.

The settings are session-only in this version and reset when Minecraft restarts. Native beacon rendering is retained.
Build with: gradle clean build --no-daemon -PwithBeam=true
