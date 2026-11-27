package io.github.n252g2.beyonder;

import com.mojang.logging.LogUtils;
import io.github.n252g2.beyonder.block.ChimneyBlock;
import io.github.n252g2.beyonder.block.GasLampBlock;
import io.github.n252g2.beyonder.block.LampPostBlock;
import io.github.n252g2.beyonder.content.BeyonderContent;
import io.github.n252g2.beyonder.content.CityBlocks;
import io.github.n252g2.beyonder.core.Abilities;
import io.github.n252g2.beyonder.core.BeyonderAttachments;
import io.github.n252g2.beyonder.core.Pathway;
import io.github.n252g2.beyonder.item.BeyonderPotionItem;
import io.github.n252g2.beyonder.item.PendulumItem;
import io.github.n252g2.beyonder.net.BeyonderNetwork;
import io.github.n252g2.beyonder.server.BeyonderLoot;
import io.github.n252g2.beyonder.server.BeyonderServerEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * Common entry point. Everything that exists on both the client and the server is registered here.
 * Client-only visuals live in {@link io.github.n252g2.beyonder.client.BeyonderClient}.
 */
@Mod(Beyonder.MODID)
public class Beyonder {
    public static final String MODID = "beyonder";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Tingen street furniture
    public static final DeferredBlock<GasLampBlock> GAS_LAMP = BLOCKS.registerBlock("gas_lamp", GasLampBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.LANTERN)
                    .lightLevel(state -> 15)
                    .noOcclusion()
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<LampPostBlock> LAMP_POST = BLOCKS.registerBlock("lamp_post", LampPostBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .requiresCorrectToolForDrops());

    // Tingen building blocks
    public static final DeferredBlock<Block> TINGEN_COBBLES = BLOCKS.registerSimpleBlock("tingen_cobbles",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> SOOT_BRICKS = BLOCKS.registerSimpleBlock("soot_bricks",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_BROWN)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops());

    // Stairs, slabs and walls for both street stones
    public static final DeferredBlock<StairBlock> TINGEN_COBBLE_STAIRS = BLOCKS.registerBlock("tingen_cobble_stairs",
            p -> new StairBlock(TINGEN_COBBLES.get().defaultBlockState(), p), stone(MapColor.STONE));
    public static final DeferredBlock<SlabBlock> TINGEN_COBBLE_SLAB = BLOCKS.registerBlock("tingen_cobble_slab",
            SlabBlock::new, stone(MapColor.STONE));
    public static final DeferredBlock<WallBlock> TINGEN_COBBLE_WALL = BLOCKS.registerBlock("tingen_cobble_wall",
            WallBlock::new, stone(MapColor.STONE).forceSolidOn());
    public static final DeferredBlock<StairBlock> SOOT_BRICK_STAIRS = BLOCKS.registerBlock("soot_brick_stairs",
            p -> new StairBlock(SOOT_BRICKS.get().defaultBlockState(), p), stone(MapColor.TERRACOTTA_BROWN));
    public static final DeferredBlock<SlabBlock> SOOT_BRICK_SLAB = BLOCKS.registerBlock("soot_brick_slab",
            SlabBlock::new, stone(MapColor.TERRACOTTA_BROWN));
    public static final DeferredBlock<WallBlock> SOOT_BRICK_WALL = BLOCKS.registerBlock("soot_brick_wall",
            WallBlock::new, stone(MapColor.TERRACOTTA_BROWN).forceSolidOn());

