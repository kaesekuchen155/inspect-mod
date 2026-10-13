package de.inspectanim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Karambit-Style Inspect nur fuer Schwerter.
 *  G                    : Schwert wird gehoben und dreht sich dauerhaft um den Griff
 *  G schnell wiederholt : jedes G gibt dem Schwert einen Extra-Schubs (Spin wird kurz schneller)
 *  G (nach Pause)       : Inspect stoppt, Schwert faehrt sanft zurueck
 *  Schlagen / Schwert aus der Hand / Slot wechseln : Inspect wird sofort beendet
 *  Shift+G              : Schwert wegwirbeln und verschwinden lassen / wieder zurueckholen
 */
public final class InspectState {
    private InspectState() {}

    public enum Mode { NONE, LOOP, STOP, VANISH, APPEAR }

    // ---- Einstellungen: hier kannst du alles anpassen ----
    private static final float BASE_OMEGA = 540f;      // Dauer-Drehgeschwindigkeit in Grad pro Sekunde (360 = 1 Umdrehung/Sek.)
    private static final float KICK_OMEGA = 900f;      // Extra-Schubs pro G-Spam
    private static final float LOOP_SCALE = 0.8f;      // Schwert-Groesse waehrend des Inspects
    private static final long SPAM_WINDOW_MS = 550;    // G innerhalb dieser Zeit nach dem letzten G = Schubs statt Stopp
    private static final long STOP_MS = 500;
    private static final long VANISH_MS = 2200;
    private static final long APPEAR_MS = 1400;
    private static final float LIFT_X = -0.32f, LIFT_Y = 0.24f, LIFT_Z = 0.0f;   // Hebe-Position
    private static final float PX = 0.56f, PY = -0.52f, PZ = -0.72f;            // Drehpunkt = Griff / Hand

    private static Mode mode = Mode.NONE;
    private static long startMs, loopStartMs, lastPressMs;
    private static boolean hidden;
    private static int lastSlot = -1;
    private static long lastNs;

    // aktuelle Pose
    private static float lift, angle, omega, flip, scale = 1f;
    private static float stopLift, stopAngle, stopFlip, stopScale;

    // ---------- Steuerung ----------
    public static void pressInspect() {
        if (hidden || mode == Mode.VANISH || mode == Mode.APPEAR) return;
        long now = System.currentTimeMillis();
        if (mode == Mode.LOOP) {
            if (now - lastPressMs < SPAM_WINDOW_MS) {          // Spam: Schubs + kurz neu hochheben
                omega += KICK_OMEGA;
                lift = Math.max(0f, lift - 0.35f);
            } else {                                           // zweiter Druck mit Pause: stoppen
                stopLift = lift; stopFlip = flip; stopScale = scale;
                stopAngle = ((angle % 360f) + 360f) % 360f;
                mode = Mode.STOP; startMs = now;
            }
        } else {
            if (mode != Mode.STOP) { lift = 0f; angle = 0f; flip = 0f; }
            else { angle = ((angle % 360f) + 360f) % 360f; }
            omega = 0f;
            mode = Mode.LOOP;
            loopStartMs = now; startMs = now;
        }
        lastPressMs = now;
    }

    public static void toggleVanish() {
        if (mode != Mode.NONE) return;
        mode = hidden ? Mode.APPEAR : Mode.VANISH;
        startMs = System.currentTimeMillis();
    }

    public static void reset() { mode = Mode.NONE; hidden = false; lift = 0f; angle = 0f; omega = 0f; flip = 0f; scale = 1f; }

    public static void tick(Minecraft mc) {
        int slot = mc.player.getInventory().getSelectedSlot();
        boolean sword = mc.player.getMainHandItem().is(ItemTags.SWORDS);
        if (slot != lastSlot || !sword) { lastSlot = slot; reset(); return; }
        // Schlagen bricht den Inspect ab
        if ((mode != Mode.NONE || hidden) && mc.options.keyAttack.isDown()) { reset(); return; }
        if (mode == Mode.STOP && progress() >= 1f) { mode = Mode.NONE; lift = 0f; angle = 0f; flip = 0f; scale = 1f; }
        if ((mode == Mode.VANISH || mode == Mode.APPEAR) && progress() >= 1f) {
            hidden = (mode == Mode.VANISH);
            mode = Mode.NONE;
        }
    }

