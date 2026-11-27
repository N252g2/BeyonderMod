package io.github.n252g2.beyonder.client;

import io.github.n252g2.beyonder.core.Equipment;
import java.util.Set;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Beyonder's HUD along the bottom of the screen, in the inventory's dark-and-brass style:
 * <ul>
 *   <li>the hotbar, where vanilla's was (it dims while weapons are drawn, since the hand holds them);</li>
 *   <li>on its left the arms: main weapon and sidearm, lit gold while drawn;</li>
 *   <li>on its right the potion belt with its keys;</li>
 *   <li>above it slim bars: health (with absorption and a fading trail when hurt) and armour on the
 *       left, hunger and air on the right, experience in a line between them and the hotbar.</li>
 * </ul>
 */
public final class BeyonderHotbar {
    private static final Set<ResourceLocation> REPLACED = Set.of(VanillaGuiLayers.HOTBAR, VanillaGuiLayers.PLAYER_HEALTH,
            VanillaGuiLayers.ARMOR_LEVEL, VanillaGuiLayers.FOOD_LEVEL, VanillaGuiLayers.AIR_LEVEL,
            VanillaGuiLayers.EXPERIENCE_BAR, VanillaGuiLayers.EXPERIENCE_LEVEL);

    private static final int PANEL = 0xE0141019, BRASS = 0xFF8A7140, GOLD = 0xFFD8B25A, TEXT = 0xFFE6DCC6,
            MUTED = 0xFF9A9080, CELL = 0xFF1E1924, CELL_DARK = 0xFF0C0A10, TRACK = 0xFF2A2430;
    private static final int HEALTH = 0xFFB3202C, HEALTH_LIGHT = 0xFFE0504A, POISON = 0xFF6E9A2E, WITHER = 0xFF4A3A40,
            FROZEN = 0xFF9CC8E0, ABSORB = 0xFFE8C04A, TRAIL = 0xFFF0E0D0, ARMOUR = 0xFF9AA4B0, FOOD = 0xFFC88A3A,
            FOOD_SICK = 0xFF7E8A3A, AIR = 0xFF6AA6E0, XP = 0xFF8ACB4A;

    private static float shownHealth = -1.0F;   // trails behind the real value after a hit
    private static long lastHurt;

    private BeyonderHotbar() {}

    static boolean active(Minecraft mc) {
        return ClientConfig.BEYONDER_HUD.get() && mc.player != null && !mc.player.isSpectator() && mc.gameMode != null;
    }

