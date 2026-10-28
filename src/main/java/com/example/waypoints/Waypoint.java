package com.example.waypoints;

/**
 * color: 0xRRGGBB, or -1 for the rainbow chroma effect.
 * expiresAt: epoch millis when the waypoint disappears, 0 = permanent.
 * worldId: identifies the singleplayer save or multiplayer server that owns this waypoint.
 * markerText: one or more characters drawn inside this waypoint's colored square.
 */
public record Waypoint(String name, int x, int y, int z, int color, String dimension, long expiresAt, String worldId, String markerText) {
    public String safeMarkerText() {
        if (markerText == null || markerText.isBlank()) return "W";
        String value = markerText.trim();
        return value.length() > 2 ? value.substring(0, 2).toUpperCase(java.util.Locale.ROOT) : value.toUpperCase(java.util.Locale.ROOT);
    }
}
