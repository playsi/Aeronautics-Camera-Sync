package com.playsi.aero_cam_sync.mixins;

import com.playsi.aero_cam_sync.ServerTiltStore;
import com.playsi.aero_cam_sync.TiltAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tilts the launch direction of projectiles (snowballs, eggs, tridents) to match the player's
 * camera tilt. {@code shootFromRotation} computes the direction straight from {@code xRot/yRot},
 * bypassing {@code getViewVector}, so the look tilt does not reach it.
 *
 * <p>A player's projectile is created on the server, so the server-side tilt from
 * {@link ServerTiltStore} is enough. Non-players (dispensers, mobs) have no tilt and are
 * unaffected.
 */
@Mixin(Projectile.class)
public abstract class ProjectileShootTiltMixin {

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    @Inject(method = "shootFromRotation", at = @At("HEAD"), cancellable = true)
    private void aero$tiltShootFromRotation(Entity shooter, float x, float y, float z,
                                            float velocity, float inaccuracy, CallbackInfo ci) {
        if (!(shooter instanceof ServerPlayer sp)) return;

        // Two independent flags: camera rotation (direction) and position shift (launch point).
        Quaternionf lookTilt = ServerTiltStore.getLookTilt(sp.getUUID());
        Quaternionf posTilt  = ServerTiltStore.getPosTilt(sp.getUUID());
        if (lookTilt == null && posTilt == null) return; // nothing to change, let vanilla run

        Projectile self = (Projectile) (Object) this;

        // Spawn the projectile from the shifted camera point, so it visibly leaves the crosshair
        // rather than the side. The formula is shared (TiltAccess.cameraAnchoredPos): rotation
        // about the feet PLUS a foreign source's eye offset. A local copy here already cost a bug -
        // projectiles flew from the vanilla point while the pick left from the shifted one.
        if (posTilt != null) {
            Vec3 spawn = TiltAccess.cameraAnchoredPos(sp, self.position(), posTilt);
            self.setPos(spawn.x, spawn.y, spawn.z);
        }

        // The vanilla direction from the rotation angles, as in Projectile#shootFromRotation.
        float f  = -Mth.sin(y * DEG_TO_RAD) * Mth.cos(x * DEG_TO_RAD);
        float f1 = -Mth.sin((x + z) * DEG_TO_RAD);
        float f2 =  Mth.cos(y * DEG_TO_RAD) * Mth.cos(x * DEG_TO_RAD);

        Vector3f dir = new Vector3f(f, f1, f2);
        // The direction is tilted only when camera rotation is on.
        if (lookTilt != null) lookTilt.transform(dir);

        self.shoot(dir.x, dir.y, dir.z, velocity, inaccuracy);

        // The shooter's inherited velocity is added WITHOUT the tilt, as vanilla does.
        Vec3 inherited = shooter.getKnownMovement();
        self.setDeltaMovement(self.getDeltaMovement()
                .add(inherited.x, shooter.onGround() ? 0.0 : inherited.y, inherited.z));

        ci.cancel();
    }
}
