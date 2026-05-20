package com.playsi.aero_cam_sync.client.utils;

import com.playsi.aero_cam_sync.client.config.Config;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Управляет сглаженным тилтом и применяет его к ванильной камере.
 *
 * <p>Состояние ({@code smoothedTilt}) хранится статически — в каждый момент
 * времени существует один активный тилт для игрока.</p>
 */
public final class CameraController {

    private CameraController() {}

    private static final Quaternionf smoothedTilt = new Quaternionf();
    private static boolean wasApplyingTilt = false;

    // -------------------------------------------------------------------------
    // Публичный API
    // -------------------------------------------------------------------------

    /**
     * Возвращает {@code true} если тилт вообще должен применяться
     * (мод включён, игрок не в транспортном средстве, режим камеры подходит).
     */
    public static boolean shouldApplyTilt() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (!Config.MOD_ENABLED.get()) return false;

        if (!Config.ALLOW_3RD_PERSON.get()
                && !mc.options.getCameraType().isFirstPerson())
            return false;

        return player != null
                && player.getVehicle() == null;
    }

    /**
     * Вызывать в начале каждого кадра ПЕРЕД updateSmoothedTilt.
     * Сбрасывает тилт если мод только что "включился" после паузы.
     */
    public static void tickApplyState() {
        boolean applying = shouldApplyTilt();
        if (applying && !wasApplyingTilt) {
            smoothedTilt.identity();
        }
        wasApplyingTilt = applying;
    }

    /**
     * Обновляет сглаженный тилт.
     *
     * @param surfaceNormal целевая нормаль, или {@code null} — плавный возврат к identity
     * @param deltaTime     время кадра (тики)
     * @param freeze        если {@code true} — тилт не меняется (игрок в воздухе над сабвелом)
     */
    public static void updateSmoothedTilt(@javax.annotation.Nullable Vector3f surfaceNormal,
                                          float deltaTime,
                                          boolean freeze) {
        if (freeze) return;

        Quaternionf target = (surfaceNormal != null)
                ? new Quaternionf().rotationTo(new Vector3f(0f, 1f, 0f), surfaceNormal)
                : new Quaternionf();

        float alpha = Config.SMOOTH_SPEED.get().floatValue();
        float t = 1f - (float) Math.pow(0.5, deltaTime / alpha);
        smoothedTilt.slerp(target, t);
    }

    /**
     * Накладывает сглаженный тилт поверх ванильного поворота и позиции камеры.
     */
    public static void applyTiltToCamera(Camera camera, float partialTick) {
        if (Config.MODIFY_CAMERA_ROT.get()) {
            applyCameraRotation(camera);
        }
        if (Config.MODIFY_CAMERA_POS.get()) {
            applyCameraPosition(camera, partialTick);
        }
    }

    /**
     * Копия текущего сглаженного тилта (иммутабельна для вызывающего).
     */
    public static Quaternionf getSmoothedTilt() {
        return new Quaternionf(smoothedTilt);
    }

    /**
     * Сбросить тилт в identity (например, при смене мира).
     */
    public static void resetTilt() {
        smoothedTilt.identity();
    }

    // -------------------------------------------------------------------------
    // Внутренняя логика
    // -------------------------------------------------------------------------

    private static void applyCameraRotation(Camera camera) {
        Quaternionf tilt    = new Quaternionf(smoothedTilt);
        Quaternionf vanilla = new Quaternionf(camera.rotation());
        tilt.mul(vanilla);
        camera.rotation().set(tilt);
    }

    private static void applyCameraPosition(Camera camera, float partialTick) {
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
}