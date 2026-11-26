package io.github.n252g2.beyonder.client;

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
    private static final int BRASS = 0xFF8A7140;
    private static final int GOLD = 0xFFD8B25A;
    private static final int PANEL = 0xA0100E14;
    private static final int TRACK = 0xFF1B1622;
    private static final int SPIRITUALITY = 0xFF8B6CD8;
    private static final int DIGESTION = 0xFFC99A2E;
    private static final int LOSS = 0xFFB3202C;
    private static final int TEXT = 0xFFE6DCC6;
    private static final int MUTED = 0xFF9A9080;

    private BeyonderHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
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
        int width = 128;
        boolean showLoss = self.lossOfControl() > 0.5F;
        int height = showLoss ? 64 : 48;

        // Panel with a brass rule along the top and bottom.
        g.fill(x, y, x + width, y + height, PANEL);
        g.fill(x, y, x + width, y + 1, BRASS);
        g.fill(x, y + height - 1, x + width, y + height, BRASS);

        String title = pathway.sequenceName(self.sequence()).toUpperCase();
        g.drawString(font, title, x + 6, y + 5, GOLD, false);
        String seq = "Seq. " + self.sequence();
        g.drawString(font, seq, x + width - 6 - font.width(seq), y + 5, MUTED, false);

        int row = y + 18;
        bar(g, font, x + 6, row, width - 12, "Spirituality",
                Mth.floor(self.spirituality()) + "/" + Mth.floor(self.maxSpirituality()),
                self.maxSpirituality() > 0 ? self.spirituality() / self.maxSpirituality() : 0, SPIRITUALITY);
        row += 15;
        bar(g, font, x + 6, row, width - 12, "Digestion", Mth.floor(self.digestion()) + "%",
                self.digestion() / 100.0F, self.digestion() >= 100.0F ? GOLD : DIGESTION);
        if (showLoss) {
            row += 15;
            int colour = LOSS;
            if (self.lossOfControl() >= 50.0F && mc.level != null) {
                // Pulse as it grows dangerous.
                float pulse = 0.5F + 0.5F * Mth.sin((mc.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false)) * 0.35F);
                colour = lerpColour(LOSS, 0xFFFF5060, pulse);
            }
            bar(g, font, x + 6, row, width - 12, "Loss of control", Mth.floor(self.lossOfControl()) + "%",
                    self.lossOfControl() / 100.0F, colour);
        }
    }

    private static void bar(GuiGraphics g, Font font, int x, int y, int width, String label, String value,
                            float fraction, int colour) {
        g.drawString(font, label, x, y, TEXT, false);
        g.drawString(font, value, x + width - font.width(value), y, MUTED, false);
        int top = y + 9;
        g.fill(x, top, x + width, top + 3, TRACK);
        int filled = Mth.floor(width * Mth.clamp(fraction, 0.0F, 1.0F));
        if (filled > 0) {
            g.fill(x, top, x + filled, top + 3, colour);
        }
    }

    private static int lerpColour(int a, int b, float t) {
        int r = Mth.floor(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int gr = Mth.floor(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Mth.floor(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return 0xFF000000 | (r << 16) | (gr << 8) | bl;
    }
}
