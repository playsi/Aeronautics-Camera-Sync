package com.playsi.aero_cam_sync;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;

@Mod(AeroCamSync.MODID)
public class AeroCamSync {
    public static final String MODID = "aero_cam_sync";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AeroCamSync(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}