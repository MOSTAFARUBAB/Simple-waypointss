# Per-waypoint marker customization

This update makes each waypoint store its own marker character (`markerText`). When creating or editing a waypoint, set its **Color** field to choose the beam color; the square logo automatically uses that same waypoint color. The **Logo text** field lets you choose a single character such as W, H, N, or another letter.

The list-wide Logo color and Text color buttons have been removed because they incorrectly changed every waypoint. Logo size remains a global Small/Medium/Large display preference. Existing waypoint JSON files remain compatible: entries without `markerText` display `W` until edited and saved.

The native beacon beam implementation is not changed by this patch.
