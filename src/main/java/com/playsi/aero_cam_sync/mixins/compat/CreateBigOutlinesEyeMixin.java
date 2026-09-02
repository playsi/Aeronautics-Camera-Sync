package com.playsi.aero_cam_sync.mixins.compat;

import com.simibubi.create.foundation.block.BigOutlines;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Create's "big outlines" pick OVERWRITES {@code mc.hitResult}, so when aiming at a track the final
 * result comes from Create, and its ray paired a vanilla origin with a globally tilted direction.
 * The vanilla eye call is routed into Sable's funnel, which supplies both the sub-level pose and
 * the ACS correction.
 *
 * <p>The net cannot replace this, verified by experiment: the path never calls
 * {@code Level#clip}, since {@code rayTraceUntil} walks blocks itself. Deleting this mixin as a
 * control case brought the track symptom straight back.
 */
@Mixin(value = BigOutlines.class, remap = false)
public abstract class CreateBigOutlinesEyeMixin {

    @Redirect(
            method = "pick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getEyePosition(F)Lnet/minecraft/world/phys/Vec3;",
                    remap = true),
            require = 0
    )
    private static Vec3 aero$sableEye(LocalPlayer player, float partialTick) {
        return Sable.HELPER.getEyePositionInterpolated(player, partialTick);
    }
}
