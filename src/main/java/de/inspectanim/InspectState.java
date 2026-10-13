package de.inspectanim;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Karambit-Style Inspect nur fuer Schwerter.
 *  G                    : Schwert springt hoch und dreht sich dauerhaft in EINE Richtung um den Griff
 *  G (Spam)             : jedes G gibt einen Extra-Schubs + Pop-Effekt, der Inspect bleibt an
 *  G (nach Pause)       : Inspect stoppt - das Schwert dreht sauber zu Ende und faehrt zurueck
 *  Schlagen / Schwert aus der Hand / Slot wechseln : Inspect wird sofort beendet
 *  Shift+G              : Schwert wegwirbeln und verschwinden lassen / wieder zurueckholen
 */
public final class InspectState {
    private InspectState() {}

    public enum Mode { NONE, LOOP, STOP, VANISH, APPEAR }

    // ---- Einstellungen: hier kannst du alles anpassen ----
    private static final float LOOP_SCALE = 0.55f;     // Schwert-Groesse beim Inspect (1.0 = normal)
    private static final float BASE_OMEGA = 640f;      // Spin-Tempo in Grad/Sekunde (360 = 1 Umdrehung pro Sekunde)
    private static final float FLOURISH = 1.5f;        // wie stark der Spin regelmaessig "aufdreht" (0 = aus)
    private static final float FLOURISH_EVERY = 1.7f;  // alle X Sekunden ein Aufdreh-Moment
    private static final float KICK_OMEGA = 1500f;     // Extra-Schubs pro G-Spam
    private static final long SPAM_WINDOW_MS = 1400;   // G innerhalb dieser Zeit nach dem letzten G = Spam (stoppt nicht)
    private static final float SPIN_DIRECTION = -1f;   // Drehrichtung: -1 oder 1 (einfach umdrehen, falls es falsch herum ist)
    private static final float PLANE_TILT = -50f;      // Neigung der Drehebene (wie das Karambit schraeg im Bild kreist)
    private static final long STOP_MS = 700;
    private static final long VANISH_MS = 2200;
    private static final long APPEAR_MS = 1400;
    private static final float LIFT_X = -0.30f, LIFT_Y = 0.24f, LIFT_Z = -0.08f;  // Hebe-Position
    private static final float PX = 0.56f, PY = -0.52f, PZ = -0.72f;             // Drehpunkt = Griff / Hand

    private static Mode mode = Mode.NONE;
    private static long startMs, loopStartMs, lastPressMs;
    private static boolean hidden;
    private static int lastSlot = -1;
    private static long lastNs;

    // aktuelle Pose
    private static float lift, liftVel, angle, omega, scale = 1f, pop, bob;
    private static float stopLift, stopAngle, stopTarget, stopScale;

