# Dimension browser and marker customization

This patch adds a dimension browser to the waypoint menu and marker appearance controls.

## Dimension browser
- `Current: ...` follows the dimension the player is currently in.
- Cycle the Dimension button to Overworld, Nether, End, and All Dimensions.
- The menu list is scoped to the current singleplayer save or multiplayer server.
- The 3D beam/HUD renderer still only renders waypoints in the player's actual current dimension; browsing another dimension in the menu does not draw its beams in the current dimension.
- New waypoints automatically store the active dimension and current world/server identity.
- Old entries with no worldId are assigned to the first active save/server that opens the menu, because their original ownership was not stored in older versions and cannot be inferred from the JSON.

## Marker appearance controls
- Logo size: Small, Medium, Large.
- Logo color: Red, Green, Blue, Yellow, Purple, Cyan, White.
- Text color: White, Yellow, Aqua, Green, Pink.
- The logo is drawn from pixel rectangles, not a font glyph, to avoid glyph/font glitches.

Settings are session-only in this patch and reset when Minecraft restarts. The existing beam renderer code is not modified by this patch.

Build in GitHub Actions with:

```bash
gradle clean build --no-daemon -PwithBeam=true
```
