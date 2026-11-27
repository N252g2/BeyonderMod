package io.github.n252g2.beyonder.net;

import io.github.n252g2.beyonder.Beyonder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the finished character from the creator. The server validates all of it. */
public record CreateCharacterPayload(String name, String background, int[] appearance) implements CustomPacketPayload {
    public static final Type<CreateCharacterPayload> TYPE = new Type<>(Beyonder.id("create_character"));
    public static final StreamCodec<FriendlyByteBuf, CreateCharacterPayload> STREAM_CODEC =
            CustomPacketPayload.codec(CreateCharacterPayload::write, CreateCharacterPayload::new);

    private CreateCharacterPayload(FriendlyByteBuf buf) {
        this(buf.readUtf(32), buf.readUtf(32), buf.readVarIntArray(16));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(name, 32);
        buf.writeUtf(background, 32);
        buf.writeVarIntArray(appearance);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
