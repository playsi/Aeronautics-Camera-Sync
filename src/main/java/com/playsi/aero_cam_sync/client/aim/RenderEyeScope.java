package com.playsi.aero_cam_sync.client.aim;

/**
 * The window inside which Sable's funnel must return the REAL eye. The tilt correction applies
 * everywhere else, and the exceptions are exactly two Sable render paths in {@code EntityRenderer}:
 * {@code getPackedLightCoords} (or lighting drifts) and {@code shouldRender} (or nameplates and
 * culling do).
 *
 * <p>A mistake in that list spoils the picture rather than the aim. Particles, sound and water
 * occlusion have not been surveyed: if they ask the funnel too, they belong here.
 *
 * <p>A counter, not a flag: the calls nest.
 */
public final class RenderEyeScope {

    private RenderEyeScope() {}

    private static int depth = 0;

    public static void enter() {
        depth++;
    }

    public static void exit() {
        if (depth > 0) depth--;
    }

    public static boolean isActive() {
        return depth > 0;
    }

    /** An unclosed window would disable the tilt permanently; a pick is the natural reset. */
    public static void reset() {
        depth = 0;
    }
}
