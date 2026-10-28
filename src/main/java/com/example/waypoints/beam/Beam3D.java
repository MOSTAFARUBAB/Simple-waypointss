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
    private static final int BEAM_HEIGHT = 512;
    private static final float BEAM_INNER_RADIUS = 0.2f;
    private static final float BEAM_OUTER_RADIUS = 0.25f;

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
            List<BeamState> out = new ArrayList<>();
            for (Waypoint w : WaypointsMod.waypoints()) {
                if (!w.dimension().equals(dim)) continue;

                double dx = w.x() + 0.5 - eye.x;
                double dz = w.z() + 0.5 - eye.z;
                double horiz = Math.sqrt(dx * dx + dz * dz);
                if (horiz <= 0.5) continue;

                // Keep the vanilla beacon's real-world beam dimensions rather than widening a custom mesh.
                int color = WaypointsMod.resolveColor(w) & 0x00FFFFFF;
                out.add(new BeamState(w.x() + 0.5, w.y(), w.z() + 0.5, color));
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

            for (BeamState beam : states) {
                matrices.pushPose();
                matrices.translate(beam.x() - camera.x, beam.y() - camera.y, beam.z() - camera.z);
                int argb = 0xFF000000 | beam.rgb();
                BeaconRenderer.submitBeaconBeam(
                        matrices,
                        collector,
                        BeaconRenderer.BEAM_LOCATION,
                        1.0f,
                        animationTime,
                        0,
                        BEAM_HEIGHT,
                        argb,
                        BEAM_INNER_RADIUS,
                        BEAM_OUTER_RADIUS
                );
                matrices.popPose();
            }
        } catch (Throwable t) {
            fail(t);
        }
    }

    private record BeamState(double x, double y, double z, int rgb) {
    }
}
