package io.github.n252g2.beyonder.item;

import io.github.n252g2.beyonder.content.BeyonderContent;
import io.github.n252g2.beyonder.core.BeyonderData;
import io.github.n252g2.beyonder.core.Beyonders;
import io.github.n252g2.beyonder.core.Pathway;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Loen coinage: 12 copper pence make a silver soli, 20 soli make a gold pound.
 *
 * <ul>
 *   <li>Pence: use with 12 or more in hand to change them for a soli.</li>
 *   <li>Soli: use to flip it, a Seer's coin divination ("is danger near?"); sneak-use to change
 *       20 for a pound, or break one into 12 pence when you have fewer.</li>
 *   <li>Pound: sneak-use to break it into 20 soli.</li>
 * </ul>
 */
public class CoinItem extends Item {
    public enum Denomination { PENNY, SOLI, POUND }

    private static final float FLIP_COST = 4.0F;
    private static final int DANGER_RADIUS = 32;

    private final Denomination denomination;

    public CoinItem(Denomination denomination, Properties properties) {
        super(properties);
        this.denomination = denomination;
    }

    /** Value in pence. */
    public int pence() {
        return switch (denomination) {
            case PENNY -> 1;
            case SOLI -> 12;
            case POUND -> 240;
        };
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.success(stack);
        }
        boolean sneaking = sp.isShiftKeyDown();
        switch (denomination) {
            case PENNY -> exchange(sp, stack, 12, new ItemStack(BeyonderContent.SILVER_SOLI.get()));
            case SOLI -> {
                if (!sneaking) {
                    flip(sp);
                } else if (stack.getCount() >= 20) {
                    exchange(sp, stack, 20, new ItemStack(BeyonderContent.GOLD_POUND.get()));
                } else {
                    exchange(sp, stack, 1, new ItemStack(BeyonderContent.COPPER_PENNY.get(), 12));
                }
            }
            case POUND -> {
                if (sneaking) {
                    exchange(sp, stack, 1, new ItemStack(BeyonderContent.SILVER_SOLI.get(), 20));
                } else {
                    sp.displayClientMessage(Component.literal("Sneak and use to break a pound into 20 soli.")
                            .withStyle(ChatFormatting.GRAY), true);
                }
            }
        }
        return InteractionResultHolder.consume(stack);
    }

    private static void exchange(ServerPlayer player, ItemStack stack, int cost, ItemStack result) {
        if (stack.getCount() < cost) {
            player.displayClientMessage(Component.literal("You need " + cost + " of these to change them.")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        stack.shrink(cost);
        if (!player.getInventory().add(result)) {
            player.drop(result, false);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_PLACE,
                SoundSource.PLAYERS, 0.6F, 1.8F);
    }

    /**
     * Coin divination. Anyone can flip a coin; a Seer's flip answers the question "is danger near?"
     * and lower Sequences answer more truly.
     */
    private void flip(ServerPlayer player) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return;
        }
        player.getCooldowns().addCooldown(this, 30);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.5F, 2.0F);
        level.sendParticles(ParticleTypes.WAX_OFF, player.getX(), player.getEyeY() + 0.4, player.getZ(), 6, 0.1, 0.2, 0.1, 0.0);

        BeyonderData data = Beyonders.get(player);
        if (data.pathway() != Pathway.SEER) {
            boolean heads = level.random.nextBoolean();
            player.displayClientMessage(Component.literal(heads ? "Heads." : "Tails.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (!Beyonders.trySpend(player, FLIP_COST)) {
            return;
        }
        boolean danger = !level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(DANGER_RADIUS),
                m -> m instanceof Enemy && m.isAlive()).isEmpty()
                || !level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(DANGER_RADIUS),
                p -> p != player && Beyonders.get(p).lossOfControl() > 60.0F).isEmpty();
        // Sequence 9 reads the coin right 70% of the time, Sequence 7 90%.
        float accuracy = 0.7F + 0.1F * (9 - Math.max(7, Math.min(9, data.sequence())));
        boolean answer = level.random.nextFloat() < accuracy ? danger : !danger;
        player.displayClientMessage(Component.literal(answer
                ? "Heads: danger is near. Your spirit prickles."
                : "Tails: no danger nearby. The coin is cool in your palm.")
                .withStyle(answer ? ChatFormatting.RED : ChatFormatting.AQUA), true);
        Beyonders.act(player, 9, 3.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        switch (denomination) {
            case PENNY -> tooltip.add(Component.literal("12 pence = 1 soli. Use with 12 in hand to change them.")
                    .withStyle(ChatFormatting.GRAY));
            case SOLI -> {
                tooltip.add(Component.literal("20 soli = 1 pound. Sneak-use to change 20, or to break one into pence.")
                        .withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.literal("Use to flip it. A Seer's coin answers: is danger near?")
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
            case POUND -> tooltip.add(Component.literal("Sneak-use to break it into 20 soli.").withStyle(ChatFormatting.GRAY));
        }
    }
}
