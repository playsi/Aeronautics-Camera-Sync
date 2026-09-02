package com.playsi.aero_cam_sync.client.sublevel;

import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.tilt.SurfaceRaycaster;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;

import javax.annotation.Nullable;

/**
 * Decides which sub-level the player stands on. Sable only NAMES A CANDIDATE; rays decide by count,
 * and the held one keeps the crown until a rival leads by {@code voteMargin}. The margin is a
 * DISTANCE, not a time, so it adds no latency: the crown moves in the same frame the player's
 * centre crosses the seam.
 *
 * <p>Ignition is Sable's, holding is the rays'. Votes answer "which sub-level", not "is this
 * a sub-level at all": the main world is not on the ballot, so a player on the ground beside a hull
 * won 1:0 and was declared aboard. CHANGING sub-level therefore needs Sable's flag in the same
 * frame. HOLDING must not: Sable drops the flag at the very edge and mid-jump, which is exactly
 * where the ring was introduced to help.
 */
public final class SubLevelTracker {

    private SubLevelTracker() {}

    /** An ACS choice, not "the last thing Sable said": it changes only by the margin rule. */
    private static @Nullable ClientSubLevel held = null;

    /**
     * Not the same as "{@code resolve} returned non-null": that also returns the held choice while
     * the player is airborne, and such an answer cannot be decided on.
     */
    private static boolean trackedBySable = false;

    /**
     * @param useVotes {@code false} in frames with no tilt at all, where the rule degenerates to
     *                 "whatever Sable said" rather than paying clips for an unused answer
     */
    public static @Nullable ClientSubLevel resolve(LocalPlayer player, boolean useVotes) {
        SubLevel answer = Sable.HELPER.getTrackingOrVehicleSubLevel(player);
        ClientSubLevel sable = answer instanceof ClientSubLevel csl ? csl : null;
        trackedBySable = sable != null;

        if (player.getAbilities().flying && Config.DISABLE_ON_FLYING.get()) {
            held = null;
            trackedBySable = false;
            return null;
        }

        // Nothing within ray length: bare ground must not burn clips on an empty ballot.
        if (!useVotes || !SurfaceRaycaster.anyInReach(player)) {
            return settle(sable);
        }

        SurfaceRaycaster.Ballot ballot = SurfaceRaycaster.cast(player, held);

        ClientSubLevel leader = ballot.leader();
        if (leader != null) {
            int leaderVotes = ballot.leaderVotes();
            int heldVotes   = ballot.votesFor(held);

            if (held == null) {
                // IGNITION needs the flag: one ring vote beside a hull wins 1:0 against nobody.
                if (!trackedBySable) return null;
                held = leader;
            } else if (heldVotes == 0) {
                if (!trackedBySable) {
                    return settle(sable); // no votes and no flag: the player left
                }
                // A leaky platform the ring misses but Sable sees: the crown stays while it names
                // the held sub-level.
                if (sable != held) held = leader;
            } else if (leaderVotes - heldVotes >= Config.VOTE_MARGIN.get() && trackedBySable) {
                held = leader; // crossed the seam
            }
            // A tie, or a lead under the margin, leaves the champion in place.

            return held;
        }

        return settle(sable);
    }

    /**
     * {@link #held} is dropped RIGHT HERE; holding it longer gives a fall with a tilted camera. A
     * jump needs no grace, since {@code downLength} exceeds its height and the sub-level below keeps
     * collecting votes, so only a real departure reaches this method.
     */
    private static @Nullable ClientSubLevel settle(@Nullable ClientSubLevel sable) {
        held = sable;
        return held;
    }

    /** For readers arriving later in the same frame: the camera clip and the API snapshot. */
    public static @Nullable ClientSubLevel getHeldSubLevel() {
        return held;
    }

    /** Diagnostics only; the selection rule asks this inside {@link #resolve}. */
    public static boolean isTrackedBySable() {
        return trackedBySable;
    }
}
