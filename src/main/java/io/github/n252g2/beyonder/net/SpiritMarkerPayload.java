package io.github.n252g2.beyonder.net;

import io.github.n252g2.beyonder.Beyonder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: place a spirit marker (a waypoint only Spirit Vision shows). Replaces one with the same label. */
public record SpiritMarkerPayload(double x, double y, double z, String label, int color) implements CustomPacketPayload {
    public static final Type<SpiritMarkerPayload> TYPE = new Type<>(Beyonder.id("spirit_marker"));
    public static final StreamCodec<FriendlyByteBuf, SpiritMarkerPayload> STREAM_CODEC =
            CustomPacketPayload.codec(SpiritMarkerPayload::write, SpiritMarkerPayload::new);

    private SpiritMarkerPayload(FriendlyByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readUtf(64), buf.readInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeUtf(label, 64);
        buf.writeInt(color);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
