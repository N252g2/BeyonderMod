package io.github.n252g2.beyonder.core;

import java.util.ArrayList;
import java.util.List;

/** Active Beyonder abilities, chosen with G and used with R. Passives live in {@link Abilities}. */
public enum Ability {
    CARD_THROW("card_throw", "Card Throw", Pathway.SEER, 8, 6.0F, 12),
    AIR_BULLET("air_bullet", "Air Bullet", Pathway.SEER, 7, 10.0F, 20),
    FLAME_JUMP("flame_jump", "Flame Jump", Pathway.SEER, 7, 16.0F, 30);

    private final String id;
    private final String displayName;
    private final Pathway pathway;
    private final int sequence; // available from this Sequence onward (lower numbers)
    private final float cost;
    private final int cooldownTicks;

    Ability(String id, String displayName, Pathway pathway, int sequence, float cost, int cooldownTicks) {
        this.id = id;
        this.displayName = displayName;
        this.pathway = pathway;
        this.sequence = sequence;
        this.cost = cost;
        this.cooldownTicks = cooldownTicks;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public float cost() {
        return cost;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public boolean availableTo(String pathwayId, int playerSequence) {
        return pathway.id().equals(pathwayId) && playerSequence <= sequence;
    }

    public static Ability byId(String id) {
        for (Ability a : values()) {
            if (a.id.equals(id)) {
                return a;
            }
        }
        return null;
    }

    public static List<Ability> availableFor(String pathwayId, int sequence) {
        List<Ability> list = new ArrayList<>();
        for (Ability a : values()) {
            if (a.availableTo(pathwayId, sequence)) {
                list.add(a);
            }
        }
        return list;
    }
}
