package com.playsi.aero_cam_sync.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.playsi.aero_cam_sync.client.Config;
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

    // --- Публичный API: заполняется из CameraUtils каждый тик ---
    public record DebugRay(Vec3 origin, Vec3 end, float r, float g, float b) {}

    private static final List<DebugRay> pendingRays = new ArrayList<>();

    /** Вызывай из CameraUtils перед рендером, чтобы передать лучи. */
    public static void submitRay(Vec3 origin, Vec3 end, float r, float g, float b) {
        if (!Config.DEBUG_RAYS.get()) return;
        pendingRays.add(new DebugRay(origin, end, r, g, b));
    }

    public static void clear() {
        pendingRays.clear();
    }

    // -----------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!Config.DEBUG_RAYS.get()) return;
        if (pendingRays.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();

        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        // Смещаем в систему координат относительно камеры
        ps.translate(-cam.x, -cam.y, -cam.z);

        MultiBufferSource.BufferSource buf =
                mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.LINES);

        Matrix4f mat = ps.last().pose();

        for (DebugRay ray : pendingRays) {
            drawLine(vc, mat,
                    (float)(ray.origin().x), (float)(ray.origin().y), (float)(ray.origin().z),
                    (float)(ray.end().x),    (float)(ray.end().y),    (float)(ray.end().z),
                    ray.r(), ray.g(), ray.b());
        }

        buf.endBatch(RenderType.LINES);
        ps.popPose();
    }

    private static void drawLine(VertexConsumer vc, Matrix4f mat,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float r, float g, float b) {
        // RenderType.LINES требует нормаль — направление линии
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len == 0) return;
        float nx = dx/len, ny = dy/len, nz = dz/len;

        vc.addVertex(mat, x1, y1, z1).setColor(r, g, b, 1f).setNormal(nx, ny, nz);
        vc.addVertex(mat, x2, y2, z2).setColor(r, g, b, 1f).setNormal(nx, ny, nz);
    }
}