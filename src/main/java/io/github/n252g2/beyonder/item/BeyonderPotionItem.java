package io.github.n252g2.beyonder.item;

import io.github.n252g2.beyonder.core.Beyonders;
import io.github.n252g2.beyonder.core.Pathway;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/** A Beyonder potion for one Sequence of one pathway. Drinking it is how you advance, or how you lose control. */
public class BeyonderPotionItem extends Item {
    private final Pathway pathway;
    private final int sequence;

    public BeyonderPotionItem(Pathway pathway, int sequence, Properties properties) {
        super(properties);
        this.pathway = pathway;
        this.sequence = sequence;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 48;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            Beyonders.drinkPotion(player, pathway, sequence, quality(stack));
            player.getCooldowns().addCooldown(this, 200);
        }
        if (entity instanceof Player player && !player.getAbilities().instabuild) {
            stack.shrink(1);
            if (stack.isEmpty()) {
                return new ItemStack(Items.GLASS_BOTTLE);
            }
            player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE));
        }
        return stack;
    }

    // ------------------------------------------------------------------ brewing quality

    /** 0 rough, 1 ordinary, 2 fine, 3 perfect. Potions that were never brewed count as ordinary. */
    public static int quality(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return 1;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains("Quality") ? Math.max(0, Math.min(3, tag.getInt("Quality"))) : 1;
    }

    public static ItemStack withQuality(ItemStack stack, int quality) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt("Quality", quality);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static String qualityName(int quality) {
        return switch (quality) {
            case 0 -> "Rough";
            case 2 -> "Fine";
            case 3 -> "Perfect";
            default -> "Ordinary";
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int q = quality(stack);
        tooltip.add(Component.literal("Quality: " + qualityName(q)).withStyle(switch (q) {
            case 0 -> ChatFormatting.DARK_GRAY;
            case 2 -> ChatFormatting.AQUA;
            case 3 -> ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.GRAY;
        }));
        tooltip.add(Component.literal("Sequence " + sequence + ": " + pathway.sequenceName(sequence))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal(sequence == 9
                ? "Becomes a Beyonder of the " + pathway.sequenceName(9) + " pathway."
                : "Digest your current potion fully before drinking.").withStyle(ChatFormatting.GRAY));
    }
}
