package com.playsi.aero_cam_sync.mixins;

import com.playsi.aero_cam_sync.ServerTiltStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Наклоняет направление вылета снарядов (снежки, яйца, тридент и т.п.) под наклон
 * камеры игрока. {@code shootFromRotation} считает направление напрямую из
 * {@code xRot/yRot}, минуя {@code getViewVector}, поэтому серверный наклон взгляда
 * ({@link ServerEntityLookMixin}) сюда не достаёт.
 *
 * <p>Снаряд игрока создаётся на сервере, поэтому достаточно серверного тилта из
 * {@link ServerTiltStore}. Для не-игроков (раздатчики, мобы) тилта нет — поведение
 * не меняется.</p>
 */
@Mixin(Projectile.class)
public abstract class ProjectileShootTiltMixin {

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    @Inject(method = "shootFromRotation", at = @At("HEAD"), cancellable = true)
    private void aero$tiltShootFromRotation(Entity shooter, float x, float y, float z,
                                            float velocity, float inaccuracy, CallbackInfo ci) {
        if (!(shooter instanceof ServerPlayer sp)) return;

        // Два независимых флага: поворот камеры (направление) и сдвиг позиции (точка вылета).
        Quaternionf lookTilt = ServerTiltStore.getLookTilt(sp.getUUID());
        Quaternionf posTilt  = ServerTiltStore.getPosTilt(sp.getUUID());
        if (lookTilt == null && posTilt == null) return; // нечего менять — пусть работает ваниль

        Projectile self = (Projectile) (Object) this;

        // Спавним снаряд из «сдвинутой» точки камеры (вокруг ног поворачиваем точку
        // вылета на тот же тилт), чтобы он визуально летел из прицела, а не сбоку.
        if (posTilt != null) {
            Vec3 feet = shooter.position();
            Vec3 spawn = self.position();
            Vector3f rel = new Vector3f(
                    (float) (spawn.x - feet.x),
                    (float) (spawn.y - feet.y),
                    (float) (spawn.z - feet.z));
            posTilt.transform(rel);
            self.setPos(feet.x + rel.x, feet.y + rel.y, feet.z + rel.z);
        }

        // Ванильное направление из углов поворота (как в Projectile#shootFromRotation).
        float f  = -Mth.sin(y * DEG_TO_RAD) * Mth.cos(x * DEG_TO_RAD);
        float f1 = -Mth.sin((x + z) * DEG_TO_RAD);
        float f2 =  Mth.cos(y * DEG_TO_RAD) * Mth.cos(x * DEG_TO_RAD);

        Vector3f dir = new Vector3f(f, f1, f2);
        // Направление наклоняем только если включён поворот камеры.
        if (lookTilt != null) lookTilt.transform(dir);

        self.shoot(dir.x, dir.y, dir.z, velocity, inaccuracy);

        // Унаследованную скорость стрелка добавляем БЕЗ наклона (как ваниль).
        Vec3 inherited = shooter.getKnownMovement();
        self.setDeltaMovement(self.getDeltaMovement()
                .add(inherited.x, shooter.onGround() ? 0.0 : inherited.y, inherited.z));

        ci.cancel();
    }
}
