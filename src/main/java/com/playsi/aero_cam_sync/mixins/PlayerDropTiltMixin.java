package com.playsi.aero_cam_sync.mixins;

import com.playsi.aero_cam_sync.ServerTiltStore;
import com.playsi.aero_cam_sync.TiltAccess;
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
 * Drops an item (the Q key) from the tilted camera rather than the hitbox head: the launch point is
 * rotated about the feet by the tilt, as the camera shift is, and the throw direction is tilted to
 * match the camera rotation. Gated by its own {@code DROP_FROM_CAMERA} setting, synced through
 * {@link ServerTiltStore}.
 *
 * <p>Aimed throws only ({@code dropAround == false}); the random scatter on death is left alone.
 * The entity is created on the server, so the server-side tilt is enough.
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

        // The launch point is the camera's: rotation about the feet plus a foreign source's eye
        // offset. Shared formula (TiltAccess.cameraAnchoredPos): a local copy here already
        // diverged from the pick once the correction gained its second term.
        if (posTilt != null) {
            Vec3 pos = TiltAccess.cameraAnchoredPos(sp, item.position(), posTilt);
            item.setPos(pos.x, pos.y, pos.z);
        }

        // The throw direction, tilted to match the camera rotation.
        if (lookTilt != null) {
            Vec3 dm = item.getDeltaMovement();
            Vector3f v = new Vector3f((float) dm.x, (float) dm.y, (float) dm.z);
            lookTilt.transform(v);
            item.setDeltaMovement(v.x, v.y, v.z);
        }
    }
}
