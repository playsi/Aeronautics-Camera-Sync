package com.playsi.aero_cam_sync.client;

import com.playsi.aero_cam_sync.Config;
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
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class CameraUtils {
    private static final float PLAYER_HALF_WIDTH = 0.3f;

    private static final float RAYCAST_OFFSET_UP   =  0.1f;
    private static final float RAYCAST_OFFSET_DOWN = -2.5f;

    /** Проверяет базовые условия: первое лицо, игрок на ногах, в мире. */
    public static boolean shouldApplyTilt() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        return player != null
                && mc.options.getCameraType().isFirstPerson()
                && player.getVehicle() == null;
    }

    /**
     * Возвращает {@link ClientSubLevel}, за которым следит или в котором едет игрок,
     * либо {@code null}, если иг\к не находится ни в одном сублевеле.
     */
    public static ClientSubLevel getClientSubLevel(LocalPlayer player) {
        SubLevel subLevel = Sable.HELPER.getTrackingOrVehicleSubLevel(player);
        return subLevel instanceof ClientSubLevel csl ? csl : null;
    }

    /**
     * Пускает луч вниз под позицией игрока и возвращает мировую нормаль
     * поверхности, на которую он приземлился.
     *
     * @return нормаль в мировом пространстве, или {@code null} при промахе
     */
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

        Vector3f averaged = new Vector3f();
        int validCount = 0;

        for (Vec3 origin : origins) {
            BlockHitResult hit = raycastDown(subLevel, player, origin);
            if (hit.getType() == HitResult.Type.MISS) continue;

            Vector3f localNormal = directionToVector(hit.getDirection());
            Vector3f worldNormal = transformToWorldSpace(localNormal, pose.orientation());

            if (!isSurfaceNearlyFlat(worldNormal)) continue;

            averaged.add(worldNormal);
            validCount++;
        }

        if (validCount == 0) return null;

        return averaged.div(validCount).normalize(); // среднее → нормализуем обратно в unit vector
    }

    private static boolean isSurfaceNearlyFlat(Vector3f normal) {
        return normal.y >= Config.MIN_NORMAL_Y.get().floatValue();
    }

    private static BlockHitResult raycastDown(ClientSubLevel subLevel, LocalPlayer player, Vec3 origin) {
        Vec3 from = new Vec3(origin.x, origin.y + RAYCAST_OFFSET_UP,   origin.z);
        Vec3 to   = new Vec3(origin.x, origin.y + RAYCAST_OFFSET_DOWN,  origin.z);

        ClipContext ctx = new ClipContext(
                from, to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        );
        return subLevel.getLevel().clip(ctx);
    }

    /** Конвертирует осевое направление блока в единичный вектор. */
    private static Vector3f directionToVector(Direction direction) {
        return new Vector3f(
                direction.getStepX(),
                direction.getStepY(),
                direction.getStepZ()
        );
    }

    /**
     * Переводит вектор из локального пространства позы в мировое,
     * применяя кватернион ориентации.
     */
    private static Vector3f transformToWorldSpace(Vector3f localVector, Quaterniondc orientation) {
        Quaternionf rotation = toQuaternionf(orientation);
        return rotation.transform(localVector);
    }

    /**
     * Накладывает тилт камеры: поворот от мирового up-вектора (0,1,0)
     * к нормали поверхности, поверх ванильного поворота камеры.
     */
    public static void applyTiltToCamera(Camera camera, Vector3f surfaceNormal) {
        Quaternionf tilt    = new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), surfaceNormal);
        Quaternionf vanilla = new Quaternionf(camera.rotation());

        tilt.mul(vanilla);
        camera.rotation().set(tilt);
    }

    /** Конвертирует {@link Quaterniondc} (double) в {@link Quaternionf} (float). */
    private static Quaternionf toQuaternionf(Quaterniondc q) {
        return new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w());
    }
}
