package com.playsi.aero_cam_sync.api;

import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import javax.annotation.Nullable;

/**
 * The client-only half of {@link AcsState}, reached through {@link AcsState#client()}.
 *
 * <p>Split out because it names client-only types: a class holding a {@link ClientSubLevel} field
 * would be loaded on a dedicated server and crash the handshake.
 *
 * <p>The vanilla camera values are captured once per frame inside {@code Camera#setup}, right
 * before ACS tilts it. They are saved, not reconstructed, since inverting the current tilt
 * quaternion does not undo what was applied: the tilt is scaled by wall proximity and that scale
 * changes during the frame. Ask before that point in the frame and you get last frame's values.
 */
public interface AcsClientState {

    /** The camera position before ACS touched it, as captured this frame. */
    Vec3 vanillaCameraPos();

    /** The camera position after the tilt and the wall clamp. */
    Vec3 cameraPos();

    /** The camera rotation before ACS touched it, as captured this frame. */
    Quaternionf vanillaCameraRot();

    /** The camera rotation after the tilt. */
    Quaternionf cameraRot();

    /**
     * How much of the tilt survives the camera's proximity to a wall: 1 in the open, falling to 0
     * with the camera flat against a wall. It scales everything at once, rotation, position, rays
     * and projectiles.
     *
     * <p>Exposed because without it "the tilt is on but the correction is almost zero" looks like
     * an ACS bug. Stays at 1 in third person and whenever camera collision is off, since in both
     * the check that produces this number is not run at all.
     */
    float tiltScale();

    /**
     * Whether the camera was in first person when this snapshot was taken. Derivable from vanilla,
     * and here anyway so that a snapshot cannot disagree with itself: read
     * {@code options.getCameraType()} separately and you are reading a different instant.
     *
     * <p>This flag alone does not tell you whether there is a tilt. The ACS tilt does nothing in
     * third person unless a mod stated {@link FrameConditions#baselineInThirdPerson}, but a
     * {@link TiltSource} is asked there regardless. {@link AcsState#tiltApplied()} is the answer.
     */
    boolean firstPerson();

    /**
     * The sub-level the tilt is being computed from, or null when there is none under the local
     * player. Always the local player's, never anyone else's.
     *
     * <p>This is the ACS answer, not Sable's: the sub-level is chosen by downward rays that
     * reach seven blocks, so the value survives a jump and clears once the player is really
     * falling. To ask which sub-level a given player is on, ask Sable, since that call carries none
     * of the camera policy this one does.
     */
    @Nullable ClientSubLevel tiltSubLevel();

    /**
     * The mod whose {@link TiltSource} set the tilt this frame, or null when ACS computed it
     * itself. The mod that claimed the frame owns everything downstream of the tilt, and this
     * answers who that was without leaving the game.
     *
     * <p>A frame value, not a registry: a source that claims nothing is not named here, and neither
     * is one that was skipped through {@link FrameConditions#skip}. To see the skips as well,
     * implement {@link AcsConditions#frameResolved} and read {@link FrameReport#skipped()}.
     */
    @Nullable String tiltSource();
}
