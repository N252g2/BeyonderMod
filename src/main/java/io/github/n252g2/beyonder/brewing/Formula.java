package io.github.n252g2.beyonder.brewing;

import io.github.n252g2.beyonder.Beyonder;
import io.github.n252g2.beyonder.content.BeyonderContent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;

/**
 * A potion formula: what goes into the flask at an Apothecary Bench. Every formula also needs one
 * water bottle as its base. Ingredients are suppliers so this enum can be loaded before the items
 * are registered.
 */
public enum Formula {
    SEER(9, 0x6C7FD6, () -> Beyonder.SEER_POTION.get(), ingredients(
            BeyonderContent.STARLIT_LAVENDER, 1,
            BeyonderContent.DREAM_MOSS, 2,
            BeyonderContent.WRAITH_ASH, 1)),
    CLOWN(8, 0xC8403F, () -> Beyonder.CLOWN_POTION.get(), ingredients(
            BeyonderContent.SHADOW_SILK, 2,
            BeyonderContent.PHANTOM_EYE, 1,
            BeyonderContent.WRAITH_ASH, 1,
            BeyonderContent.STARLIT_LAVENDER, 1)),
    MAGICIAN(7, 0x9B5FD0, () -> Beyonder.MAGICIAN_POTION.get(), ingredients(
            BeyonderContent.MIRROR_SHARD, 2,
            BeyonderContent.PHANTOM_EYE, 1,
            BeyonderContent.CRIMSON_SPORE, 1,
            BeyonderContent.DREAM_MOSS, 1));

    private final int sequence;
    private final int smokeColor;
    private final Supplier<Item> result;
    private final Map<Supplier<? extends Item>, Integer> ingredients;

    Formula(int sequence, int smokeColor, Supplier<Item> result, Map<Supplier<? extends Item>, Integer> ingredients) {
        this.sequence = sequence;
        this.smokeColor = smokeColor;
        this.result = result;
        this.ingredients = ingredients;
    }

    public int sequence() {
        return sequence;
    }

    /** Colour of the smoke that rises when this potion finishes brewing (RGB). */
    public int smokeColor() {
        return smokeColor;
    }

    public Item result() {
        return result.get();
    }

    /** Ingredient → count, in the order the formula lists them. */
    public Map<Supplier<? extends Item>, Integer> ingredients() {
        return ingredients;
    }

    // ------------------------------------------------------------------ the brewing minigame

    /** Half the width of the safe temperature band (0..1 scale). Higher Sequences are less forgiving. */
    public float bandHalfWidth() {
        return switch (sequence) {
            case 9 -> 0.145F;
            case 8 -> 0.12F;
            default -> 0.10F;
        };
    }

    /** Width of the "add now" zone on the timing bar (0..1). */
    public float zoneWidth() {
        return switch (sequence) {
            case 9 -> 0.26F;
            case 8 -> 0.21F;
            default -> 0.17F;
        };
    }

    /** Ticks for the timing marker to sweep across the bar and back. */
    public int sweepTicks() {
        return switch (sequence) {
            case 9 -> 42;
            case 8 -> 36;
            default -> 30;
        };
    }

    /** Ticks the steady phases need: warming the base, dissolving each ingredient, letting it settle. */
    public static final int HEAT_TICKS = 50;
    public static final int DISSOLVE_TICKS = 30;
    public static final int SETTLE_TICKS = 40;

    /** The fastest a brew can honestly be finished; the server rejects anything quicker. */
    public int minBrewTicks() {
        int steady = HEAT_TICKS + ingredients.size() * (DISSOLVE_TICKS + 5) + SETTLE_TICKS;
        return (int) (steady * 0.8F);
    }

    public static Formula byIndex(int index) {
        Formula[] all = values();
        return index >= 0 && index < all.length ? all[index] : null;
    }

    private static Map<Supplier<? extends Item>, Integer> ingredients(Object... pairs) {
        Map<Supplier<? extends Item>, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked")
            Supplier<? extends Item> item = (Supplier<? extends Item>) pairs[i];
            map.put(item, (Integer) pairs[i + 1]);
        }
        return map;
    }
}
