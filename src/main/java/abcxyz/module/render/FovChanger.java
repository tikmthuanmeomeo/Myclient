package abcxyz.module.render;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.Notify;
import abcxyz.core.NumSetting;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Sets your field of view, also above the 110 the video settings allow (up to 190), and can keep the held item at a
 * comfortable distance and stop the view from reaching through walls when the FOV is very wide.
 * <p>
 * Ported from ArrowAddon (MIT License, (c) Nhân-Tài (ArrowAddon)), see THIRD_PARTY_NOTICES/ArrowAddon-LICENSE.txt.
 */
public class FovChanger extends Module {
    private static final int PROBE = 101;
    private static final double NEAR_CHECK = 0.055;
    private static final double CHECK_CAP = 1.2;
    private static final double RISE_PER_TICK = 4.0;

    private final NumSetting fov = add(new NumSetting("Fov", 120, 30, 190, 1));
    private final BoolSetting allowOver179 = add(new BoolSetting("AllowOver179", false));
    private final BoolSetting noEffects = add(new BoolSetting("DisableFovEffects", true));
    private final NumSetting handDistance = add(new NumSetting("HandDistance", 100, 0, 300, 1));
    private final BoolSetting handFollowsFov = add(new BoolSetting("HandFollowsFov", true)).with(handDistance);
    private final BoolSetting antiClip = add(new BoolSetting("AntiClip", true));

    private Integer originalFov;
    private Double originalEffectScale;
    private Field valueField;
    private boolean fieldSearched;
    private boolean warned;
    private double smoothFov = -1.0;
    private int appliedFov = 70;

    public FovChanger() {
        super("FovChanger", "Set your FOV, up to 190", Category.Render);
        allowOver179.label = "Allow over 179";
        noEffects.label = "Disable FOV effects";
        handFollowsFov.label = "Hand follows FOV";
        handDistance.label = "Hand distance %";
        antiClip.label = "Anti clip";
    }

    @Override
    public String info() {
        return String.valueOf(appliedFov);
    }

    @Override
    public void onEnable() {
        if (mc.options == null) return;
        warned = false;
        smoothFov = -1.0;
        originalFov = mc.options.getFov().getValue();
        originalEffectScale = mc.options.getFovEffectScale().getValue();
        try {
            apply();
        } catch (Throwable t) {
            Notify.chat("FovChanger", "Could not apply the FOV: " + t.getClass().getSimpleName());
        }
    }

    @Override
    public void onDisable() {
        if (mc.options == null) return;
        try {
            if (originalFov != null) setFovRaw(originalFov);
            if (originalEffectScale != null) mc.options.getFovEffectScale().setValue(originalEffectScale);
        } catch (Throwable ignored) {
        }
        originalFov = null;
        originalEffectScale = null;
        smoothFov = -1.0;
        appliedFov = 70;
    }

    @Override
    public void onTick() {
        if (mc.options == null) return;
        try {
            apply();
        } catch (Throwable t) {
            if (warned) return;
            warned = true;
            Notify.chat("FovChanger", "Could not apply the FOV: " + t.getClass().getSimpleName());
        }
    }

    /** Called before the first-person hand/item is drawn (HeldItemRendererMixin). */
    public void transformHand(MatrixStack matrices) {
        if (!handFollowsFov.on()) return;
        double factor = Math.max(0.0, (appliedFov - 70.0) / 70.0) * (handDistance.i() / 100.0);
        if (factor <= 0.0) return;
        matrices.translate(0.0, 0.0, -0.5 * factor);
    }

    private void setFovRaw(int value) throws Exception {
        SimpleOption<Integer> option = mc.options.getFov();
        Field f = findValueField(option);
        if (f != null) {
            if (!Integer.valueOf(value).equals(option.getValue())) f.set(option, value);
        } else {
            int clamped = Math.min(value, 110);
            if (!Integer.valueOf(clamped).equals(option.getValue())) option.setValue(clamped);
        }
    }

    private void apply() throws Exception {
        int userTarget = allowOver179.on() ? fov.i() : Math.min(fov.i(), 179);
        int target = userTarget;
        if (antiClip.on()) target = Math.min(userTarget, allowedFovNearWalls(userTarget));
        if (smoothFov < 0.0) smoothFov = target;
        smoothFov = target < smoothFov ? target : Math.min(target, smoothFov + RISE_PER_TICK);
        int value = (int) Math.round(smoothFov);
        appliedFov = value;

        SimpleOption<Integer> option = mc.options.getFov();
        Field f = findValueField(option);
        if (f != null) {
            if (!Integer.valueOf(value).equals(option.getValue())) f.set(option, value);
        } else {
            int clamped = Math.min(value, 110);
            if (!Integer.valueOf(clamped).equals(option.getValue())) option.setValue(clamped);
            if (!warned) {
                warned = true;
                Notify.chat("FovChanger", "Cannot write a FOV above 110 in this game version, using 110.");
            }
        }
        if (noEffects.on()) {
            SimpleOption<Double> effects = mc.options.getFovEffectScale();
            if (effects.getValue() == null || Math.abs(effects.getValue()) > 1.0E-6) effects.setValue(0.0);
        }
    }

    private int allowedFovNearWalls(int maxFov) {
        if (mc.player == null || mc.world == null || !mc.options.getPerspective().isFirstPerson()) return maxFov;
        if (!blocked(maxFov)) return maxFov;
        int lo = 30;
        int hi = maxFov;
        if (blocked(lo)) return lo;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (blocked(mid)) hi = mid;
            else lo = mid;
        }
        return lo;
    }

    /** Would the edges of a view with this FOV poke into a block right in front of the eyes? */
    private boolean blocked(double fovDeg) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d f = mc.player.getRotationVec(1.0f).normalize();
        double yawRad = Math.toRadians(mc.player.getYaw());
        Vec3d r = new Vec3d(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
        Vec3d u = r.crossProduct(f).normalize();
        double aspect = (double) mc.getWindow().getFramebufferWidth() / (double) Math.max(1, mc.getWindow().getFramebufferHeight());
        double tanV = Math.tan(Math.toRadians(fovDeg) / 2.0);
        double tanH = tanV * aspect;
        for (int sx = -1; sx <= 1; sx++) {
            for (int sy = -1; sy <= 1; sy++) {
                if (sx == 0 && sy == 0) continue;
                Vec3d dir = f.add(r.multiply(tanH * sx)).add(u.multiply(tanV * sy));
                double len = Math.min(dir.length() * NEAR_CHECK, CHECK_CAP);
                Vec3d end = eye.add(dir.normalize().multiply(len));
                BlockHitResult hit = mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE, (Entity) mc.player));
                if (hit.getType() != HitResult.Type.MISS) return true;
            }
        }
        return false;
    }

    /**
     * The FOV slider refuses values above 110, so the value is written straight into the option: the field is found
     * by setting a probe value and looking for the field that holds it (works with any field names).
     */
    private Field findValueField(SimpleOption<Integer> option) {
        if (fieldSearched) return valueField;
        fieldSearched = true;
        Integer before = option.getValue();
        try {
            option.setValue(PROBE);
            for (Field f : SimpleOption.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != Object.class) continue;
                f.setAccessible(true);
                Object v = f.get(option);
                if (v instanceof Integer i && i == PROBE) {
                    valueField = f;
                    break;
                }
            }
        } catch (Throwable ignored) {
            valueField = null;
        } finally {
            try {
                if (before != null) option.setValue(before);
            } catch (Throwable ignored) {
            }
        }
        return valueField;
    }
}
