package io.github.n252g2.beyonder.server;

import io.github.n252g2.beyonder.core.Abilities;
import io.github.n252g2.beyonder.core.Beyonders;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Keeps Beyonder state ticking and synced. */
public final class BeyonderServerEvents {
    private BeyonderServerEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(BeyonderServerEvents::onTick);
        NeoForge.EVENT_BUS.addListener(BeyonderServerEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(BeyonderServerEvents::onLogout);
        NeoForge.EVENT_BUS.addListener(BeyonderServerEvents::onRespawn);
        NeoForge.EVENT_BUS.addListener(BeyonderServerEvents::onChangeDimension);
        NeoForge.EVENT_BUS.addListener(BeyonderServerEvents::onStartTracking);
        NeoForge.EVENT_BUS.addListener(BeyonderCommands::register);
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            Beyonders.secondTick(player);
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Beyonders.sync(player);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Beyonders.forget(event.getEntity());
        Abilities.forget(event.getEntity());
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (event.isEndConquered()) {
                Beyonders.sync(player);
            } else {
                Beyonders.afterRespawn(player);
            }
        }
    }

    private static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Beyonders.sync(player);
        }
    }

    /** When someone comes into view, tell the viewer what they are (their halo depends on it). */
    private static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof Player target) {
            PacketDistributor.sendToPlayer(viewer, Beyonders.payload(target));
        }
    }
}
