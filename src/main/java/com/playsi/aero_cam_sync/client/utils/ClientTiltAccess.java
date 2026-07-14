package com.playsi.aero_cam_sync.client.utils;

import com.playsi.aero_cam_sync.client.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;

/**
 * Клиентский поставщик тилта для {@link com.playsi.aero_cam_sync.TiltAccess}.
 *
 * <p>Тилт отдаётся только для ЛОКАЛЬНОГО игрока — взаимодействия (вёдра, снаряды
 * через {@code getPlayerPOVHitResult}) выполняются именно им. Условия совпадают с
 * клиентским наклоном камеры, чтобы предсказание клиента совпадало с авторитетным
 * результатом сервера.</p>
 */
public final class ClientTiltAccess {

    private ClientTiltAccess() {}

    /** Тилт направления — привязан к повороту камеры (MODIFY_CAMERA_ROT). */
    public static Quaternionf getClientLookTilt(Player player) {
        if (!baseAllows(player)) return null;
        if (!Config.MODIFY_CAMERA_ROT.get()) return null;
        return CameraController.getSmoothedTilt();
    }

    /** Тилт сдвига начала луча — привязан к сдвигу позиции камеры (MODIFY_CAMERA_POS). */
    public static Quaternionf getClientPosTilt(Player player) {
        if (!baseAllows(player)) return null;
        if (!Config.MODIFY_CAMERA_POS.get()) return null;
        return CameraController.getSmoothedTilt();
    }

    private static boolean baseAllows(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != player) return false;
        if (!Config.isLoaded()) return false;
        if (!Config.MOD_ENABLED.get()) return false;
        return CameraController.shouldApplyTilt();
    }
}
