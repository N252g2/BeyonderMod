package io.github.n252g2.beyonder.item;

import io.github.n252g2.beyonder.core.Beyonders;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Death Knell, a Sealed Artifact: an iron-black, long-barrelled revolver. Every shot finds its
 * target's weak point and lands as a fatal blow, and a bell tolls with it. Its price: each shot
 * draws on the wielder's Spirituality, and anyone without enough pays in blood instead.
 */
public class DeathKnellItem extends RevolverItem {
    public static final Stats STATS = new Stats(13.0F, 14, 50, 6, 2.0F, 0.15F, 3.4F);
    private static final float SPIRITUALITY_COST = 4.0F;

    public DeathKnellItem(Properties properties) {
        super(properties, STATS);
    }

    @Override
    protected boolean findsWeakness() {
        return true;
    }

    @Override
    protected void shotSound(ServerLevel level, ServerPlayer player) {
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BELL_BLOCK,
                SoundSource.PLAYERS, 1.2F, 0.5F);
    }

    @Override
    protected void afterShot(ServerPlayer player, ItemStack stack) {
        boolean paid = Beyonders.get(player).isBeyonder()
                && Beyonders.get(player).spirituality() >= SPIRITUALITY_COST
                && Beyonders.trySpend(player, SPIRITUALITY_COST);
        if (!paid && !player.isCreative()) {
            player.hurt(player.damageSources().magic(), 2.0F);
            player.displayClientMessage(Component.literal("The knell takes its toll from you.")
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Sealed Artifact").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.literal("Every shot finds a weakness and lands as a fatal blow.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Price: 4 Spirituality a shot, or your blood.").withStyle(ChatFormatting.DARK_RED));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
