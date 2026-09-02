package com.playsi.aero_cam_sync.apiimpl;

import com.playsi.aero_cam_sync.api.AimPolicy;
import com.playsi.aero_cam_sync.api.AimQuery;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The aim policy registry, polled from {@code ClipNet}.
 *
 * <p>{@code decide} is called from {@code clip} dozens of times per frame. The common
 * case, with no policies at all, costs one {@link #isEmpty()} check and no allocation: the
 * {@link AimQuery} object is built only when there is somebody to read it.
 */
public final class AimPolicies {

    private AimPolicies() {}

    private record Registered(String modId, AimPolicy policy) {}

    private static final CopyOnWriteArrayList<Registered> POLICIES = new CopyOnWriteArrayList<>();

    public static void add(String modId, AimPolicy policy) {
        POLICIES.add(new Registered(modId, policy));
    }

    public static boolean isEmpty() {
        return POLICIES.isEmpty();
    }

    /**
     * Asks the policies in registration order.
     *
     * <p>The first non-{@code PASS} wins. The rest are still polled: two policies disputing one ray
     * is somebody's bug, and it must show in the log without the debug setting. There are only a
     * handful of policies, so a full pass is cheaper than silently handing the user inconsistent
     * behaviour.
     *
     * <p>The facts arrive loose and the {@link AimQuery} is built here, so the caller
     * ({@code ClipNet}) can bail out on {@link #isEmpty()} before any allocation.
     *
     * @return the decision, or {@link AimPolicy.Decision#PASS} if the policies stayed silent and
     *         the net's standard rule applies
     */
    public static AimPolicy.Decision decide(Player player, Vec3 from, Vec3 to,
                                            @Nullable ClipContext context, boolean startsAtEye) {
        AimQuery query = new AimQueryImpl(player, from, to, context, startsAtEye);

        AimPolicy.Decision winner = AimPolicy.Decision.PASS;
        String winnerMod = null;

        for (Registered r : POLICIES) {
            AimPolicy.Decision decision;
            try {
                decision = r.policy().decide(query);
            } catch (Throwable t) {
                ApiLog.warn(r.modId(), "aim policy threw, treated as PASS: {}", String.valueOf(t));
                continue;
            }
            if (decision == null || decision == AimPolicy.Decision.PASS) continue;

            if (winnerMod == null) {
                winner = decision;
                winnerMod = r.modId();
            } else if (decision != winner) {
                // Arguments are passed loose, with no string concatenation: disputing policies
                // answer this way on EVERY ray, and ApiLog deduplicates the output itself.
                ApiLog.event(winnerMod, "aim policy conflict with {}: {} vs {}, first wins",
                        r.modId(), winner, decision);
            }
        }

        // Under the debug setting, one line per new decision per mod per session. The string is
        // built only when the setting is on: this is a hot path.
        if (winnerMod != null && ApiLog.debugEnabled()) {
            ApiLog.debug(winnerMod, "aim policy decided " + winner);
        }

        return winner;
    }
}
