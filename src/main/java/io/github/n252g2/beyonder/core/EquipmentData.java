package io.github.n252g2.beyonder.core;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** What a player has in the Beyonder equipment slots, and whether their weapons are drawn. */
public final class EquipmentData implements INBTSerializable<CompoundTag> {
    private final NonNullList<ItemStack> items = NonNullList.withSize(Equipment.SIZE, ItemStack.EMPTY);
    private boolean drawn;
    private int drawnHotbarSlot = -1;
    private boolean clothesOverArmour;

    public NonNullList<ItemStack> items() {
        return items;
    }

    public boolean drawn() {
        return drawn;
    }

    public void setDrawn(boolean drawn, int hotbarSlot) {
        this.drawn = drawn;
        this.drawnHotbarSlot = drawn ? hotbarSlot : -1;
    }

    /** The hotbar slot that was selected when the weapons came out; changing it puts them away. */
    public int drawnHotbarSlot() {
        return drawnHotbarSlot;
    }

    /** Cosmetic: show the clothing instead of the armour worn over it. */
    public boolean clothesOverArmour() {
        return clothesOverArmour;
    }

    public void setClothesOverArmour(boolean value) {
        clothesOverArmour = value;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, items, provider);
        tag.putBoolean("ClothesOverArmour", clothesOverArmour);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(tag, items, provider);
        clothesOverArmour = tag.getBoolean("ClothesOverArmour");
        drawn = false; // weapons always start put away
        drawnHotbarSlot = -1;
    }
}
