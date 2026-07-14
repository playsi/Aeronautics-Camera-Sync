package com.playsi.aero_cam_sync.client.utils;

import com.playsi.aero_cam_sync.client.config.Config;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
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

    static final Quaternionf smoothedTilt = new Quaternionf();
    private static boolean wasApplyingTilt = false;

    // Множитель близости к стене: 1 = стен рядом нет (полный наклон), 0 = камера
    // вплотную к стене (наклона нет). Сглаживается, чтобы не дёргалось.
    private static float wallScale = 1.0f;

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
        if (player == null) return false;

        if (!Config.MOD_ENABLED.get()) return false;

        if (!Config.ALLOW_3RD_PERSON.get()
                && !mc.options.getCameraType().isFirstPerson())
            return false;

        return player.getVehicle() == null;
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
        // Сначала считаем, насколько близко стена в направлении сдвига — и масштабируем
        // ВЕСЬ наклон по этой близости (поворот + позиция + снаряды через getSmoothedTilt).
        updateWallScale(camera, partialTick);

        if (Config.MODIFY_CAMERA_ROT.get()) {
            applyCameraRotation(camera);
        }
        if (Config.MODIFY_CAMERA_POS.get()) {
            applyCameraPosition(camera, partialTick);
        }
    }

    /**
     * Текущий ПРИМЕНЯЕМЫЙ тилт — сглаженный наклон, уменьшенный по близости к стене.
     * Его используют и камера, и взгляд (перекрестие), и синхронизация на сервер,
     * поэтому всё (поворот, сдвиг, снаряды, лучи) уменьшается у стены согласованно.
     */
    public static Quaternionf getSmoothedTilt() {
        return effectiveTilt();
    }

    private static Quaternionf effectiveTilt() {
        if (wallScale >= 0.999f) return new Quaternionf(smoothedTilt);
        return new Quaternionf().slerp(smoothedTilt, wallScale);
    }

    /**
     * Сбросить тилт в identity (например, при смене мира).
     */
    public static void resetTilt() {
        smoothedTilt.identity();
        wallScale = 1.0f;
    }

    // -------------------------------------------------------------------------
    // Внутренняя логика
    // -------------------------------------------------------------------------

    private static void applyCameraRotation(Camera camera) {
        Quaternionf tilt    = effectiveTilt();
        Quaternionf vanilla = new Quaternionf(camera.rotation());
        tilt.mul(vanilla);
        camera.rotation().set(tilt);
    }

    /**
     * Считает {@link #wallScale}: НАИБОЛЬШИЙ масштаб наклона (0..1), при котором точка
     * камеры находится в чистом пространстве — у неё есть зазор во ВСЕ стороны (а не
     * только вдоль сдвига) и она не за стеной от глаза. У стены — 0 (наклона нет).
     * Уменьшение быстрое (чтобы не успеть залезть в блок), возврат — плавный.
     */
    private static void updateWallScale(Camera camera, float partialTick) {
        float target = 1.0f;

        //if (Config.MODIFY_CAMERA_POS.get() && Config.CAMERA_COLLISION.get()) {
          if (false) {  // This function work bad

            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                double feetX = Mth.lerp(partialTick, player.xOld, player.getX());
                double feetY = Mth.lerp(partialTick, player.yOld, player.getY());
                double feetZ = Mth.lerp(partialTick, player.zOld, player.getZ());

                Vec3 eye = camera.getPosition();
                ClientSubLevel sl = SubLevelTracker.getCachedSubLevel();
                Pose3dc pose = null;
                try { if (sl != null) pose = sl.renderPose(partialTick); }
                catch (Throwable ignored) { pose = null; }

                boolean prev = LevelClipMixinState.inTiltedClip;
                LevelClipMixinState.inTiltedClip = true;
                try {
                    target = 0.0f; // запас: позиция глаза (масштаб 0) всегда чистая
                    for (float s = 1.0f; s > 0.001f; s -= 0.1f) {
                        Vector3f off = new Quaternionf().slerp(smoothedTilt, s).transform(new Vector3f(
                                (float) (eye.x - feetX),
                                (float) (eye.y - feetY),
                                (float) (eye.z - feetZ)));
                        Vec3 cam = new Vec3(feetX + off.x, feetY + off.y, feetZ + off.z);
                        if (cameraClear(player, eye, cam, sl, pose)) { target = s; break; }
                    }
                } finally {
                    LevelClipMixinState.inTiltedClip = prev;
                }
            }
        }

        // Экспоненциальное сглаживание уже само быстрое, когда далеко от цели, и мягкое
        // у самой цели — поэтому ощущается плавно и при этом не успевает залезть в блок.
        // Полупериод настраивается; возврат наклона чуть плавнее спуска.
        float dt = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
        float smooth = Config.CAMERA_COLLISION_SMOOTH.get().floatValue();
        float halfLife = (target < wallScale) ? smooth : smooth * 1.5f;
        float a = (halfLife <= 1.0e-4f) ? 1f : 1f - (float) Math.pow(0.5, dt / halfLife);
        wallScale = Mth.lerp(a, wallScale, target);
    }

    /** Камера в чистом пространстве: не за стеной от глаза И есть зазор во все стороны. */
    private static boolean cameraClear(LocalPlayer player, Vec3 eye, Vec3 cam,
                                       ClientSubLevel sl, Pose3dc pose) {
        Level level = player.level();

        // 1) Между глазом и камерой нет стены (камера не за/внутри блока).
        if (blockedWorld(level, player, eye, cam)) return false;
        if (blockedSub(sl, pose, player, eye, cam)) return false;

        // 2) Зазор вокруг камеры (ближняя плоскость) — по 6 осям.
        final double R = 0.15;
        for (int axis = 0; axis < 3; axis++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                Vec3 to = cam.add(axis == 0 ? sign * R : 0,
                                  axis == 1 ? sign * R : 0,
                                  axis == 2 ? sign * R : 0);
                if (blockedWorld(level, player, cam, to)) return false;
                if (blockedSub(sl, pose, player, cam, to)) return false;
            }
        }
        return true;
    }

    private static boolean blockedWorld(Level level, LocalPlayer player, Vec3 from, Vec3 to) {
        ClipContext ctx = new ClipContext(from, to,
                ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player);
        ((ClipContextExtension) ctx).sable$setDoNotProject(true);
        return level.clip(ctx).getType() != HitResult.Type.MISS;
    }

    private static boolean blockedSub(ClientSubLevel sl, Pose3dc pose, LocalPlayer player,
                                      Vec3 from, Vec3 to) {
        if (sl == null || pose == null) return false;
        Vec3 lf = pose.transformPositionInverse(from);
        Vec3 lt = pose.transformPositionInverse(to);
        ClipContext ctx = new ClipContext(lf, lt,
                ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player);
        ((ClipContextExtension) ctx).sable$setDoNotProject(true);
        return sl.getLevel().clip(ctx).getType() != HitResult.Type.MISS;
    }

    private static void applyCameraPosition(Camera camera, float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        double feetX = Mth.lerp(partialTick, player.xOld, player.getX());
        double feetY = Mth.lerp(partialTick, player.yOld, player.getY());
        double feetZ = Mth.lerp(partialTick, player.zOld, player.getZ());

        Vec3 vanillaCamPos = camera.getPosition();

        // Сдвиг считается уже УМЕНЬШЕННЫМ наклоном (по близости к стене) — у стены он сам
        // сходит к нулю, поэтому камера не лезет в блок. Тот же тилт уходит на снаряды/лучи.
        Vector3f offset = effectiveTilt().transform(new Vector3f(
                (float)(vanillaCamPos.x - feetX),
                (float)(vanillaCamPos.y - feetY),
                (float)(vanillaCamPos.z - feetZ)));

        double targetX = feetX + offset.x;
        double targetY = feetY + offset.y;
        double targetZ = feetZ + offset.z;

        // Сдвиг камеры может занести её ВНУТРЬ соседней стены → видно сквозь блоки (X-ray).
        // Как ванильная камера от 3-го лица: прокидываем луч от глаза к желаемой позиции и
        // не пускаем камеру за ближайшую стену.
        //if (Config.CAMERA_COLLISION.get()) { // This function work bad

        if (false) {
            Vec3 clamped = clampToCollision(player, vanillaCamPos,
                    new Vec3(targetX, targetY, targetZ), partialTick);
            targetX = clamped.x;
            targetY = clamped.y;
            targetZ = clamped.z;
        }

        camera.setPosition(targetX, targetY, targetZ);
    }

    /**
     * Ограничивает позицию камеры так, чтобы между {@code anchor} (глаз — заведомо
     * безопасная точка) и камерой не было сплошного блока — ни МИРА, ни СУБЛЕВЕЛА
     * (палубы). Восемь смещённых лучей (как в ванильном {@code Camera#getMaxZoom})
     * учитывают ближнюю плоскость отсечения, чтобы камера не утыкалась вплотную.
     */
    private static Vec3 clampToCollision(LocalPlayer player, Vec3 anchor, Vec3 desired, float partialTick) {
        Vec3 delta = desired.subtract(anchor);
        double dist = delta.length();
        if (dist < 1.0e-4) return desired;

        float maxDist = collisionMaxDist(player, anchor, desired, partialTick);
        if (maxDist >= dist) return desired;
        return anchor.add(delta.scale(maxDist / dist));
    }

    /** @return расстояние от {@code anchor} до ближайшей стены вдоль отрезка (или его длина, если стен нет). */
    private static float collisionMaxDist(LocalPlayer player, Vec3 anchor, Vec3 desired, float partialTick) {
        Level level = player.level();
        Vec3 delta = desired.subtract(anchor);
        double dist = delta.length();
        if (dist < 1.0e-4) return 0f;

        // Сублевел, на котором стоим (для клипа по палубе в его локальной системе).
        ClientSubLevel subLevel = SubLevelTracker.getCachedSubLevel();
        Pose3dc subPose = null;
        try {
            if (subLevel != null) subPose = subLevel.renderPose(partialTick);
        } catch (Throwable ignored) { subPose = null; }

        float maxDist = (float) dist;

        // Клип без сдвига ClipShifter (иначе перекосит проверку).
        boolean prev = LevelClipMixinState.inTiltedClip;
        LevelClipMixinState.inTiltedClip = true;
        try {
            for (int i = 0; i < 8; i++) {
                Vec3 corner = new Vec3(
                        (i & 1) * 2 - 1,
                        (i >> 1 & 1) * 2 - 1,
                        (i >> 2 & 1) * 2 - 1
                ).scale(0.1);

                Vec3 from = anchor.add(corner);
                Vec3 to = desired.add(corner);

                // 1) МИР. Sable @Overwrite'ит clip и, если начало внутри AABB контраптиона
                //    (а мы на нём стоим), проецирует точку в дальний плот — луч становится
                //    бредом. doNotProject=true → ванильный мировой клип, реально ловит рельеф.
                ClipContext ctx = new ClipContext(from, to,
                        ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player);
                ((ClipContextExtension) ctx).sable$setDoNotProject(true);
                HitResult hit = level.clip(ctx);
                if (hit.getType() != HitResult.Type.MISS) {
                    float d = (float) hit.getLocation().distanceTo(anchor);
                    if (d < maxDist) maxDist = d;
                }

                // 2) СУБЛЕВЕЛ (палуба). Переводим луч в локальную систему плота и клипаем
                //    его напрямую (doNotProject, без повторной проекции). Поза — жёсткое
                //    преобразование, поэтому расстояние в локальной системе == мировому.
                if (subPose != null) {
                    Vec3 lFrom = subPose.transformPositionInverse(from);
                    Vec3 lTo = subPose.transformPositionInverse(to);
                    ClipContext lctx = new ClipContext(lFrom, lTo,
                            ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player);
                    ((ClipContextExtension) lctx).sable$setDoNotProject(true);
                    HitResult lhit = subLevel.getLevel().clip(lctx);
                    if (lhit.getType() != HitResult.Type.MISS) {
                        float d = (float) lhit.getLocation().distanceTo(lFrom);
                        if (d < maxDist) maxDist = d;
                    }
                }
            }
        } finally {
            LevelClipMixinState.inTiltedClip = prev;
        }

        return maxDist;
    }
}