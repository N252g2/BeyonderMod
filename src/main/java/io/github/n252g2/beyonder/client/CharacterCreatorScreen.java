package io.github.n252g2.beyonder.client;

import io.github.n252g2.beyonder.core.Appearance;
import io.github.n252g2.beyonder.core.Appearance.Slot;
import io.github.n252g2.beyonder.core.Background;
import io.github.n252g2.beyonder.net.CharacterSyncPayload;
import io.github.n252g2.beyonder.net.ClientInbox;
import io.github.n252g2.beyonder.net.CreateCharacterPayload;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Character creation, shown on first join: a live preview on the left (drag it to turn the
 * character), and on the right a name, the look (one row per choice with arrows), and a background with
 * its story and perks. Nothing else can happen until the character is made.
 */
public class CharacterCreatorScreen extends Screen {
    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z'\\-]*( [A-Za-z][A-Za-z'\\-]*){0,2}");
    private static final int GOLD = 0xFFD8B25A, BRASS = 0xFF8A7140, TEXT = 0xFFE6DCC6, MUTED = 0xFF9A9080, RED = 0xFFE0604A;

    private final int[] appearance;
    private int background;
    private EditBox nameBox;
    private Button begin;
    private String problem = "";
    private boolean waiting;
    private int versionAtSubmit;
    /** How far the preview is turned (degrees); dragged with the mouse. */
    private float spin = -22.0F;
    private boolean spinning;

    public CharacterCreatorScreen() {
        super(Component.literal("Who are you?"));
        this.appearance = Appearance.random(RandomSource.create());
        CharacterSkins.setPreview(appearance);
    }

    /** Opens the creator whenever the server says this player's character isn't made yet. */
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        CharacterSyncPayload self = ClientInbox.character(mc.player.getId());
        if (self != null && !self.created() && !(mc.screen instanceof CharacterCreatorScreen)
                && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            mc.setScreen(new CharacterCreatorScreen());
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ layout

    private int panelX() {
        return width / 2 - 10;
    }

    @Override
    protected void init() {
        int x = panelX();
        int y = 34;
        String keep = nameBox == null ? "" : nameBox.getValue();
        nameBox = new EditBox(font, x, y, 170, 18, Component.literal("Name"));
        nameBox.setMaxLength(24);
        nameBox.setHint(Component.literal("Your character's name"));
        nameBox.setValue(keep);
        nameBox.setResponder(s -> problem = "");
        addRenderableWidget(nameBox);
        y += 30;

        for (Slot slot : Appearance.ORDER) {
            final int row = y;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> step(slot, -1)).bounds(x + 70, row, 16, 16).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> step(slot, 1)).bounds(x + 190, row, 16, 16).build());
            y += 18;
        }
        y += 8;
        final int bgRow = y;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> background = Math.floorMod(background - 1, Background.values().length))
                .bounds(x + 70, bgRow, 16, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> background = Math.floorMod(background + 1, Background.values().length))
                .bounds(x + 190, bgRow, 16, 16).build());

        int by = height - 30;
        addRenderableWidget(Button.builder(Component.literal("Randomise look"), b -> randomise()).bounds(x, by, 100, 20).build());
        begin = addRenderableWidget(Button.builder(Component.literal("Begin in Tingen"), b -> submit()).bounds(x + 106, by, 120, 20).build());
        setInitialFocus(nameBox);
    }

    private void step(Slot slot, int dir) {
        int i = slot.ordinal();
        appearance[i] = Math.floorMod(appearance[i] + dir, slot.count());
        CharacterSkins.setPreview(appearance);
    }

    private void randomise() {
        int[] r = Appearance.random(RandomSource.create());
        System.arraycopy(r, 0, appearance, 0, appearance.length);
        CharacterSkins.setPreview(appearance);
    }

    private void submit() {
        String name = nameBox.getValue().trim().replaceAll("\\s+", " ");
        if (name.length() < 2 || name.length() > 24) {
            problem = "A name must be 2 to 24 letters long.";
            return;
        }
        if (!NAME.matcher(name).matches()) {
            problem = "Letters, spaces, hyphens and apostrophes only (up to three words).";
            return;
        }
        waiting = true;
        versionAtSubmit = ClientInbox.characterVersion();
        PacketDistributor.sendToServer(new CreateCharacterPayload(name, Background.values()[background].id(), appearance.clone()));
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        CharacterSyncPayload self = ClientInbox.character(mc.player.getId());
        if (self != null && self.created()) {
            mc.setScreen(null);
            return;
        }
        if (waiting && ClientInbox.characterVersion() != versionAtSubmit) {
            waiting = false;
            problem = "That name can't be used. Try another (see chat for why).";
        }
        begin.active = !waiting;
        begin.setMessage(Component.literal(waiting ? "…" : "Begin in Tingen"));
    }

    @Override
    public void removed() {
        CharacterSkins.setPreview(null);
        super.removed();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, 0, width, height, 0xF0100C16, 0xF0181220);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int x = panelX();

        g.drawCenteredString(font, "Who are you?", width / 2, 10, GOLD);
        g.fill(width / 2 - 70, 22, width / 2 + 70, 23, BRASS);

        // the character, facing us (drag to turn)
        int px0 = previewX0(), px1 = previewX1();
        g.fill(px0, 30, px1, height - 40, 0x60000000);
        g.fill(px0, 30, px1, 31, BRASS);
        g.fill(px0, height - 41, px1, height - 40, BRASS);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            int size = Math.min(80, (height - 90) / 2);
            renderPreview(g, px0, 30, px1, height - 40, size, mc.player);
        }
        g.drawCenteredString(font, "drag to turn", (px0 + px1) / 2, 34, 0xFF6E665A);
        String shown = nameBox.getValue().isBlank() ? "Unnamed" : nameBox.getValue().trim();
        g.drawCenteredString(font, shown, (px0 + px1) / 2, height - 54, TEXT);

        g.drawString(font, "Name", x, 24, MUTED, false);
        int y = 64;
        for (Slot slot : Appearance.ORDER) {
            g.drawString(font, slot.label(), x, y + 4, MUTED, false);
            String value = slot.options().get(Appearance.get(appearance, slot));
            g.drawCenteredString(font, value, x + 138, y + 4, TEXT);
            y += 18;
        }
        y += 8;
        Background bg = Background.values()[background];
        g.drawString(font, "Background", x, y + 4, MUTED, false);
        g.drawCenteredString(font, bg.title(), x + 138, y + 4, GOLD);
        y += 22;
        int wrap = Math.min(260, width - x - 10);
        for (FormattedCharSequence line : font.split(Component.literal(bg.story()).withStyle(s -> s.withItalic(true)), wrap)) {
            g.drawString(font, line, x, y, 0xFFB8AE9A, false);
            y += 10;
        }
        y += 3;
        List<String> perks = bg.perks();
        for (String perk : perks) {
            g.drawString(font, "• " + perk, x, y, 0xFF9BD08C, false);
            y += 10;
        }
        if (!problem.isEmpty()) {
            g.drawString(font, problem, x, height - 44, RED, false);
        }
    }

    private int previewX0() {
        return Math.max(10, panelX() - 190);
    }

    private int previewX1() {
        return panelX() - 20;
    }

    /**
     * Draws the character upright and looking straight ahead, turned by {@link #spin}. (Vanilla's
     * follow-the-mouse preview made them lean back and stare up whenever the cursor was near the top.)
     */
    private static void renderPreview(GuiGraphics g, int x0, int y0, int x1, int y1, int size, LivingEntity e, float spin) {
        float cx = (x0 + x1) / 2.0F, cy = (y0 + y1) / 2.0F;
        g.enableScissor(x0, y0, x1, y1);
        Quaternionf camera = new Quaternionf().rotateX((float) Math.toRadians(-6.0));
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI).mul(camera);
        float body = e.yBodyRot, yRot = e.getYRot(), xRot = e.getXRot(), headO = e.yHeadRotO, head = e.yHeadRot;
        e.yBodyRot = 180.0F + spin;
        e.setYRot(180.0F + spin);
        e.setXRot(0.0F);
        e.yHeadRot = e.getYRot();
        e.yHeadRotO = e.getYRot();
        float scale = e.getScale();
        Vector3f offset = new Vector3f(0.0F, e.getBbHeight() / 2.0F + 0.0625F * scale, 0.0F);
        InventoryScreen.renderEntityInInventory(g, cx, cy, size / scale, offset, pose, camera, e);
        e.yBodyRot = body;
        e.setYRot(yRot);
        e.setXRot(xRot);
        e.yHeadRotO = headO;
        e.yHeadRot = head;
        g.disableScissor();
    }

    private void renderPreview(GuiGraphics g, int x0, int y0, int x1, int y1, int size, LivingEntity e) {
        renderPreview(g, x0, y0, x1, y1, size, e, spin);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (button == 0 && mx >= previewX0() && mx < previewX1() && my >= 30 && my < height - 40) {
            spinning = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (spinning) {
            spin -= (float) dx * 1.6F;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        spinning = false;
        return super.mouseReleased(mx, my, button);
    }
}
