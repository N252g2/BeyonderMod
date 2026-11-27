package io.github.n252g2.beyonder.net;

import io.github.n252g2.beyonder.Beyonder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: fire or reload the gun in the main hand. The server checks everything. */
public record GunActionPayload(int action) implements CustomPacketPayload {
    public static final int FIRE = 0;
    public static final int RELOAD = 1;

    public static final Type<GunActionPayload> TYPE = new Type<>(Beyonder.id("gun_action"));
    public static final StreamCodec<FriendlyByteBuf, GunActionPayload> STREAM_CODEC =
            CustomPacketPayload.codec(GunActionPayload::write, GunActionPayload::new);

    private GunActionPayload(FriendlyByteBuf buf) {
        this(buf.readByte());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeByte(action);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
