package com.playsi.aero_cam_sync.apiimpl;

import com.playsi.aero_cam_sync.api.ConditionContext;
import net.minecraft.world.entity.player.Player;

/**
 * Phase 1's input: everything known about the frame BEFORE anything has been decided about it.
 *
 * <p>Three fields, and that is not a stub awaiting extension. There is no normal here because the
 * ray has not been cast, and no {@code acsTilt} because the ACS tilt is not computed and whether
 * it will be is partly being decided right now. Either value placed here would be a frame stale,
 * and a modder would read it as current.
 *
 * <p>Like {@link TiltContextImpl}, not one client type: {@code AcsConditions} is registered through
 * the common {@code AcsHandle}, so everything in its signatures loads on a dedicated server too.
 */
record ConditionContextImpl(Player player,
                            float partialTick,
                            boolean firstPerson) implements ConditionContext {
}
