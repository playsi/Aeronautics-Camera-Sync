package com.playsi.aero_cam_sync.apiimpl;

import com.playsi.aero_cam_sync.api.TiltContext;
import com.playsi.aero_cam_sync.api.TiltSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The registry of tilt sources. One mod wins a whole frame rather than values being combined:
 * everything downstream reads one tilt, and two mods editing it in turn give a hybrid pose.
 * "Layer mine on top of theirs" is expressed through {@code TiltContext.acsTilt()} instead.
 *
 * <p>ACS is not in the list. It is the baseline and not a party to the dispute;
 * an entry with minimum priority would make it possible to register BELOW it and never fire.
 *
 * <p>Leaving the world does NOT reset this: registration is a property of the mod, unlike frame
 * conditions, which do not survive even one frame.
 */
public final class TiltSources {

    private TiltSources() {}

    /**
     * Both halves in one record: returning them apart would allow a frame where one mod's offset
     * combined with another's tilt.
     *
     * @param eyeOffset world-space and RAW; {@code CameraController} applies the wall clamp
     */
    public record Winner(String modId, Quaternionf tilt, Vec3 eyeOffset) {}


    private record Registered(String modId, int priority, long order, TiltSource source) {}

    /**
     * Priority descending, registration order breaking ties. Copy-on-write, and sorted here rather
     * than in {@link #resolve}: registration is rare, reads happen every frame on the render thread.
     */
    private static volatile List<Registered> sources = List.of();

    /** Breaks priority ties. Under the same lock as the write. */
    private static long registrations = 0L;

    public static synchronized void add(String modId, int priority, TiltSource source) {
        List<Registered> next = new ArrayList<>(sources);
        next.add(new Registered(modId, priority, registrations++, source));
        next.sort(Comparator.comparingInt(Registered::priority).reversed()
                .thenComparingLong(Registered::order));
        sources = List.copyOf(next);
    }

    public static boolean isEmpty() {
        return sources.isEmpty();
    }

    /**
     * The first source to claim the frame wins; the rest are NOT polled, since priority already
     * answers who outranks whom.
     *
     * @return the winner, or {@code null} to leave the tilt with ACS
     */
    @Nullable
    public static Winner resolve(Player player, float partialTick, float deltaTicks,
                                 @Nullable Vector3f surfaceNormal, Quaternionf acsTilt,
                                 boolean firstPerson, Vec3 vanillaCameraPos, Vec3 feet) {
        List<Registered> list = sources;
        if (list.isEmpty()) return null;

        TiltContext context = new TiltContextImpl(
                player, partialTick, deltaTicks, surfaceNormal, acsTilt, firstPerson,
                vanillaCameraPos, feet);

        for (Registered r : list) {
            // BEFORE the predicate: a skipped source is not asked at all this frame.
            if (Conditions.isSkipped(r.modId())) continue;

            boolean claims;
            try {
                claims = r.source().appliesTo(context);
            } catch (Throwable t) {
                // Never propagate: a foreign bug must not become a black screen.
                ApiLog.warn(r.modId(), "tilt source predicate threw, treated as declined: {}",
                        String.valueOf(t));
                continue;
            }
            if (!claims) continue;

            Quaternionf tilt;
            try {
                tilt = r.source().tilt(context);
            } catch (Throwable t) {
                ApiLog.warn(r.modId(), "tilt source threw, frame passed to the next source: {}",
                        String.valueOf(t));
                continue;
            }

            if (tilt == null) {
                ApiLog.warn(r.modId(), "tilt source claimed the frame but returned null"
                        + ": appliesTo and tilt disagree; frame passed to the next source");
                continue;
            }

            // A zero quaternion gives NaN on normalize(), and NaN in the tilt is a vanished world.
            float lengthSqr = tilt.lengthSquared();
            if (!Float.isFinite(lengthSqr) || lengthSqr < 1.0e-12f) {
                ApiLog.warn(r.modId(), "tilt source returned a degenerate quaternion,"
                        + " frame passed to the next source");
                continue;
            }

            ApiLog.event(r.modId(), "tilt source took the frame (priority {})", r.priority());

            return new Winner(r.modId(), new Quaternionf(tilt).normalize(),
                    eyeOffsetOf(r, context));
        }

        return null;
    }

    /**
     * Asked ONLY of the winner and only once its tilt is accepted, or a loser's offset ends up on
     * someone else's tilt. Any failure here means zero, never passing the frame on: the tilt is
     * already applied, and moving on now would apply two tilts in one frame.
     */
    private static Vec3 eyeOffsetOf(Registered r, TiltContext context) {
        Vec3 offset;
        try {
            offset = r.source().eyeOffset(context);
        } catch (Throwable t) {
            ApiLog.warn(r.modId(), "eyeOffset() threw, treated as zero: {}", String.valueOf(t));
            return Vec3.ZERO;
        }

        // null is a legitimate "no offset": declining the frame is no longer an option here.
        if (offset == null) return Vec3.ZERO;

        if (!Double.isFinite(offset.x) || !Double.isFinite(offset.y) || !Double.isFinite(offset.z)) {
            ApiLog.warn(r.modId(), "eyeOffset() returned a non-finite vector, treated as zero");
            return Vec3.ZERO;
        }

        double length = offset.length();
        if (length <= EyeOffsetClamp.MAX_BLOCKS) return offset;

        // Asked ONLY when the ceiling is exceeded, so the modder need not keep it cheap.
        boolean deliberate;
        try {
            deliberate = r.source().eyeOffsetIsDeliberate(context);
        } catch (Throwable t) {
            // The default must be the safe one.
            ApiLog.warn(r.modId(), "eyeOffsetIsDeliberate() threw, the clamp is applied: {}",
                    String.valueOf(t));
            deliberate = false;
        }

        // The value goes into the format string, not into an argument: ApiLog deduplicates on
        // format without arguments, so a placeholder would give one line for the whole session.
        ApiLog.warn(r.modId(), "eyeOffset() of " + String.format("%.2f", length)
                + " blocks is larger than " + EyeOffsetClamp.MAX_BLOCKS
                + (deliberate
                    ? ": NOT clamped, eyeOffsetIsDeliberate() said it is meant;"
                      + " interaction reach follows the eye"
                    : ": clamped, since interaction reach follows the eye;"
                      + " return true from eyeOffsetIsDeliberate() if you mean it"));

        return EyeOffsetClamp.apply(deliberate, offset, length);
    }
}
