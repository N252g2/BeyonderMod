package io.github.n252g2.beyonder.core;

import io.github.n252g2.beyonder.net.EquipActionPayload;
import io.github.n252g2.beyonder.net.EquipStatePayload;
import io.github.n252g2.beyonder.net.PlayerAnimPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingGetProjectileEvent;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server rules for the equipment: drawing, swapping, the belt, weapon use, death and syncing. */
public final class EquipmentRules {
    private static final Map<UUID, Integer> LAST_SENT = new ConcurrentHashMap<>();

    private EquipmentRules() {}

    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, EquipmentRules::onTick);
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, EquipmentRules::onDeath);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> resync(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> resync(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> resync(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> LAST_SENT.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.StartTracking.class, EquipmentRules::onStartTracking);
        NeoForge.EVENT_BUS.addListener(AttackEntityEvent.class, EquipmentRules::onAttack);
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem.class, EquipmentRules::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(LivingEntityUseItemEvent.Start.class, EquipmentRules::onUseStart);
        NeoForge.EVENT_BUS.addListener(LivingSwapItemsEvent.Hands.class, EquipmentRules::onSwapHands);
        NeoForge.EVENT_BUS.addListener(LivingGetProjectileEvent.class, EquipmentRules::onGetProjectile);
    }

    // ------------------------------------------------------------------ actions from the client

    public static void action(ServerPlayer player, int action) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        switch (action) {
            case EquipActionPayload.TOGGLE_DRAW -> {
                if (Equipment.isDrawn(player)) {
                    sheathe(player);
                } else {
                    draw(player);
                }
            }
            case EquipActionPayload.SHEATHE -> sheathe(player);
            case EquipActionPayload.SWAP -> swap(player);
            case EquipActionPayload.TOGGLE_CLOTHES -> {
                EquipmentData d = Equipment.get(player);
                d.setClothesOverArmour(!d.clothesOverArmour());
                sync(player);
            }
            default -> {
                int belt = action - EquipActionPayload.BELT_FIRST;
                if (belt >= 0 && belt < Equipment.Slot.BELTS.length) {
                    useBelt(player, Equipment.Slot.BELTS[belt]);
                }
            }
        }
    }

    public static void draw(ServerPlayer player) {
        EquipmentData d = Equipment.get(player);
        if (d.drawn()) {
            return;
        }
        player.stopUsingItem();
        d.setDrawn(true, player.getInventory().selected);
        Equipment.Kind kind = Equipment.Kind.of(Equipment.item(player, Equipment.Slot.WEAPON));
        sound(player, kind, true);
        anim(player, PlayerAnim.DRAW, kind.ordinal(), false); // the drawing client already plays it
        sync(player);
    }

    public static void sheathe(ServerPlayer player) {
        EquipmentData d = Equipment.get(player);
        if (!d.drawn()) {
            return;
        }
        ItemStack weapon = Equipment.item(player, Equipment.Slot.WEAPON);
        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
        d.setDrawn(false, -1);
        Equipment.Kind kind = Equipment.Kind.of(weapon);
        sound(player, kind, false);
        anim(player, PlayerAnim.SHEATHE, kind.ordinal(), false);
        sync(player);
    }

    /** Main weapon and sidearm (off hand) change places. */
    public static void swap(ServerPlayer player) {
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && !Equipment.Slot.WEAPON.accepts(off)) {
            player.displayClientMessage(Component.literal("Only a weapon can go in the weapon slot.")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        EquipmentData d = Equipment.get(player);
        ItemStack main = d.items().get(Equipment.Slot.WEAPON.ordinal());
        if (main.isEmpty() && off.isEmpty()) {
            return;
        }
        player.stopUsingItem();
        d.items().set(Equipment.Slot.WEAPON.ordinal(), off);
        player.getInventory().offhand.set(0, main);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(),
                SoundSource.PLAYERS, 0.6F, 1.3F);
        if (d.drawn()) {
            anim(player, PlayerAnim.SWAP, Equipment.Kind.of(off).ordinal(), false);
        }
        sync(player);
    }

    private static void useBelt(ServerPlayer player, Equipment.Slot slot) {
        ItemStack stack = Equipment.item(player, slot);
        if (stack.isEmpty()) {
            player.displayClientMessage(Component.literal("Nothing in that belt slot. Put potions or food there in your inventory (E).")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return;
        }
        boolean food = stack.has(net.minecraft.core.component.DataComponents.FOOD);
        if (food && !player.canEat(false)) {
            player.displayClientMessage(Component.literal("You aren't hungry.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        player.stopUsingItem();
        ItemStack before = stack;
        ItemStack result = stack.finishUsingItem(player.level(), player);
        EquipmentData d = Equipment.get(player);
        if (result != before) {
            // e.g. a glass bottle: the belt keeps what is left of the stack, the empty goes in the bag
            d.items().set(slot.ordinal(), before.isEmpty() ? ItemStack.EMPTY : before);
            if (!result.isEmpty() && !player.getInventory().add(result)) {
                player.drop(result, false);
            }
        } else if (before.isEmpty()) {
            d.items().set(slot.ordinal(), ItemStack.EMPTY);
        }
        player.getCooldowns().addCooldown(before.getItem(), 24);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                food ? SoundEvents.GENERIC_EAT : SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8F, 1.0F);
        anim(player, PlayerAnim.DRINK, 0, true);
    }

    private static void sound(ServerPlayer player, Equipment.Kind kind, boolean drawing) {
        SoundEvent sound = switch (kind) {
            case GUN -> SoundEvents.ARMOR_EQUIP_CHAIN.value();
            case HEAVY -> SoundEvents.ARMOR_EQUIP_NETHERITE.value();
            case FISTS -> SoundEvents.ARMOR_EQUIP_LEATHER.value();
            default -> SoundEvents.ARMOR_EQUIP_IRON.value();
        };
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS,
                0.7F, drawing ? 1.35F : 1.1F);
    }

    // ------------------------------------------------------------------ syncing

    public static void anim(ServerPlayer player, PlayerAnim anim, int variant, boolean includeSelf) {
        PlayerAnimPayload payload = new PlayerAnimPayload(player.getId(), anim.ordinal(), variant);
        if (includeSelf) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);
        } else {
            PacketDistributor.sendToPlayersTrackingEntity(player, payload);
        }
    }

    private static EquipStatePayload state(ServerPlayer player) {
        EquipmentData d = Equipment.get(player);
        List<ItemStack> visible = new ArrayList<>();
        visible.add(d.items().get(Equipment.Slot.WEAPON.ordinal()).copy());
        visible.add(player.getOffhandItem().copy());
        for (Equipment.Slot s : Equipment.Slot.CLOTHING) {
            visible.add(d.items().get(s.ordinal()).copy());
        }
        return new EquipStatePayload(player.getId(), d.drawn(), d.clothesOverArmour(), visible);
    }

    private static int stateHash(ServerPlayer player) {
        EquipmentData d = Equipment.get(player);
        int h = d.drawn() ? 1 : 0;
        h = h * 31 + (d.clothesOverArmour() ? 1 : 0);
        h = h * 31 + ItemStack.hashItemAndComponents(d.items().get(Equipment.Slot.WEAPON.ordinal()));
        h = h * 31 + ItemStack.hashItemAndComponents(player.getOffhandItem());
        for (Equipment.Slot s : Equipment.Slot.CLOTHING) {
            h = h * 31 + ItemStack.hashItemAndComponents(d.items().get(s.ordinal()));
        }
        return h;
    }

    public static void sync(ServerPlayer player) {
        LAST_SENT.put(player.getUUID(), stateHash(player));
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, state(player));
    }

    private static void resync(Player player) {
        if (player instanceof ServerPlayer sp) {
            sync(sp);
        }
    }

    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer watcher) {
            PacketDistributor.sendToPlayer(watcher, state(target));
        }
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        EquipmentData d = Equipment.get(player);
        // Picking another hotbar slot (scroll, number keys) puts the weapons away.
        if (d.drawn() && player.getInventory().selected != d.drawnHotbarSlot()) {
            sheathe(player);
        }
        if (player.tickCount % 5 == 0) {
            Integer last = LAST_SENT.get(player.getUUID());
            if (last == null || last != stateHash(player)) {
                sync(player);
            }
        }
    }

    // ------------------------------------------------------------------ weapons only work drawn

    private static void refuse(Player player) {
        if (!player.level().isClientSide) {
            player.displayClientMessage(Component.literal("Weapons only work from the weapon slot: equip it (E), then draw it (X).")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!Equipment.mayUse(player, player.getMainHandItem())) {
            event.setCanceled(true);
            refuse(player);
        }
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!Equipment.mayUse(player, event.getItemStack())) {
            event.setCanceled(true);
            refuse(player);
        }
    }

    private static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player && !Equipment.mayUse(player, event.getItem())) {
            event.setCanceled(true);
        }
    }

    /** No swapping hands with F while weapons are drawn (the main hand is the weapon slot). */
    private static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (event.getEntity() instanceof Player player && Equipment.isDrawn(player)) {
            event.setCanceled(true);
        }
    }

    /** Bows and crossbows draw arrows from the ammo pouch first. */
    private static void onGetProjectile(LivingGetProjectileEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getProjectileWeaponItemStack().getItem() instanceof ProjectileWeaponItem weapon)) {
            return;
        }
        var supported = weapon.getAllSupportedProjectiles(event.getProjectileWeaponItemStack());
        for (Equipment.Slot slot : new Equipment.Slot[] {Equipment.Slot.AMMO_1, Equipment.Slot.AMMO_2}) {
            ItemStack ammo = Equipment.item(player, slot);
            if (!ammo.isEmpty() && supported.test(ammo)) {
                event.setProjectileItemStack(ammo);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ death

    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.isCanceled()) {
            return;
        }
        EquipmentData d = Equipment.get(player);
        d.setDrawn(false, -1);
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        for (int i = 0; i < d.items().size(); i++) {
            ItemStack s = d.items().get(i);
            if (!s.isEmpty()) {
                player.drop(s, true, false);
                d.items().set(i, ItemStack.EMPTY);
            }
        }
    }
}
