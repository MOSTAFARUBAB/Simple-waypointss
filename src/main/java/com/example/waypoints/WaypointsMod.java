package com.example.waypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

/**
 * Simple client-side waypoints for Minecraft 26.3 (Fabric, Mojang names).
 *
 * Beam + label are drawn on the HUD by projecting each waypoint's world position onto the screen,
 * so they show through walls. Waypoints are saved to config/simple-waypoints.json.
 */
public class WaypointsMod implements ClientModInitializer {
    public static final String MOD_ID = "simplewaypoints";

    static final int CHROMA = -1;
    private static final int[] COLORS = {0xFF0000, 0x00FF00, 0x3366FF, 0xFFFF00, 0xFF55FF, 0x00FFFF, 0xFF9900, 0xFFFFFF, CHROMA};
    private static final String[] COLOR_NAMES = {"red", "green", "blue", "yellow", "pink", "cyan", "orange", "white", "chroma"};

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<Waypoint>>() {}.getType();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("simple-waypoints.json");

    private static final List<Waypoint> WAYPOINTS = new ArrayList<>();
    private static KeyMapping addKey;
    private static KeyMapping menuKey;
    private static boolean openMenuRequested = false;

    /** True when the real 3D beam is running; then the on-screen beam is not drawn. */
    public static volatile boolean beam3dActive = false;

