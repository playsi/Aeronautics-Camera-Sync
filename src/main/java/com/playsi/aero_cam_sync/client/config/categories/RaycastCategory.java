package com.playsi.aero_cam_sync.client.config.categories;

import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.config.entries.*;
import com.playsi.aero_cam_sync.client.config.ui.ConfigCategory;

public class RaycastCategory {

    public static ConfigCategory build() {
        ConfigCategory raycast = new ConfigCategory("aero_cam_sync.configuration.raycast");
        raycast.add(new SliderEntry(
                "aero_cam_sync.configuration.count",
                "aero_cam_sync.configuration.count.tooltip",
                Config.RAYCAST_COUNT,
                1, 100, 1,       // sliderMin, sliderMax, step
                1, 10000));      // hardMin, hardMax

        raycast.add(new SliderEntry(
                "aero_cam_sync.configuration.downLength",
                "aero_cam_sync.configuration.downLength.tooltip",
                Config.RAYCAST_DOWN_LENGTH,
                0.1, 12.0, 0.1,
                0.1, 12.0));

        raycast.add(new SliderEntry(
                "aero_cam_sync.configuration.upLength",
                "aero_cam_sync.configuration.upLength.tooltip",
                Config.RAYCAST_UP_LENGTH,
                -1.0, 1.0, 0.05,
                -1.0, 1.0));

        return (raycast);
    }
}
