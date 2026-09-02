package com.playsi.aero_cam_sync.mixins.compat;

import dev.ryanhcode.sable.Sable;
import dev.simulated_team.simulated.content.physics_staff.PhysicsStaffItemRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Physics staff compatibility (Create Simulated): the staff beam leaves the tilted camera.
 *
 * <p>Symptom: the selection around the grabbed object is right while the beam itself goes off to
 * the side, as though the camera were not tilted. And so it is: the selection box is drawn from
 * {@code camera.getPosition()}, that is, from the tilted camera, while the beam direction comes from
 * the vanilla {@code Entity#getEyePosition(F)}:
 * <pre>
 *   dirToAnchor = globalAnchor.sub(player.getEyePosition(partialTicks)).normalize();
 * </pre>
 *
 * <p>That call is routed into Sable's funnel, which is more correct on its own (it assembles the
 * eye against the sub-level pose) and carries the tilt correction, added at its exit. No arithmetic
 * of its own is needed here.
 */
@Mixin(value = PhysicsStaffItemRenderer.class, remap = false)
public abstract class SimulatedStaffBeamMixin {

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",
                    remap = true),
            require = 0
    )
    private Vec3 aero$sableEye(Player player, float partialTick) {
        return Sable.HELPER.getEyePositionInterpolated(player, partialTick);
    }
}
