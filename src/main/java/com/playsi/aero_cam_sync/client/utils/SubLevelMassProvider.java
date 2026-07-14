package com.playsi.aero_cam_sync.client.utils;

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
 * Отдаёт массу сабвела БЕЗ пересчёта: Sable ведёт массу инкрементально на сервере
 * ({@link ServerSubLevel#getMassTracker()}), её достаточно прочитать (O(1)).
 *
 * <p>В одиночной игре читаем готовую массу прямо у серверного сабвела встроенного
 * сервера. На удалённом сервере серверного сабвела под рукой нет — там как запасной
 * вариант собираем массу {@link MassTracker#build} с РЕДКИМ кэшем (чтобы не было
 * ежесекундных фризов на больших конструкциях).</p>
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

    /** Готовая масса из серверного сабвела (только встроенный сервер). */
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
            // не блокируем — отдадим запасной вариант
        }
        return null;
    }

    /** Запасной вариант для удалённого сервера: редкий пересчёт. */
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
