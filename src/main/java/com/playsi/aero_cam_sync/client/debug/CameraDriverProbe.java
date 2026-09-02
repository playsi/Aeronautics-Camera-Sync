package com.playsi.aero_cam_sync.client.debug;

import com.playsi.aero_cam_sync.AeroCamSync;
import com.playsi.aero_cam_sync.client.tilt.ClientTiltAccess;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.loading.FMLLoader;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Dev probe: is anyone else rotating the camera first? It compares the "vanilla" rotation captured
 * at the TAIL of {@code Camera#setup} against what the player's angles alone would give. The live
 * case is Sable's {@code rotation.premul(subLevelOrientation)} for a seated player, after which
 * tilting on top would double the sub-level rotation.
 *
 * <p>DIAGNOSTICS, not a gate. The number knows nothing of the rotation's MEANING: "already
 * in the sub-level's frame" and "nudged by recoil" look identical here. Switching off on it would let
 * any screen-shake mod silently kill ACS in someone's modpack.
 */
public final class CameraDriverProbe {

    private CameraDriverProbe() {}

    /** Static and final, so the call is dead code in a release. */
    public static final boolean DEV = !FMLLoader.isProduction();

    /** Quaternion rounding and the vanilla bob give fractions of a degree. */
    private static final float THRESHOLD_DEGREES = 0.25f;

    /** Or the log drowns while a divergence holds. */
    private static final int LOG_EVERY_FRAMES = 60;

    private static volatile float lastDegrees = 0f;
    private static int frames = 0;

    /**
     * Take a sample. Called from {@code CameraMixin} right after {@code captureVanilla}: after
     * anyone else's intervention and before the ACS one.
     *
     * @param camera      the frame's main camera
     * @param entity      the camera entity, which is where the angles come from and NOT the camera:
     *                    a foreign mod that rotated the quaternion usually rewrites
     *                    {@code camera.getYRot()} to match (Sable does), so comparing against the
     *                    camera's own fields would always agree
     * @param partialTick this frame's tick fraction
     */
    public static void sample(Camera camera, Entity entity, float partialTick) {
        if (!DEV) return;

        Quaternionf expected = new Quaternionf().rotationYXZ(
                -entity.getViewYRot(partialTick) * ((float) Math.PI / 180f),
                entity.getViewXRot(partialTick) * ((float) Math.PI / 180f),
                0f);

        // Rotation difference: how far the expected must be turned to reach the actual.
        Quaternionf delta = expected.conjugate().mul(camera.rotation(), new Quaternionf()).normalize();

        // Quaternion angle. abs(w) because q and -q are the same rotation; without it half the
        // frames would report 360 degrees instead of zero.
        float degrees = (float) Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(delta.w))));
        lastDegrees = degrees;

        if (degrees < THRESHOLD_DEGREES) {
            frames = 0;
            return;
        }
        if (!ClientTiltAccess.isDebugMessages()) return;
        if (frames++ % LOG_EVERY_FRAMES != 0) return;

        // The axis matters as much as the angle: roll about Z is almost certainly a shake mod,
        // while a rotation by the sub-level angle about a horizontal axis is almost certainly a
        // of reference.
        Vector3f axis = new Vector3f(delta.x, delta.y, delta.z);
        if (axis.lengthSquared() > 1.0e-12f) axis.normalize();

        AeroCamSync.LOGGER.info(
                "[AeroCamSync] camera already rotated by someone else: {}° about ({}, {}, {})"
                        + ", diagnostic only, we do not switch off on it",
                String.format("%.2f", degrees),
                String.format("%.2f", axis.x), String.format("%.2f", axis.y),
                String.format("%.2f", axis.z));
    }

    public static float lastDegrees() {
        return lastDegrees;
    }
}
