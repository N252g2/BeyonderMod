package io.github.n252g2.beyonder.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Fog and night tint for the Overworld: a hazy industrial day and a close, cold night
 * under the red moon. The moon texture itself is replaced in assets/minecraft.
 */
public final class TingenAtmosphere {
    private TingenAtmosphere() {}

    /** 0 during the day, 1 in the middle of the night, smooth through dusk and dawn. */
    public static float nightFactor(ClientLevel level, float partialTick) {
        float angle = level.getTimeOfDay(partialTick);
        float sunHeight = Mth.cos(angle * Mth.TWO_PI);
        return Mth.clamp((0.25F - sunHeight) / 0.55F, 0.0F, 1.0F);
    }

    private static boolean appliesTo(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != Level.OVERWORLD) {
            return false;
        }
        if (camera.getFluidInCamera() != FogType.NONE) {
            return false;
        }
        Entity entity = camera.getEntity();
        if (entity instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
            return false; // never weaken effect fog
        }
        return true;
    }

    static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || event.getType() != FogType.NONE) {
            return;
        }
        if (!ClientConfig.bool(ClientConfig.TINGEN_FOG, true) || !appliesTo(event.getCamera())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float strength = ClientConfig.number(ClientConfig.FOG_STRENGTH, 1.0F);
        if (strength <= 0.0F || mc.level == null) {
            return;
        }
        float night = nightFactor(mc.level, (float) event.getPartialTick());
        float rain = mc.level.getRainLevel((float) event.getPartialTick());

        float far = event.getFarPlaneDistance();
        float near = event.getNearPlaneDistance();

        // Day: a soft industrial haze. Night: the streets close in.
        float targetFar = Mth.lerp(night, 150.0F, 54.0F);
        targetFar = Mth.lerp(rain * 0.5F, targetFar, targetFar * 0.7F);
        targetFar = Math.min(far, targetFar);
        float targetNear = targetFar * Mth.lerp(night, 0.30F, 0.02F);

        event.setFarPlaneDistance(Mth.lerp(strength, far, targetFar));
        event.setNearPlaneDistance(Mth.lerp(strength, near, Math.min(near, targetNear)));
        event.setCanceled(true); // required for the new distances to take effect
    }

    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!ClientConfig.bool(ClientConfig.NIGHT_TINT, true) || !appliesTo(event.getCamera())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float night = nightFactor(mc.level, (float) event.getPartialTick());
        float r = event.getRed();
        float g = event.getGreen();
        float b = event.getBlue();

        // Day: pull the sky toward smoky grey-blue.
        float luma = r * 0.2126F + g * 0.7152F + b * 0.0722F;
        float day = (1.0F - night) * 0.35F;
        r = Mth.lerp(day, r, luma * 0.92F);
        g = Mth.lerp(day, g, luma * 0.97F);
        b = Mth.lerp(day, b, luma * 1.08F);

        // Night: cold murk with a faint crimson cast from the moon.
        r = Mth.lerp(night * 0.85F, r, 0.120F);
        g = Mth.lerp(night * 0.85F, g, 0.085F);
        b = Mth.lerp(night * 0.85F, b, 0.115F);

        event.setRed(r);
        event.setGreen(g);
        event.setBlue(b);
    }
}
