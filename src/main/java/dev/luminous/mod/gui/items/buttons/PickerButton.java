package dev.luminous.mod.gui.items.buttons;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.core.impl.CommandManager;
import dev.luminous.mod.gui.ClickGuiScreen;
import dev.luminous.mod.modules.impl.client.ClickGui;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Objects;

public class PickerButton extends Button {
    static MatrixStack matrixStack;
    private final ColorSetting setting;
    boolean pickingColor;
    boolean pickingHue;
    boolean pickingAlpha;
    boolean open;

    public PickerButton(ColorSetting setting) {
        super(setting.getName());
        this.setting = setting;
    }

    public static boolean mouseOver(int minX, int minY, int maxX, int maxY, int mX, int mY) {
        return mX >= minX && mY >= minY && mX <= maxX && mY <= maxY;
    }

    public static Color getColor(Color color, float alpha) {
        float red = (float) color.getRed() / 255.0f;
        float green = (float) color.getGreen() / 255.0f;
        float blue = (float) color.getBlue() / 255.0f;
        return new Color(red, green, blue, alpha);
    }

    public static void drawPickerBase(int pickerX, int pickerY, int pickerWidth, int pickerHeight, float red, float green, float blue, float alpha) {
        {

            RenderSystem.enableBlend();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), (float) pickerX, (float) pickerY, 0.0f).color(1f, 1f, 1f, 1f);
            bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), (float) pickerX, (float) (pickerY + pickerHeight), 0.0f).color(1f, 1f, 1f, 1f);
            bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), (float) (pickerX + pickerWidth), (float) (pickerY + pickerHeight), 0.0f).color(red, green, blue, 1f);
            bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), (float) (pickerX + pickerWidth), (float) pickerY, 0.0f).color(red, green, blue, 1f);
            BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
            RenderSystem.disableBlend();
        }

        {

            RenderSystem.enableBlend();
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), (float) pickerX, (float) pickerY, 0.0f).color(0f, 0f, 0f, 0f);
            bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), (float) pickerX, (float) (pickerY + pickerHeight), 0.0f).color(0f, 0f, 0f, 1f);
            bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), (float) (pickerX + pickerWidth), (float) (pickerY + pickerHeight), 0.0f).color(0f, 0f, 0f, 1f);
            bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), (float) (pickerX + pickerWidth), (float) pickerY, 0.0f).color(0f, 0f, 0f, 0f);
            BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
            RenderSystem.disableBlend();
        }
    }

    public static void drawLeftGradientRect(int left, int top, int right, int bottom, int startColor, int endColor) {

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), right, top, 0.0f).color((float) (endColor >> 24 & 0xFF) / 255.0f, (float) (endColor >> 16 & 0xFF) / 255.0f, (float) (endColor >> 8 & 0xFF) / 255.0f, (float) (endColor >> 24 & 0xFF) / 255.0f);
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), left, top, 0.0f).color((float) (startColor >> 16 & 0xFF) / 255.0f, (float) (startColor >> 8 & 0xFF) / 255.0f, (float) (startColor & 0xFF) / 255.0f, (float) (startColor >> 24 & 0xFF) / 255.0f);
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), left, bottom, 0.0f).color((float) (startColor >> 16 & 0xFF) / 255.0f, (float) (startColor >> 8 & 0xFF) / 255.0f, (float) (startColor & 0xFF) / 255.0f, (float) (startColor >> 24 & 0xFF) / 255.0f);
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), right, bottom, 0.0f).color((float) (endColor >> 24 & 0xFF) / 255.0f, (float) (endColor >> 16 & 0xFF) / 255.0f, (float) (endColor >> 8 & 0xFF) / 255.0f, (float) (endColor >> 24 & 0xFF) / 255.0f);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        RenderSystem.disableBlend();
    }

    public static void gradient(int minX, int minY, int maxX, int maxY, int startColor, int endColor, boolean left) {

        float startA = (float) (startColor >> 24 & 0xFF) / 255.0f;
        float startR = (float) (startColor >> 16 & 0xFF) / 255.0f;
        float startG = (float) (startColor >> 8 & 0xFF) / 255.0f;
        float startB = (float) (startColor & 0xFF) / 255.0f;
        float endA = (float) (endColor >> 24 & 0xFF) / 255.0f;
        float endR = (float) (endColor >> 16 & 0xFF) / 255.0f;
        float endG = (float) (endColor >> 8 & 0xFF) / 255.0f;
        float endB = (float) (endColor & 0xFF) / 255.0f;

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), (float) minX, (float) minY, 0.0f).color(startR, startG, startB, startA);
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), (float) minX, (float) maxY, 0.0f).color(startR, startG, startB, startA);

        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), (float) maxX, (float) maxY, 0.0f).color(endR, endG, endB, endA);
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), (float) maxX, (float) minY, 0.0f).color(endR, endG, endB, endA);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        RenderSystem.disableBlend();
    }

    public static int gradientColor(int color, int percentage) {
        int r = ((color & 0xFF0000) >> 16) * (100 + percentage) / 100;
        int g = ((color & 0xFF00) >> 8) * (100 + percentage) / 100;
        int b = (color & 0xFF) * (100 + percentage) / 100;
        return new Color(r, g, b).hashCode();
    }

    public static void drawGradientRect(float left, float top, float right, float bottom, int startColor, int endColor, boolean hovered) {
        if (hovered) {
            startColor = gradientColor(startColor, -20);
            endColor = gradientColor(endColor, -20);
        }
        float c = (float) (startColor >> 24 & 0xFF) / 255.0f;
        float c1 = (float) (startColor >> 16 & 0xFF) / 255.0f;
        float c2 = (float) (startColor >> 8 & 0xFF) / 255.0f;
        float c3 = (float) (startColor & 0xFF) / 255.0f;
        float c4 = (float) (endColor >> 24 & 0xFF) / 255.0f;
        float c5 = (float) (endColor >> 16 & 0xFF) / 255.0f;
        float c6 = (float) (endColor >> 8 & 0xFF) / 255.0f;
        float c7 = (float) (endColor & 0xFF) / 255.0f;
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), right, top, 0.0f).color(c1, c2, c3, c);
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), left, top, 0.0f).color(c1, c2, c3, c);
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), left, bottom, 0.0f).color(c5, c6, c7, c4);
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), right, bottom, 0.0f).color(c5, c6, c7, c4);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        RenderSystem.disableBlend();
    }

    public static String readClipboard() {
        try {
            return (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        } catch (UnsupportedFlavorException | IOException exception) {
            return null;
        }
    }

    public static void drawOutlineRect(double left, double top, double right, double bottom, Color color, float lineWidth) {
        if (left < right) {
            double i = left;
            left = right;
            right = i;
        }
        if (top < bottom) {
            double j = top;
            top = bottom;
            bottom = j;
        }
        float f3 = (float) (color.getRGB() >> 24 & 0xFF) / 255.0f;
        float f = (float) (color.getRGB() >> 16 & 0xFF) / 255.0f;
        float f1 = (float) (color.getRGB() >> 8 & 0xFF) / 255.0f;
        float f2 = (float) (color.getRGB() & 0xFF) / 255.0f;

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), (float) left, (float) bottom, 0.0F).color(f, f1, f2, f3);
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), (float) right, (float) bottom, 0.0F).color(f, f1, f2, f3);
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), (float) right, (float) top, 0.0F).color(f, f1, f2, f3);
        bufferBuilder.vertex(matrixStack.peek().getPositionMatrix(), (float) left, (float) top, 0.0F).color(f, f1, f2, f3);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        RenderSystem.disableBlend();
    }

    @Override
    public void drawScreen(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        matrixStack = context.getMatrices();
        Color color = ClickGui.getInstance().color.getValue();
        Render2DUtil.rect(context.getMatrices(), this.x, this.y, this.x + (float) this.width + 7.0f, this.y + (float) this.height - 0.5f, this.getState() ? (!this.isHovering(mouseX, mouseY) ? ColorUtil.injectAlpha(color, ClickGui.getInstance().alpha.getValueInt()).getRGB() : ColorUtil.injectAlpha(color, ClickGui.getInstance().hoverAlpha.getValueInt()).getRGB()) : (!this.isHovering(mouseX, mouseY) ? defaultColor : hoverColor));
        Render2DUtil.rect(matrixStack, x - 1.5f + (float) width + 0.6f - 0.5f, y + 4.0f, x + (float) width + 7.0f - 2.5f, y + (float) height - 5.0f, ColorUtil.injectAlpha(setting.getValue(), 255).getRGB());

        drawString(getName(), x + 2.3f, y - 1.7f - (float) ClickGuiScreen.getInstance().getTextOffset(), enableTextColor);

        if (open) {
            drawPicker(setting, (int) x, (int) y + height, (int) x, (int) y + 103, (int) x, (int) y + 95, mouseX, mouseY);
            drawString("copy", x + 2.3f, y + 96 + height - (float) ClickGuiScreen.getInstance().getTextOffset(), isInsideCopy(mouseX, mouseY) ? enableTextColor : defaultTextColor);
            drawString("paste", x + (float) width - 2.3f - getWidth("paste") + 11.7f - 4.6f, y + 96 + height - (float) ClickGuiScreen.getInstance().getTextOffset(), isInsidePaste(mouseX, mouseY) ? enableTextColor : defaultTextColor);
            drawString("sync", x + 2.3f, y + 96 + getFontHeight() + height - (float) ClickGuiScreen.getInstance().getTextOffset(), setting.sync ? ColorUtil.injectAlpha(color, 255).getRGB() : isInsideRainbow(mouseX, mouseY) ? enableTextColor : defaultTextColor);
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            int pickerWidth = (int) (width + 7.4f);
            int pickerHeight = 78;
            int hueSliderWidth = pickerWidth + 3;
            int hueSliderHeight = 7;
            int alphaSliderHeight = 7;
            if (mouseOver((int) x, (int) y + 15, (int) x + pickerWidth, (int) y + 15 + pickerHeight, mouseX, mouseY)) {
                pickingColor = true;
            }
            if (mouseOver((int) x, (int) y + 103, (int) x + hueSliderWidth, (int) y + 103 + hueSliderHeight, mouseX, mouseY)) {
                pickingHue = true;
            }
            if (mouseOver((int) x, (int) y + 95, (int) x + pickerWidth, (int) y + 95 + alphaSliderHeight, mouseX, mouseY)) {
                pickingAlpha = true;
            }
        }
        if (isHovering(mouseX, mouseY)) {
            if (mouseButton == 1) {
                sound();
                open = !open;
            } else if (mouseButton == 0 && setting.injectBoolean) {
                sound();
                setting.booleanValue = !setting.booleanValue;
            }
        }
        if (mouseButton == 0 && isInsideRainbow(mouseX, mouseY) && open) {
            setting.sync = !setting.sync;
        }
        if (mouseButton == 0 && isInsideCopy(mouseX, mouseY) && open) {
            sound();
            String hex = String.format("#%02x%02x%02x%02x", setting.getValue().getAlpha(), setting.getValue().getRed(), setting.getValue().getGreen(), setting.getValue().getBlue());
            StringSelection selection = new StringSelection(hex);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
            CommandManager.sendMessage("Copied the color to your clipboard.");
        }
        if (mouseButton == 0 && isInsidePaste(mouseX, mouseY) && open) {
            try {
                if (readClipboard() != null) {
                    if (Objects.requireNonNull(readClipboard()).startsWith("#")) {
                        String hex = Objects.requireNonNull(readClipboard());

                        int a = Integer.valueOf(hex.substring(1, 3), 16);
                        int r = Integer.valueOf(hex.substring(3, 5), 16);
                        int g = Integer.valueOf(hex.substring(5, 7), 16);
                        int b = Integer.valueOf(hex.substring(7, 9), 16);

                        setting.setValue(new Color(r, g, b, a));
                    } else {
                        String[] color = readClipboard().split(",");
                        setting.setValue(new Color(Integer.parseInt(color[0]), Integer.parseInt(color[1]), Integer.parseInt(color[2])));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                CommandManager.sendMessage("§4Bad color format! Use Hex (#FFFFFFFF)");
                setting.setValue(-1);
            }
        }
    }

    @Override
    public boolean getState() {
        return this.setting.booleanValue;
    }

    @Override
    public void update() {
        this.setHidden(!this.setting.isVisible());
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int releaseButton) {
        pickingAlpha = false;
        pickingHue = false;
        pickingColor = false;
    }

    public boolean isInsideCopy(int mouseX, int mouseY) {
        return mouseOver((int) ((int) x + 2.3f), (int) (y + 96 + height - (float) ClickGuiScreen.getInstance().getTextOffset()), (int) ((int) x + 2.3f) + getWidth("copy"), (int) (y + 95 + height - (float) ClickGuiScreen.getInstance().getTextOffset()) + getFontHeight(), mouseX, mouseY);
    }

    public boolean isInsideRainbow(int mouseX, int mouseY) {
        return mouseOver((int) ((int) x + 2.3f), (int) (y + 96 + height + getFontHeight() - (float) ClickGuiScreen.getInstance().getTextOffset()), (int) ((int) x + 2.3f) + getWidth("sync"), (int) (y + 95 + height + getFontHeight() - (float) ClickGuiScreen.getInstance().getTextOffset()) + getFontHeight(), mouseX, mouseY);
    }

    public boolean isInsidePaste(int mouseX, int mouseY) {
        return mouseOver((int) (x + (float) width - 2.3f - getWidth("paste") + 11.7f - 4.6f), (int) (y + 96 + height - (float) ClickGuiScreen.getInstance().getTextOffset()), (int) (x + (float) width - 2.3f - getWidth("paste") + 11.7f - 4.6f) + getWidth("paste"), (int) (y + 95 + height - (float) ClickGuiScreen.getInstance().getTextOffset()) + getFontHeight(), mouseX, mouseY);
    }

    @Override
    public int getHeight() {
        if (open) {
            return super.getHeight() + 119;
        }
        return super.getHeight();
    }

    float[] hsb = new float[]{1, 1, 1, 1};

    public void drawPicker(ColorSetting setting, int pickerX, int pickerY, int hueSliderX, int hueSliderY, int alphaSliderX, int alphaSliderY, int mouseX, int mouseY) {
        float restrictedX;
        int pickerWidth = (int) (width + 7.4f);
        int pickerHeight = 78;
        int hueSliderHeight = 7;
        int alphaSliderHeight = 7;
        if (!(!pickingColor || GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), 0) == 1 && mouseOver(pickerX, pickerY, pickerX + pickerWidth, pickerY + pickerHeight, mouseX, mouseY))) {
            pickingColor = false;
        }
        if (!pickingColor) {
            hsb = Color.RGBtoHSB(setting.getValue().getRed(), setting.getValue().getGreen(), setting.getValue().getBlue(), null);
        }
        float[] color = new float[]{
                hsb[0],
                hsb[1],
                hsb[2],
                (float) setting.getValue().getAlpha() / 255.0f
        };
        if (!(!pickingHue || GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), 0) == 1 && mouseOver(hueSliderX, hueSliderY, hueSliderX + pickerWidth, hueSliderY + hueSliderHeight, mouseX, mouseY))) {
            pickingHue = false;
        }
        if (!(!pickingAlpha || GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), 0) == 1 && mouseOver(alphaSliderX, alphaSliderY, alphaSliderX + pickerWidth, alphaSliderY + alphaSliderHeight, mouseX, mouseY))) {
            pickingAlpha = false;
        }
        if (pickingHue) {
            restrictedX = Math.min(Math.max(hueSliderX, mouseX), hueSliderX + pickerWidth);
            color[0] = (restrictedX - (float) hueSliderX) / (float) pickerWidth;
        }
        if (pickingAlpha) {
            restrictedX = Math.min(Math.max(alphaSliderX, mouseX), alphaSliderX + pickerWidth);
            color[3] = 1.0f - (restrictedX - (float) alphaSliderX) / (float) pickerWidth;
        }
        if (pickingColor) {
            restrictedX = Math.min(Math.max(pickerX, mouseX), pickerX + pickerWidth);
            float restrictedY = Math.min(Math.max(pickerY, mouseY), pickerY + pickerHeight);
            color[1] = (restrictedX - (float) pickerX) / (float) pickerWidth;
            color[2] = 1.0f - (restrictedY - (float) pickerY) / (float) pickerHeight;
        }
        int selectedColor = Color.HSBtoRGB(color[0], 1.0f, 1.0f);
        float selectedRed = (float) (selectedColor >> 16 & 0xFF) / 255.0f;
        float selectedGreen = (float) (selectedColor >> 8 & 0xFF) / 255.0f;
        float selectedBlue = (float) (selectedColor & 0xFF) / 255.0f;

        drawPickerBase(pickerX, pickerY, pickerWidth, pickerHeight, selectedRed, selectedGreen, selectedBlue, color[3]);
        drawHueSlider(hueSliderX, hueSliderY, pickerWidth, hueSliderHeight, color[0]);

        int cursorX = (int) ((float) pickerX + color[1] * (float) pickerWidth);
        int cursorY = (int) ((float) (pickerY + pickerHeight) - color[2] * (float) pickerHeight);

        setting.setValue(getColor(new Color(Color.HSBtoRGB(color[0], color[1], color[2])), color[3]));
        Render2DUtil.arrow(matrixStack, (cursorX), (cursorY), setting.getValue());

        drawAlphaSlider(alphaSliderX, alphaSliderY, pickerWidth - 1, alphaSliderHeight, selectedRed, selectedGreen, selectedBlue, color[3]);
    }

    public void drawHueSlider(int x, int y, int width, int height, float hue) {
        int step = 0;
        if (height > width) {
            Render2DUtil.rect(matrixStack, x, y, x + width, y + 4, -65536);
            y += 4;
            for (int colorIndex = 0; colorIndex < 6; ++colorIndex) {
                int previousStep = Color.HSBtoRGB((float) step / 6.0f, 1.0f, 1.0f);
                int nextStep = Color.HSBtoRGB((float) (step + 1) / 6.0f, 1.0f, 1.0f);
                drawGradientRect(x, (float) y + (float) step * ((float) height / 6.0f), x + width, (float) y + (float) (step + 1) * ((float) height / 6.0f), previousStep, nextStep, false);
                ++step;
            }
            int sliderMinY = (int) ((float) y + (float) height * hue) - 4;
            Render2DUtil.rect(matrixStack, x, sliderMinY - 1, x + width, sliderMinY + 1, -1);
            drawOutlineRect(x, sliderMinY - 1, x + width, sliderMinY + 1, Color.BLACK, 1.0f);
        } else {
            for (int colorIndex = 0; colorIndex < 6; ++colorIndex) {
                int previousStep = Color.HSBtoRGB((float) step / 6.0f, 1.0f, 1.0f);
                int nextStep = Color.HSBtoRGB((float) (step + 1) / 6.0f, 1.0f, 1.0f);
                gradient(x + step * (width / 6), y, x + (step + 1) * (width / 6) + 3, y + height, previousStep, nextStep, true);
                ++step;
            }

            int sliderMinX = (int) ((float) x + (float) width * hue);

            Render2DUtil.rect(matrixStack, sliderMinX - 1, y - 1.2f, sliderMinX + 1, y + height + 1.2f, -1);
            drawOutlineRect(sliderMinX - 1.2, y - 1.2, sliderMinX + 1.2, y + height + 1.2, Color.BLACK, 0.1f);

        }
    }

    public void drawAlphaSlider(int x, int y, int width, int height, float red, float green, float blue, float alpha) {
        boolean left = true;
        int checkerBoardSquareSize = height / 2;
        for (int squareIndex = -checkerBoardSquareSize; squareIndex < width; squareIndex += checkerBoardSquareSize) {
            if (!left) {
                Render2DUtil.rect(matrixStack, x + squareIndex, y, x + squareIndex + checkerBoardSquareSize, y + height, -1);
                Render2DUtil.rect(matrixStack, x + squareIndex, y + checkerBoardSquareSize, x + squareIndex + checkerBoardSquareSize, y + height, -7303024);
                if (squareIndex < width - checkerBoardSquareSize) {
                    int minX = x + squareIndex + checkerBoardSquareSize;
                    int maxX = Math.min(x + width, x + squareIndex + checkerBoardSquareSize * 2);
                    Render2DUtil.rect(matrixStack, minX, y, maxX, y + height, -7303024);
                    Render2DUtil.rect(matrixStack, minX, y + checkerBoardSquareSize, maxX, y + height, -1);
                }
            }
            left = !left;
        }
        drawLeftGradientRect(x, y, x + width, y + height, new Color(red, green, blue, 1.0f).getRGB(), 0);

        int sliderMinX = (int) ((float) (x + width) - (float) width * alpha);
        Render2DUtil.rect(matrixStack, sliderMinX - 1, y - 1.2f, sliderMinX + 1, y + height + 1.2f, -1);
        drawOutlineRect(sliderMinX - 1.2, y - 1.2, sliderMinX + 1.2, y + height + 1.2, Color.BLACK, 0.1f);

    }
}