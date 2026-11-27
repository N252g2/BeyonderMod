package io.github.n252g2.beyonder.client;

import io.github.n252g2.beyonder.core.Equipment;
import io.github.n252g2.beyonder.core.EquipmentMenuSlot;
import io.github.n252g2.beyonder.net.EquipActionPayload;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Beyonder inventory: clothing on the left (beside the vanilla armour column), the character in
 * the middle with the sidearm, main weapon and ammo pouch under the crafting grid, and accessories,
 * the Sealed Artifact and the potion belt on the right. Vanilla's slots keep their places, so the
 * bag, hotbar, armour and crafting work as always.
 */
public class BeyonderInventoryScreen extends AbstractContainerScreen<InventoryMenu> {
    private static final int BG = 0xF0141019, PANEL = 0xF01C1622, BRASS = 0xFF8A7140, GOLD = 0xFFD8B25A,
            TEXT = 0xFFE6DCC6, MUTED = 0xFF9A9080, SLOT_DARK = 0xFF0C0A10, SLOT_LIGHT = 0xFF3A3040;
    // panels relative to leftPos/topPos: {x0, y0, x1, y1}
    private static final int[] LEFT = {-50, 0, -2, 84};
    private static final int[] RIGHT = {178, 0, 228, 88};

    private final Player player;
    private float spin = -18.0F;
    private boolean spinning;
    private Button clothesButton;

    public BeyonderInventoryScreen(Player player) {
        super(player.inventoryMenu, player.getInventory(), Component.literal("Inventory"));
        this.player = player;
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.titleLabelX = 97;
    }

