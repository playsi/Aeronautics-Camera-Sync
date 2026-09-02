package com.playsi.aero_cam_sync.mixins.client;

import com.playsi.aero_cam_sync.client.debug.PickDiagnostics;
import com.playsi.aero_cam_sync.client.tilt.CameraController;
import com.playsi.aero_cam_sync.client.aim.PickScope;
import com.playsi.aero_cam_sync.client.aim.RenderEyeScope;
import dev.ryanhcode.sable.ActiveSableCompanion;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The one place where ACS tells the world where the player looks from.
 * {@code getEyePositionInterpolated} is the funnel every client-side ray "from the eye" passes
 * through, so one substitution here makes foreign picks follow the crosshair.
 *
 * <p>Tilt applies everywhere; the exceptions are listed in {@link RenderEyeScope}.
 */
@Mixin(value = ActiveSableCompanion.class, remap = false)
public abstract class SableEyeMixin {

    @Inject(method = "getEyePositionInterpolated", at = @At("RETURN"), cancellable = true)
    private void aero$tiltedPickOrigin(Entity entity, float partialTicks,
                                       CallbackInfoReturnable<Vec3> cir) {
        if (RenderEyeScope.isActive()) return;

        Minecraft mc = Minecraft.getInstance();
        // Background rays from Sound Physics Perfected must not be touched (Issue #24).
        if (!mc.isSameThread()) return;
        if (entity != mc.player) return;

        Vec3 offset = CameraController.aimEyeOffset(partialTicks);
        if (offset == null) return;

        // A CORRECTION is added rather than the camera position substituted whole: Sable's eye
        // carries THIS frame's renderPose while the camera position carries the PREVIOUS frame's,
        // and substituting it moved the origin by one frame of sub-level drift.
        Vec3 sableEye = cir.getReturnValue();
        if (sableEye == null) return;

        Vec3 tilted = sableEye.add(offset);

        PickDiagnostics.recordEyeDelta(PickScope.origin(), tilted);

        PickScope.countSubstitution();
        cir.setReturnValue(tilted);
    }
}
