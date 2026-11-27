package io.github.n252g2.beyonder.net;

import io.github.n252g2.beyonder.Beyonder;
import io.github.n252g2.beyonder.core.Appearance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: a player's character (name, background, appearance), or that it isn't made yet. */
public record CharacterSyncPayload(int entityId, boolean created, String name, String background, int[] appearance)
        implements CustomPacketPayload {
    public static final Type<CharacterSyncPayload> TYPE = new Type<>(Beyonder.id("character"));
    public static final StreamCodec<FriendlyByteBuf, CharacterSyncPayload> STREAM_CODEC =
            CustomPacketPayload.codec(CharacterSyncPayload::write, CharacterSyncPayload::new);

    private CharacterSyncPayload(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readBoolean(), buf.readUtf(32), buf.readUtf(32), Appearance.sanitize(buf.readVarIntArray(16)));
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBoolean(created);
        buf.writeUtf(name, 32);
        buf.writeUtf(background, 32);
        buf.writeVarIntArray(appearance);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
