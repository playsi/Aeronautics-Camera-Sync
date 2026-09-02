package com.playsi.aero_cam_sync.api;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A consistent snapshot of ACS state, taken by {@link AcsHandle#state}. Works on both sides; the
 * client-only half is behind {@link #client()}.
 *
 * <p>The {@code aim*} values are never null and never garbage: with no tilt they are equal to the
 * {@code vanilla*} ones. Write {@code state.aimRay(reach)} once and do not branch on "what if there
 * is no tilt today", because that branch is where the bugs live. They are equal when the tilt is
 * off in the config or by the player's toggle, when the player is in third person, when a nearby
 * wall has scaled the tilt to zero, and when the player is not on a sub-level.
 *
 * <p>Exactly three things return null, and each means "there is no such thing" rather than "there
 * is no tilt": {@link #posTilt()}, {@link #lookTilt()} and {@link #client()}.
 */
public interface AcsState {

    /** Whether ACS is enabled, both in the config and by the player's in-game toggle. */
    boolean modEnabled();

    /**
     * Whether a tilt is actually being applied right now.
     *
     * <p>Measured on the residual tilt and not on the settings, so during the ease-out after
     * {@link AcsHandle#suppress(long)} this stays true until the leftover drops below the threshold
     * at which ACS stops correcting rays at all.
     */
    boolean tiltApplied();

    /** Whether any mod currently holds a suppression lease. */
    boolean suppressed();

    /** Mod ids holding a suppression lease; empty if none. Order is not guaranteed. */
    List<String> suppressedBy();

    /**
     * The rotation applied to the camera position, the eye rotated around the feet, or null when
     * position shifting is not happening at all: the option is off, or ACS is not tilting this
     * player right now.
     *
     * <p>Identity while the tilt is on but the sub-level is level. This is the raw quaternion for
     * mods doing their own maths; for aiming use {@link #aimEye()} and {@link #aimRay(double)},
     * which never need a null check.
     */
    @Nullable Quaternionf posTilt();

    /**
     * The rotation applied to the view direction, or null when direction tilting is not happening
     * at all. Same shape as {@link #posTilt()}; the two are independent options and either can be
     * on alone.
     */
    @Nullable Quaternionf lookTilt();

    /**
     * The displacement a {@link TiltSource} asked ACS to apply to the eye, or {@link Vec3#ZERO}
     * when nobody did. Never null, like the {@code aim*} values and for the same reason.
     *
     * <p>Already included in {@link #aimEye()}; this is the component on its own, for answering
     * "how much of that correction is the other mod's" without leaving the game. Scaled by
     * {@code tiltScale()}, so near a wall it is smaller than what the source returned.
     *
     * <p>Works on both sides, since it is synced along with the tilt: the server decides projectile
     * origins and interaction reach from it. {@link AcsClientState#tiltSource()} names the mod that
     * asked.
     */
    Vec3 eyeOffset();

    /** The player's untouched eye position, {@code player.getEyePosition(partialTick)}. */
    Vec3 vanillaEye();

    /** The point the player actually aims from: the eye plus the tilt correction. */
    Vec3 aimEye();

    /**
     * The untilted look direction, computed from the raw pitch and yaw. Recomputed, not stored,
     * because it is a function of {@code partialTick} and you may ask for any.
     */
    Vec3 vanillaLook(float partialTick);

    /** The look direction as tilted by ACS: the one the crosshair points along. */
    Vec3 aimLook(float partialTick);

    /**
     * The ray a mod would have built without ACS: from {@link #vanillaEye()} along
     * {@link #vanillaLook} for {@code reach} blocks.
     */
    AcsRay vanillaRay(double reach);

    /**
     * The ray that matches what the player sees: from {@link #aimEye()} along {@link #aimLook} for
     * {@code reach} blocks. This is what belongs in your {@code ClipContext}.
     */
    AcsRay aimRay(double reach);

    /** The client-only half of the snapshot, or null on a dedicated server. */
    @Nullable AcsClientState client();
}
