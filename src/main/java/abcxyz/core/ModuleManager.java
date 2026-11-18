package abcxyz.core;

import abcxyz.gui.ClickGuiScreen;
import abcxyz.module.combat.AutoCrystal;
import abcxyz.module.combat.AutoTotem;
import abcxyz.module.combat.Hitbox;
import abcxyz.module.combat.SafeAnchor;
import abcxyz.module.combat.TriggerBot;
import abcxyz.module.combat.Velocity;
import abcxyz.module.misc.AutoRespawn;
import abcxyz.module.misc.EasyPlace;
import abcxyz.module.movement.Sprint;
import abcxyz.module.render.ESP;
import abcxyz.module.render.FullBright;
import abcxyz.module.render.HandView;
import abcxyz.module.render.HealthIndicator;
import abcxyz.module.render.ModuleList;
import abcxyz.module.render.NoHurtCam;
import abcxyz.module.render.Skybox;
import abcxyz.module.render.Tracers;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleManager {
    public final List<Module> modules = new ArrayList<>();
    private final Map<Module, Boolean> keyDown = new HashMap<>();
    private boolean guiKeyDown = false;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public ModuleManager() {
        modules.add(new TriggerBot());
        modules.add(new Hitbox());
        modules.add(new Velocity());
        modules.add(new AutoTotem());
        modules.add(new AutoCrystal());
        modules.add(new SafeAnchor());
        modules.add(new Sprint());
        modules.add(new HealthIndicator());
        modules.add(new ESP());
        modules.add(new Tracers());
        modules.add(new FullBright());
        modules.add(new NoHurtCam());
        modules.add(new Skybox());
        modules.add(new HandView());
        modules.add(new ModuleList());
        modules.add(new AutoRespawn());
        modules.add(new EasyPlace());
        boolean hadConfig = Config.load(this);
        if (!hadConfig) {
            get(ModuleList.class).setEnabled(true, false);
            get(HealthIndicator.class).setEnabled(true, false);
        }
    }

    public <T extends Module> T get(Class<T> type) {
        for (Module m : modules) {
            if (type.isInstance(m)) return type.cast(m);
        }
        return null;
    }

    public List<Module> byCategory(Category category) {
        List<Module> list = new ArrayList<>();
        for (Module m : modules) {
            if (m.category == category) list.add(m);
        }
        return list;
    }

    public void tick() {
        if (mc.getWindow() == null) return;
        long window = mc.getWindow().getHandle();

        boolean guiPressed = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        if (guiPressed && !guiKeyDown && mc.currentScreen == null) {
            mc.setScreen(new ClickGuiScreen());
        }
        guiKeyDown = guiPressed;

        if (mc.currentScreen == null) {
            for (Module m : modules) {
                if (m.key < 0) continue;
                boolean down = InputUtil.isKeyPressed(window, m.key);
                if (down && !keyDown.getOrDefault(m, false)) m.toggle();
                keyDown.put(m, down);
            }
        }

        if (mc.player == null || mc.world == null) return;
        for (Module m : modules) {
            if (!m.isEnabled()) continue;
            try {
                m.onTick();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public void frame(WorldRenderContext context) {
        if (mc.player == null || mc.world == null) return;
        for (Module m : modules) {
            if (!m.isEnabled()) continue;
            try {
                m.onFrame();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public void render3D(WorldRenderContext context) {
        if (mc.player == null || mc.world == null) return;
        for (Module m : modules) {
            if (!m.isEnabled()) continue;
            try {
                m.onRender3D(context);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public void hud(DrawContext context) {
        for (Module m : modules) {
            if (!m.isEnabled()) continue;
            try {
                m.onHud(context);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
