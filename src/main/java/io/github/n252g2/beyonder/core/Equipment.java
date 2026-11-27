package io.github.n252g2.beyonder.core;

import io.github.n252g2.beyonder.Beyonder;
import io.github.n252g2.beyonder.item.BeyonderPotionItem;
import io.github.n252g2.beyonder.item.RevolverItem;
import io.github.n252g2.beyonder.item.StaffItem;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import io.github.n252g2.beyonder.item.CrimsonGreatswordItem;

/**
 * The Beyonder equipment beside the vanilla inventory: clothing (worn under armour, which stays in
 * the vanilla armour slots), a main weapon (the vanilla off-hand slot is the sidearm), an ammo pouch,
 * accessories, a worn Sealed Artifact and a potion belt.
 *
 * <p>Weapons only work when equipped and drawn: while drawn, the main hand <i>is</i> the weapon slot
 * and the off hand shows the sidearm. Scrolling the hotbar puts them away.</p>
 */
public final class Equipment {
    public static final TagKey<Item> WEAPONS = tag("weapons");
    public static final TagKey<Item> AMMO = tag("ammo");
    public static final TagKey<Item> BELT = tag("belt");

    /** One slot of the Beyonder equipment; {@code x, y} place it in the inventory screen. */
    public enum Slot {
        HAT("Hat", "hat", tag("clothing/hat"), 1, -24, 8),
        SHIRT("Shirt or waistcoat", "shirt", tag("clothing/shirt"), 1, -42, 26),
        COAT("Coat", "coat", tag("clothing/coat"), 1, -24, 26),
        GLOVES("Gloves", "gloves", tag("clothing/gloves"), 1, -42, 44),
        TROUSERS("Trousers or skirt", "trousers", tag("clothing/trousers"), 1, -24, 44),
        SHOES("Shoes", "shoes", tag("clothing/shoes"), 1, -24, 62),
        WEAPON("Main weapon", "weapon", WEAPONS, 1, 96, 62),
        AMMO_1("Ammo pouch", "ammo", AMMO, 64, 128, 62),
        AMMO_2("Ammo pouch", "ammo", AMMO, 64, 146, 62),
        RING_1("Ring", "ring", tag("accessories/ring"), 1, 184, 8),
        RING_2("Ring", "ring", tag("accessories/ring"), 1, 184, 26),
        AMULET("Amulet", "amulet", tag("accessories/amulet"), 1, 184, 44),
        CHARM("Charm or pocket watch", "charm", tag("accessories/charm"), 1, 184, 62),
        ARTIFACT("Sealed Artifact", "artifact", tag("worn_artifacts"), 1, 206, 8),
        BELT_1("Potion belt", "belt", BELT, 16, 206, 30),
        BELT_2("Potion belt", "belt", BELT, 16, 206, 48),
        BELT_3("Potion belt", "belt", BELT, 16, 206, 66);

        public static final Slot[] CLOTHING = {HAT, SHIRT, COAT, GLOVES, TROUSERS, SHOES};
        public static final Slot[] BELTS = {BELT_1, BELT_2, BELT_3};

        private final String label;
        private final String icon;
        private final TagKey<Item> tag;
        private final int maxStack;
        public final int x, y;

        Slot(String label, String icon, TagKey<Item> tag, int maxStack, int x, int y) {
            this.label = label;
            this.icon = icon;
            this.tag = tag;
            this.maxStack = maxStack;
            this.x = x;
            this.y = y;
        }

        public String label() {
            return label;
        }

        public ResourceLocation icon() {
            return Beyonder.id("item/empty_slot/" + icon);
        }

        public int maxStack() {
            return maxStack;
        }

        public boolean accepts(ItemStack stack) {
            if (stack.isEmpty()) {
                return true;
            }
            return switch (this) {
                case WEAPON -> isWeapon(stack);
                // Sequence potions are drunk with care, not from a belt
                case BELT_1, BELT_2, BELT_3 -> !(stack.getItem() instanceof BeyonderPotionItem)
                        && (stack.is(BELT) || stack.getItem() instanceof PotionItem || stack.has(DataComponents.FOOD));
                default -> stack.is(tag);
            };
        }
    }

    public static final int SIZE = Slot.values().length;

    private Equipment() {}

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, Beyonder.id(path));
    }

    public static EquipmentData get(Player player) {
        return player.getData(BeyonderAttachments.EQUIPMENT);
    }

    public static ItemStack item(Player player, Slot slot) {
        return get(player).items().get(slot.ordinal());
    }

    // ------------------------------------------------------------------ weapons

    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return stack.is(WEAPONS) || stack.is(ItemTags.SWORDS) || item instanceof SwordItem || item instanceof RevolverItem
                || item instanceof StaffItem || item instanceof BowItem || item instanceof CrossbowItem
                || item instanceof TridentItem || item instanceof MaceItem;
    }

    /** How a weapon is held, drawn and carried. */
    public enum Kind {
        FISTS, BLADE, HEAVY, STAFF, GUN, BOW;

        public static Kind of(ItemStack stack) {
            if (stack.isEmpty()) {
                return FISTS;
            }
            Item item = stack.getItem();
            if (item instanceof RevolverItem) {
                return GUN;
            }
            if (item instanceof CrimsonGreatswordItem || item instanceof AxeItem || item instanceof MaceItem) {
                return HEAVY;
            }
            if (item instanceof StaffItem || item instanceof TridentItem) {
                return STAFF;
            }
            if (item instanceof BowItem || item instanceof CrossbowItem) {
                return BOW;
            }
            return BLADE;
        }
    }

    /**
     * Whether this player's main-hand item comes from the weapon slot right now. Only the server's
     * player and this client's own player follow the rule; other players on a client are just
     * drawn with whatever the server says they hold.
     */
    public static boolean redirectsMainHand(Player player) {
        return (player instanceof ServerPlayer || player.isLocalPlayer()) && get(player).drawn();
    }

    public static boolean isDrawn(Player player) {
        return get(player).drawn();
    }

    /**
     * Whether a weapon in this hand may be used: weapons only work drawn (main hand from the weapon
     * slot, off hand as the sidearm). Anything else works as always.
     */
    public static boolean mayUse(Player player, ItemStack stack) {
        return !isWeapon(stack) || isDrawn(player);
    }
}
