package io.github.n252g2.beyonder.client;

import io.github.n252g2.beyonder.net.ClientInbox;
import io.github.n252g2.beyonder.net.PotionVisionPayload;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The vision that comes with a potion, as in the show: an icy chill, the world twisting and splitting,
 * a racing heart and whispers from nowhere, then a blinding flash and a quiet, clear awakening.
 *
 * <pre>
 *   0 - 25     the chill: cold blue wash, slow heartbeat
 *  25 - 100    the ordeal: swirl and colour split grow, camera sways, whispers, heart races
 * 100 - 110    the edge: everything at its peak
 *     110      the climax: white flash (or red darkness if it goes wrong); the outcome lands
 * 110 - 170    the awakening: calm returns; a brief glimpse of everyone's spirit
 * </pre>
 */
public final class PotionVision {
    private static final List<String> WHISPERS = List.of(
            "...can you hear it...", "...the fog... the gray fog...", "...who is speaking...",
            "...don't look back...", "...it is watching...", "...remember the price...", "...open your eyes...");
    private static final List<String> WHISPERS_FAIL = List.of(
            "...too soon...", "...it doesn't fit...", "...let go...", "...you are not you...", "...come apart...");

    private static int kind = -1;
    private static int tick = -1;
    private static float rollSeed;

    private PotionVision() {}

    public static boolean active() {
        return tick >= 0;
    }

    private static boolean dark() {
        return kind == PotionVisionPayload.FAILURE || kind == PotionVisionPayload.FATAL;
    }

    private static float time(float partial) {
        return tick < 0 ? -1.0F : tick + partial;
    }

    /** 0..1: how strongly the world is warped right now. */
    public static float intensity(float partial) {
        float t = time(partial);
        if (t < 0) {
            return 0.0F;
        }
        int climax = PotionVisionPayload.CLIMAX_TICKS;
        if (t < climax) {
            float ramp = Mth.clamp(t / climax, 0.0F, 1.0F);
            float surge = 0.12F * Mth.sin(t * 0.35F) * ramp; // waves of it
            return Mth.clamp(ramp * ramp * 0.95F + surge, 0.0F, 1.0F);
        }
        float after = (t - climax) / (PotionVisionPayload.LENGTH_TICKS - climax);
        return dark() ? Mth.clamp(1.0F - after * 0.6F, 0.0F, 1.0F) : Mth.clamp(1.0F - after * 2.5F, 0.0F, 1.0F);
    }

    /** 0..1: the white (or red) flash at the climax. */
    public static float flash(float partial) {
        float t = time(partial) - PotionVisionPayload.CLIMAX_TICKS;
        if (t < -6 || t > 40) {
            return 0.0F;
        }
        return t < 0 ? (t + 6) / 6.0F * 0.6F : Mth.clamp(1.0F - t / 40.0F, 0.0F, 1.0F);
    }

    /** 0..1: the cold blue of the first moments. */
    public static float chill(float partial) {
        float t = time(partial);
        if (t < 0 || t > PotionVisionPayload.CLIMAX_TICKS) {
            return 0.0F;
        }
        return Mth.clamp(t / 20.0F, 0.0F, 1.0F) * (1.0F - Mth.clamp((t - 60.0F) / 50.0F, 0.0F, 1.0F));
    }

    /** 1 for a vision gone wrong (red), 0 otherwise. */
    public static float darkness() {
        return active() && dark() ? 1.0F : 0.0F;
    }

    /** After a good vision, a glimpse of the spirit world (used by Spirit Vision's halos). */
    public static float glimpse(float partial) {
        if (dark()) {
            return 0.0F;
        }
        float t = time(partial) - PotionVisionPayload.CLIMAX_TICKS;
        if (t < 0) {
            return 0.0F;
        }
        float end = PotionVisionPayload.LENGTH_TICKS - PotionVisionPayload.CLIMAX_TICKS;
        return t < 10 ? t / 10.0F : Mth.clamp(1.0F - (t - 10) / (end - 10), 0.0F, 1.0F);
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        PotionVisionPayload incoming;
        while ((incoming = ClientInbox.pollVision()) != null) {
            kind = incoming.kind();
            tick = 0;
            rollSeed = mc.level != null ? mc.level.getRandom().nextFloat() * 10.0F : 0.0F;
        }
        if (tick < 0 || mc.isPaused()) {
            return;
        }
        Player player = mc.player;
        if (player == null || mc.level == null) {
            tick = -1;
            return;
        }
        sounds(mc, player);
        if (tick == PotionVisionPayload.CLIMAX_TICKS) {
            climaxParticles(mc, player);
        }
        if (++tick > PotionVisionPayload.LENGTH_TICKS) {
            tick = -1;
            kind = -1;
        }
    }

