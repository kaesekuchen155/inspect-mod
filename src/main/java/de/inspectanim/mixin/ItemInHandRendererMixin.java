package de.inspectanim.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import de.inspectanim.InspectState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ACHTUNG: Das ist der einzige versionsabhaengige Teil der Mod.
 * Falls die Parameterliste von renderArmWithItem in 26.3 anders ist, hier anpassen.
 */
@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    @ModifyVariable(method = "renderArmWithItem", at = @At("HEAD"), argsOnly = true)
    private ItemStack inspectanim$hideItem(ItemStack stack) {
        var player = Minecraft.getInstance().player;
        if (player != null && stack == player.getMainHandItem() && InspectState.shouldHideItem()) {
            return ItemStack.EMPTY; // Item weg, Hand bleibt
        }
        return stack;
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void inspectanim$push(AbstractClientPlayer player, float partialTick, float pitch,
                                  InteractionHand hand, float swingProgress, ItemStack stack,
                                  float equipProgress, PoseStack poseStack,
                                  SubmitNodeCollector collector, int light, CallbackInfo ci) {
        poseStack.pushPose();
        if (hand == InteractionHand.MAIN_HAND) InspectState.applyTransform(poseStack);
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void inspectanim$pop(AbstractClientPlayer player, float partialTick, float pitch,
                                 InteractionHand hand, float swingProgress, ItemStack stack,
                                 float equipProgress, PoseStack poseStack,
                                 SubmitNodeCollector collector, int light, CallbackInfo ci) {
        poseStack.popPose();
    }
}
