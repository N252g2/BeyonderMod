package io.github.n252g2.beyonder.core;

import io.github.n252g2.beyonder.Beyonder;
import io.github.n252g2.beyonder.net.BeyonderSyncPayload;
import io.github.n252g2.beyonder.net.PotionVisionPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side rules for Beyonders: Spirituality, digestion (the acting method), advancing with
 * potions, and losing control.
 */
public final class Beyonders {
    public static final ResourceKey<DamageType> LOST_CONTROL =
            ResourceKey.create(Registries.DAMAGE_TYPE, Beyonder.id("lost_control"));

    private static final Set<UUID> SPIRIT_VISION = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, List<Pending>> PENDING = new ConcurrentHashMap<>();

    private record Pending(long at, Runnable action) {}

    private Beyonders() {}

    public static BeyonderData get(Player player) {
        return player.getData(BeyonderAttachments.BEYONDER);
    }

    public static boolean hasSpiritVision(Player player) {
        return SPIRIT_VISION.contains(player.getUUID());
    }

    private static boolean free(Player player) {
        return player.isCreative() || player.isSpectator();
    }

    // ---------------------------------------------------------------- sync

    public static BeyonderSyncPayload payload(Player player) {
        BeyonderData d = get(player);
        return new BeyonderSyncPayload(player.getId(), d.pathway().id(), d.sequence(), d.spirituality(),
                d.maxSpirituality(), d.digestion(), d.lossOfControl(), hasSpiritVision(player));
    }

    /** Sends the player's state to them and to everyone who can see them (for halos). */
    public static void sync(ServerPlayer player) {
        if (player.connection == null) {
            return; // not connected (yet): nothing to tell
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload(player));
    }

    // ---------------------------------------------------------------- spirituality

    /** Spends Spirituality; returns false (and says so) if there is not enough. Free in creative. */
    public static boolean trySpend(ServerPlayer player, float amount) {
        if (free(player)) {
            return true;
        }
        BeyonderData d = get(player);
        if (d.spirituality() < amount) {
            player.displayClientMessage(Component.literal("Your Spirituality is too low.").withStyle(ChatFormatting.DARK_PURPLE), true);
            return false;
        }
        d.setSpirituality(d.spirituality() - amount);
        sync(player);
        return true;
    }

