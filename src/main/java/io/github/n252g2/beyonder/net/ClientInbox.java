package io.github.n252g2.beyonder.net;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * What the server has told this client. Deliberately free of client-only classes so the
 * packet handlers can be registered on a dedicated server; client code reads from here.
 */
public final class ClientInbox {
    private static final Map<Integer, BeyonderSyncPayload> BEYONDERS = new ConcurrentHashMap<>();
    private static final Queue<SpiritMarkerPayload> MARKERS = new ConcurrentLinkedQueue<>();
    private static final Queue<PotionVisionPayload> VISIONS = new ConcurrentLinkedQueue<>();
    private static final Queue<BrewStartPayload> BREWS = new ConcurrentLinkedQueue<>();
    private static final Map<Integer, CharacterSyncPayload> CHARACTERS = new ConcurrentHashMap<>();
    private static volatile int characterVersion;

    private ClientInbox() {}

    public static void accept(BeyonderSyncPayload payload) {
        BEYONDERS.put(payload.entityId(), payload);
    }

    public static void accept(SpiritMarkerPayload payload) {
        MARKERS.add(payload);
    }

    public static void accept(PotionVisionPayload payload) {
        VISIONS.add(payload);
    }

    public static void accept(CharacterSyncPayload payload) {
        CHARACTERS.put(payload.entityId(), payload);
        characterVersion++;
    }

    /** A player's character as last told by the server, or null if unknown. */
    public static CharacterSyncPayload character(int entityId) {
        return CHARACTERS.get(entityId);
    }

    /** Goes up whenever any character changes (the skin cache watches it). */
    public static int characterVersion() {
        return characterVersion;
    }

    public static void accept(BrewStartPayload payload) {
        BREWS.add(payload);
    }

    public static BrewStartPayload pollBrew() {
        return BREWS.poll();
    }

    public static PotionVisionPayload pollVision() {
        return VISIONS.poll();
    }

    /** The last known state of an entity, or null if it is not a known Beyonder. */
    public static BeyonderSyncPayload get(int entityId) {
        return BEYONDERS.get(entityId);
    }

    public static SpiritMarkerPayload pollMarker() {
        return MARKERS.poll();
    }

    public static void clear() {
        BEYONDERS.clear();
        MARKERS.clear();
        VISIONS.clear();
        BREWS.clear();
        CHARACTERS.clear();
        characterVersion++;
    }
}
