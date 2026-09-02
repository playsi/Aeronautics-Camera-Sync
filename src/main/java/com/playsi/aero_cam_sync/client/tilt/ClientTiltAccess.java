package com.playsi.aero_cam_sync.client.tilt;

import com.playsi.aero_cam_sync.client.aim.RenderEyeScope;
import com.playsi.aero_cam_sync.client.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * The client-side tilt provider. Served for the LOCAL player only, under the same conditions as the
 * camera tilt, so the client's prediction agrees with the server's authoritative result.
 */
public final class ClientTiltAccess {

    private ClientTiltAccess() {}

    public static boolean isModEnabled() {
        return Config.isLoaded() && Config.MOD_ENABLED.get();
    }

    /** Here because {@code Config} is client-side and common code may only reach it behind a side check. */
    public static boolean isDebugMessages() {
        return Config.isLoaded() && Config.DEBUG_MESSAGES.get();
    }

    public static boolean isRenderThread() {
        return Minecraft.getInstance().isSameThread();
    }

    /**
     * Whether the aim ray origin may be moved at all right now: exactly the conditions under which
     * the funnel adds the correction. Two sources of one correction must switch on and off
     * together, or a ray gets a tilted direction and an untilted origin.
     *
     * <p>The third-person rule must match {@code PickScopeMixin.pickOrigin} WORD FOR WORD.
     * It has drifted once, and the symptom hides: the crosshair still looks right because the
     * funnel has its correction, so only a foreign ray or {@code AcsState.aimEye} shows it.
     */
    public static boolean isAimShiftAllowed() {
        // Sable render paths that need the REAL eye (lighting, entity culling).
        if (RenderEyeScope.isActive()) return false;

        if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) return true;

        // The source clause is not belt-and-braces: sources are not gated by camera mode, so the
        // camera IS rotated there. The SHARED gate is asked rather than the camera mode, because it
        // holds a per-frame snapshot.
        return CameraController.isThirdPersonAllowed()
                || CameraController.tiltSource() != null;
    }

    /** Once per caller, or the log drowns: clip is called dozens of times per frame. */
    public static void reportClipNetCatch(java.util.function.Supplier<String> caller,
                                          double offsetLength) {
        if (!ClientTiltAccess.isDebugMessages()) return;

        String name = caller.get();
        if (!com.playsi.aero_cam_sync.ClipNet.firstTimeSeen(name)) return;

        com.playsi.aero_cam_sync.AeroCamSync.LOGGER.info(
                "[AeroCamSync] clip-net caught: {} | offset={}", name,
                String.format("%.3f", offsetLength));
    }

    /**
     * Asks {@link CameraController#rotationActive()} rather than the setting: under a foreign source
     * the setting does not cut the tilt, and reading it here splits the ray from the camera.
     */
    public static Quaternionf getClientLookTilt(Player player) {
        if (!baseAllows(player)) return null;
        if (!CameraController.rotationActive()) return null;
        return CameraController.getSmoothedTilt();
    }

    public static Quaternionf getClientPosTilt(Player player) {
        if (!baseAllows(player)) return null;
        if (!CameraController.posShiftActive()) return null;
        return CameraController.getSmoothedTilt();
    }

    /**
     * The gates must stay word-for-word those of {@link #getClientPosTilt}: rotation and offset are
     * ONE correction, and enabling them separately gives a ray from where the camera is not.
     */
    public static Vec3 getClientEyeOffset(Player player) {
        if (!baseAllows(player)) return Vec3.ZERO;
        if (!CameraController.posShiftActive()) return Vec3.ZERO;
        return CameraController.effectiveEyeOffset();
    }

    private static boolean baseAllows(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != player) return false;
        if (!Config.isLoaded()) return false;
        // Not duplicating MOD_ENABLED: shouldApplyTilt() accounts for it and for a claimed frame.
        return CameraController.shouldApplyTilt();
    }
}
