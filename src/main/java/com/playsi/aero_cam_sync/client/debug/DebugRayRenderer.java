package com.playsi.aero_cam_sync.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.playsi.aero_cam_sync.client.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

import static com.playsi.aero_cam_sync.AeroCamSync.MODID;

@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class DebugRayRenderer {

    private static final long PICK_RAY_TTL_MS = 3000L;

    public record DebugRay(Vec3 origin, Vec3 end, float r, float g, float b) {}
    private record TimedDebugRay(DebugRay ray, long expireAt) {}
    private static final List<DebugRay> pendingRays = new ArrayList<>();
    private static final List<TimedDebugRay> pickRays = new ArrayList<>();

    public static void submitRay(Vec3 origin, Vec3 end, float r, float g, float b) {
        if (!Config.DEBUG_RAYS.get()) return;
        pendingRays.add(new DebugRay(origin, end, r, g, b));
    }

    public static void submitPickRay(Vec3 origin, Vec3 end, float r, float g, float b) {
        if (!Config.DEBUG_PICK_RAYS.get()) return;
        long expireAt = System.currentTimeMillis() + PICK_RAY_TTL_MS;
        pickRays.add(new TimedDebugRay(new DebugRay(origin, end, r, g, b), expireAt));
    }

    public static void clear() {
        pendingRays.clear();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!Config.DEBUG_RAYS.get() && !Config.DEBUG_PICK_RAYS.get()) return;

        long now = System.currentTimeMillis();
        pickRays.removeIf(t -> now >= t.expireAt());

        boolean hasNormal = Config.DEBUG_RAYS.get() && !pendingRays.isEmpty();
        boolean hasPick   = Config.DEBUG_PICK_RAYS.get() && !pickRays.isEmpty();
        if (!hasNormal && !hasPick) return;

        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);

        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.LINES);
        Matrix4f mat = ps.last().pose();

        if (hasNormal) {
            for (DebugRay ray : pendingRays) {
                drawLine(vc, mat, ray);
            }
        }

        if (hasPick) {
            for (TimedDebugRay t : pickRays) {
                drawLine(vc, mat, t.ray(), 1);
            }
        }

        buf.endBatch(RenderType.LINES);
        ps.popPose();
    }

    private static void drawLine(VertexConsumer vc, Matrix4f mat, DebugRay ray) {
        drawLine(vc, mat, ray, 1f);
    }

    private static void drawLine(VertexConsumer vc, Matrix4f mat, DebugRay ray, float alpha) {
        float x1 = (float) ray.origin().x, y1 = (float) ray.origin().y, z1 = (float) ray.origin().z;
        float x2 = (float) ray.end().x,    y2 = (float) ray.end().y,    z2 = (float) ray.end().z;

        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0) return;
        float nx = dx / len, ny = dy / len, nz = dz / len;

        vc.addVertex(mat, x1, y1, z1).setColor(ray.r(), ray.g(), ray.b(), alpha).setNormal(nx, ny, nz);
        vc.addVertex(mat, x2, y2, z2).setColor(ray.r(), ray.g(), ray.b(), alpha).setNormal(nx, ny, nz);
    }
}