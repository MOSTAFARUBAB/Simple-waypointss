// Create a waypoint at coordinates (100, 65, -200)
WaypointBuilder builder = waypointService.createWaypointBuilder(100, 65, -200);

// Configure the waypoint with a name and color
builder.withName("Resource Area")
       .withColor(WaypointColor.fromRgb(255, 0, 0)); // Red color

// Create the waypoint for the player
UUID waypointId = waypointService.createWaypoint(player, builder);
