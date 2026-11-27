package io.github.n252g2.beyonder.mixin;

import io.github.n252g2.beyonder.client.PlayerAnimator;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After vanilla poses a player, Beyonder's stances and animations are blended on top. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void beyonder$animate(LivingEntity entity, float limbSwing, float limbAmount, float ageInTicks,
            float headYaw, float headPitch, CallbackInfo ci) {
        PlayerAnimator.pose((HumanoidModel<?>) (Object) this, entity, limbSwing, limbAmount, ageInTicks);
    }
}
