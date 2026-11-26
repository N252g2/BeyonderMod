package io.github.n252g2.beyonder.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.n252g2.beyonder.core.Ability;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Hold G: a ring of your powers appears. Point at one with the mouse and let go of G to choose it.
 * The game keeps running underneath.
 */
public class AbilityWheelScreen extends Screen {
    private static final int RING_INNER = 34;
    private static final int RING_OUTER = 92;

    private static final int SLICE = 0xC0141018;
    private static final int SLICE_HOVER = 0xE04A3A1C;
    private static final int EDGE = 0xFF8A7140;
    private static final int GOLD = 0xFFD8B25A;
    private static final int TEXT = 0xFFE6DCC6;
    private static final int MUTED = 0xFF9A9080;

    private final List<WheelEntry> entries;
    private int hovered = -1;

    public AbilityWheelScreen(List<WheelEntry> entries) {
        super(Component.literal("Abilities"));
        this.entries = entries;
    }

    /** One slot on the wheel: an ability, or the Spirit Vision toggle. */
    public record WheelEntry(String name, String detail, Ability ability, boolean spiritVision) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No blur or dimming: the world stays visible behind the wheel.
    }

    @Override
    public void tick() {
        // If G was released while the screen missed the key event, choose and close anyway.
        Minecraft mc = Minecraft.getInstance();
        int key = AbilityKeys.NEXT.getKey().getValue();
        if (AbilityKeys.NEXT.getKey().getType() == InputConstants.Type.KEYSYM
                && !InputConstants.isKeyDown(mc.getWindow().getWindow(), key)) {
            chooseAndClose();
        }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (AbilityKeys.NEXT.matches(keyCode, scanCode)) {
            chooseAndClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        chooseAndClose();
        return true;
    }

    private void chooseAndClose() {
        if (hovered >= 0 && hovered < entries.size()) {
            AbilityKeys.choose(entries.get(hovered));
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        int cy = height / 2;
        int n = entries.size();
        if (n == 0) {
            g.drawCenteredString(font, "You have no powers yet.", cx, cy, MUTED);
            return;
        }

        // Which slice is the mouse pointing at? Slices start at the top and go clockwise.
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        hovered = -1;
        if (dx * dx + dy * dy > 12 * 12) {
            double angle = Math.atan2(dx, -dy); // 0 at the top, clockwise
            if (angle < 0) {
                angle += Math.PI * 2;
            }
            double slice = Math.PI * 2 / n;
            hovered = Math.floorMod((int) Math.floor((angle + slice / 2) / slice), n);
        }

        drawRing(g, cx, cy, n);

        // Labels around the ring.
        double slice = Math.PI * 2 / n;
        for (int i = 0; i < n; i++) {
            double a = i * slice;
            int lx = cx + (int) Math.round(Math.sin(a) * (RING_INNER + RING_OUTER) / 2.0);
            int ly = cy - (int) Math.round(Math.cos(a) * (RING_INNER + RING_OUTER) / 2.0);
            WheelEntry e = entries.get(i);
            int colour = i == hovered ? GOLD : TEXT;
            drawWrapped(g, font, e.name(), lx, ly - 4, colour);
        }

        // Centre: what you are about to pick.
        WheelEntry focus = hovered >= 0 ? entries.get(hovered) : null;
        if (focus != null) {
            g.drawCenteredString(font, focus.name(), cx, cy - 8, GOLD);
            g.drawCenteredString(font, focus.detail(), cx, cy + 3, MUTED);
        } else {
            g.drawCenteredString(font, "Point and", cx, cy - 8, MUTED);
            g.drawCenteredString(font, "release G", cx, cy + 3, MUTED);
        }
    }

    private static void drawWrapped(GuiGraphics g, Font font, String text, int x, int y, int colour) {
        int space = text.indexOf(' ');
        if (font.width(text) > 54 && space > 0) {
            g.drawCenteredString(font, text.substring(0, space), x, y - 5, colour);
            g.drawCenteredString(font, text.substring(space + 1), x, y + 5, colour);
        } else {
            g.drawCenteredString(font, text, x, y, colour);
        }
    }

    /** Ring slices as triangles, plus thin brass edges between them. */
    private void drawRing(GuiGraphics g, int cx, int cy, int n) {
        Matrix4f m = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        double slice = Math.PI * 2 / n;
        int steps = Math.max(6, 48 / n);
        double gap = 0.025;
        for (int i = 0; i < n; i++) {
            int colour = i == hovered ? SLICE_HOVER : SLICE;
            double start = i * slice - slice / 2 + gap;
            double end = i * slice + slice / 2 - gap;
            for (int s = 0; s < steps; s++) {
                double a0 = Mth.lerp(s / (double) steps, start, end);
                double a1 = Mth.lerp((s + 1) / (double) steps, start, end);
                quad(buf, m, cx, cy, a0, a1, RING_INNER, RING_OUTER, colour);
                // brass rim on the outside
                quad(buf, m, cx, cy, a0, a1, RING_OUTER, RING_OUTER + 2, i == hovered ? GOLD : EDGE);
            }
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder buf, Matrix4f m, int cx, int cy, double a0, double a1,
                             int r0, int r1, int colour) {
        float ix0 = (float) (cx + Math.sin(a0) * r0), iy0 = (float) (cy - Math.cos(a0) * r0);
        float ix1 = (float) (cx + Math.sin(a1) * r0), iy1 = (float) (cy - Math.cos(a1) * r0);
        float ox0 = (float) (cx + Math.sin(a0) * r1), oy0 = (float) (cy - Math.cos(a0) * r1);
        float ox1 = (float) (cx + Math.sin(a1) * r1), oy1 = (float) (cy - Math.cos(a1) * r1);
        int a = (colour >>> 24) & 0xFF, r = (colour >> 16) & 0xFF, gr = (colour >> 8) & 0xFF, b = colour & 0xFF;
        buf.addVertex(m, ix0, iy0, 0).setColor(r, gr, b, a);
        buf.addVertex(m, ox0, oy0, 0).setColor(r, gr, b, a);
        buf.addVertex(m, ox1, oy1, 0).setColor(r, gr, b, a);
        buf.addVertex(m, ix0, iy0, 0).setColor(r, gr, b, a);
        buf.addVertex(m, ox1, oy1, 0).setColor(r, gr, b, a);
        buf.addVertex(m, ix1, iy1, 0).setColor(r, gr, b, a);
    }
}
