package io.github.n252g2.beyonder.net;

import io.github.n252g2.beyonder.core.Abilities;
import io.github.n252g2.beyonder.core.Ability;
import io.github.n252g2.beyonder.brewing.Brewing;
import io.github.n252g2.beyonder.core.Beyonders;
import io.github.n252g2.beyonder.item.RevolverItem;
import net.minecraft.world.item.ItemStack;
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
        registrar.playToClient(PotionVisionPayload.TYPE, PotionVisionPayload.STREAM_CODEC,
                (payload, context) -> ClientInbox.accept(payload));
        registrar.playToClient(CharacterSyncPayload.TYPE, CharacterSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientInbox.accept(payload));
        registrar.playToServer(CreateCharacterPayload.TYPE, CreateCharacterPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        io.github.n252g2.beyonder.core.Characters.create(player, payload);
                    }
                }));
        registrar.playToClient(BrewStartPayload.TYPE, BrewStartPayload.STREAM_CODEC,
                (payload, context) -> ClientInbox.accept(payload));
        registrar.playToServer(BrewResultPayload.TYPE, BrewResultPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Brewing.finish(player, payload.nonce(), payload.quality());
                    }
                }));
        registrar.playToServer(SpiritVisionPayload.TYPE, SpiritVisionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Beyonders.setSpiritVision(player, payload.active());
                    }
                }));
        registrar.playToServer(UseAbilityPayload.TYPE, UseAbilityPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    Ability ability = Ability.byId(payload.ability());
                    if (ability != null && context.player() instanceof ServerPlayer player) {
                        Abilities.use(player, ability);
                    }
                }));
        registrar.playToClient(EquipStatePayload.TYPE, EquipStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    ClientInbox.accept(payload);
                    // our own drawn state drives which slot is the main hand on this client
                    net.minecraft.world.entity.player.Player self = context.player();
                    if (self != null && self.getId() == payload.entityId()) {
                        var d = io.github.n252g2.beyonder.core.Equipment.get(self);
                        if (d.drawn() != payload.drawn()) {
                            d.setDrawn(payload.drawn(), self.getInventory().selected);
                        }
                        d.setClothesOverArmour(payload.clothesOverArmour());
                    }
                }));
        registrar.playToClient(PlayerAnimPayload.TYPE, PlayerAnimPayload.STREAM_CODEC,
                (payload, context) -> ClientInbox.accept(payload));
        registrar.playToServer(EquipActionPayload.TYPE, EquipActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        io.github.n252g2.beyonder.core.EquipmentRules.action(player, payload.action());
                    }
                }));
        registrar.playToServer(GunActionPayload.TYPE, GunActionPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ItemStack gun = player.getMainHandItem();
                        // guns only work drawn from the weapon slot
                        if (gun.getItem() instanceof RevolverItem && io.github.n252g2.beyonder.core.Equipment.isDrawn(player)) {
                            if (payload.action() == GunActionPayload.FIRE) {
                                RevolverItem.fire(player, gun);
                            } else if (payload.action() == GunActionPayload.RELOAD) {
                                RevolverItem.reload(player, gun);
                            }
                        }
                    }
                }));
    }
}
