package com.playsi.aero_cam_sync.api;

import net.minecraft.world.phys.Vec3;

/**
 * A ray, in the form you would put into a {@code ClipContext}.
 *
 * <pre>{@code
 * AcsRay ray = state.aimRay(player.blockInteractionRange());
 * level.clip(new ClipContext(ray.from(), ray.to(),
 *         ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
 * }</pre>
 */
public interface AcsRay {

    /** Where the ray starts. */
    Vec3 from();

    /** Where the ray ends. */
    Vec3 to();

    /** Normalised direction from {@link #from()} to {@link #to()}. */
    Vec3 direction();
}
