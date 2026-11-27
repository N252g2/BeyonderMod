package io.github.n252g2.beyonder.core;

import java.util.List;

/**
 * The life a character led in Tingen before the story begins. Each gives a small, lasting perk
 * and something to start with.
 */
public enum Background {
    HISTORY_GRADUATE("history_graduate", "History Graduate",
            "Fresh from Khoy University with a head full of old languages and older secrets.",
            List.of("Starts with a Seer Potion Formula", "Starts with paper and 3 soli")),
    DOCK_WORKER("dock_worker", "Dock Worker",
            "Years hauling crates on the Tussock River docks.",
            List.of("+2 hearts of maximum health", "Starts with bread and 18 pence")),
    CONSTABLE("constable", "Constable",
            "You walked a beat in the Iron Cross district before things got strange.",
            List.of("Starts with a revolver and 12 rounds", "Starts with 2 soli")),
    APOTHECARY("apothecary", "Apothecary's Apprentice",
            "You measured powders behind a pharmacy counter on Narcissus Street.",
            List.of("Brewing is more forgiving (wider heat band)", "Starts with water bottles and herbs, and 1 soli")),
    STREET_URCHIN("street_urchin", "Street Urchin",
            "The lower streets raised you. You know every alley and how to run.",
            List.of("Moves 6% faster", "Starts with 30 pence")),
    HEIR("heir", "Heir of a Faded House",
            "An old name, a thin purse, and a cane your grandfather swore by.",
            List.of("Starts with a sword cane", "Starts with 1 gold pound"));

    private final String id;
    private final String title;
    private final String story;
    private final List<String> perks;

    Background(String id, String title, String story, List<String> perks) {
        this.id = id;
        this.title = title;
        this.story = story;
        this.perks = perks;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String story() {
        return story;
    }

    public List<String> perks() {
        return perks;
    }

    public static Background byId(String id) {
        for (Background b : values()) {
            if (b.id.equals(id)) {
                return b;
            }
        }
        return null;
    }
}
