package io.github.n252g2.beyonder.net;

import io.github.n252g2.beyonder.Beyonder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: draw or sheathe, swap weapons, use a belt slot, or toggle clothes over armour. */
public record EquipActionPayload(int action) implements CustomPacketPayload {
    public static final int TOGGLE_DRAW = 0;
    public static final int SHEATHE = 1;
    public static final int SWAP = 2;
    public static final int TOGGLE_CLOTHES = 3;
    public static final int BELT_FIRST = 10; // 10, 11, 12: belt slots 1-3

    public static final Type<EquipActionPayload> TYPE = new Type<>(Beyonder.id("equip_action"));
    public static final StreamCodec<FriendlyByteBuf, EquipActionPayload> STREAM_CODEC =
            CustomPacketPayload.codec(EquipActionPayload::write, EquipActionPayload::new);

    private EquipActionPayload(FriendlyByteBuf buf) {
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
