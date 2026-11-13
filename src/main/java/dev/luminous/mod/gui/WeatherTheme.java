package dev.luminous.mod.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.api.utils.render.Render2DUtil;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Colors, drawing helpers and panel layout for the Weather Client ClickGui.
 */
public final class WeatherTheme {
    public static final int HEADER_TOP = 0xFF2D8CE0;
    public static final int HEADER_BOTTOM = 0xFF1B4F9E;
    public static final int BORDER = 0xFF26B4F0;
    public static final int BODY = 0xE6070D2A;
    public static final int ROW_ON_LEFT = 0xFF1F78C8;
    public static final int ROW_ON_RIGHT = 0xFF123F80;
    public static final int ROW_ON_LEFT_HOVER = 0xFF2F90E0;
    public static final int ACCENT = 0xFF35D6FF;
    public static final int TEXT_OFF = 0xFFC8D4F0;
    public static final int TEXT_ON = 0xFFFFFFFF;
    public static final int DOTS = 0xFFB8D8FF;

    /**
     * false = only show the Flux module list (panels below).
     * true  = also show every other module of the base client in their own category panels.
     */
    public static final boolean SHOW_ALL_MODULES = false;

    /** Panel name -> module names (shown in this order, not alphabetical). */
    public static final Map<String, String[]> PANELS = new LinkedHashMap<>();

    static {
        PANELS.put("Combat", new String[]{"AntiRegear", "AutoCrystal",
                "PistonCrystal", "AutoEZ", "AutoRegear"});
        PANELS.put("Movement", new String[]{"ElytraFly", "Velocity"});
        PANELS.put("Render", new String[]{"NameTags", "NoRender", "FullBright", "Skybox"});
        PANELS.put("Player", new String[]{"AutoArmor", "FakePlayer", "HotbarSaver", "InvSaver", "Nuker", "AutoTool"});
        PANELS.put("Weather", new String[]{"ClickGui", "ClientSetting"});
    }

    private WeatherTheme() {
    }

    public static void header(MatrixStack ms, float x1, float y1, float x2, float y2) {
        gradientV(ms, x1, y1, x2, y2, HEADER_TOP, HEADER_BOTTOM);
        outline(ms, x1, y1, x2, y2, BORDER);
    }

    /** 1px outline drawn just inside the rectangle. */
    public static void outline(MatrixStack ms, float x1, float y1, float x2, float y2, int color) {
        Render2DUtil.rect(ms, x1, y1, x2, y1 + 1, color);
        Render2DUtil.rect(ms, x1, y2 - 1, x2, y2, color);
        Render2DUtil.rect(ms, x1, y1, x1 + 1, y2, color);
        Render2DUtil.rect(ms, x2 - 1, y1, x2, y2, color);
    }

    public static void gradientV(MatrixStack ms, float x1, float y1, float x2, float y2, int top, int bottom) {
        Matrix4f m = ms.peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        b.vertex(m, x1, y1, 0f).color(top);
        b.vertex(m, x1, y2, 0f).color(bottom);
        b.vertex(m, x2, y2, 0f).color(bottom);
        b.vertex(m, x2, y1, 0f).color(top);
        BufferRenderer.drawWithGlobalProgram(b.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
