package com.playsi.aero_cam_sync;

import com.playsi.aero_cam_sync.network.NetworkHandler;
import net.neoforged.bus.api.IEventBus;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;

@Mod(AeroCamSync.MODID)
public class AeroCamSync {
    public static final String MODID = "aero_cam_sync";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AeroCamSync(IEventBus modEventBus) {
        modEventBus.addListener(NetworkHandler::register);
    }
}