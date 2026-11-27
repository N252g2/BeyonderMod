package io.github.n252g2.beyonder.net;

import io.github.n252g2.beyonder.Beyonder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to clients: play a body animation on a player (see PlayerAnim). */
public record PlayerAnimPayload(int entityId, int anim, int variant) implements CustomPacketPayload {
    public static final Type<PlayerAnimPayload> TYPE = new Type<>(Beyonder.id("player_anim"));
    public static final StreamCodec<FriendlyByteBuf, PlayerAnimPayload> STREAM_CODEC =
            CustomPacketPayload.codec(PlayerAnimPayload::write, PlayerAnimPayload::new);

    private PlayerAnimPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readByte(), buf.readVarInt());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeByte(anim);
        buf.writeVarInt(variant);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
