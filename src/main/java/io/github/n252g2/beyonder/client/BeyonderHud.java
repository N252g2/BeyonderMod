package io.github.n252g2.beyonder.client;

import io.github.n252g2.beyonder.core.Ability;
import io.github.n252g2.beyonder.core.Pathway;
import io.github.n252g2.beyonder.net.BeyonderSyncPayload;
import io.github.n252g2.beyonder.net.ClientInbox;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * The Beyonder panel, top left: pathway and Sequence, Spirituality, digestion, and loss of control
 * once it starts to creep in. Dark glass with brass edges, like the launcher.
 */
public final class BeyonderHud {
    // the same palette as the Beyonder hotbar and inventory
    private static final int BRASS = 0xFF8A7140;
    private static final int GOLD = 0xFFD8B25A;
    private static final int PANEL = 0xE0141019;
    private static final int CELL = 0xFF1E1924;
    private static final int CELL_DARK = 0xFF0C0A10;
    private static final int TRACK = 0xFF2A2430;
    private static final int SPIRITUALITY = 0xFF8B6CD8;
    private static final int DIGESTION = 0xFFC99A2E;
    private static final int LOSS = 0xFFB3202C;
    private static final int TEXT = 0xFFE6DCC6;
    private static final int MUTED = 0xFF9A9080;

    private BeyonderHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        PotionVision.renderOverlay(g, delta);
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        BeyonderSyncPayload self = ClientInbox.get(mc.player.getId());
        if (self == null || "none".equals(self.pathway())) {
            return;
        }
        Pathway pathway = Pathway.byId(self.pathway());
        Font font = mc.font;

        int x = 6;
        int y = 6;
        int width = 132;
        boolean showLoss = self.lossOfControl() > 0.5F;
        Ability ability = AbilityKeys.current(mc.player);
        int height = 20 + 17 * 2 + (showLoss ? 17 : 0) + (ability != null ? 22 : 0) + 3;

        frame(g, x, y, x + width, y + height, BRASS);

        // header: the Sequence's name, and its number on a brass tag
        String title = pathway.sequenceName(self.sequence()).toUpperCase();
        g.drawString(font, title, x + 6, y + 6, GOLD, true);
        String seq = String.valueOf(self.sequence());
        int tagW = font.width(seq) + 8;
        int tx = x + width - 5 - tagW;
        frame(g, tx, y + 4, tx + tagW, y + 15, GOLD);
        g.drawString(font, seq, tx + 4, y + 6, GOLD, false);
        g.fill(x + 4, y + 18, x + width - 4, y + 19, 0x808A7140);

        int row = y + 22;
        bar(g, font, x + 6, row, width - 12, "Spirituality",
                Mth.floor(self.spirituality()) + "/" + Mth.floor(self.maxSpirituality()),
                self.maxSpirituality() > 0 ? self.spirituality() / self.maxSpirituality() : 0, SPIRITUALITY);
        row += 17;
        boolean digested = self.digestion() >= 100.0F;
        bar(g, font, x + 6, row, width - 12, digested ? "Digested" : "Digestion", Mth.floor(self.digestion()) + "%",
                self.digestion() / 100.0F, digested ? GOLD : DIGESTION);
        if (showLoss) {
            row += 17;
            int colour = LOSS;
            if (self.lossOfControl() >= 50.0F && mc.level != null) {
                // Pulse as it grows dangerous.
                float pulse = 0.5F + 0.5F * Mth.sin((mc.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false)) * 0.35F);
                colour = lerpColour(LOSS, 0xFFFF5060, pulse);
            }
            bar(g, font, x + 6, row, width - 12, "Loss of control", Mth.floor(self.lossOfControl()) + "%",
                    self.lossOfControl() / 100.0F, colour);
        }
        if (ability != null) {
            row += 18;
            // the chosen ability as a slot: its key in a small cell, the name, the cost, a cooldown sweep
            float cooldown = AbilityKeys.cooldownLeft(mc, ability);
            int cx = x + 5, cw = width - 10;
            g.fill(cx, row, cx + cw, row + 18, CELL_DARK);
            g.fill(cx + 1, row + 1, cx + cw, row + 18, CELL);
            if (cooldown > 0) {
                g.fill(cx + 1, row + 1, cx + 1 + Mth.floor((cw - 1) * cooldown), row + 18, 0x60000000);
            }
            String keyName = AbilityKeys.USE.getTranslatedKeyMessage().getString();
            if (keyName.length() > 3) {
                keyName = keyName.substring(0, 3);
            }
            int kw = Math.max(12, font.width(keyName) + 6);
            frame(g, cx + 3, row + 3, cx + 3 + kw, row + 15, cooldown > 0 ? BRASS : GOLD);
            g.drawString(font, keyName, cx + 3 + (kw - font.width(keyName)) / 2, row + 5, cooldown > 0 ? MUTED : GOLD, false);
            g.drawString(font, ability.displayName(), cx + kw + 7, row + 5, cooldown > 0 ? MUTED : TEXT, false);
            String cost = Mth.floor(ability.cost()) + " sp";
            g.drawString(font, cost, cx + cw - 4 - font.width(cost), row + 5, MUTED, false);
        }
    }

    private static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int border) {
        g.fill(x0, y0, x1, y1, PANEL);
        g.fill(x0, y0, x1, y0 + 1, border);
        g.fill(x0, y1 - 1, x1, y1, border);
        g.fill(x0, y0, x0 + 1, y1, border);
        g.fill(x1 - 1, y0, x1, y1, border);
    }

    /** A label and value over a slim framed bar, like the health and hunger bars by the hotbar. */
    private static void bar(GuiGraphics g, Font font, int x, int y, int width, String label, String value,
                            float fraction, int colour) {
        g.drawString(font, label, x, y, TEXT, false);
        g.drawString(font, value, x + width - font.width(value), y, MUTED, false);
        int top = y + 9;
        frame(g, x - 1, top - 1, x + width + 1, top + 5, BRASS);
        g.fill(x, top, x + width, top + 4, TRACK);
        int filled = Mth.floor(width * Mth.clamp(fraction, 0.0F, 1.0F));
        if (filled > 0) {
            g.fill(x, top, x + filled, top + 4, colour);
            g.fill(x, top, x + filled, top + 1, 0x40FFFFFF);
        }
    }

    private static int lerpColour(int a, int b, float t) {
        int r = Mth.floor(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int gr = Mth.floor(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Mth.floor(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return 0xFF000000 | (r << 16) | (gr << 8) | bl;
    }
}
