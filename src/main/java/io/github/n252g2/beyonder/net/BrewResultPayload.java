package io.github.n252g2.beyonder.net;

import io.github.n252g2.beyonder.Beyonder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: how the brew went. Quality 0 (rough) to 3 (perfect), or -1 if the flask cracked. */
public record BrewResultPayload(int nonce, int quality) implements CustomPacketPayload {
    public static final int RUINED = -1;

    public static final Type<BrewResultPayload> TYPE = new Type<>(Beyonder.id("brew_result"));
    public static final StreamCodec<FriendlyByteBuf, BrewResultPayload> STREAM_CODEC =
            CustomPacketPayload.codec(BrewResultPayload::write, BrewResultPayload::new);

    private BrewResultPayload(FriendlyByteBuf buf) {
        this(buf.readInt(), buf.readByte());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeInt(nonce);
        buf.writeByte(quality);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
