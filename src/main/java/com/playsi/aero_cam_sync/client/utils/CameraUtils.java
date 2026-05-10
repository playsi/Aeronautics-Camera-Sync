package com.playsi.aero_cam_sync.client.utils;

import com.playsi.aero_cam_sync.client.Config;
import com.playsi.aero_cam_sync.client.debug.DebugRayRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import javax.annotation.Nullable;

public class CameraUtils {

    // ── кеш последнего известного сабвела ────────────────────────────────────

    private static ClientSubLevel cacheSub = null;

    // ── константы рейкастов ───────────────────────────────────────────────────

    private static final float RAY_RADIUS         = 0.58f;

    private static int rayCount() { return Config.RAYCAST_COUNT.get(); }
    private static float offsetUp() { return Config.RAYCAST_UP_LENGTH.get().floatValue(); }
    private static float offsetDown() { return -Config.RAYCAST_DOWN_LENGTH.get().floatValue(); }

    // ── сглаженный тилт (живёт между кадрами) ────────────────────────────────

    private static final Quaternionf smoothedTilt = new Quaternionf(); // identity

    // ── публичное API ─────────────────────────────────────────────────────────

    public static boolean shouldApplyTilt() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (!Config.ALLOW_3RD_PERSON.get()
                && !mc.options.getCameraType().isFirstPerson())
            return false;

