package io.github.n252g2.beyonder.core;

import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The equipment slots as a vanilla container, backed by the player's attachment. It reads the
 * attachment on every call, so it stays right after the player's data is loaded or copied.
 */
public final class EquipmentContainer implements Container {
    private final Player player;

    public EquipmentContainer(Player player) {
        this.player = player;
    }

    public Player player() {
        return player;
    }

    private EquipmentData data() {
        return Equipment.get(player);
    }

    @Override
    public int getContainerSize() {
        return Equipment.SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : data().items()) {
            if (!s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return data().items().get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack out = ContainerHelper.removeItem(data().items(), slot, amount);
        if (!out.isEmpty()) {
            setChanged();
        }
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(data().items(), slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        data().items().set(slot, stack);
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < Equipment.SIZE; i++) {
            data().items().set(i, ItemStack.EMPTY);
        }
    }
}
