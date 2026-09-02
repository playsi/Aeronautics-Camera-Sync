package com.playsi.aero_cam_sync.client.config.categories;

import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.config.entries.SeparatorEntry;
import com.playsi.aero_cam_sync.client.config.entries.ThresholdEntry;
import com.playsi.aero_cam_sync.client.config.entries.ToggleButtonEntry;
import com.playsi.aero_cam_sync.client.config.ui.ConfigCategory;

/**
 * Tilt activation thresholds, based on the properties of the sub-level the player stands on. Each
 * criterion is one compact row: a toggle plus a "greater or equal" value. All are off by default,
 * so the tilt behaves as before.
 */
public class ActivationCategory {

    public static ConfigCategory build() {
        ConfigCategory cat = new ConfigCategory("aero_cam_sync.configuration.activation");

        cat.add(new SeparatorEntry("aero_cam_sync.configuration.activation.physical"));

        cat.add(new ThresholdEntry(
                "aero_cam_sync.configuration.gateMass",
                "aero_cam_sync.configuration.gateMass.tooltip",
                Config.GATE_MASS_ENABLED, Config.GATE_MASS_MIN,
                0.0, 1000.0, 1.0, 0.0, 100_000_000.0));

        cat.add(new ThresholdEntry(
                "aero_cam_sync.configuration.gateBlocks",
                "aero_cam_sync.configuration.gateBlocks.tooltip",
                Config.GATE_BLOCKS_ENABLED, Config.GATE_BLOCKS_MIN,
                0, 1000, 1, 0, 100_000_000));

        cat.add(new SeparatorEntry("aero_cam_sync.configuration.activation.size"));

        cat.add(new ThresholdEntry(
                "aero_cam_sync.configuration.gateLength",
                "aero_cam_sync.configuration.gateLength.tooltip",
                Config.GATE_LENGTH_ENABLED, Config.GATE_LENGTH_MIN,
                1, 64, 1, 1, 100_000));

        cat.add(new ThresholdEntry(
                "aero_cam_sync.configuration.gateHeight",
                "aero_cam_sync.configuration.gateHeight.tooltip",
                Config.GATE_HEIGHT_ENABLED, Config.GATE_HEIGHT_MIN,
                1, 64, 1, 1, 100_000));

        cat.add(new ThresholdEntry(
                "aero_cam_sync.configuration.gateWidth",
                "aero_cam_sync.configuration.gateWidth.tooltip",
                Config.GATE_WIDTH_ENABLED, Config.GATE_WIDTH_MIN,
                1, 64, 1, 1, 100_000));

        cat.add(new SeparatorEntry("aero_cam_sync.configuration.activation.misc"));

        cat.add(new ToggleButtonEntry(
                "aero_cam_sync.configuration.disableOnFlying",
                "aero_cam_sync.configuration.disableOnFlying.tooltip",
                Config.DISABLE_ON_FLYING));

        return cat;
    }
}
