package com.rubab.simplewaypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;

public final class WaypointStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<List<Waypoint>>(){}.getType();
    private static final List<Waypoint> ITEMS = new ArrayList<>();
    public static List<Waypoint> all() { return Collections.unmodifiableList(ITEMS); }
    public static void add(Waypoint w) { ITEMS.add(w); save(); }
    public static void toggle(Waypoint w) {
        for (int i=0;i<ITEMS.size();i++) if (ITEMS.get(i).id().equals(w.id())) ITEMS.set(i,w.withVisible(!w.visible()));
        save();
    }
    public static void load() {
        try {
            Path p = Minecraft.getInstance().gameDirectory.toPath().resolve("config/simple-waypoints.json");
            if (Files.exists(p)) { List<Waypoint> v=GSON.fromJson(Files.readString(p),TYPE); if(v!=null) ITEMS.addAll(v); }
        } catch(Exception e) { System.err.println("[Simple Waypoints] Load failed: "+e); }
    }
    public static void save() {
        try {
            Path p=Minecraft.getInstance().gameDirectory.toPath().resolve("config/simple-waypoints.json");
            Files.createDirectories(p.getParent()); Files.writeString(p,GSON.toJson(ITEMS));
        } catch(Exception e) { System.err.println("[Simple Waypoints] Save failed: "+e); }
    }
}
