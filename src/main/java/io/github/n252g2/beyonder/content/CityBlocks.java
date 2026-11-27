package io.github.n252g2.beyonder.content;

import io.github.n252g2.beyonder.Beyonder;
import io.github.n252g2.beyonder.block.TingenFacingBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The Tingen city block set (mod 0.11.0): London stock and red brick, Portland-style ashlar, clay roofs,
 * flagstone pavements and kerbs, weathered timber, sash and shop windows, awnings, wall lamps, a clock dial,
 * gravestones, crates and sewer grates. Textures and models come from {@code tools/gen_city_blocks.py}.
 */
public final class CityBlocks {
    private CityBlocks() {}

    private static final List<DeferredItem<BlockItem>> ITEMS = new ArrayList<>();

    // ------------------------------------------------------------------ brick and stone families

    public static final DeferredBlock<Block> STOCK_BRICKS = simple("stock_bricks", stone(MapColor.TERRACOTTA_YELLOW, SoundType.STONE));
    public static final DeferredBlock<StairBlock> STOCK_BRICK_STAIRS = stairs("stock_brick_stairs", STOCK_BRICKS, stone(MapColor.TERRACOTTA_YELLOW, SoundType.STONE));
    public static final DeferredBlock<SlabBlock> STOCK_BRICK_SLAB = block("stock_brick_slab", SlabBlock::new, stone(MapColor.TERRACOTTA_YELLOW, SoundType.STONE));
    public static final DeferredBlock<WallBlock> STOCK_BRICK_WALL = block("stock_brick_wall", WallBlock::new, stone(MapColor.TERRACOTTA_YELLOW, SoundType.STONE).forceSolidOn());

    public static final DeferredBlock<Block> RED_BRICKS = simple("red_bricks", stone(MapColor.TERRACOTTA_RED, SoundType.STONE));
    public static final DeferredBlock<StairBlock> RED_BRICK_STAIRS = stairs("red_brick_stairs", RED_BRICKS, stone(MapColor.TERRACOTTA_RED, SoundType.STONE));
    public static final DeferredBlock<SlabBlock> RED_BRICK_SLAB = block("red_brick_slab", SlabBlock::new, stone(MapColor.TERRACOTTA_RED, SoundType.STONE));
    public static final DeferredBlock<WallBlock> RED_BRICK_WALL = block("red_brick_wall", WallBlock::new, stone(MapColor.TERRACOTTA_RED, SoundType.STONE).forceSolidOn());

    public static final DeferredBlock<Block> ASHLAR = simple("ashlar", stone(MapColor.SAND, SoundType.STONE));
    public static final DeferredBlock<StairBlock> ASHLAR_STAIRS = stairs("ashlar_stairs", ASHLAR, stone(MapColor.SAND, SoundType.STONE));
    public static final DeferredBlock<SlabBlock> ASHLAR_SLAB = block("ashlar_slab", SlabBlock::new, stone(MapColor.SAND, SoundType.STONE));
    public static final DeferredBlock<WallBlock> ASHLAR_WALL = block("ashlar_wall", WallBlock::new, stone(MapColor.SAND, SoundType.STONE).forceSolidOn());
    public static final DeferredBlock<Block> CARVED_ASHLAR = simple("carved_ashlar", stone(MapColor.SAND, SoundType.STONE));
    public static final DeferredBlock<RotatedPillarBlock> ASHLAR_PILLAR = block("ashlar_pillar", RotatedPillarBlock::new, stone(MapColor.SAND, SoundType.STONE));

    // ------------------------------------------------------------------ roofs and paving

    public static final DeferredBlock<Block> CLAY_ROOF_TILES = simple("clay_roof_tiles", stone(MapColor.TERRACOTTA_ORANGE, SoundType.DEEPSLATE_TILES));
    public static final DeferredBlock<StairBlock> CLAY_ROOF_STAIRS = stairs("clay_roof_stairs", CLAY_ROOF_TILES, stone(MapColor.TERRACOTTA_ORANGE, SoundType.DEEPSLATE_TILES));
    public static final DeferredBlock<SlabBlock> CLAY_ROOF_SLAB = block("clay_roof_slab", SlabBlock::new, stone(MapColor.TERRACOTTA_ORANGE, SoundType.DEEPSLATE_TILES));

    public static final DeferredBlock<Block> FLAGSTONES = simple("flagstones", stone(MapColor.STONE, SoundType.STONE));
    public static final DeferredBlock<SlabBlock> FLAGSTONE_SLAB = block("flagstone_slab", SlabBlock::new, stone(MapColor.STONE, SoundType.STONE));
    public static final DeferredBlock<Block> KERBSTONE = simple("kerbstone", stone(MapColor.STONE, SoundType.STONE));

    // ------------------------------------------------------------------ timber

