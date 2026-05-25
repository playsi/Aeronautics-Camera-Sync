package com.playsi.aero_cam_sync.client.utils;

import net.minecraft.core.Direction;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class MathUtils {

    private MathUtils() {}

    /**
     * Converts {@link Direction} into a unit vector.
     */
    public static Vector3f directionToVector(Direction direction) {
        return new Vector3f(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    /**
     * Rotates the local vector into world space using the pose quaternion.
     */
    public static Vector3f transformToWorldSpace(Vector3f localVector, Quaterniondc orientation) {
        return toQuaternionf(orientation).transform(localVector);
    }

    /**
     * Converts a double-precision quaternion to a float-precision quaternion.
     */
    public static Quaternionf toQuaternionf(Quaterniondc q) {
        return new Quaternionf((float) q.x(), (float) q.y(), (float) q.z(), (float) q.w());
    }
}