package com.playsi.aero_cam_sync;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The single entry point for a player's tilt, on both sides. The {@code ClientTiltAccess} reference
 * only executes in the {@code isClientSide} branch, so a dedicated server never loads it.
 */
public final class TiltAccess {

    private TiltAccess() {}

    /**
     * One constant for two definitions: let the net and {@code tiltApplied()} drift
     * and a mod gets {@code false} on a ray ACS is still moving.
     */
    public static final double EPSILON_SQR = 1.0e-8;

    /** Tied to camera rotation. */
    public static Quaternionf getLookTilt(Player player) {
        if (player == null) return null;
        if (player.level().isClientSide) {
            return com.playsi.aero_cam_sync.client.tilt.ClientTiltAccess.getClientLookTilt(player);
        }
        if (player instanceof ServerPlayer sp) {
            return ServerTiltStore.getLookTilt(sp.getUUID());
        }
        return null;
    }

    /** Tied to the camera position shift. */
    public static Quaternionf getPosTilt(Player player) {
        if (player == null) return null;
        if (player.level().isClientSide) {
            return com.playsi.aero_cam_sync.client.tilt.ClientTiltAccess.getClientPosTilt(player);
        }
        if (player instanceof ServerPlayer sp) {
            return ServerTiltStore.getPosTilt(sp.getUUID());
        }
        return null;
    }

    /**
     * The second half of the correction, already wall-scaled. Mirrors {@link #getPosTilt}
     * on purpose: rotation and offset are terms of ONE correction, and enabling them separately
     * leaves the ray starting where the camera is not.
     */
    public static Vec3 sourceEyeOffset(Player player) {
        if (player == null) return Vec3.ZERO;
        if (player.level().isClientSide) {
            return com.playsi.aero_cam_sync.client.tilt.ClientTiltAccess.getClientEyeOffset(player);
        }
        if (player instanceof ServerPlayer sp) {
            return ServerTiltStore.getEyeOffset(sp.getUUID());
        }
        return Vec3.ZERO;
    }

    /**
     * A DELTA, not an absolute position: foreign code assembles its own eye its own way, and adding
     * to it preserves that computation while fixing only the tilt. The client-side
     * {@code CameraController#aimEyeOffset(float)} does the same for render paths.
     */
    public static Vec3 aimEyeOffset(Player player) {
        if (player == null) return null;

        if (player.level().isClientSide) {
            // The same exceptions as the funnel, or the net moves rays the funnel leaves alone.
            if (!com.playsi.aero_cam_sync.client.tilt.ClientTiltAccess.isAimShiftAllowed()) {
                return null;
            }
        }

        Quaternionf posTilt = getPosTilt(player);
        if (posTilt == null) return null;

        // Logical pose, no partialTick: this path serves the tick and the server.
        return eyeRotationDelta(player.getEyePosition(), player.position(), posTilt)
                .add(sourceEyeOffset(player));
    }

    /**
     * The ONE copy of the eye-shift arithmetic. Keep it that way: three copies once made an audit
     * unreliable by construction. It takes ready {@code eye} and {@code feet} because callers
     * differ in TIME (interpolated frame pose versus logical tick pose), which is a real
     * difference; the formula is not.
     */
    public static Vec3 eyeRotationDelta(Vec3 eye, Vec3 feet, Quaternionf posTilt) {
        Vector3f rel = new Vector3f(
                (float) (eye.x - feet.x),
                (float) (eye.y - feet.y),
                (float) (eye.z - feet.z));
        Vector3f tilted = posTilt.transform(new Vector3f(rel));
        return new Vec3(tilted.x - rel.x, tilted.y - rel.y, tilted.z - rel.z);
    }

    /**
     * A point RIGIDLY TIED TO THE CAMERA: projectile spawn, item drop. Both halves of the
     * correction, exactly as the camera applies them. Private copies of this in the server mixins
     * once kept only the first, and projectiles flew from the vanilla point.
     *
     * @param posTilt the position tilt the caller already obtained
     */
    public static Vec3 cameraAnchoredPos(Player player, Vec3 point, Quaternionf posTilt) {
        Vec3 feet = player.position();
        return point.add(eyeRotationDelta(point, feet, posTilt))
                .add(sourceEyeOffset(player));
    }

    public static Vec3 aimEyePosition(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 offset = aimEyeOffset(player);
        return offset == null ? eye : eye.add(offset);
    }
}
