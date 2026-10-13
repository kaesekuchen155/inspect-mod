package de.inspectanim.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import de.inspectanim.InspectState;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 26.3: ItemInHandRenderer wurde zu FirstPersonHandsAndItemsRenderer.
 * Die Parameterliste wird nicht angegeben, nur der PoseStack wird per @Local geholt.
 * Das ist der einzige versionsabhaengige Teil der Mod.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class ItemInHandRendererMixin {

    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void inspectanim$push(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        poseStack.pushPose();
        InspectState.applyTransform(poseStack);
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void inspectanim$pop(CallbackInfo ci, @Local(argsOnly = true) PoseStack poseStack) {
        poseStack.popPose();
    }
}
