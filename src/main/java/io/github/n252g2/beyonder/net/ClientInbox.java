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
    }
}
