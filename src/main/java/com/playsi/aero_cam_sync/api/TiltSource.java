package com.playsi.aero_cam_sync.api;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import javax.annotation.Nullable;

/**
 * A mod's own answer to "how should the camera be tilted this frame". Register with
 * {@link AcsHandle#addTiltSource(int, TiltSource)}, and read
 * {@code docs/tilt-control.md#driving-the-tilt-yourself} before you do.
 *
 * <p>Sources are asked in descending priority order and the first one whose {@link #appliesTo}
 * returns true wins; the rest are not asked. If nobody claims the frame, ACS uses its own tilt,
 * taken from the surface under the player, and if ACS is not tilting either the camera is vanilla.
 * The winner owns the whole camera pose, not only its rotation: {@link #eyeOffset} is where it says
 * the eye has moved to.
 *
 * <p>Everything downstream of the tilt follows your quaternion, including the server. ACS sends the
 * applied tilt over, where it decides the direction a projectile leaves along, the point it leaves
 * from, and the reach of block and entity interaction. A source is not a camera effect: claim a
 * frame and you have changed where the player's arrows go. Claim only the frames your scenario is
 * actually about, and leave the rest to whoever is below you.
 *
 * <p>Priority is a number and nothing more: higher is asked first, ties go to registration order,
 * and the ACS tilt is not in the list at all, it is what happens when the list runs out. A slot at
 * the bottom would be a slot you could register below, and a source that can never win is a defect
 * nothing would report.
 *
 * <p>Two things override you, and they are the same idea at two scopes.
 * {@link AcsHandle#suppress(long)} stops everyone on a clock, because a cutscene has to be able to
 * take the camera regardless of who is driving. {@link FrameConditions#skip(String, String)} is
 * that aimed at one mod for one frame; any mod may name yours, and the log says who did. What does
 * not stop you: the player's mod toggle, the {@code Enabled} setting, the camera mode, and the
 * {@code Rotate camera} and {@code Shift camera position} settings. Those halves belong to the ACS
 * arithmetic and your quaternion has none. If you want the player's toggle to silence your source,
 * check it yourself in {@link #appliesTo}.
 *
 * <p>What stays with ACS is the wall clamp: your tilt is scaled by
 * {@link AcsClientState#tiltScale()} the same way, so a camera claimed by your mod still does not
 * end up inside a block. If your mod really rotates the player and that check measures from a point
 * that no longer exists, take the duty over with
 * {@link FrameConditions#takeOverCameraCollision(String)}.
 *
 * <p>Cost: {@link #appliesTo} is called once per frame per source, down to the winner. Decide from
 * the {@link TiltContext} and return, with no raycasts and no world queries. Mods that register no
 * source pay one comparison.
 *
 * <pre>{@code
 * ACS.addTiltSource(100, new TiltSource() {
 *     @Override
 *     public boolean appliesTo(TiltContext ctx) {
 *         return ctx.player().level().dimension() == MyDimensions.ORBIT;
 *     }
 *
 *     @Override
 *     public Quaternionf tilt(TiltContext ctx) {
 *         return myGravity.orientation(ctx.partialTick());
 *     }
 * });
 * }</pre>
 */
public interface TiltSource {

    /**
     * Whether this source wants to set the tilt this frame.
     *
     * <p>Answer false for every frame that is not your scenario. That is what lets the mods below
     * you, and ACS itself, keep working. A source that always answers true has taken the tilt for
     * the whole session, including for players standing on sub-levels your scenario knows nothing
     * about. If declining leaves the ACS floor tilt showing under your scenario, the answer is
     * {@link FrameConditions#skipBaseline(String)} on those frames, not a wider predicate.
     */
    boolean appliesTo(TiltContext context);

    /**
     * The tilt to apply, as a rotation from world-up to where up should be. Called only when
     * {@link #appliesTo} answered true.
     *
     * <p>Returning null is treated as declining after all, so the next source down is asked, and it
     * is logged once, since it usually means the two methods disagree about the same condition.
     *
     * <p>The quaternion is copied and normalised by ACS; you may return a field.
     */
    @Nullable Quaternionf tilt(TiltContext context);

    /**
     * Where the eye really is, relative to where ACS would otherwise put it. World space, and a
     * delta, not a position.
     *
     * <p>The ACS arithmetic knows exactly one arc, {@code feet + tilt * (vanillaCameraPos - feet)},
     * the eye rotated about the player's feet. A body that pivots at a neck, a hip or a seat mount
     * lands the eye somewhere that formula never visits. Use
     * {@link TiltContext#cameraPosFor(Quaternionf)} to turn an absolute target into a delta, rather
     * than writing the formula down on your side, where it is one release away from disagreeing.
     *
     * <p>The vector is added on top of the rotation, and then to the origin of every ray that
     * leaves the eye: the crosshair pick, block and entity interaction, projectile spawn points,
     * thrown items, buckets, and the copy of all of it the server settles hits with. So reach
     * follows the eye. Push it a metre forward and the player reaches a metre further, through
     * whatever that metre crossed. It is not a screen-shake channel for the same reason.
     *
     * <p>Above four blocks ACS scales it back to four, keeping its direction, and logs one warning
     * per session with your mod id. Four covers the worst honest case with room to spare: a body
     * pivoting at the neck lands some 3.2 blocks off the ACS formula with the player upside down.
     * If your scenario means more than that, say so from {@link #eyeOffsetIsDeliberate}.
     *
     * <p>Smoothing is yours, and so is the ease-out. Unlike the tilt, where the ACS slerp resumes
     * from whatever value you left behind, a vector ACS does not own has nowhere to resume from, so
     * the frame after your last claim the offset is zero. A large offset at the moment you stop
     * claiming is a visible jump.
     *
     * <p>Called on the winner only, immediately after {@link #tilt}. {@link Vec3#ZERO} and null
     * both mean "no displacement", and neither declines the frame, which you have already
     * claimed. A non-finite vector is refused with one warning and treated as zero: NaN in a camera
     * position is a black screen the player will report to the wrong mod. Scaled by
     * {@link AcsClientState#tiltScale()} and clamped by camera collision exactly as the tilt is.
     *
     * <pre>{@code
     * @Override
     * public Vec3 eyeOffset(TiltContext ctx) {
     *     Vec3 head = myPhysics.headPosition(ctx.partialTick());   // where it really is
     *     return head.subtract(ctx.cameraPosFor(myTiltThisFrame)); // where ACS would put it
     * }
     * }</pre>
     */
    default @Nullable Vec3 eyeOffset(TiltContext context) {
        return Vec3.ZERO;
    }

    /**
     * Whether an {@link #eyeOffset} longer than four blocks is meant, and should be applied as
     * given rather than scaled back. Asked of the winner immediately after {@link #eyeOffset},
     * and only when the vector exceeds the ceiling, so on almost every frame it is not called.
     *
     * <p>It lives here rather than in {@link FrameConditions} because of what it asserts. A
     * condition states something about the world and is true for the frame whoever wins it. This
     * states something about your vector, and stated frame-wide one mod's honest reason would
     * uncap another mod's mistake on the same frame.
     *
     * <p>Reach is measured from the aim origin, on the server, so returning true says the player
     * reaching that far through whatever is in the way is your scenario and not a bug. The warning
     * goes in the log either way, worded to say the clamp was declined.
     *
     * <p>This is not server-side protection. The clamp runs on the client before the pose is
     * synced, so it defends against a mistake in a mod, not against a modified client.
     */
    default boolean eyeOffsetIsDeliberate(TiltContext context) {
        return false;
    }
}
