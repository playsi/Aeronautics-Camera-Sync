package com.playsi.aero_cam_sync.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.List;

/**
 * What happened on one frame. Handed to {@link AcsConditions#frameResolved} after the pose has been
 * applied to the camera, and valid for that call only.
 *
 * <p>{@link AcsHandle#state} answers "what is true right now" for whoever asks. This answers "what
 * happened on the frame that just resolved", including the parts a snapshot cannot carry because
 * they are about the resolution itself: who claimed it, who was skipped and by whom. A source can
 * take a frame, a skip can pass that source over, and the wall clamp can scale the result to
 * nothing, all leaving the same final quaternion behind.
 *
 * <p>Reporting only: by the time it is handed out the camera has moved and the rays follow it.
 */
public interface FrameReport {

    /** The local player the frame was drawn for. Never null. */
    Player player();

    /** Render partial tick of the frame. */
    float partialTick();

    /** Whether the camera was in first person on this frame. */
    boolean firstPerson();

    /**
     * The mod whose {@link TiltSource} drove this frame, or null when nobody claimed it. Null does
     * not mean "no tilt", see {@link #baselineActive()}.
     */
    @Nullable String tiltSource();

    /**
     * Whether the ACS tilt is what the camera followed on this frame: nobody claimed it and the ACS
     * policy was allowed to run, so no {@link FrameConditions#skipBaseline}, no suppression lease,
     * and a camera mode ACS tilts in.
     *
     * <p>With {@link #tiltSource()} null and this false, the frame carried no tilt of anyone's and
     * the camera was easing back to level.
     */
    boolean baselineActive();

    /**
     * The tilt actually applied to the camera, after the wall clamp, and therefore the same value
     * the rays, the projectiles and the server were given. A copy; mutating it changes nothing.
     * Identity when there was no tilt.
     */
    Quaternionf tilt();

    /**
     * The eye offset actually applied, in world space, after the same clamp. {@link Vec3#ZERO} when
     * there was none, which is the ordinary case: only a {@link TiltSource#eyeOffset} produces one.
     */
    Vec3 eyeOffset();

    /**
     * How much of the tilt survived the camera-collision clamp: 1 with no wall in the way, 0 with
     * the camera against one. One number scales all of it, so a frame where this is small is a
     * frame where a source got the camera and a wall took it back.
     */
    float tiltScale();

    /**
     * The mods whose sources were passed over on this frame, in no guaranteed order. Empty on
     * almost every frame. Includes your own mod id if somebody skipped you, which is what makes "my
     * source stopped winning and I do not know why" answerable without a log.
     */
    List<String> skipped();
}
