package io.github.n252g2.beyonder.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.n252g2.beyonder.net.CharacterSyncPayload;
import io.github.n252g2.beyonder.net.ClientInbox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

/**
 * Players' name tags show their character's name. While you look straight at someone, their
 * account name appears underneath, smaller and a little transparent.
 */
public final class NameTags {
    private static final float SMALL = 0.65F;

    private NameTags() {}

    static void onRenderNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        CharacterSyncPayload c = ClientInbox.character(player.getId());
        if (c == null || !c.created() || c.name().isEmpty()) {
            return;
        }
        event.setContent(Component.literal(c.name()));

        Minecraft mc = Minecraft.getInstance();
        if (player == mc.player || mc.crosshairPickEntity != player || player.isInvisible()) {
            return;
        }
        Vec3 at = player.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, player.getViewYRot(event.getPartialTick()));
        if (at == null) {
            return;
        }
        String account = player.getGameProfile().getName();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(at.x, at.y + 0.5, at.z);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float scale = 0.025F * SMALL;
        pose.scale(scale, -scale, scale);
        Font font = mc.font;
        float x = -font.width(account) / 2.0F;
        float y = 9.0F / SMALL + 1.0F; // just under the main name
        int background = (int) (mc.options.getBackgroundOpacity(0.25F) * 255.0F * 0.6F) << 24;
        font.drawInBatch(account, x, y, 0x99FFFFFF, false, pose.last().pose(), event.getMultiBufferSource(),
                Font.DisplayMode.NORMAL, background, event.getPackedLight());
        pose.popPose();
    }
}
