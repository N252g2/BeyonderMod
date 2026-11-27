package io.github.n252g2.beyonder.core;

import java.util.Arrays;
import java.util.List;
import net.minecraft.util.RandomSource;

/**
 * What a character looks like: a small set of choices the client turns into a skin. Stored and sent
 * as an int array, one entry per {@link Slot}.
 */
public final class Appearance {
    public enum Slot {
        SKIN("Skin tone", List.of("Porcelain", "Fair", "Rose", "Olive", "Tan", "Bronze", "Brown", "Deep")),
        EYES("Eyes", List.of("Grey", "Blue", "Green", "Hazel", "Brown", "Dark", "Amber", "Violet")),
        BROWS("Brows", List.of("Straight", "Arched", "Heavy")),
        HAIR("Hair", List.of("Short and neat", "Side parting", "Slicked back", "Tousled", "Curly",
                "Bob", "Long, tied back", "Long and loose", "Close-cropped")),
        HAIR_COLOR("Hair colour", List.of("Black", "Dark brown", "Brown", "Chestnut", "Auburn", "Copper",
                "Fair", "Platinum", "Grey", "White")),
        FACIAL_HAIR("Facial hair", List.of("None", "Stubble", "Moustache", "Mutton chops", "Full beard")),
        BUILD("Build", List.of("Broad", "Slender")),
        CLOTHES("Clothes", List.of("Charcoal", "Navy", "Brown", "Burgundy", "Bottle green"));

        private final String label;
        private final List<String> options;

        Slot(String label, List<String> options) {
            this.label = label;
            this.options = options;
        }

        public String label() {
            return label;
        }

        public List<String> options() {
            return options;
        }

        public int count() {
            return options.size();
        }
    }

    public static final int SIZE = Slot.values().length;

    private Appearance() {}

    public static int[] defaults() {
        return new int[] {1, 0, 0, 0, 1, 0, 0, 0};
    }

    public static int[] random(RandomSource random) {
        int[] a = new int[SIZE];
        for (Slot s : Slot.values()) {
            a[s.ordinal()] = random.nextInt(s.count());
        }
        return a;
    }

    /** Clamps every choice into range (the server never trusts a client's numbers). */
    public static int[] sanitize(int[] in) {
        int[] out = defaults();
        if (in == null) {
            return out;
        }
        for (Slot s : Slot.values()) {
            int i = s.ordinal();
            if (i < in.length) {
                out[i] = Math.floorMod(in[i], s.count());
            }
        }
        return out;
    }

    public static int get(int[] appearance, Slot slot) {
        return slot.ordinal() < appearance.length ? Math.floorMod(appearance[slot.ordinal()], slot.count()) : 0;
    }

    public static boolean slim(int[] appearance) {
        return get(appearance, Slot.BUILD) == 1;
    }

    public static String key(int[] appearance) {
        return Arrays.toString(appearance);
    }
}
