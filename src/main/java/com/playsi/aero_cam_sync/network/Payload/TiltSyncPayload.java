// common
package com.playsi.aero_cam_sync.network.Payload;

import com.playsi.aero_cam_sync.ServerTiltStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Quaternionf;

public record TiltSyncPayload(float qx, float qy, float qz, float qw, boolean active)
        implements CustomPacketPayload {

    public static final Type<TiltSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("aero_cam_sync", "tilt_sync"));

    public static final StreamCodec<FriendlyByteBuf, TiltSyncPayload> CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeFloat(p.qx); buf.writeFloat(p.qy);
                        buf.writeFloat(p.qz); buf.writeFloat(p.qw);
                        buf.writeBoolean(p.active);
                    },
                    buf -> new TiltSyncPayload(
                            buf.readFloat(), buf.readFloat(),
                            buf.readFloat(), buf.readFloat(),
                            buf.readBoolean()
                    )
            );

    public static TiltSyncPayload from(Quaternionf q, boolean active) {
        return new TiltSyncPayload(q.x, q.y, q.z, q.w, active);
    }

    public Quaternionf toQuaternion() {
        return new Quaternionf(qx, qy, qz, qw);
    }

    public static void handle(TiltSyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (payload.active()) {
                ServerTiltStore.set(player.getUUID(), payload.toQuaternion());
            } else {
                ServerTiltStore.clear(player.getUUID());
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}