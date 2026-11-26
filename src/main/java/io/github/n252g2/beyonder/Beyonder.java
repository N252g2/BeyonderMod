package io.github.n252g2.beyonder;

import com.mojang.logging.LogUtils;
import io.github.n252g2.beyonder.block.GasLampBlock;
import io.github.n252g2.beyonder.block.LampPostBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
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

    public static final DeferredItem<BlockItem> GAS_LAMP_ITEM = ITEMS.registerSimpleBlockItem("gas_lamp", GAS_LAMP);
    public static final DeferredItem<BlockItem> LAMP_POST_ITEM = ITEMS.registerSimpleBlockItem("lamp_post", LAMP_POST);
    public static final DeferredItem<BlockItem> TINGEN_COBBLES_ITEM = ITEMS.registerSimpleBlockItem("tingen_cobbles", TINGEN_COBBLES);
    public static final DeferredItem<BlockItem> SOOT_BRICKS_ITEM = ITEMS.registerSimpleBlockItem("soot_bricks", SOOT_BRICKS);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("beyonder", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.beyonder"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> GAS_LAMP_ITEM.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(GAS_LAMP_ITEM.get());
                output.accept(LAMP_POST_ITEM.get());
                output.accept(TINGEN_COBBLES_ITEM.get());
                output.accept(SOOT_BRICKS_ITEM.get());
            })
            .build());

    public Beyonder(IEventBus modBus, ModContainer container) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
        LOGGER.info("Beyonder loaded. The fog settles over Tingen.");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
