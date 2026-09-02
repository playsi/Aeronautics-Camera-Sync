package com.playsi.aero_cam_sync.network;

import com.playsi.aero_cam_sync.AeroCamSync;
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

    public static void handle(HandshakePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // The reply goes FIRST and is unconditional: this is the protocol. Anything that can
            // throw (logs, config) comes strictly after, or an exception cuts the handshake short,
            // the reply never reaches the client and it stays CLIENT_ONLY forever.
            ctx.reply(new HandshakeResponsePacket());

            // NOTE: this handler runs on the SERVER while Config is CLIENT-side
            // (ModConfig.Type.CLIENT, registered only under @Mod(dist = Dist.CLIENT)), so on a
            // dedicated server its spec is not registered.
            AeroCamSync.LOGGER.debug(
                    "[AeroCamSync] Handshake received from: {}",
                    ctx.player().getName().getString()
            );
        });
    }
}