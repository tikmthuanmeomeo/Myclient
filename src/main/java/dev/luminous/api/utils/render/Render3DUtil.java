package dev.luminous.api.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.Alien;
import dev.luminous.api.utils.Wrapper;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.*;
import java.util.ArrayList;

public class Render3DUtil implements Wrapper {

    public static void endBuilding(BufferBuilder bb) {
        BuiltBuffer builtBuffer = bb.endNullable();
        if (builtBuffer != null)
            BufferRenderer.drawWithGlobalProgram(builtBuffer);
    }

    public static MatrixStack matrixFrom(double x, double y, double z) {
        MatrixStack matrices = new MatrixStack();

        Camera camera = mc.gameRenderer.getCamera();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
        matrices.translate(x - camera.getPos().x, y - camera.getPos().y, z - camera.getPos().z);

        return matrices;
    }

    public static void drawText3D(String text, Vec3d vec3d, Color color) {
        drawText3D(Text.of(text), vec3d.x, vec3d.y, vec3d.z, 0, 0, 1, color.getRGB());
    }

    public static void drawText3D(String text, Vec3d vec3d, int color) {
        drawText3D(Text.of(text), vec3d.x, vec3d.y, vec3d.z, 0, 0, 1, color);
    }

    public static void drawText3D(Text text, Vec3d vec3d, double offX, double offY, double scale, Color color) {
        drawText3D(text, vec3d.x, vec3d.y, vec3d.z, offX, offY, scale, color.getRGB());
    }

