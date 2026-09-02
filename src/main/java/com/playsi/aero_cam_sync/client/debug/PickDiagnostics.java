package com.playsi.aero_cam_sync.client.debug;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Last frame's pick results, so a click can compare against the same frame. The sub-level drifts,
 * making a cross-frame comparison meaningless. A separate class because a mixin may not hold
 * non-private static fields.
 */
public final class PickDiagnostics {

    public static volatile String lastTilted = "-";
    public static volatile String lastVanilla = "-";

    public static volatile String lastEyeDelta = "-";

    /** If not, which gate closed it. No window means a fully vanilla pick. */
    public static volatile String lastScope = "-";

    public static volatile String lastPickTail = "-";

    /** How many {@code pick(F)} calls since the last click; more than one means a stray pick. */
    public static volatile int picksThisFrame = 0;

    /**
     * The ONE place that decides whether diagnostics run. The gate lives here rather than at the
     * call sites, because the callers ship and forget to ask: {@link #recordEyeDelta} was doing a
     * {@code String.format} on every ray "from the eye".
     */
    public static boolean enabled() {
        return com.playsi.aero_cam_sync.client.tilt.ClientTiltAccess.isDebugMessages();
    }

    public static void recordScope(String scope) {
        if (!enabled()) return;
        lastScope = scope;
    }

    public static void recordScopeDenied(String reason) {
        if (!enabled()) return;
        lastScope = "off:" + reason;
    }

    public static void resetPickCount() {
        if (!enabled()) return;
        picksThisFrame = 0;
    }

    /**
     * Divergence of the new origin from the old one. {@code from} may be {@code null}: the window
     * is closed and there is nothing to compare against.
     */
    public static void recordEyeDelta(@Nullable Vec3 from, Vec3 to) {
        if (from == null || !enabled()) return;
        lastEyeDelta = String.format("%.3f", from.distanceTo(to));
    }

    /**
     * Prints each unique caller exactly once. Timer sampling gave an incomplete picture: with two
     * calls per frame, every Nth landed in the same position and the second caller never reached
     * the log.
     */
    public static void logOnce(String tag, String caller) {
        if (seen.add(tag + "|" + caller)) {
            com.playsi.aero_cam_sync.AeroCamSync.LOGGER.info("[AeroCamSync] {}: {}", tag, caller);
        }
    }

    private static final java.util.Set<String> seen =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static String caller() {
        return StackWalker.getInstance().walk(frames -> frames
                .map(f -> f.getClassName() + "#" + f.getMethodName())
                .filter(s -> !s.startsWith("java.lang.StackWalker"))
                .filter(s -> !s.contains("$aero_cam_sync$") && !s.contains("aero$"))
                .filter(s -> !s.contains("mixinextras$") && !s.contains("$mixinextras"))
                .limit(8)
                .reduce((a, b) -> a + "\n      <- " + b)
                .orElse("?"));
    }

    private PickDiagnostics() {
    }
}
