package com.playsi.aero_cam_sync.apiimpl;

import com.playsi.aero_cam_sync.api.AcsState;
import net.minecraft.world.entity.player.Player;

/**
 * Tilt transitions: start and end. Suppression events come from {@link SuppressionLeases} instead,
 * where the transition is visible more precisely, lease expiry included.
 *
 * Why from the tick rather than the frame
 *
 * <p>A frame-based point ({@code Camera#setup}) sits behind its own guards: with the mod off or in
 * third person {@code CameraMixin} exits early and "the tilt ended" would never arrive, which is
 * exactly the case a mod must hear about. The tick runs unconditionally.
 *
 * <p>Twenty polls a second are enough: the event already lags its cause, since the tilt ends when
 * the residual falls below the threshold, several frames after stepping off the sub-level.
 */
public final class TiltEvents {

    private TiltEvents() {}

    private static boolean tiltWasApplied = false;

    /** The client tick. With nobody subscribed the snapshot is not even built; it costs an allocation. */
    public static void tick(Player player) {
        if (TiltListeners.isEmpty()) {
            tiltWasApplied = false;
            return;
        }

        AcsState state = AcsStateImpl.capture(null, player, 1.0f);
        boolean applied = state.tiltApplied();
        if (applied == tiltWasApplied) return;

        tiltWasApplied = applied;
        if (applied) TiltListeners.tiltStarted(state);
        else TiltListeners.tiltStopped(state);
    }

    /** Leaving a world: the next join must start from a clean transition. */
    public static void reset() {
        tiltWasApplied = false;
    }
}
