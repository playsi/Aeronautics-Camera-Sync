package com.playsi.aero_cam_sync;

import com.playsi.aero_cam_sync.network.NetworkHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;

@Mod(AeroCamSync.MODID)
@EventBusSubscriber(modid = AeroCamSync.MODID)
public class AeroCamSync {
    public static final String MODID = "aero_cam_sync";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AeroCamSync(IEventBus modEventBus) {
        modEventBus.addListener(NetworkHandler::register);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ServerTiltStore.onPlayerLeave(event.getEntity().getUUID());
    }
}