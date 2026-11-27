package io.github.n252g2.beyonder.item;

import io.github.n252g2.beyonder.Beyonder;
import io.github.n252g2.beyonder.core.Equipment;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A piece of clothing for one of the equipment's clothing slots. Worn under armour; drawn over the
 * character (see ClothingLayer). Right-click to put it on.
 */
public class ClothingItem extends Item {
    /** Hats have their own models; everything else is a skin-shaped overlay. */
    public enum Hat { NONE, TOP_HAT, BOWLER, FLAT_CAP }

    private final Equipment.Slot slot;
    private final Hat hat;
    private final boolean sleeves;

    public ClothingItem(Properties properties, Equipment.Slot slot, Hat hat, boolean sleeves) {
        super(properties.stacksTo(1));
        this.slot = slot;
        this.hat = hat;
        this.sleeves = sleeves;
    }

    public Equipment.Slot slot() {
        return slot;
    }

    public Hat hat() {
        return hat;
    }

    /** Whether it covers the arms (and so has a slim-arm texture too). */
    public boolean sleeves() {
        return sleeves;
    }

    public ResourceLocation texture(boolean slim) {
        String name = BuiltInRegistries.ITEM.getKey(this).getPath();
        return Beyonder.id("textures/clothing/" + name + (slim && sleeves ? "_slim" : "") + ".png");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!level.isClientSide) {
            ItemStack worn = Equipment.item(player, slot);
            Equipment.get(player).items().set(slot.ordinal(), held.copy());
            player.setItemInHand(hand, worn);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                    SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(slot.label() + ". Right-click to wear it.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Worn under armour.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
