package com.playsi.aero_cam_sync.client;

import com.playsi.aero_cam_sync.AeroCamSync;
import com.playsi.aero_cam_sync.SideManager;
import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.config.ModConfigScreen;
import com.playsi.aero_cam_sync.client.debug.DebugRayRenderer;
import com.playsi.aero_cam_sync.network.HandshakePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Objects;

import static com.playsi.aero_cam_sync.AeroCamSync.MODID;

@Mod(value = MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class AeroCamSyncClient {

    private static boolean pendingHandshake = false;

    public AeroCamSyncClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mc, parent) -> new ModConfigScreen(parent));

        Objects.requireNonNull(container.getEventBus()).addListener((ModConfigEvent.Loading e) -> {
            if (e.getConfig().getSpec() == Config.SPEC)
                KeyBindings.loadFromConfig();
        });
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.TOGGLE);
        event.register(KeyBindings.OPEN_CONFIG);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        AeroCamSync.LOGGER.info("{} Initialized!", MODID);
    }

    @SubscribeEvent
    static void onClientConnectedToServer(ClientPlayerNetworkEvent.LoggingIn event) {
        SideManager.reset();

        if (Config.IGNORE_SERVER.get()) {
            SideManager.setSide(SideManager.Side.CLIENT_ONLY);
            if (Config.DEBUG_MESSAGES.get()) {
                AeroCamSync.LOGGER.info("[AeroCamSync] IGNORE_SERVER enabled, skipping handshake -> CLIENT_ONLY");
            }
            return;
        }

        pendingHandshake = true;
        if (Config.DEBUG_MESSAGES.get()) {
            AeroCamSync.LOGGER.info("[AeroCamSync] Login detected, handshake scheduled for next tick");
        }
    }

    @SubscribeEvent
    static void onClientDisconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        pendingHandshake = false;
        SideManager.reset();
        if (Config.DEBUG_MESSAGES.get()) {
            AeroCamSync.LOGGER.info("[AeroCamSync] Disconnected, SideManager reset");
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        // Отправляем handshake на первом тике после логина
        if (pendingHandshake) {
            pendingHandshake = false;

            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                boolean serverHasMod = mc.getConnection()
                        .getConnectionType()
                        .isNeoForge();

                // Дополнительно проверяем через negotiated channels
                boolean channelAvailable = mc.getConnection()
                        .hasChannel(HandshakePacket.TYPE);

                if (channelAvailable) {
                    PacketDistributor.sendToServer(new HandshakePacket());
                    if (Config.DEBUG_MESSAGES.get()) {
                        AeroCamSync.LOGGER.info("[AeroCamSync] Handshake sent to server (NeoForge: {})", serverHasMod);
                    }
                    // SideManager переключится в CLIENT_SERVER когда придёт HandshakeResponsePacket
                } else {
                    SideManager.setSide(SideManager.Side.CLIENT_ONLY);
                    if (Config.DEBUG_MESSAGES.get()) {
                        AeroCamSync.LOGGER.info("[AeroCamSync] Channel not available on server -> CLIENT_ONLY");
                    }
                }
            } else {
                SideManager.setSide(SideManager.Side.CLIENT_ONLY);
                if (Config.DEBUG_MESSAGES.get()) {
                    AeroCamSync.LOGGER.info("[AeroCamSync] No connection found -> CLIENT_ONLY");
                }
            }
        }

        // enable disable camera sync
        while (KeyBindings.TOGGLE.consumeClick()) {
            boolean newValue = !Config.MOD_ENABLED.get();
            Config.MOD_ENABLED.set(newValue);
            DebugRayRenderer.clear();

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                String msgKey = newValue ? "msg.aero_cam_sync.enabled" : "msg.aero_cam_sync.disabled";
                mc.player.displayClientMessage(Component.translatable(msgKey), true);


                if (Config.DEBUG_MESSAGES.get()) {
                    AeroCamSync.LOGGER.info(
                            "[AeroCamSync] Toggled: {} | Side: {}",
                            newValue ? "ENABLED" : "DISABLED",
                            SideManager.getSide()
                    );
                }
            }
        }

        // open mod config
        while (KeyBindings.OPEN_CONFIG.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                mc.setScreen(new ModConfigScreen(null));
            }
        }
    }
}