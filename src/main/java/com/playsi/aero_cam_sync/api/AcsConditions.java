package com.playsi.aero_cam_sync.api;

/**
 * Per-frame conditions a mod puts on what ACS does, and a report of what happened. Register with
 * {@link AcsHandle#addConditions}. This is what to reach for when you are not driving the tilt
 * yourself; {@code docs/tilt-control.md} has the worked examples.
 *
 * <p>Nothing here can be switched on for the session. A condition is answered per frame, so one
 * you forget to state is one you do not have, and there is no residue from the last frame, ever.
 * If your scenario is running, say so on every frame it runs on. The cost is that your predicate
 * has to be right every frame instead of once at startup; what it buys is that a mod cannot assert
 * something about the world for hours after it stopped being true, which is what the switches this
 * replaced actually did.
 *
 * <p>Both methods run on the client render thread, once per frame, in registration order, and both
 * are no-op defaults. Only registered mods are called. Do not raycast in here and do not allocate:
 * these are cheap booleans about a scenario whose state you already know.
 *
 * <p>The same object may implement {@link TiltSource}, and for a mod that does both that is the
 * recommended shape: "is my scenario running" gets answered once. {@link #conditionsFor} is called
 * on your own frames too, including the ones you go on to win, which is where taking over camera
 * collision matters most.
 */
public interface AcsConditions {

    /**
     * State your conditions for the frame about to be drawn. Say nothing and nothing is imposed.
     *
     * <p>Called before anything about the frame has been decided: before the surface raycast,
     * before any {@link TiltSource} is asked. That ordering is why {@link ConditionContext} carries
     * so little, since the answers here are what the decision is made from.
     *
     * @param context    what is known about the frame at this point; valid for this call only
     * @param conditions where to state conditions; valid for this call only
     */
    default void conditionsFor(ConditionContext context, FrameConditions conditions) {}

    /**
     * What actually happened on the frame, after the pose was applied: who drove it, what the final
     * pose was, who was skipped and by whom.
     *
     * <p>Reporting only, and too late to change anything: the camera has moved and the rays already
     * follow it. Conditions belong in {@link #conditionsFor}.
     *
     * @param report valid for this call only
     */
    default void frameResolved(FrameReport report) {}
}
