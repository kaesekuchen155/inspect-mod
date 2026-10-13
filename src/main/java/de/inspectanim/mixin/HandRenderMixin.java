package de.inspectanim.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import de.inspectanim.InspectHand;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Eigene Mixin-Datei (nicht "required"): Falls sich hier in 26.3 etwas unterscheidet,
 * laeuft die Mod trotzdem weiter, nur ohne Hand.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class HandRenderMixin {

    @Inject(method = "submitArmWithItem", at = @At("RETURN"), require = 0)
    private void inspectanim$hand(CallbackInfo ci,
                                  @Local(argsOnly = true) PoseStack poseStack,
                                  @Local(argsOnly = true) SubmitNodeCollector collector,
                                  @Local(argsOnly = true) int light) {
        InspectHand.render((Object) this, poseStack, collector, light);
    }
}
