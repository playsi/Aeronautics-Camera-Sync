package com.playsi.aero_cam_sync.client;

import com.playsi.aero_cam_sync.AeroCamSync;
import com.playsi.aero_cam_sync.Config;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import static com.playsi.aero_cam_sync.AeroCamSync.MODID;

@Mod(value = MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class AeroCamSyncClient {
    public AeroCamSyncClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        AeroCamSync.LOGGER.info("{} Initializated!",MODID);
    }
}
