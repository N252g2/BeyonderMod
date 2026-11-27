package io.github.n252g2.beyonder.mixin;

import io.github.n252g2.beyonder.core.Equipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While weapons are drawn, the main hand is the weapon slot rather than the selected hotbar slot. */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Shadow
    @Final
    public Player player;

    @Inject(method = "getSelected", at = @At("HEAD"), cancellable = true)
    private void beyonder$drawnWeapon(CallbackInfoReturnable<ItemStack> cir) {
        if (Equipment.redirectsMainHand(player)) {
            cir.setReturnValue(Equipment.item(player, Equipment.Slot.WEAPON));
        }
    }

    /** The drop key does nothing while drawn: a drawn weapon isn't tossed away by accident. */
    @Inject(method = "removeFromSelected", at = @At("HEAD"), cancellable = true)
    private void beyonder$keepDrawnWeapon(boolean all, CallbackInfoReturnable<ItemStack> cir) {
        if (Equipment.redirectsMainHand(player)) {
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}