    private static void sounds(Minecraft mc, Player player) {
        int climax = PotionVisionPayload.CLIMAX_TICKS;
        // A heartbeat that speeds up toward the climax.
        if (tick < climax) {
            int interval = Math.max(7, 28 - tick / 5);
            if (tick % interval == 0) {
                play(mc, player, SoundEvents.WARDEN_HEARTBEAT, 0.9F, dark() ? 0.6F : 0.8F);
            }
        }
        // Whispers from nowhere.
        if (tick > 25 && tick < climax && tick % 23 == 0) {
            play(mc, player, SoundEvents.AMBIENT_CAVE.value(), 0.7F, 0.6F + mc.level.getRandom().nextFloat() * 0.4F);
        }
        if (tick == 20) {
            play(mc, player, SoundEvents.GLASS_BREAK, 0.5F, 0.5F); // the cold cracks through you
        }
        if (tick == climax) {
            if (dark()) {
                play(mc, player, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.0F, 0.6F);
            } else {
                play(mc, player, SoundEvents.ELDER_GUARDIAN_CURSE, 0.7F, 1.4F);
                play(mc, player, SoundEvents.BEACON_ACTIVATE, 1.0F, 0.8F);
            }
        }
    }

    private static void play(Minecraft mc, Player player, SoundEvent sound, float volume, float pitch) {
        mc.level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
    }

    private static void climaxParticles(Minecraft mc, Player player) {
        RandomSource r = mc.level.getRandom();
        for (int i = 0; i < 60; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double speed = 0.15 + r.nextDouble() * 0.25;
            mc.level.addParticle(dark() ? ParticleTypes.SQUID_INK : ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1.0, player.getZ(),
                    Math.cos(a) * speed, (r.nextDouble() - 0.3) * 0.2, Math.sin(a) * speed);
        }
    }

    /** The camera sways and shudders with the vision. */
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float k = intensity((float) event.getPartialTick());
        if (k <= 0.0F) {
            return;
        }
        float t = time((float) event.getPartialTick()) + rollSeed;
        event.setRoll(event.getRoll() + Mth.sin(t * 0.07F) * 14.0F * k);
        float shake = k * k * 1.2F;
        event.setYaw(event.getYaw() + Mth.sin(t * 1.7F) * shake);
        event.setPitch(event.getPitch() + Mth.cos(t * 2.3F) * shake);
    }

    /** Breathing field of view: the world stretches and draws in. */
    static void onComputeFov(ViewportEvent.ComputeFov event) {
        float k = intensity((float) event.getPartialTick());
        if (k <= 0.0F) {
            return;
        }
        float t = time((float) event.getPartialTick());
        event.setFOV(event.getFOV() * (1.0 + 0.12 * k * Mth.sin(t * 0.15F)));
    }

    /** Whispered fragments drift across the screen during the ordeal. */
    public static void renderOverlay(GuiGraphics g, DeltaTracker delta) {
        if (!active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float t = time(delta.getGameTimeDeltaPartialTick(false));
        List<String> lines = dark() ? WHISPERS_FAIL : WHISPERS;
        int w = g.guiWidth();
        int h = g.guiHeight();
        for (int i = 0; i < lines.size(); i++) {
            float start = 28 + i * 11;
            float life = t - start;
            if (life < 0 || life > 30 || t > PotionVisionPayload.CLIMAX_TICKS) {
                continue;
            }
            float alpha = Mth.sin(life / 30.0F * Mth.PI);
            int a = Mth.clamp((int) (alpha * 200), 4, 255);
            // Scatter the lines around the screen, drifting slowly.
            float px = 0.2F + ((i * 0.37F + rollSeed * 0.1F) % 0.6F);
            float py = 0.25F + ((i * 0.53F) % 0.5F);
            int x = (int) (w * px + life * 0.6F);
            int y = (int) (h * py - life * 0.3F);
            int colour = dark() ? 0xD04040 : 0xD8E4F0;
            g.drawCenteredString(font, lines.get(i), x, y, (a << 24) | colour);
        }
        // After a good vision: one clear line.
        if (!dark() && t > PotionVisionPayload.CLIMAX_TICKS + 8 && t < PotionVisionPayload.LENGTH_TICKS) {
            float life = t - PotionVisionPayload.CLIMAX_TICKS - 8;
            int a = Mth.clamp((int) (Mth.sin(life / 52.0F * Mth.PI) * 230), 4, 255);
            g.drawCenteredString(font, kind == PotionVisionPayload.AWAKEN ? "You see." : "You see further.",
                    w / 2, h / 2 + 30, (a << 24) | 0xF2E6C8);
        }
    }
}
