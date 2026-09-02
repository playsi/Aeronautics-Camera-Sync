package com.playsi.aero_cam_sync.apiimpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tilt suppression leases: {@code modId -> time suppressed until}. Owned, not a flag, or mod Y
 * finishing its cutscene switches off the suppression mod X has not started.
 *
 * <p>Keep this class free of Minecraft types. It is the one piece of the API that is
 * genuinely unit-testable, and time is fed in through {@link #advance(long, boolean)} for that
 * reason. Internal time stops while paused: a mod extends its lease from the tick, ticks stop in a
 * paused singleplayer game and real time does not, so without that a cutscene releases the tilt
 * mid-pause.
 *
 * <p>The player's switch ({@link #setForced}) lives here rather than as a separate check in the
 * camera, so "is it suppressed" answers identically to every caller.
 */
public final class SuppressionLeases {

    private SuppressionLeases() {}

    /** A longer lease logs a warning: nothing to forbid, but it must be visible. */
    static final long LONG_LEASE_MILLIS = 10_000L;

    /**
     * More than a second per tick means the game was not running (world load, minimised window),
     * and charging that to a lease releases the tilt as the picture comes back.
     */
    private static final long MAX_STEP_MILLIS = 1_000L;

    private static final Map<String, Long> LEASES = new ConcurrentHashMap<>();

    /** Real time minus whatever the game spent paused. */
    private static volatile long now = 0L;

    /** {@code -1} means there has been no step yet. */
    private static long lastRealMillis = -1L;

    /** Cached, so listeners only hear about transitions. */
    private static volatile boolean suppressed = false;

    /**
     * Not shaped like a modId: {@code suppressedBy()} carries mod identifiers, and slipping the
     * ACS one in would be a lie. No API asked for this; the player did.
     */
    public static final String CONFIG_HOLDER = "aero_cam_sync:suppress-all";

    /** Fed in from outside, to keep Minecraft types out of this class. */
    private static volatile boolean forced = false;

    /** {@code millis <= 0} is ignored. */
    public static void suppress(String modId, long millis) {
        if (millis <= 0) return;
        LEASES.put(modId, now + millis);
        refresh();
    }

    /** Called every client tick; the comparison keeps listeners from hearing it twenty times a second. */
    public static void setForced(boolean value) {
        if (forced == value) return;
        forced = value;
        refresh();
    }

    /** Never touches anyone else's lease, which is why leases have owners. */
    public static void release(String modId) {
        if (LEASES.remove(modId) == null) return;
        refresh();
    }

    public static boolean isSuppressed() {
        return suppressed;
    }

    public static boolean isSuppressedBy(String modId) {
        Long until = LEASES.get(modId);
        return until != null && until > now;
    }

    /**
     * Order is not guaranteed, as the contract says. The player's switch arrives as
     * {@link #CONFIG_HOLDER}, or a mod seeing suppression with an empty list concludes ACS is
     * broken.
     */
    public static List<String> holders() {
        if (LEASES.isEmpty() && !forced) return Collections.emptyList();

        List<String> result = new ArrayList<>(LEASES.size() + 1);
        if (forced) result.add(CONFIG_HOLDER);
        for (Map.Entry<String, Long> entry : LEASES.entrySet()) {
            if (entry.getValue() > now) result.add(entry.getKey());
        }
        return result;
    }

    /** @param realMillis monotonicity is not required; a backwards step is skipped */
    public static void advance(long realMillis, boolean paused) {
        long previous = lastRealMillis;
        lastRealMillis = realMillis;

        if (previous < 0) return; // the first step only sets the origin
        if (paused) return;

        long delta = realMillis - previous;
        if (delta <= 0) return;
        if (delta > MAX_STEP_MILLIS) delta = MAX_STEP_MILLIS;

        now += delta;
        refresh();
    }

    /**
     * The clock resets too, or the next session starts with accumulated time and its first lease
     * expires in the past.
     */
    public static void reset() {
        boolean was = suppressed;
        LEASES.clear();
        now = 0L;
        lastRealMillis = -1L;
        // forced is NOT reset: config state, not session state, and resetting it would give
        // listeners a spurious transition on every world join.
        suppressed = forced;
        if (was != suppressed) TiltListeners.suppressionChanged(suppressed, holders());
    }

    private static void refresh() {
        // Expiry must run regardless of the override, or switching the override off resurrects a
        // lease that expired long ago.
        boolean anyLease = false;
        for (Iterator<Map.Entry<String, Long>> it = LEASES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Long> entry = it.next();
            if (entry.getValue() <= now) it.remove();
            else anyLease = true;
        }

        boolean any = anyLease || forced;
        if (any == suppressed) return;
        suppressed = any;
        // Direct call: TiltListeners pulls in no Minecraft types either.
        TiltListeners.suppressionChanged(any, holders());
    }

    /** Test-only. */
    static long currentTime() {
        return now;
    }
}