    @Override
    protected void init() {
        super.init();
        clothesButton = addRenderableWidget(Button.builder(clothesLabel(), b -> {
                    var d = Equipment.get(player);
                    d.setClothesOverArmour(!d.clothesOverArmour()); // predicted; the server confirms
                    b.setMessage(clothesLabel());
                    PacketDistributor.sendToServer(new EquipActionPayload(EquipActionPayload.TOGGLE_CLOTHES));
                })
                .bounds(leftPos + LEFT[0], topPos + LEFT[3] + 3, LEFT[2] - LEFT[0], 14)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        "Show your clothing instead of the armour worn over it. Armour still protects you.")))
                .build());
    }

    private Component clothesLabel() {
        return Component.literal(Equipment.get(player).clothesOverArmour() ? "Clothes ✔" : "Clothes");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (minecraft != null && minecraft.gameMode != null && minecraft.gameMode.hasInfiniteItems()) {
            minecraft.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(player)); // hands over to creative
            return;
        }
        if (clothesButton != null) {
            clothesButton.setMessage(clothesLabel());
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (menu.getCarried().isEmpty() && hoveredSlot instanceof EquipmentMenuSlot slot && !slot.hasItem()) {
            g.renderComponentTooltip(font, List.of(Component.literal(slot.kind().label()),
                    Component.literal(hint(slot.kind())).withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
            return;
        }
        if (menu.getCarried().isEmpty() && hoveredSlot != null && hoveredSlot.index == 45 && !hoveredSlot.hasItem()) {
            g.renderComponentTooltip(font, List.of(Component.literal("Sidearm / off hand"),
                    Component.literal("A second weapon, drawn with your main one.").withStyle(net.minecraft.ChatFormatting.GRAY),
                    Component.literal("Or anything you'd hold in your off hand.").withStyle(net.minecraft.ChatFormatting.GRAY)),
                    mouseX, mouseY);
            return;
        }
        super.renderTooltip(g, mouseX, mouseY);
    }

    private static String hint(Equipment.Slot kind) {
        return switch (kind) {
            case WEAPON -> "Draw with " + EquipmentClient.DRAW.getTranslatedKeyMessage().getString()
                    + ", swap with the sidearm with " + EquipmentClient.SWAP.getTranslatedKeyMessage().getString() + ".";
            case AMMO_1, AMMO_2 -> "Rounds and arrows. Reloads take from here first.";
            case BELT_1 -> "Potions or food. Use with " + EquipmentClient.BELT[0].getTranslatedKeyMessage().getString() + ".";
            case BELT_2 -> "Potions or food. Use with " + EquipmentClient.BELT[1].getTranslatedKeyMessage().getString() + ".";
            case BELT_3 -> "Potions or food. Use with " + EquipmentClient.BELT[2].getTranslatedKeyMessage().getString() + ".";
            case ARTIFACT -> "A worn Sealed Artifact. Mind its drawback.";
            default -> "Worn under armour.";
        };
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        panel(g, x, y, x + imageWidth, y + imageHeight);
        panel(g, x + LEFT[0], y + LEFT[1], x + LEFT[2], y + LEFT[3]);
        panel(g, x + RIGHT[0], y + RIGHT[1], x + RIGHT[2], y + RIGHT[3]);

        // the character, turned by dragging
        g.fill(x + 25, y + 7, x + 76, y + 78, 0xFF0A080D);
        PlayerPreview.render(g, x + 26, y + 8, x + 75, y + 77, 30, player, spin);

        // crafting arrow
        g.fill(x + 136, y + 33, x + 148, y + 35, BRASS);
        g.fill(x + 146, y + 31, x + 148, y + 37, BRASS);

        for (Slot slot : menu.slots) {
            if (slot.isActive()) {
                slotFrame(g, x + slot.x - 1, y + slot.y - 1, slot instanceof EquipmentMenuSlot || slot.index == 45);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, "Crafting", 97, 6, MUTED, false);
        g.drawString(font, "Clothing", LEFT[0] + 3, -10, GOLD, false);
        g.drawString(font, "Kit", RIGHT[0] + 3, -10, GOLD, false);
        g.drawString(font, "Arms", 78, 54, MUTED, false);
        g.drawString(font, "Ammo", 128, 54, MUTED, false);
        String keys = EquipmentClient.DRAW.getTranslatedKeyMessage().getString() + " draw  "
                + EquipmentClient.SWAP.getTranslatedKeyMessage().getString() + " swap";
        g.drawString(font, keys, RIGHT[0] + 2, RIGHT[3] + 4, MUTED, false);
        String belt = EquipmentClient.BELT[0].getTranslatedKeyMessage().getString() + " "
                + EquipmentClient.BELT[1].getTranslatedKeyMessage().getString() + " "
                + EquipmentClient.BELT[2].getTranslatedKeyMessage().getString() + " belt";
        g.drawString(font, belt, RIGHT[0] + 2, RIGHT[3] + 14, MUTED, false);
    }

    private static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, PANEL);
        g.fill(x0, y0, x1, y0 + 1, BRASS);
        g.fill(x0, y1 - 1, x1, y1, BRASS);
        g.fill(x0, y0, x0 + 1, y1, BRASS);
        g.fill(x1 - 1, y0, x1, y1, BRASS);
    }

    private static void slotFrame(GuiGraphics g, int x, int y, boolean special) {
        g.fill(x, y, x + 18, y + 18, special ? 0xFF6E5A34 : SLOT_LIGHT);
        g.fill(x + 1, y + 1, x + 18, y + 18, SLOT_DARK);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF1E1924);
    }

    // ------------------------------------------------------------------ mouse

    private boolean overDoll(double mx, double my) {
        return mx >= leftPos + 26 && mx < leftPos + 75 && my >= topPos + 8 && my < topPos + 77;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && overDoll(mx, my) && hoveredSlot == null) {
            spinning = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
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
        if (spinning) {
            spinning = false;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    /** Clicks on the side panels are inside the screen (so a held item isn't thrown out). */
    @Override
    protected boolean hasClickedOutside(double mx, double my, int left, int top, int button) {
        return super.hasClickedOutside(mx, my, left, top, button)
                && !inside(mx, my, LEFT, LEFT[3] + 18) && !inside(mx, my, RIGHT, RIGHT[3] + 24);
    }

    private boolean inside(double mx, double my, int[] r, int bottom) {
        return mx >= leftPos + r[0] && mx < leftPos + r[2] && my >= topPos + r[1] - 12 && my < topPos + bottom;
    }
}
