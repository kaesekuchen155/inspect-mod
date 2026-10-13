package de.inspectanim;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;

import java.lang.reflect.Method;

/**
 * Zeichnet den Arm des Spielers (Vanilla zeigt bei gehaltenem Schwert keine Hand).
 * Nutzt die Arm-Methode des Spiels per Reflection und sucht sie nach Namen/Parametern,
 * damit kleine Aenderungen in 26.3 nicht zum Absturz fuehren (Hand fehlt dann einfach).
 */
public final class InspectHand {
    private InspectHand() {}

    private static Method method;
    private static boolean searched, failed, announced;
    private static long lastDrawNs;

    public static void render(Object renderer, PoseStack ps, Object collector, int light) {
        if (failed || !InspectState.wantsArm()) return;
        if (!announced) { announced = true; System.out.println("[inspectanim] Hand-Mixin laeuft."); }
        long now = System.nanoTime();
        if (now - lastDrawNs < 2_000_000L) return;   // nur einmal pro Frame (Haupthand), nicht nochmal fuer die Nebenhand
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isInvisible()) return;
        try {
            if (!searched) {
                searched = true;
                for (Method m : renderer.getClass().getDeclaredMethods()) {
                    String n = m.getName().toLowerCase();
                    if (!n.contains("playerarm") || m.getReturnType() != void.class) continue;
                    if (canFill(m, collector)) { method = m; m.setAccessible(true); break; }
                }
                if (method == null) {
                    failed = true;
                    System.err.println("[inspectanim] Arm-Methode nicht gefunden - Hand wird nicht gezeichnet.");
                    for (Method m : renderer.getClass().getDeclaredMethods()) {
                        String n = m.getName().toLowerCase();
                        if (n.contains("arm") || n.contains("hand"))
                            System.err.println("[inspectanim]   Kandidat: " + m);
                    }
                    return;
                }
                System.out.println("[inspectanim] Arm-Methode gefunden: " + method);
            }
            HumanoidArm arm = mc.player.getMainArm();
            Class<?>[] types = method.getParameterTypes();
            Object[] args = new Object[types.length];
            for (int i = 0; i < types.length; i++) args[i] = pick(types[i], ps, collector, light, arm);

            ps.pushPose();
            InspectState.applyArmTransform(ps);
            method.invoke(renderer, args);
            ps.popPose();
            lastDrawNs = now;
        } catch (Throwable t) {
            failed = true;
            System.err.println("[inspectanim] Hand konnte nicht gezeichnet werden: " + t);
        }
    }

    private static boolean canFill(Method m, Object collector) {
        for (Class<?> t : m.getParameterTypes()) {
            if (t == PoseStack.class || t == int.class || t == float.class || t == HumanoidArm.class) continue;
            if (t.isInstance(collector)) continue;
            return false;
        }
        return true;
    }

    private static Object pick(Class<?> t, PoseStack ps, Object collector, int light, HumanoidArm arm) {
        if (t == PoseStack.class) return ps;
        if (t == int.class) return light;
        if (t == float.class) return 0f;           // Ausfahr-/Schwung-Fortschritt: Arm voll oben, kein Schwung
        if (t == HumanoidArm.class) return arm;
        return collector;
    }
}
