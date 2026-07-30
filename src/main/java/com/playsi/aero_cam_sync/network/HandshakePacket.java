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

    /** Вызывается на сервере когда пришёл пакет от клиента */
    public static void handle(HandshakePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // Ответ идёт ПЕРВЫМ и ничем не обусловлен: это протокол. Всё, что способно
            // бросить (логи, конфиг), — строго после. Иначе исключение обрывает рукопожатие,
            // ответ до клиента не доходит и он навсегда остаётся в CLIENT_ONLY.
            ctx.reply(new HandshakeResponsePacket());

            // ВАЖНО: этот обработчик выполняется на СЕРВЕРЕ, а Config — КЛИЕНТСКИЙ
            // (ModConfig.Type.CLIENT, регистрируется только в @Mod(dist = Dist.CLIENT)).
            // На выделенном сервере спека не зарегистрирована, и любой .get() кидает
            // IllegalStateException «Cannot get config value before config is loaded» — Issue #33.
            // Серверное логирование не должно зависеть от клиентской настройки вообще:
            // уровень выбирает лог-конфиг сервера, а не DEBUG_MESSAGES игрока.
            AeroCamSync.LOGGER.debug(
                    "[AeroCamSync] Handshake received from: {}",
                    ctx.player().getName().getString()
            );
        });
    }
}