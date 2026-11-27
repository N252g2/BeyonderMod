package io.github.n252g2.beyonder.mixin;

import io.github.n252g2.beyonder.core.Equipment;
import io.github.n252g2.beyonder.core.EquipmentContainer;
import io.github.n252g2.beyonder.core.EquipmentMenuSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Beyonder equipment slots live in the player's own inventory menu (after vanilla's 46), so
 * the game keeps them in sync like any other inventory slot.
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends RecipeBookMenu<CraftingInput, CraftingRecipe> {
    protected InventoryMenuMixin(MenuType<?> type, int id) {
        super(type, id);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void beyonder$equipmentSlots(Inventory inventory, boolean active, Player owner, CallbackInfo ci) {
        EquipmentContainer container = new EquipmentContainer(owner);
        for (Equipment.Slot kind : Equipment.Slot.values()) {
            this.addSlot(new EquipmentMenuSlot(container, kind));
        }
    }

    /** Shift-clicking from the bag or hotbar fills a matching equipment slot first. */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void beyonder$quickEquip(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        if (index < 9 || index >= 45 || index >= this.slots.size()) {
            return;
        }
        Slot from = this.slots.get(index);
        if (!from.hasItem()) {
            return;
        }
        ItemStack stack = from.getItem();
        ItemStack original = stack.copy();
        for (int i = 46; i < this.slots.size(); i++) {
            if (!(this.slots.get(i) instanceof EquipmentMenuSlot target) || !target.mayPlace(stack)) {
                continue;
            }
            ItemStack there = target.getItem();
            boolean room = there.isEmpty()
                    || (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < target.getMaxStackSize(stack));
            if (room && this.moveItemStackTo(stack, i, i + 1, false)) {
                if (stack.isEmpty()) {
                    from.setByPlayer(ItemStack.EMPTY);
                } else {
                    from.setChanged();
                }
                cir.setReturnValue(original);
                return;
            }
        }
    }
}