    /** Vanilla's hotbar and status bars step aside for ours. */
    static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (REPLACED.contains(event.getName()) && active(Minecraft.getInstance())) {
            event.setCanceled(true);
        }
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !active(mc)) {
            return;
        }
        LocalPlayer player = mc.player;
        Font font = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        int x0 = w / 2 - 91, y0 = h - 23;
        boolean drawn = Equipment.isDrawn(player);

        hotbar(g, font, player, x0, y0, drawn);
        arms(g, font, player, x0 - 54, y0, drawn);
        belt(g, font, player, x0 + 186, y0);
        if (mc.gameMode.canHurtPlayer()) {
            bars(g, font, mc, player, x0, y0);
        }
    }

    // ------------------------------------------------------------------ panels

    private static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int border) {
        g.fill(x0, y0, x1, y1, PANEL);
        g.fill(x0, y0, x1, y0 + 1, border);
        g.fill(x0, y1 - 1, x1, y1, border);
        g.fill(x0, y0, x0 + 1, y1, border);
        g.fill(x1 - 1, y0, x1, y1, border);
    }

    private static void cell(GuiGraphics g, int x, int y, int size) {
        g.fill(x, y, x + size, y + size, CELL_DARK);
        g.fill(x + 1, y + 1, x + size, y + size, CELL);
    }

    private static void item(GuiGraphics g, Font font, LocalPlayer player, ItemStack stack, int x, int y, int seed) {
        if (!stack.isEmpty()) {
            g.renderItem(player, stack, x, y, seed);
            g.renderItemDecorations(font, stack, x, y);
        }
    }

    private static void hotbar(GuiGraphics g, Font font, LocalPlayer player, int x0, int y0, boolean drawn) {
        frame(g, x0, y0, x0 + 182, y0 + 22, BRASS);
        int selected = player.getInventory().selected;
        for (int i = 0; i < 9; i++) {
            int x = x0 + 1 + i * 20;
            cell(g, x + 1, y0 + 2, 18);
        }
        if (!drawn) {
            int x = x0 + i(selected);
            g.fill(x - 1, y0 - 1, x + 21, y0, GOLD);
            g.fill(x - 1, y0 + 22, x + 21, y0 + 23, GOLD);
            g.fill(x - 1, y0, x, y0 + 22, GOLD);
            g.fill(x + 20, y0, x + 21, y0 + 22, GOLD);
        }
        for (int i = 0; i < 9; i++) {
            item(g, font, player, player.getInventory().items.get(i), x0 + 3 + i * 20, y0 + 3, i + 1);
        }
        if (drawn) {
            g.fill(x0 + 1, y0 + 1, x0 + 181, y0 + 21, 0x70100C16); // the hand is busy with the weapons
        }
    }

    private static int i(int slot) {
        return 1 + slot * 20;
    }

    private static void arms(GuiGraphics g, Font font, LocalPlayer player, int x, int y0, boolean drawn) {
        frame(g, x, y0, x + 50, y0 + 22, drawn ? GOLD : BRASS);
        cell(g, x + 3, y0 + 2, 18);
        cell(g, x + 28, y0 + 2, 18);
        item(g, font, player, Equipment.item(player, Equipment.Slot.WEAPON), x + 4, y0 + 3, 100);
        ItemStack side = player.getOffhandItem();
        item(g, font, player, side, x + 29, y0 + 3, 101);
        if (!drawn) {
            g.fill(x + 3, y0 + 2, x + 47, y0 + 20, 0x50100C16);
        }
        // the attack-strength meter, when it's set to show by the hotbar
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            float strength = player.getAttackStrengthScale(0.0F);
            if (strength < 1.0F) {
                g.fill(x + 3, y0 + 20, x + 47, y0 + 21, TRACK);
                g.fill(x + 3, y0 + 20, x + 3 + (int) (44 * strength), y0 + 21, GOLD);
            }
        }
        key(g, font, EquipmentClient.DRAW.getTranslatedKeyMessage().getString(), x + 1, y0 - 7, drawn ? GOLD : MUTED);
    }

    private static void belt(GuiGraphics g, Font font, LocalPlayer player, int x, int y0) {
        frame(g, x, y0, x + 62, y0 + 22, BRASS);
        Equipment.Slot[] slots = Equipment.Slot.BELTS;
        for (int i = 0; i < slots.length; i++) {
            int cx = x + 2 + i * 20;
            cell(g, cx, y0 + 2, 18);
            ItemStack stack = Equipment.item(player, slots[i]);
            item(g, font, player, stack, cx + 1, y0 + 3, 200 + i);
            if (!stack.isEmpty()) {
                float cd = player.getCooldowns().getCooldownPercent(stack.getItem(), 0.0F);
                if (cd > 0.0F) {
                    int hgt = Mth.ceil(16 * cd);
                    g.fill(cx + 1, y0 + 3 + 16 - hgt, cx + 17, y0 + 19, 0x80FFFFFF);
                }
            }
            key(g, font, EquipmentClient.BELT[i].getTranslatedKeyMessage().getString(), cx + 1, y0 - 7, MUTED);
        }
    }

    /** A small key letter above a slot (at three-quarter size). */
    private static void key(GuiGraphics g, Font font, String key, int x, int y, int colour) {
        if (key.length() > 3) {
            key = key.substring(0, 3);
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 200.0F);
        g.pose().scale(0.75F, 0.75F, 1.0F);
        g.drawString(font, key, 0, 0, colour, true);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ bars

    private static void bars(GuiGraphics g, Font font, Minecraft mc, LocalPlayer player, int x0, int y0) {
        int barY = y0 - 12;
        int leftW = 81, rightX = x0 + 101;

        // experience: a line just above the hotbar, the level between the bars
        if (mc.gameMode.hasExperience()) {
            g.fill(x0, y0 - 4, x0 + 182, y0 - 2, TRACK);
            g.fill(x0, y0 - 4, x0 + (int) (182 * player.experienceProgress), y0 - 2, XP);
            if (player.experienceLevel > 0) {
                String lvl = String.valueOf(player.experienceLevel);
                g.drawString(font, lvl, x0 + 91 - font.width(lvl) / 2, barY - 1, XP, true);
            }
        }

        // health, with absorption in gold and a pale trail that drains after a hit
        float health = player.getHealth(), max = player.getMaxHealth(), absorb = player.getAbsorptionAmount();
        long now = player.level().getGameTime();
        if (shownHealth < 0.0F || health > shownHealth) {
            shownHealth = health;
        } else if (health < shownHealth) {
            if (player.hurtTime == player.hurtDuration - 1) {
                lastHurt = now;
            }
            if (now - lastHurt > 10) {
                shownHealth = Math.max(health, shownHealth - max * 0.02F);
            }
        }
        float total = Math.max(max, health + absorb);
        int colour = player.hasEffect(MobEffects.WITHER) ? WITHER : player.hasEffect(MobEffects.POISON) ? POISON
                : player.isFullyFrozen() ? FROZEN : HEALTH;
        boolean low = health <= max * 0.25F;
        if (low && (now / 5) % 2 == 0) {
            colour = HEALTH_LIGHT;
        }
        frame(g, x0 - 1, barY - 1, x0 + leftW + 1, barY + 6, low ? HEALTH : BRASS);
        g.fill(x0, barY, x0 + leftW, barY + 5, TRACK);
        g.fill(x0, barY, x0 + (int) (leftW * shownHealth / total), barY + 5, TRAIL);
        g.fill(x0, barY, x0 + (int) (leftW * health / total), barY + 5, colour);
        g.fill(x0, barY, x0 + (int) (leftW * health / total), barY + 1, 0x40FFFFFF);
        if (absorb > 0.0F) {
            int a0 = x0 + (int) (leftW * health / total);
            g.fill(a0, barY, x0 + (int) (leftW * (health + absorb) / total), barY + 5, ABSORB);
        }
        String hp = String.valueOf(Mth.ceil(health + absorb));
        g.drawString(font, hp, x0 - 3 - font.width(hp), barY - 1, low ? HEALTH_LIGHT : TEXT, true);

        // armour, above health, only when worn
        int armour = player.getArmorValue();
        if (armour > 0) {
            int ay = barY - 7;
            g.fill(x0, ay, x0 + leftW, ay + 3, TRACK);
            g.fill(x0, ay, x0 + (int) (leftW * Math.min(1.0F, armour / 20.0F)), ay + 3, ARMOUR);
            key(g, font, String.valueOf(armour), x0 - 3 - (int) (font.width(String.valueOf(armour)) * 0.75F), ay - 2, ARMOUR);
        }

        // hunger, unless riding something with its own health (vanilla shows the mount's there)
        boolean mounted = player.getVehicle() instanceof LivingEntity;
        if (!mounted) {
            int food = player.getFoodData().getFoodLevel();
            float sat = player.getFoodData().getSaturationLevel();
            int fc = player.hasEffect(MobEffects.HUNGER) ? FOOD_SICK : FOOD;
            frame(g, rightX - 1, barY - 1, rightX + leftW + 1, barY + 6, food <= 6 ? HEALTH : BRASS);
            g.fill(rightX, barY, rightX + leftW, barY + 5, TRACK);
            // the bar empties from the right, mirroring health
            int fw = (int) (leftW * food / 20.0F);
            g.fill(rightX + leftW - fw, barY, rightX + leftW, barY + 5, fc);
            int sw = (int) (leftW * Math.min(food, sat) / 20.0F);
            g.fill(rightX + leftW - sw, barY, rightX + leftW, barY + 1, 0xFFF0D090);
            g.drawString(font, String.valueOf(food), rightX + leftW + 3, barY - 1, food <= 6 ? HEALTH_LIGHT : TEXT, true);
        }

        // air, above hunger, while underwater or catching breath
        int air = player.getAirSupply(), maxAir = player.getMaxAirSupply();
        if (player.isEyeInFluid(FluidTags.WATER) || air < maxAir) {
            int ay = barY - 7;
            g.fill(rightX, ay, rightX + leftW, ay + 3, TRACK);
            int aw = (int) (leftW * Math.max(0, air) / (float) maxAir);
            g.fill(rightX + leftW - aw, ay, rightX + leftW, ay + 3, AIR);
        }
    }
}
