package com.playsi.aero_cam_sync.apiimpl;

import net.minecraft.world.phys.Vec3;

/**
 * The eye-offset ceiling. The offset is the aim ray ORIGIN, and the server measures interaction
 * reach from it, so an unbounded one reaches through walls.
 *
 * <p>Guards against a MISTAKE, not a cheater: the clamp runs client-side before sending, and a
 * modified client simply will not apply it.
 */
public final class EyeOffsetClamp {

    private EyeOffsetClamp() {}

    /**
     * Do not lower this. A mod flipping a player about the neck legitimately produces ~3.2 blocks,
     * so anything tighter breaks it silently.
     */
    public static final double MAX_BLOCKS = 4.0;

    /** Clamped, not zeroed: "the mod overdid it" is diagnosable, "the mod broke" is not. */
    public static Vec3 apply(boolean deliberate, Vec3 offset, double length) {
        if (length <= MAX_BLOCKS) return offset;
        if (deliberate) return offset;
        return offset.scale(MAX_BLOCKS / length);
    }
}
