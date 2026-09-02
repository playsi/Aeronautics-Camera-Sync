package com.playsi.aero_cam_sync;

import net.minecraft.world.level.Level;

import java.util.function.Supplier;

/**
 * Side and thread checks for {@link ClipNet}: the one place where common code looks into client
 * classes.
 *
 * <p>References to the client package execute only inside the {@code isClientSide} branch, so a
 * dedicated server never loads them, the same trick as in {@link TiltAccess}.
 */
final class SideGate {

    private SideGate() {}

    /**
     * Whether the call comes from this side's own thread.
     *
     * <p>Aiming is main-thread logic. Background rays must not be touched: Sound Physics Perfected
     * traces sound from a thread pool through {@code world.clip()}, and stepping in there means
     * reading main-thread objects from another thread and re-entering Sable's non-atomic clip, and the
     * client hung outright (Issue #24).
     */
    static boolean isOwnThread(Level level) {
        if (level.isClientSide) {
            return com.playsi.aero_cam_sync.client.tilt.ClientTiltAccess.isRenderThread();
        }
        return level.getServer() != null && level.getServer().isSameThread();
    }

    /**
     * Diagnostics: prints EVERY new caught caller exactly once. Client only, since the setting that
     * gates it lives in the client config.
     */
    static void reportCatch(Level level, Supplier<String> caller, double offsetLength) {
        if (!level.isClientSide) return;
        com.playsi.aero_cam_sync.client.tilt.ClientTiltAccess.reportClipNetCatch(caller, offsetLength);
    }
}
