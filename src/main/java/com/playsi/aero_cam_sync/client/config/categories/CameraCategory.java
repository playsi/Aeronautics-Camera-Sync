package com.playsi.aero_cam_sync.client.config.categories;

import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.config.entries.*;
import com.playsi.aero_cam_sync.client.config.ui.ConfigCategory;

public class CameraCategory {
    public static ConfigCategory build() {
        ConfigCategory camera = new ConfigCategory("aero_cam_sync.configuration.camera");
        camera.add(new

                ToggleButtonEntry(
                "aero_cam_sync.configuration.rotateCamera",
                "aero_cam_sync.configuration.rotateCamera.tooltip",
                Config.MODIFY_CAMERA_ROT));

        camera.add(new

                ToggleButtonEntry(
                "aero_cam_sync.configuration.moveCamera",
                "aero_cam_sync.configuration.moveCamera.tooltip",
                Config.MODIFY_CAMERA_POS));

        camera.add(new

                ToggleButtonEntry(
                "aero_cam_sync.configuration.dropFromCamera",
                "aero_cam_sync.configuration.dropFromCamera.tooltip",
                Config.DROP_FROM_CAMERA));

        camera.add(new

                ToggleButtonEntry(
                "aero_cam_sync.configuration.cameraCollision",
                "aero_cam_sync.configuration.cameraCollision.tooltip",
                Config.CAMERA_COLLISION));

        camera.add(new

                SliderEntry(
                "aero_cam_sync.configuration.cameraCollisionSmooth",
                "aero_cam_sync.configuration.cameraCollisionSmooth.tooltip",
                Config.CAMERA_COLLISION_SMOOTH,
                0.0, 2.0, 0.05,   // sliderMin, sliderMax, step
                0.0, 5.0));        // hardMin, hardMax

        camera.add(new

                SliderEntry(
                "aero_cam_sync.configuration.smoothSpeed",
                "aero_cam_sync.configuration.smoothSpeed.tooltip",
                Config.SMOOTH_SPEED,
                0.1, 10.0, 0.05,   // sliderMin, sliderMax, step
                0.0, 9999.0));       // hardMin, hardMax

        camera.add(new

                SliderEntry(
                "aero_cam_sync.configuration.minNormalY",
                "aero_cam_sync.configuration.minNormalY.tooltip",
                Config.MIN_NORMAL_Y,
                0.0, 1.0, 0.01,
                0.0, 1.0));

        camera.add(new

                SeparatorEntry());

        camera.add(new

                ToggleButtonEntry(
                "aero_cam_sync.configuration.dropCacheOnAllMiss",
                "aero_cam_sync.configuration.dropCacheOnAllMiss.tooltip",
                Config.DROP_CACHE_ON_ALL_MISS));

        camera.add(new

                ToggleButtonEntry(
                "aero_cam_sync.configuration.disableOnFlying",
                "aero_cam_sync.configuration.disableOnFlying.tooltip",
                Config.DISABLE_ON_FLYING));


        return (camera);
    }

}