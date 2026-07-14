package com.playsi.aero_cam_sync.client.utils;

import com.playsi.aero_cam_sync.client.config.Config;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;

/**
 * Решает, разрешён ли наклон камеры на данном сабвеле по порогам активации.
 *
 * <p>Каждый включённый критерий требует {@code значение >= порог}. Наклон
 * применяется только если ВСЕ включённые критерии пройдены. Если ни один не
 * включён — наклон разрешён всегда (поведение по умолчанию). Масса — основной
 * критерий.</p>
 */
public final class SubLevelThresholds {

    private SubLevelThresholds() {}

    public static boolean anyEnabled() {
        return Config.GATE_MASS_ENABLED.get()
                || Config.GATE_BLOCKS_ENABLED.get()
                || Config.GATE_LENGTH_ENABLED.get()
                || Config.GATE_HEIGHT_ENABLED.get()
                || Config.GATE_WIDTH_ENABLED.get();
    }

    public static boolean passes(ClientSubLevel sl) {
        if (!anyEnabled()) return true;
        if (sl == null) return true;

        SubLevelMetrics.Metrics m;
        try {
            m = SubLevelMetrics.get(sl);
        } catch (Throwable t) {
            // Не блокируем наклон, если Sable не отдал данные.
            return true;
        }

        if (Config.GATE_MASS_ENABLED.get()   && m.mass()   < Config.GATE_MASS_MIN.get())   return false;
        if (Config.GATE_BLOCKS_ENABLED.get() && m.blocks() < Config.GATE_BLOCKS_MIN.get())  return false;
        if (Config.GATE_LENGTH_ENABLED.get() && m.length() < Config.GATE_LENGTH_MIN.get())  return false;
        if (Config.GATE_HEIGHT_ENABLED.get() && m.height() < Config.GATE_HEIGHT_MIN.get())  return false;
        if (Config.GATE_WIDTH_ENABLED.get()  && m.width()  < Config.GATE_WIDTH_MIN.get())   return false;
        return true;
    }
}
