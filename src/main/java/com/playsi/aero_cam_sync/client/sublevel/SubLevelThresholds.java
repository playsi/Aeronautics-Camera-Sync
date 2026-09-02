package com.playsi.aero_cam_sync.client.sublevel;

import com.playsi.aero_cam_sync.client.config.Config;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;

/**
 * Decides whether the camera tilt is allowed on a given sub-level, by the activation thresholds.
 *
 * <p>Each enabled criterion requires {@code value >= threshold}, and the tilt applies only if ALL
 * enabled criteria pass. With none enabled the tilt is always allowed, which is the default. Mass is
 * the primary criterion.
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
            // Do not block the tilt when Sable did not supply the data.
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
