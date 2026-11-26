package io.github.n252g2.beyonder.net;

import io.github.n252g2.beyonder.core.Beyonders;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Registers Beyonder's packets. */
public final class BeyonderNetwork {
    private BeyonderNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(BeyonderSyncPayload.TYPE, BeyonderSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientInbox.accept(payload));
        registrar.playToClient(SpiritMarkerPayload.TYPE, SpiritMarkerPayload.STREAM_CODEC,
                (payload, context) -> ClientInbox.accept(payload));
        registrar.playToServer(SpiritVisionPayload.TYPE, SpiritVisionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Beyonders.setSpiritVision(player, payload.active());
                    }
                }));
    }
}
