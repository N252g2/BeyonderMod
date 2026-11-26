package io.github.n252g2.beyonder.item;

import io.github.n252g2.beyonder.core.Beyonders;
import io.github.n252g2.beyonder.core.Pathway;
import java.util.List;
import net.minecraft.ChatFormatting;
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
            Beyonders.drinkPotion(player, pathway, sequence);
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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Sequence " + sequence + ": " + pathway.sequenceName(sequence))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal(sequence == 9
                ? "Becomes a Beyonder of the " + pathway.sequenceName(9) + " pathway."
                : "Digest your current potion fully before drinking.").withStyle(ChatFormatting.GRAY));
    }
}
