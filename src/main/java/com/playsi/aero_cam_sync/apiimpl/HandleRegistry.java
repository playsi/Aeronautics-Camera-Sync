package com.playsi.aero_cam_sync.apiimpl;

import com.playsi.aero_cam_sync.api.AcsHandle;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles by {@code modId}, one per mod.
 *
 * <p>{@link ConcurrentHashMap}: a mod can call {@code forMod} from anywhere (a static initialiser,
 * client setup, a server tick), and the map is shared by both sides.
 */
public final class HandleRegistry {

    private HandleRegistry() {}

    private static final ConcurrentHashMap<String, AcsHandleImpl> HANDLES = new ConcurrentHashMap<>();

    public static AcsHandle forMod(String modId) {
        // A programmer error, not a user one: fail here and loudly, or the log gets a handle with
        // an empty name and all modId-based diagnostics become meaningless.
        if (modId == null || modId.isBlank()) {
            throw new IllegalArgumentException("AeroCamSyncApi.forMod: modId must not be null or blank");
        }
        return HANDLES.computeIfAbsent(modId, AcsHandleImpl::new);
    }

    /** Every handle issued, for the call summary. */
    static Collection<AcsHandleImpl> all() {
        return HANDLES.values();
    }
}
