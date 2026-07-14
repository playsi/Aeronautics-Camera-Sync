package com.playsi.aero_cam_sync.mixins;

import com.playsi.aero_cam_sync.TiltAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Приводит луч {@code Item#getPlayerPOVHitResult} в соответствие с наклонённой камерой.
 * Этот хелпер используют вёдра жидкости и многие предметы «использовать по блоку».
 *
 * <p>Чинит две вещи разом (в т.ч. для модовых вёдер/жидкостей, использующих этот хелпер):</p>
 * <ul>
 *   <li><b>Направление</b> — {@code calculateViewVector} наклоняется под поворот камеры,
 *       иначе жидкость ставилась бы мимо перекрестия.</li>
 *   <li><b>Начало луча</b> — {@code getEyePosition} сдвигается в позицию камеры, иначе луч
 *       идёт из реального глаза и попадает в блок под другой ГРАНЬЮ → жидкость ставится на
 *       соседний блок (баг с водой на наклонном сублевеле).</li>
 * </ul>
 *
 * <p>Общий миксин — работает и на клиенте (предсказание), и на сервере (авторитет).</p>
 */
@Mixin(Item.class)
public class ItemPovTiltMixin {

    @Redirect(
            method = "getPlayerPOVHitResult",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getEyePosition()Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 aero$tiltPovOrigin(Player player) {
        Vec3 eye = player.getEyePosition();

        // На клиенте начало луча сдвигает ClipShifter (SableCompatClipMixin) — не дублируем,
        // иначе двойной сдвиг. Сервер же ClipShifter не трогает, поэтому сдвигаем тут.
        if (player.level().isClientSide) return eye;

        Quaternionf posTilt = TiltAccess.getPosTilt(player);
        if (posTilt == null) return eye;

        // Поворачиваем точку глаза вокруг ног на тилт — так же, как сдвигается камера
        // (тилт уже уменьшен по близости к стене), иначе луч разъедется с прицелом.
        Vec3 feet = player.position();
        Vector3f rel = new Vector3f(
                (float) (eye.x - feet.x),
                (float) (eye.y - feet.y),
                (float) (eye.z - feet.z));
        posTilt.transform(rel);
        return new Vec3(feet.x + rel.x, feet.y + rel.y, feet.z + rel.z);
    }

    @Redirect(
            method = "getPlayerPOVHitResult",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;calculateViewVector(FF)Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 aero$tiltPovLook(Player player, float pitch, float yaw) {
        Vec3 base = player.calculateViewVector(pitch, yaw);

        Quaternionf tilt = TiltAccess.getLookTilt(player);
        if (tilt == null) return base;

        Vector3f v = new Vector3f((float) base.x, (float) base.y, (float) base.z);
        tilt.transform(v);
        return new Vec3(v.x, v.y, v.z);
    }
}
