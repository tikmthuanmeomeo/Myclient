package dev.luminous.api.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.api.utils.Wrapper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.awt.*;

public class Render2DUtil implements Wrapper {
    public static void rect(MatrixStack stack, float x1, float y1, float x2, float y2, int color) {
        rectFilled(stack, x1, y1, x2, y2, color);
    }

    public static void arrow(MatrixStack matrixStack, float x, float y, Color color) {
        drawRectWithOutline(matrixStack, x - 1f, y - 1f, 2, 2, color, Color.BLACK);
    }

    public static void rectFilled(MatrixStack matrix, float x1, float y1, float x2, float y2, int color) {
        float f = (float) (color >> 24 & 255) / 255.0F;
        float g = (float) (color >> 16 & 255) / 255.0F;
        float h = (float) (color >> 8 & 255) / 255.0F;
        float j = (float) (color & 255) / 255.0F;
        if (f <= 0.01) return;

        float i;
        if (x1 < x2) {
            i = x1;
            x1 = x2;
            x2 = i;
        }

        if (y1 < y2) {
            i = y1;
            y1 = y2;
            y2 = i;
        }

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x1, y2, 0.0F).color(g, h, j, f);
        bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x2, y2, 0.0F).color(g, h, j, f);
        bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x2, y1, 0.0F).color(g, h, j, f);
        bufferBuilder.vertex(matrix.peek().getPositionMatrix(), x1, y1, 0.0F).color(g, h, j, f);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        RenderSystem.disableBlend();
    }

    public static void horizontalGradient(MatrixStack matrices, float x1, float y1, float x2, float y2, Color startColor, Color endColor) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        bufferBuilder.vertex(matrix, x1, y1, 0.0F).color(startColor.getRGB());
        bufferBuilder.vertex(matrix, x1, y2, 0.0F).color(startColor.getRGB());
        bufferBuilder.vertex(matrix, x2, y2, 0.0F).color(endColor.getRGB());
        bufferBuilder.vertex(matrix, x2, y1, 0.0F).color(endColor.getRGB());
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        RenderSystem.disableBlend();
    }

    public static void horizontalGradient(MatrixStack matrices, float x1, float y1, float x2, float y2, int startColor, int endColor) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        bufferBuilder.vertex(matrix, x1, y1, 0.0F).color(startColor);
        bufferBuilder.vertex(matrix, x1, y2, 0.0F).color(startColor);
        bufferBuilder.vertex(matrix, x2, y2, 0.0F).color(endColor);
        bufferBuilder.vertex(matrix, x2, y1, 0.0F).color(endColor);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        RenderSystem.disableBlend();
    }

    public static void drawLine(MatrixStack matrices, float x, float y, float x1, float y1, int color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        bufferBuilder.vertex(matrix, x, y, 0f).color(color);
        bufferBuilder.vertex(matrix, x1, y1, 0f).color(color);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        RenderSystem.disableBlend();
    }

    public static void drawRectWithOutline(MatrixStack matrices, float x, float y, float width, float height, Color c, Color c2) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, x, y + height, 0.0F).color(c.getRGB());
        buffer.vertex(matrix, x + width, y + height, 0.0F).color(c.getRGB());
        buffer.vertex(matrix, x + width, y, 0.0F).color(c.getRGB());
        buffer.vertex(matrix, x, y, 0.0F).color(c.getRGB());
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, x, y + height, 0.0F).color(c2.getRGB());
        buffer.vertex(matrix, x + width, y + height, 0.0F).color(c2.getRGB());
        buffer.vertex(matrix, x + width, y, 0.0F).color(c2.getRGB());
        buffer.vertex(matrix, x, y, 0.0F).color(c2.getRGB());
        buffer.vertex(matrix, x, y + height, 0.0F).color(c2.getRGB());
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    public static void drawRect(MatrixStack matrices, float x, float y, float width, float height, int c) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        bufferBuilder.vertex(matrix, x, y + height, 0.0F).color(c);
        bufferBuilder.vertex(matrix, x + width, y + height, 0.0F).color(c);
        bufferBuilder.vertex(matrix, x + width, y, 0.0F).color(c);
        bufferBuilder.vertex(matrix, x, y, 0.0F).color(c);
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        RenderSystem.disableBlend();
    }

    public static void drawRect(MatrixStack matrices, float x, float y, float width, float height, Color c) {
        drawRect(matrices, x, y, width, height, c.getRGB());
    }

    public static void drawRect(DrawContext drawContext, float x, float y, float width, float height, Color c) {
        drawRect(drawContext.getMatrices(), x, y, width, height, c);
        //drawContext.fill((int) x, (int) y, (int) (x + width), (int) (y + height), c.getRGB());
    }

    public static boolean isHovered(double mouseX, double mouseY, double x, double y, double width, double height) {
        return mouseX >= x && mouseX - width <= x && mouseY >= y && mouseY - height <= y;
    }

    public static void drawGlow(MatrixStack matrices, float x, float y, float width, float height, int color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int startColor = ColorUtil.injectAlpha(color, 20);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float halfWidth = width / 2.0F;
        float halfHeight = height / 2.0F;
        float centerX = x + halfWidth;
        float centerY = y + halfHeight;
        float x2 = x + width;
        float y2 = y + height;

        bufferBuilder.vertex(matrix, centerX, centerY, 0.0F).color(color);
        bufferBuilder.vertex(matrix, x, centerY, 0.0F).color(startColor);
        bufferBuilder.vertex(matrix, x, y, 0.0F).color(startColor);
        bufferBuilder.vertex(matrix, centerX, y, 0.0F).color(startColor);

        bufferBuilder.vertex(matrix, centerX, centerY, 0.0F).color(color);
        bufferBuilder.vertex(matrix, centerX, y, 0.0F).color(startColor);
        bufferBuilder.vertex(matrix, x2, y, 0.0F).color(startColor);
        bufferBuilder.vertex(matrix, x2, centerY, 0.0F).color(startColor);

        bufferBuilder.vertex(matrix, centerX, centerY, 0.0F).color(color);
        bufferBuilder.vertex(matrix, x, centerY, 0.0F).color(startColor);
        bufferBuilder.vertex(matrix, x, y2, 0.0F).color(startColor);
        bufferBuilder.vertex(matrix, centerX, y2, 0.0F).color(startColor);

        bufferBuilder.vertex(matrix, centerX, centerY, 0.0F).color(color);
        bufferBuilder.vertex(matrix, x2, centerY, 0.0F).color(startColor);
        bufferBuilder.vertex(matrix, x2, y2, 0.0F).color(startColor);
        bufferBuilder.vertex(matrix, centerX, y2, 0.0F).color(startColor);

        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }
}
