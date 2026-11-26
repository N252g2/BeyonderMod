package io.github.n252g2.beyonder.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.n252g2.beyonder.Beyonder;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws what Spirit Vision reveals: halos around living things and waypoint markers.
 *
 * <p>Halos are camera-facing glow cards drawn with additive blending. Each creature gets a tight inner
 * glow and a wider, fainter outer one, sized, coloured and pulsing according to {@link SpiritAura}.
 * They draw twice: faintly through walls (you sense them), then fully where you can see them.</p>
 */
public final class SpiritRenderer {
    private static final ResourceLocation AURA = Beyonder.id("textures/misc/aura.png");
    private static final ResourceLocation BEAM = Beyonder.id("textures/misc/marker_beam.png");

    private static final double AURA_RANGE = 48.0;
    private static final double SENSE_THROUGH_WALLS = 28.0;
    private static final float MARKER_HEIGHT = 48.0F;
    /** Frames stacked vertically in aura.png and marker_beam.png (see tools/gen_textures.py). */
    private static final int AURA_FRAMES = 16;
    private static final int BEAM_FRAMES = 16;

    private SpiritRenderer() {}

    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float vision = Math.max(SpiritVision.amount(partial), PotionVision.glimpse(partial) * 0.8F);
        if (vision <= 0.01F) {
            return;
        }

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float time = (mc.level.getGameTime() + partial) / 20.0F;

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        modelView.mul(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        // Pass 1: sensed through walls. Pass 2: seen directly.
        RenderSystem.setShaderTexture(0, AURA);
        RenderSystem.disableDepthTest();
        drawAuras(mc, camera, cam, partial, time, vision * 0.35F, SENSE_THROUGH_WALLS);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL
        drawAuras(mc, camera, cam, partial, time, vision, AURA_RANGE);

        RenderSystem.setShaderTexture(0, BEAM);
        RenderSystem.disableDepthTest();
        drawMarkerBeams(mc, cam, time, vision);

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();

        drawMarkerLabels(mc, camera, cam, vision);

        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private static void drawAuras(Minecraft mc, Camera camera, Vec3 cam, float partial, float time, float alphaScale, double range) {
        AABB area = new AABB(cam, cam).inflate(range);
        Quaternionf facing = camera.rotation();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        boolean any = false;
        for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && !(e == mc.player && mc.options.getCameraType().isFirstPerson()))) {
            Vec3 pos = entity.getPosition(partial);
            double distance = pos.distanceTo(cam);
            if (distance > range) {
                continue;
            }
            float strength = SpiritAura.strength(entity);
            float[] color = SpiritAura.color(entity, strength);
            float size = SpiritAura.sizeScale(strength);
            float pulse = 0.85F + 0.15F * Mth.sin(time * SpiritAura.pulseSpeed(strength) + entity.getId() * 1.7F);
            float fade = 1.0F - (float) Mth.clamp((distance - range * 0.75) / (range * 0.25), 0.0, 1.0);
            float alpha = SpiritAura.opacity(strength) * pulse * fade * alphaScale;
            if (alpha <= 0.003F) {
                continue;
            }

            float height = entity.getBbHeight();
            float width = entity.getBbWidth();
            float cx = (float) (pos.x - cam.x);
            float cy = (float) (pos.y + height * 0.5 - cam.y);
            float cz = (float) (pos.z - cam.z);

            // Wisps stream faster around stronger spirits; each creature starts at its own point in the loop.
            float fps = 7.0F + 1.6F * Mth.clamp(strength + 1.0F, 0.0F, 8.0F);
            float frame = time * fps + entity.getId() * 5.0F;
            float sway = 0.07F * Mth.sin(time * 0.7F + entity.getId());

            // Inner glow hugs the body; outer glow spills out with strength and drifts out of step.
            float innerW = Math.max(width, height * 0.45F) * 0.85F * size;
            float innerH = height * 0.62F * size;
            quad(buffer, facing, cx, cy, cz, innerW, innerH, sway, frame, AURA_FRAMES, color, alpha);
            float breathe = 1.0F + 0.06F * Mth.sin(time * 0.9F + entity.getId());
            quad(buffer, facing, cx, cy, cz, innerW * 1.55F * breathe, innerH * 1.35F * breathe, -sway * 1.5F,
                    frame * 0.7F + AURA_FRAMES / 2.0F, AURA_FRAMES, color, alpha * 0.45F);
            any = true;
        }
        draw(buffer, any);
    }

    private static void drawMarkerBeams(Minecraft mc, Vec3 cam, float time, float vision) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        boolean any = false;
        for (SpiritMarkers.Marker marker : SpiritMarkers.all()) {
            if (marker.dimension() != mc.level.dimension()) {
                continue;
            }
            float x = (float) (marker.position().x - cam.x);
            float y = (float) (marker.position().y - cam.y);
            float z = (float) (marker.position().z - cam.z);
            // Beam turns around the vertical axis to face the camera; widens with distance so it stays visible.
            float distance = Mth.sqrt(x * x + z * z);
            float half = 0.35F + distance * 0.008F;
            float dx = distance > 0.001F ? -z / distance * half : half;
            float dz = distance > 0.001F ? x / distance * half : 0.0F;
            float alpha = vision * (0.80F + 0.20F * Mth.sin(time * 2.0F));
            float[] c = {marker.red(), marker.green(), marker.blue()};
            int frame = Math.floorMod((int) (time * 12.0F), BEAM_FRAMES);
            float v0 = frame / (float) BEAM_FRAMES;
            float v1 = (frame + 1) / (float) BEAM_FRAMES;
            vertex(buffer, x - dx, y, z - dz, 0.0F, v1, c, alpha);
            vertex(buffer, x + dx, y, z + dz, 1.0F, v1, c, alpha);
            vertex(buffer, x + dx, y + MARKER_HEIGHT, z + dz, 1.0F, v0, c, alpha);
            vertex(buffer, x - dx, y + MARKER_HEIGHT, z - dz, 0.0F, v0, c, alpha);
            any = true;
        }
        draw(buffer, any);
    }

    private static void drawMarkerLabels(Minecraft mc, Camera camera, Vec3 cam, float vision) {
        Font font = mc.font;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int alpha = Mth.clamp((int) (vision * 255.0F), 0, 255);
        if (alpha < 8) {
            return;
        }
        for (SpiritMarkers.Marker marker : SpiritMarkers.all()) {
            if (marker.dimension() != mc.level.dimension()) {
                continue;
            }
            Vec3 at = marker.position().add(0.0, 2.5, 0.0);
            double distance = at.distanceTo(cam);
            String text = marker.label() + "  " + Math.round(distance) + "m";
            float scale = 0.025F * (float) Math.max(1.0, distance * 0.09);

            PoseStack pose = new PoseStack();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            pose.mulPose(camera.rotation());
            pose.scale(scale, -scale, scale);
            int color = (alpha << 24) | 0xF2E6C8;
            int background = (Math.min(alpha, 96) << 24);
            font.drawInBatch(text, -font.width(text) / 2.0F, 0.0F, color, false, pose.last().pose(), buffers,
                    Font.DisplayMode.SEE_THROUGH, background, LightTexture.FULL_BRIGHT);
        }
        buffers.endBatch();
    }

    /**
     * A camera-facing card showing one flipbook frame. {@code roll} sways it around the view axis;
     * {@code frame} may be fractional and wraps around {@code frames}.
     */
    private static void quad(BufferBuilder buffer, Quaternionf facing, float cx, float cy, float cz,
                             float halfWidth, float halfHeight, float roll, float frame, int frames,
                             float[] color, float alpha) {
        int index = Math.floorMod((int) Math.floor(frame), frames);
        float v0 = index / (float) frames;
        float v1 = (index + 1) / (float) frames;
        float cos = Mth.cos(roll);
        float sin = Mth.sin(roll);
        float[][] local = {{-halfWidth, -halfHeight}, {halfWidth, -halfHeight}, {halfWidth, halfHeight}, {-halfWidth, halfHeight}};
        float[][] uv = {{0.0F, v1}, {1.0F, v1}, {1.0F, v0}, {0.0F, v0}};
        for (int i = 0; i < 4; i++) {
            float lx = local[i][0] * cos - local[i][1] * sin;
            float ly = local[i][0] * sin + local[i][1] * cos;
            Vector3f v = new Vector3f(lx, ly, 0.0F).rotate(facing);
            vertex(buffer, cx + v.x(), cy + v.y(), cz + v.z(), uv[i][0], uv[i][1], color, alpha);
        }
    }

    private static void vertex(BufferBuilder buffer, float x, float y, float z, float u, float v, float[] color, float alpha) {
        buffer.addVertex(x, y, z).setUv(u, v).setColor(color[0], color[1], color[2], Math.min(alpha, 1.0F));
    }

    private static void draw(BufferBuilder buffer, boolean any) {
        MeshData mesh = buffer.build();
        if (mesh != null) {
            if (any) {
                BufferUploader.drawWithShader(mesh);
            } else {
                mesh.close();
            }
        }
    }
}
