package io.github.n252g2.beyonder.item;

import io.github.n252g2.beyonder.content.BeyonderContent;
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
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A six-shot revolver that handles like a modern gun mod: left click fires, hold right click to aim
 * down the sights (tighter shots, slower walk), and the reload key (R) loads the cylinder from your
 * inventory: rounds in your off hand first, then ordinary, then silver. Silver sears the undead.
 *
 * <p>The client sends fire and reload requests ({@code GunActionPayload}); everything is decided here.</p>
 */
public class RevolverItem extends Item {
    /** How a gun handles. Every gun shares the revolver's controls and animations. */
    public record Stats(float damage, int fireTicks, int reloadTicks, int capacity, float hipSpread, float adsSpread, float kick) {}

    public static final Stats REVOLVER = new Stats(9.0F, 9, 40, 6, 3.0F, 0.35F, 2.6F);
    public static final int CAPACITY = 6;
    public static final int RELOAD_TICKS = 40;
    private static final double RANGE = 64.0;
    private static final String KEY = "Cylinder";

    private final Stats stats;

    public RevolverItem(Properties properties) {
        this(properties, REVOLVER);
    }

    public RevolverItem(Properties properties, Stats stats) {
        super(properties);
        this.stats = stats;
    }

    public Stats stats() {
        return stats;
    }

    public static Stats stats(ItemStack stack) {
        return stack.getItem() instanceof RevolverItem gun ? gun.stats : REVOLVER;
    }

    /** Hook: a gun that always finds the target's weak point (Death Knell). */
    protected boolean findsWeakness() {
        return false;
    }

    /** Hook: runs after every shot (a Sealed Artifact's price). */
    protected void afterShot(ServerPlayer player, ItemStack stack) {}

    /** Hook: extra sound layered on the shot. */
    protected void shotSound(ServerLevel level, ServerPlayer player) {}

    // ------------------------------------------------------------------ aiming (hold use)

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    public static boolean isAiming(Player player) {
        return player.isUsingItem() && player.getUseItem().getItem() instanceof RevolverItem;
    }

    // ------------------------------------------------------------------ firing

    public static void fire(ServerPlayer player, ItemStack stack) {
        RevolverItem item = (RevolverItem) stack.getItem();
        if (player.getCooldowns().isOnCooldown(item)) {
            return;
        }
        String cylinder = cylinder(stack);
        ServerLevel level = player.serverLevel();
        if (cylinder.isEmpty()) {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 0.5F, 1.8F);
            return;
        }
        boolean silver = cylinder.charAt(0) == 'S';
        setCylinder(stack, cylinder.substring(1));
        Stats st = item.stats;
        player.getCooldowns().addCooldown(item, st.fireTicks());
        io.github.n252g2.beyonder.core.EquipmentRules.anim(player, io.github.n252g2.beyonder.core.PlayerAnim.RECOIL, 0, false);

