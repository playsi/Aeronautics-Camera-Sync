package com.playsi.aero_cam_sync.client.config.categories;

import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.config.entries.*;
import com.playsi.aero_cam_sync.client.config.ui.ConfigCategory;

public class DebugCategory {
public static ConfigCategory build() {
    ConfigCategory debug = new ConfigCategory("aero_cam_sync.configuration.debug");
        debug.add(new SeparatorEntry("Samples ray"));

        debug.add(new ToggleButtonEntry(
                "aero_cam_sync.configuration.rays",
                        "aero_cam_sync.configuration.rays.tooltip",
                Config.DEBUG_RAYS));

        debug.add(new SeparatorEntry("Pick ray"));

        debug.add(new ToggleButtonEntry(
                "aero_cam_sync.configuration.pickRays",
                        "aero_cam_sync.configuration.pickRays.tooltip",
                Config.DEBUG_PICK_RAYS));

        debug.add(new SliderEntry(
                "aero_cam_sync.configuration.pickRaysTimeSec",
                        "aero_cam_sync.configuration.pickRaysTimeSec.tooltip",
                Config.PICK_RAYS_TIME_SEC,
                0.1, 60.0, 0.1,
                        0.01, 600.0));

        debug.add(new SeparatorEntry(""));

        debug.add(new ToggleButtonEntry(
                "aero_cam_sync.configuration.debugMessages",
                        "",
                Config.DEBUG_MESSAGES));
        return (debug);
    }
}
