package io.github.n252g2.beyonder.net;

import io.github.n252g2.beyonder.Beyonder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the ingredients are in the flask; open the brewing minigame. */
public record BrewStartPayload(int formula, int nonce) implements CustomPacketPayload {
    public static final Type<BrewStartPayload> TYPE = new Type<>(Beyonder.id("brew_start"));
    public static final StreamCodec<FriendlyByteBuf, BrewStartPayload> STREAM_CODEC =
            CustomPacketPayload.codec(BrewStartPayload::write, BrewStartPayload::new);

    private BrewStartPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(formula);
        buf.writeInt(nonce);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
