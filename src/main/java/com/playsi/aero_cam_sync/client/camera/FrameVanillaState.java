package com.playsi.aero_cam_sync.client.camera;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * This frame's vanilla main-camera pose, captured BEFORE the tilt.
 *
 * <p>Stored, not reconstructed: rotating back by the current quaternion need not undo what
 * was applied at write time, because the applied tilt is scaled by {@code wallScale} and that
 * changes during the frame.
 *
 * <p>The frame counter exists because {@code Camera#setup} happens late while the snapshot may be
 * asked for earlier, where the honest answer is "last frame's value".
 */
public final class FrameVanillaState {

    private FrameVanillaState() {}

    private static long currentFrame = 0L;
    private static long capturedFrame = -1L;

    private static Vec3 pos = Vec3.ZERO;
    private static final Quaternionf rot = new Quaternionf();

    public static void beginFrame() {
        currentFrame++;
    }

    public static void capture(Camera camera) {
        pos = camera.getPosition();
        rot.set(camera.rotation());
        capturedFrame = currentFrame;
    }

    public static boolean hasValue() {
        return capturedFrame >= 0;
    }

    public static boolean isFresh() {
        return capturedFrame == currentFrame;
    }

    public static Vec3 pos() {
        return pos;
    }

    public static Quaternionf rot() {
        return new Quaternionf(rot);
    }

    /** A previous-frame value from another world is worse than none. */
    public static void reset() {
        capturedFrame = -1L;
        pos = Vec3.ZERO;
        rot.identity();
    }
}
