package io.github.n252g2.beyonder.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.n252g2.beyonder.item.RevolverItem;
import io.github.n252g2.beyonder.net.GunActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Gun handling on the client, in the spirit of modern gun mods: the revolver sits low on the right
 * at the hip and comes up to the centre of the screen when you aim (hold right click), with the
 * sights lined up on the crosshair. Left click fires with a kick of the camera and the gun, R
 * reloads with a tilt-and-load animation, and a small ammo counter sits by the hotbar.
 */
public final class GunClient {
    // Display scale of the first-person model (see models/item/revolver.json).
    private static final float SCALE = 0.8F;
    // Where the gun's centre sits, in view space (x right, y up, -z forward).
    private static final float HIP_X = 0.36F, HIP_Y = -0.30F, HIP_Z = -0.66F;
    // When aiming, the sight line (model y = 11.25/16) lands exactly on the screen centre.
    private static final float SIGHT = (11.25F / 16.0F - 0.5F) * SCALE;
    private static final float ADS_X = 0.0F, ADS_Y = -SIGHT, ADS_Z = -0.80F;

    private static float aim, prevAim;
    private static float recoil, prevRecoil;
    private static int reloadTicks;
    private static final int RELOAD_ANIM = RevolverItem.RELOAD_TICKS;

    private GunClient() {}

    private static boolean holdingGun(LocalPlayer player) {
        return player != null && player.getMainHandItem().getItem() instanceof RevolverItem;
    }

    // ------------------------------------------------------------------ input

