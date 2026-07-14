package com.playsi.aero_cam_sync;

import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.network.Payload.TiltSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

import static com.playsi.aero_cam_sync.client.utils.CameraController.getSmoothedTilt;
import static com.playsi.aero_cam_sync.client.utils.CameraController.shouldApplyTilt;


public class SideManager {

    public enum Side {
        UNKNOWN,
        CLIENT_ONLY,
        CLIENT_SERVER
    }

    private static Side currentSide = Side.UNKNOWN;

    public static Side getSide() {
        return currentSide;
    }

    public static void setSide(Side side) {
        if (Config.DEBUG_MESSAGES.get()) {
            AeroCamSync.LOGGER.info("[AeroCamSync] SideManager -> {}", side);
        }
        currentSide = side;
    }

    public static boolean isClientOnly() {
        if (Config.IGNORE_SERVER.get()) {
            return true;
        }

        return currentSide != Side.CLIENT_SERVER;
    }

    public static boolean isClientServer() {
        if (Config.IGNORE_SERVER.get()) {
            return false;
        }

        return currentSide == Side.CLIENT_SERVER;
    }

    public static void sendTiltToServer() {
        Minecraft mc = Minecraft.getInstance();
        boolean enabled = shouldApplyTilt();
        // Два НЕЗАВИСИМЫХ флага: поворот камеры наклоняет направление снаряда/взгляда,
        // сдвиг позиции — точку вылета. Любой из них может работать в одиночку.
        boolean rotActive = enabled && Config.MODIFY_CAMERA_ROT.get();
        boolean posShift  = enabled && Config.MODIFY_CAMERA_POS.get();
        boolean dropFromCamera = Config.DROP_FROM_CAMERA.get();
        boolean present = rotActive || posShift;
        Quaternionf q = present ? new Quaternionf(getSmoothedTilt()) : new Quaternionf();

        if (mc.hasSingleplayerServer()) {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null && mc.player != null) {
                ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (sp != null) {
                    if (present) {
                        ServerTiltStore.set(sp.getUUID(), q, rotActive, posShift, dropFromCamera);
                    } else {
                        ServerTiltStore.clear(sp.getUUID()); // раньше было set(..., null) → NPE
                    }
                }
            }
            return;
        }

        // Мультиплеер с модом на сервере
        if (currentSide != Side.CLIENT_SERVER) return;
        if (mc.getConnection() == null) return;

        PacketDistributor.sendToServer(TiltSyncPayload.from(q, rotActive, posShift, dropFromCamera));
    }

    public static void reset() {
        if (Config.DEBUG_MESSAGES.get()) {
            AeroCamSync.LOGGER.info("[AeroCamSync] SideManager reset (disconnect)");
        }
        currentSide = Side.UNKNOWN;
    }
}