    public static final DeferredBlock<Block> WEATHERED_PLANKS = simple("weathered_planks", wood(MapColor.WOOD));
    public static final DeferredBlock<StairBlock> WEATHERED_PLANK_STAIRS = stairs("weathered_plank_stairs", WEATHERED_PLANKS, wood(MapColor.WOOD));
    public static final DeferredBlock<SlabBlock> WEATHERED_PLANK_SLAB = block("weathered_plank_slab", SlabBlock::new, wood(MapColor.WOOD));
    public static final DeferredBlock<RotatedPillarBlock> DARK_TIMBER = block("dark_timber", RotatedPillarBlock::new, wood(MapColor.COLOR_BROWN));

    // ------------------------------------------------------------------ windows

    public static final DeferredBlock<Block> SASH_WINDOW = simple("sash_window", glass(0));
    public static final DeferredBlock<Block> LIT_SASH_WINDOW = simple("lit_sash_window", glass(11).mapColor(MapColor.COLOR_ORANGE));
    public static final DeferredBlock<TransparentBlock> SHOP_WINDOW = block("shop_window", TransparentBlock::new,
            glass(0).noOcclusion().isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));

    // ------------------------------------------------------------------ street detail

    public static final DeferredBlock<TingenFacingBlock> CLOCK_FACE = facing("clock_face", TingenFacingBlock.Kind.CLOCK,
            stone(MapColor.SAND, SoundType.STONE));
    public static final DeferredBlock<Block> CRATE = simple("crate", wood(MapColor.WOOD).strength(1.5F));
    public static final DeferredBlock<Block> SEWER_GRATE = simple("sewer_grate", stone(MapColor.STONE, SoundType.METAL));
    public static final DeferredBlock<TingenFacingBlock> GRAVESTONE = facing("gravestone", TingenFacingBlock.Kind.GRAVESTONE,
            stone(MapColor.STONE, SoundType.STONE).noOcclusion());
    public static final DeferredBlock<TingenFacingBlock> RED_AWNING = awning("red_awning", MapColor.COLOR_RED);
    public static final DeferredBlock<TingenFacingBlock> GREEN_AWNING = awning("green_awning", MapColor.COLOR_GREEN);
    public static final DeferredBlock<TingenFacingBlock> BLUE_AWNING = awning("blue_awning", MapColor.COLOR_BLUE);
    public static final DeferredBlock<TingenFacingBlock> WALL_GAS_LAMP = facing("wall_gas_lamp", TingenFacingBlock.Kind.WALL_LAMP,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5F, 6.0F).sound(SoundType.LANTERN)
                    .lightLevel(s -> 14).noOcclusion().noCollission().pushReaction(PushReaction.DESTROY)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<TingenFacingBlock> PARK_BENCH = facing("park_bench", TingenFacingBlock.Kind.BENCH,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 6.0F).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<TingenFacingBlock> POST_BOX = facing("post_box", TingenFacingBlock.Kind.POST_BOX,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3.0F, 6.0F).sound(SoundType.METAL)
                    .noOcclusion().requiresCorrectToolForDrops());

    /** The block items, in creative-tab order. */
    public static List<DeferredItem<BlockItem>> tabItems() {
        return ITEMS;
    }

    /** Loads this class so its entries join the registers before they fire. */
    public static void init() {}

    // ------------------------------------------------------------------ helpers

    private static <B extends Block> DeferredBlock<B> block(String name, Function<BlockBehaviour.Properties, B> factory,
                                                            BlockBehaviour.Properties props) {
        DeferredBlock<B> block = Beyonder.BLOCKS.registerBlock(name, factory, props);
        ITEMS.add(Beyonder.ITEMS.registerSimpleBlockItem(name, block, new Item.Properties()));
        return block;
    }

    private static DeferredBlock<Block> simple(String name, BlockBehaviour.Properties props) {
        return block(name, Block::new, props);
    }

    private static DeferredBlock<StairBlock> stairs(String name, Supplier<? extends Block> base, BlockBehaviour.Properties props) {
        return block(name, p -> new StairBlock(base.get().defaultBlockState(), p), props);
    }

    private static DeferredBlock<TingenFacingBlock> facing(String name, TingenFacingBlock.Kind kind, BlockBehaviour.Properties props) {
        return block(name, p -> new TingenFacingBlock(p, kind), props);
    }

    private static DeferredBlock<TingenFacingBlock> awning(String name, MapColor color) {
        return facing(name, TingenFacingBlock.Kind.AWNING, BlockBehaviour.Properties.of().mapColor(color)
                .strength(0.8F).sound(SoundType.WOOL).noOcclusion().noCollission().ignitedByLava()
                .pushReaction(PushReaction.DESTROY));
    }

    private static BlockBehaviour.Properties stone(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(2.0F, 6.0F)
                .sound(sound)
                .requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties wood(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .instrument(NoteBlockInstrument.BASS)
                .strength(2.0F, 3.0F)
                .sound(SoundType.WOOD)
                .ignitedByLava();
    }

    private static BlockBehaviour.Properties glass(int light) {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.NONE)
                .instrument(NoteBlockInstrument.HAT)
                .strength(0.6F)
                .sound(SoundType.GLASS)
                .lightLevel(s -> light);
    }
}
