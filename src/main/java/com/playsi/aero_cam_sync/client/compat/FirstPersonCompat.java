package com.playsi.aero_cam_sync.client.compat;

import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Soft (reflective) integration with the First-person Model mod (tr7zw, mod id
 * {@code firstperson}). There is NO hard dependency: without the mod everything returns
 * {@code false} and ACS touches nothing.
 *
 * <p>First-person Model renders the player's real body in first person through the
 * ordinary {@code PlayerRenderer}, shifted behind the camera. The ACS camera rotates about the feet
 * while the body stays upright, so the head comes away from it. Keeping the head under the camera
 * means rotating the body by the same tilt about the same pivot, but only while the mod is
 * rendering the first-person body: other players and third person are left alone. That is what
 * {@link #isRenderingFirstPersonBody()} checks.
 */
public final class FirstPersonCompat {

    private FirstPersonCompat() {}

    private static final boolean PRESENT =
            ModList.get() != null && ModList.get().isLoaded("firstperson");

    private static boolean resolved = false;
    private static Method isRenderingPlayerMethod; // FirstPersonAPI#isRenderingPlayer()
    private static boolean usable = false;

    public static boolean isLoaded() {
        return PRESENT;
    }

    /**
     * @return {@code true} only if First-person Model is installed AND is right now rendering the
     *         local player's body in first person
     */
    public static boolean isRenderingFirstPersonBody() {
        if (!PRESENT) return false;
        ensureResolved();
        if (!usable) return false;
        try {
            return (boolean) isRenderingPlayerMethod.invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void ensureResolved() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> api = Class.forName("dev.tr7zw.firstperson.api.FirstPersonAPI");
            isRenderingPlayerMethod = api.getMethod("isRenderingPlayer");
            usable = true;
        } catch (Throwable ignored) {
            // a different or missing API: the compat simply switches off
            usable = false;
        }
    }
}
