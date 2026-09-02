package com.playsi.aero_cam_sync;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerTiltStore {

    private ServerTiltStore() {}

    // Stored only for players with an active tilt.
    private static final Map<UUID, Quaternionf> tilts = new ConcurrentHashMap<>();
    // Whether camera rotation (MODIFY_CAMERA_ROT) is on: tilts the look and projectile direction.
    private static final Map<UUID, Boolean> rotFlags = new ConcurrentHashMap<>();
    // Whether the camera position shift (MODIFY_CAMERA_POS) is on: moves the launch point.
    private static final Map<UUID, Boolean> posFlags = new ConcurrentHashMap<>();
    // Whether items are dropped from the camera (DROP_FROM_CAMERA).
    private static final Map<UUID, Boolean> dropFlags = new ConcurrentHashMap<>();
    // A foreign TiltSource's eye offset: the second half of the camera pose. World space, already
    // wall-clamped on the client. Its own map rather than a field beside the quaternion, because it
    // is an independent quantity: there can be tilt without offset and offset without tilt.
    private static final Map<UUID, Vec3> eyeOffsets = new ConcurrentHashMap<>();

    public static void set(UUID playerId, Quaternionf q, boolean rotActive, boolean posShift,
                           boolean dropFromCamera, Vec3 eyeOffset) {
        tilts.put(playerId, new Quaternionf(q)); // copy!
        rotFlags.put(playerId, rotActive);
        posFlags.put(playerId, posShift);
        dropFlags.put(playerId, dropFromCamera);
        eyeOffsets.put(playerId, eyeOffset);
    }

    public static void clear(UUID playerId) {
        tilts.remove(playerId);
        rotFlags.remove(playerId);
        posFlags.remove(playerId);
        dropFlags.remove(playerId);
        eyeOffsets.remove(playerId);
    }

    /**
     * Tilt for the DIRECTION (look, projectile direction, item rays). {@code null} if camera
     * rotation is off, in which case the direction stays vanilla.
     */
    public static Quaternionf getLookTilt(UUID playerId) {
        return Boolean.TRUE.equals(rotFlags.get(playerId)) ? tilts.get(playerId) : null;
    }

    /**
     * Tilt for MOVING the projectile launch point about the player's feet. {@code null} if the
     * camera position shift is off.
     */
    public static Quaternionf getPosTilt(UUID playerId) {
        return Boolean.TRUE.equals(posFlags.get(playerId)) ? tilts.get(playerId) : null;
    }

    /**
     * The eye offset set by a foreign mod through {@code TiltSource.eyeOffset}, or
     * {@code Vec3.ZERO}.
     *
     * <p>Under the same flag as {@link #getPosTilt}: the offset moves the ray ORIGIN, exactly the
     * half that flag enables. Served with the shift off, the server would score hits from a point
     * where the client has no camera.
     */
    public static Vec3 getEyeOffset(UUID playerId) {
        if (!Boolean.TRUE.equals(posFlags.get(playerId))) return Vec3.ZERO;
        Vec3 offset = eyeOffsets.get(playerId);
        return offset == null ? Vec3.ZERO : offset;
    }

    public static boolean getDropFromCamera(UUID playerId) {
        return Boolean.TRUE.equals(dropFlags.get(playerId));
    }

    public static void onPlayerLeave(UUID playerId) {
        clear(playerId);
    }
}