        return player != null
                && player.getVehicle() == null;
    }

    /**
     * Возвращает актуальный ClientSubLevel для применения тилта.
     *
     * <ul>
     *   <li>Игрок на сабвеле → обновляем кеш, возвращаем его.</li>
     *   <li>Игрок в воздухе (прыжок) → возвращаем кеш без изменений,
     *       чтобы камера не дёргалась.</li>
     *   <li>Игрок на земле, но не на сабвеле → сбрасываем кеш,
     *       возвращаем null.</li>
     * </ul>
     */
    public static ClientSubLevel getClientSubLevel(LocalPlayer player) {
        SubLevel subLevel = Sable.HELPER.getTrackingOrVehicleSubLevel(player);

        if (subLevel != null) {
            cacheSub = subLevel instanceof ClientSubLevel csl ? csl : null;
            return cacheSub;
        }

        if (player.onGround()) {
            cacheSub = null;
            return null;
        }
        return cacheSub;
    }

    /**
     * Считает усреднённую нормаль поверхности под игроком.
     * Использует радиальную сетку из 9 лучей (центр + 8 по кругу).
     *
     * @return нормаль, или {@code null} если ни один луч не попал
     */
    public static @Nullable Vector3f getSurfaceNormal(ClientSubLevel subLevel, Pose3dc pose) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return null;

        Vec3[] origins = buildRayOrigins(player.position());
        Vector3f sum   = new Vector3f();
        int hits       = 0;

        for (Vec3 origin : origins) {
            BlockHitResult hit = raycastDown(subLevel, player, origin);

            recordDebugRay(origin, hit);

            if (hit.getType() == HitResult.Type.MISS) continue;

            Vector3f worldNormal = toWorldNormal(hit.getDirection(), pose.orientation());
            if (!isSurfaceNearlyFlat(worldNormal)) continue;

            sum.add(worldNormal);
            hits++;
        }

        return hits > 0 ? sum.div(hits).normalize() : null;
    }

    private static Vec3[] buildRayOrigins(Vec3 feet) {
        int count      = rayCount();
        Vec3[] origins = new Vec3[count + 1];
        origins[0]     = feet;
        for (int i = 0; i < count; i++) {
            double angle  = 2 * Math.PI * i / count;
            origins[i + 1] = feet.add(Math.cos(angle) * RAY_RADIUS, 0, Math.sin(angle) * RAY_RADIUS);
        }
        return origins;
    }

    private static void recordDebugRay(Vec3 origin, BlockHitResult hit) {
        boolean missed = hit.getType() == HitResult.Type.MISS;
        Vec3 from = origin.add(0, offsetUp(),   0);
        Vec3 to   = origin.add(0, offsetDown(), 0);
        DebugRayRenderer.submitRay(from, to, missed ? 1f : 0.2f, missed ? 0.2f : 1f, 0.2f);
    }

    private static Vector3f toWorldNormal(Direction direction, Quaterniondc orientation) {
        return transformToWorldSpace(directionToVector(direction), orientation);
    }

    /**
     * Обновляет сглаженный тилт.
     *
     * @param surfaceNormal целевая нормаль, или {@code null} — плавный возврат к identity
     * @param deltaTime     время кадра (тики)
     * @param freeze        если {@code true} — тилт не меняется (игрок в воздухе над сабвелом)
     */
    public static void updateSmoothedTilt(Vector3f surfaceNormal, float deltaTime, boolean freeze) {
        if (freeze) return;

        Quaternionf target = (surfaceNormal != null)
                ? new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), surfaceNormal)
                : new Quaternionf();

        float alpha = Config.SMOOTH_SPEED.get().floatValue();
        float t = 1f - (float) Math.pow(0.5, deltaTime / alpha);
        smoothedTilt.slerp(target, t);
    }

    /**
     * Накладывает сглаженный тилт поверх ванильного поворота камеры.
     */
    public static void applyTiltToCamera(Camera camera, float partialTick) {
        if (Config.MODIFY_CAMERA_ROT.get()) {
            applyCameraRotation(camera);
        }
        if (Config.MODIFY_CAMERA_POS.get()) {
            applyCameraPosition(camera, partialTick);
        }
    }

    public static void applyCameraRotation(Camera camera) {
        Quaternionf tilt = new Quaternionf(smoothedTilt);
        Quaternionf vanilla = new Quaternionf(camera.rotation());
        tilt.mul(vanilla);
        camera.rotation().set(tilt);
    }

    public static void applyCameraPosition(Camera camera, float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        double feetX = Mth.lerp(partialTick, player.xOld, player.getX());
        double feetY = Mth.lerp(partialTick, player.yOld, player.getY());
        double feetZ = Mth.lerp(partialTick, player.zOld, player.getZ());

        Vec3 vanillaCamPos = camera.getPosition();
        Vector3f offset = new Vector3f(
                (float)(vanillaCamPos.x - feetX),
                (float)(vanillaCamPos.y - feetY),
                (float)(vanillaCamPos.z - feetZ)
        );

        new Quaternionf(smoothedTilt).transform(offset);

        camera.setPosition(
                feetX + offset.x,
                feetY + offset.y,
                feetZ + offset.z
        );
    }


    public static Quaternionf getSmoothedTilt() {
        return new Quaternionf(smoothedTilt);
    }

    private static boolean isSurfaceNearlyFlat(Vector3f normal) {
        return normal.y >= Config.MIN_NORMAL_Y.get().floatValue();
    }

    /**
     * Бьёт луч вниз в пространстве сабвела.
     * Дополнительно проверяет, что точка попадания принадлежит этому сабвелу
     * (защита от ложных хитов когда игрок не над сабвелом).
     */
    private static BlockHitResult raycastDown(ClientSubLevel subLevel, LocalPlayer player, Vec3 origin) {
        Vec3 from = new Vec3(origin.x, origin.y + offsetUp(),   origin.z);
        Vec3 to   = new Vec3(origin.x, origin.y + offsetDown(),  origin.z);

        ClipContext ctx = new ClipContext(
                from, to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        );

        BlockHitResult result = subLevel.getLevel().clip(ctx);

        if (result.getType() == HitResult.Type.MISS) return result;

        Vec3 hitLocal = result.getLocation();
        Vector3d hitWorld = subLevel.logicalPose()
                .transformPosition(new Vector3d(hitLocal.x, hitLocal.y, hitLocal.z));

        BoundingBox3dc bounds = subLevel.boundingBox();
        boolean inside = hitWorld.x >= bounds.minX() && hitWorld.x <= bounds.maxX()
                && hitWorld.y >= bounds.minY() && hitWorld.y <= bounds.maxY()
                && hitWorld.z >= bounds.minZ() && hitWorld.z <= bounds.maxZ();

        if (!inside) {
            return BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to));
        }

        return result;
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
}