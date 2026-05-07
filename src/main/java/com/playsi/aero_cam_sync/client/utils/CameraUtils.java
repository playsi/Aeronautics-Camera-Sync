package com.playsi.aero_cam_sync.client.utils;

import com.playsi.aero_cam_sync.client.Config;
import com.playsi.aero_cam_sync.client.debug.DebugRayRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.checkerframework.checker.units.qual.C;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class CameraUtils {
    private static final float PLAYER_HALF_WIDTH = 0.3f;

    private static final float RAYCAST_OFFSET_UP   =  0.1f;
    private static final float RAYCAST_OFFSET_DOWN = - Config.RAYCAST_DOWN_LENGTH.get().floatValue();

    /** Текущий сглаженный тилт (identity = нет наклона). Живёт между кадрами. */
    private static final Quaternionf smoothedTilt = new Quaternionf(); // identity

    public static boolean shouldApplyTilt() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        return player != null
                && mc.options.getCameraType().isFirstPerson()
                && player.getVehicle() == null;
    }

    public static ClientSubLevel getClientSubLevel(LocalPlayer player) {
        SubLevel subLevel = Sable.HELPER.getTrackingOrVehicleSubLevel(player);
        return subLevel instanceof ClientSubLevel csl ? csl : null;
    }

    public static Vector3f getSurfaceNormal(ClientSubLevel subLevel, Pose3dc pose) {
        LocalPlayer player = Minecraft.getInstance().player;
        assert player != null;

        Vec3 feet = player.position();

        Vec3[] origins = {
                feet.add( PLAYER_HALF_WIDTH, 0,  PLAYER_HALF_WIDTH),
                feet.add(-PLAYER_HALF_WIDTH, 0,  PLAYER_HALF_WIDTH),
                feet.add( PLAYER_HALF_WIDTH, 0, -PLAYER_HALF_WIDTH),
                feet.add(-PLAYER_HALF_WIDTH, 0, -PLAYER_HALF_WIDTH),
        };

        DebugRayRenderer.clear();

        Vector3f averaged = new Vector3f();
        int validCount = 0;

        for (Vec3 origin : origins) {
            Vec3 from = origin.add(0, RAYCAST_OFFSET_UP,   0);
            Vec3 to   = origin.add(0, RAYCAST_OFFSET_DOWN, 0);

            // Рисуем луч: зелёный = начало, красный = конец
            DebugRayRenderer.submitRay(from, to, 0.2f, 1f, 0.2f);

            BlockHitResult hit = raycastDown(subLevel, player, origin);
            if (hit.getType() == HitResult.Type.MISS) continue;

            Vector3f localNormal = directionToVector(hit.getDirection());
            Vector3f worldNormal = transformToWorldSpace(localNormal, pose.orientation());

            if (!isSurfaceNearlyFlat(worldNormal)) continue;

            averaged.add(worldNormal);
            validCount++;
        }

        if (validCount == 0) return null;

        return averaged.div(validCount).normalize();
    }

    /**
     * Обновляет сглаженный тилт: slerp от текущего к целевому.
     *
     * @param surfaceNormal целевая нормаль, или {@code null} — плавный возврат к identity
     */
    public static void updateSmoothedTilt(Vector3f surfaceNormal) {
        // Целевой кватернион: поворот от (0,1,0) к нормали поверхности,
        // либо identity, если нормали нет.
        Quaternionf target = (surfaceNormal != null)
                ? new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), surfaceNormal)
                : new Quaternionf(); // identity

        float alpha = Config.SMOOTH_SPEED.get().floatValue();
        smoothedTilt.slerp(target, alpha);
    }

    /**
     * Накладывает сглаженный тилт поверх ванильного поворота камеры.
     */
    public static void applyTiltToCamera(Camera camera) {
        Quaternionf tilt    = new Quaternionf(smoothedTilt);
        Quaternionf vanilla = new Quaternionf(camera.rotation());

        tilt.mul(vanilla);
        camera.rotation().set(tilt);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private static boolean isSurfaceNearlyFlat(Vector3f normal) {
        return normal.y >= Config.MIN_NORMAL_Y.get().floatValue();
    }

    private static BlockHitResult raycastDown(ClientSubLevel subLevel, LocalPlayer player, Vec3 origin) {
        Vec3 from = new Vec3(origin.x, origin.y + RAYCAST_OFFSET_UP,  origin.z);
        Vec3 to   = new Vec3(origin.x, origin.y + RAYCAST_OFFSET_DOWN, origin.z);

        ClipContext ctx = new ClipContext(
                from, to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        );
        return subLevel.getLevel().clip(ctx);
    }

    private static Vector3f directionToVector(Direction direction) {
        return new Vector3f(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static Vector3f transformToWorldSpace(Vector3f localVector, Quaterniondc orientation) {
        return toQuaternionf(orientation).transform(localVector);
    }

    private static Quaternionf toQuaternionf(Quaterniondc q) {
        return new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w());
    }

    public static Quaternionf getSmoothedTilt() {
        return new Quaternionf(smoothedTilt);
    }
}