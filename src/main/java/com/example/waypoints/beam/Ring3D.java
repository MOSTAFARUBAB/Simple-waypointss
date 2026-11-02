package com.example.waypoints.beam;

import com.example.waypoints.Waypoint;
import com.example.waypoints.WaypointsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A flat ring lying on the ground around each waypoint's beam.
 *
 * It uses only Minecraft's own rendering: the same SubmitNodeCollector that draws the vanilla beacon beam
 * (Beam3D), with Minecraft's built-in "debug quads" render type for flat colored shapes. There is no custom
 * render pipeline. Terrain hides it like anything else on the ground, and it follows the "Beams" toggle.
 *
 * Only compiled with -PwithBeam=true -PwithRing=true. If anything fails at runtime the ring switches itself
 * off and the beam keeps working.
 */
public final class Ring3D {
    private static final double MAX_DISTANCE_SQUARED = 128.0 * 128.0;
    private static final int MAX_RINGS = 24;
    private static final int SEGMENTS = 48;
    private static final float OUTER_RADIUS = 1.0f;
    private static final float INNER_RADIUS = 0.86f;
    private static final float LIFT = 0.03f;   // just above the ground so it does not flicker into it
    private static final int ALPHA = 0xE6;

    private static volatile List<RingState> rings = List.of();
    private static volatile boolean failed = false;

    private Ring3D() {
    }

    /** Called by reflection from WaypointsMod. */
    public static void init() {
        LevelExtractionEvents.END_EXTRACTION.register(Ring3D::extract);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(Ring3D::renderAndDraw);
    }

    private static void fail(Throwable t) {
        failed = true;
        rings = List.of();
        t.printStackTrace();
    }

    // ---- extraction phase: copy what we need ----
    private static void extract(LevelExtractionContext context) {
        if (failed) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || !WaypointsMod.showWaypointBeams) {
                rings = List.of();
                return;
            }
            Vec3 eye = mc.player.getEyePosition();
            List<RingState> out = new ArrayList<>();
            for (Waypoint w : WaypointsMod.currentContextWaypoints()) {
                double dx = w.x() + 0.5 - eye.x;
                double dz = w.z() + 0.5 - eye.z;
                if (dx * dx + dz * dz > MAX_DISTANCE_SQUARED) continue;
                if (out.size() >= MAX_RINGS) break;
                out.add(new RingState(w.x() + 0.5, w.y() + LIFT, w.z() + 0.5, WaypointsMod.resolveColor(w) & 0x00FFFFFF));
            }
            rings = List.copyOf(out);
        } catch (Throwable t) {
            fail(t);
        }
    }

    // ---- drawing phase ----
    private static void renderAndDraw(LevelRenderContext context) {
        if (failed) return;
        try {
            List<RingState> states = rings;
            if (states.isEmpty()) return;

            Vec3 camera = context.levelState().cameraRenderState.pos;
            PoseStack matrices = context.poseStack();
            var collector = context.submitNodeCollector();

            for (RingState ring : states) {
                final int argb = (ALPHA << 24) | ring.rgb();
                matrices.pushPose();
                matrices.translate(ring.x() - camera.x, ring.y() - camera.y, ring.z() - camera.z);
                collector.submitCustomGeometry(matrices, RenderTypes.debugQuads(), (pose, buffer) -> {
                    for (int i = 0; i < SEGMENTS; i++) {
                        double a0 = (Math.PI * 2.0) * i / SEGMENTS;
                        double a1 = (Math.PI * 2.0) * (i + 1) / SEGMENTS;
                        float c0 = (float) Math.cos(a0);
                        float s0 = (float) Math.sin(a0);
                        float c1 = (float) Math.cos(a1);
                        float s1 = (float) Math.sin(a1);
                        float ix0 = c0 * INNER_RADIUS, iz0 = s0 * INNER_RADIUS;
                        float ox0 = c0 * OUTER_RADIUS, oz0 = s0 * OUTER_RADIUS;
                        float ix1 = c1 * INNER_RADIUS, iz1 = s1 * INNER_RADIUS;
                        float ox1 = c1 * OUTER_RADIUS, oz1 = s1 * OUTER_RADIUS;
                        // both windings, so the ring is visible from above and below
                        buffer.addVertex(pose, ix0, 0f, iz0).setColor(argb);
                        buffer.addVertex(pose, ox0, 0f, oz0).setColor(argb);
                        buffer.addVertex(pose, ox1, 0f, oz1).setColor(argb);
                        buffer.addVertex(pose, ix1, 0f, iz1).setColor(argb);

                        buffer.addVertex(pose, ix1, 0f, iz1).setColor(argb);
                        buffer.addVertex(pose, ox1, 0f, oz1).setColor(argb);
                        buffer.addVertex(pose, ox0, 0f, oz0).setColor(argb);
                        buffer.addVertex(pose, ix0, 0f, iz0).setColor(argb);
                    }
                });
                matrices.popPose();
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    private record RingState(double x, double y, double z, int rgb) {
    }
}
