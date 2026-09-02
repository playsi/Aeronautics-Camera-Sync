package com.playsi.aero_cam_sync.mixins;

import com.playsi.aero_cam_sync.TiltAccess;
import com.simibubi.create.foundation.utility.RaycastHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Brings Create's rays ({@link RaycastHelper}) into line with the tilted camera.
 *
 * <p>Create computes its ray from {@code getEyePosition()} and raw {@code xRot/yRot} rather than
 * from the ACS camera, so under tilt its ray diverges from the crosshair: the redstone link handler,
 * for instance, misses the frequency slot, does not cancel the event, and a block is placed on top
 * instead of the frequency being set. It happens with rotation alone and with the shift alone.
 *
 * <p>Both halves are fixed as everywhere else: the ORIGIN is moved to the camera position and the
 * DIRECTION is tilted. A common mixin, working on the client (hint and prediction) and on the
 * server (the right-click event arrives on both sides). With no tilt ({@link TiltAccess} returned
 * null) the behaviour is vanilla.
 */
@Mixin(value = RaycastHelper.class, remap = false)
public class CreateRaycastTiltMixin {

    // The ray ORIGIN is no longer fixed here: rayTraceRange ends in a vanilla level.clip() whose
    // origin equals player.getEyePosition(), which is exactly what the net (ClipNetMixin) should
    // take. The direction stays, because getTraceTarget computes it from RAW getXRot/getYRot and
    // the net never sees that.

    /** Ray direction, tilted to match the camera (about the given {@code origin}). */
    @Inject(method = "getTraceTarget", at = @At("RETURN"), cancellable = true)
    private static void aero$cameraDirection(Player player, double range, Vec3 origin,
                                             CallbackInfoReturnable<Vec3> cir) {
        Quaternionf lookTilt = TiltAccess.getLookTilt(player);
        if (lookTilt == null) return;

        Vec3 dir = cir.getReturnValue().subtract(origin);
        Vector3f v = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        lookTilt.transform(v);
        cir.setReturnValue(origin.add(v.x, v.y, v.z));
    }
}
