package io.github.n252g2.beyonder.core;

import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** One Beyonder equipment slot in the player's inventory menu. */
public final class EquipmentMenuSlot extends Slot {
    private final Equipment.Slot kind;
    private final Player owner;

    public EquipmentMenuSlot(EquipmentContainer container, Equipment.Slot kind) {
        super(container, kind.ordinal(), kind.x, kind.y);
        this.kind = kind;
        this.owner = container.player();
    }

    public Equipment.Slot kind() {
        return kind;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return kind.accepts(stack);
    }

    @Override
    public int getMaxStackSize() {
        return kind.maxStack();
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return Math.min(kind.maxStack(), stack.getMaxStackSize());
    }

    @Override
    public boolean mayPickup(Player player) {
        // A drawn weapon stays in hand until it is put away.
        return !(kind == Equipment.Slot.WEAPON && Equipment.isDrawn(owner)) && super.mayPickup(player);
    }

    @Override
    public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
        return Pair.of(InventoryMenu.BLOCK_ATLAS, kind.icon());
    }
}
