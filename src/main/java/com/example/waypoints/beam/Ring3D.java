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
 * The flat ring that lies on the ground around a waypoint (the "feature on the ground").
 * It is its own small renderer and does not touch the beam in any way. Blocks hide it like any
 * other thing on the ground. Only compiled in the -PwithBeam build; if anything fails the ring just
 * switches itself off and the beam keeps working.
 */
public final class Ring3D {
    private static final double MAX_DISTANCE_SQUARED = 128.0 * 128.0;
    private static final int MAX_RINGS = 24;
    private static final int SEGMENTS = 40;
    private static final float OUTER_RADIUS = 1.0f;
    private static final float INNER_RADIUS = 0.88f;
    private static final float LIFT = 0.02f;   // keeps the ring just above the ground so it does not flicker into it
    private static final float ALPHA = 0.9f;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(WaypointsMod.MOD_ID, "pipeline/ring_depth_tested"))
                    .build());

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final StagedVertexBuffer STAGED_BUFFER =
            new StagedVertexBuffer(() -> "Waypoint Rings Buffer", RenderType.SMALL_BUFFER_SIZE);

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

    // ---- extraction phase ----
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

                int c = WaypointsMod.resolveColor(w);
                out.add(new RingState(w.x() + 0.5, w.y() + LIFT, w.z() + 0.5,
                        ((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f));
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
            for (RingState s : states) {
                float cx = (float) s.x();
                float y = (float) s.y();
                float cz = (float) s.z();
                for (int i = 0; i < SEGMENTS; i++) {
                    double a0 = (Math.PI * 2.0) * i / SEGMENTS;
                    double a1 = (Math.PI * 2.0) * (i + 1) / SEGMENTS;
                    float c0 = (float) Math.cos(a0);
                    float s0 = (float) Math.sin(a0);
                    float c1 = (float) Math.cos(a1);
                    float s1 = (float) Math.sin(a1);
                    float ix0 = cx + c0 * INNER_RADIUS, iz0 = cz + s0 * INNER_RADIUS;
                    float ox0 = cx + c0 * OUTER_RADIUS, oz0 = cz + s0 * OUTER_RADIUS;
                    float ix1 = cx + c1 * INNER_RADIUS, iz1 = cz + s1 * INNER_RADIUS;
                    float ox1 = cx + c1 * OUTER_RADIUS, oz1 = cz + s1 * OUTER_RADIUS;
                    // both sides, so the ring is visible whichever way the quads face
                    quad(pose, builder, ix0, iz0, ox0, oz0, ox1, oz1, ix1, iz1, y, s.r(), s.g(), s.b());
                    quad(pose, builder, ix1, iz1, ox1, oz1, ox0, oz0, ix0, iz0, y, s.r(), s.g(), s.b());
                }
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

    private static void quad(Matrix4fc m, VertexConsumer buf,
                             float x1, float z1, float x2, float z2, float x3, float z3, float x4, float z4,
                             float y, float r, float g, float b) {
        buf.addVertex(m, x1, y, z1).setColor(r, g, b, ALPHA);
        buf.addVertex(m, x2, y, z2).setColor(r, g, b, ALPHA);
        buf.addVertex(m, x3, y, z3).setColor(r, g, b, ALPHA);
        buf.addVertex(m, x4, y, z4).setColor(r, g, b, ALPHA);
    }

    private static void drawBuffer(Minecraft client, StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
        var dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        RenderTarget mainTarget = client.gameRenderer.mainRenderTarget();
        var colorTexture = mainTarget.getColorTextureView();

        try (var pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> WaypointsMod.MOD_ID + " ring rendering", colorTexture,
                        Optional.empty(), mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));

            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);

            pass.setVertexBuffer(0, info.vertexBuffer().slice());
            pass.setIndexBuffer(info.indexBuffer(), info.indexType());
            pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        }
    }

    private record RingState(double x, double y, double z, float r, float g, float b) {
    }
}
