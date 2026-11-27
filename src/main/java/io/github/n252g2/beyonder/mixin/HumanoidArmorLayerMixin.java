package io.github.n252g2.beyonder.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.n252g2.beyonder.client.EquipmentClient;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player who chose to show their clothes over their armour is drawn without the armour. */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void beyonder$clothesOverArmour(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity,
            float limbSwing, float limbAmount, float partialTick, float ageInTicks, float headYaw, float headPitch, CallbackInfo ci) {
        if (EquipmentClient.clothesOverArmour(entity)) {
            ci.cancel();
        }
    }
}
