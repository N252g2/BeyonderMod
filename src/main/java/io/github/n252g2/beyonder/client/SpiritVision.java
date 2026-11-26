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
import net.minecraft.world.entity.player.Player;
import io.github.n252g2.beyonder.net.BeyonderSyncPayload;
import io.github.n252g2.beyonder.net.ClientInbox;
import io.github.n252g2.beyonder.net.SpiritVisionPayload;
import net.minecraft.ChatFormatting;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * The Seer's first ability. Opening Spirit Vision washes the world into a watercolor palette
 * and reveals the auras of living things around you (drawn by {@link SpiritRenderer}),
 * along with waypoint markers ({@link SpiritMarkers}).
 *
 * <p>Only a Seer can open it (or anyone in creative), and it drains Spirituality while open. The server
 * keeps the books and closes it when Spirituality runs out.</p>
 */
public final class SpiritVision {
    public static final KeyMapping KEY = new KeyMapping(
            "key.beyonder.spirit_vision", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.beyonder");

    private static final float FADE_PER_TICK = 1.0F / 12.0F; // about 0.6 seconds
    private static final double AURA_RANGE = 24.0;

    private static boolean active;
    private static int toggleGrace; // ticks to ignore stale server state after a toggle
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

        if (toggleGrace > 0) {
            toggleGrace--;
        }
        BeyonderSyncPayload self = mc.player != null ? ClientInbox.get(mc.player.getId()) : null;
        if (active && toggleGrace == 0 && self != null && !self.spiritVision()
                && mc.player != null && !mc.player.isCreative() && !mc.player.isSpectator()) {
            active = false; // the server closed it (Spirituality ran dry)
        }

        previousAmount = amount;
        float target = active ? 1.0F : 0.0F;
        if (amount < target) {
            amount = Math.min(target, amount + FADE_PER_TICK);
        } else if (amount > target) {
            amount = Math.max(target, amount - FADE_PER_TICK);
        }

        SpiritMarkers.drainInbox();

        if (active && amount > 0.5F && mc.level != null && mc.player != null && !mc.isPaused()) {
            spawnAuras(mc);
        }
    }

    private static boolean canOpen(Player player) {
        if (player.isCreative() || player.isSpectator()) {
            return true;
        }
        BeyonderSyncPayload self = ClientInbox.get(player.getId());
        return self != null && "seer".equals(self.pathway()) && self.spirituality() >= 2.0F;
    }

    static void toggle(Minecraft mc) {
        Player player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        if (!active && !canOpen(player)) {
            BeyonderSyncPayload self = ClientInbox.get(player.getId());
            boolean seer = self != null && "seer".equals(self.pathway());
            player.displayClientMessage(Component.literal(seer
                    ? "Your Spirituality is too low to open your Spirit Vision."
                    : "You have no Spirit Vision. Only a Seer can open it.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        active = !active;
        toggleGrace = 20;
        PacketDistributor.sendToServer(new SpiritVisionPayload(active));
        mc.level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                0.9F, active ? 0.55F : 0.4F, false);
        player.displayClientMessage(Component.translatable(active
                ? "message.beyonder.spirit_vision.open"
                : "message.beyonder.spirit_vision.close"), true);
    }

    /** Strong spirits shed a few drifting wisps in their aura colour; the halo itself is drawn by SpiritRenderer. */
    private static void spawnAuras(Minecraft mc) {
        if (mc.level.getGameTime() % 3L != 0L) {
            return;
        }
        RandomSource random = mc.level.getRandom();
        for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class,
                mc.player.getBoundingBox().inflate(AURA_RANGE), e -> e != mc.player && e.isAlive())) {
            float strength = SpiritAura.strength(entity);
            if (strength < SpiritAura.NOTABLE) {
                continue;
            }
            float[] c = SpiritAura.color(entity, strength);
            DustParticleOptions dust = new DustParticleOptions(new Vector3f(c[0], c[1], c[2]), 0.8F + strength * 0.1F);
            int count = strength >= SpiritAura.DANGEROUS ? 2 : 1;
            for (int i = 0; i < count; i++) {
                double x = entity.getX() + (random.nextDouble() - 0.5) * entity.getBbWidth() * 1.6;
                double y = entity.getY() + random.nextDouble() * entity.getBbHeight() * 1.2;
                double z = entity.getZ() + (random.nextDouble() - 0.5) * entity.getBbWidth() * 1.6;
                mc.level.addParticle(dust, x, y, z, 0.0, 0.03, 0.0);
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
        ClientInbox.clear();
        active = false;
        amount = 0.0F;
        previousAmount = 0.0F;
    }
}
