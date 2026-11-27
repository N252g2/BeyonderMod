package io.github.n252g2.beyonder.mixin;

import io.github.n252g2.beyonder.core.Equipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Whatever replaces the main-hand item while drawn goes into the weapon slot. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "setItemSlot", at = @At("HEAD"), cancellable = true)
    private void beyonder$weaponSlot(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (slot == EquipmentSlot.MAINHAND && Equipment.redirectsMainHand(self)) {
            Equipment.get(self).items().set(Equipment.Slot.WEAPON.ordinal(), stack);
            ci.cancel();
        }
    }
}
