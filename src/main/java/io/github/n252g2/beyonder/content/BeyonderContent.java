package io.github.n252g2.beyonder.content;

import io.github.n252g2.beyonder.core.Equipment;
import io.github.n252g2.beyonder.item.ClothingItem;

import io.github.n252g2.beyonder.Beyonder;
import io.github.n252g2.beyonder.block.ApothecaryBenchBlock;
import io.github.n252g2.beyonder.brewing.Formula;
import io.github.n252g2.beyonder.item.CoinItem;
import io.github.n252g2.beyonder.item.CrimsonGreatswordItem;
import io.github.n252g2.beyonder.item.DeathKnellItem;
import io.github.n252g2.beyonder.item.FormulaItem;
import io.github.n252g2.beyonder.item.LoreItem;
import io.github.n252g2.beyonder.item.RevolverItem;
import io.github.n252g2.beyonder.item.StaffItem;
import io.github.n252g2.beyonder.item.SwordCaneItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Mod 0.6.0 content: potion ingredients, formulas and the Apothecary Bench; Loen coinage;
 * firearms; and more of the Tingen block set. Registered on {@link Beyonder}'s registers.
 */
public final class BeyonderContent {
    private BeyonderContent() {}

    // ------------------------------------------------------------------ ingredients

    public static final DeferredItem<LoreItem> STARLIT_LAVENDER = ingredient("starlit_lavender", Rarity.COMMON);
    public static final DeferredItem<LoreItem> DREAM_MOSS = ingredient("dream_moss", Rarity.COMMON);
    public static final DeferredItem<LoreItem> WRAITH_ASH = ingredient("wraith_ash", Rarity.COMMON);
    public static final DeferredItem<LoreItem> SHADOW_SILK = ingredient("shadow_silk", Rarity.COMMON);
    public static final DeferredItem<LoreItem> PHANTOM_EYE = ingredient("phantom_eye", Rarity.UNCOMMON);
    public static final DeferredItem<LoreItem> MIRROR_SHARD = ingredient("mirror_shard", Rarity.UNCOMMON);
    public static final DeferredItem<LoreItem> CRIMSON_SPORE = ingredient("crimson_spore", Rarity.RARE);

    // ------------------------------------------------------------------ formulas

    public static final DeferredItem<FormulaItem> SEER_FORMULA = formula("seer_formula", Formula.SEER);
    public static final DeferredItem<FormulaItem> CLOWN_FORMULA = formula("clown_formula", Formula.CLOWN);
    public static final DeferredItem<FormulaItem> MAGICIAN_FORMULA = formula("magician_formula", Formula.MAGICIAN);

    // ------------------------------------------------------------------ coins

