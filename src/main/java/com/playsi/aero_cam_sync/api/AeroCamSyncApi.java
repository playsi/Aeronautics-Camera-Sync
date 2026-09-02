package com.playsi.aero_cam_sync.api;

import com.playsi.aero_cam_sync.apiimpl.HandleRegistry;

/**
 * Entry point of the ACS API.
 *
 * <pre>{@code
 * private static final AcsHandle ACS = AeroCamSyncApi.forMod("mymod");
 * }</pre>
 *
 * @see AcsHandle
 */
public final class AeroCamSyncApi {

    private AeroCamSyncApi() {}

    /**
     * The handle for a mod. Same id, same object, so keep it in a static field.
     *
     * <p>The id goes in the ACS log next to everything your mod asks for, so pass the real mod id
     * and not a display name.
     *
     * @param modId your mod id, non-blank
     * @throws IllegalArgumentException if {@code modId} is null or blank
     */
    public static AcsHandle forMod(String modId) {
        return HandleRegistry.forMod(modId);
    }
}
