package io.github.n252g2.beyonder.client;

import io.github.n252g2.beyonder.Beyonder;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
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

        NeoForge.EVENT_BUS.addListener(TingenAtmosphere::onRenderFog);
        NeoForge.EVENT_BUS.addListener(TingenAtmosphere::onFogColor);
        NeoForge.EVENT_BUS.addListener(GradingRenderer::onRenderStage);
        NeoForge.EVENT_BUS.addListener(SpiritVision::onClientTick);
        NeoForge.EVENT_BUS.addListener(SpiritVision::onComputeFov);
        NeoForge.EVENT_BUS.addListener(SpiritVision::onLoggingOut);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(SpiritVision.KEY);
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        // Shaders are resources: rebuild the post chain after F3+T or a resource pack change.
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> GradingRenderer.reset());
    }
}
