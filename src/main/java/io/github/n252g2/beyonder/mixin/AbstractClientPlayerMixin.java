package io.github.n252g2.beyonder.mixin;

import io.github.n252g2.beyonder.client.CharacterSkins;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Players who have made a Beyonder character are drawn with their character's skin. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void beyonder$characterSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin skin = CharacterSkins.skinFor((AbstractClientPlayer) (Object) this, cir.getReturnValue());
        if (skin != null) {
            cir.setReturnValue(skin);
        }
    }
}
