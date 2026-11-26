package io.github.n252g2.beyonder.net;

import io.github.n252g2.beyonder.Beyonder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: use an active ability. The server checks everything. */
public record UseAbilityPayload(String ability) implements CustomPacketPayload {
    public static final Type<UseAbilityPayload> TYPE = new Type<>(Beyonder.id("use_ability"));
    public static final StreamCodec<FriendlyByteBuf, UseAbilityPayload> STREAM_CODEC =
            CustomPacketPayload.codec(UseAbilityPayload::write, UseAbilityPayload::new);

    private UseAbilityPayload(FriendlyByteBuf buf) {
        this(buf.readUtf(64));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeUtf(ability, 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
