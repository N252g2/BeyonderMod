package io.github.n252g2.beyonder.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * The Seer's first ability. Opening Spirit Vision washes the world into a watercolor palette
 * and reveals the auras of living things around you.
 *
 * <p>For now it is open to everyone for testing; it will be gated behind the Seer pathway
 * and cost Spirituality once those systems exist.</p>
 */
public final class SpiritVision {
    public static final KeyMapping KEY = new KeyMapping(
            "key.beyonder.spirit_vision", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.beyonder");

    private static final float FADE_PER_TICK = 1.0F / 12.0F; // about 0.6 seconds
    private static final double AURA_RANGE = 24.0;

    private static final Vector3f AURA_PLAYER = new Vector3f(0.45F, 0.68F, 1.00F);
    private static final Vector3f AURA_HOSTILE = new Vector3f(0.95F, 0.22F, 0.28F);
    private static final Vector3f AURA_LIVING = new Vector3f(0.50F, 0.95F, 0.62F);

    private static boolean active;
    private static float amount;
    private static float previousAmount;

    private SpiritVision() {}

    public static boolean isActive() {
        return active;
    }

    /** How far the vision is open, 0..1, smoothed between ticks. */
    public static float amount(float partialTick) {
        return Mth.lerp(partialTick, previousAmount, amount);
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (KEY.consumeClick()) {
            if (mc.player != null) {
                toggle(mc);
            }
        }

        previousAmount = amount;
        float target = active ? 1.0F : 0.0F;
        if (amount < target) {
            amount = Math.min(target, amount + FADE_PER_TICK);
        } else if (amount > target) {
            amount = Math.max(target, amount - FADE_PER_TICK);
        }

        if (active && amount > 0.5F && mc.level != null && mc.player != null && !mc.isPaused()) {
            spawnAuras(mc);
        }
    }

    private static void toggle(Minecraft mc) {
        active = !active;
        Player player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        mc.level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.9F, active ? 0.55F : 0.4F, false);
        player.displayClientMessage(Component.translatable(active
                ? "message.beyonder.spirit_vision.open"
                : "message.beyonder.spirit_vision.close"), true);
    }

    private static void spawnAuras(Minecraft mc) {
        if (mc.level.getGameTime() % 2L != 0L) {
            return;
        }
        RandomSource random = mc.level.getRandom();
        for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(AURA_RANGE), e -> e != mc.player && e.isAlive())) {
            Vector3f color = entity instanceof Player ? AURA_PLAYER
                    : entity instanceof Enemy ? AURA_HOSTILE
                    : AURA_LIVING;
            float scale = 0.6F + Math.min(entity.getBbHeight(), 3.0F) * 0.25F;
            DustParticleOptions dust = new DustParticleOptions(color, scale);
            for (int i = 0; i < 2; i++) {
                double x = entity.getX() + (random.nextDouble() - 0.5) * entity.getBbWidth() * 1.5;
                double y = entity.getY() + random.nextDouble() * entity.getBbHeight() * 1.1;
                double z = entity.getZ() + (random.nextDouble() - 0.5) * entity.getBbWidth() * 1.5;
                mc.level.addParticle(dust, x, y, z, 0.0, 0.02, 0.0);
            }
        }
    }

    static void onComputeFov(ViewportEvent.ComputeFov event) {
        // A brief inward breath while the vision opens or closes.
        float t = amount((float) event.getPartialTick());
        float pulse = t * (1.0F - t) * 4.0F;
        if (pulse > 0.0F) {
            event.setFOV(event.getFOV() * (1.0 - 0.08 * pulse));
        }
    }

    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        amount = 0.0F;
        previousAmount = 0.0F;
    }
}
