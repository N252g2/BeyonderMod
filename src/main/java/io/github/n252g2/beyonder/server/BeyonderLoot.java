package io.github.n252g2.beyonder.server;

import io.github.n252g2.beyonder.content.BeyonderContent;
import io.github.n252g2.beyonder.item.FormulaItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Where mystical ingredients, formulas and coins come from until quests and markets exist.
 *
 * <ul>
 *   <li>Wild: starlit lavender from flowers and grass picked at night under open sky; dream moss
 *       from moss, vines and leaves.</li>
 *   <li>Creatures you defeat: wraith ash (undead), shadow silk (spiders), phantom eyes, mirror
 *       shards and the odd formula (witches), and pence and soli from hostiles.</li>
 *   <li>Crimson nights (full moon): crimson spores from any hostile, and everything wild is
 *       twice as likely.</li>
 * </ul>
 */
public final class BeyonderLoot {
    private BeyonderLoot() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BeyonderLoot::onDrops);
        NeoForge.EVENT_BUS.addListener(BeyonderLoot::onBreak);
    }

    /** The full moon is a crimson night, matching the client's red sky. */
    public static boolean isCrimsonNight(Level level) {
        return level.dimension() == Level.OVERWORLD && level.isNight() && level.getMoonPhase() == 0;
    }

    private static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof Player)) {
            return;
        }
        RandomSource r = level.random;
        EntityType<?> type = dead.getType();
        boolean crimson = isCrimsonNight(level);

        if (type.is(EntityTypeTags.UNDEAD) && r.nextFloat() < 0.15F) {
            drop(event, dead, BeyonderContent.WRAITH_ASH.get(), 1 + r.nextInt(2));
        }
        if ((type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER) && r.nextFloat() < 0.25F) {
            drop(event, dead, BeyonderContent.SHADOW_SILK.get(), 1);
        }
        if (type == EntityType.PHANTOM && r.nextFloat() < 0.4F) {
            drop(event, dead, BeyonderContent.PHANTOM_EYE.get(), 1);
        }
        if (type == EntityType.WITCH) {
            if (r.nextFloat() < 0.5F) {
                drop(event, dead, BeyonderContent.MIRROR_SHARD.get(), 1);
            }
            if (r.nextFloat() < 0.1F) {
                drop(event, dead, randomFormula(r), 1);
            }
        }
        if (dead instanceof Enemy) {
            if (crimson && r.nextFloat() < 0.2F) {
                drop(event, dead, BeyonderContent.CRIMSON_SPORE.get(), 1);
            }
            if (r.nextFloat() < 0.35F) {
                drop(event, dead, BeyonderContent.COPPER_PENNY.get(), 1 + r.nextInt(3));
            }
            boolean wellOff = type == EntityType.WITCH || type == EntityType.PILLAGER || type == EntityType.VINDICATOR
                    || type == EntityType.EVOKER;
            if (wellOff && r.nextFloat() < 0.3F) {
                drop(event, dead, BeyonderContent.SILVER_SOLI.get(), 1);
            }
        }
    }

    /** Mostly the Seer formula; the higher ones are rarer. */
    private static Item randomFormula(RandomSource r) {
        float roll = r.nextFloat();
        FormulaItem item = roll < 0.6F ? BeyonderContent.SEER_FORMULA.get()
                : roll < 0.9F ? BeyonderContent.CLOWN_FORMULA.get()
                : BeyonderContent.MAGICIAN_FORMULA.get();
        return item;
    }

    private static void drop(LivingDropsEvent event, LivingEntity at, Item item, int count) {
        event.getDrops().add(new ItemEntity(at.level(), at.getX(), at.getY() + 0.5, at.getZ(), new ItemStack(item, count)));
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || event.getPlayer().isCreative()) {
            return;
        }
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        RandomSource r = level.random;
        float boost = isCrimsonNight(level) ? 2.0F : 1.0F;

        boolean bloom = state.is(BlockTags.FLOWERS) || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS);
        if (bloom && level.isNight() && level.canSeeSky(pos) && r.nextFloat() < 0.1F * boost) {
            Block.popResource(level, pos, new ItemStack(BeyonderContent.STARLIT_LAVENDER.get()));
        }
        float mossChance = state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.MOSS_CARPET) ? 0.2F
                : state.is(Blocks.VINE) || state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT) ? 0.1F
                : state.is(BlockTags.LEAVES) ? 0.02F : 0.0F;
        if (mossChance > 0 && r.nextFloat() < mossChance * boost) {
            Block.popResource(level, pos, new ItemStack(BeyonderContent.DREAM_MOSS.get()));
        }
    }
}
