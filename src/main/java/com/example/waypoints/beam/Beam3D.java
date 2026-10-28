package com.example.waypoints.beam;

import com.example.waypoints.Waypoint;
import com.example.waypoints.WaypointsMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Real 3D waypoint beams, drawn inside the world (through walls) so they never flicker or drift
 * when you turn or walk. Based on the Fabric "Rendering in the World" custom pipeline example,
 * ported to the 26.3 "Renderpearl" package names.
 *
 * This file is only compiled when building with -PwithBeam=true. If anything in here fails at runtime,
 * the mod switches itself back to the on-screen beam.
 */
public final class Beam3D {
    private static final float BEAM_HEIGHT = 320f;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(WaypointsMod.MOD_ID, "pipeline/beam_depth_tested"))
                    .build());

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final StagedVertexBuffer STAGED_BUFFER =
            new StagedVertexBuffer(() -> "Waypoint Beams Buffer", RenderType.SMALL_BUFFER_SIZE);

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
            double fovRad = Math.toRadians(mc.options.fov().get());
            double screenH = Math.max(1, mc.getWindow().getHeight());
            String dim = WaypointsMod.currentDimension();
            List<BeamState> out = new ArrayList<>();
            for (Waypoint w : WaypointsMod.waypoints()) {
                if (!w.dimension().equals(dim)) continue;

                double dx = w.x() + 0.5 - eye.x;
                double dz = w.z() + 0.5 - eye.z;
                double horiz = Math.sqrt(dx * dx + dz * dz);
                double fade = Math.max(0.0, Math.min(1.0, (horiz - 0.5) / 1.0));
                if (fade <= 0.02) continue;

                // keep the beam about the same thickness on screen at any distance (no thin shimmering line)
                double fromPixels = horiz * 2.0 * Math.tan(fovRad / 2.0) * (WaypointsMod.beamPixels() * 0.5) / screenH;
                float core = (float) Math.max(0.22 * WaypointsMod.beamWidth, fromPixels);
                float glow = core;

                int c = WaypointsMod.resolveColor(w);
                float r = ((c >> 16) & 0xFF) / 255f;
                float g = ((c >> 8) & 0xFF) / 255f;
                float b = (c & 0xFF) / 255f;
                out.add(new BeamState(w.x() + 0.5, w.y(), w.z() + 0.5, r, g, b, core, glow, (float) fade));
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

            RenderPipeline pipeline = PIPELINE;
            var formatBinding = pipeline.getVertexFormatBinding(0);
            PrimitiveTopology primitive = pipeline.getPrimitiveTopology();
            var draw = STAGED_BUFFER.appendDraw(
                    formatBinding, primitive,
                    primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null);

            PoseStack matrices = context.poseStack();
            Vec3 camera = context.levelState().cameraRenderState.pos;

            matrices.pushPose();
            matrices.translate(-camera.x, -camera.y, -camera.z);

            VertexConsumer builder = STAGED_BUFFER.getVertexBuilder(draw);
            Matrix4fc pose = matrices.last().pose();
            for (BeamState s : states) {
                float x = (float) s.x();
                float y = (float) s.y();
                float z = (float) s.z();
                // one solid beam; the depth test makes blocks in front of it hide it, like a beacon
                box(pose, builder, x - s.core(), y, z - s.core(), x + s.core(), y + BEAM_HEIGHT, z + s.core(),
                        s.r(), s.g(), s.b(), 0.9f * s.fade());
            }

            matrices.popPose();

            STAGED_BUFFER.upload();
            var info = STAGED_BUFFER.getExecuteInfo(draw);
            if (info != null) {
                drawBuffer(Minecraft.getInstance(), info, pipeline);
            }
            STAGED_BUFFER.endFrame();
        } catch (Throwable t) {
            fail(t);
        }
    }

    private static void box(Matrix4fc m, VertexConsumer buf,
                            float minX, float minY, float minZ,
                            float maxX, float maxY, float maxZ,
                            float r, float g, float b, float a) {
        // Front
        buf.addVertex(m, minX, minY, maxZ).setColor(r, g, b, a);
        buf.addVertex(m, maxX, minY, maxZ).setColor(r, g, b, a);
        buf.addVertex(m, maxX, maxY, maxZ).setColor(r, g, b, a);
        buf.addVertex(m, minX, maxY, maxZ).setColor(r, g, b, a);
        // Back
        buf.addVertex(m, maxX, minY, minZ).setColor(r, g, b, a);
        buf.addVertex(m, minX, minY, minZ).setColor(r, g, b, a);
        buf.addVertex(m, minX, maxY, minZ).setColor(r, g, b, a);
        buf.addVertex(m, maxX, maxY, minZ).setColor(r, g, b, a);
        // Left
        buf.addVertex(m, minX, minY, minZ).setColor(r, g, b, a);
        buf.addVertex(m, minX, minY, maxZ).setColor(r, g, b, a);
        buf.addVertex(m, minX, maxY, maxZ).setColor(r, g, b, a);
        buf.addVertex(m, minX, maxY, minZ).setColor(r, g, b, a);
        // Right
        buf.addVertex(m, maxX, minY, maxZ).setColor(r, g, b, a);
        buf.addVertex(m, maxX, minY, minZ).setColor(r, g, b, a);
        buf.addVertex(m, maxX, maxY, minZ).setColor(r, g, b, a);
        buf.addVertex(m, maxX, maxY, maxZ).setColor(r, g, b, a);
        // Top
        buf.addVertex(m, minX, maxY, maxZ).setColor(r, g, b, a);
        buf.addVertex(m, maxX, maxY, maxZ).setColor(r, g, b, a);
        buf.addVertex(m, maxX, maxY, minZ).setColor(r, g, b, a);
        buf.addVertex(m, minX, maxY, minZ).setColor(r, g, b, a);
        // Bottom
        buf.addVertex(m, minX, minY, minZ).setColor(r, g, b, a);
        buf.addVertex(m, maxX, minY, minZ).setColor(r, g, b, a);
        buf.addVertex(m, maxX, minY, maxZ).setColor(r, g, b, a);
        buf.addVertex(m, minX, minY, maxZ).setColor(r, g, b, a);
    }

    private static void drawBuffer(Minecraft client, StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
        var dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        RenderTarget mainTarget = client.gameRenderer.mainRenderTarget();
        var colorTexture = mainTarget.getColorTextureView();

        try (var pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> WaypointsMod.MOD_ID + " beam rendering", colorTexture,
                        Optional.empty(), mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));

            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);

            pass.setVertexBuffer(0, info.vertexBuffer().slice());
            pass.setIndexBuffer(info.indexBuffer(), info.indexType());
            pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        }
    }

    private record BeamState(double x, double y, double z, float r, float g, float b,
                             float core, float glow, float fade) {
    }
}
