package com.example.waypoints.beam;

import com.example.waypoints.Waypoint;
import com.example.waypoints.WaypointsMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Real 3D waypoint beams, drawn inside the world (through walls) so they never flicker or drift
 * when you turn or walk. Based on the Fabric "Rendering in the World" custom pipeline example,
 * ported to the 26.3 "Renderpearl" package names.
 *
 * This file is only compiled when building with -PwithBeam=true. If anything in here fails at runtime,
 * the mod switches itself back to the on-screen beam.
 */
public final class Beam3D {
    private static final int MAX_RENDERED_BEAMS = 24;
    private static final double MAX_BEAM_DISTANCE_SQUARED = 192.0 * 192.0;

    private static volatile List<BeamState> beams = List.of();
    private static volatile boolean failed = false;

    private Beam3D() {
    }

    /** Called by reflection from WaypointsMod. */
    public static void init() {
        LevelExtractionEvents.END_EXTRACTION.register(Beam3D::extract);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(Beam3D::renderAndDraw);
        WaypointsMod.beam3dActive = true;
    }

    private static void fail(Throwable t) {
        failed = true;
        WaypointsMod.beam3dActive = false;
        t.printStackTrace();
    }

    // ---- extraction phase ----
    private static void extract(LevelExtractionContext context) {
        if (failed) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                beams = List.of();
                return;
            }
            Vec3 eye = mc.player.getEyePosition();
            double[] cam = WaypointsMod.cameraState(mc);
            if (cam != null) eye = new Vec3(cam[0], cam[1], cam[2]);
            String dim = WaypointsMod.currentDimension();
            String worldId = WaypointsMod.currentWorldId();
            List<BeamState> out = new ArrayList<>();
            if (!WaypointsMod.showWaypointBeams) {
                beams = List.of();
                return;
            }
            for (Waypoint w : WaypointsMod.currentContextWaypoints()) {
                double dx = w.x() + 0.5 - eye.x;
                double dz = w.z() + 0.5 - eye.z;
                double distanceSquared = dx * dx + dz * dz;
                if (distanceSquared <= 0.25 || distanceSquared > MAX_BEAM_DISTANCE_SQUARED) continue;
                if (out.size() >= MAX_RENDERED_BEAMS) break;

                int color = WaypointsMod.resolveColor(w) & 0x00FFFFFF;
                // Store the waypoint's horizontal position; the native beacon beam will
                // be drawn from the world's minimum build height through its maximum.
                out.add(new BeamState(w.x() + 0.5, w.z() + 0.5, color));
            }
            beams = List.copyOf(out);
        } catch (Throwable t) {
            fail(t);
        }
    }

    // ---- drawing phase ----
    private static void renderAndDraw(LevelRenderContext context) {
        if (failed) return;
        try {
            List<BeamState> states = beams;
            if (states.isEmpty()) return;

            Vec3 camera = context.levelState().cameraRenderState.pos;
            PoseStack matrices = context.poseStack();
            // Use the same vanilla renderer used by beacon blocks. This preserves the native
            // animated texture, scrolling UVs, inner beam, and translucent outer glow.
            float animationTime = (System.currentTimeMillis() % 3_600_000L) / 50.0f;
            var collector = context.submitNodeCollector();

            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            int minBuildY = mc.level.getMinY();
            int maxBuildY = mc.level.getMaxY();
            int fullWorldHeight = maxBuildY - minBuildY;

            for (BeamState beam : states) {
                matrices.pushPose();
                // Anchor the vanilla beacon beam at the bottom of the world, not at
                // the waypoint's Y coordinate, so it spans the entire build height.
                matrices.translate(beam.x() - camera.x, minBuildY - camera.y, beam.z() - camera.z);
                int argb = 0xFF000000 | beam.rgb();
                BeaconRenderer.submitBeaconBeam(
                        matrices,
                        collector,
                        BeaconRenderer.BEAM_LOCATION,
                        1.0f,
                        animationTime,
                        0,
                        fullWorldHeight,
                        argb,
                        WaypointsMod.beamInnerRadius(),
                        WaypointsMod.beamOuterRadius()
                );
                matrices.popPose();
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    private record BeamState(double x, double z, int rgb) {
    }
}
