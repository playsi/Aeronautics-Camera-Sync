package com.playsi.aero_cam_sync.network;

import com.playsi.aero_cam_sync.AeroCamSync;
import com.playsi.aero_cam_sync.client.Config;
import com.playsi.aero_cam_sync.SideManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record HandshakeResponsePacket() implements CustomPacketPayload {

    public static final Type<HandshakeResponsePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AeroCamSync.MODID, "handshake_response"));

    public static final StreamCodec<FriendlyByteBuf, HandshakeResponsePacket> STREAM_CODEC =
            StreamCodec.unit(new HandshakeResponsePacket());

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HandshakeResponsePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            SideManager.setSide(SideManager.Side.CLIENT_SERVER);

            if (Config.DEBUG_MESSAGES.get()) {
                AeroCamSync.LOGGER.info(
                        "[AeroCamSync] Server confirmed mod presence -> CLIENT_SERVER mode"
                );
            }
        });
    }
}