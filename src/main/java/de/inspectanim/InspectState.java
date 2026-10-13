package de.inspectanim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Karambit-Style Inspect nur fuer Schwerter:
 *  1. Schwert wird zur Bildschirmmitte gehoben und gekippt
 *  2. Schwert wirbelt zweimal schnell um den Griff (wie das Karambit um den Finger)
 *  3. Schwert dreht sich auf die andere Seite (mit Ueberschwingen) und kommt zurueck
 * Shift+G: gleicher Wirbel, danach schrumpft das Schwert weg / kommt wieder.
 */
public final class InspectState {
    private InspectState() {}

    public enum Mode { NONE, INSPECT, VANISH, APPEAR }

    // ---- Einstellungen: hier kannst du alles anpassen ----
    private static final long INSPECT_MS = 3400;
    private static final long VANISH_MS = 2200;
    private static final long APPEAR_MS = 1400;
    private static final float TWIRL_ROUNDS = 2f;                 // Anzahl Umdrehungen um den Griff
    private static final float LIFT_X = -0.42f, LIFT_Y = 0.30f, LIFT_Z = 0.18f; // Hebe-Position
    // Position der Hand im Bild (Drehpunkt = Griff)
    private static final float PX = 0.56f, PY = -0.52f, PZ = -0.72f;

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

    // ---------- Hilfsfunktionen ----------
    private static void rot(PoseStack ps, Quaternionf q) { ps.mulPose(new Matrix4f().rotation(q)); }

    private static float clamp01(float x) { return Math.max(0f, Math.min(1f, x)); }

    private static float smooth(float x) { x = clamp01(x); return x * x * (3f - 2f * x); }

    private static float easeInOut(float x) {
        x = clamp01(x);
        return x < 0.5f ? 4f * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 3) / 2f;
    }

    private static float easeOutBack(float x) {
        x = clamp01(x);
        float c1 = 1.70158f, c3 = c1 + 1f;
        float t = x - 1f;
        return 1f + c3 * t * t * t + c1 * t * t;
    }

    /** Wird auf den PoseStack der Hand angewendet (Kamera-Raum, Drehpunkt = Griff). */
    public static void applyTransform(PoseStack ps) {
        if (mode == Mode.NONE) {
            if (hidden) ps.scale(0.001f, 0.001f, 0.001f);
            return;
        }
        float p = progress();

        float lift;       // 0..1 wie weit das Schwert Richtung Bildmitte gehoben ist
        float twirl = 0f; // Winkel um den Griff (Grad), in der Bildebene
        float flip = 0f;  // Drehung auf die andere Seite (Grad)
        float scale = 1f;

        switch (mode) {
            case INSPECT -> {
                lift = smooth(p / 0.14f) * (1f - smooth((p - 0.86f) / 0.14f));
                twirl = 360f * TWIRL_ROUNDS * easeInOut((p - 0.14f) / 0.42f);
                float f = easeOutBack((p - 0.58f) / 0.24f);
                flip = 180f * f * (1f - smooth((p - 0.84f) / 0.16f));
            }
            case VANISH -> {
                lift = smooth(p / 0.18f);
                twirl = 360f * TWIRL_ROUNDS * easeInOut((p - 0.18f) / 0.5f);
                scale = Math.max(0.001f, 1f - smooth((p - 0.55f) / 0.4f));
            }
            default -> { // APPEAR
                lift = 1f - smooth((p - 0.45f) / 0.55f);
                twirl = -360f * (1f - easeInOut(p / 0.8f));
                scale = Math.max(0.001f, smooth(p / 0.5f));
            }
        }

        // 1) Zur Bildmitte heben
        ps.translate(LIFT_X * lift, LIFT_Y * lift, LIFT_Z * lift);

        // 2) Um den Griff (Hand-Position) drehen
        ps.translate(PX, PY, PZ);
        rot(ps, Axis.ZP.rotationDegrees(twirl));                                   // Wirbel um den Griff
        rot(ps, Axis.YP.rotationDegrees(flip - 35f * lift));                       // Seite zeigen + Grundkippung
        rot(ps, Axis.ZP.rotationDegrees(15f * lift));
        rot(ps, Axis.XP.rotationDegrees(6f * lift * (float) Math.sin(6 * Math.PI * p)));
        ps.scale(scale, scale, scale);
        ps.translate(-PX, -PY, -PZ);
    }
}
