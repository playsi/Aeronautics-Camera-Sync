package com.playsi.aero_cam_sync.client.tilt;

import com.playsi.aero_cam_sync.ServerTiltStore;
import com.playsi.aero_cam_sync.SideManager;
import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.network.Payload.TiltSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

/**
 * Sends the tilt to the server: the other half of what {@code SideManager} used to be.
 *
 * <p>That class did two jobs, holding side state and sending the tilt, and the second one pulled
 * {@link Minecraft} and {@link CameraController} into a common package: a class common by location
 * but client-side by dependency. This half now lives where it belongs.
 *
 * <p>Called once per client tick from {@code AeroCamSyncClient}.
 */
public final class TiltSync {

    private TiltSync() {}

    /**
     * Puts the current tilt where the authoritative side will see it: straight into
     * {@link ServerTiltStore} in singleplayer, in a packet in multiplayer with the mod on the
     * server.
     */
    public static void sendToServer() {
        Minecraft mc = Minecraft.getInstance();
        boolean enabled = CameraController.shouldApplyTilt();
        // Two INDEPENDENT flags: camera rotation tilts the projectile and look direction, the
        // position shift moves the launch point. Either can work alone.
        //
        // The same methods the camera and rays ask, not the settings directly: under a foreign
        // source the settings do not cut the tilt, and reading them here would have the server
        // scoring hits by a tilt the player cannot see.
        boolean rotActive = enabled && CameraController.rotationActive();
        boolean posShift  = enabled && CameraController.posShiftActive();
        boolean dropFromCamera = Config.DROP_FROM_CAMERA.get();
        boolean present = rotActive || posShift;
        Quaternionf q = present
                ? new Quaternionf(CameraController.getSmoothedTilt())
                : new Quaternionf();

        // The second half of the pose. Under the shift flag, as on the server: the offset moves
        // the ray ORIGIN, exactly the half that flag enables. Sent as APPLIED (wall-clamped) rather
        // than as requested, or the server would score hits by an offset the player never saw.
        Vec3 eyeOffset = posShift ? CameraController.effectiveEyeOffset() : Vec3.ZERO;

        if (mc.hasSingleplayerServer()) {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null && mc.player != null) {
                ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (sp != null) {
                    if (present) {
                        ServerTiltStore.set(sp.getUUID(), q, rotActive, posShift, dropFromCamera,
                                eyeOffset);
                    } else {
                        ServerTiltStore.clear(sp.getUUID()); // was set(..., null), which NPE'd
                    }
                }
            }
            return;
        }

        // Multiplayer with the mod on the server.
        if (SideManager.getSide() != SideManager.Side.CLIENT_SERVER) return;
        if (mc.getConnection() == null) return;

        PacketDistributor.sendToServer(
                TiltSyncPayload.from(q, rotActive, posShift, dropFromCamera, eyeOffset));
    }
}
