package io.github.n252g2.beyonder.client;

import io.github.n252g2.beyonder.Beyonder;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import io.github.n252g2.beyonder.content.BeyonderContent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** Client entry point: the Tingen atmosphere, the color grade and Spirit Vision. */
@Mod(value = Beyonder.MODID, dist = Dist.CLIENT)
public class BeyonderClient {
    public BeyonderClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(BeyonderClient::onRegisterKeys);
        modBus.addListener(BeyonderClient::onRegisterReloadListeners);
        modBus.addListener(BeyonderClient::onRegisterGuiLayers);
        modBus.addListener(BeyonderClient::onRegisterExtensions);
        modBus.addListener(BeyonderClient::onClientSetup);

        NeoForge.EVENT_BUS.addListener(TingenAtmosphere::onRenderFog);
        NeoForge.EVENT_BUS.addListener(TingenAtmosphere::onFogColor);
        NeoForge.EVENT_BUS.addListener(GradingRenderer::onRenderStage);
        NeoForge.EVENT_BUS.addListener(SpiritVision::onClientTick);
        NeoForge.EVENT_BUS.addListener(AbilityKeys::onClientTick);
        NeoForge.EVENT_BUS.addListener(PotionVision::onClientTick);
        NeoForge.EVENT_BUS.addListener(PotionVision::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(PotionVision::onComputeFov);
        NeoForge.EVENT_BUS.addListener(TingenAmbience::onClientTick);
        NeoForge.EVENT_BUS.addListener(SpiritVision::onComputeFov);
        NeoForge.EVENT_BUS.addListener(SpiritVision::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(SpiritRenderer::onRenderStage);
        NeoForge.EVENT_BUS.addListener(SpiritMarkers::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(SpiritMarkers::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(GunClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(BrewingScreen::onClientTick);
        NeoForge.EVENT_BUS.addListener(CharacterCreatorScreen::onClientTick);
        NeoForge.EVENT_BUS.addListener(NameTags::onRenderNameTag);
        NeoForge.EVENT_BUS.addListener(GunClient::onInteraction);
        NeoForge.EVENT_BUS.addListener(GunClient::onComputeFov);
        NeoForge.EVENT_BUS.addListener(GunClient::onRenderLayer);
        NeoForge.EVENT_BUS.addListener(EquipmentClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(BeyonderHotbar::onRenderLayer);
        NeoForge.EVENT_BUS.addListener(EquipmentClient::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(EquipmentClient::onInteraction);
        NeoForge.EVENT_BUS.addListener(EquipmentClient::onRenderHand);
        NeoForge.EVENT_BUS.addListener(PlayerAnimator::onClientTick);
        if (SelfTest.ENABLED) {
            NeoForge.EVENT_BUS.addListener(SelfTest::onClientTick);
        }
        modBus.addListener(BeyonderClient::onAddLayers);
        modBus.addListener(ClothingLayer::registerDefinitions);
        modBus.addListener(ClothingLayer::addLayers);
        NeoForge.EVENT_BUS.addListener(ClothingLayer::onRenderArm);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(SpiritVision.KEY);
        event.register(AbilityKeys.USE);
        event.register(AbilityKeys.NEXT);
        event.register(EquipmentClient.DRAW);
        event.register(EquipmentClient.SWAP);
        for (var key : EquipmentClient.BELT) {
            event.register(key);
        }
    }

    /** Sheathed weapons are drawn on both player models (wide and slim). */
    @SuppressWarnings("unchecked")
    private static void onAddLayers(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers event) {
        for (net.minecraft.client.resources.PlayerSkin.Model model : event.getSkins()) {
            if (event.getSkin(model) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer renderer) {
                renderer.addLayer(new SheathLayer(renderer));
            }
        }
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(Beyonder.id("beyonder_hud"), BeyonderHud::render);
        event.registerAboveAll(Beyonder.id("gun_hud"), GunClient::renderHud);
        event.registerAbove(net.neoforged.neoforge.client.gui.VanillaGuiLayers.HOTBAR, Beyonder.id("hotbar"), BeyonderHotbar::render);
    }

    private static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
        // The sword cane swaps to its drawn-blade model.
        event.enqueueWork(() -> net.minecraft.client.renderer.item.ItemProperties.register(BeyonderContent.SWORD_CANE.get(),
                Beyonder.id("drawn"), (stack, level, entity, seed) ->
                        io.github.n252g2.beyonder.item.SwordCaneItem.isDrawn(stack) ? 1.0F : 0.0F));
    }

    private static void onRegisterExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(GunClient.EXTENSIONS, BeyonderContent.REVOLVER.get(), BeyonderContent.DEATH_KNELL.get());
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        // Shaders are resources: rebuild the post chain after F3+T or a resource pack change.
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> GradingRenderer.reset());
        // Character skins are built from resource parts: rebuild them too.
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> CharacterSkins.reset());
    }
}
