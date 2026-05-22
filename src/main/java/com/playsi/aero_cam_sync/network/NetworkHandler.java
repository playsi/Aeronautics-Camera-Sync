package com.playsi.aero_cam_sync.network;

import com.playsi.aero_cam_sync.network.Payload.TiltSyncPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {

    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1")
                .optional();

        registrar.playToServer(
                HandshakePacket.TYPE,
                HandshakePacket.STREAM_CODEC,
                HandshakePacket::handle
        );

        registrar.playToClient(
                HandshakeResponsePacket.TYPE,
                HandshakeResponsePacket.STREAM_CODEC,
                HandshakeResponsePacket::handle
        );

        registrar.playToServer(
                TiltSyncPayload.TYPE,
                TiltSyncPayload.CODEC,
                TiltSyncPayload::handle
        );
    }
}