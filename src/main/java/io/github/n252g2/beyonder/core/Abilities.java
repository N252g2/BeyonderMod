package io.github.n252g2.beyonder.core;

import io.github.n252g2.beyonder.Beyonder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The Seer pathway's powers.
 *
 * <ul>
 *   <li>Sequence 8, Clown: Card Throw (active), Acrobatics (passive: lighter falls).</li>
 *   <li>Sequence 7, Magician: Air Bullet and Flame Jump (active), Paper Substitute (passive:
 *       a paper figurine takes a heavy blow in your place).</li>
 * </ul>
 */
public final class Abilities {
    private static final Map<UUID, Map<Ability, Long>> COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> PAPER_SUBSTITUTE_READY = new ConcurrentHashMap<>();
    private static final int PAPER_SUBSTITUTE_COOLDOWN = 600; // 30 s

    private Abilities() {}

    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener(Abilities::onFall);
        NeoForge.EVENT_BUS.addListener(Abilities::onIncomingDamage);
    }

    private static boolean hasSequence(Player player, int atMost) {
        BeyonderData d = Beyonders.get(player);
        return d.pathway() == Pathway.SEER && d.sequence() <= atMost;
    }

    // ------------------------------------------------------------------ actives

    public static void use(ServerPlayer player, Ability ability) {
        BeyonderData d = Beyonders.get(player);
        if (!player.isCreative() && !ability.availableTo(d.pathway().id(), d.sequence())) {
            player.displayClientMessage(Component.literal("You cannot use " + ability.displayName() + " yet.")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long now = player.level().getGameTime();
        Map<Ability, Long> mine = COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        Long readyAt = mine.get(ability);
        if (readyAt != null && now < readyAt) {
            return;
        }
        boolean used = switch (ability) {
            case CARD_THROW -> cardThrow(player, d);
            case AIR_BULLET -> airBullet(player, d);
            case FLAME_JUMP -> flameJump(player);
        };
        if (used) {
            mine.put(ability, now + ability.cooldownTicks());
        }
    }

    /** Throws a hardened card. Clowns digest by defeating hostile creatures with it. */
    private static boolean cardThrow(ServerPlayer player, BeyonderData d) {
        if (!Beyonders.trySpend(player, Ability.CARD_THROW.cost())) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        Vec3 end = trail(player, 24.0, new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)));
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.6F);
        LivingEntity target = target(player, 24.0);
        if (target != null) {
            float damage = 5.0F + Math.max(0, 8 - d.sequence());
            target.hurt(player.damageSources().playerAttack(player), damage);
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 8, 0.2, 0.2, 0.2, 0.2);
            if (!target.isAlive() && target instanceof Enemy) {
                Beyonders.act(player, 8, 4.0F); // a Clown's performance
            }
        } else {
            level.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 4, 0.1, 0.1, 0.1, 0.1);
        }
        return true;
    }

    /** Snaps the fingers and fires compressed air. */
    private static boolean airBullet(ServerPlayer player, BeyonderData d) {
        if (!Beyonders.trySpend(player, Ability.AIR_BULLET.cost())) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        trail(player, 32.0, ParticleTypes.POOF);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0F, 1.8F);
        LivingEntity target = target(player, 32.0);
        if (target != null) {
            Vec3 look = player.getLookAngle();
            target.hurt(player.damageSources().playerAttack(player), 9.0F);
            target.knockback(0.9, -look.x, -look.z);
            level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(0.5), target.getZ(), 1, 0, 0, 0, 0);
            if (!target.isAlive()) {
                Beyonders.act(player, 7, 3.0F); // a Magician's trick
            }
        }
        return true;
    }

    /** Steps into a nearby flame and out of another one where you are looking. */
    private static boolean flameJump(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(24.0));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 aim = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();

        BlockPos flame = nearestFlame(level, BlockPos.containing(aim), 4);
        if (flame == null) {
            player.displayClientMessage(Component.literal("There is no flame there to step out of.")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        BlockPos landing = standingSpot(level, flame);
        if (landing == null) {
            player.displayClientMessage(Component.literal("That flame has no room to step out of.")
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (!Beyonders.trySpend(player, Ability.FLAME_JUMP.cost())) {
            return false;
        }
        burst(level, player.position(), ParticleTypes.FLAME);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.3F);
        player.teleportTo(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5);
        player.resetFallDistance();
        burst(level, player.position(), ParticleTypes.FLAME);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.6F);
        Beyonders.act(player, 7, 1.5F);
        return true;
    }

    private static BlockPos nearestFlame(ServerLevel level, BlockPos around, int radius) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-radius, -radius, -radius), around.offset(radius, radius, radius))) {
            if (isFlame(level.getBlockState(pos))) {
                double dist = pos.distSqr(around);
                if (dist < bestDistance) {
                    bestDistance = dist;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    private static boolean isFlame(BlockState state) {
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH) || state.is(Blocks.LANTERN)
                || state.is(Blocks.SOUL_LANTERN) || state.is(Beyonder.GAS_LAMP.get())) {
            return true;
        }
        boolean litBlock = state.is(BlockTags.CAMPFIRES) || state.is(BlockTags.CANDLES);
        return litBlock && state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
    }

    private static BlockPos standingSpot(ServerLevel level, BlockPos flame) {
        BlockPos[] tries = {flame.above(), flame.north(), flame.south(), flame.east(), flame.west(), flame.below()};
        for (BlockPos pos : tries) {
            if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                    && !isFlame(level.getBlockState(pos))) {
                return pos;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ passives

    /** Clown and beyond: acrobatics soften every fall. */
    private static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && hasSequence(player, 8)) {
            event.setDamageMultiplier(event.getDamageMultiplier() * 0.4F);
        }
    }

    /** Magician and beyond: a paper figurine takes a heavy blow in your place, if you carry paper. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !hasSequence(player, 7)) {
            return;
        }
        DamageSource source = event.getSource();
        if (event.getAmount() < 6.0F || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(DamageTypeTags.IS_FALL) || source.is(Beyonders.LOST_CONTROL)) {
            return;
        }
        long now = player.level().getGameTime();
        Long ready = PAPER_SUBSTITUTE_READY.get(player.getUUID());
        if (ready != null && now < ready) {
            return;
        }
        if (!player.isCreative() && !consumePaper(player)) {
            return;
        }
        event.setCanceled(true);
        PAPER_SUBSTITUTE_READY.put(player.getUUID(), now + PAPER_SUBSTITUTE_COOLDOWN);

        ServerLevel level = player.serverLevel();
        burst(level, player.position(), new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.PAPER)));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PUT, SoundSource.PLAYERS, 1.0F, 0.8F);
        Entity attacker = source.getEntity();
        Vec3 away = attacker != null ? player.position().subtract(attacker.position()).normalize() : player.getLookAngle().reverse();
        player.randomTeleport(player.getX() + away.x * 4.0, player.getY(), player.getZ() + away.z * 4.0, true);
        player.displayClientMessage(Component.literal("A paper figurine crumples in your place.").withStyle(ChatFormatting.GOLD), true);
        Beyonders.act(player, 7, 3.0F);
    }

    private static boolean consumePaper(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.PAPER)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ helpers

    public static LivingEntity target(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, limit, box,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    /** Draws a particle line along the player's aim; returns where it stopped. */
    public static Vec3 trail(ServerPlayer player, double range, ParticleOptions particle) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        double length = stop.distanceTo(eye);
        Vec3 step = player.getLookAngle();
        ServerLevel level = player.serverLevel();
        for (double t = 1.0; t < length; t += 0.8) {
            Vec3 p = eye.add(step.scale(t));
            level.sendParticles(particle, p.x, p.y - 0.1, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        return stop;
    }

    private static void burst(ServerLevel level, Vec3 at, ParticleOptions particle) {
        level.sendParticles(particle, at.x, at.y + 1.0, at.z, 40, 0.4, 0.8, 0.4, 0.05);
    }

    public static void forget(Player player) {
        COOLDOWNS.remove(player.getUUID());
        PAPER_SUBSTITUTE_READY.remove(player.getUUID());
    }
}
