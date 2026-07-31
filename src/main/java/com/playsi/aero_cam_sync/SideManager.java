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

    /**
     * Значение {@link Config#IGNORE_SERVER}, зафиксированное на входе в мир, на всю сессию.
     *
     * <p>Опцию НЕЛЬЗЯ читать вживую. На сервере с модом переключение прямо в игре
     * мгновенно уводит клиент в клиент-онли ветку ({@code AUTO_DISABLE_FOR_RAYCAST_ITEMS}
     * выравнивает камеру, предсказание идёт без наклона) и одновременно глушит
     * {@link #sendTiltToServer()} — а сервер продолжает крутить снаряды/взгляд по
     * ПОСЛЕДНЕМУ полученному тилту, потому что {@link ServerTiltStore} никто не чистит.
     * Клиент целится прямо, сервер стреляет под старым наклоном → траектории расходятся.
     * Поэтому смена режима применяется только на следующем заходе в мир.</p>
     */
    private static boolean ignoreServerSession = false;

    public static Side getSide() {
        return currentSide;
    }

    public static void setSide(Side side) {
        if (Config.DEBUG_MESSAGES.get()) {
            AeroCamSync.LOGGER.info("[AeroCamSync] SideManager -> {}", side);
        }
        currentSide = side;
    }

    /** Зафиксированный на сессию режим «только клиент» (см. {@link #ignoreServerSession}). */
    public static boolean isIgnoreServerSession() {
        return ignoreServerSession;
    }

    /**
     * {@code true}, если игрок переключил «только на клиенте» уже в мире:
     * настройка сохранена, но вступит в силу лишь при следующем заходе.
     * Используется только для подсказки в экране настроек.
     */
    public static boolean isIgnoreServerPending() {
        return currentSide != Side.UNKNOWN
                && Config.isLoaded()
                && Config.IGNORE_SERVER.get() != ignoreServerSession;
    }

    public static boolean isClientOnly() {
        if (ignoreServerSession) {
            return true;
        }

        return currentSide != Side.CLIENT_SERVER;
    }

    public static boolean isClientServer() {
        if (ignoreServerSession) {
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

    /**
     * Начало сессии (вход в мир/на сервер): сбрасываем сторону и фиксируем
     * «только на клиенте» на всю сессию.
     */
    public static void beginSession() {
        currentSide = Side.UNKNOWN;
        latchIgnoreServer();
        if (Config.isLoaded() && Config.DEBUG_MESSAGES.get()) {
            AeroCamSync.LOGGER.info("[AeroCamSync] Session started, clientOnly latched = {}", ignoreServerSession);
        }
    }

    public static void reset() {
        if (Config.isLoaded() && Config.DEBUG_MESSAGES.get()) {
            AeroCamSync.LOGGER.info("[AeroCamSync] SideManager reset (disconnect)");
        }
        currentSide = Side.UNKNOWN;
        // Вне мира держим фиксацию актуальной: подсказка «нужен перезаход» гаснет,
        // а следующий заход всё равно перечитает конфиг в beginSession().
        latchIgnoreServer();
    }

    private static void latchIgnoreServer() {
        // Конфиг клиентский: на выделенном сервере его спека не зарегистрирована,
        // а .get() до загрузки бросает IllegalStateException (Issue #19, #33).
        if (Config.isLoaded()) {
            ignoreServerSession = Config.IGNORE_SERVER.get();
        }
    }
}