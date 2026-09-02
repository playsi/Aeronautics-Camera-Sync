package com.playsi.aero_cam_sync.client;

import com.playsi.aero_cam_sync.client.tilt.TiltSync;
import com.playsi.aero_cam_sync.AeroCamSync;
import com.playsi.aero_cam_sync.SideManager;
import com.playsi.aero_cam_sync.apiimpl.ApiLog;
import com.playsi.aero_cam_sync.apiimpl.SuppressionLeases;
import com.playsi.aero_cam_sync.apiimpl.TiltEvents;
import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.config.ModConfigScreen;
import com.playsi.aero_cam_sync.client.config.alert.ConfigMigrationManager;
import com.playsi.aero_cam_sync.client.config.alert.ConfigResetScreen;
import com.playsi.aero_cam_sync.client.debug.DebugRayRenderer;
import com.playsi.aero_cam_sync.client.devtest.AcsApiTestMod;
import com.playsi.aero_cam_sync.client.devtest.TiltSourceTest;
import com.playsi.aero_cam_sync.client.camera.FrameVanillaState;
import com.playsi.aero_cam_sync.client.compat.CutThroughCompat;
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
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

import java.nio.file.Files;

import static com.playsi.aero_cam_sync.AeroCamSync.MODID;
import static com.playsi.aero_cam_sync.client.tilt.BlacklistHandle.handleBlacklistToggle;;


@Mod(value = MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class AeroCamSyncClient {

    private static boolean pendingHandshake = false;

    /**
     * The gate lives here rather than inside the test class: a {@code KeyMapping} constructor
     * registers itself in static Minecraft registries, so merely loading it would leave stray
     * keybinds in the shipped mod.
     */
    private static final boolean DEV_API_TEST = !FMLLoader.isProduction();

    public AeroCamSyncClient(ModContainer container) {
        boolean configExisted = Files.exists(FMLPaths.CONFIGDIR.get().resolve(MODID + "-client.toml"));
        ConfigMigrationManager.setConfigExisted(configExisted);

        container.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mc, parent) -> new ModConfigScreen(parent));
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.TOGGLE);
        event.register(KeyBindings.OPEN_CONFIG);
        event.register(KeyBindings.ADD_MAINHAND_ITEM);
        if (DEV_API_TEST) AcsApiTestMod.registerKeys(event);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        AeroCamSync.LOGGER.info("{} Initialized!", MODID);
        if (DEV_API_TEST) {
            AcsApiTestMod.init();
            TiltSourceTest.init();
        }
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
        // The side must not change mid-game, or client and server diverge (see SideManager).
        SideManager.beginSession();

        if (SideManager.isIgnoreServerSession()) {
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


    /**
     * Reliably before {@code Camera#setup}, which is the only way {@link FrameVanillaState} can tell
     * this frame's value from last frame's.
     */
    @SubscribeEvent
    static void onRenderFrame(RenderFrameEvent.Pre event) {
        FrameVanillaState.beginFrame();
    }

    @SubscribeEvent
    static void onClientDisconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        pendingHandshake = false;
        SideManager.reset();

        SuppressionLeases.reset();
        TiltEvents.reset();
        FrameVanillaState.reset();
        ApiLog.resetSession();
        if (Config.DEBUG_MESSAGES.get()) {
            AeroCamSync.LOGGER.info("[AeroCamSync] Disconnected, SideManager reset");
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();

        // Paused matters: a mod extends its lease from the tick, and in a paused singleplayer game
        // ticks stop while real time does not.
        SuppressionLeases.advance(System.currentTimeMillis(), mc.isPaused());

        // Read here, beside the clock, because SuppressionLeases must not know Minecraft types.
        if (Config.isLoaded()) {
            SuppressionLeases.setForced(Config.SUPPRESS_ALL.get());
        }

        // In the tick, not the pick: what must be counted is time in a world, not frames.
        CutThroughCompat.observeTick(mc.level != null && mc.player != null);

        if (pendingHandshake) {
            pendingHandshake = false;

            if (mc.getConnection() != null) {
                boolean serverHasMod = mc.getConnection()
                        .getConnectionType()
                        .isNeoForge();

                boolean channelAvailable = mc.getConnection()
                        .hasChannel(HandshakePacket.TYPE);

                if (channelAvailable) {
                    PacketDistributor.sendToServer(new HandshakePacket());
                    if (Config.DEBUG_MESSAGES.get()) {
                        AeroCamSync.LOGGER.info("[AeroCamSync] Handshake sent to server (NeoForge: {})", serverHasMod);
                    }
                    // SideManager switches to CLIENT_SERVER when HandshakeResponsePacket arrives.
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
                TiltSync.sendToServer();
            }
            TiltEvents.tick(mc.player);
        }

        // enable disable camera sync
        while (KeyBindings.TOGGLE.consumeClick()) {
            boolean newValue = !Config.MOD_ENABLED.get();
            Config.MOD_ENABLED.set(newValue);
            DebugRayRenderer.clear();


            if (mc.player != null) {
                // The toggle still flips, but showing "on/off" would be a lie right now: the
                // player pressed it and saw nothing. Name the holder instead.
                if (SuppressionLeases.isSuppressed()) {
                    mc.player.displayClientMessage(
                            Component.translatable("msg.aero_cam_sync.suppressed_by",
                                    String.join(", ", SuppressionLeases.holders())),
                            true);
                    continue;
                }

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

        if (DEV_API_TEST) AcsApiTestMod.tick(mc);
    }

}