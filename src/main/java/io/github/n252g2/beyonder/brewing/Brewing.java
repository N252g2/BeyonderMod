package io.github.n252g2.beyonder.brewing;

import io.github.n252g2.beyonder.item.BeyonderPotionItem;
import io.github.n252g2.beyonder.net.BrewResultPayload;
import io.github.n252g2.beyonder.net.BrewStartPayload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Brews in progress. The ingredients go into the flask when a brew starts; the player then plays
 * the brewing minigame on their screen and reports how it went. The server checks that the brew
 * is the one it started, that it took a believable amount of time, and that the player is still
 * at the bench, then hands over the potion (or the smoke of a cracked flask).
 */
public final class Brewing {
    private record Active(Formula formula, ServerLevel level, BlockPos pos, long startTick, int nonce) {}

    private static final Map<UUID, Active> ACTIVE = new ConcurrentHashMap<>();
    private static final long TIMEOUT_TICKS = 20L * 60 * 5;

    private Brewing() {}

    public static boolean isBrewing(Player player) {
        Active a = ACTIVE.get(player.getUUID());
        return a != null && player.level().getGameTime() - a.startTick() < TIMEOUT_TICKS;
    }

    public static void start(ServerPlayer player, ServerLevel level, BlockPos pos, Formula formula) {
        int nonce = player.getRandom().nextInt();
        ACTIVE.put(player.getUUID(), new Active(formula, level, pos.immutable(), level.getGameTime(), nonce));
        level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8F, 1.0F);
        PacketDistributor.sendToPlayer(player, new BrewStartPayload(formula.ordinal(), nonce));
    }

    public static void finish(ServerPlayer player, int nonce, int quality) {
        Active a = ACTIVE.remove(player.getUUID());
        if (a == null || a.nonce() != nonce) {
            return;
        }
        ServerLevel level = a.level();
        BlockPos pos = a.pos();
        long took = level.getGameTime() - a.startTick();
        boolean honest = took >= a.formula().minBrewTicks() && took < TIMEOUT_TICKS
                && player.level() == level && player.distanceToSqr(pos.getCenter()) < 64.0;

        if (quality == BrewResultPayload.RUINED || !honest) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5, 20, 0.2, 0.3, 0.2, 0.02);
            level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 0.9F);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 0.8F);
            player.displayClientMessage(Component.literal("The flask cracks. The ingredients are lost.")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        Formula formula = a.formula();
        int q = Math.max(0, Math.min(3, quality));
        ColorParticleOption smoke = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | formula.smokeColor());
        level.sendParticles(smoke, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 30 + q * 15, 0.25, 0.4, 0.25, 1.0);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.35, pos.getY() + 1.4, pos.getZ() + 0.45, 3, 0.05, 0.1, 0.05, 0.01);
        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 0.9F);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.6F + q * 0.15F);
        if (q == 3) {
            level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
        }

        ItemStack potion = BeyonderPotionItem.withQuality(new ItemStack(formula.result()), q);
        if (!player.getInventory().add(potion)) {
            player.drop(potion, false);
        }
        player.displayClientMessage(Component.literal(BeyonderPotionItem.qualityName(q) + " ")
                .append(potion.getHoverName()).append(". The potion settles.").withStyle(ChatFormatting.GOLD), true);
    }

    public static void forget(Player player) {
        ACTIVE.remove(player.getUUID());
    }
}
