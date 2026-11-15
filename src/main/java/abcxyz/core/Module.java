package abcxyz.core;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public final String name;
    public final String description;
    public final Category category;
    public final List<Setting<?>> settings = new ArrayList<>();
    public int key = -1;
    public boolean expanded = false;
    protected final MinecraftClient mc = MinecraftClient.getInstance();
    private boolean enabled = false;

    protected Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    protected <T extends Setting<?>> T add(T setting) {
        settings.add(setting);
        return setting;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggle() {
        setEnabled(!enabled, true);
    }

    public void setEnabled(boolean value, boolean save) {
        if (enabled == value) return;
        enabled = value;
        try {
            if (value) onEnable();
            else onDisable();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (save) Config.save();
    }

    /** Short text shown after the name in the module list. */
    public String info() {
        return null;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    public void onTick() {
    }

    /** Called every rendered frame (much more often than onTick). */
    public void onFrame() {
    }

    public void onRender3D(WorldRenderContext context) {
    }

    public void onHud(DrawContext context) {
    }
}
