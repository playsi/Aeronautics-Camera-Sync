package com.playsi.aero_cam_sync.mixins;

import com.playsi.aero_cam_sync.TiltAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tilts the look direction on both sides at once.
 *
 * <p>COMMON, and must be: it has to work on a dedicated server. Not one client type belongs
 * here: only {@code TiltAccess} reaches the client package, and only inside the
 * {@code isClientSide} branch. Breaking that drops a dedicated server at handshake (Issue #33).
 */
@Mixin(value = Entity.class, priority = 1300)
public abstract class EntityLookMixin {

    @Inject(method = "getViewVector", at = @At("RETURN"), cancellable = true)
    private void aero$tiltViewVector(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        aero$applyLookTilt(cir);
    }

    @Inject(method = "getLookAngle", at = @At("RETURN"), cancellable = true)
    private void aero$tiltLookAngle(CallbackInfoReturnable<Vec3> cir) {
        aero$applyLookTilt(cir);
    }

    /** No gates here: {@link TiltAccess#getLookTilt(Player)} holds them all. */
    @Unique
    private void aero$applyLookTilt(CallbackInfoReturnable<Vec3> cir) {
        if (!((Object) this instanceof Player player)) return;

        Quaternionf tilt = TiltAccess.getLookTilt(player);
        if (tilt == null) return;

        Vec3 vanilla = cir.getReturnValue();
        Vector3f v = new Vector3f((float) vanilla.x, (float) vanilla.y, (float) vanilla.z);
        tilt.transform(v);
        cir.setReturnValue(new Vec3(v.x, v.y, v.z));
    }
}
