package de.inspectanim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;

public final class InspectState {
    private InspectState() {}

    public enum Mode { NONE, INSPECT, VANISH, APPEAR }

    // ---- Zeiten (ms) - hier kannst du die Geschwindigkeit anpassen ----
    private static final long INSPECT_MS = 1800;
    private static final long VANISH_MS = 1300;
    private static final long APPEAR_MS = 1000;

    private static Mode mode = Mode.NONE;
    private static long startMs;
    private static boolean hidden;
    private static int lastSlot = -1;

    public static void startInspect() {
        if (hidden || mode != Mode.NONE) return;
        mode = Mode.INSPECT; startMs = System.currentTimeMillis();
    }

    public static void toggleVanish() {
        if (mode != Mode.NONE) return;
        mode = hidden ? Mode.APPEAR : Mode.VANISH;
        startMs = System.currentTimeMillis();
    }

    public static void tick(Minecraft mc) {
        int slot = mc.player.getInventory().getSelectedSlot();
        if (slot != lastSlot) { lastSlot = slot; hidden = false; mode = Mode.NONE; }
        if (mode != Mode.NONE && progress() >= 1f) {
            if (mode == Mode.VANISH) hidden = true;
            if (mode == Mode.APPEAR) hidden = false;
            mode = Mode.NONE;
        }
    }

    private static long duration() {
        return switch (mode) { case INSPECT -> INSPECT_MS; case VANISH -> VANISH_MS; case APPEAR -> APPEAR_MS; default -> 1; };
    }

    private static float progress() {
        return Math.min(1f, (System.currentTimeMillis() - startMs) / (float) duration());
    }

    private static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    /** Soll das Item gerade NICHT gezeichnet werden (Hand bleibt sichtbar)? */
    public static boolean shouldHideItem() {
        if (mode == Mode.VANISH) return progress() > 0.7f;
        if (mode == Mode.APPEAR) return progress() < 0.25f;
        return hidden;
    }

    public static boolean isActive() { return mode != Mode.NONE || hidden; }

    /** Wird auf den PoseStack der Haupthand angewendet. */
    public static void applyTransform(PoseStack ps) {
        if (mode == Mode.NONE) return;
        float p = progress();
        float arc = (float) Math.sin(Math.PI * p);

        // Item etwas zur Bildschirmmitte und nach oben holen
        ps.translate(-0.12f * arc, 0.08f * arc, -0.08f * arc);

        switch (mode) {
            case INSPECT -> {
                ps.mulPose(Axis.XP.rotationDegrees(-360f * smooth(p)));                 // Flip
                ps.mulPose(Axis.YP.rotationDegrees(30f * (float) Math.sin(2 * Math.PI * p)));
                ps.mulPose(Axis.ZP.rotationDegrees(15f * (float) Math.sin(3 * Math.PI * p)));
            }
            case VANISH -> {
                ps.mulPose(Axis.XP.rotationDegrees(-540f * smooth(p)));
                ps.mulPose(Axis.YP.rotationDegrees(360f * smooth(p)));
                float s = 1f - 0.95f * smooth((p - 0.35f) / 0.4f);                       // schrumpft weg
                ps.scale(s, s, s);
            }
            case APPEAR -> {
                ps.mulPose(Axis.YP.rotationDegrees(-360f * (1f - smooth(p))));
                float s = 0.05f + 0.95f * smooth((p - 0.2f) / 0.6f);                     // waechst rein
                ps.scale(s, s, s);
            }
            default -> {}
        }
    }
}
