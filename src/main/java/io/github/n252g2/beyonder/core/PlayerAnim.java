package io.github.n252g2.beyonder.core;

/** Body animations the server tells clients to play on a player (the client does the posing). */
public enum PlayerAnim {
    DRAW(10), SHEATHE(10), SWAP(8), RECOIL(5), RELOAD(40), CAST(12), DRINK(16);

    // CAST variants
    public static final int CAST_CARD = 0, CAST_AIR_BULLET = 1, CAST_FLAME = 2, CAST_PAPER = 3, CAST_DIVINE = 4, CAST_OTHER = 5;

    private final int ticks;

    PlayerAnim(int ticks) {
        this.ticks = ticks;
    }

    /** How long it lasts; a reload's length comes with it (its variant is the reload time). */
    public int ticks(int variant) {
        return this == RELOAD ? Math.max(8, variant) : ticks;
    }

    public static PlayerAnim byId(int id) {
        PlayerAnim[] all = values();
        return id >= 0 && id < all.length ? all[id] : null;
    }
}
