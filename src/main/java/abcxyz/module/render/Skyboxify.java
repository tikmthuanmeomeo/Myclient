package abcxyz.module.render;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.ModeSetting;
import abcxyz.core.NumSetting;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replacement for the Skyboxify mod. Reads the OptiFine-style sky of the enabled resource pack
 * (assets/minecraft/optifine/sky/world0/skyN.properties, or mcpatcher/sky/world0) and draws it on a SPHERE around the camera,
 * so every direction is at the same distance (the cube used by the mod gets cut at its edges and corners with a low render distance).
 * The sky image is the usual 3x2 skybox layout: bottom|top|east on the first row, south|west|north on the second one.
 * When the pack has no sky, the built-in one (assets/abcxyz/textures/skyboxify/default.png) is used.
 */
public class Skyboxify extends Module {
    private final ModeSetting source = add(new ModeSetting("Source", "Auto", "Auto", "Built-in"));
    private final NumSetting detail = add(new NumSetting("Detail", 16, 4, 32, 4));
    private final BoolSetting nightOnly = add(new BoolSetting("NightOnly", false));

    private static final Identifier BUILT_IN = Identifier.of("abcxyz", "textures/skyboxify/default.png");
    private static final Pattern SKY_NAME = Pattern.compile("sky(\\d+)\\.properties$");
    // position of every face in the 3x2 image (column, row), in this order: bottom, top, east, south, west, north
    private static final int[] FACE_COL = {0, 1, 2, 0, 1, 2};
    private static final int[] FACE_ROW = {0, 0, 0, 1, 1, 1};

    private static class Layer {
        Identifier id;
        String blend;
        int width;
        int height;
    }

    private final List<Layer> layers = new ArrayList<>();
    private ResourceManager lastManager;
    private String lastSource = "";
    private boolean usingBuiltIn = false;

    // sphere geometry, built once for a given detail: unit direction + position inside its face (0..1) + face index
    private int builtDetail = -1;
    private float[] dir = new float[0];
    private float[] local = new float[0];
    private int[] face = new int[0];

    public Skyboxify() {
        super("Skyboxify", "Pack sky (OptiFine skyboxes) drawn on a sphere, replaces the Skyboxify mod", Category.Render);
    }

    @Override
    public String info() {
        if (layers.isEmpty()) return "none";
        return usingBuiltIn ? "built-in" : layers.size() + (layers.size() == 1 ? " layer" : " layers");
    }

    @Override
    public void onEnable() {
        lastManager = null;
    }

    @Override
    public void onDisable() {
        freeLayers();
        lastManager = null;
    }

    private void freeLayers() {
        for (Layer l : layers) {
            try {
                mc.getTextureManager().destroyTexture(l.id);
            } catch (Throwable ignored) {
            }
        }
        layers.clear();
    }

    // ---------------------------------------------------------------- loading

    private static Identifier resolveSource(String src, Identifier propsId) {
        String path = propsId.getPath();
        if (src == null) return propsId.withPath(path.replace(".properties", ".png"));
        src = src.trim();
        if (src.startsWith("./")) {
            int slash = path.lastIndexOf('/');
            return propsId.withPath(path.substring(0, slash + 1) + src.substring(2));
        }
        String[] parts = src.split("/", 3);
        if (parts.length == 3 && parts[0].equals("assets")) return Identifier.of(parts[1], parts[2]);
        return Identifier.tryParse(src);
    }

    private void reload() {
        freeLayers();
        usingBuiltIn = false;
        ResourceManager rm = mc.getResourceManager();
        lastManager = rm;
        lastSource = source.get();

        if (!source.is("Built-in")) {
            loadPackSky(rm, "optifine/sky/world0");
            if (layers.isEmpty()) loadPackSky(rm, "mcpatcher/sky/world0");
        }
        if (layers.isEmpty()) {
            Layer l = loadTexture(rm, BUILT_IN, "alpha", 0);
            if (l != null) {
                layers.add(l);
                usingBuiltIn = true;
            }
        }
    }

    private void loadPackSky(ResourceManager rm, String folder) {
        Map<Identifier, Resource> found = rm.findResources(folder, id -> id.getNamespace().equals("minecraft") && id.getPath().endsWith(".properties"));
        List<Identifier> ids = new ArrayList<>();
        for (Identifier id : found.keySet()) {
            if (SKY_NAME.matcher(id.getPath()).find()) ids.add(id);
        }
        ids.sort(Comparator.comparingInt(Skyboxify::skyNumber));
        for (Identifier id : ids) {
            try {
                Properties p = new Properties();
                try (InputStream in = found.get(id).getInputStream()) {
                    p.load(in);
                }
                Identifier tex = resolveSource(p.getProperty("source"), id);
                if (tex == null) continue;
                Layer l = loadTexture(rm, tex, p.getProperty("blend", "add").trim().toLowerCase(), layers.size());
                if (l != null) layers.add(l);
            } catch (Throwable t) {
                System.err.println("[abcxyz] Skyboxify: failed to read " + id + ": " + t);
            }
        }
    }

