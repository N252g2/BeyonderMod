package io.github.n252g2.beyonder.client;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import io.github.n252g2.beyonder.Beyonder;
import java.io.IOException;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Runs the Tingen post-processing chain after the world is drawn (before the hand and HUD):
 * the muted Victorian grade, and the watercolor palette while Spirit Vision is open.
 */
public final class GradingRenderer {
    private static final ResourceLocation CHAIN_ID = Beyonder.id("shaders/post/tingen.json");

    private static PostChain chain;
    private static boolean failed;
    private static int width = -1;
    private static int height = -1;

    private static Method irisInstance;
    private static Method irisShaderPackInUse;
    private static boolean irisChecked;

    private GradingRenderer() {}

    /**
     * True while an Iris shader pack is running. Beyonder Shaders applies the same grade itself,
     * so the mod only adds Spirit Vision on top. Uses reflection so Iris stays optional.
     */
    static boolean irisShadersActive() {
        if (!irisChecked) {
            irisChecked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisInstance = api.getMethod("getInstance");
                irisShaderPackInUse = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError notInstalled) {
                irisInstance = null;
            }
        }
        if (irisInstance == null) {
            return false;
        }
        try {
            return (Boolean) irisShaderPackInUse.invoke(irisInstance.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    static void reset() {
        if (chain != null) {
            chain.close();
        }
        chain = null;
        failed = false;
        width = -1;
        height = -1;
    }

    private static PostChain chain(Minecraft mc) {
        if (chain == null && !failed) {
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), CHAIN_ID);
            } catch (IOException | JsonSyntaxException e) {
                Beyonder.LOGGER.error("Could not load the Tingen color grade; it will stay off until resources reload", e);
                failed = true;
            }
        }
        return chain;
    }

    static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float spirit = SpiritVision.amount(partial);
        boolean overworld = mc.level.dimension() == Level.OVERWORLD;
        float strength = ClientConfig.bool(ClientConfig.COLOR_GRADING, true) && overworld && !irisShadersActive()
                ? ClientConfig.number(ClientConfig.GRADING_STRENGTH, 1.0F)
                : 0.0F;
        if (strength <= 0.0F && spirit <= 0.001F) {
            return;
        }

        PostChain postChain = chain(mc);
        if (postChain == null) {
            return;
        }
        RenderTarget main = mc.getMainRenderTarget();
        if (main.width != width || main.height != height) {
            width = main.width;
            height = main.height;
            postChain.resize(width, height);
        }

        float night = overworld ? TingenAtmosphere.nightFactor(mc.level, partial) : 0.5F;
        float clock = (float) ((mc.level.getGameTime() % 24000L) + partial) / 20.0F;

        postChain.setUniform("Strength", strength);
        postChain.setUniform("Night", night);
        postChain.setUniform("Spirit", spirit);
        postChain.setUniform("Clock", clock);
        postChain.process(event.getPartialTick().getGameTimeDeltaTicks());
        main.bindWrite(false);
    }
}
