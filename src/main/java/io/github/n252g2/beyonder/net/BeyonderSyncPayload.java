package io.github.n252g2.beyonder.net;

import io.github.n252g2.beyonder.Beyonder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: a player's Beyonder state. Other players only receive pathway and Sequence. */
public record BeyonderSyncPayload(int entityId, String pathway, int sequence, float spirituality,
                                  float maxSpirituality, float digestion, float lossOfControl,
                                  boolean spiritVision) implements CustomPacketPayload {
    public static final Type<BeyonderSyncPayload> TYPE = new Type<>(Beyonder.id("sync"));
    public static final StreamCodec<FriendlyByteBuf, BeyonderSyncPayload> STREAM_CODEC =
            CustomPacketPayload.codec(BeyonderSyncPayload::write, BeyonderSyncPayload::new);

    private BeyonderSyncPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readUtf(64), buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readBoolean());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(pathway, 64);
        buf.writeVarInt(sequence);
        buf.writeFloat(spirituality);
        buf.writeFloat(maxSpirituality);
        buf.writeFloat(digestion);
        buf.writeFloat(lossOfControl);
        buf.writeBoolean(spiritVision);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
