Text scaling adjustment:
- Labels still grow as the waypoint gets farther away.
- Default maximum scale is capped at 1.25x (previously 2x).
- Large marker-size mode is capped at 1.5x (previously 3x).
- Small mode remains capped at 1.0x.
- Minimum scale is 0.75x.
- FOV is still excluded from the scale calculation to avoid zoom-related size jumps.
This is a source patch; it has not been compiled or tested in-game.
