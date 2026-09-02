// common
package com.playsi.aero_cam_sync.network.Payload;

import com.playsi.aero_cam_sync.ServerTiltStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaternionf;

/**
 * The player's camera pose as computed by the client: what the server decides projectile
 * trajectories and interaction reach from.
 *
 * <p>A pose, not a tilt: with {@code TiltSource.eyeOffset} it has two halves, the rotation
 * ({@code qx..qw}) and the eye offset ({@code ox, oy, oz}). The offset travels HERE rather than
 * being recomputed server-side, because the server cannot compute it in principle: it comes from a
 * foreign mod that knows where a rotated player's head actually is.
 *
 * <p>The offset is already wall-clamped on the client, so the server receives the applied value
 * rather than the requested one and the client's prediction matches the authoritative result.
 */
public record TiltSyncPayload(float qx, float qy, float qz, float qw,
                              boolean rotActive, boolean posShift, boolean dropFromCamera,
                              float ox, float oy, float oz)
        implements CustomPacketPayload {

    public static final Type<TiltSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("aero_cam_sync", "tilt_sync"));

    public static final StreamCodec<FriendlyByteBuf, TiltSyncPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeFloat(p.qx); buf.writeFloat(p.qy);
                        buf.writeFloat(p.qz); buf.writeFloat(p.qw);
                        buf.writeBoolean(p.rotActive);
                        buf.writeBoolean(p.posShift);
                        buf.writeBoolean(p.dropFromCamera);
                        buf.writeFloat(p.ox); buf.writeFloat(p.oy); buf.writeFloat(p.oz);
                    },
                    buf -> new TiltSyncPayload(
                            buf.readFloat(), buf.readFloat(),
                            buf.readFloat(), buf.readFloat(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readFloat(), buf.readFloat(), buf.readFloat()
                    )
            );

    public static TiltSyncPayload from(Quaternionf q, boolean rotActive, boolean posShift,
                                       boolean dropFromCamera, Vec3 eyeOffset) {
        return new TiltSyncPayload(q.x, q.y, q.z, q.w, rotActive, posShift, dropFromCamera,
                (float) eyeOffset.x, (float) eyeOffset.y, (float) eyeOffset.z);
    }

    public Quaternionf toQuaternion() {
        return new Quaternionf(qx, qy, qz, qw);
    }

    public Vec3 toEyeOffset() {
        return new Vec3(ox, oy, oz);
    }

    public static void handle(TiltSyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (payload.rotActive() || payload.posShift()) {
                ServerTiltStore.set(player.getUUID(), payload.toQuaternion(),
                        payload.rotActive(), payload.posShift(), payload.dropFromCamera(),
                        payload.toEyeOffset());
            } else {
                ServerTiltStore.clear(player.getUUID());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
