package com.playsi.aero_cam_sync.api;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The facts about one ray, handed to {@link AimPolicy#decide(AimQuery)}.
 *
 * <p>Valid only for the duration of the {@code decide} call, so do not keep it.
 */
public interface AimQuery {

    /** The player the ray belongs to, taken from the clip context, not guessed. */
    Player player();

    /** Where the ray starts, before any correction. */
    Vec3 from();

    /** Where the ray ends, before any correction. */
    Vec3 to();

    /**
     * The clip context this ray came from, or {@code null} if the ray was built outside
     * {@code clip} and is being asked about anyway.
     */
    @Nullable ClipContext context();

    /**
     * Whether the normal ACS rule matched, that is, whether {@link #from()} sits exactly on
     * the player's vanilla eye position.
     *
     * <p>{@code true} here means the ray gets shifted unless a policy returns
     * {@link AimPolicy.Decision#KEEP_VANILLA}; {@code false} means it does not unless a policy
     * returns {@link AimPolicy.Decision#SHIFT}.
     */
    boolean startsAtEye();
}
