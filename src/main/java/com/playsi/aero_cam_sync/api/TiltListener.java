package com.playsi.aero_cam_sync.api;

import java.util.List;

/**
 * Notifications about the tilt turning on, off, and being suppressed.
 *
 * <p>Register with {@link AcsHandle#addListener(TiltListener)}. Every method has a default
 * implementation, so override only what you need.
 *
 * <p>Events fire on transitions, not per frame: stepping onto a sub-level gives exactly one
 * {@link #onTiltStart}, stepping off gives exactly one {@link #onTiltStop}.
 *
 * <p>There are no sub-level events here: which sub-level the player is on is
 * Sable's business, and the current one is available from
 * {@link AcsClientState#tiltSubLevel()}.
 */
public interface TiltListener {

    /** The tilt has just become significant. */
    default void onTiltStart(AcsState state) {}

    /**
     * The tilt has just eased back to level.
     *
     * <p>Fires when the residual tilt drops below the threshold, so it comes some frames
     * <i>after</i> the cause (leaving the sub-level, a suppression lease), not at that moment.
     */
    default void onTiltStop(AcsState state) {}

    /**
     * A suppression lease was taken or dropped, including by expiry.
     *
     * @param suppressed whether the tilt is suppressed after this change
     * @param by         mod ids currently holding a lease; empty when {@code suppressed} is false
     */
    default void onSuppressionChanged(boolean suppressed, List<String> by) {}
}
