package com.playsi.aero_cam_sync.network;

import com.playsi.aero_cam_sync.AeroCamSync;
import com.playsi.aero_cam_sync.client.config.Config;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HandshakePacket() implements CustomPacketPayload {

    public static final Type<HandshakePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroCamSync.MODID, "handshake"));

    public static final StreamCodec<FriendlyByteBuf, HandshakePacket> STREAM_CODEC =
            StreamCodec.unit(new HandshakePacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Вызывается на сервере когда пришёл пакет от клиента */
    public static void handle(HandshakePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Config.DEBUG_MESSAGES.get()) {
                AeroCamSync.LOGGER.info(
                        "[AeroCamSync] Handshake received from: {}",
                        ctx.player().getName().getString()
                );
            }

            ctx.reply(new HandshakeResponsePacket());
        });
    }
}