    public static final DeferredItem<CoinItem> COPPER_PENNY = Beyonder.ITEMS.registerItem("copper_penny",
            p -> new CoinItem(CoinItem.Denomination.PENNY, p), new Item.Properties().stacksTo(64));
    public static final DeferredItem<CoinItem> SILVER_SOLI = Beyonder.ITEMS.registerItem("silver_soli",
            p -> new CoinItem(CoinItem.Denomination.SOLI, p), new Item.Properties().stacksTo(64));
    public static final DeferredItem<CoinItem> GOLD_POUND = Beyonder.ITEMS.registerItem("gold_pound",
            p -> new CoinItem(CoinItem.Denomination.POUND, p), new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON));

    // ------------------------------------------------------------------ firearms

    public static final DeferredItem<RevolverItem> REVOLVER = Beyonder.ITEMS.registerItem("revolver",
            RevolverItem::new, new Item.Properties().stacksTo(1).durability(600));
    public static final DeferredItem<LoreItem> REVOLVER_ROUND = Beyonder.ITEMS.registerItem("revolver_round",
            LoreItem::new, new Item.Properties().stacksTo(64));
    public static final DeferredItem<LoreItem> SILVER_ROUND = Beyonder.ITEMS.registerItem("silver_round",
            LoreItem::new, new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON));

    public static final DeferredItem<DeathKnellItem> DEATH_KNELL = Beyonder.ITEMS.registerItem("death_knell",
            DeathKnellItem::new, new Item.Properties().stacksTo(1).durability(1200).rarity(Rarity.EPIC));

    // ------------------------------------------------------------------ blades and staves

    public static final DeferredItem<SwordItem> LONGSWORD = Beyonder.ITEMS.registerItem("longsword",
            p -> new SwordItem(Tiers.IRON, p.attributes(SwordItem.createAttributes(Tiers.IRON, 4, -2.6F))),
            new Item.Properties());
    public static final DeferredItem<SwordCaneItem> SWORD_CANE = Beyonder.ITEMS.registerItem("sword_cane",
            SwordCaneItem::new, new Item.Properties());
    public static final DeferredItem<CrimsonGreatswordItem> CRIMSON_GREATSWORD = Beyonder.ITEMS.registerItem("crimson_greatsword",
            CrimsonGreatswordItem::new, new Item.Properties().rarity(Rarity.RARE).fireResistant());
    public static final DeferredItem<StaffItem> CRESCENT_STAFF = Beyonder.ITEMS.registerItem("crescent_staff",
            StaffItem::new, new Item.Properties().stacksTo(1).durability(500).rarity(Rarity.UNCOMMON));

    // ------------------------------------------------------------------ blocks

    public static final DeferredBlock<ApothecaryBenchBlock> APOTHECARY_BENCH = Beyonder.BLOCKS.registerBlock(
            "apothecary_bench", ApothecaryBenchBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD)
                    .noOcclusion().lightLevel(state -> 6));

    public static final DeferredBlock<Block> SLATE_ROOF_TILES = Beyonder.BLOCKS.registerSimpleBlock("slate_roof_tiles",
            stone(MapColor.COLOR_GRAY, SoundType.DEEPSLATE_TILES));
    public static final DeferredBlock<StairBlock> SLATE_ROOF_STAIRS = Beyonder.BLOCKS.registerBlock("slate_roof_stairs",
            p -> new StairBlock(SLATE_ROOF_TILES.get().defaultBlockState(), p), stone(MapColor.COLOR_GRAY, SoundType.DEEPSLATE_TILES));
    public static final DeferredBlock<SlabBlock> SLATE_ROOF_SLAB = Beyonder.BLOCKS.registerBlock("slate_roof_slab",
            SlabBlock::new, stone(MapColor.COLOR_GRAY, SoundType.DEEPSLATE_TILES));
    public static final DeferredBlock<Block> WHITEWASHED_PLASTER = Beyonder.BLOCKS.registerSimpleBlock("whitewashed_plaster",
            stone(MapColor.TERRACOTTA_WHITE, SoundType.CALCITE));
    public static final DeferredBlock<Block> TIMBER_PLASTER = Beyonder.BLOCKS.registerSimpleBlock("timber_plaster",
            stone(MapColor.TERRACOTTA_WHITE, SoundType.CALCITE));
    public static final DeferredBlock<IronBarsBlock> IRON_RAILING = Beyonder.BLOCKS.registerBlock("iron_railing",
            IronBarsBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(5.0F, 6.0F)
                    .sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops());

    public static final DeferredItem<BlockItem> APOTHECARY_BENCH_ITEM = Beyonder.ITEMS.registerSimpleBlockItem("apothecary_bench", APOTHECARY_BENCH);
    public static final DeferredItem<BlockItem> SLATE_ROOF_TILES_ITEM = Beyonder.ITEMS.registerSimpleBlockItem("slate_roof_tiles", SLATE_ROOF_TILES);
    public static final DeferredItem<BlockItem> SLATE_ROOF_STAIRS_ITEM = Beyonder.ITEMS.registerSimpleBlockItem("slate_roof_stairs", SLATE_ROOF_STAIRS);
    public static final DeferredItem<BlockItem> SLATE_ROOF_SLAB_ITEM = Beyonder.ITEMS.registerSimpleBlockItem("slate_roof_slab", SLATE_ROOF_SLAB);
    public static final DeferredItem<BlockItem> WHITEWASHED_PLASTER_ITEM = Beyonder.ITEMS.registerSimpleBlockItem("whitewashed_plaster", WHITEWASHED_PLASTER);
    public static final DeferredItem<BlockItem> TIMBER_PLASTER_ITEM = Beyonder.ITEMS.registerSimpleBlockItem("timber_plaster", TIMBER_PLASTER);
    public static final DeferredItem<BlockItem> IRON_RAILING_ITEM = Beyonder.ITEMS.registerSimpleBlockItem("iron_railing", IRON_RAILING);

    /** Everything above, in creative-tab order. */
    // ---------------------------------------------------------------- clothing (tools/gen_clothing.py draws them)
    public static final DeferredItem<ClothingItem> TOP_HAT = clothing("top_hat", Equipment.Slot.HAT, ClothingItem.Hat.TOP_HAT, false);
    public static final DeferredItem<ClothingItem> BOWLER_HAT = clothing("bowler_hat", Equipment.Slot.HAT, ClothingItem.Hat.BOWLER, false);
    public static final DeferredItem<ClothingItem> FLAT_CAP = clothing("flat_cap", Equipment.Slot.HAT, ClothingItem.Hat.FLAT_CAP, false);
    public static final DeferredItem<ClothingItem> FROCK_COAT = clothing("frock_coat", Equipment.Slot.COAT, ClothingItem.Hat.NONE, true);
    public static final DeferredItem<ClothingItem> OVERCOAT = clothing("overcoat", Equipment.Slot.COAT, ClothingItem.Hat.NONE, true);
    public static final DeferredItem<ClothingItem> TWEED_JACKET = clothing("tweed_jacket", Equipment.Slot.COAT, ClothingItem.Hat.NONE, true);
    public static final DeferredItem<ClothingItem> BURGUNDY_WAISTCOAT = clothing("burgundy_waistcoat", Equipment.Slot.SHIRT, ClothingItem.Hat.NONE, false);
    public static final DeferredItem<ClothingItem> BROCADE_WAISTCOAT = clothing("brocade_waistcoat", Equipment.Slot.SHIRT, ClothingItem.Hat.NONE, false);
    public static final DeferredItem<ClothingItem> LEATHER_GLOVES = clothing("leather_gloves", Equipment.Slot.GLOVES, ClothingItem.Hat.NONE, true);
    public static final DeferredItem<ClothingItem> WHITE_GLOVES = clothing("white_gloves", Equipment.Slot.GLOVES, ClothingItem.Hat.NONE, true);
    public static final DeferredItem<ClothingItem> PINSTRIPE_TROUSERS = clothing("pinstripe_trousers", Equipment.Slot.TROUSERS, ClothingItem.Hat.NONE, false);
    public static final DeferredItem<ClothingItem> BLACK_TROUSERS = clothing("black_trousers", Equipment.Slot.TROUSERS, ClothingItem.Hat.NONE, false);
    public static final DeferredItem<ClothingItem> OXFORD_SHOES = clothing("oxford_shoes", Equipment.Slot.SHOES, ClothingItem.Hat.NONE, false);
    public static final DeferredItem<ClothingItem> RIDING_BOOTS = clothing("riding_boots", Equipment.Slot.SHOES, ClothingItem.Hat.NONE, false);

    public static final List<DeferredItem<ClothingItem>> CLOTHING = List.of(TOP_HAT, BOWLER_HAT, FLAT_CAP, FROCK_COAT,
            OVERCOAT, TWEED_JACKET, BURGUNDY_WAISTCOAT, BROCADE_WAISTCOAT, LEATHER_GLOVES, WHITE_GLOVES,
            PINSTRIPE_TROUSERS, BLACK_TROUSERS, OXFORD_SHOES, RIDING_BOOTS);

    private static DeferredItem<ClothingItem> clothing(String name, Equipment.Slot slot, ClothingItem.Hat hat, boolean sleeves) {
        return Beyonder.ITEMS.registerItem(name, p -> new ClothingItem(p, slot, hat, sleeves));
    }

    public static List<DeferredItem<? extends Item>> tabItems() {
        return List.of(SLATE_ROOF_TILES_ITEM, SLATE_ROOF_STAIRS_ITEM, SLATE_ROOF_SLAB_ITEM, WHITEWASHED_PLASTER_ITEM,
                TIMBER_PLASTER_ITEM, IRON_RAILING_ITEM, APOTHECARY_BENCH_ITEM,
                SEER_FORMULA, CLOWN_FORMULA, MAGICIAN_FORMULA,
                STARLIT_LAVENDER, DREAM_MOSS, WRAITH_ASH, SHADOW_SILK, PHANTOM_EYE, MIRROR_SHARD, CRIMSON_SPORE,
                COPPER_PENNY, SILVER_SOLI, GOLD_POUND,
                REVOLVER, DEATH_KNELL, REVOLVER_ROUND, SILVER_ROUND,
                LONGSWORD, SWORD_CANE, CRIMSON_GREATSWORD, CRESCENT_STAFF,
                TOP_HAT, BOWLER_HAT, FLAT_CAP, FROCK_COAT, OVERCOAT, TWEED_JACKET, BURGUNDY_WAISTCOAT, BROCADE_WAISTCOAT,
                LEATHER_GLOVES, WHITE_GLOVES, PINSTRIPE_TROUSERS, BLACK_TROUSERS, OXFORD_SHOES, RIDING_BOOTS);
    }

    /** Loads this class so its entries join the registers before they fire. */
    public static void init() {}

    private static DeferredItem<LoreItem> ingredient(String name, Rarity rarity) {
        return Beyonder.ITEMS.registerItem(name, LoreItem::new, new Item.Properties().stacksTo(64).rarity(rarity));
    }

    private static DeferredItem<FormulaItem> formula(String name, Formula formula) {
        return Beyonder.ITEMS.registerItem(name, p -> new FormulaItem(formula, p),
                new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    private static BlockBehaviour.Properties stone(MapColor color, SoundType sound) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(2.0F, 6.0F)
                .sound(sound)
                .requiresCorrectToolForDrops();
    }
}
