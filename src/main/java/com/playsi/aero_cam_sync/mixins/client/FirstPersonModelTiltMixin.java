package com.playsi.aero_cam_sync.mixins.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.tilt.CameraController;
import com.playsi.aero_cam_sync.client.compat.FirstPersonCompat;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Compatibility with the First-person Model mod (tr7zw).
 *
 * <p>The mod renders the first-person body with the ordinary {@link PlayerRenderer}, shifted behind
 * the camera. The ACS camera rotates about the feet while the body stays upright, and the head comes
 * away. Here the body is rotated by the same smoothed tilt about the same pivot, so the head
 * returns under the camera and the body looks like it stands on a sloping surface.
 *
 * <p>Fires STRICTLY while First-person Model is actually rendering the first-person body
 * ({@link FirstPersonCompat#isRenderingFirstPersonBody()}): other players and third person are
 * untouched. Without the mod installed this mixin is a no-op.
 */
@Mixin(PlayerRenderer.class)
public abstract class FirstPersonModelTiltMixin {

    @Inject(method = "setupRotations", at = @At("HEAD"))
    private void aero$tiltFirstPersonBody(AbstractClientPlayer entity, PoseStack poseStack,
                                          float bob, float yBodyRot, float partialTick, float scale,
                                          CallbackInfo ci) {
        // Only while First-person Model is rendering the first-person body.
        if (!FirstPersonCompat.isRenderingFirstPersonBody()) return;

        // The body is tilted only when the camera POSITION shifts: that shift is what moves the
        // camera off the eyes and detaches the head, and the same rotation about the feet brings it
        // back. With rotation alone the camera stays at the eyes and the body must stay upright, or
        // the head comes away instead.
        if (!Config.MODIFY_CAMERA_POS.get()) return;

        // Config.MOD_ENABLED is not duplicated: shouldApplyTilt() already accounts for it and for
        // a source that claimed the frame.
        if (!CameraController.shouldApplyTilt()) return;

        // The same effective tilt the camera shift uses, wall attenuation included, so the head
        // tracks the camera frame for frame.
        Quaternionf tilt = CameraController.getSmoothedTilt();
        poseStack.mulPose(tilt);
    }
}
