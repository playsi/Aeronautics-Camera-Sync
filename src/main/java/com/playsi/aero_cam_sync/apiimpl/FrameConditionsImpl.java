package com.playsi.aero_cam_sync.apiimpl;

import com.playsi.aero_cam_sync.api.FrameConditions;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Collector for one frame's conditions. One instance per frame, walked by every subscriber.
 *
 * <p>{@link #owner} says who is answering right now, set by {@link Conditions#collect} before each
 * call. It exists for exactly one thing: the skip log line must name BOTH parties, who skipped and
 * whom, or "my source stopped winning" cannot be diagnosed at all. The object is handed to foreign
 * code, but {@code owner} is invisible to it: the {@link FrameConditions} interface does not carry
 * it, so nobody can forge their own name.
 *
 * <p>Not thread-safe, and must not be: it lives inside one {@code collect} call on the render
 * thread.
 */
final class FrameConditionsImpl implements FrameConditions {

    boolean baselineSkipped = false;
    boolean thirdPerson = false;
    boolean collisionTakenOver = false;

    /** Empty on the vast majority of frames; the set is created only when there is something. */
    Set<String> skipped = null;

    private String owner = "?";

    void owner(String modId) {
        this.owner = modId;
    }

    @Override
    public void skipBaseline(String reason) {
        check(reason, "skipBaseline");
        baselineSkipped = true;
        ApiLog.event(owner, "baseline skipped: {}", reason);
    }

    @Override
    public void skip(String modId, String reason) {
        check(reason, "skip");
        if (modId == null || modId.isBlank()) {
            throw new IllegalArgumentException(
                    "FrameConditions.skip: modId must not be null or blank");
        }
        if (skipped == null) skipped = new LinkedHashSet<>(2);
        skipped.add(modId);

        // The values go into the format string, against ApiLog's usual rule: the
        // dedup key there is the format WITHOUT arguments, so a mod skipping two sources would see
        // one line out of two. The log cannot drown, because the set of pairs is finite.
        ApiLog.event(owner, "skipped tilt source of " + modId + ": " + reason);
    }

    @Override
    public void baselineInThirdPerson(String reason) {
        check(reason, "baselineInThirdPerson");
        thirdPerson = true;
        ApiLog.event(owner, "baseline allowed in third person: {}", reason);
    }

    @Override
    public void takeOverCameraCollision(String reason) {
        check(reason, "takeOverCameraCollision");
        collisionTakenOver = true;
        ApiLog.event(owner, "camera collision taken over: {}", reason);
    }

    /**
     * An empty reason is a programmer error, not a user one, and must fail loudly: the reason is
     * the only thing in the log that answers "why did my camera behave differently".
     */
    private static void check(String reason, String method) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "FrameConditions." + method + ": reason must not be null or blank"
                    + ", it is the only thing that answers \"why\" in a log");
        }
    }
}
