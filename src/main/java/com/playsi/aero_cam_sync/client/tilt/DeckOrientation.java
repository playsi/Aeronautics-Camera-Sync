package com.playsi.aero_cam_sync.client.tilt;

import com.playsi.aero_cam_sync.client.config.Config;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * Computes the camera's target "up" from the sub-level pose, without a single ray.
 *
 * <p>With {@code u = q^-1 * worldUp}, the component {@code |u_i|} of the largest local axis IS the
 * world Y of that rotated face, since {@code (q*f).worldUp = f.u}. Hence the threshold is compared
 * against {@code |u_i|} directly and no normal is built.
 *
 * <p>{@code minNormalY} must never go below 0.708. Two faces can only pass at once when the
 * threshold drops under {@code cos 45}, and then the choice becomes ambiguous; above it, exactly
 * one face passes or none does. {@code 0.8 = cos 36.87} is also where a player starts sliding off,
 * and past it the tilt is zeroed rather than capped.
 */
public final class DeckOrientation {

    private DeckOrientation() {}

    private static final Vector3f WORLD_UP = new Vector3f(0f, 1f, 0f);

    /** Unit normal in WORLD space, or {@code null} if the sub-level is too steep for any face. */
    public static @Nullable Vector3f targetUp(Pose3dc pose) {
        Quaternionf q = MathUtils.toQuaternionf(pose.orientation());

        // The pose quaternion is unit, so the conjugate is the inverse.
        Vector3f u = q.conjugate(new Quaternionf()).transform(new Vector3f(WORLD_UP));

        float ax = Math.abs(u.x);
        float ay = Math.abs(u.y);
        float az = Math.abs(u.z);

        Vector3f face;
        float cos;
        if (ay >= ax && ay >= az) {
            face = new Vector3f(0f, Math.signum(u.y), 0f);
            cos  = ay;
        } else if (ax >= az) {
            face = new Vector3f(Math.signum(u.x), 0f, 0f);
            cos  = ax;
        } else {
            face = new Vector3f(0f, 0f, Math.signum(u.z));
            cos  = az;
        }

        if (cos < Config.MIN_NORMAL_Y.get().floatValue()) return null;

        return q.transform(face);
    }
}
