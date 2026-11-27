package io.github.n252g2.beyonder.item;

import io.github.n252g2.beyonder.content.BeyonderContent;
import io.github.n252g2.beyonder.core.Abilities;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A six-shot revolver, so mortals and Nighthawks can fight. The cylinder is loaded from your
 * inventory: rounds in your off hand first, then ordinary rounds, then silver. Silver rounds hurt
 * the undead far more. Use to fire; sneak-use (or fire on an empty cylinder) to reload.
 */
public class RevolverItem extends Item {
    private static final int CAPACITY = 6;
    private static final int FIRE_TICKS = 10;
    private static final int RELOAD_TICKS = 40;
    private static final double RANGE = 48.0;
    private static final String KEY = "Cylinder";

    public RevolverItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.success(stack);
        }
        String cylinder = cylinder(stack);
        if (sp.isShiftKeyDown() || cylinder.isEmpty()) {
            reload(sp, stack, cylinder);
        } else {
            fire(sp, stack, hand, cylinder);
        }
        return InteractionResultHolder.consume(stack);
    }

    private void fire(ServerPlayer player, ItemStack stack, InteractionHand hand, String cylinder) {
        boolean silver = cylinder.charAt(0) == 'S';
        setCylinder(stack, cylinder.substring(1));
        player.getCooldowns().addCooldown(this, FIRE_TICKS);
        ServerLevel level = player.serverLevel();

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 muzzle = eye.add(look.scale(0.8)).add(0, -0.15, 0);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                SoundSource.PLAYERS, 1.4F, 0.55F);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE,
                SoundSource.PLAYERS, 0.5F, 1.9F);
        level.sendParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, 0.05, 0.05, 0.05, 0.02);
        Abilities.trail(player, RANGE, silver ? ParticleTypes.END_ROD : ParticleTypes.CRIT);

        LivingEntity target = Abilities.target(player, RANGE);
        if (target != null) {
            float damage = 9.0F;
            if (silver && target.getType().is(EntityTypeTags.UNDEAD)) {
                damage += 8.0F;
                level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(0.6), target.getZ(), 12, 0.2, 0.3, 0.2, 0.05);
            }
            target.hurt(player.damageSources().playerAttack(player), damage);
            target.knockback(0.3, -look.x, -look.z);
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.6), target.getZ(), 4, 0.15, 0.15, 0.15, 0.1);
        } else {
            BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(RANGE)), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                BlockPos pos = hit.getBlockPos();
                BlockState state = level.getBlockState(pos);
                Vec3 at = hit.getLocation();
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 8, 0.05, 0.05, 0.05, 0.1);
                level.playSound(null, pos, state.getSoundType(level, pos, player).getHitSound(), SoundSource.BLOCKS, 0.8F, 1.4F);
            }
        }
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
    }

    private void reload(ServerPlayer player, ItemStack gun, String cylinder) {
        if (cylinder.length() >= CAPACITY) {
            player.displayClientMessage(Component.literal("The cylinder is full.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Inventory inv = player.getInventory();
        StringBuilder loaded = new StringBuilder(cylinder);
        // Off hand first, so you can choose silver; then ordinary rounds; then silver.
        loadFrom(player.getOffhandItem(), loaded);
        for (int pass = 0; pass < 2 && loaded.length() < CAPACITY; pass++) {
            Item want = pass == 0 ? BeyonderContent.REVOLVER_ROUND.get() : BeyonderContent.SILVER_ROUND.get();
            for (int i = 0; i < inv.getContainerSize() && loaded.length() < CAPACITY; i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(want)) {
                    loadFrom(s, loaded);
                }
            }
        }
        if (loaded.length() == cylinder.length()) {
            player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 0.6F, 1.6F);
            player.displayClientMessage(Component.literal("Click. You have no rounds.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        setCylinder(gun, loaded.toString());
        inv.setChanged();
        player.getCooldowns().addCooldown(this, RELOAD_TICKS);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.CHAIN_PLACE,
                SoundSource.PLAYERS, 0.8F, 1.5F);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.IRON_DOOR_CLOSE,
                SoundSource.PLAYERS, 0.4F, 2.0F);
        player.displayClientMessage(Component.literal("Reloaded: " + describe(loaded.toString())).withStyle(ChatFormatting.GRAY), true);
    }

    private static void loadFrom(ItemStack source, StringBuilder loaded) {
        char kind;
        if (source.is(BeyonderContent.SILVER_ROUND.get())) {
            kind = 'S';
        } else if (source.is(BeyonderContent.REVOLVER_ROUND.get())) {
            kind = 'L';
        } else {
            return;
        }
        while (loaded.length() < CAPACITY && !source.isEmpty()) {
            loaded.append(kind);
            source.shrink(1);
        }
    }

    private static String cylinder(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(KEY);
    }

    private static void setCylinder(ItemStack stack, String value) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(KEY, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static String describe(String cylinder) {
        long silver = cylinder.chars().filter(c -> c == 'S').count();
        long lead = cylinder.length() - silver;
        if (silver == 0) {
            return lead + "/" + CAPACITY + " rounds";
        }
        return lead + " ordinary, " + silver + " silver (" + cylinder.length() + "/" + CAPACITY + ")";
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String cylinder = cylinder(stack);
        tooltip.add(Component.literal("Cylinder: " + (cylinder.isEmpty() ? "empty" : describe(cylinder)))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Use to fire. Sneak-use to reload (off-hand rounds load first).")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Silver rounds sear the undead.").withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }
}
