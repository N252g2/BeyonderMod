package io.github.n252g2.beyonder.core;

import java.util.List;

/**
 * The 22 pathways of the Beyonder world. Only the Seer is playable so far; each pathway lists the
 * names of its Sequences from 9 (the first potion) downward.
 */
public enum Pathway {
    NONE("none", List.of()),
    SEER("seer", List.of("Seer", "Clown", "Magician", "Faceless", "Marionettist", "Bizarro Sorcerer",
            "Scholar of Yore", "Miracle Invoker", "Attendant of Mysteries", "Fool"));

    /** The lowest Sequence that has a potion in the game so far. */
    public static final int LOWEST_IMPLEMENTED = 7;
    /** Sequence value meaning "not a Beyonder". */
    public static final int MORTAL = 10;

    private final String id;
    private final List<String> sequenceNames; // index 0 = Sequence 9

    Pathway(String id, List<String> sequenceNames) {
        this.id = id;
        this.sequenceNames = sequenceNames;
    }

    public String id() {
        return id;
    }

    public String sequenceName(int sequence) {
        int index = 9 - sequence;
        return index >= 0 && index < sequenceNames.size() ? sequenceNames.get(index) : "Mortal";
    }

    public static Pathway byId(String id) {
        for (Pathway p : values()) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return NONE;
    }

    /** Maximum Spirituality for a Sequence: it grows with every potion. */
    public static float maxSpirituality(int sequence) {
        if (sequence >= MORTAL) {
            return 0.0F;
        }
        return 100.0F + (9 - sequence) * 70.0F;
    }

    /** Spirituality regained per second while not channeling. */
    public static float regenPerSecond(int sequence) {
        return sequence >= MORTAL ? 0.0F : 1.0F + (9 - sequence) * 0.4F;
    }
}