        // Hip fire scatters; aimed shots go nearly where the sights are.
        float spread = isAiming(player) ? st.adsSpread() : st.hipSpread();
        if (player.isSprinting() || !player.onGround()) {
            spread += 2.5F;
        }
        float pitch = player.getXRot() + (float) player.getRandom().triangle(0.0, spread);
        float yaw = player.getYRot() + (float) player.getRandom().triangle(0.0, spread);
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        Vec3 eye = player.getEyePosition();

        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                SoundSource.PLAYERS, 1.6F, 0.5F + player.getRandom().nextFloat() * 0.08F);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE,
                SoundSource.PLAYERS, 0.4F, 1.9F);
        Vec3 muzzle = eye.add(dir.scale(1.1)).add(0, -0.12, 0);
        level.sendParticles(ParticleTypes.FLASH, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 6, 0.04, 0.04, 0.04, 0.015);
        item.shotSound(level, player);

        BlockHitResult block = level.clip(new ClipContext(eye, eye.add(dir.scale(RANGE)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? eye.add(dir.scale(RANGE)) : block.getLocation();
        AABB box = player.getBoundingBox().expandTowards(dir.scale(RANGE)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, limit, box,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player);
        Vec3 stop = hit != null ? hit.getLocation() : limit;

        // A faint tracer every couple of blocks.
        double length = stop.distanceTo(muzzle);
        Vec3 step = stop.subtract(muzzle).normalize();
        for (double t = 1.5; t < length; t += 2.0) {
            Vec3 p = muzzle.add(step.scale(t));
            level.sendParticles(silver ? ParticleTypes.END_ROD : ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }

        if (hit != null && hit.getEntity() instanceof LivingEntity target) {
            float damage = st.damage();
            boolean headshot = item.findsWeakness() || hit.getLocation().y > target.getEyeY() - 0.25;
            if (headshot) {
                damage *= 1.5F;
            }
            if (silver && target.getType().is(EntityTypeTags.UNDEAD)) {
                damage += 8.0F;
                level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(0.6), target.getZ(), 12, 0.2, 0.3, 0.2, 0.05);
            }
            target.invulnerableTime = 0; // fast follow-up shots all land
            target.hurt(player.damageSources().playerAttack(player), damage);
            target.knockback(0.25, -dir.x, -dir.z);
            if (headshot) {
                level.playSound(null, target.getX(), target.getEyeY(), target.getZ(), SoundEvents.ANVIL_LAND,
                        SoundSource.PLAYERS, 0.25F, 2.0F);
            }
        } else if (block.getType() != HitResult.Type.MISS) {
            BlockPos pos = block.getBlockPos();
            BlockState state = level.getBlockState(pos);
            Vec3 at = block.getLocation();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 10, 0.05, 0.05, 0.05, 0.15);
            level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 2, 0.02, 0.02, 0.02, 0.01);
            level.playSound(null, pos, state.getSoundType(level, pos, player).getHitSound(), SoundSource.BLOCKS, 0.9F, 1.4F);
        }
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(InteractionHand.MAIN_HAND));
        item.afterShot(player, stack);
    }

    // ------------------------------------------------------------------ reloading

    public static void reload(ServerPlayer player, ItemStack gun) {
        RevolverItem item = (RevolverItem) gun.getItem();
        int capacity = item.stats.capacity();
        String cylinder = cylinder(gun);
        if (cylinder.length() >= capacity || player.getCooldowns().isOnCooldown(item)) {
            return;
        }
        Inventory inv = player.getInventory();
        StringBuilder loaded = new StringBuilder(cylinder);
        loadFrom(player.getOffhandItem(), loaded, capacity);
        // the ammo pouch next, then the bag
        loadFrom(io.github.n252g2.beyonder.core.Equipment.item(player, io.github.n252g2.beyonder.core.Equipment.Slot.AMMO_1), loaded, capacity);
        loadFrom(io.github.n252g2.beyonder.core.Equipment.item(player, io.github.n252g2.beyonder.core.Equipment.Slot.AMMO_2), loaded, capacity);
        for (int pass = 0; pass < 2 && loaded.length() < capacity; pass++) {
            Item want = pass == 0 ? BeyonderContent.REVOLVER_ROUND.get() : BeyonderContent.SILVER_ROUND.get();
            for (int i = 0; i < inv.getContainerSize() && loaded.length() < capacity; i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(want)) {
                    loadFrom(s, loaded, capacity);
                }
            }
        }
        if (loaded.length() == cylinder.length()) {
            player.displayClientMessage(Component.literal("You have no rounds.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        setCylinder(gun, loaded.toString());
        inv.setChanged();
        player.getCooldowns().addCooldown(item, item.stats.reloadTicks());
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.CHAIN_PLACE,
                SoundSource.PLAYERS, 0.8F, 1.5F);
        io.github.n252g2.beyonder.core.EquipmentRules.anim(player, io.github.n252g2.beyonder.core.PlayerAnim.RELOAD,
                item.stats.reloadTicks(), false);
        // The cylinder swings back in near the end of the reload.
        io.github.n252g2.beyonder.core.Beyonders.later(player, item.stats.reloadTicks() - 8, () ->
                player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.IRON_DOOR_CLOSE,
                        SoundSource.PLAYERS, 0.45F, 2.0F));
    }

    private static void loadFrom(ItemStack source, StringBuilder loaded, int capacity) {
        char kind;
        if (source.is(BeyonderContent.SILVER_ROUND.get())) {
            kind = 'S';
        } else if (source.is(BeyonderContent.REVOLVER_ROUND.get())) {
            kind = 'L';
        } else {
            return;
        }
        while (loaded.length() < capacity && !source.isEmpty()) {
            loaded.append(kind);
            source.shrink(1);
        }
    }

    /** Rounds in the cylinder, next shot first: 'L' ordinary, 'S' silver. */
    public static String cylinder(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(KEY);
    }

    private static void setCylinder(ItemStack stack, String value) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(KEY, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    /** Rounds this player could load: ordinary and silver, in the ammo pouch or anywhere in the inventory. */
    public static int reserve(Player player) {
        int n = 0;
        for (var slot : new io.github.n252g2.beyonder.core.Equipment.Slot[] {
                io.github.n252g2.beyonder.core.Equipment.Slot.AMMO_1, io.github.n252g2.beyonder.core.Equipment.Slot.AMMO_2}) {
            ItemStack s = io.github.n252g2.beyonder.core.Equipment.item(player, slot);
            if (s.is(BeyonderContent.REVOLVER_ROUND.get()) || s.is(BeyonderContent.SILVER_ROUND.get())) {
                n += s.getCount();
            }
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(BeyonderContent.REVOLVER_ROUND.get()) || s.is(BeyonderContent.SILVER_ROUND.get())) {
                n += s.getCount();
            }
        }
        return n;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String cylinder = cylinder(stack);
        long silver = cylinder.chars().filter(c -> c == 'S').count();
        tooltip.add(Component.literal("Cylinder: " + cylinder.length() + "/" + stats.capacity()
                + (silver > 0 ? " (" + silver + " silver)" : "")).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Left click fire · hold right click aim · R reload").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Off-hand rounds load first. Silver sears the undead.").withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }

    /** The client's camera kick for this gun. */
    public static float kick(ItemStack stack, boolean aiming) {
        float k = stats(stack).kick();
        return aiming ? k * 0.6F : k;
    }
}
