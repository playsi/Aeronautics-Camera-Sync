package com.playsi.aero_cam_sync.apiimpl;

import com.playsi.aero_cam_sync.AeroCamSync;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The log of everything a foreign mod initiated. The asterisk and {@code modId} are mandatory: in an
 * "ACS broke my mod" report the first line must say who called in at all.
 *
 * <pre>
 * [AeroCamSync] * mymod: api handle acquired
 * </pre>
 *
 * <p>Deduplication is by TEMPLATE: the key is {@code modId} plus the format WITHOUT substituted
 * values, or one line per lease duration would be printed.
 */
public final class ApiLog {

    private ApiLog() {}

    /** {@code modId + '\0' + format}. */
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    /** Always printed, no debug setting required, once per (mod, event kind) per session. */
    public static void event(String modId, String format, Object... args) {
        if (!firstTime(modId, format)) return;
        AeroCamSync.LOGGER.info("[AeroCamSync] * {}: " + format, prepend(modId, args));
    }

    /** As above, as a warning. */
    public static void warn(String modId, String format, Object... args) {
        if (!firstTime(modId, format)) return;
        AeroCamSync.LOGGER.warn("[AeroCamSync] * {}: " + format, prepend(modId, args));
    }

    /** As above, only under {@code DEBUG_MESSAGES}. */
    public static void debug(String modId, String format, Object... args) {
        if (!debugEnabled()) return;
        if (!firstTime(modId, format)) return;
        AeroCamSync.LOGGER.info("[AeroCamSync] * {}: " + format, prepend(modId, args));
    }

    private static boolean firstTime(String modId, String format) {
        return SEEN.add(modId + '\0' + format);
    }

    private static Object[] prepend(String modId, Object[] args) {
        Object[] full = new Object[args.length + 1];
        full[0] = modId;
        System.arraycopy(args, 0, full, 1, args.length);
        return full;
    }

    /** The next session must see the first lines again. */
    public static void resetSession() {
        SEEN.clear();
        lastSummaryNs = System.nanoTime();
    }

    private static final long SUMMARY_PERIOD_NS = 30_000_000_000L;

    /** {@code nanoTime()} per call would cost more than the counter itself. */
    private static final int CLOCK_CHECK_MASK = 0x3FF;

    private static int sinceClockCheck = 0;
    private static long lastSummaryNs = System.nanoTime();

    /**
     * A hot path: calls are never logged individually, only summarised. The counters are
     * not atomic: statistics, not state, and a CAS costs more than a lost increment.
     */
    static void count(AcsHandleImpl handle, AcsHandleImpl.Call call) {
        handle.counters[call.ordinal()]++;

        if ((++sinceClockCheck & CLOCK_CHECK_MASK) != 0) return;
        if (!debugEnabled()) return;

        long now = System.nanoTime();
        if (now - lastSummaryNs < SUMMARY_PERIOD_NS) return;
        lastSummaryNs = now;

        for (AcsHandleImpl h : HandleRegistry.all()) h.logSummaryAndReset();
    }

    /**
     * The client-package reference executes only under a side check, so a dedicated server never
     * loads {@code ClientTiltAccess}.
     */
    static boolean debugEnabled() {
        if (FMLEnvironment.dist != Dist.CLIENT) return false;
        return com.playsi.aero_cam_sync.client.tilt.ClientTiltAccess.isDebugMessages();
    }
}
