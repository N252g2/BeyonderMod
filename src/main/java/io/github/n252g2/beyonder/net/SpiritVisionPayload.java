package io.github.n252g2.beyonder.net;

import io.github.n252g2.beyonder.Beyonder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the player opened or closed their Spirit Vision. */
public record SpiritVisionPayload(boolean active) implements CustomPacketPayload {
    public static final Type<SpiritVisionPayload> TYPE = new Type<>(Beyonder.id("spirit_vision"));
    public static final StreamCodec<FriendlyByteBuf, SpiritVisionPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SpiritVisionPayload::write, SpiritVisionPayload::new);

    private SpiritVisionPayload(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
