package com.playsi.aero_cam_sync;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;

/**
 * Единая точка получения тилта игрока, работает на обеих сторонах.
 *
 * <p>На сервере тилт берётся из {@link ServerTiltStore} (синхронизируется клиентом).
 * На клиенте — из сглаженного тилта камеры локального игрока. Ссылка на клиентский
 * класс {@code ClientTiltAccess} исполняется только в ветке {@code isClientSide},
 * поэтому на выделенном сервере он не загружается.</p>
 */
public final class TiltAccess {

    private TiltAccess() {}

    /** Тилт для НАКЛОНА НАПРАВЛЕНИЯ луча/взгляда (привязан к повороту камеры), либо null. */
    public static Quaternionf getLookTilt(Player player) {
        if (player == null) return null;
        if (player.level().isClientSide) {
            return com.playsi.aero_cam_sync.client.utils.ClientTiltAccess.getClientLookTilt(player);
        }
        if (player instanceof ServerPlayer sp) {
            return ServerTiltStore.getLookTilt(sp.getUUID());
        }
        return null;
    }

    /** Тилт для СДВИГА НАЧАЛА луча (привязан к сдвигу позиции камеры), либо null. */
    public static Quaternionf getPosTilt(Player player) {
        if (player == null) return null;
        if (player.level().isClientSide) {
            return com.playsi.aero_cam_sync.client.utils.ClientTiltAccess.getClientPosTilt(player);
        }
        if (player instanceof ServerPlayer sp) {
            return ServerTiltStore.getPosTilt(sp.getUUID());
        }
        return null;
    }
}
