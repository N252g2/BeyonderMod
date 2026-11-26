package io.github.n252g2.beyonder.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Small things that make Tingen feel inhabited: soot and ash drifting through the night air, dust by day,
 * crimson spores under a full moon, and church bells at dawn, dusk and midnight.
 */
public final class TingenAmbience {
    private static long lastTime = -1;
    private static int tollsLeft;
    private static int tollTimer;
    private static boolean crimsonAnnounced;

    private TingenAmbience() {}

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Player player = mc.player;
        if (level == null || player == null || mc.isPaused() || level.dimension() != Level.OVERWORLD) {
            return;
        }
        float night = TingenAtmosphere.nightFactor(level, 1.0F);
        boolean crimson = TingenAtmosphere.isCrimsonNight(level);
        boolean outdoors = level.canSeeSky(BlockPos.containing(player.getEyePosition()));

        if (outdoors && ClientConfig.bool(ClientConfig.AMBIENT_PARTICLES, true)) {
            drift(level, player, night, crimson);
        }
        bells(mc, level, player);

        if (crimson && night > 0.6F && !crimsonAnnounced) {
            crimsonAnnounced = true;
            player.displayClientMessage(Component.literal("The moon turns crimson. Something stirs in Tingen tonight.")
                    .withStyle(ChatFormatting.DARK_RED), true);
        } else if (night < 0.2F) {
            crimsonAnnounced = false;
        }
    }

    private static void drift(ClientLevel level, Player player, float night, boolean crimson) {
        RandomSource r = level.getRandom();
        int count = crimson ? 4 : night > 0.5F ? 3 : 1;
        for (int i = 0; i < count; i++) {
            if (night <= 0.5F && r.nextFloat() > 0.45F) {
                continue; // daytime dust is sparse
            }
            double x = player.getX() + (r.nextDouble() - 0.5) * 28.0;
            double y = player.getY() + r.nextDouble() * 10.0 - 2.0;
            double z = player.getZ() + (r.nextDouble() - 0.5) * 28.0;
            ParticleOptions particle;
            if (crimson && r.nextFloat() < 0.6F) {
                particle = ParticleTypes.CRIMSON_SPORE;
            } else if (night > 0.5F) {
                particle = r.nextFloat() < 0.35F ? ParticleTypes.ASH : ParticleTypes.WHITE_ASH; // soot from a thousand chimneys
            } else {
                particle = ParticleTypes.WHITE_ASH;
            }
            level.addParticle(particle, x, y, z, 0.0, 0.0, 0.0);
        }
    }

    /** The cathedral tolls three times at dawn and dusk, four at midnight. */
    private static void bells(Minecraft mc, ClientLevel level, Player player) {
        long time = level.getDayTime() % 24000L;
        if (lastTime >= 0 && time != lastTime && ClientConfig.bool(ClientConfig.CHURCH_BELLS, true)) {
            if (crossed(lastTime, time, 0L) || crossed(lastTime, time, 12000L)) {
                tollsLeft = 3;
            } else if (crossed(lastTime, time, 18000L)) {
                tollsLeft = 4;
            }
        }
        lastTime = time;
        if (tollsLeft > 0 && --tollTimer <= 0) {
            tollsLeft--;
            tollTimer = 36;
            // Far away to the north, over the rooftops.
            level.playLocalSound(player.getX(), player.getY() + 12.0, player.getZ() - 24.0,
                    SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 2.5F, 0.5F, false);
        }
    }

    private static boolean crossed(long from, long to, long mark) {
        return from < to ? from < mark && mark <= to : from < mark || mark <= to;
    }
}
