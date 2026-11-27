package io.github.n252g2.beyonder.client;

import io.github.n252g2.beyonder.content.BeyonderContent;
import io.github.n252g2.beyonder.core.Appearance;
import io.github.n252g2.beyonder.core.Background;
import io.github.n252g2.beyonder.core.Characters;
import io.github.n252g2.beyonder.net.CreateCharacterPayload;
import io.github.n252g2.beyonder.net.EquipActionPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A scripted run for CI only (with -Dbeyonder.selftest=true): makes a character in a singleplayer
 * world, then draws, swaps, attacks, reloads and sheathes while taking screenshots, and quits.
 * It never runs in normal play.
 */
public final class SelfTest {
    public static final boolean ENABLED = Boolean.getBoolean("beyonder.selftest");
    private static int t = -1;

    private SelfTest() {}

    private static int idle;

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (mc.player == null || mc.level == null || server == null) {
            if (++idle % 200 == 0) {
                org.slf4j.LoggerFactory.getLogger("beyonder-selftest").info("waiting for the world (screen: {})",
                        mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
            }
            return;
        }
        t++;
        if (t % 20 == 0) {
            org.slf4j.LoggerFactory.getLogger("beyonder-selftest").info("self-test tick {}", t);
        }
        switch (t) {
            case 40 -> server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (sp == null) {
                    return;
                }
                int[] look = Appearance.defaults();
                Characters.create(sp, new CreateCharacterPayload("Klein Test", Background.CONSTABLE.id(), look));
                sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(BeyonderContent.LONGSWORD.get()));
                var src = server.createCommandSourceStack().withSuppressedOutput();
                server.getCommands().performPrefixedCommand(src, "time set noon");
                server.getCommands().performPrefixedCommand(src, "weather clear");
                server.getCommands().performPrefixedCommand(src, "gamemode survival @a");
                server.getCommands().performPrefixedCommand(src, "gamerule doDaylightCycle false");
                sp.setYRot(0.0F);
                sp.setXRot(0.0F);
                server.getCommands().performPrefixedCommand(src, "item replace entity @a armor.chest with minecraft:iron_chestplate");
                var eq = io.github.n252g2.beyonder.core.Equipment.get(sp).items();
                eq.set(io.github.n252g2.beyonder.core.Equipment.Slot.BELT_1.ordinal(), new ItemStack(net.minecraft.world.item.Items.BREAD, 5));
                eq.set(io.github.n252g2.beyonder.core.Equipment.Slot.BELT_3.ordinal(), new ItemStack(net.minecraft.world.item.Items.HONEY_BOTTLE, 2));
                sp.giveExperienceLevels(7);
                sp.giveExperiencePoints(20);
            });
            case 80 -> {
                mc.options.hideGui = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            case 90 -> shot(mc, "01_sheathed_front");
            case 92 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 100 -> shot(mc, "02_sheathed_back");
            case 102 -> {
                mc.options.hideGui = false;
                mc.setScreen(new BeyonderInventoryScreen(mc.player));
            }
            case 112 -> shot(mc, "03_inventory");
            case 114 -> {
                mc.setScreen(null);
                mc.options.hideGui = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            case 120 -> EquipmentClient.toggleDraw(mc.player, io.github.n252g2.beyonder.core.Equipment.get(mc.player));
            case 123 -> shot(mc, "04_drawing_gun");
            case 135 -> shot(mc, "05_gun_stance");
            case 137 -> GunClient.tryFire(mc, mc.player);
            case 138 -> shot(mc, "06_recoil");
            case 146 -> GunClient.tryReload(mc, mc.player);
            case 160 -> shot(mc, "07_reload");
            case 190 -> {
                PlayerAnimator.start(mc.player, io.github.n252g2.beyonder.core.PlayerAnim.SWAP, 0);
                PacketDistributor.sendToServer(new EquipActionPayload(EquipActionPayload.SWAP));
            }
            case 205 -> shot(mc, "08_sword_stance");
            case 210, 222, 234 -> mc.player.swing(InteractionHand.MAIN_HAND);
            case 213 -> shot(mc, "09_slash_diagonal");
            case 225 -> shot(mc, "10_slash_sweep");
            case 237 -> shot(mc, "11_thrust");
            case 250 -> EquipmentClient.toggleDraw(mc.player, io.github.n252g2.beyonder.core.Equipment.get(mc.player));
            case 253 -> shot(mc, "12_sheathing");
            case 268 -> shot(mc, "13_sheathed_sword_on_hip");
            case 270 -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            case 278 -> shot(mc, "14_sheathed_back_view");
            case 280 -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = false; // F1 also hides the hand
            }
            case 284 -> EquipmentClient.toggleDraw(mc.player, io.github.n252g2.beyonder.core.Equipment.get(mc.player));
            case 289 -> shot(mc, "15_first_person_drawing");
            case 300 -> shot(mc, "16_first_person_drawn");
            case 301 -> mc.player.swing(InteractionHand.MAIN_HAND);
            case 303 -> shot(mc, "16b_first_person_swing");
            case 302 -> {
                mc.options.hideGui = false;
                PlayerAnimator.start(mc.player, io.github.n252g2.beyonder.core.PlayerAnim.CAST, io.github.n252g2.beyonder.core.PlayerAnim.CAST_CARD);
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            case 306 -> shot(mc, "17_cast_card");
            case 308 -> server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (sp != null) {
                    sp.hurt(sp.damageSources().generic(), 7.0F);
                    sp.getFoodData().setFoodLevel(13);
                }
            });
            case 310 -> mc.options.setCameraType(CameraType.FIRST_PERSON);
            case 313 -> shot(mc, "18_hud_drawn_after_hit");
            case 316 -> EquipmentClient.toggleDraw(mc.player, io.github.n252g2.beyonder.core.Equipment.get(mc.player));
            case 340 -> shot(mc, "19_hud_sheathed");
            case 350 -> mc.stop();
            default -> {}
        }
    }

    private static void shot(Minecraft mc, String name) {
        org.slf4j.LoggerFactory.getLogger("beyonder-selftest").info("screenshot {}", name);
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> {});
    }
}
