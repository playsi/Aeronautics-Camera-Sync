package com.playsi.aero_cam_sync.client.config.categories;

import com.playsi.aero_cam_sync.client.KeyBindings;
import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.config.entries.*;
import com.playsi.aero_cam_sync.client.config.ui.ConfigCategory;

public class GeneralCategory {

    public static ConfigCategory build() {

        ConfigCategory general = new ConfigCategory("aero_cam_sync.configuration.general");
        general.add(new ToggleButtonEntry(
                "aero_cam_sync.configuration.enabled",
                "aero_cam_sync.configuration.enabled.tooltip",
                Config.MOD_ENABLED));

        general.add(new KeyBindEntry(
                "aero_cam_sync.configuration.toggleKey",
                "aero_cam_sync.configuration.toggleKey.tooltip",
                KeyBindings.TOGGLE,
                Config.TOGGLE_KEY));

        general.add(new SeparatorEntry());

        general.add(new ToggleButtonEntry(
                "aero_cam_sync.configuration.allow3rdPerson",
                "aero_cam_sync.configuration.allow3rdPerson.tooltip",
                Config.ALLOW_3RD_PERSON));

        general.add(new ToggleButtonEntry(
                "aero_cam_sync.configuration.ignoreServer",
                "aero_cam_sync.configuration.ignoreServer.tooltip",
                Config.IGNORE_SERVER));

        general.add(new KeyBindEntry(
                "aero_cam_sync.configuration.openConfigKey",
                "aero_cam_sync.configuration.openConfigKey.tooltip",
                KeyBindings.OPEN_CONFIG,
                Config.OPEN_CONFIG_KEY));

        return (general);
    }
}
