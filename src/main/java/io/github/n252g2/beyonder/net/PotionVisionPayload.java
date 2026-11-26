package io.github.n252g2.beyonder.net;

import io.github.n252g2.beyonder.Beyonder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: play the potion vision. Kinds: see {@link #AWAKEN} and friends. */
public record PotionVisionPayload(int kind) implements CustomPacketPayload {
    /** First potion: becoming a Beyonder. */
    public static final int AWAKEN = 0;
    /** Advancing to the next Sequence. */
    public static final int ADVANCE = 1;
    /** Taken too soon: survived, but something slipped. */
    public static final int FAILURE = 2;
    /** Losing control completely. */
    public static final int FATAL = 3;

    /** Ticks from drinking to the climax, when the outcome lands. Client and server agree on this. */
    public static final int CLIMAX_TICKS = 110;
    public static final int LENGTH_TICKS = 170;

    public static final Type<PotionVisionPayload> TYPE = new Type<>(Beyonder.id("potion_vision"));
    public static final StreamCodec<FriendlyByteBuf, PotionVisionPayload> STREAM_CODEC =
            CustomPacketPayload.codec(PotionVisionPayload::write, PotionVisionPayload::new);

    private PotionVisionPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(kind);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
