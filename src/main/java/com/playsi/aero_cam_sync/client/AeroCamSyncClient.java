package com.playsi.aero_cam_sync.client;

import com.playsi.aero_cam_sync.AeroCamSync;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
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
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.TOGGLE);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        AeroCamSync.LOGGER.info("{} Initialized!", MODID);
    }

    /** Проверяем нажатие каждый тик. */
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        while (KeyBindings.TOGGLE.consumeClick()) {
            boolean newValue = !Config.MOD_ENABLED.get();
            Config.MOD_ENABLED.set(newValue);

            // Показываем ActionBar-сообщение игроку
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String msgKey = newValue ? "msg.aero_cam_sync.enabled" : "msg.aero_cam_sync.disabled";
                mc.player.displayClientMessage(Component.translatable(msgKey), true);
            }
        }
    }
}