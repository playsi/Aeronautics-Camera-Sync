package com.playsi.aero_cam_sync;

import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.tilt.ClientTiltAccess;

/**
 * Side state for the current session. State only: keep transmission out, or this common
 * class becomes client-side by dependency.
 *
 * <p>{@code IGNORE_SERVER} is only read through {@code Config.isLoaded()}: on a dedicated server its
 * spec is not registered and {@code .get()} throws before load (Issues #19, #33).
 */
public class SideManager {

    public enum Side {
        UNKNOWN,
        CLIENT_ONLY,
        CLIENT_SERVER
    }

    private static Side currentSide = Side.UNKNOWN;

    /**
     * Latched on world entry. The option MUST NOT be read live: toggling it mid-game silences tilt
     * transmission while the server keeps firing by the LAST tilt it received, since nothing clears
     * {@link ServerTiltStore}.
     */
    private static boolean ignoreServerSession = false;

    public static Side getSide() {
        return currentSide;
    }

    public static void setSide(Side side) {
        // Through the accessor: a direct .get() here throws before the config loads.
        if (ClientTiltAccess.isDebugMessages()) {
            AeroCamSync.LOGGER.info("[AeroCamSync] SideManager -> {}", side);
        }
        currentSide = side;
    }

    public static boolean isIgnoreServerSession() {
        return ignoreServerSession;
    }

    /** For the "rejoin needed" hint on the settings screen. */
    public static boolean isIgnoreServerPending() {
        return currentSide != Side.UNKNOWN
                && Config.isLoaded()
                && Config.IGNORE_SERVER.get() != ignoreServerSession;
    }

    public static boolean isClientOnly() {
        if (ignoreServerSession) {
            return true;
        }

        return currentSide != Side.CLIENT_SERVER;
    }

    public static boolean isClientServer() {
        if (ignoreServerSession) {
            return false;
        }

        return currentSide == Side.CLIENT_SERVER;
    }

    /** Entering a world: reset the side and latch client-only for the session. */
    public static void beginSession() {
        currentSide = Side.UNKNOWN;
        latchIgnoreServer();
        if (ClientTiltAccess.isDebugMessages()) {
            AeroCamSync.LOGGER.info("[AeroCamSync] Session started, clientOnly latched = {}", ignoreServerSession);
        }
    }

    public static void reset() {
        if (ClientTiltAccess.isDebugMessages()) {
            AeroCamSync.LOGGER.info("[AeroCamSync] SideManager reset (disconnect)");
        }
        currentSide = Side.UNKNOWN;
        // Kept current outside a world so the "rejoin needed" hint clears.
        latchIgnoreServer();
    }

    private static void latchIgnoreServer() {
        if (Config.isLoaded()) {
            ignoreServerSession = Config.IGNORE_SERVER.get();
        }
    }
}
