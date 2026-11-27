package io.github.n252g2.beyonder.net;

import io.github.n252g2.beyonder.Beyonder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * Server to clients: what others can see of a player's equipment. {@code visible} holds the main
 * weapon, the sidearm (off hand), the six clothing slots, then the three belt slots.
 */
public record EquipStatePayload(int entityId, boolean drawn, boolean clothesOverArmour, List<ItemStack> visible)
        implements CustomPacketPayload {
    public static final int WEAPON = 0, SIDEARM = 1, CLOTHING = 2, BELT = 8;

    public static final Type<EquipStatePayload> TYPE = new Type<>(Beyonder.id("equip_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EquipStatePayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), EquipStatePayload::read);

    private static EquipStatePayload read(RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        boolean drawn = buf.readBoolean();
        boolean clothes = buf.readBoolean();
        int n = buf.readVarInt();
        List<ItemStack> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
        return new EquipStatePayload(id, drawn, clothes, items);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBoolean(drawn);
        buf.writeBoolean(clothesOverArmour);
        buf.writeVarInt(visible.size());
        for (ItemStack s : visible) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s);
        }
    }

    public ItemStack item(int index) {
        return index < visible.size() ? visible.get(index) : ItemStack.EMPTY;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
