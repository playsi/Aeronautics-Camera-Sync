package com.playsi.aero_cam_sync.client.utils;

import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.debug.DebugRayRenderer;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * Отвечает за рейкасты вниз и вычисление усреднённой нормали поверхности.
 *
 * <p>Использует радиальную сетку лучей: центр + {@code RAYCAST_COUNT} по кругу
 * радиуса {@code RAY_RADIUS}. Нормали усредняются только по лучам,
 * попавшим в "плоскую" поверхность ({@link #isSurfaceNearlyFlat}).</p>
 */
public final class SurfaceRaycaster {

    private SurfaceRaycaster() {}

    private static final float RAY_RADIUS = 0.58f;

    private static int rayCount()      { return Config.RAYCAST_COUNT.get(); }
    private static float offsetUp()    { return Config.RAYCAST_UP_LENGTH.get().floatValue(); }
    private static float offsetDown()  { return -Config.RAYCAST_DOWN_LENGTH.get().floatValue(); }

    // -------------------------------------------------------------------------
    // Публичный API
    // -------------------------------------------------------------------------

    /**
     * Считает усреднённую нормаль поверхности под игроком в пространстве сабвела.
     *
     * @return нормаль в мировых координатах, или {@code null} если все лучи промахнулись
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

        return allRaysMiss(hits) ? null : sum.div(hits).normalize();
    }

    /**
     * Возвращает {@code true} если ни один луч не попал в поверхность.
     * Используется снаружи для проверки перед применением тилта.
     */
    public static boolean allRaysMiss(int hits) {
        return hits == 0;
    }

    // -------------------------------------------------------------------------
    // Внутренняя логика
    // -------------------------------------------------------------------------

    private static Vec3[] buildRayOrigins(Vec3 feet) {
        int count      = rayCount();
        Vec3[] origins = new Vec3[count + 1];
        origins[0]     = feet;
        for (int i = 0; i < count; i++) {
            double angle   = 2 * Math.PI * i / count;
            origins[i + 1] = feet.add(Math.cos(angle) * RAY_RADIUS, 0, Math.sin(angle) * RAY_RADIUS);
        }
        return origins;
    }

    /**
     * Бьёт луч вниз в локальном пространстве сабвела.
     * Дополнительно проверяет, что точка попадания принадлежит этому сабвелу
     * (защита от ложных хитов когда игрок не над сабвелом).
     */
    private static BlockHitResult raycastDown(ClientSubLevel subLevel, LocalPlayer player, Vec3 origin) {
        Vec3 from = new Vec3(origin.x, origin.y + offsetUp(),   origin.z);
        Vec3 to   = new Vec3(origin.x, origin.y + offsetDown(), origin.z);

        ClipContext ctx = new ClipContext(
                from, to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        );

        BlockHitResult result = subLevel.getLevel().clip(ctx);

        if (result.getType() == HitResult.Type.MISS) return result;

        if (!isHitInsideSubLevel(subLevel, result)) {
            return BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to));
        }

        return result;
    }

    /**
     * Проверяет, что мировые координаты точки попадания находятся
     * в пределах bounding box сабвела.
     */
    private static boolean isHitInsideSubLevel(ClientSubLevel subLevel, BlockHitResult result) {
        Vec3 hitLocal = result.getLocation();
        Vector3d hitWorld = subLevel.logicalPose()
                .transformPosition(new Vector3d(hitLocal.x, hitLocal.y, hitLocal.z));

        BoundingBox3dc bounds = subLevel.boundingBox();
        return hitWorld.x >= bounds.minX() && hitWorld.x <= bounds.maxX()
                && hitWorld.y >= bounds.minY() && hitWorld.y <= bounds.maxY()
                && hitWorld.z >= bounds.minZ() && hitWorld.z <= bounds.maxZ();
    }

    private static Vector3f toWorldNormal(Direction direction, Quaterniondc orientation) {
        Vector3f localVec = MathUtils.directionToVector(direction);
        return MathUtils.transformToWorldSpace(localVec, orientation);
    }

    private static boolean isSurfaceNearlyFlat(Vector3f normal) {
        return normal.y >= Config.MIN_NORMAL_Y.get().floatValue();
    }

    private static void recordDebugRay(Vec3 origin, BlockHitResult hit) {
        boolean missed = hit.getType() == HitResult.Type.MISS;
        Vec3 from = origin.add(0, offsetUp(),   0);
        Vec3 to   = origin.add(0, offsetDown(), 0);
        DebugRayRenderer.submitRay(from, to,
                missed ? 1f : 0.2f,
                missed ? 0.2f : 1f,
                0.2f);
    }
}