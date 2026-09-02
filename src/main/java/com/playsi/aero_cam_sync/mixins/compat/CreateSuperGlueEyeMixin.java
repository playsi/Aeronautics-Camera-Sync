package com.playsi.aero_cam_sync.mixins.compat;

import com.playsi.aero_cam_sync.TiltAccess;
import com.simibubi.create.content.contraptions.glue.SuperGlueSelectionHandler;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Super glue (Create): selecting already-placed glue, from the tilted camera. Its own ray pairs a
 * vanilla origin with a globally tilted direction, and the net cannot take it because it is
 * an {@code AABB#clip} that never reaches {@code Level#clip}. Sable already handles the sub-level
 * part, so the ray origin is the only thing ACS has to supply.
 *
 * <p>NOT fixed: automatic gluing on block placement. That clips a {@code catnip
 * RayTraceLevel}, which is not a {@code Level}, so it is past both the net and Sable's overwrite:
 * that path is already broken on a sub-level without ACS.
 */
@Mixin(value = SuperGlueSelectionHandler.class, remap = false)
public abstract class CreateSuperGlueEyeMixin {

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getEyePosition()Lnet/minecraft/world/phys/Vec3;",
                    remap = true),
            require = 0
    )
    private Vec3 aero$tiltedEye(LocalPlayer player) {
        return TiltAccess.aimEyePosition(player);
    }
}
