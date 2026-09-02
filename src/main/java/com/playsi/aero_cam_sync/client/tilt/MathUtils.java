package com.playsi.aero_cam_sync.client.tilt;

import org.joml.Quaterniondc;
import org.joml.Quaternionf;

public final class MathUtils {

    private MathUtils() {}

    /**
     * Converts a double-precision quaternion to a float-precision quaternion.
     */
    public static Quaternionf toQuaternionf(Quaterniondc q) {
        return new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w());
    }
}
