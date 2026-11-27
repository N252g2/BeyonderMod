package io.github.n252g2.beyonder.core;

import io.github.n252g2.beyonder.Beyonder;
import io.github.n252g2.beyonder.content.BeyonderContent;
import io.github.n252g2.beyonder.net.CharacterSyncPayload;
import io.github.n252g2.beyonder.net.CreateCharacterPayload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Character creation on the server: who has made their character, the rules a name must follow,
 * the background perks, and holding a new player still until their character exists.
 */
public final class Characters {
    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z'\\-]*( [A-Za-z][A-Za-z'\\-]*){0,2}");
    private static final ResourceLocation DOCK_HEALTH = Beyonder.id("background_dock_worker");
    private static final ResourceLocation URCHIN_SPEED = Beyonder.id("background_street_urchin");
    private static final Map<UUID, Vec3> HELD = new ConcurrentHashMap<>();

    private Characters() {}

    public static CharacterData get(Player player) {
        return player.getData(BeyonderAttachments.CHARACTER);
    }

    public static boolean created(Player player) {
        return get(player).created();
    }

    public static CharacterSyncPayload payload(Player player) {
        CharacterData d = get(player);
        return new CharacterSyncPayload(player.getId(), d.created(), d.name(), d.background(), d.appearance());
    }

    /** Tell this player and everyone who can see them about their character. */
    public static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload(player));
    }

    // ------------------------------------------------------------------ joining

    public static void onLogin(ServerPlayer player) {
        sync(player);
        // Tell the newcomer about everyone already here (their skins and names).
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other != player) {
                PacketDistributor.sendToPlayer(player, payload(other));
            }
        }
        applyPerks(player);
        if (!created(player)) {
            HELD.put(player.getUUID(), player.position());
            player.displayClientMessage(Component.literal("Before you step into Tingen, tell us who you are.")
                    .withStyle(ChatFormatting.GOLD), false);
        }
        player.refreshDisplayName();
        player.refreshTabListName();
    }

    public static void onLogout(Player player) {
        HELD.remove(player.getUUID());
    }

    /** Until the character exists the player stays where they arrived. */
    public static void tick(ServerPlayer player) {
        Vec3 anchor = HELD.get(player.getUUID());
        if (anchor == null) {
            return;
        }
        if (created(player)) {
            HELD.remove(player.getUUID());
            return;
        }
        player.setDeltaMovement(Vec3.ZERO);
        if (player.position().distanceToSqr(anchor) > 0.01) {
            player.teleportTo(anchor.x, anchor.y, anchor.z);
        }
    }

    // ------------------------------------------------------------------ creating

    public static void create(ServerPlayer player, CreateCharacterPayload request) {
        CharacterData data = get(player);
        if (data.created()) {
            return;
        }
        String name = request.name().trim().replaceAll("\\s+", " ");
        String problem = nameProblem(player, name);
        if (problem != null) {
            player.displayClientMessage(Component.literal(problem).withStyle(ChatFormatting.RED), false);
            sync(player); // reopens the creator with the problem shown
            return;
        }
        Background background = Background.byId(request.background());
        if (background == null) {
            background = Background.HISTORY_GRADUATE;
        }
        data.create(name, background, Appearance.sanitize(request.appearance()));
        player.setData(BeyonderAttachments.CHARACTER, data);
        HELD.remove(player.getUUID());

        giveStart(player, background);
        applyPerks(player);
        player.refreshDisplayName();
        player.refreshTabListName();
        sync(player);

        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE,
                SoundSource.PLAYERS, 0.6F, 1.2F);
        player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(),
                20, 0.4, 0.8, 0.4, 0.02);
        player.displayClientMessage(Component.literal("Welcome to Tingen, " + name + ".").withStyle(ChatFormatting.GOLD), false);
        player.server.getPlayerList().broadcastSystemMessage(Component.literal(name + " (" + player.getGameProfile().getName()
                + ") has arrived in Tingen.").withStyle(ChatFormatting.GRAY), false);
    }

    /** Why a name can't be used, or null if it can. */
    public static String nameProblem(Player player, String name) {
        if (name.length() < 2 || name.length() > 24) {
            return "A name must be between 2 and 24 letters long.";
        }
        if (!NAME.matcher(name).matches()) {
            return "Names may use letters, spaces, hyphens and apostrophes (up to three words).";
        }
        if (player.getServer() != null) {
            for (ServerPlayer other : player.getServer().getPlayerList().getPlayers()) {
                if (other != player && created(other) && get(other).name().equalsIgnoreCase(name)) {
                    return "Someone in Tingen already goes by that name.";
                }
            }
        }
        return null;
    }

    /** For staff: make a player go through character creation again. */
    public static void reset(ServerPlayer player) {
        CharacterData data = get(player);
        data.reset();
        player.setData(BeyonderAttachments.CHARACTER, data);
        HELD.put(player.getUUID(), player.position());
        player.refreshDisplayName();
        player.refreshTabListName();
        sync(player);
    }

    // ------------------------------------------------------------------ backgrounds

    private static void giveStart(ServerPlayer player, Background background) {
        switch (background) {
            case HISTORY_GRADUATE -> {
                give(player, new ItemStack(BeyonderContent.SEER_FORMULA.get()));
                give(player, new ItemStack(Items.PAPER, 8));
                give(player, new ItemStack(BeyonderContent.SILVER_SOLI.get(), 3));
            }
            case DOCK_WORKER -> {
                give(player, new ItemStack(Items.BREAD, 6));
                give(player, new ItemStack(BeyonderContent.COPPER_PENNY.get(), 18));
            }
            case CONSTABLE -> {
                ItemStack gun = new ItemStack(BeyonderContent.REVOLVER.get());
                CompoundTag tag = new CompoundTag();
                tag.putString("Cylinder", "LLLLLL");
                gun.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                equip(player, Equipment.Slot.WEAPON, gun);
                equip(player, Equipment.Slot.AMMO_1, new ItemStack(BeyonderContent.REVOLVER_ROUND.get(), 6));
                give(player, new ItemStack(BeyonderContent.SILVER_SOLI.get(), 2));
            }
            case APOTHECARY -> {
                for (int i = 0; i < 2; i++) {
                    give(player, PotionContents.createItemStack(Items.POTION, Potions.WATER));
                }
                equip(player, Equipment.Slot.BELT_1, PotionContents.createItemStack(Items.POTION, Potions.HEALING));
                give(player, new ItemStack(BeyonderContent.STARLIT_LAVENDER.get()));
                give(player, new ItemStack(BeyonderContent.DREAM_MOSS.get(), 2));
                give(player, new ItemStack(BeyonderContent.SILVER_SOLI.get()));
            }
            case STREET_URCHIN -> give(player, new ItemStack(BeyonderContent.COPPER_PENNY.get(), 30));
            case HEIR -> {
                equip(player, Equipment.Slot.WEAPON, new ItemStack(BeyonderContent.SWORD_CANE.get()));
                give(player, new ItemStack(BeyonderContent.GOLD_POUND.get()));
            }
        }
    }

    /** Starting weapons and ammo go straight into their equipment slots (or the bag if taken). */
    private static void equip(ServerPlayer player, Equipment.Slot slot, ItemStack stack) {
        EquipmentData d = Equipment.get(player);
        if (d.items().get(slot.ordinal()).isEmpty()) {
            d.items().set(slot.ordinal(), stack);
        } else {
            give(player, stack);
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /** Lasting perks are attribute bonuses; they're re-applied on every login and respawn. */
    public static void applyPerks(ServerPlayer player) {
        Background b = Background.byId(get(player).background());
        boolean made = created(player);
        set(player.getAttribute(Attributes.MAX_HEALTH), DOCK_HEALTH, made && b == Background.DOCK_WORKER ? 4.0 : 0.0,
                AttributeModifier.Operation.ADD_VALUE);
        set(player.getAttribute(Attributes.MOVEMENT_SPEED), URCHIN_SPEED, made && b == Background.STREET_URCHIN ? 0.06 : 0.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static void set(AttributeInstance attribute, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(id);
        if (amount != 0.0) {
            attribute.addTransientModifier(new AttributeModifier(id, amount, op));
        }
    }

    /** The name other people see: the character's, once it exists. */
    public static Component displayName(Player player, Component fallback) {
        CharacterData d = get(player);
        return d.created() && !d.name().isEmpty() ? Component.literal(d.name()) : fallback;
    }
}
