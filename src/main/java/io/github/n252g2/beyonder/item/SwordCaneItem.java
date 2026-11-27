package io.github.n252g2.beyonder.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * A gentleman's walking cane with a blade hidden in it. Use it to draw or sheathe the blade:
 * sheathed it is a heavy stick, drawn it is a quick, sharp sword.
 */
public class SwordCaneItem extends SwordItem {
    public SwordCaneItem(Properties properties) {
        super(Tiers.IRON, properties.attributes(SwordItem.createAttributes(Tiers.IRON, 0, -2.2F)));
    }

    public static boolean isDrawn(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean("Drawn");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            boolean drawn = !isDrawn(stack);
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            tag.putBoolean("Drawn", drawn);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS,
                    SwordItem.createAttributes(Tiers.IRON, drawn ? 3 : 0, drawn ? -1.9F : -2.2F));
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    drawn ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.6F, 1.8F);
        }
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(isDrawn(stack) ? "Blade drawn." : "Blade sheathed.").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Use to draw or sheathe the hidden blade.").withStyle(ChatFormatting.GRAY));
    }
}