    private static int skyNumber(Identifier id) {
        Matcher m = SKY_NAME.matcher(id.getPath());
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private Layer loadTexture(ResourceManager rm, Identifier tex, String blend, int index) {
        try {
            Resource res = rm.getResource(tex).orElse(null);
            if (res == null) {
                System.err.println("[abcxyz] Skyboxify: missing texture " + tex);
                return null;
            }
            NativeImage image;
            try (InputStream in = res.getInputStream()) {
                image = NativeImage.read(in);
            }
            Layer l = new Layer();
            l.id = Identifier.of("abcxyz", "skyboxify/layer" + index);
            l.blend = blend;
            l.width = image.getWidth();
            l.height = image.getHeight();
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            mc.getTextureManager().registerTexture(l.id, texture);
            texture.setFilter(true, false);
            return l;
        } catch (Throwable t) {
            System.err.println("[abcxyz] Skyboxify: failed to load " + tex + ": " + t);
            return null;
        }
    }

    // ---------------------------------------------------------------- geometry

    /** Point of the cube (from -1 to 1) for a position (a, b) in a face, same layout as the Skyboxify mod / OptiFine. */
    private static void cubePoint(int f, float a, float b, float[] out) {
        switch (f) {
            case 0 -> { out[0] = b; out[1] = -1f; out[2] = -a; }   // bottom
            case 1 -> { out[0] = -b; out[1] = 1f; out[2] = -a; }   // top
            case 2 -> { out[0] = 1f; out[1] = -b; out[2] = a; }    // east
            case 3 -> { out[0] = -a; out[1] = -b; out[2] = 1f; }   // south
            case 4 -> { out[0] = -1f; out[1] = -b; out[2] = -a; }  // west
            default -> { out[0] = a; out[1] = -b; out[2] = -1f; }  // north
        }
    }

    private void buildGeometry(int n) {
        int vertices = 6 * n * n * 4;
        dir = new float[vertices * 3];
        local = new float[vertices * 2];
        face = new int[vertices];
        float[] p = new float[3];
        int k = 0;
        for (int f = 0; f < 6; f++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    int[][] corner = {{j, i}, {j + 1, i}, {j + 1, i + 1}, {j, i + 1}};
                    for (int[] c : corner) {
                        float fu = c[0] / (float) n, fv = c[1] / (float) n;
                        cubePoint(f, fu * 2f - 1f, fv * 2f - 1f, p);
                        float len = (float) Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
                        dir[k * 3] = p[0] / len;
                        dir[k * 3 + 1] = p[1] / len;
                        dir[k * 3 + 2] = p[2] / len;
                        local[k * 2] = fu;
                        local[k * 2 + 1] = fv;
                        face[k] = f;
                        k++;
                    }
                }
            }
        }
        builtDetail = n;
    }

    // ---------------------------------------------------------------- drawing

    /** 0..1: how much of the sky to show (only used with NightOnly: fades in at dusk and out at dawn). */
    private float visibility() {
        if (!nightOnly.on()) return 1f;
        long t = mc.world.getTimeOfDay() % 24000L;
        if (t < 12000L) return 0f;
        if (t < 13000L) return (t - 12000L) / 1000f;
        if (t < 23000L) return 1f;
        return (24000L - t) / 1000f;
    }

    @Override
    public void onRender3D(WorldRenderContext context) {
        if (mc.world.getRegistryKey() != World.OVERWORLD) return;
        if (mc.getResourceManager() != lastManager || !source.get().equals(lastSource)) reload();
        if (layers.isEmpty()) return;
        float vis = visibility();
        if (vis <= 0f) return;
        int n = detail.i();
        if (n != builtDetail) buildGeometry(n);

        // The sphere is always inside the far plane (which is 4 x view distance) and outside the loaded terrain.
        float radius = Math.max(64f, Math.min(512f, mc.options.getViewDistance().getValue() * 16f * 1.4f));
        Matrix4f m = new Matrix4f(context.matrixStack().peek().getPositionMatrix());

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        for (Layer l : layers) {
            switch (l.blend) {
                case "replace" -> RenderSystem.disableBlend();
                case "add" -> {
                    RenderSystem.enableBlend();
                    RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
                }
                default -> {
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                }
            }
            RenderSystem.setShaderTexture(0, l.id);
            drawLayer(l, m, radius, vis);
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private void drawLayer(Layer l, Matrix4f m, float radius, float vis) {
        float w = l.width, h = l.height;
        float cw = w / 3f, ch = h / 2f;
        float inset = 1f; // texels kept away from the edge of a face, so the filter doesn't take the next face
        int alpha = Math.max(0, Math.min(255, (int) (vis * 255f)));
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        int count = face.length;
        for (int k = 0; k < count; k++) {
            int f = face[k];
            float u = (FACE_COL[f] * cw + inset + local[k * 2] * (cw - 2f * inset)) / w;
            float v = (FACE_ROW[f] * ch + inset + local[k * 2 + 1] * (ch - 2f * inset)) / h;
            b.vertex(m, dir[k * 3] * radius, dir[k * 3 + 1] * radius, dir[k * 3 + 2] * radius)
                    .texture(u, v)
                    .color(255, 255, 255, alpha);
        }
        BuiltBuffer built = b.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
    }
}
