package com.playsi.aero_cam_sync.mixins.compat;

import com.playsi.aero_cam_sync.TiltAccess;
import dev.simulated_team.simulated.content.entities.honey_glue.HoneyGlueClientHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Honey glue (Create Simulated): the preview and hover rays, fired from the tilted camera. The net
 * cannot take these two: one passes a null entity in its {@code ClipContext}, the other clips an
 * {@code AABB} without touching {@code Level#clip}.
 *
 * <p>{@code onScroll} is left untouched: there the eye is a "camera inside the box"
 * test, not a ray, and it is the only method computing through {@code renderPose}.
 */
@Mixin(value = HoneyGlueClientHandler.class, remap = false)
public abstract class SimulatedHoneyGlueEyeMixin {

    /** No {@code ordinal}: both eye calls must move, or the ray stretches instead of translating. */
    @Redirect(
            method = {"getHitResult", "updateHovered"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getEyePosition()Lnet/minecraft/world/phys/Vec3;",
                    remap = true),
            require = 0
    )
    private Vec3 aero$tiltedEye(Player player) {
        return TiltAccess.aimEyePosition(player);
    }
}
