package io.github.n252g2.beyonder.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.math.Axis;
import io.github.n252g2.beyonder.core.Equipment;
import io.github.n252g2.beyonder.core.EquipmentData;
import io.github.n252g2.beyonder.core.PlayerAnim;
import io.github.n252g2.beyonder.net.ClientInbox;
import io.github.n252g2.beyonder.net.EquipActionPayload;
import io.github.n252g2.beyonder.net.EquipStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Keys for drawing, swapping and the belt; the new inventory; weapons in first person. */
public final class EquipmentClient {
    public static final KeyMapping DRAW = new KeyMapping(
            "key.beyonder.draw", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.beyonder");
    public static final KeyMapping SWAP = new KeyMapping(
            "key.beyonder.swap_weapons", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.beyonder");
    public static final KeyMapping[] BELT = {
            new KeyMapping("key.beyonder.belt_1", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, "key.categories.beyonder"),
            new KeyMapping("key.beyonder.belt_2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, "key.categories.beyonder"),
            new KeyMapping("key.beyonder.belt_3", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.beyonder")};

    private static long lastRefusal;

    private EquipmentClient() {}

    // ------------------------------------------------------------------ keys

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        EquipmentData d = Equipment.get(player);
        // Picking another hotbar slot puts the weapons away (the server does the same).
        if (d.drawn() && player.getInventory().selected != d.drawnHotbarSlot()) {
            ItemStack weapon = Equipment.item(player, Equipment.Slot.WEAPON);
            d.setDrawn(false, -1);
            PlayerAnimator.start(player, PlayerAnim.SHEATHE, Equipment.Kind.of(weapon).ordinal());
        }
        boolean free = mc.screen == null && player.isAlive() && !player.isSpectator();
        while (DRAW.consumeClick()) {
            if (free) {
                toggleDraw(player, d);
            }
        }
        while (SWAP.consumeClick()) {
            if (free) {
                if (d.drawn()) {
                    PlayerAnimator.start(player, PlayerAnim.SWAP, 0);
                }
                PacketDistributor.sendToServer(new EquipActionPayload(EquipActionPayload.SWAP));
            }
        }
        for (int i = 0; i < BELT.length; i++) {
            while (BELT[i].consumeClick()) {
                if (free) {
                    PacketDistributor.sendToServer(new EquipActionPayload(EquipActionPayload.BELT_FIRST + i));
                }
            }
        }
    }

    static void toggleDraw(LocalPlayer player, EquipmentData d) {
        ItemStack weapon = Equipment.item(player, Equipment.Slot.WEAPON);
        int kind = Equipment.Kind.of(weapon).ordinal();
        if (d.drawn()) {
            d.setDrawn(false, -1);
            PlayerAnimator.start(player, PlayerAnim.SHEATHE, kind);
        } else {
            if (weapon.isEmpty() && !Equipment.isWeapon(player.getOffhandItem())) {
                player.displayClientMessage(Component.literal("Fists raised. Put a weapon in your weapon slot (E) to draw it.")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            player.stopUsingItem();
            d.setDrawn(true, player.getInventory().selected);
            PlayerAnimator.start(player, PlayerAnim.DRAW, kind);
        }
        PacketDistributor.sendToServer(new EquipActionPayload(EquipActionPayload.TOGGLE_DRAW));
    }

    // ------------------------------------------------------------------ the new inventory

    static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        // creative keeps its own inventory (vanilla's screen hands over to it on opening)
        if (event.getNewScreen() != null && event.getNewScreen().getClass() == InventoryScreen.class && mc.player != null
                && mc.gameMode != null && !mc.gameMode.hasInfiniteItems()) {
            event.setNewScreen(new BeyonderInventoryScreen(mc.player));
        }
    }

    // ------------------------------------------------------------------ weapons only work drawn

    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(event.isAttack() || event.isUseItem())) {
            return;
        }
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!Equipment.mayUse(player, stack)) {
            event.setCanceled(true);
            event.setSwingHand(false);
            long now = System.currentTimeMillis();
            if (now - lastRefusal > 1500 && event.getHand() == InteractionHand.MAIN_HAND) {
                lastRefusal = now;
                player.displayClientMessage(Component.literal("Weapons only work from the weapon slot: equip it (E), then draw it ("
                        + DRAW.getTranslatedKeyMessage().getString() + ").").withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    // ------------------------------------------------------------------ what shows in hand

    static boolean drawnFor(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return true;
        }
        return PlayerAnimator.drawn(player);
    }

    /**
     * Whether an item should be left out of a player's hand: a sheathed sidearm (it's drawn on the
     * body instead), or anything in the hand that is reaching for or putting away a weapon.
     */
    public static boolean hideInHand(LivingEntity entity, ItemStack stack, HumanoidArm arm, float partialTick) {
        if (!(entity instanceof Player player) || stack.isEmpty()) {
            return false;
        }
        boolean mainArm = arm == player.getMainArm();
        if (!mainArm && Equipment.isWeapon(stack) && !PlayerAnimator.drawn(player)) {
            return true;
        }
        float draw = PlayerAnimator.progress(player, PlayerAnim.DRAW, partialTick);
        if (draw >= 0.0F && draw < 0.45F && (mainArm || Equipment.isWeapon(stack))) {
            return true;
        }
        float sheathe = PlayerAnimator.progress(player, PlayerAnim.SHEATHE, partialTick);
        return sheathe >= 0.0F && sheathe < 0.85F && (mainArm || Equipment.isWeapon(stack));
    }

    public static boolean clothesOverArmour(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        if (player.isLocalPlayer()) {
            return Equipment.get(player).clothesOverArmour();
        }
        EquipStatePayload s = ClientInbox.equipment(player.getId());
        return s != null && s.clothesOverArmour();
    }

    /** First person: weapons come up from below as they're drawn and drop away as they're put back. */
    static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        if (hideInHand(player, event.getItemStack(), arm, event.getPartialTick())) {
            event.setCanceled(true);
            return;
        }
        float draw = PlayerAnimator.progress(player, PlayerAnim.DRAW, event.getPartialTick());
        float sheathe = PlayerAnimator.progress(player, PlayerAnim.SHEATHE, event.getPartialTick());
        float swap = PlayerAnimator.progress(player, PlayerAnim.SWAP, event.getPartialTick());
        float drop = 0.0F;
        if (draw >= 0.45F) {
            drop = 1.0F - PlayerAnimator.smooth((draw - 0.45F) / 0.55F);
        } else if (sheathe >= 0.85F) {
            drop = 1.0F - PlayerAnimator.smooth((sheathe - 0.85F) / 0.15F);
        } else if (swap >= 0.0F) {
            drop = (float) Math.sin(swap * Math.PI);
        }
        if (drop > 0.0F) {
            float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            event.getPoseStack().translate(0.0F, -0.7F * drop, 0.15F * drop);
            event.getPoseStack().mulPose(Axis.XP.rotationDegrees(-35.0F * drop));
            event.getPoseStack().mulPose(Axis.ZP.rotationDegrees(side * 12.0F * drop));
        }
    }
}
