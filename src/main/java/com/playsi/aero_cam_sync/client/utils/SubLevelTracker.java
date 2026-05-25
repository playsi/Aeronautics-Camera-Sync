package com.playsi.aero_cam_sync.client.utils;

import com.playsi.aero_cam_sync.client.config.Config;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;

import javax.annotation.Nullable;

public final class SubLevelTracker {

    private SubLevelTracker() {}

    private static @Nullable ClientSubLevel cachedSubLevel = null;

    public static @Nullable ClientSubLevel getClientSubLevel(LocalPlayer player) {
        SubLevel subLevel = Sable.HELPER.getTrackingOrVehicleSubLevel(player);

        if (player.getAbilities().flying && Config.DISABLE_ON_FLYING.get() ) {
            cachedSubLevel = null;
            return null;
        }

        if (subLevel != null) {
            cachedSubLevel = subLevel instanceof ClientSubLevel csl ? csl : null;
            return cachedSubLevel;
        }

        if (!player.onGround() ) {
            return cachedSubLevel;
        }

        return null;
    }

    public static void invalidateCache() {
        cachedSubLevel = null;
    }

    public static @Nullable ClientSubLevel getCachedSubLevel() {
        return cachedSubLevel;
    }
}
