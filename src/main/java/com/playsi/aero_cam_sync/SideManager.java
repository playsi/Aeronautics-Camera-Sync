package com.playsi.aero_cam_sync;

import com.playsi.aero_cam_sync.client.Config;

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

    public static void reset() {
        if (Config.DEBUG_MESSAGES.get()) {
            AeroCamSync.LOGGER.info("[AeroCamSync] SideManager reset (disconnect)");
        }
        currentSide = Side.UNKNOWN;
    }
}