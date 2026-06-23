package com.playsi.aero_cam_sync.client;

import com.playsi.aero_cam_sync.AeroCamSync;
import com.playsi.aero_cam_sync.SideManager;
import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.config.ModConfigScreen;
import com.playsi.aero_cam_sync.client.config.alert.ConfigMigrationManager;
import com.playsi.aero_cam_sync.client.config.alert.ConfigResetScreen;
import com.playsi.aero_cam_sync.client.debug.DebugRayRenderer;
import com.playsi.aero_cam_sync.network.HandshakePacket;
import com.playsi.aero_cam_sync.network.Payload.TiltSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

import java.nio.file.Files;
import java.util.Objects;

import static com.playsi.aero_cam_sync.AeroCamSync.MODID;
import static com.playsi.aero_cam_sync.client.utils.BlacklistHandle.handleBlacklistToggle;;


@Mod(value = MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class AeroCamSyncClient {

    private static boolean pendingHandshake = false;

    public AeroCamSyncClient(ModContainer container) {
        boolean configExisted = Files.exists(FMLPaths.CONFIGDIR.get().resolve(MODID + "-client.toml"));
        ConfigMigrationManager.setConfigExisted(configExisted);

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
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (!(event.getScreen() instanceof TitleScreen)) return;
        if (ConfigMigrationManager.wasPromptShown()) return;
        if (!ConfigMigrationManager.needsResetPrompt()) return;

        event.setNewScreen(new ConfigResetScreen(event.getScreen()));
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

        if (Minecraft.getInstance().hasSingleplayerServer()) {
            SideManager.setSide(SideManager.Side.CLIENT_SERVER);
            if (Config.DEBUG_MESSAGES.get())
                AeroCamSync.LOGGER.info("[AeroCamSync] Singleplayer detected -> CLIENT_SERVER (direct)");
            return;
        }

        pendingHandshake = true;
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
        Minecraft mc = Minecraft.getInstance();
        // Отправляем handshake на первом тике после логина
        if (pendingHandshake) {
            pendingHandshake = false;

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

        if (mc.player != null && mc.level != null) {
            if (SideManager.isClientServer() || mc.hasSingleplayerServer()) {
                SideManager.sendTiltToServer();
            }
        }

        // enable disable camera sync
        while (KeyBindings.TOGGLE.consumeClick()) {
            boolean newValue = !Config.MOD_ENABLED.get();
            Config.MOD_ENABLED.set(newValue);
            DebugRayRenderer.clear();


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
            if (mc.screen == null) {
                mc.setScreen(new ModConfigScreen(null));
            }
        }
        while (KeyBindings.ADD_MAINHAND_ITEM.consumeClick()) {
            handleBlacklistToggle(mc.player);
        }
    }

}