    /** Soll die Hand gezeichnet werden? (Vanilla zeichnet bei gehaltenem Item keine Hand) */
    public static boolean wantsArm() { return mode != Mode.NONE || hidden; }

    // ---------- Hilfsfunktionen ----------
    private static long duration() {
        return switch (mode) { case STOP -> STOP_MS; case VANISH -> VANISH_MS; case APPEAR -> APPEAR_MS; default -> 1; };
    }

    private static float progress() {
        return Math.min(1f, (System.currentTimeMillis() - startMs) / (float) duration());
    }

    private static void rot(PoseStack ps, Quaternionf q) { ps.mulPose(new Matrix4f().rotation(q)); }
    private static float clamp01(float x) { return Math.max(0f, Math.min(1f, x)); }
    private static float smooth(float x) { x = clamp01(x); return x * x * (3f - 2f * x); }

    private static float easeInOut(float x) {
        x = clamp01(x);
        return x < 0.5f ? 4f * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 3) / 2f;
    }

    /** Aktualisiert die Pose fuer diesen Frame (zeitbasiert, daher ruckelfrei). */
    private static void compute() {
        long nowNs = System.nanoTime();
        float dt = lastNs == 0 ? 0f : Math.min(0.05f, (nowNs - lastNs) / 1_000_000_000f);
        lastNs = nowNs;

        switch (mode) {
            case LOOP -> {
                float loopT = (System.currentTimeMillis() - loopStartMs) / 1000f;
                lift += (1f - lift) * Math.min(1f, 9f * dt);
                float ramp = smooth(loopT / 0.7f);                       // Spin faehrt sanft hoch
                float target = BASE_OMEGA * ramp;
                omega += (target - omega) * Math.min(1f, 3f * dt);       // Schubs klingt von selbst ab
                angle += omega * dt;
                flip = 35f * (float) Math.sin(2 * Math.PI * loopT / 2.2); // Klinge zeigt abwechselnd ihre Seiten
                scale = 1f + (LOOP_SCALE - 1f) * smooth(lift);
            }
            case STOP -> {
                float s = smooth(progress());
                lift = stopLift * (1f - s);
                float target = stopAngle > 180f ? 360f : 0f;
                angle = stopAngle + (target - stopAngle) * s;
                flip = stopFlip * (1f - s);
                scale = stopScale + (1f - stopScale) * s;
            }
            case VANISH -> {
                float p = progress();
                lift = smooth(p / 0.18f);
                angle = 720f * easeInOut((p - 0.18f) / 0.5f);
                flip = 0f;
                scale = Math.max(0.001f, LOOP_SCALE * (1f - smooth((p - 0.55f) / 0.4f)));
            }
            case APPEAR -> {
                float p = progress();
                lift = 1f - smooth((p - 0.45f) / 0.55f);
                angle = -360f * (1f - easeInOut(p / 0.8f));
                flip = 0f;
                scale = Math.max(0.001f, LOOP_SCALE * smooth(p / 0.5f));
            }
            default -> { }
        }
    }

    /** Transform fuers SCHWERT (Kamera-Raum, Drehpunkt = Griff). */
    public static void applyTransform(PoseStack ps) {
        if (mode == Mode.NONE) {
            if (hidden) ps.scale(0.001f, 0.001f, 0.001f);   // Schwert ist verschwunden
            return;
        }
        compute();
        ps.translate(LIFT_X * lift, LIFT_Y * lift, LIFT_Z * lift);
        ps.translate(PX, PY, PZ);
        rot(ps, Axis.ZP.rotationDegrees(angle));                       // Dauer-Spin um den Griff
        rot(ps, Axis.YP.rotationDegrees(flip - 30f * lift));           // Seitenwechsel + Grundkippung
        rot(ps, Axis.ZP.rotationDegrees(10f * lift));
        ps.scale(scale, scale, scale);
        ps.translate(-PX, -PY, -PZ);
    }

    /** Transform fuer die HAND: folgt dem Griff, dreht aber nicht mit dem Spin mit. */
    public static void applyArmTransform(PoseStack ps) {
        if (mode == Mode.NONE) return;
        ps.translate(LIFT_X * lift, LIFT_Y * lift, LIFT_Z * lift);
        ps.translate(PX, PY, PZ);
        rot(ps, Axis.YP.rotationDegrees(-30f * lift));
        rot(ps, Axis.ZP.rotationDegrees(10f * lift));
        ps.translate(-PX, -PY, -PZ);
    }
}
