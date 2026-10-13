package de.inspectanim;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;

public class InspectAnimClient implements ClientModInitializer {
    public static KeyMapping INSPECT_KEY;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("inspectanim", "main"));

        // 26.3: Tastencodes kommen aus InputConstants (SDL), kein GLFW mehr
        INSPECT_KEY = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.inspectanim.inspect", InputConstants.KEY_G, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;
            InspectState.tick(mc);
            while (INSPECT_KEY.consumeClick()) {
                if (!mc.player.getMainHandItem().is(ItemTags.SWORDS)) continue; // nur Schwerter
                if (mc.player.isShiftKeyDown()) InspectState.toggleVanish();
                else InspectState.startInspect();
            }
        });
    }
}
