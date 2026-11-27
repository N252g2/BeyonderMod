package io.github.n252g2.beyonder.core;

import io.github.n252g2.beyonder.Beyonder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Item tags for the equipment slots. Kept apart from {@link Equipment} so the slot enum can be
 * built without first initialising the class that counts its slots.
 */
public final class EquipmentTags {
    public static final TagKey<Item> WEAPONS = tag("weapons");
    public static final TagKey<Item> AMMO = tag("ammo");
    public static final TagKey<Item> BELT = tag("belt");

    private EquipmentTags() {}

    static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, Beyonder.id(path));
    }
}
