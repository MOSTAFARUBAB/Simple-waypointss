# Automatic Dimension Recognition + Pixel Logo Fix

Changes:
- Canonicalizes dimension IDs (including older `ResourceKey[...]` strings) before saving/filtering, so the mod automatically recognizes the dimension where a waypoint is created.
- Keeps menu/HUD/3D-beam filtering aligned to the current world/server and canonical current dimension.
- Generates the next default waypoint name only from names already used in the same world/server and dimension.
- Replaces the font-rendered logo glyph with a crisp, custom pixel-art white W inside a red square, matching the supplied reference style. No beam rendering code was changed.

Build in GitHub Actions with:
`gradle clean build --no-daemon -PwithBeam=true`

This patch has not been compiled in this environment because Gradle is not installed here.
