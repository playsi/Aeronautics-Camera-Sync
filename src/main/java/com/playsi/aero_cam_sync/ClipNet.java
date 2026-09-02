package com.playsi.aero_cam_sync;

import com.playsi.aero_cam_sync.apiimpl.AimPolicies;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.EntityCollisionContext;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One interception on {@code BlockGetter#clip} instead of per-mod compat: aim rays are caught here
 * and their origin moved to the tilted camera.
 *
 * <p>Everything rests on the filter. Only a ray whose origin coincides with its owner's
 * vanilla eye is corrected, which works because every hand-assembled eye
 * ({@code position().add(0, getEyeHeight(), 0)} and friends) is bit-for-bit
 * {@code getEyePosition()}. Two properties follow: nothing already corrected can be shifted twice,
 * and foreign physics does not pass, which is what the old "within 4 blocks" threshold got wrong
 * (Issue #30).
 *
 * <p>Never caught: block walks that bypass {@code clip}, reference points with no ray, and
 * foreign distance metrics. Those need targeted compat.
 */
public final class ClipNet {

    private ClipNet() {}

    /**
     * Lives in {@link TiltAccess} because the public API defines "tilt is applied" by the same
     * threshold, and the two definitions must be one number.
     */
    private static final double EPSILON_SQR = TiltAccess.EPSILON_SQR;

    /** A repeat clip of the net's own must not fall into it again. */
    private static final ThreadLocal<Boolean> INSIDE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * Without this the net catches itself: camera collision clips from the eye, which makes it a
     * feedback loop measuring its own output. A counter, not a flag: the calls nest.
     */
    private static final ThreadLocal<int[]> SUPPRESSED = ThreadLocal.withInitial(() -> new int[1]);

    /** Always pair with {@link #resume()}. */
    public static void suppress() {
        SUPPRESSED.get()[0]++;
    }

    public static void resume() {
        int[] depth = SUPPRESSED.get();
        if (depth[0] > 0) depth[0]--;
    }

    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    public static BlockHitResult tryShift(BlockGetter blockGetter, ClipContext context) {
        if (INSIDE.get()) return null;
        if (SUPPRESSED.get()[0] > 0) return null;
        if (!(blockGetter instanceof Level level)) return null;
        if (!SideGate.isOwnThread(level)) return null;

        if (!(context.collisionContext instanceof EntityCollisionContext entityContext)) return null;
        if (!(entityContext.getEntity() instanceof Player player)) return null;
        if (player.level() != level) return null;

        Vec3 offset = TiltAccess.aimEyeOffset(player);
        if (offset == null) return null;

        // Without this the net catches the vanilla Entity#pick on level ground and recomputes the
        // clip with a zero shift.
        if (offset.lengthSqr() < EPSILON_SQR) return null;

        // THE MAIN CHECK: the ray starts exactly at this player's vanilla eye.
        Vec3 from = context.getFrom();
        boolean startsAtEye = from.distanceToSqr(player.getEyePosition()) <= EPSILON_SQR;

        if (!decideWithPolicies(player, context, from, startsAtEye)) return null;

        ClipContext shifted = new ClipContext(
                from.add(offset),
                context.getTo().add(offset),
                context.block,
                context.fluid,
                player);

        INSIDE.set(Boolean.TRUE);
        try {
            BlockHitResult result = level.clip(shifted);
            // The key is the call chain alone: with the offset in it, one caller gave thirty-odd
            // lines, one per new value.
            SideGate.reportCatch(level, ClipNet::describeCaller, offset.length());
            return result;
        } finally {
            INSIDE.set(Boolean.FALSE);
        }
    }

    /**
     * Policies can decide either way, but they cannot touch the net's invariants: suppression, the
     * shared gate with the funnel and the negligible-correction threshold all sit ABOVE this and
     * reject the ray before any poll.
     */
    private static boolean decideWithPolicies(Player player, ClipContext context,
                                              Vec3 from, boolean startsAtEye) {
        if (AimPolicies.isEmpty()) return startsAtEye;

        return switch (AimPolicies.decide(player, from, context.getTo(), context, startsAtEye)) {
            case SHIFT -> true;
            case KEEP_VANILLA -> false;
            case PASS -> startsAtEye;
        };
    }

    private static String describeCaller() {
        return StackWalker.getInstance()
                .walk(frames -> frames
                        .map(f -> f.getClassName() + "#" + f.getMethodName())
                        .filter(name -> !name.startsWith("com.playsi.aero_cam_sync")
                                && !name.startsWith("net.minecraft.world.level.BlockGetter")
                                && !name.startsWith("java."))
                        // Three: one frame cannot tell a mod's Entity#pick from the vanilla pick.
                        .limit(3)
                        .reduce((a, b) -> a + " ← " + b)
                        .orElse("<unknown>"));
    }

    /** Each caller is printed exactly once per session. */
    public static boolean firstTimeSeen(String caller) {
        return SEEN.add(caller);
    }
}
