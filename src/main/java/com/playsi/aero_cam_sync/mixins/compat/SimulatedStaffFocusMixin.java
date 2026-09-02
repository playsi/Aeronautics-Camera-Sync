package com.playsi.aero_cam_sync.mixins.compat;

import com.playsi.aero_cam_sync.client.tilt.CameraController;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffClientHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The physics staff's ray origin, taken from the tilted camera. Simulated reconstructs the camera as
 * "feet plus eye height", which holds only while the camera is not displaced, and the ACS camera is
 * the feet, so the staff tip stays at the vanilla point while the selection box is right.
 *
 * <p>The RESULT is corrected, not the terms: one of them carries FOV and rotation and another the
 * crouch eye-height smoothing, which must not be disturbed.
 *
 * <p>Not {@link SimulatedStaffBeamMixin}: that fixes WHERE the staff model points, this one
 * WHERE the ray leaves from. Both are needed.
 *
 * <p>First person is checked EXPLICITLY, not left to {@code aimEyeOffset} returning
 * {@code null}: since third person became an API switch, a mod enabling it would silently get the
 * correction in a branch that computes from the body, not the camera.
 */
@Mixin(value = PhysicsStaffClientHandler.class, remap = false)
public abstract class SimulatedStaffFocusMixin {

    @Inject(method = "getStaffFocusPos", at = @At("RETURN"), cancellable = true)
    private static void aero$tiltFocusPos(Player player, boolean mainHand, float pt,
                                          CallbackInfoReturnable<Vec3> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (player != mc.player) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;

        Vec3 offset = CameraController.aimEyeOffset(pt);
        if (offset == null) return;

        Vec3 focus = cir.getReturnValue();
        if (focus == null) return;

        cir.setReturnValue(focus.add(offset));
    }
}
