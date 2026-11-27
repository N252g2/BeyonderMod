package io.github.n252g2.beyonder.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.n252g2.beyonder.core.Equipment;
import io.github.n252g2.beyonder.core.Equipment.Kind;
import io.github.n252g2.beyonder.core.PlayerAnim;
import io.github.n252g2.beyonder.net.ClientInbox;
import io.github.n252g2.beyonder.net.EquipStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Weapons that aren't drawn are worn: blades on the left hip, guns in a holster on the right hip,
 * heavy weapons, staves and bows across the back. The sidearm takes the other hip if its own is taken.
 */
public class SheathLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private enum Mount { LEFT_HIP, RIGHT_HIP, BACK, BACK_LOW }

    public SheathLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
            float limbAmount, float partialTick, float ageInTicks, float headYaw, float headPitch) {
        if (player.isInvisible() || player.isSleeping()) {
            return;
        }
        ItemStack weapon, sidearm;
        boolean drawn;
        if (player.isLocalPlayer()) {
            weapon = Equipment.item(player, Equipment.Slot.WEAPON);
            sidearm = player.getOffhandItem();
            drawn = Equipment.isDrawn(player);
        } else {
            EquipStatePayload s = ClientInbox.equipment(player.getId());
            if (s == null) {
                return;
            }
            weapon = s.item(EquipStatePayload.WEAPON);
            sidearm = s.item(EquipStatePayload.SIDEARM);
            drawn = s.drawn();
        }
        belt(pose, buffers, light, player);
        // Mid-draw the weapon is still on the body; mid-sheathe it isn't there yet.
        float draw = PlayerAnimator.progress(player, PlayerAnim.DRAW, partialTick);
        float sheathe = PlayerAnimator.progress(player, PlayerAnim.SHEATHE, partialTick);
        boolean onBody = (!drawn && !(sheathe >= 0.0F && sheathe < 0.5F)) || (drawn && draw >= 0.0F && draw < 0.45F);
        if (!onBody) {
            return;
        }
        Mount main = null;
        if (!weapon.isEmpty()) {
            main = preferred(Kind.of(weapon));
            wear(pose, buffers, light, player, weapon, main);
        }
        if (Equipment.isWeapon(sidearm)) {
            Mount m = preferred(Kind.of(sidearm));
            if (m == main) {
                m = switch (m) {
                    case LEFT_HIP -> Mount.RIGHT_HIP;
                    case RIGHT_HIP -> Mount.LEFT_HIP;
                    case BACK -> Mount.BACK_LOW;
                    case BACK_LOW -> Mount.BACK;
                };
            }
            wear(pose, buffers, light, player, sidearm, m);
        }
    }

    /** Belt slot positions: front right, front left, and the small of the back. {x, z, facing back?} */
    private static final float[][] BELT_SPOTS = {{-0.15F, -0.175F, 0.0F}, {0.15F, -0.175F, 0.0F}, {0.0F, 0.175F, 1.0F}};

    /** What hangs from the belt, small: potions, food, whatever the belt slots hold. */
    private void belt(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player) {
        for (int i = 0; i < Equipment.Slot.BELTS.length; i++) {
            ItemStack stack;
            if (player.isLocalPlayer()) {
                stack = Equipment.item(player, Equipment.Slot.BELTS[i]);
            } else {
                EquipStatePayload s = ClientInbox.equipment(player.getId());
                stack = s == null ? ItemStack.EMPTY : s.item(EquipStatePayload.BELT + i);
            }
            if (stack.isEmpty()) {
                continue;
            }
            float[] spot = BELT_SPOTS[i];
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            pose.translate(spot[0], 0.76F, spot[1]);
            pose.mulPose(Axis.ZP.rotationDegrees(180.0F)); // model space is upside down; items hang upright
            if (spot[2] > 0.0F) {
                pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
            pose.mulPose(Axis.ZP.rotationDegrees(i == 0 ? 8.0F : -8.0F));
            pose.scale(0.26F, 0.26F, 0.26F);
            Minecraft.getInstance().getItemRenderer().renderStatic(player, stack, ItemDisplayContext.NONE, false, pose, buffers,
                    player.level(), light, OverlayTexture.NO_OVERLAY, player.getId() + 10 + i);
            pose.popPose();
        }
    }

    private static Mount preferred(Kind kind) {
        return switch (kind) {
            case GUN -> Mount.RIGHT_HIP;
            case BLADE -> Mount.LEFT_HIP;
            default -> Mount.BACK;
        };
    }

    /**
     * Model space here: +y is down, +x is the player's left, +z is their back; 1 = one block, a
     * pixel is 1/16. Item models come out centred with their "up" pointing down, which suits a
     * hanging blade.
     */
    private void wear(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ItemStack stack, Mount mount) {
        boolean gun = Kind.of(stack) == Kind.GUN;
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        switch (mount) {
            case LEFT_HIP, RIGHT_HIP -> {
                float side = mount == Mount.LEFT_HIP ? 1.0F : -1.0F;
                if (gun) {
                    // holstered: barrel down, grip up and to the back
                    // low on the thigh: the grip at the belt, the barrel along the leg
                    pose.translate(side * 0.30F, 0.86F, 0.02F);
                    pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                    pose.mulPose(Axis.XP.rotationDegrees(-8.0F));
                    pose.scale(0.5F, 0.5F, 0.5F);
                } else {
                    // a blade hangs from the belt, tip down and back, flat against the leg
                    pose.translate(side * 0.31F, 0.58F, 0.05F);
                    pose.mulPose(Axis.XP.rotationDegrees(26.0F));
                    pose.mulPose(Axis.YP.rotationDegrees(90.0F));
                    pose.scale(0.36F, 0.36F, 0.36F);
                }
            }
            case BACK, BACK_LOW -> {
                // across the back, hilt over the right shoulder
                pose.translate(0.0F, mount == Mount.BACK ? 0.34F : 0.48F, 0.2F);
                pose.mulPose(Axis.ZP.rotationDegrees(mount == Mount.BACK ? -38.0F : 38.0F));
                if (gun) {
                    pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                }
                pose.scale(0.45F, 0.45F, 0.45F);
            }
        }
        Minecraft.getInstance().getItemRenderer().renderStatic(player, stack, ItemDisplayContext.NONE, false, pose, buffers,
                player.level(), light, OverlayTexture.NO_OVERLAY, player.getId() + mount.ordinal());
        pose.popPose();
    }
}
