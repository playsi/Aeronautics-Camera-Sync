package com.playsi.aero_cam_sync;

import org.joml.Quaternionf;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerTiltStore {

    private ServerTiltStore() {}

    // Храним только для игроков с активным тилтом
    private static final Map<UUID, Quaternionf> tilts = new ConcurrentHashMap<>();

    public static void set(UUID playerId, Quaternionf q) {
        tilts.put(playerId, new Quaternionf(q)); // копируем!
    }

    public static void clear(UUID playerId) {
        tilts.remove(playerId);
    }

    /** null → тилт не применяется */
    public static Quaternionf get(UUID playerId) {
        return tilts.get(playerId);
    }

    /** Вызывать при выходе игрока, чтобы не текло */
    public static void onPlayerLeave(UUID playerId) {
        tilts.remove(playerId);
    }
}