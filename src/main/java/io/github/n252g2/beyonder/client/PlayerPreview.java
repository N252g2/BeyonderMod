package io.github.n252g2.beyonder.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A player drawn in a screen, facing the viewer and turned by a given angle (dragged with the mouse). */
public final class PlayerPreview {
    private PlayerPreview() {}

    /**
     * Draws the character upright and looking straight ahead, turned by {@code spin}. (Vanilla's
     * follow-the-mouse preview made them lean back and stare up whenever the cursor was near the top.)
     */
    public static void render(GuiGraphics g, int x0, int y0, int x1, int y1, int size, LivingEntity e, float spin) {
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

}
