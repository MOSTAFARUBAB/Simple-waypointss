package com.rubab.simplewaypoints;
import java.util.UUID;
public record Waypoint(UUID id, String name, String dimension, double x, double y, double z, int color, boolean visible) {
    public Waypoint withVisible(boolean value) { return new Waypoint(id,name,dimension,x,y,z,color,value); }
}
