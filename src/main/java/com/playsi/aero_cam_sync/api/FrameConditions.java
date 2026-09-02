package com.playsi.aero_cam_sync.api;

/**
 * The conditions one mod puts on one frame. Handed to {@link AcsConditions#conditionsFor} and valid
 * for that call only.
 *
 * <p>Every registered mod is asked for its conditions once, before resolution begins. The skips
 * they name are collected into one set, and then the source stack is walked from the highest
 * priority down, passing over anyone in it. Collection does not repeat, so two mods that skip each
 * other are both passed over and the frame goes to the baseline. Re-asking after a skip would mean
 * arbitrating between two third-party mods, which ACS has no basis on which to do.
 *
 * <p>A skip is one mod silencing another, so it is logged with both mod ids and your reason, once
 * per pair per session. That log line is the only thing that answers "why did my source stop
 * winning in this modpack".
 *
 * <p>Every method is idempotent within a frame; calling one twice only replaces the reason. Reasons
 * must not be null or blank.
 */
public interface FrameConditions {

    /**
     * Do not apply the ACS tilt on this frame.
     *
     * <p>What stops: the raycast of the surface under the player's feet, the threshold that decides
     * whether that surface counts, the smoothing that follows it, and the freeze that holds the
     * tilt while the player is airborne over a sub-level. That is the whole of the ACS policy and
     * it is all this touches.
     *
     * <p>What keeps running: everything that carries a tilt. The camera, the crosshair, the
     * aiming-ray net, block and entity picking, projectiles, and the sync to the server. Sources
     * are asked exactly as they would have been. With this stated and nobody claiming the frame,
     * the camera is level where it would otherwise have followed the sub-level.
     *
     * <p>It is the companion of a narrow {@link TiltSource} predicate. Without it, the ACS floor
     * tilt reappears on top of your scenario on every frame you decline, and widening the predicate
     * to cover that takes the tilt from every player everywhere.
     *
     * <p>It is not how to ask for less tilt: that is a source returning a smaller quaternion.
     *
     * @param reason why the ACS answer is not the right one on this frame; ends up in the log
     */
    void skipBaseline(String reason);

    /**
     * Do not ask this mod's {@link TiltSource} on this frame. It is passed over during resolution
     * as though it had declined, and the frame goes to whoever is next down the stack.
     *
     * <p>Nothing else about that mod is affected: its conditions were already collected, and its
     * {@link AcsConditions#frameResolved} still runs. Naming a mod that never registered a source
     * is harmless and silent; naming your own is allowed and means what it says.
     *
     * <p>This is one mod overriding another, so use it for a scenario and not for a preference.
     *
     * @param modId  the mod whose source should be passed over
     * @param reason why it must not drive this frame; logged with both mod ids
     */
    void skip(String modId, String reason);

    /**
     * Let the ACS tilt run in third person on this frame, camera rotated and every ray following
     * it. By default the baseline does nothing back there: vanilla camera, vanilla crosshair, every
     * ray from the eye vanilla put it at.
     *
     * <p>There is no way to ask for one half of it. The tilt quaternion is only maintained while it
     * is being applied, so "rays follow the camera but the camera is not rotated" would aim along a
     * value nobody is updating.
     *
     * <p>This does not gate sources, only ACS. A {@link TiltSource} is asked in third person
     * whether or not anyone states this, and decides for itself through
     * {@link TiltContext#firstPerson()}. If you drive the camera in third person, you need a
     * source, not this.
     *
     * @param reason why the tilt belongs in third person on this frame; ends up in the log
     */
    void baselineInThirdPerson(String reason);

    /**
     * Take over keeping the camera out of blocks for this frame. With this stated you guarantee the
     * camera point is not inside a block: ACS checks nothing, and seeing through a wall becomes
     * your bug.
     *
     * <p>It exists for a mod that really rotates the player in the world. The ACS check measures
     * from the vanilla eye, straight up in world Y from the feet, and for a rotated player that
     * point is a fiction: it lands inside the hull while the real eye is in open air, the camera
     * looks buried, and the whole tilt is scaled to zero.
     *
     * <p>State it on the frames you are actually rotating them. The switch this replaced also
     * turned the check off on every frame the player stood on flat ground with nothing rotated at
     * all.
     *
     * <p>It covers the whole check, the same ground the player's {@code Camera collision} setting
     * covers. The clamp is also what keeps the wall scale honest, so with it taken over the tilt is
     * no longer reduced near walls, for you or for the baseline.
     *
     * @param reason why the check measures from a point that does not exist; ends up in the log
     */
    void takeOverCameraCollision(String reason);
}