    public static void drawText3D(Text text, double x, double y, double z, double offX, double offY, double scale, int color) {
        RenderSystem.disableDepthTest();
        MatrixStack matrices = matrixFrom(x, y, z);

        Camera camera = mc.gameRenderer.getCamera();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

        RenderSystem.enableBlend();

        matrices.translate(offX, offY, 0);
        matrices.scale(-0.025f * (float) scale, -0.025f * (float) scale, 1);

        int halfWidth = mc.textRenderer.getWidth(text) / 2;

        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(new BufferAllocator(1536));

        mc.textRenderer.drawLayer(text.getString(), -halfWidth, 0f, -1, true, matrices.peek().getPositionMatrix(), immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xf000f0);
        immediate.draw();

        mc.textRenderer.draw(text.copy(), -halfWidth, 0f, color, false, matrices.peek().getPositionMatrix(), immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xf000f0);
        immediate.draw();

        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    public static void drawFill(MatrixStack matrixStack, Box bb, Color fillColor) {
        draw3DBox(matrixStack, bb, fillColor, new Color(0, 0, 0, 0), false, true);
    }

    public static void drawBox(MatrixStack matrixStack, Box bb, Color outlineColor) {
        draw3DBox(matrixStack, bb, new Color(0, 0, 0, 0), outlineColor, true, false);
    }

    public static void drawBox(MatrixStack matrixStack, Box bb, Color outlineColor, float lineWidth) {
        draw3DBox(matrixStack, bb, new Color(0, 0, 0, 0), outlineColor, true, false, lineWidth);
    }

    public static void draw3DBox(MatrixStack matrixStack, Box box, Color fillColor, Color outlineColor) {
        draw3DBox(matrixStack, box, fillColor, outlineColor, true, true);
    }

    public static void draw3DBox(MatrixStack matrixStack, Box box, Color fillColor, Color outlineColor, boolean outline, boolean fill) {
        draw3DBox(matrixStack, box, fillColor, outlineColor, outline, fill, 1.5f);
    }

    public static void draw3DBox(MatrixStack matrixStack, Box box, Color fillColor, Color outlineColor, boolean outline, boolean fill, float lineWidth) {
        box = box.offset(mc.gameRenderer.getCamera().getPos().negate());
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();

        Matrix4f matrix = matrixStack.peek().getPositionMatrix();

        if (outline) {
            float a = outlineColor.getAlpha() / 255f;
            float r = outlineColor.getRed() / 255f;
            float g = outlineColor.getGreen() / 255f;
            float b = outlineColor.getBlue() / 255f;
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            RenderSystem.lineWidth(lineWidth);
            BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).color(r, g, b, a);

            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);

            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);

            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).color(r, g, b, a);

            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);

            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);

            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);

            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);

            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);

            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);

            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);

            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);

            BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        }

        if (fill) {
            float a = fillColor.getAlpha() / 255f;
            float r = fillColor.getRed() / 255f;
            float g = fillColor.getGreen() / 255f;
            float b = fillColor.getBlue() / 255f;
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);

            BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);

            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);

            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).color(r, g, b, a);

            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.minZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);

            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);

            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.minZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.minY, (float) box.maxZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(r, g, b, a);
            bufferBuilder.vertex(matrix, (float) box.minX, (float) box.maxY, (float) box.minZ).color(r, g, b, a);

            BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        }
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    public static void drawFadeFill(MatrixStack stack, Box box, Color c, Color c1) {
        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f posMatrix = stack.peek().getPositionMatrix();
        float minX = (float) (box.minX - mc.getEntityRenderDispatcher().camera.getPos().getX());
        float minY = (float) (box.minY - mc.getEntityRenderDispatcher().camera.getPos().getY());
        float minZ = (float) (box.minZ - mc.getEntityRenderDispatcher().camera.getPos().getZ());
        float maxX = (float) (box.maxX - mc.getEntityRenderDispatcher().camera.getPos().getX());
        float maxY = (float) (box.maxY - mc.getEntityRenderDispatcher().camera.getPos().getY());
        float maxZ = (float) (box.maxZ - mc.getEntityRenderDispatcher().camera.getPos().getZ());
        buffer.vertex(posMatrix, minX, minY, minZ).color(c.getRGB());
        buffer.vertex(posMatrix, maxX, minY, minZ).color(c.getRGB());
        buffer.vertex(posMatrix, maxX, minY, maxZ).color(c.getRGB());
        buffer.vertex(posMatrix, minX, minY, maxZ).color(c.getRGB());

        buffer.vertex(posMatrix, minX, minY, minZ).color(c.getRGB());
        buffer.vertex(posMatrix, minX, maxY, minZ).color(c1.getRGB());
        buffer.vertex(posMatrix, maxX, maxY, minZ).color(c1.getRGB());
        buffer.vertex(posMatrix, maxX, minY, minZ).color(c.getRGB());

        buffer.vertex(posMatrix, maxX, minY, minZ).color(c.getRGB());
        buffer.vertex(posMatrix, maxX, maxY, minZ).color(c1.getRGB());
        buffer.vertex(posMatrix, maxX, maxY, maxZ).color(c1.getRGB());
        buffer.vertex(posMatrix, maxX, minY, maxZ).color(c.getRGB());

        buffer.vertex(posMatrix, minX, minY, maxZ).color(c.getRGB());
        buffer.vertex(posMatrix, maxX, minY, maxZ).color(c.getRGB());
        buffer.vertex(posMatrix, maxX, maxY, maxZ).color(c1.getRGB());
        buffer.vertex(posMatrix, minX, maxY, maxZ).color(c1.getRGB());

        buffer.vertex(posMatrix, minX, minY, minZ).color(c.getRGB());
        buffer.vertex(posMatrix, minX, minY, maxZ).color(c.getRGB());
        buffer.vertex(posMatrix, minX, maxY, maxZ).color(c1.getRGB());
        buffer.vertex(posMatrix, minX, maxY, minZ).color(c1.getRGB());

        buffer.vertex(posMatrix, minX, maxY, minZ).color(c1.getRGB());
        buffer.vertex(posMatrix, minX, maxY, maxZ).color(c1.getRGB());
        buffer.vertex(posMatrix, maxX, maxY, maxZ).color(c1.getRGB());
        buffer.vertex(posMatrix, maxX, maxY, minZ).color(c1.getRGB());

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public static void drawLine(Vec3d start, Vec3d end, Color color) {
        drawLine(start.x, start.getY(), start.z, end.getX(), end.getY(), end.getZ(), color, 1);
    }

    public static void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, Color color, float width) {
        RenderSystem.enableBlend();
        MatrixStack matrices = matrixFrom(x1, y1, z1);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
        RenderSystem.lineWidth(width);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
        vertexLine(matrices, buffer, 0f, 0f, 0f, (float) (x2 - x1), (float) (y2 - y1), (float) (z2 - z1), color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.lineWidth(1f);
        RenderSystem.disableBlend();
    }

    public static void vertexLine(MatrixStack matrices, VertexConsumer buffer, double x1, double y1, double z1, double x2, double y2, double z2, Color lineColor) {
        Matrix4f model = matrices.peek().getPositionMatrix();
        MatrixStack.Entry entry = matrices.peek();
        Vector3f normalVec = getNormal((float) x1, (float) y1, (float) z1, (float) x2, (float) y2, (float) z2);
        buffer.vertex(model, (float) x1, (float) y1, (float) z1).color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), lineColor.getAlpha()).normal(entry, normalVec.x(), normalVec.y(), normalVec.z());
        buffer.vertex(model, (float) x2, (float) y2, (float) z2).color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), lineColor.getAlpha()).normal(entry, normalVec.x(), normalVec.y(), normalVec.z());
    }

    public static Vector3f getNormal(float x1, float y1, float z1, float x2, float y2, float z2) {
        float xNormal = x2 - x1;
        float yNormal = y2 - y1;
        float zNormal = z2 - z1;
        float normalSqrt = MathHelper.sqrt(xNormal * xNormal + yNormal * yNormal + zNormal * zNormal);

        return new Vector3f(xNormal / normalSqrt, yNormal / normalSqrt, zNormal / normalSqrt);
    }

    public static void drawTargetEsp(MatrixStack stack, @NotNull Entity target, Color color) {
        ArrayList<Vec3d> vecs = new ArrayList<>();
        ArrayList<Vec3d> vecs1 = new ArrayList<>();
        ArrayList<Vec3d> vecs2 = new ArrayList<>();

        double x = target.prevX + (target.getX() - target.prevX) * getTickDelta()
                - mc.getEntityRenderDispatcher().camera.getPos().getX();
        double y = target.prevY + (target.getY() - target.prevY) * getTickDelta()
                - mc.getEntityRenderDispatcher().camera.getPos().getY();
        double z = target.prevZ + (target.getZ() - target.prevZ) * getTickDelta()
                - mc.getEntityRenderDispatcher().camera.getPos().getZ();

        double height = target.getHeight();

        for (int i = 0; i <= 361; ++i) {
            double v = Math.sin(Math.toRadians(i));
            double u = Math.cos(Math.toRadians(i));
            Vec3d vec = new Vec3d((float) (u * 0.5f), height, (float) (v * 0.5f));
            vecs.add(vec);

            double v1 = Math.sin(Math.toRadians((i + 120) % 360));
            double u1 = Math.cos(Math.toRadians(i + 120) % 360);
            Vec3d vec1 = new Vec3d((float) (u1 * 0.5f), height, (float) (v1 * 0.5f));
            vecs1.add(vec1);

            double v2 = Math.sin(Math.toRadians((i + 240) % 360));
            double u2 = Math.cos(Math.toRadians((i + 240) % 360));
            Vec3d vec2 = new Vec3d((float) (u2 * 0.5f), height, (float) (v2 * 0.5f));
            vecs2.add(vec2);
            height -= 0.004f;
        }

        stack.push();
        stack.translate(x, y, z);
        BufferBuilder bufferBuilder;
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

        Matrix4f matrix = stack.peek().getPositionMatrix();

        for (int j = 0; j < vecs.size() - 1; ++j) {
            float alpha = 1f - (((float) j + ((System.currentTimeMillis() - Alien.initTime) / 5f)) % 360) / 60f;
            bufferBuilder.vertex(matrix, (float) vecs.get(j).x, (float) vecs.get(j).y, (float) vecs.get(j).z).color(ColorUtil.injectAlpha(ColorUtil.pulseColor(color, (int) (j / 20f), 10, 1), (int) (alpha * 255)).getRGB());
            bufferBuilder.vertex(matrix, (float) vecs.get(j + 1).x, (float) vecs.get(j + 1).y + 0.1f, (float) vecs.get(j + 1).z).color(ColorUtil.injectAlpha(ColorUtil.pulseColor(color, (int) (j / 20f), 10, 1), (int) (alpha * 255f)).getRGB());
        }
        endBuilding(bufferBuilder);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int j = 0; j < vecs1.size() - 1; ++j) {
            float alpha = 1f - (((float) j + ((System.currentTimeMillis() - Alien.initTime) / 5f)) % 360) / 60f;
            bufferBuilder.vertex(matrix, (float) vecs1.get(j).x, (float) vecs1.get(j).y, (float) vecs1.get(j).z).color(ColorUtil.injectAlpha(ColorUtil.pulseColor(color, (int) (j / 20f), 10, 1), (int) (alpha * 255)).getRGB());
            bufferBuilder.vertex(matrix, (float) vecs1.get(j + 1).x, (float) vecs1.get(j + 1).y + 0.1f, (float) vecs1.get(j + 1).z).color(ColorUtil.injectAlpha(ColorUtil.pulseColor(color, (int) (j / 20f), 10, 1), (int) (alpha * 255f)).getRGB());
        }
        endBuilding(bufferBuilder);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int j = 0; j < vecs2.size() - 1; ++j) {
            float alpha = 1f - (((float) j + ((System.currentTimeMillis() - Alien.initTime) / 5f)) % 360) / 60f;
            bufferBuilder.vertex(matrix, (float) vecs2.get(j).x, (float) vecs2.get(j).y, (float) vecs2.get(j).z).color(ColorUtil.injectAlpha(ColorUtil.pulseColor(color, (int) (j / 20f), 10, 1), (int) (alpha * 255)).getRGB());
            bufferBuilder.vertex(matrix, (float) vecs2.get(j + 1).x, (float) vecs2.get(j + 1).y + 0.1f, (float) vecs2.get(j + 1).z).color(ColorUtil.injectAlpha(ColorUtil.pulseColor(color, (int) (j / 20f), 10, 1), (int) (alpha * 255f)).getRGB());
        }
        endBuilding(bufferBuilder);

        RenderSystem.enableCull();
        stack.translate(-x, -y, -z);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        stack.pop();
    }

    public static float getTickDelta() {
        return mc.getRenderTickCounter().getTickDelta(true);
    }
}