    public static void setSpiritVision(ServerPlayer player, boolean active) {
        BeyonderData d = get(player);
        if (active && !free(player)) {
            if (d.pathway() != Pathway.SEER) {
                player.displayClientMessage(Component.literal("You have no Spirit Vision. Only a Seer can open it.")
                        .withStyle(ChatFormatting.GRAY), true);
                active = false;
            } else if (d.spirituality() < 2.0F) {
                player.displayClientMessage(Component.literal("Your Spirituality is too low to open your Spirit Vision.")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
                active = false;
            }
        }
        if (active) {
            SPIRIT_VISION.add(player.getUUID());
        } else {
            SPIRIT_VISION.remove(player.getUUID());
        }
        sync(player);
    }

    public static void forget(Player player) {
        SPIRIT_VISION.remove(player.getUUID());
        STALENESS.remove(player.getUUID());
    }

    // ---------------------------------------------------------------- digestion (acting)

    /** How fast each Sequence digests: a quarter speed at 9, slowing by a fifth for every Sequence after. */
    static float pace(int sequence) {
        return 0.25F * (float) Math.pow(0.8, Math.max(0, 9 - sequence));
    }

    /** Per player and kind of act: how stale it is, and when that was last updated. */
    private static final Map<UUID, Map<String, float[]>> STALENESS = new ConcurrentHashMap<>();
    private static final float STALE_DECAY_TICKS = 2400.0F; // one use forgotten every 2 minutes

    /**
     * Acting digests the potion, but only when you play the role of your current Sequence:
     * a Seer divines and observes, a Clown performs in battle, a Magician deceives with tricks.
     * Digestion is slow on purpose, and the same act repeated soon after counts for less (a role is
     * lived, not ground out); {@code kind} names the act, or null for steady acts like Spirit Vision.
     */
    public static void act(ServerPlayer player, int forSequence, float amount, String kind) {
        BeyonderData d = get(player);
        if (d.pathway() != Pathway.SEER || d.sequence() != forSequence || d.digestion() >= 100.0F) {
            return;
        }
        float gain = amount * pace(forSequence);
        if (kind != null) {
            long now = player.level().getGameTime();
            float[] st = STALENESS.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(kind, k -> new float[] {0.0F, now});
            st[0] = Math.max(0.0F, st[0] - (now - st[1]) / STALE_DECAY_TICKS);
            st[1] = now;
            gain /= 1.0F + 0.6F * st[0];
            st[0] += 1.0F;
        }
        d.setDigestion(d.digestion() + gain);
        if (d.digestion() >= 100.0F) {
            player.displayClientMessage(Component.literal("The potion is fully digested. You are ready to advance.")
                    .withStyle(ChatFormatting.GOLD), false);
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
        sync(player);
    }

    /** How to digest each Sequence, shown when you reach it. */
    public static String actingHint(Pathway pathway, int sequence) {
        if (pathway != Pathway.SEER) {
            return "";
        }
        return switch (sequence) {
            case 9 -> "Act as a Seer: divine with the Spirit Pendulum and observe with your Spirit Vision (V). Digestion is slow, and the same act repeated soon after counts for less.";
            case 8 -> "Act as a Clown: perform in battle. Defeat hostile creatures with Card Throw (G to select, R to use).";
            case 7 -> "Act as a Magician: deceive and amaze. Flame Jump, Air Bullet and Paper Substitute all count.";
            default -> "";
        };
    }

    // ---------------------------------------------------------------- loss of control

    public static void addLossOfControl(ServerPlayer player, float amount) {
        if (free(player)) {
            return;
        }
        BeyonderData d = get(player);
        d.setLossOfControl(d.lossOfControl() + amount);
        if (d.lossOfControl() >= 100.0F) {
            loseControl(player);
        } else {
            sync(player);
        }
    }

    private static void loseControl(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.6, 1.0, 0.6, 0.05);
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.8, 0.5, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 0.5F);
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(LOST_CONTROL));
        player.hurt(source, Float.MAX_VALUE);
    }

    // ---------------------------------------------------------------- potions

    /**
     * Drinking a potion starts a vision (played on the client). The outcome is decided now but lands at
     * the vision's climax, so the flash and the result arrive together.
     */
    public static void drinkPotion(ServerPlayer player, Pathway pathway, int sequence) {
        drinkPotion(player, pathway, sequence, 1);
    }

    /**
     * As above, for a potion of the given brewing quality (0 rough … 3 perfect). A better brew is
     * gentler on an undigested spirit: the gamble is less likely to go wrong.
     */
    public static void drinkPotion(ServerPlayer player, Pathway pathway, int sequence, int quality) {
        if (PENDING.containsKey(player.getUUID())) {
            return; // already in a vision
        }
        BeyonderData d = get(player);
        String name = pathway.sequenceName(sequence);

        if (d.isBeyonder() && d.pathway() == pathway && sequence >= d.sequence()) {
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
            player.displayClientMessage(Component.literal("You have already passed this Sequence. The potion only sickens you.")
                    .withStyle(ChatFormatting.GRAY), false);
            return;
        }

        int kind;
        Runnable outcome;
        if (!d.isBeyonder()) {
            if (sequence == 9) {
                kind = PotionVisionPayload.AWAKEN;
                outcome = () -> {
                    BeyonderData now = get(player);
                    now.become(pathway, 9);
                    now.setSpirituality(now.maxSpirituality() * 0.5F);
                    awaken(player, "The world settles. Voices fade. You are a Beyonder: " + name + ", Sequence 9.");
                };
            } else {
                kind = PotionVisionPayload.FATAL;
                outcome = () -> {
                    warn(player, "A mortal body cannot hold a Sequence " + sequence + " potion.");
                    addLossOfControl(player, 100.0F);
                };
            }
        } else if (d.pathway() != pathway) {
            kind = PotionVisionPayload.FATAL;
            outcome = () -> {
                warn(player, "Two pathways war inside you.");
                addLossOfControl(player, 100.0F);
            };
        } else if (sequence < d.sequence() - 1) {
            kind = PotionVisionPayload.FATAL;
            outcome = () -> {
                warn(player, "You tried to skip a Sequence. Your spirit cannot bear it.");
                addLossOfControl(player, 100.0F);
            };
        } else {
            // The next Sequence: safe when fully digested, a gamble otherwise.
            float undigested = 1.0F - d.digestion() / 100.0F;
            float risk = 1.0F + 0.15F * (1 - quality); // rough 1.15x, fine 0.85x, perfect 0.7x
            if (undigested > 0.0F && player.getRandom().nextFloat() < undigested * 0.9F * risk) {
                float loss = 50.0F + undigested * 60.0F;
                kind = d.lossOfControl() + loss >= 100.0F ? PotionVisionPayload.FATAL : PotionVisionPayload.FAILURE;
                outcome = () -> {
                    warn(player, "The potion was taken too soon. Something in you begins to slip.");
                    addLossOfControl(player, loss);
                };
            } else {
                kind = PotionVisionPayload.ADVANCE;
                outcome = () -> {
                    BeyonderData now = get(player);
                    now.become(pathway, sequence);
                    now.setSpirituality(now.maxSpirituality());
                    now.setLossOfControl(now.lossOfControl() + undigested * 40.0F);
                    awaken(player, "You advance. You are now " + name + ", Sequence " + sequence + ".");
                };
            }
        }

        // The ordeal: the body seizes while the vision plays.
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, PotionVisionPayload.CLIMAX_TICKS + 20, 3, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, PotionVisionPayload.LENGTH_TICKS, 1, false, false));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0F, 0.7F);
        PacketDistributor.sendToPlayer(player, new PotionVisionPayload(kind));
        later(player, PotionVisionPayload.CLIMAX_TICKS, outcome);
    }

    // ---------------------------------------------------------------- delayed actions

    public static void later(ServerPlayer player, int ticks, Runnable action) {
        PENDING.computeIfAbsent(player.getUUID(), k -> new ArrayList<>())
                .add(new Pending(player.level().getGameTime() + ticks, action));
    }

    /** Called every tick for each player: runs actions that are due. */
    public static void tickPending(ServerPlayer player) {
        List<Pending> list = PENDING.get(player.getUUID());
        if (list == null) {
            return;
        }
        long now = player.level().getGameTime();
        List<Pending> due = new ArrayList<>();
        list.removeIf(p -> {
            if (p.at() <= now) {
                due.add(p);
                return true;
            }
            return false;
        });
        if (list.isEmpty()) {
            PENDING.remove(player.getUUID());
        }
        due.forEach(p -> p.action().run());
    }

    /** On logout, finish anything pending so a potion is never lost. */
    public static void flushPending(ServerPlayer player) {
        List<Pending> list = PENDING.remove(player.getUUID());
        if (list != null) {
            list.forEach(p -> p.action().run());
        }
    }

    private static void awaken(ServerPlayer player, String message) {
        player.playNotifySound(SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.GOLD), false);
        BeyonderData d = get(player);
        String hint = actingHint(d.pathway(), d.sequence());
        if (!hint.isEmpty()) {
            player.displayClientMessage(Component.literal(hint).withStyle(ChatFormatting.GRAY), false);
        }
        sync(player);
    }

    private static void warn(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.DARK_RED), false);
    }

    // ---------------------------------------------------------------- once per second

    public static void secondTick(ServerPlayer player) {
        BeyonderData d = get(player);
        if (!d.isBeyonder()) {
            if (hasSpiritVision(player) && !free(player)) {
                setSpiritVision(player, false);
            }
            return;
        }
        boolean changed = false;

        if (hasSpiritVision(player) && !free(player)) {
            float drain = Math.max(0.8F, 2.0F - (9 - d.sequence()) * 0.3F);
            d.setSpirituality(d.spirituality() - drain);
            changed = true;
            if (d.spirituality() <= 0.0F) {
                SPIRIT_VISION.remove(player.getUUID());
                player.displayClientMessage(Component.literal("Your Spirituality runs dry. Your Spirit Vision closes.")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
                addLossOfControl(player, 8.0F);
            } else if (d.pathway() == Pathway.SEER && hasLivingNearby(player)) {
                act(player, 9, 0.15F, null); // observing with Spirit Vision is a Seer's act
            }
        } else if (d.spirituality() < d.maxSpirituality()) {
            float regen = Pathway.regenPerSecond(d.sequence());
            if (player.getMainHandItem().getItem() instanceof io.github.n252g2.beyonder.item.StaffItem && Equipment.isDrawn(player)) {
                regen *= 1.5F; // the crescent staff gathers spirit
            }
            d.setSpirituality(d.spirituality() + regen);
            changed = true;
        }

        if (d.lossOfControl() > 0.0F) {
            if (d.lossOfControl() >= 50.0F && player.getRandom().nextFloat() < 0.15F) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
                player.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8F, 0.7F);
            }
            d.setLossOfControl(d.lossOfControl() - 0.1F); // calms slowly: about 15 minutes for 100
            changed = true;
        }
        if (changed) {
            sync(player);
        }
    }

    private static boolean hasLivingNearby(ServerPlayer player) {
        return !player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(16.0),
                e -> e != player && e.isAlive()).isEmpty();
    }

    /** After death the madness is gone; Spirituality comes back at half. */
    public static void afterRespawn(ServerPlayer player) {
        BeyonderData d = get(player);
        d.setLossOfControl(0.0F);
        d.setSpirituality(Mth.floor(d.maxSpirituality() * 0.5F));
        SPIRIT_VISION.remove(player.getUUID());
        sync(player);
    }
}
