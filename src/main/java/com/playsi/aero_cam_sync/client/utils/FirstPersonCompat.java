package com.playsi.aero_cam_sync.client.utils;

import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Мягкая (рефлексивная) интеграция с модом First-person Model (tr7zw, mod id
 * {@code firstperson}). Жёсткой зависимости НЕТ: если мод не установлен — всё всегда
 * возвращает {@code false}, и наш код не трогает ничего лишнего.
 *
 * <p><b>Зачем.</b> First-person Model рендерит реальное тело игрока от первого лица
 * через обычный {@code PlayerRenderer}, сдвигая его за камеру. Наша камера наклоняется
 * вокруг ног (поворот + сдвиг позиции), а тело остаётся вертикальным — из-за этого
 * голова «отрывается» от тела. Чтобы голова осталась под камерой, тело нужно наклонить
 * тем же тилтом вокруг ног — но только в момент, когда мод рендерит тело от первого
 * лица (чужих игроков и вид от третьего лица не трогаем). Это и проверяет
 * {@link #isRenderingFirstPersonBody()} через {@code FirstPersonAPI.isRenderingPlayer()}.</p>
 */
public final class FirstPersonCompat {

    private FirstPersonCompat() {}

    /** Установлен ли First-person Model (определяется один раз). */
    private static final boolean PRESENT =
            ModList.get() != null && ModList.get().isLoaded("firstperson");

    private static boolean resolved = false;
    private static Method isRenderingPlayerMethod; // FirstPersonAPI#isRenderingPlayer()
    private static boolean usable = false;

    public static boolean isLoaded() {
        return PRESENT;
    }

    /**
     * @return {@code true} только если First-person Model установлен И прямо сейчас
     *         рендерит тело локального игрока от первого лица.
     */
    public static boolean isRenderingFirstPersonBody() {
        if (!PRESENT) return false;
        ensureResolved();
        if (!usable) return false;
        try {
            return (boolean) isRenderingPlayerMethod.invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void ensureResolved() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> api = Class.forName("dev.tr7zw.firstperson.api.FirstPersonAPI");
            isRenderingPlayerMethod = api.getMethod("isRenderingPlayer");
            usable = true;
        } catch (Throwable ignored) {
            // другой/отсутствующий API — компат просто выключается
            usable = false;
        }
    }
}
