# New Feather-style waypoint menus

Both menus were replaced with the dark Feather-style design:
- WaypointScreen.java      : the J menu (list). Dimension buttons Overworld / Nether / End / All dimensions,
                             toggles (Labels, Beams, Beam thickness, Logo size), + New Waypoint, Edit, Delete, paging.
- WaypointEditScreen.java  : create / edit. Name, X/Y/Z, dimension buttons, color picker (HEX, Chroma, Reset,
                             Recent colors), timer, logo letter.
- WaypointsMod.java        : 3 small edits so a waypoint set to "All dimensions" shows in every dimension.

Layout fits a 270 px high GUI (1080p at GUI scale 4), so no buttons are cut off.
Alpha slider and Favorite colors from the reference picture are not included.
