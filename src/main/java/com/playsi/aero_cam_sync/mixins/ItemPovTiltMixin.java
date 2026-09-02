package com.playsi.aero_cam_sync.mixins;

import com.playsi.aero_cam_sync.TiltAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Tilts the {@code Item#getPlayerPOVHitResult} direction, the helper fluid buckets and "use on
 * block" items go through. Common: client prediction and server authority both need it.
 */
@Mixin(Item.class)
public class ItemPovTiltMixin {

    // The ORIGIN is left to the net: this path ends in a vanilla level.clip() whose origin is
    // exactly player.getEyePosition(), so it matches the net's filter.

    @Redirect(
            method = "getPlayerPOVHitResult",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;calculateViewVector(FF)Lnet/minecraft/world/phys/Vec3;")
    )
    private static Vec3 aero$tiltPovLook(Player player, float pitch, float yaw) {
        Vec3 base = player.calculateViewVector(pitch, yaw);

        Quaternionf tilt = TiltAccess.getLookTilt(player);
        if (tilt == null) return base;

        Vector3f v = new Vector3f((float) base.x, (float) base.y, (float) base.z);
        tilt.transform(v);
        return new Vec3(v.x, v.y, v.z);
    }
}
