package de.inspectanim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Karambit-Style Inspect nur fuer Schwerter.
 *  G           : startet den Inspect, der in Dauerschleife laeuft (Wirbel um den Griff, Seitenwechsel, ...)
 *  G schnell wiederholt (Spam): der Anfang des Inspects startet jedes Mal neu
 *  G (nach kurzer Pause) : Inspect stoppt, Schwert faehrt sanft zurueck
 *  Schwert aus der Hand / Slot wechseln: Inspect wird sofort beendet
 *  Shift+G     : Schwert wegwirbeln und verschwinden lassen / wieder zurueckholen
 */
public final class InspectState {
    private InspectState() {}

    public enum Mode { NONE, LOOP, STOP, VANISH, APPEAR }

    // ---- Einstellungen: hier kannst du alles anpassen ----
    private static final long SPAM_WINDOW_MS = 550;    // G innerhalb dieser Zeit nach dem letzten G = Neustart statt Stopp
    private static final long INTRO_MS = 450;          // Zeit zum Hochheben des Schwerts
    private static final long CYCLE_MS = 2800;         // Dauer eines Durchlaufs in der Schleife
    private static final long STOP_MS = 500;           // Dauer des sanften Zurueckfahrens
    private static final long VANISH_MS = 2200;
    private static final long APPEAR_MS = 1400;
    private static final float TWIRL_ROUNDS = 2f;      // Umdrehungen um den Griff pro Durchlauf
    private static final float LIFT_X = -0.42f, LIFT_Y = 0.30f, LIFT_Z = 0.18f; // Hebe-Position
    private static final float PX = 0.56f, PY = -0.52f, PZ = -0.72f;            // Drehpunkt = Griff / Hand

    private static Mode mode = Mode.NONE;
    private static long startMs;
    private static long lastPressMs;
    private static boolean hidden;
    private static int lastSlot = -1;

    // zuletzt berechnete Pose (fuer sanftes Stoppen und Spam-Neustart)
    private static float cLift, cTwirl, cFlip, cScale = 1f;
    private static float introFrom;
    private static float stopLift, stopTwirl, stopFlip;

    // ---------- Steuerung ----------
    public static void pressInspect() {
        if (hidden || mode == Mode.VANISH || mode == Mode.APPEAR) return;
        long now = System.currentTimeMillis();
        if (mode == Mode.LOOP) {
            if (now - lastPressMs < SPAM_WINDOW_MS) {   // Spam: Anfang nochmal abspielen
                introFrom = cLift;
                startMs = now;
            } else {                                    // normaler zweiter Druck: stoppen
                stopLift = cLift;
                stopTwirl = ((cTwirl % 360f) + 360f) % 360f;
                stopFlip = cFlip;
                mode = Mode.STOP;
                startMs = now;
            }
        } else {                                        // NONE oder STOP: (neu) starten
            introFrom = (mode == Mode.STOP) ? cLift : 0f;
            mode = Mode.LOOP;
            startMs = now;
        }
        lastPressMs = now;
    }

    public static void toggleVanish() {
        if (mode != Mode.NONE) return;
        mode = hidden ? Mode.APPEAR : Mode.VANISH;
        startMs = System.currentTimeMillis();
    }

    public static void reset() { mode = Mode.NONE; hidden = false; }

