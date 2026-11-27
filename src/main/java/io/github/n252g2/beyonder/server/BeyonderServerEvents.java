package io.github.n252g2.beyonder.server;

import io.github.n252g2.beyonder.core.Abilities;
import io.github.n252g2.beyonder.core.Beyonders;
import io.github.n252g2.beyonder.core.Characters;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
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
        NeoForge.EVENT_BUS.addListener(BeyonderServerEvents::onNameFormat);
        NeoForge.EVENT_BUS.addListener(BeyonderServerEvents::onTabListName);
        NeoForge.EVENT_BUS.addListener(BeyonderServerEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(BeyonderServerEvents::onAttack);
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, BeyonderServerEvents::holdStill);
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem.class, BeyonderServerEvents::holdStill);
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.LeftClickBlock.class, BeyonderServerEvents::holdStill);
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteract.class, BeyonderServerEvents::holdStill);
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteractSpecific.class, BeyonderServerEvents::holdStill);
    }

    // ------------------------------------------------------------------ characters

    /** Chat and other server-side names use the character's name. */
    private static void onNameFormat(PlayerEvent.NameFormat event) {
        if (!event.getEntity().level().isClientSide) {
            event.setDisplayname(Characters.displayName(event.getEntity(), event.getDisplayname()));
        }
    }

    /** The tab list shows the character's name with the account name beside it. */
    private static void onTabListName(PlayerEvent.TabListNameFormat event) {
        var player = event.getEntity();
        if (Characters.created(player)) {
            event.setDisplayName(Component.literal(Characters.get(player).name())
                    .append(Component.literal(" (" + player.getGameProfile().getName() + ")").withStyle(ChatFormatting.DARK_GRAY)));
        }
    }

    /** A player still making their character can't be hurt. */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !Characters.created(player)) {
            event.setCanceled(true);
        }
    }

    private static void onAttack(AttackEntityEvent event) {
        if (!event.getEntity().level().isClientSide && !Characters.created(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Nothing can be touched until the character exists. */
    private static void holdStill(PlayerInteractEvent event) {
        if (!event.getLevel().isClientSide && event instanceof net.neoforged.bus.api.ICancellableEvent c
                && !Characters.created(event.getEntity())) {
            c.setCanceled(true);
        }
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Characters.tick(player);
            Beyonders.tickPending(player);
            if (player.tickCount % 20 == 0) {
                Beyonders.secondTick(player);
            }
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Beyonders.sync(player);
            Characters.onLogin(player);
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Beyonders.flushPending(player);
        }
        Beyonders.forget(event.getEntity());
        Characters.onLogout(event.getEntity());
        Abilities.forget(event.getEntity());
        io.github.n252g2.beyonder.brewing.Brewing.forget(event.getEntity());
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Characters.applyPerks(player);
            Characters.sync(player);
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
            PacketDistributor.sendToPlayer(viewer, Characters.payload(target));
        }
    }
}