    // A window with the lamps lit behind it, and a smoking chimney
    public static final DeferredBlock<Block> LIT_WINDOW = BLOCKS.registerSimpleBlock("lit_window",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.6F).sound(SoundType.GLASS)
                    .lightLevel(state -> 11));
    public static final DeferredBlock<ChimneyBlock> CHIMNEY = BLOCKS.registerBlock("chimney", ChimneyBlock::new,
            stone(MapColor.TERRACOTTA_BROWN));

    public static final DeferredItem<BlockItem> GAS_LAMP_ITEM = ITEMS.registerSimpleBlockItem("gas_lamp", GAS_LAMP);
    public static final DeferredItem<BlockItem> LAMP_POST_ITEM = ITEMS.registerSimpleBlockItem("lamp_post", LAMP_POST);
    public static final DeferredItem<BlockItem> TINGEN_COBBLES_ITEM = ITEMS.registerSimpleBlockItem("tingen_cobbles", TINGEN_COBBLES);
    public static final DeferredItem<BlockItem> SOOT_BRICKS_ITEM = ITEMS.registerSimpleBlockItem("soot_bricks", SOOT_BRICKS);
    public static final DeferredItem<BlockItem> TINGEN_COBBLE_STAIRS_ITEM = ITEMS.registerSimpleBlockItem("tingen_cobble_stairs", TINGEN_COBBLE_STAIRS);
    public static final DeferredItem<BlockItem> TINGEN_COBBLE_SLAB_ITEM = ITEMS.registerSimpleBlockItem("tingen_cobble_slab", TINGEN_COBBLE_SLAB);
    public static final DeferredItem<BlockItem> TINGEN_COBBLE_WALL_ITEM = ITEMS.registerSimpleBlockItem("tingen_cobble_wall", TINGEN_COBBLE_WALL);
    public static final DeferredItem<BlockItem> SOOT_BRICK_STAIRS_ITEM = ITEMS.registerSimpleBlockItem("soot_brick_stairs", SOOT_BRICK_STAIRS);
    public static final DeferredItem<BlockItem> SOOT_BRICK_SLAB_ITEM = ITEMS.registerSimpleBlockItem("soot_brick_slab", SOOT_BRICK_SLAB);
    public static final DeferredItem<BlockItem> SOOT_BRICK_WALL_ITEM = ITEMS.registerSimpleBlockItem("soot_brick_wall", SOOT_BRICK_WALL);
    public static final DeferredItem<BlockItem> LIT_WINDOW_ITEM = ITEMS.registerSimpleBlockItem("lit_window", LIT_WINDOW);
    public static final DeferredItem<BlockItem> CHIMNEY_ITEM = ITEMS.registerSimpleBlockItem("chimney", CHIMNEY);

    // Seer pathway
    public static final DeferredItem<BeyonderPotionItem> SEER_POTION = ITEMS.registerItem("seer_potion",
            p -> new BeyonderPotionItem(Pathway.SEER, 9, p), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BeyonderPotionItem> CLOWN_POTION = ITEMS.registerItem("clown_potion",
            p -> new BeyonderPotionItem(Pathway.SEER, 8, p), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BeyonderPotionItem> MAGICIAN_POTION = ITEMS.registerItem("magician_potion",
            p -> new BeyonderPotionItem(Pathway.SEER, 7, p), new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<PendulumItem> PENDULUM = ITEMS.registerItem("spirit_pendulum",
            PendulumItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("beyonder", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.beyonder"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> GAS_LAMP_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(GAS_LAMP_ITEM.get());
                output.accept(LAMP_POST_ITEM.get());
                output.accept(TINGEN_COBBLES_ITEM.get());
                output.accept(SOOT_BRICKS_ITEM.get());
                output.accept(TINGEN_COBBLE_STAIRS_ITEM.get());
                output.accept(TINGEN_COBBLE_SLAB_ITEM.get());
                output.accept(TINGEN_COBBLE_WALL_ITEM.get());
                output.accept(SOOT_BRICK_STAIRS_ITEM.get());
                output.accept(SOOT_BRICK_SLAB_ITEM.get());
                output.accept(SOOT_BRICK_WALL_ITEM.get());
                output.accept(LIT_WINDOW_ITEM.get());
                output.accept(CHIMNEY_ITEM.get());
                CityBlocks.tabItems().forEach(item -> output.accept(item.get()));
                output.accept(SEER_POTION.get());
                output.accept(CLOWN_POTION.get());
                output.accept(MAGICIAN_POTION.get());
                output.accept(PENDULUM.get());
                BeyonderContent.tabItems().forEach(item -> output.accept(item.get()));
            })
            .build());

    private static BlockBehaviour.Properties stone(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(2.0F, 6.0F)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops();
    }

    public Beyonder(IEventBus modBus, ModContainer container) {
        BeyonderContent.init();
        CityBlocks.init();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
        BeyonderAttachments.register(modBus);
        modBus.addListener(BeyonderNetwork::register);
        BeyonderServerEvents.register();
        BeyonderLoot.register();
        Abilities.registerEvents();
        io.github.n252g2.beyonder.core.EquipmentRules.registerEvents();
        LOGGER.info("Beyonder loaded. The fog settles over Tingen.");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
