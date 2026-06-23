package com.playsi.aero_cam_sync.mixins;

import com.playsi.aero_cam_sync.ServerTiltStore;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Выбрасывает предмет (клавиша Q) из наклонённой камеры, а не из головы хитбокса:
 * точка вылета поворачивается вокруг ног на тилт (как сдвиг камеры), а направление
 * броска наклоняется под поворот камеры. Гейтится отдельной настройкой
 * {@code DROP_FROM_CAMERA} (синхронизируется через {@link ServerTiltStore}).
 *
 * <p>Только направленный бросок ({@code dropAround == false}); случайный разброс при
 * смерти не трогаем. Сущность создаётся на сервере, поэтому достаточно серверного тилта.</p>
 */
@Mixin(Player.class)
public abstract class PlayerDropTiltMixin {

    @Inject(
            method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("RETURN")
    )
    private void aero$dropFromCamera(ItemStack stack, boolean dropAround, boolean includeThrowerName,
                                     CallbackInfoReturnable<ItemEntity> cir) {
        if (dropAround) return;

        Player self = (Player) (Object) this;
        if (!(self instanceof ServerPlayer sp)) return;
        if (!ServerTiltStore.getDropFromCamera(sp.getUUID())) return;

        ItemEntity item = cir.getReturnValue();
        if (item == null) return;

        Quaternionf posTilt = ServerTiltStore.getPosTilt(sp.getUUID());
        Quaternionf lookTilt = ServerTiltStore.getLookTilt(sp.getUUID());
        if (posTilt == null && lookTilt == null) return;

        // Точка вылета — поворот вокруг ног (совпадает со сдвигом камеры).
        if (posTilt != null) {
            Vec3 feet = self.position();
            Vec3 pos = item.position();
            Vector3f rel = new Vector3f(
                    (float) (pos.x - feet.x),
                    (float) (pos.y - feet.y),
                    (float) (pos.z - feet.z));
            posTilt.transform(rel);
            item.setPos(feet.x + rel.x, feet.y + rel.y, feet.z + rel.z);
        }

        // Направление броска — наклон под поворот камеры.
        if (lookTilt != null) {
            Vec3 dm = item.getDeltaMovement();
            Vector3f v = new Vector3f((float) dm.x, (float) dm.y, (float) dm.z);
            lookTilt.transform(v);
            item.setDeltaMovement(v.x, v.y, v.z);
        }
    }
}
