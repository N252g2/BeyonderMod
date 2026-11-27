package io.github.n252g2.beyonder.mixin;

import io.github.n252g2.beyonder.core.EquipmentContainer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The creative inventory tab has no room for the equipment slots, so it leaves them out. */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
    @Inject(method = "selectTab", at = @At("TAIL"))
    private void beyonder$hideEquipment(CreativeModeTab tab, CallbackInfo ci) {
        ((AbstractContainerScreen<?>) (Object) this).getMenu().slots.removeIf(s -> s.container instanceof EquipmentContainer);
    }
}