    /** Runs before vanilla handles the keys, so left clicks become shots instead of attacks. */
    static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        prevAim = aim;
        prevRecoil = recoil;
        recoil *= 0.62F;
        if (reloadTicks > 0) {
            reloadTicks--;
        }
        boolean gun = holdingGun(player);
        float target = gun && RevolverItem.isAiming(player) && reloadTicks == 0 ? 1.0F : 0.0F;
        aim += (target - aim) * 0.38F;
        if (!gun) {
            reloadTicks = 0;
            return;
        }
        if (mc.screen == null) {
            while (mc.options.keyAttack.consumeClick()) {
                tryFire(mc, player);
            }
        }
    }

    /** Left click never swings or breaks blocks while a gun is in hand. */
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isAttack() && holdingGun(Minecraft.getInstance().player)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private static void tryFire(Minecraft mc, LocalPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (reloadTicks > 0 || player.getCooldowns().isOnCooldown(stack.getItem())) {
            return;
        }
        if (RevolverItem.cylinder(stack).isEmpty()) {
            player.playSound(SoundEvents.DISPENSER_FAIL, 0.5F, 1.8F);
            player.displayClientMessage(Component.literal("Empty. Press " + AbilityKeys.USE.getTranslatedKeyMessage().getString()
                    + " to reload.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        boolean aiming = RevolverItem.isAiming(player);
        recoil = 1.0F;
        float kick = RevolverItem.kick(aiming);
        player.setXRot(player.getXRot() - kick);
        player.setYRot(player.getYRot() + (player.getRandom().nextFloat() - 0.5F) * kick * 0.5F);
        PacketDistributor.sendToServer(new GunActionPayload(GunActionPayload.FIRE));
    }

    /** Called by the R key while a gun is in hand. */
    static void tryReload(Minecraft mc, LocalPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (reloadTicks > 0 || player.getCooldowns().isOnCooldown(stack.getItem())) {
            return;
        }
        if (RevolverItem.cylinder(stack).length() >= RevolverItem.CAPACITY) {
            player.displayClientMessage(Component.literal("The cylinder is full.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (RevolverItem.reserve(player) == 0) {
            player.displayClientMessage(Component.literal("You have no rounds.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        reloadTicks = RELOAD_ANIM;
        PacketDistributor.sendToServer(new GunActionPayload(GunActionPayload.RELOAD));
    }

    static boolean isHoldingGun() {
        return holdingGun(Minecraft.getInstance().player);
    }

    // ------------------------------------------------------------------ view

    static void onComputeFov(ViewportEvent.ComputeFov event) {
        float a = Mth.lerp((float) event.getPartialTick(), prevAim, aim);
        if (a > 0.001F && isHoldingGun()) {
            event.setFOV(event.getFOV() * (1.0 - 0.16 * a));
        }
    }

    /** No crosshair while looking down the sights. */
    static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (event.getName().equals(VanillaGuiLayers.CROSSHAIR) && aim > 0.5F && isHoldingGun()) {
            event.setCanceled(true);
        }
    }

    /** The ammo counter: rounds in the cylinder as six chambers, and the reserve beside them. */
    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || !holdingGun(mc.player)) {
            return;
        }
        ItemStack stack = mc.player.getMainHandItem();
        String cylinder = RevolverItem.cylinder(stack);
        int w = g.guiWidth();
        int h = g.guiHeight();
        int x = w - 92;
        int y = h - 30;
        g.fill(x - 4, y - 4, x + 88, y + 22, 0xA0100E14);
        g.fill(x - 4, y - 4, x + 88, y - 3, 0xFF8A7140);
        for (int i = 0; i < RevolverItem.CAPACITY; i++) {
            int cx = x + 2 + i * 9;
            int color = i < cylinder.length() ? (cylinder.charAt(i) == 'S' ? 0xFFE6ECF4 : 0xFFD8B25A) : 0xFF2A2430;
            g.fill(cx, y + 2, cx + 6, y + 12, 0xFF0C0A10);
            g.fill(cx + 1, y + 3, cx + 5, y + 11, color);
            if (i < cylinder.length()) {
                g.fill(cx + 1, y + 3, cx + 5, y + 5, cylinder.charAt(i) == 'S' ? 0xFFFFFFFF : 0xFFB0884A);
            }
        }
        Font font = mc.font;
        String count = cylinder.length() + " / " + RevolverItem.reserve(mc.player);
        g.drawString(font, count, x + 58, y + 4, cylinder.isEmpty() ? 0xFFB3202C : 0xFFE6DCC6, true);
        if (reloadTicks > 0) {
            float p = 1.0F - reloadTicks / (float) RELOAD_ANIM;
            g.fill(x - 4, y + 18, x - 4 + (int) (92 * p), y + 20, 0xFFD8B25A);
        }
    }

    // ------------------------------------------------------------------ the gun in hand

    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return HumanoidModel.ArmPose.CROSSBOW_HOLD; // a two-handed shooting stance in third person
        }

        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                float partialTick, float equipProcess, float swingProcess) {
            float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            float a = smooth(Mth.lerp(partialTick, prevAim, aim));
            float r = Mth.lerp(partialTick, prevRecoil, recoil);
            float reload = 0.0F;
            if (reloadTicks > 0 && arm == player.getMainArm()) {
                float p = 1.0F - (reloadTicks - partialTick) / RELOAD_ANIM;
                reload = Mth.sin(Mth.clamp(p, 0.0F, 1.0F) * Mth.PI);
            }

            float x = Mth.lerp(a, HIP_X, ADS_X) * side;
            float y = Mth.lerp(a, HIP_Y, ADS_Y) - equipProcess * 0.6F - reload * 0.12F;
            float z = Mth.lerp(a, HIP_Z, ADS_Z) + r * Mth.lerp(a, 0.09F, 0.05F);
            pose.translate(x, y, z);
            // Hip: angled slightly in toward the crosshair, like a gun held at the ready.
            pose.mulPose(Axis.YP.rotationDegrees(side * Mth.lerp(a, 4.0F, 0.0F)));
            // Recoil lifts the muzzle; the reload rolls the gun over to swing the cylinder out.
            pose.mulPose(Axis.XP.rotationDegrees(r * Mth.lerp(a, 9.0F, 4.0F) + reload * 28.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(side * reload * 38.0F));
            return true;
        }
    };

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }
}
