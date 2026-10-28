# Simple Waypoints: restore vanilla beam proportions and fix marker glyph

Changes in this patch:
- Restores native BeaconRenderer proportions (inner radius 0.20, outer radius 0.25) instead of the widened custom values.
- Removes the width selector because the request is to keep the beam looking like a vanilla beacon.
- Replaces the Unicode diamond marker glyph with a small geometric marker made only from filled rectangles, avoiding font/resource-pack glyph corruption.
- Uses normalized case/whitespace-insensitive world ID comparisons in the menu filter.

Build with: `gradle clean build --no-daemon -PwithBeam=true`

Note: this source package has not been compiled or tested in-game here. Please test the Overworld/Nether/End selector and report the build log if Actions finds a compile error.
