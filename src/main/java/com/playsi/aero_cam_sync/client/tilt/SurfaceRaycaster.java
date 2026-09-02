package com.playsi.aero_cam_sync.client.tilt;

import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.debug.DebugRayRenderer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * A ring of rays around the player's feet that VOTES for the sub-level underneath. The rays exist
 * because Sable drops its "player is on a sub-level" flag right at the edge, before the player
 * physically leaves; {@code RAY_RADIUS} is chosen so the edge catches at least one ray at any
 * rotation.
 *
 * <p>No tilt is computed here: {@code getDirection()} is one of six {@code Direction} values and
 * carries no slope at all. See {@link DeckOrientation}.
 *
 * <p>The centre ray is weighted, not decisive. The ring answers "which sub-level am I
 * touching" and answers late (0.58 is nearly twice the player's half-width, so rays behind keep
 * voting for the one just left); the centre answers "what am I standing on" without lag. But over a
 * hatch it falls through and names the sub-level below, so it must outweigh a dissenting minority
 * of the ring and lose to a unanimous majority around a hole.
 *
 * <p>One clip per ray is enough because Sable's overwritten {@code Level#clip} already tries
 * the main world and every intersected sub-level and returns the NEAREST hit.
 */
public final class SurfaceRaycaster {

    private SurfaceRaycaster() {}

    private static final float RAY_RADIUS = 0.58f;

    private static int rayCount()      { return Config.RAYCAST_COUNT.get(); }
    private static int centerWeight()  { return Config.RAYCAST_CENTER_WEIGHT.get(); }
    private static float offsetUp()    { return Config.RAYCAST_UP_LENGTH.get().floatValue(); }
    private static float offsetDown()  { return -Config.RAYCAST_DOWN_LENGTH.get().floatValue(); }

    // Vote result

    /** One or two candidates in practice: flat arrays beat a hash map and make no garbage. */
    public static final class Ballot {

        private final ClientSubLevel[] keys;
        private final int[] votes;
        private int size = 0;

        private Ballot(int capacity) {
            this.keys  = new ClientSubLevel[capacity];
            this.votes = new int[capacity];
        }

        private void count(ClientSubLevel subLevel, int weight) {
            for (int i = 0; i < size; i++) {
                if (keys[i] == subLevel) {
                    votes[i] += weight;
                    return;
                }
            }
            keys[size]  = subLevel;
            votes[size] = weight;
            size++;
        }

        /** Not always a ray count: the centre ray brings {@code centerWeight} votes at once. */
        public int votesFor(@Nullable ClientSubLevel subLevel) {
            if (subLevel == null) return 0;
            for (int i = 0; i < size; i++) {
                if (keys[i] == subLevel) return votes[i];
            }
            return 0;
        }

        /** Ties break on {@code UUID} so the answer cannot depend on ray iteration order. */
        public @Nullable ClientSubLevel leader() {
            ClientSubLevel best = null;
            int bestVotes = 0;
            for (int i = 0; i < size; i++) {
                if (votes[i] > bestVotes
                        || (votes[i] == bestVotes && best != null && lower(keys[i], best))) {
                    best = keys[i];
                    bestVotes = votes[i];
                }
            }
            return best;
        }

        public int leaderVotes() {
            int bestVotes = 0;
            for (int i = 0; i < size; i++) {
                if (votes[i] > bestVotes) bestVotes = votes[i];
            }
            return bestVotes;
        }

