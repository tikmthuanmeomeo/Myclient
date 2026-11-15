package abcxyz.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/** Small helpers for drawing lines, boxes and text in the world (visible through walls). */
public final class Render3D {
    private Render3D() {
    }

    /** Moves the matrix stack so that world coordinates can be used directly. Call pop() on it afterwards. */
    public static Matrix4f push(WorldRenderContext context) {
        MatrixStack ms = context.matrixStack();
        Vec3d cam = context.camera().getPos();
        ms.push();
        ms.translate(-cam.x, -cam.y, -cam.z);
        return ms.peek().getPositionMatrix();
    }

    public static void pop(WorldRenderContext context) {
        context.matrixStack().pop();
    }

    public static void begin() {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(2.0f);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
    }

    public static void end() {
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.lineWidth(1.0f);
    }

    public static BufferBuilder lines() {
        return Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
    }

    public static void flush(BufferBuilder builder) {
        BuiltBuffer built = builder.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
    }

    public static void line(BufferBuilder b, Matrix4f m, Vec3d from, Vec3d to, int argb) {
        int a = (argb >> 24) & 255, r = (argb >> 16) & 255, g = (argb >> 8) & 255, bl = argb & 255;
        b.vertex(m, (float) from.x, (float) from.y, (float) from.z).color(r, g, bl, a);
        b.vertex(m, (float) to.x, (float) to.y, (float) to.z).color(r, g, bl, a);
    }

    public static void box(BufferBuilder b, Matrix4f m, Box box, int argb) {
        Vec3d[] c = new Vec3d[8];
        int i = 0;
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    c[i++] = new Vec3d(x == 0 ? box.minX : box.maxX, y == 0 ? box.minY : box.maxY, z == 0 ? box.minZ : box.maxZ);
                }
            }
        }
        // corner index = x*4 + y*2 + z
        int[][] edges = {
                {0, 1}, {2, 3}, {4, 5}, {6, 7},
                {0, 2}, {1, 3}, {4, 6}, {5, 7},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] e : edges) line(b, m, c[e[0]], c[e[1]], argb);
    }

    /** A piece of text with its own colour. */
    public record Part(String text, int color) {
    }

    /**
     * Rows of coloured text floating at a world position, facing the camera and scaling with distance like a nametag.
     * rows.get(0) is the bottom row, the next rows are drawn above it.
     */
    public static void textRows(WorldRenderContext context, List<List<Part>> rows, Vec3d pos, double sizeMultiplier) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Camera camera = context.camera();
        Vec3d cam = camera.getPos();
        double dist = Math.max(1.0, cam.distanceTo(pos));
        float scale = (float) (Math.max(0.02, dist * 0.0045) * sizeMultiplier);
        MatrixStack ms = context.matrixStack();
        ms.push();
        ms.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        ms.multiply(camera.getRotation());
        ms.scale(-scale, -scale, scale);
        TextRenderer tr = mc.textRenderer;
        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        Matrix4f matrix = ms.peek().getPositionMatrix();
        for (int i = 0; i < rows.size(); i++) {
            int total = 0;
            for (Part part : rows.get(i)) total += tr.getWidth(part.text());
            float x = -total / 2f;
            float y = -(i + 1) * 9f;
            for (Part part : rows.get(i)) {
                tr.draw(part.text(), x, y, part.color(), false, matrix, consumers,
                        TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
                x += tr.getWidth(part.text());
            }
        }
        consumers.draw();
        ms.pop();
    }

    /** Text that always faces the camera; it keeps a similar size on screen at any distance. */
    public static void text(WorldRenderContext context, String text, Vec3d pos, int color) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Camera camera = context.camera();
        Vec3d cam = camera.getPos();
        double dist = Math.max(1.0, cam.distanceTo(pos));
        float scale = (float) Math.max(0.025, dist * 0.0055);
        MatrixStack ms = context.matrixStack();
        ms.push();
        ms.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        ms.multiply(camera.getRotation());
        ms.scale(-scale, -scale, scale);
        TextRenderer tr = mc.textRenderer;
        float width = tr.getWidth(text);
        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        tr.draw(text, -width / 2f, 0f, color, false, ms.peek().getPositionMatrix(), consumers,
                TextRenderer.TextLayerType.SEE_THROUGH, 0x66000000, 0xF000F0);
        consumers.draw();
        ms.pop();
    }
}
