package dev.luminous.mod.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.Alien;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.mod.Mod;
import dev.luminous.mod.gui.items.Component;
import dev.luminous.mod.gui.items.Item;
import dev.luminous.mod.gui.items.buttons.ModuleButton;
import dev.luminous.mod.modules.Module;
import dev.luminous.core.impl.FontManager;
import dev.luminous.mod.modules.impl.client.ClickGui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;

public class ClickGuiScreen extends Screen {
    private static ClickGuiScreen INSTANCE;

    static {
        INSTANCE = new ClickGuiScreen();
    }

    public static final int PANEL_STEP = 112;
    private final ArrayList<Component> components = new ArrayList<>();

    public ClickGuiScreen() {
        super(Text.literal("Weather Client"));
        setInstance();
        load();
    }

    public static ClickGuiScreen getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ClickGuiScreen();
        }
        return INSTANCE;
    }

    private void setInstance() {
        INSTANCE = this;
    }

    private static Module.Category iconFor(String panel) {
        return switch (panel) {
            case "Combat" -> Module.Category.Combat;
            case "Movement" -> Module.Category.Movement;
            case "Render" -> Module.Category.Render;
            case "Player" -> Module.Category.Player;
            default -> Module.Category.Client;
        };
    }

    private void load() {
        if (WeatherTheme.SHOW_ALL_MODULES) {
            int x = -PANEL_STEP + 34;
            for (final Module.Category category : Module.Category.values()) {
                this.components.add(new Component(category.toString(), category, x += PANEL_STEP, 24, true) {
                    @Override
                    public void setupItems() {
                        for (Module module : Alien.MODULE.getModules()) {
                            if (module.getCategory().equals(category)) {
                                this.addButton(new ModuleButton(module));
                            }
                        }
                    }
                });
            }
            this.components.forEach(c -> c.getItems().sort(Comparator.comparing(Mod::getName)));
            return;
        }
        int x = -PANEL_STEP + 34;
        for (Map.Entry<String, String[]> entry : WeatherTheme.PANELS.entrySet()) {
            final String[] names = entry.getValue();
            this.components.add(new Component(entry.getKey(), iconFor(entry.getKey()), x += PANEL_STEP, 24, true) {
                @Override
                public void setupItems() {
                    // keep the order given in WeatherTheme.PANELS
                    for (String name : names) {
                        for (Module module : Alien.MODULE.getModules()) {
                            if (module.getName().equalsIgnoreCase(name)) {
                                this.addButton(new ModuleButton(module));
                                break;
                            }
                        }
                    }
                }
            });
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RenderSystem.setShaderColor(1, 1, 1, (float) ClickGui.getInstance().alphaValue);
        Item.context = context;
        renderBackground(context, mouseX, mouseY, delta);
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        // dark navy tint
        context.fillGradient(0, 0, w, h, 0xB0050A1C, 0xCC0A1230);

        ClickGui gui = ClickGui.getInstance();
        if (gui.weather.getValue()) {
            WeatherEffects.render(context, w, h, gui.rain.getValue(), gui.lightning.getValue(),
                    gui.rainAmount.getValueInt(), gui.lightningRate.getValueFloat(),
                    gui.weatherSound.getValue(), gui.weatherVolume.getValueFloat());
        }

        drawTitle(context, w);
        this.components.forEach(components -> components.drawScreen(context, mouseX, mouseY, delta));
        drawHint(context, w, h);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private void drawTitle(DrawContext context, int w) {
        String title = "Weather Client";
        float scale = 1.6f;
        float tw = FontManager.ui.getWidth(title) * scale;
        float x = (w - tw) / 2f;
        context.getMatrices().push();
        context.getMatrices().translate(x, 6, 0);
        context.getMatrices().scale(scale, scale, 1f);
        FontManager.ui.drawString(context.getMatrices(), title, 0, 0, 0xFF35D6FF, true);
        context.getMatrices().pop();
        float lineY = 6 + FontManager.ui.getFontHeight() * scale + 3;
        dev.luminous.api.utils.render.Render2DUtil.rect(context.getMatrices(), x - 8, lineY, x + tw + 8, lineY + 1.5f, 0xFF1F78C8);
    }

    private void drawHint(DrawContext context, int w, int h) {
        String hint = "Left: toggle  |  Right: settings  |  Drag title: move";
        float tw = FontManager.ui.getWidth(hint);
        FontManager.ui.drawString(context.getMatrices(), hint, (int) ((w - tw) / 2f), h - 12, 0xFF9FB4DA, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int clickedButton) {
        this.components.forEach(components -> components.mouseClicked((int) mouseX, (int) mouseY, clickedButton));
        return super.mouseClicked(mouseX, mouseY, clickedButton);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int releaseButton) {
        this.components.forEach(components -> components.mouseReleased((int) mouseX, (int) mouseY, releaseButton));
        return super.mouseReleased(mouseX, mouseY, releaseButton);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (InputUtil.isKeyPressed(Wrapper.mc.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT)) {
            if (verticalAmount < 0) {
                this.components.forEach(component -> component.setX(component.getX() - 15));
            } else if (verticalAmount > 0) {
                this.components.forEach(component -> component.setX(component.getX() + 15));
            }
        } else {
            if (verticalAmount < 0) {
                this.components.forEach(component -> component.setY(component.getY() - 15));
            } else if (verticalAmount > 0) {
                this.components.forEach(component -> component.setY(component.getY() + 15));
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        this.components.forEach(component -> component.onKeyPressed(keyCode));
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        this.components.forEach(component -> component.onKeyTyped(chr, modifiers));
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    public final ArrayList<Component> getComponents() {
        return this.components;
    }

    public int getTextOffset() {
        return -ClickGui.getInstance().textOffset.getValueInt() - 6;
    }
}
