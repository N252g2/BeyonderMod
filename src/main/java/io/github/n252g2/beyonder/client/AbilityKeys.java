package io.github.n252g2.beyonder.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.n252g2.beyonder.core.Ability;
import io.github.n252g2.beyonder.net.BeyonderSyncPayload;
import io.github.n252g2.beyonder.net.ClientInbox;
import io.github.n252g2.beyonder.net.UseAbilityPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Hold G for the ability wheel; R uses the chosen ability. The server decides whether it works. */
public final class AbilityKeys {
    public static final KeyMapping USE = new KeyMapping(
            "key.beyonder.use_ability", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.beyonder");
    public static final KeyMapping NEXT = new KeyMapping(
            "key.beyonder.next_ability", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.beyonder");

    private static Ability selected;
    private static long readyAt; // client-side guess of the cooldown, for the HUD

    private AbilityKeys() {}

    /** Abilities this player can use right now (creative players get all of them, for testing). */
    public static List<Ability> available(Player player) {
        if (player.isCreative()) {
            return List.of(Ability.values());
        }
        BeyonderSyncPayload self = ClientInbox.get(player.getId());
        return self == null ? List.of() : Ability.availableFor(self.pathway(), self.sequence());
    }

    public static Ability current(Player player) {
        List<Ability> list = available(player);
        if (list.isEmpty()) {
            return null;
        }
        return selected != null && list.contains(selected) ? selected : list.get(0);
    }

    /** The wheel's slots: Spirit Vision for Seers, then every ability you can use. */
    static List<AbilityWheelScreen.WheelEntry> wheelEntries(Player player) {
        List<AbilityWheelScreen.WheelEntry> entries = new ArrayList<>();
        BeyonderSyncPayload self = ClientInbox.get(player.getId());
        if (player.isCreative() || (self != null && "seer".equals(self.pathway()))) {
            entries.add(new AbilityWheelScreen.WheelEntry("Spirit Vision",
                    SpiritVision.isActive() ? "close" : "open", null, true));
        }
        for (Ability a : available(player)) {
            entries.add(new AbilityWheelScreen.WheelEntry(a.displayName(), Math.round(a.cost()) + " Spirituality", a, false));
        }
        return entries;
    }

    static void choose(AbilityWheelScreen.WheelEntry entry) {
        Minecraft mc = Minecraft.getInstance();
        if (entry.spiritVision()) {
            SpiritVision.toggle(mc);
            return;
        }
        selected = entry.ability();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(entry.name() + " ready. Press "
                    + USE.getTranslatedKeyMessage().getString() + " to use it.").withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** 0 when ready, else the fraction of the cooldown left. */
    public static float cooldownLeft(Minecraft mc, Ability ability) {
        if (mc.level == null || ability == null) {
            return 0.0F;
        }
        long left = readyAt - mc.level.getGameTime();
        return left <= 0 ? 0.0F : Math.min(1.0F, left / (float) ability.cooldownTicks());
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        while (NEXT.consumeClick()) {
            if (player == null || mc.screen != null) {
                continue;
            }
            List<AbilityWheelScreen.WheelEntry> entries = wheelEntries(player);
            if (entries.isEmpty()) {
                player.displayClientMessage(Component.literal("You have no powers yet. Drink a Seer Potion.").withStyle(ChatFormatting.GRAY), true);
                continue;
            }
            mc.setScreen(new AbilityWheelScreen(entries));
        }
        while (USE.consumeClick()) {
            if (player == null || mc.level == null) {
                continue;
            }
            if (GunClient.isHoldingGun()) {
                GunClient.tryReload(mc, mc.player);
                continue;
            }
            Ability ability = current(player);
            if (ability == null) {
                player.displayClientMessage(Component.literal("You have no abilities yet. Advance to Sequence 8.").withStyle(ChatFormatting.GRAY), true);
                continue;
            }
            if (mc.level.getGameTime() < readyAt) {
                continue;
            }
            readyAt = mc.level.getGameTime() + ability.cooldownTicks();
            PacketDistributor.sendToServer(new UseAbilityPayload(ability.id()));
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }
}
