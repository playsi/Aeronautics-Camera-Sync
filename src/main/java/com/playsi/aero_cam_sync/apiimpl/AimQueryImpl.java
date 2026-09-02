package com.playsi.aero_cam_sync.apiimpl;

import com.playsi.aero_cam_sync.api.AimQuery;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The facts about one ray, for {@code AimPolicy}.
 *
 * <p>Built ONLY when policies are registered: {@code decide} is called from {@code clip} dozens of
 * times per frame, and the common case, with no policies, must cost one comparison and no
 * allocation.
 */
record AimQueryImpl(Player player, Vec3 from, Vec3 to,
                    @Nullable ClipContext context, boolean startsAtEye) implements AimQuery {
}