    public static void tick(Minecraft mc) {
        int slot = mc.player.getInventory().getSelectedSlot();
        boolean sword = mc.player.getMainHandItem().is(ItemTags.SWORDS);
        if (slot != lastSlot || !sword) { lastSlot = slot; reset(); return; }
        if (mode == Mode.STOP && progress() >= 1f) mode = Mode.NONE;
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

    private static float easeOutBack(float x) {
        x = clamp01(x);
        float c1 = 1.70158f, c3 = c1 + 1f, t = x - 1f;
        return 1f + c3 * t * t * t + c1 * t * t;
    }

    /** Berechnet Hebung, Wirbel, Seitenwechsel und Groesse fuer den aktuellen Moment. */
    private static void compute() {
        cScale = 1f;
        switch (mode) {
            case LOOP -> {
                long t = System.currentTimeMillis() - startMs;
                cLift = introFrom + (1f - introFrom) * smooth(t / (float) INTRO_MS);
                long ct = t - 200;                                   // Wirbel startet kurz nach dem Hochheben
                if (ct < 0) { cTwirl = 0f; cFlip = 0f; return; }
                float ph = (ct % CYCLE_MS) / (float) CYCLE_MS;
                cTwirl = ph < 0.5f ? 360f * TWIRL_ROUNDS * easeInOut(ph / 0.5f) : 360f * TWIRL_ROUNDS;
                if (ph < 0.55f) cFlip = 0f;
                else if (ph < 0.8f) cFlip = 180f * easeOutBack((ph - 0.55f) / 0.25f);   // andere Seite zeigen
                else if (ph < 0.92f) cFlip = 180f * (1f - smooth((ph - 0.8f) / 0.12f)); // zurueckdrehen
                else cFlip = 0f;
            }
            case STOP -> {
                float p = progress(), s = smooth(p);
                cLift = stopLift * (1f - s);
                float target = stopTwirl > 180f ? 360f : 0f;
                cTwirl = stopTwirl + (target - stopTwirl) * s;
                cFlip = stopFlip * (1f - s);
            }
            case VANISH -> {
                float p = progress();
                cLift = smooth(p / 0.18f);
                cTwirl = 360f * TWIRL_ROUNDS * easeInOut((p - 0.18f) / 0.5f);
                cFlip = 0f;
                cScale = Math.max(0.001f, 1f - smooth((p - 0.55f) / 0.4f));
            }
            case APPEAR -> {
                float p = progress();
                cLift = 1f - smooth((p - 0.45f) / 0.55f);
                cTwirl = -360f * (1f - easeInOut(p / 0.8f));
                cFlip = 0f;
                cScale = Math.max(0.001f, smooth(p / 0.5f));
            }
            default -> { cLift = 0f; cTwirl = 0f; cFlip = 0f; }
        }
    }

    /** Transform fuers SCHWERT (Kamera-Raum, Drehpunkt = Griff). */
    public static void applyTransform(PoseStack ps) {
        if (mode == Mode.NONE) {
            if (hidden) ps.scale(0.001f, 0.001f, 0.001f);   // Schwert ist verschwunden
            return;
        }
        compute();
        ps.translate(LIFT_X * cLift, LIFT_Y * cLift, LIFT_Z * cLift);
        ps.translate(PX, PY, PZ);
        rot(ps, Axis.ZP.rotationDegrees(cTwirl));                                  // Wirbel um den Griff
        rot(ps, Axis.YP.rotationDegrees(cFlip - 35f * cLift));                     // Seite zeigen + Grundkippung
        rot(ps, Axis.ZP.rotationDegrees(15f * cLift));
        rot(ps, Axis.XP.rotationDegrees(6f * cLift * (float) Math.sin(6 * Math.PI * (System.currentTimeMillis() % 4000) / 4000.0)));
        ps.scale(cScale, cScale, cScale);
        ps.translate(-PX, -PY, -PZ);
    }

    /** Transform fuer die HAND: folgt dem Griff, dreht aber nicht mit dem Wirbel mit. */
    public static void applyArmTransform(PoseStack ps) {
        if (mode == Mode.NONE) return;
        compute();
        ps.translate(LIFT_X * cLift, LIFT_Y * cLift, LIFT_Z * cLift);
        ps.translate(PX, PY, PZ);
        rot(ps, Axis.YP.rotationDegrees(-35f * cLift));
        rot(ps, Axis.ZP.rotationDegrees(15f * cLift));
        ps.translate(-PX, -PY, -PZ);
    }
}