        private static boolean lower(ClientSubLevel a, ClientSubLevel b) {
            try {
                return a.getUniqueId().compareTo(b.getUniqueId()) < 0;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    // Public API

    /**
     * A cheap AABB gate before {@link #cast}, which costs one clip per ray. Answers {@code true} if
     * Sable throws: a wasted ring is harmless next to a silently disabled tilt.
     */
    public static boolean anyInReach(LocalPlayer player) {
        Vec3 feet = player.position();
        BoundingBox3d column = new BoundingBox3d(
                feet.x - RAY_RADIUS, feet.y + offsetDown(), feet.z - RAY_RADIUS,
                feet.x + RAY_RADIUS, feet.y + offsetUp(),   feet.z + RAY_RADIUS);

        try {
            return Sable.HELPER.getAllIntersecting(player.level(), column).iterator().hasNext();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * {@code downLength} is greater than the jump height, so votes keep coming
     * mid-jump and no separate airborne freeze is needed.
     *
     * @param held used ONLY to colour debug rays, never counted
     */
    public static Ballot cast(LocalPlayer player, @Nullable ClientSubLevel held) {
        Level level = player.level();
        Vec3 feet = player.position();

        int count = rayCount();
        Ballot ballot = new Ballot(count + 1);

        float up   = offsetUp();
        float down = offsetDown();

        int centerWeight = centerWeight();
        if (centerWeight > 0) {
            cast(level, player, ballot, held, feet.x, feet.y, feet.z, up, down, centerWeight);
        }

        // No early exit on the first hit: a vote needs the whole ring.
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            cast(level, player, ballot, held,
                    feet.x + Math.cos(angle) * RAY_RADIUS,
                    feet.y,
                    feet.z + Math.sin(angle) * RAY_RADIUS,
                    up, down, 1);
        }

        return ballot;
    }

    // Internals

    private static void cast(Level level, LocalPlayer player, Ballot ballot,
                             @Nullable ClientSubLevel held,
                             double x, double feetY, double z, float up, float down, int weight) {
        Vec3 from = new Vec3(x, feetY + up,   z);
        Vec3 to   = new Vec3(x, feetY + down, z);

        BlockHitResult hit = clipDown(level, player, from, to);

        // Three outcomes, not two: "met the hull" must stay distinct from "missed everything".
        ClientSubLevel owner = null;
        boolean steep = false;

        if (hit != null) {
            ClientSubLevel subLevel = ownerOf(level, hit);
            if (subLevel != null) {
                if (standable(subLevel, hit.getDirection())) owner = subLevel;
                else steep = true;
            }
        }

        if (owner != null) ballot.count(owner, weight);

        recordDebugRay(from, to, owner, held, steep);
    }

    /**
     * This runs inside {@code Camera#setup} and may not drop the frame: the clip goes through
     * Sable's overwritten path and can catch a sub-level mid-teardown.
     */
    private static @Nullable BlockHitResult clipDown(Level level, LocalPlayer player, Vec3 from, Vec3 to) {
        ClipContext ctx = new ClipContext(
                from, to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        );

        BlockHitResult hit;
        try {
            hit = level.clip(ctx);
        } catch (Throwable ignored) {
            return null;
        }

        return hit == null || hit.getType() == HitResult.Type.MISS ? null : hit;
    }

    private static @Nullable ClientSubLevel ownerOf(Level level, BlockHitResult hit) {
        SubLevel subLevel;
        try {
            subLevel = Sable.HELPER.getContaining(level, hit.getLocation());
        } catch (Throwable ignored) {
            return null;
        }

        return subLevel instanceof ClientSubLevel csl ? csl : null;
    }

    /**
     * Whether this is a floor rather than a hull face. Without this check one ray clipping a hull
     * edge tilts a player standing on the ground beside the ship. The face arrives in local space,
     * so its normal is rotated by the pose first.
     *
     * <p>The vote counts if Sable throws: losing the tilt on a real floor is worse.
     */
    private static boolean standable(ClientSubLevel subLevel, Direction face) {
        Quaternionf orientation;
        try {
            orientation = MathUtils.toQuaternionf(subLevel.logicalPose().orientation());
        } catch (Throwable ignored) {
            return true;
        }

        Vector3f normal = orientation.transform(
                new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ()));

        return normal.y >= Config.MIN_NORMAL_Y.get().floatValue();
    }

    /**
     * Green votes for the held sub-level, blue for a neighbour, red missed everything, yellow hit a
     * face nobody stands on. Yellow is separate from red so "beside a hull" stays distinct from "on
     * bare ground".
     */
    private static void recordDebugRay(Vec3 from, Vec3 to,
                                       @Nullable ClientSubLevel owner,
                                       @Nullable ClientSubLevel held,
                                       boolean steep) {
        if (owner == null) {
            if (steep) DebugRayRenderer.submitRay(from, to, 1f, 0.85f, 0.2f);
            else       DebugRayRenderer.submitRay(from, to, 1f, 0.2f, 0.2f);
        } else if (owner == held) {
            DebugRayRenderer.submitRay(from, to, 0.2f, 1f, 0.2f);
        } else {
            DebugRayRenderer.submitRay(from, to, 0.2f, 0.4f, 1f);
        }
    }
}