    @Override
    public void onInitializeClient() {
        load();
        // Try to enable the real 3D beam (only present in the "3d-beam" build). Falls back to the on-screen beam.
        try {
            Class.forName("com.example.waypoints.beam.Beam3D").getMethod("init").invoke(null);
        } catch (Throwable t) {
            beam3dActive = false;
        }

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        addKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.simplewaypoints.add", InputConstants.KEY_B, category));
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.simplewaypoints.menu", InputConstants.KEY_J, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            purgeExpired();
            adoptLegacy();
            while (addKey.consumeClick()) {
                if (client.player == null) return;
                addHere();
                client.player.sendSystemMessage(Component.literal("Waypoint added. Press J to edit or delete it."));
            }
            while (menuKey.consumeClick()) {
                openMenuRequested = true;
            }
            if (openMenuRequested) {
                openMenuRequested = false;
                if (client.player != null) WaypointScreen.openMenu();
            }
        });

        registerCommands();

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(MOD_ID, "waypoints"),
                WaypointsMod::renderHud);
    }

    // ---------------- commands ----------------
    private static CompletableFuture<Suggestions> suggestNames(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder b) {
        for (Waypoint w : worldWaypoints()) b.suggest(w.name());
        return b.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestColors(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder b) {
        for (String n : COLOR_NAMES) b.suggest(n);
        return b.buildFuture();
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(ClientCommands.literal("wp")
                        .then(ClientCommands.literal("menu")
                                .executes(ctx -> {
                                    openMenuRequested = true;
                                    return 1;
                                }))
                        .then(ClientCommands.literal("add")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> {
                                            BlockPos p = ctx.getSource().getPlayer().blockPosition();
                                            add(StringArgumentType.getString(ctx, "name"), p.getX(), p.getY(), p.getZ());
                                            feedback(ctx.getSource(), "Waypoint added at your position.");
                                            return 1;
                                        })
                                        .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                                                .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                                                        .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                                                .executes(ctx -> {
                                                                    add(StringArgumentType.getString(ctx, "name"),
                                                                            IntegerArgumentType.getInteger(ctx, "x"),
                                                                            IntegerArgumentType.getInteger(ctx, "y"),
                                                                            IntegerArgumentType.getInteger(ctx, "z"));
                                                                    feedback(ctx.getSource(), "Waypoint added.");
                                                                    return 1;
                                                                }))))))
                        .then(ClientCommands.literal("remove")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .suggests(WaypointsMod::suggestNames)
                                        .executes(ctx -> {
                                            String n = StringArgumentType.getString(ctx, "name");
                                            boolean removed = WAYPOINTS.removeIf(w -> inCurrentWorld(w) && w.name().equalsIgnoreCase(n));
                                            save();
                                            feedback(ctx.getSource(), removed ? "Removed." : "No waypoint with that name.");
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("color")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .suggests(WaypointsMod::suggestNames)
                                        .then(ClientCommands.argument("color", StringArgumentType.word())
                                                .suggests(WaypointsMod::suggestColors)
                                                .executes(ctx -> {
                                                    String n = StringArgumentType.getString(ctx, "name");
                                                    Integer c = parseColor(StringArgumentType.getString(ctx, "color"));
                                                    if (c == null) {
                                                        feedback(ctx.getSource(), "Unknown color. Use red, green, blue, yellow, pink, cyan, orange, white, a hex code like ff8800, or chroma.");
                                                        return 0;
                                                    }
                                                    boolean ok = update(n, w -> new Waypoint(w.name(), w.x(), w.y(), w.z(), c, w.dimension(), w.expiresAt()));
                                                    feedback(ctx.getSource(), ok ? "Color changed." : "No waypoint with that name.");
                                                    return 1;
                                                }))))
                        .then(ClientCommands.literal("expire")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .suggests(WaypointsMod::suggestNames)
                                        .then(ClientCommands.argument("seconds", IntegerArgumentType.integer(0))
                                                .executes(ctx -> {
                                                    String n = StringArgumentType.getString(ctx, "name");
                                                    int secs = IntegerArgumentType.getInteger(ctx, "seconds");
                                                    long at = secs == 0 ? 0L : System.currentTimeMillis() + secs * 1000L;
                                                    boolean ok = update(n, w -> new Waypoint(w.name(), w.x(), w.y(), w.z(), w.color(), w.dimension(), at));
                                                    feedback(ctx.getSource(), ok ? (secs == 0 ? "Waypoint is now permanent." : "Waypoint will disappear in " + secs + "s.") : "No waypoint with that name.");
                                                    return 1;
                                                }))))
                        .then(ClientCommands.literal("list")
                                .executes(ctx -> {
                                    List<Waypoint> here = worldWaypoints();
                                    if (here.isEmpty()) feedback(ctx.getSource(), "No waypoints.");
                                    for (Waypoint w : here) {
                                        String extra = w.color() == CHROMA ? " chroma" : String.format(" #%06X", w.color() & 0xFFFFFF);
                                        if (w.expiresAt() > 0) extra += " (" + Math.max(0, (w.expiresAt() - System.currentTimeMillis()) / 1000) + "s left)";
                                        feedback(ctx.getSource(), w.name() + "  " + w.x() + " " + w.y() + " " + w.z() + extra);
                                    }
                                    return 1;
                                }))
                        .then(ClientCommands.literal("clear")
                                .executes(ctx -> {
                                    WAYPOINTS.removeIf(WaypointsMod::inCurrentWorld);
                                    save();
                                    feedback(ctx.getSource(), "All waypoints cleared.");
                                    return 1;
                                }))));
    }

    private static void feedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal(message));
    }

    // ---------------- data ----------------
    public static List<Waypoint> waypoints() {
        return WAYPOINTS;
    }

    /**
     * Identifies the world/server you are in: the world's name in singleplayer, the server address in multiplayer.
     * Uses reflection so a name change in a future Minecraft version can never break the build;
     * if it can't be read, all worlds share one list (the old behaviour).
     */
    private static Object cachedLevel = null;
    private static String cachedWorldKey = "unknown";

    private static Object callMethod(Object target, String name) {
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(name);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object readField(Object target, String name) {
        try {
            java.lang.reflect.Field f = target.getClass().getField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Throwable t) {
            return null;
        }
    }

    static String worldKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return cachedWorldKey;
        if (mc.level == cachedLevel) return cachedWorldKey;

        String key = "unknown";
        try {
            Object singleplayer = callMethod(mc, "getSingleplayerServer");
            if (singleplayer != null) {
                Object worldData = callMethod(singleplayer, "getWorldData");
                Object levelName = worldData != null ? callMethod(worldData, "getLevelName") : null;
                if (levelName != null) key = "world:" + levelName;
            } else {
                Object server = callMethod(mc, "getCurrentServer");
                if (server != null) {
                    Object ip = readField(server, "ip");
                    if (ip == null) ip = callMethod(server, "ip");
                    if (ip != null) key = "server:" + ip;
                }
            }
        } catch (Throwable ignored) {
        }
        cachedLevel = mc.level;
        cachedWorldKey = key;
        return key;
    }

    /** Stored in each waypoint as "<world>|<dimension>" so every world keeps its own waypoints. */
    public static String currentDimension() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "unknown|unknown";
        return worldKey() + "|" + mc.level.dimension().toString();
    }

    static boolean inCurrentWorld(Waypoint w) {
        return w.dimension().startsWith(worldKey() + "|");
    }

    /** Only the waypoints that belong to the world/server you are in (all of its dimensions). */
    public static List<Waypoint> worldWaypoints() {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : WAYPOINTS) {
            if (inCurrentWorld(w)) out.add(w);
        }
        return out;
    }

    /**
     * Waypoints saved before worlds were tracked have no world in them. The first world you join after
     * updating adopts them (once), so nothing is lost.
     */
    private static void adoptLegacy() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        String prefix = worldKey() + "|";
        boolean changed = false;
        for (int i = 0; i < WAYPOINTS.size(); i++) {
            Waypoint w = WAYPOINTS.get(i);
            if (!w.dimension().contains("|")) {
                WAYPOINTS.set(i, new Waypoint(w.name(), w.x(), w.y(), w.z(), w.color(), prefix + w.dimension(), w.expiresAt()));
                changed = true;
            }
        }
        if (changed) save();
    }

    static void add(String name, int x, int y, int z) {
        int color = COLORS[WAYPOINTS.size() % 8]; // red, green, blue, ... (skips chroma)
        WAYPOINTS.add(new Waypoint(name, x, y, z, color, currentDimension(), 0L));
        save();
    }

    static void addHere() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        BlockPos p = mc.player.blockPosition();
        add("Waypoint" + (worldWaypoints().size() + 1), p.getX(), p.getY(), p.getZ());
    }

    static void remove(Waypoint w) {
        WAYPOINTS.remove(w);
        save();
    }

    /** Adds a new waypoint (old == null) or replaces an existing one. */
    static void upsert(Waypoint old, Waypoint neu) {
        if (old == null) {
            WAYPOINTS.add(neu);
            save();
        } else {
            replace(old, neu);
        }
    }

    /** Given text from a color box, returns the name of the next color in the cycle. */
    static String nextColorName(String current) {
        Integer c = parseColor(current.trim());
        int idx = -1;
        for (int i = 0; i < COLORS.length; i++) {
            if (c != null && COLORS[i] == c) idx = i;
        }
        return COLOR_NAMES[(idx + 1) % COLOR_NAMES.length];
    }

    private static void replace(Waypoint old, Waypoint neu) {
        int i = WAYPOINTS.indexOf(old);
        if (i >= 0) {
            WAYPOINTS.set(i, neu);
            save();
        }
    }

    /** Next color in the cycle: red > green > blue > yellow > pink > cyan > orange > white > rainbow. */
    static void cycleColor(Waypoint w) {
        int idx = 0;
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i] == w.color()) idx = i;
        }
        int next = COLORS[(idx + 1) % COLORS.length];
        replace(w, new Waypoint(w.name(), w.x(), w.y(), w.z(), next, w.dimension(), w.expiresAt()));
    }

    /** Timer cycle: permanent > 60s > 5 min > permanent. */
    static void cycleTimer(Waypoint w) {
        long now = System.currentTimeMillis();
        long next;
        if (w.expiresAt() == 0) next = now + 60_000L;
        else if (w.expiresAt() - now < 120_000L) next = now + 300_000L;
        else next = 0L;
        replace(w, new Waypoint(w.name(), w.x(), w.y(), w.z(), w.color(), w.dimension(), next));
    }

    private static boolean update(String name, UnaryOperator<Waypoint> fn) {
        boolean found = false;
        for (int i = 0; i < WAYPOINTS.size(); i++) {
            Waypoint w = WAYPOINTS.get(i);
            if (inCurrentWorld(w) && w.name().equalsIgnoreCase(name)) {
                WAYPOINTS.set(i, fn.apply(w));
                found = true;
            }
        }
        if (found) save();
        return found;
    }

    private static void purgeExpired() {
        long now = System.currentTimeMillis();
        if (WAYPOINTS.removeIf(w -> w.expiresAt() > 0 && now > w.expiresAt())) save();
    }

    static Integer parseColor(String s) {
        switch (s.toLowerCase()) {
            case "red": return 0xFF0000;
            case "green": return 0x00FF00;
            case "blue": return 0x3366FF;
            case "yellow": return 0xFFFF00;
            case "pink": return 0xFF55FF;
            case "cyan": return 0x00FFFF;
            case "orange": return 0xFF9900;
            case "white": return 0xFFFFFF;
            case "chroma": case "rainbow": return CHROMA;
            default:
                String hex = s.startsWith("#") ? s.substring(1) : s;
                if (hex.length() != 6) return null;
                try {
                    return Integer.parseInt(hex, 16);
                } catch (NumberFormatException e) {
                    return null;
                }
        }
    }

    /** Actual 0xRRGGBB to draw this frame (handles the rainbow effect). */
    public static int resolveColor(Waypoint w) {
        if (w.color() != CHROMA) return w.color() & 0xFFFFFF;
        float h = (System.currentTimeMillis() % 4000L) / 4000f * 6f;
        int i = (int) h;
        float f = h - i;
        float q = 1f - f;
        float r, g, b;
        switch (i % 6) {
            case 0: r = 1; g = f; b = 0; break;
            case 1: r = q; g = 1; b = 0; break;
            case 2: r = 0; g = 1; b = f; break;
            case 3: r = 0; g = q; b = 1; break;
            case 4: r = f; g = 0; b = 1; break;
            default: r = 1; g = 0; b = q; break;
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private static void load() {
        try {
            if (Files.exists(FILE)) {
                List<Waypoint> list = GSON.fromJson(Files.readString(FILE), LIST_TYPE);
                if (list != null) WAYPOINTS.addAll(list);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(WAYPOINTS, LIST_TYPE));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ---------------- HUD rendering (beam + label) ----------------
    private static final double BEAM_HEIGHT = 320.0;
    private static final double BEAM_DOWN = 400.0;
    private static final double CORE_HALF_WIDTH_BLOCKS = 0.16;
    private static final double GLOW_HALF_WIDTH_BLOCKS = 0.38;

    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || WAYPOINTS.isEmpty()) return;

        float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        Vec3 eye = mc.player.getEyePosition(partial);
        double yaw = Math.toRadians(mc.player.getViewYRot(partial));
        double pitch = Math.toRadians(mc.player.getViewXRot(partial));

        // Camera basis vectors (Minecraft: yaw 0 looks toward +Z).
        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw);
        double ry = 0;
        double rz = -Math.sin(yaw);
        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;
        double[] f = {fx, fy, fz};
        double[] r = {rx, ry, rz};
        double[] u = {ux, uy, uz};

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        double aspect = (double) sw / (double) sh;
        double tanHalfFov = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);

        Font font = mc.font;
        String dim = currentDimension();

        for (Waypoint w : WAYPOINTS) {
            if (!w.dimension().equals(dim)) continue;

            double bx = w.x() + 0.5 - eye.x;
            double by = w.y() - eye.y;
            double bz = w.z() + 0.5 - eye.z;
            double horiz = Math.sqrt(bx * bx + bz * bz);
            int rgb = resolveColor(w);

            // beam: fades out smoothly as you get close, so it never pops on/off
            double fade = Math.max(0.0, Math.min(1.0, (horiz - 1.0) / 3.0));
            if (!beam3dActive && fade > 0.02) {
                drawBeam(graphics, bx, by, bz, f, r, u, sw, sh, tanHalfFov, aspect, rgb, fade);
            }

            // label near the base of the beam
            double dx = bx;
            double dy = by + 1.2;
            double dz = bz;
            double[] p = project(dx, dy, dz, f, r, u, sw, sh, tanHalfFov, aspect);
            if (p == null) continue;
            int sx = (int) p[0];
            int sy = (int) p[1];
            if (sx < -60 || sx > sw + 60 || sy < -60 || sy > sh + 60) continue;

            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            String text = w.name() + " (" + (int) dist + "m)";
            int tw = font.width(text);
            int x0 = sx - tw / 2;
            int y0 = sy - 4;
            graphics.fill(x0 - 2, y0 - 2, x0 + tw + 2, y0 + 10, 0x90000000);
            graphics.text(font, text, x0, y0, 0xFFFFFFFF, false);
        }
    }

    /** Projects a world offset (relative to the eye) to {screenX, screenY, depth}, or null if behind the camera. */
    private static double[] project(double dx, double dy, double dz, double[] f, double[] r, double[] u,
                                    int sw, int sh, double tanHalfFov, double aspect) {
        double depth = dx * f[0] + dy * f[1] + dz * f[2];
        if (depth < 0.1) return null;
        double side = dx * r[0] + dy * r[1] + dz * r[2];
        double up = dx * u[0] + dy * u[1] + dz * u[2];
        double ndcX = side / (depth * tanHalfFov * aspect);
        double ndcY = up / (depth * tanHalfFov);
        return new double[]{sw / 2.0 + ndcX * sw / 2.0, sh / 2.0 - ndcY * sh / 2.0, depth};
    }

    /**
     * Draws a tall beam: a bright core plus a soft glow. A straight line in 3D is a straight line on screen,
     * so it is drawn row by row. Edges are anti-aliased with partly transparent pixels so the width
     * changes smoothly (no popping or flicker) while you walk.
     */
    private static void drawBeam(GuiGraphicsExtractor g, double bx, double by, double bz,
                                 double[] f, double[] r, double[] u, int sw, int sh,
                                 double tanHalfFov, double aspect, int rgb, double fade) {
        double near = 0.1;
        double startY = by - BEAM_DOWN;
        double totalLen = BEAM_DOWN + BEAM_HEIGHT;
        double d0 = bx * f[0] + startY * f[1] + bz * f[2];
        double d1 = d0 + totalLen * f[1];
        if (d0 < near && d1 < near) return;

        double t0 = 0.0;
        double t1 = 1.0;
        if (d0 < near) t0 = (near - d0) / (d1 - d0);
        else if (d1 < near) t1 = (near - d0) / (d1 - d0);

        double[] a = project(bx, startY + t0 * totalLen, bz, f, r, u, sw, sh, tanHalfFov, aspect);
        double[] b = project(bx, startY + t1 * totalLen, bz, f, r, u, sw, sh, tanHalfFov, aspect);
        if (a == null || b == null) return;

        double pxPerInvDepth = (sh / 2.0) / tanHalfFov;
        double invA = 1.0 / a[2];
        double invB = 1.0 / b[2];
        double yA = a[1];
        double yB = b[1];

        if (Math.abs(yB - yA) < 1.0) {
            double hw = Math.max(2.0, CORE_HALF_WIDTH_BLOCKS * pxPerInvDepth * invA);
            int cx = (int) a[0];
            int cy = (int) yA;
            g.fill(cx - (int) hw, cy - (int) hw, cx + (int) hw, cy + (int) hw, argb(1.0 * fade, rgb));
            return;
        }

        int rowStart = (int) Math.max(0, Math.floor(Math.min(yA, yB)));
        int rowEnd = (int) Math.min(sh, Math.ceil(Math.max(yA, yB)));
        for (int sy = rowStart; sy < rowEnd; sy++) {
            double frac = (sy - yA) / (yB - yA);
            if (frac < 0.0 || frac > 1.0) continue;
            double sx = a[0] + frac * (b[0] - a[0]);
            double inv = invA + frac * (invB - invA);

            // soft glow first, bright core on top
            double glowHalf = Math.max(2.5, GLOW_HALF_WIDTH_BLOCKS * pxPerInvDepth * inv);
            double coreHalf = Math.max(1.4, CORE_HALF_WIDTH_BLOCKS * pxPerInvDepth * inv);
            drawRowSpan(g, sx, sy, glowHalf, 0.18 * fade, rgb, sw);
            drawRowSpan(g, sx, sy, coreHalf, 1.0 * fade, rgb, sw);
        }
    }

    /** One horizontal slice of the beam with smooth (anti-aliased) left and right edges. */
    private static void drawRowSpan(GuiGraphicsExtractor g, double centerX, int sy, double halfWidth,
                                    double alpha, int rgb, int sw) {
        double left = centerX - halfWidth;
        double right = centerX + halfWidth;
        if (right < 0 || left > sw) return;

        int li = (int) Math.ceil(left);
        int ri = (int) Math.floor(right);
        if (ri > li) {
            g.fill(li, sy, ri, sy + 1, argb(alpha, rgb));
        }
        double leftCover = li - left;   // part of the pixel left of the span that is covered
        if (leftCover > 0.02) {
            g.fill(li - 1, sy, li, sy + 1, argb(alpha * Math.min(1.0, leftCover), rgb));
        }
        double rightCover = right - Math.max(ri, li);
        if (rightCover > 0.02) {
            int rx = Math.max(ri, li);
            g.fill(rx, sy, rx + 1, sy + 1, argb(alpha * Math.min(1.0, rightCover), rgb));
        }
    }

    private static int argb(double alpha, int rgb) {
        int a = (int) Math.round(255.0 * Math.max(0.0, Math.min(1.0, alpha)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
