package com.playsi.aero_cam_sync.client.sublevel;

import dev.ryanhcode.sable.api.physics.mass.MassData;
import dev.ryanhcode.sable.api.physics.mass.MassTracker;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Serves a sub-level's mass WITHOUT recomputing it: Sable tracks mass incrementally on the server
 * ({@link ServerSubLevel#getMassTracker()}) and it only needs reading, in O(1).
 *
 * <p>In singleplayer the ready mass is read straight from the integrated server's sub-level. On a
 * remote server there is no server sub-level to hand, so the fallback builds the mass with
 * {@link MassTracker#build} behind an INFREQUENT cache, to avoid per-second freezes on large
 * constructions.
 */
public final class SubLevelMassProvider {

    private SubLevelMassProvider() {}

    private static final long BUILD_CACHE_MS = 5000;

    private static UUID builtId = null;
    private static long builtAtMs = Long.MIN_VALUE;
    private static double builtMass = 0.0;

    public static double getMass(ClientSubLevel sl, BoundingBox3ic bounds) {
        Double ready = readyServerMass(sl);
        if (ready != null) return ready;
        return builtMassCached(sl, bounds);
    }

    private static Double readyServerMass(ClientSubLevel sl) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.hasSingleplayerServer()) return null;
        try {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server == null) return null;
            ServerLevel serverLevel = server.getLevel(sl.getLevel().dimension());
            if (serverLevel == null) return null;
            ServerSubLevelContainer container = SubLevelContainer.getContainer(serverLevel);
            if (container == null) return null;
            SubLevel ssl = container.getSubLevel(sl.getUniqueId());
            if (ssl instanceof ServerSubLevel serverSubLevel) {
                MassData md = serverSubLevel.getMassTracker();
                if (md != null) return md.getMass();
            }
        } catch (Throwable ignored) {
            // do not block: the fallback will be served
        }
        return null;
    }

    /** Fallback for a remote server: an infrequent recompute. */
    private static double builtMassCached(ClientSubLevel sl, BoundingBox3ic bounds) {
        UUID id = sl.getUniqueId();
        long now = System.currentTimeMillis();
        if (id.equals(builtId) && now - builtAtMs < BUILD_CACHE_MS) {
            return builtMass;
        }
        builtMass = MassTracker.build(sl.getLevel(), bounds).getMass();
        builtId = id;
        builtAtMs = now;
        return builtMass;
    }
}
