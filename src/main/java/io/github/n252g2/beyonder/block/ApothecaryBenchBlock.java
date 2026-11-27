package io.github.n252g2.beyonder.block;

import io.github.n252g2.beyonder.brewing.Brewing;
import io.github.n252g2.beyonder.brewing.Formula;
import io.github.n252g2.beyonder.item.FormulaItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An apothecary's bench: a heavy table with a spirit burner and flasks. Use it while holding a potion
 * formula and carrying the ingredients plus a water bottle: they go into the flask and the brewing
 * minigame opens (see {@code BrewingScreen}). How well you brew decides the potion's quality.
 */
public class ApothecaryBenchBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 12, 0, 16, 15, 16),      // table top
            Block.box(1, 0, 1, 3, 12, 3), Block.box(13, 0, 1, 15, 12, 3),
            Block.box(1, 0, 13, 3, 12, 15), Block.box(13, 0, 13, 15, 12, 15),
            Block.box(3, 15, 5, 7, 22, 9),        // tall flask
            Block.box(9, 15, 8, 13, 19, 12));     // squat flask

    public ApothecaryBenchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof FormulaItem formulaItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) {
            return ItemInteractionResult.SUCCESS;
        }
        if (Brewing.isBrewing(sp)) {
            sp.displayClientMessage(Component.literal("You are already brewing.").withStyle(ChatFormatting.GRAY), true);
            return ItemInteractionResult.CONSUME;
        }
        brew(sp, (ServerLevel) level, pos, formulaItem);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal("Hold a potion formula to brew. Bring a water bottle and its ingredients.")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void brew(ServerPlayer player, ServerLevel level, BlockPos pos, FormulaItem formulaItem) {
        Formula formula = formulaItem.formula();
        Inventory inv = player.getInventory();
        boolean free = player.isCreative();

        if (!free) {
            List<MutableComponent> missing = new ArrayList<>();
            if (count(inv, ApothecaryBenchBlock::isWaterBottle) < 1) {
                missing.add(Component.literal("1 × Water Bottle"));
            }
            for (Map.Entry<Supplier<? extends Item>, Integer> e : formula.ingredients().entrySet()) {
                Item item = e.getKey().get();
                int have = count(inv, s -> s.is(item));
                if (have < e.getValue()) {
                    missing.add(Component.literal((e.getValue() - have) + " × ").append(item.getDescription()));
                }
            }
            if (!missing.isEmpty()) {
                MutableComponent msg = Component.literal("Still missing: ").withStyle(ChatFormatting.GRAY);
                for (int i = 0; i < missing.size(); i++) {
                    if (i > 0) {
                        msg.append(", ");
                    }
                    msg.append(missing.get(i));
                }
                player.displayClientMessage(msg, false);
                level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.6F, 0.6F);
                return;
            }
            take(inv, ApothecaryBenchBlock::isWaterBottle, 1);
            for (Map.Entry<Supplier<? extends Item>, Integer> e : formula.ingredients().entrySet()) {
                Item item = e.getKey().get();
                take(inv, s -> s.is(item), e.getValue());
            }
        }

        Brewing.start(player, level, pos, formula);
    }

    private static boolean isWaterBottle(ItemStack stack) {
        if (!stack.is(Items.POTION)) {
            return false;
        }
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.is(Potions.WATER);
    }

    private static int count(Inventory inv, Predicate<ItemStack> test) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && test.test(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void take(Inventory inv, Predicate<ItemStack> test, int amount) {
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && test.test(s)) {
                int used = Math.min(amount, s.getCount());
                s.shrink(used);
                amount -= used;
            }
        }
        inv.setChanged();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // The spirit burner under the tall flask.
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.31, pos.getY() + 0.97, pos.getZ() + 0.44, 0.0, 0.002, 0.0);
        }
        if (random.nextInt(10) == 0) {
            level.addParticle(ParticleTypes.BUBBLE_POP, pos.getX() + 0.31, pos.getY() + 1.38, pos.getZ() + 0.44, 0.0, 0.01, 0.0);
        }
    }
}