    // ---------- Steuerung ----------
    public static void pressInspect() {
        if (hidden || mode == Mode.VANISH || mode == Mode.APPEAR) return;
        long now = System.currentTimeMillis();
        if (mode == Mode.LOOP && now - lastPressMs >= SPAM_WINDOW_MS) {
            // Pause seit dem letzten G -> Inspect stoppen (dreht noch sauber zu Ende)
            stopLift = lift; stopScale = scale; stopAngle = angle;
            float target = (float) (Math.floor(angle / 360f) + 1) * 360f;
            if (target - angle < 120f) target += 360f;
            stopTarget = target;
            mode = Mode.STOP; startMs = now;
        } else if (mode == Mode.LOOP) {
            // Spam: Schubs + Pop, Inspect laeuft weiter
            omega += KICK_OMEGA;
            liftVel += 2.5f;
            pop = 1f;
        } else {
            if (mode != Mode.STOP) { lift = 0f; angle = 0f; omega = 0f; }
            else { angle = angle % 360f; }
            liftVel = 2.0f; pop = 0.6f;
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

    public static void reset() {
        mode = Mode.NONE; hidden = false;
        lift = 0f; liftVel = 0f; angle = 0f; omega = 0f; scale = 1f; pop = 0f; bob = 0f;
    }

    public static void tick(Minecraft mc) {
        int slot = mc.player.getInventory().getSelectedSlot();
        boolean sword = mc.player.getMainHandItem().is(ItemTags.SWORDS);
        if (slot != lastSlot || !sword) { lastSlot = slot; reset(); return; }
        // Schlagen bricht den Inspect ab
        if ((mode != Mode.NONE || hidden) && mc.options.keyAttack.isDown()) { reset(); return; }
        if (mode == Mode.STOP && progress() >= 1f) reset();
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

    private static float easeOut(float x) { x = clamp01(x); float t = 1f - x; return 1f - t * t * t; }

    /** Aktualisiert die Pose fuer diesen Frame (zeitbasiert, daher ruckelfrei). */
    private static void compute() {
        long nowNs = System.nanoTime();
        float dt = lastNs == 0 ? 0f : Math.min(0.05f, (nowNs - lastNs) / 1_000_000_000f);
        lastNs = nowNs;

        switch (mode) {
            case LOOP -> {
                float loopT = (System.currentTimeMillis() - loopStartMs) / 1000f;
                // Hochheben als Feder -> kleiner Ueberschwinger = "Wurf"-Gefuehl
                liftVel += (180f * (1f - lift) - 16f * liftVel) * dt;
                lift += liftVel * dt;
                // Spin: immer vorwaerts, regelmaessig mit Aufdreh-Moment
                float phase = (float) Math.max(0.0, Math.sin(2 * Math.PI * loopT / FLOURISH_EVERY));
                float flourish = 1f + FLOURISH * phase * phase * phase * phase;
                float target = BASE_OMEGA * flourish * smooth(loopT / 0.6f);
                omega += (target - omega) * Math.min(1f, 3.5f * dt);
                angle += omega * dt;
                pop = Math.max(0f, pop - dt * 3.2f);
                bob = 0.018f * (float) Math.sin(2 * Math.PI * loopT / 0.9);
                scale = (1f + (LOOP_SCALE - 1f) * smooth(lift)) * (1f + 0.22f * pop + 0.07f * phase * phase);
            }
            case STOP -> {
                float p = progress();
                lift = stopLift * (1f - smooth(p));
                angle = stopAngle + (stopTarget - stopAngle) * easeOut(p);   // weiter in dieselbe Richtung
                scale = stopScale + (1f - stopScale) * smooth(p);
                bob = 0f;
            }
            case VANISH -> {
                float p = progress();
                lift = smooth(p / 0.18f);
                angle = 900f * easeInOut((p - 0.18f) / 0.5f);
                scale = Math.max(0.001f, LOOP_SCALE * (1f - smooth((p - 0.55f) / 0.4f)));
                bob = 0f;
            }
            case APPEAR -> {
                float p = progress();
                lift = 1f - smooth((p - 0.45f) / 0.55f);
                angle = -720f * (1f - easeInOut(p / 0.8f));
                scale = Math.max(0.001f, LOOP_SCALE * smooth(p / 0.5f));
                bob = 0f;
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
        ps.translate(LIFT_X * lift, LIFT_Y * lift + bob, LIFT_Z * lift);
        ps.translate(PX, PY, PZ);
        rot(ps, Axis.YP.rotationDegrees(PLANE_TILT * lift));   // feste, schraege Drehebene
        rot(ps, Axis.ZP.rotationDegrees(SPIN_DIRECTION * angle)); // Spin in EINE Richtung (wie das Karambit)
        ps.scale(scale, scale, scale);
        ps.translate(-PX, -PY, -PZ);
    }

    /** Transform fuer die HAND: folgt dem Griff, dreht aber nicht mit dem Spin mit. */
    public static void applyArmTransform(PoseStack ps) {
        if (mode == Mode.NONE) return;
        ps.translate(LIFT_X * lift, LIFT_Y * lift + bob, LIFT_Z * lift);
        ps.translate(PX, PY, PZ);
        rot(ps, Axis.YP.rotationDegrees(PLANE_TILT * lift));
        ps.translate(-PX, -PY, -PZ);
    }
}
