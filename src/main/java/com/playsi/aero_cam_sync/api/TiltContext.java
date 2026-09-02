package com.playsi.aero_cam_sync.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * Everything a {@link TiltSource} is handed to decide with. Valid for the duration of the call it
 * was passed to; the quaternions and vectors are copies and yours to keep, already interpolated for
 * {@link #partialTick()}.
 *
 * <p>Do not call {@link AcsHandle#state} from inside a source. The snapshot reports the resolved
 * tilt, resolving asks the sources, and a source asking for the snapshot closes that loop into a
 * {@code StackOverflowError} rather than a wrong number. This object carries the same inputs
 * already computed, so there is never a reason to.
 */
public interface TiltContext {

    /** The local player the tilt is being computed for. Never null. */
    Player player();

    /** Render partial tick of the frame being drawn. */
    float partialTick();

    /**
     * Length of this frame in ticks, as {@code getRealtimeDeltaTicks()}. This is the number to
     * smooth against: a source that eases towards a target off anything else moves at a different
     * speed on a different machine.
     */
    float deltaTicks();

    /**
     * The surface normal under the player, in world space, or null when there is no surface to
     * speak of: the player is not on a sub-level, is in the air, or the ray missed.
     *
     * <p>The raw input ACS computed its own tilt from, handed over so a source does not have to
     * cast the same ray again with slightly different thresholds.
     */
    @Nullable Vector3f surfaceNormal();

    /**
     * The tilt ACS worked out for this frame, before any source was asked. This is the base for a
     * source that only wants to modify the tilt: half the lean, a lean about a different pivot, a
     * lean capped at some angle. Take it, transform it, return the result. A modifier is just a
     * source that reads this value, which is why there is no separate mechanism for one.
     *
     * <p>It is the smoothed tilt and not the raw target, and it is not scaled by
     * {@link AcsClientState#tiltScale()}, since the wall clamp is applied after resolution to
     * whatever the winner returned.
     *
     * <p>On a frame where somebody stated {@link FrameConditions#skipBaseline(String)} this eases
     * to identity: with the ACS policy standing down, what would have been applied is nothing.
     * {@link #surfaceNormal()} is unaffected.
     */
    Quaternionf acsTilt();

    /** Whether the camera is in first person right now. */
    boolean firstPerson();

    /**
     * The camera position before ACS touched it this frame: vanilla's own answer, crouch smoothing,
     * view bob, third-person zoom and all. Note this is the camera and not the eye, and in third
     * person the two are up to four blocks apart. It is where the position arithmetic starts.
     */
    Vec3 vanillaCameraPos();

    /**
     * Where ACS will put the camera for {@code tilt}, with no offset and before the collision
     * clamp: {@code feet + tilt * (vanillaCameraPos - feet)}. This is the baseline
     * {@link TiltSource#eyeOffset} is measured against; a source that knows where the eye actually
     * is subtracts this from it and returns the difference.
     *
     * <p>It is here so the formula lives in one place. Writing it out on your side works today and
     * is one release away from disagreeing.
     *
     * <p>A pure function of this frame: feet, {@link #vanillaCameraPos()}, and the quaternion you
     * pass. Safe to call from any of the source methods, in any order, as often as you like; ACS
     * normalises a copy of what you hand in and keeps nothing. Pass the tilt you are about to
     * return, not {@link #acsTilt()}, unless you want the offset measured against the ACS one.
     */
    Vec3 cameraPosFor(Quaternionf tilt);
}
