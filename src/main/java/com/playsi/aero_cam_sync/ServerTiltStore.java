package com.playsi.aero_cam_sync;

import org.joml.Quaternionf;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerTiltStore {

    private ServerTiltStore() {}

    // Храним только для игроков с активным тилтом
    private static final Map<UUID, Quaternionf> tilts = new ConcurrentHashMap<>();
    // Включён ли поворот камеры (MODIFY_CAMERA_ROT) — наклоняет направление взгляда/снаряда.
    private static final Map<UUID, Boolean> rotFlags = new ConcurrentHashMap<>();
    // Включён ли сдвиг позиции камеры (MODIFY_CAMERA_POS) — сдвигает точку вылета снаряда.
    private static final Map<UUID, Boolean> posFlags = new ConcurrentHashMap<>();
    // Выбрасывать ли предметы из камеры (DROP_FROM_CAMERA).
    private static final Map<UUID, Boolean> dropFlags = new ConcurrentHashMap<>();

    public static void set(UUID playerId, Quaternionf q, boolean rotActive, boolean posShift, boolean dropFromCamera) {
        tilts.put(playerId, new Quaternionf(q)); // копируем!
        rotFlags.put(playerId, rotActive);
        posFlags.put(playerId, posShift);
        dropFlags.put(playerId, dropFromCamera);
    }

    public static void clear(UUID playerId) {
        tilts.remove(playerId);
        rotFlags.remove(playerId);
        posFlags.remove(playerId);
        dropFlags.remove(playerId);
    }

    /**
     * Тилт для НАКЛОНА НАПРАВЛЕНИЯ (взгляд, направление снаряда, лучи предметов).
     * {@code null}, если поворот камеры выключен — тогда направление остаётся ванильным.
     */
    public static Quaternionf getLookTilt(UUID playerId) {
        return Boolean.TRUE.equals(rotFlags.get(playerId)) ? tilts.get(playerId) : null;
    }

    /**
     * Тилт для СДВИГА ТОЧКИ ВЫЛЕТА снаряда вокруг ног игрока.
     * {@code null}, если сдвиг позиции камеры выключен.
     */
    public static Quaternionf getPosTilt(UUID playerId) {
        return Boolean.TRUE.equals(posFlags.get(playerId)) ? tilts.get(playerId) : null;
    }

    /** Включён ли у игрока выброс предметов из камеры. */
    public static boolean getDropFromCamera(UUID playerId) {
        return Boolean.TRUE.equals(dropFlags.get(playerId));
    }

    /** Вызывать при выходе игрока, чтобы не текло */
    public static void onPlayerLeave(UUID playerId) {
        clear(playerId);
    }
}
