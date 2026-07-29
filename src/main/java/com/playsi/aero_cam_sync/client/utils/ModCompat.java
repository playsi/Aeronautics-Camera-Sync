package com.playsi.aero_cam_sync.client.utils;

import net.neoforged.fml.ModList;

/**
 * Наличие сторонних модов, поведение которых мы обязаны сохранить.
 *
 * <p>Проверка ленивая и закэширована: {@link ModList#get()} на этапе загрузки класса может
 * быть ещё не готов, а дёргать его каждый кадр из пика — лишнее.</p>
 */
public final class ModCompat {

    private ModCompat() {}

    private static Boolean cutThrough = null;

    /**
     * Cut Through (fuzss) — позволяет бить сквозь блоки без коллизии (трава и т.п.),
     * не ломая их. Он правит {@code GameRenderer#pick}, повторяя блочный клип с
     * {@code ClipContext.Block.COLLIDER}; наш re-pick перезаписывает {@code mc.hitResult}
     * целиком, поэтому при его наличии мы обязаны сами считать окклюзию по COLLIDER —
     * иначе функционал мода пропадает.
     */
    public static boolean cutThroughLoaded() {
        Boolean cached = cutThrough;
        if (cached != null) return cached;

        boolean loaded;
        try {
            loaded = ModList.get() != null && ModList.get().isLoaded("cutthrough");
        } catch (Throwable ignored) {
            return false; // слишком рано — не кэшируем, спросим на следующем кадре
        }
        cutThrough = loaded;
        return loaded;
    }
}
