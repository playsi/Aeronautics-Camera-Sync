package com.playsi.aero_cam_sync.client.aim;

import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The window inside which "the player's eye" is the tilted camera position. Opened around
 * {@code GameRenderer#pick(F)}, so everything in the pick gets an origin matching the crosshair
 * without knowing about ACS.
 *
 * <p>Static and main-thread only: a pick lives on the render thread, and background rays are kept
 * out by both the opener and the reader.
 */
public final class PickScope {

    private PickScope() {}

    private static boolean active = false;

    @Nullable
    private static Vec3 origin = null;

    private static int substitutions = 0;

    public static void open(Vec3 pickOrigin) {
        origin = pickOrigin;
        substitutions = 0;
        active = true;
    }

    public static void close() {
        active = false;
        origin = null;
    }

    public static void countSubstitution() {
        substitutions++;
    }

    /** Zero means the window opened but the funnel was never reached: the ray was built past it. */
    public static int substitutions() {
        return substitutions;
    }

    public static boolean isActive() {
        return active;
    }

    @Nullable
    public static Vec3 origin() {
        return origin;
    }
}
