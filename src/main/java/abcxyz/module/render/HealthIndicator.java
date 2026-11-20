package abcxyz.module.render;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.ModeSetting;
import abcxyz.core.NumSetting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Health indicator drawn like the "Health Indicators" mod: the real heart icons of the game (so they follow the resource pack),
 * drawn in two passes (empty containers first, then full / half hearts on top), gold hearts for absorption, other icons when the
 * entity is withered, poisoned or frozen (and in hardcore), hearts that jump with Regeneration, a number mode and optional armor icons.
 */
public class HealthIndicator extends Module {
    private static final float BASE_SCALE = 0.025f;
    private static final int MAX_ICONS = 100;
    private static final int MAX_ENTITIES = 40;

    private final ModeSetting mode = add(new ModeSetting("Mode", "Hearts", "Hearts", "Number", "Dynamic"));
    private final NumSetting range = add(new NumSetting("Range", 64, 8, 256, 8));
    private final NumSetting scale = add(new NumSetting("Scale", 0.025, 0.01, 0.1, 0.005));
    private final NumSetting height = add(new NumSetting("Height", 0.5, 0.0, 2.0, 0.05));
    private final NumSetting perRow = add(new NumSetting("IconsPerRow", 10, 1, 20, 1));
    private final BoolSetting players = add(new BoolSetting("Players", true));
    private final BoolSetting mobs = add(new BoolSetting("Mobs", false));
    private final BoolSetting throughWalls = add(new BoolSetting("ThroughWalls", true));
    private final BoolSetting effects = add(new BoolSetting("Effects", true));
    private final BoolSetting armor = add(new BoolSetting("Armor", false));
    private final BoolSetting aboveName = add(new BoolSetting("AboveName", true));

    private final Map<String, Identifier> sprites = new HashMap<>();

    /** One icon to draw: the matrix of its row, and its position inside the row (in icon pixels, y grows downwards). */
    private record Icon(Matrix4f matrix, float x, float y) {
    }

    public HealthIndicator() {
        super("HealthIndicator", "Shows hearts (or the health number) above other players, like the Health Indicators mod", Category.Render);
    }

    @Override
    public String info() {
        return mode.get();
    }

    private Identifier sprite(String name) {
        return sprites.computeIfAbsent(name, n -> Identifier.of("minecraft", "textures/gui/sprites/hud/" + n + ".png"));
    }

    private boolean wanted(Entity e) {
        if (e == mc.player || !(e instanceof LivingEntity living) || !living.isAlive() || e instanceof ArmorStandEntity) return false;
        if (e instanceof PlayerEntity) return players.on();
        return mobs.on();
    }

    /** Extra height so the icons stay above the name tag (the mod uses 10.35 pixels, and again with a score under the name). */
    private float labelOffset(LivingEntity e, float s) {
        if (!aboveName.on()) return 0f;
        boolean label = e instanceof PlayerEntity || (e.hasCustomName() && e.isCustomNameVisible());
        if (!label) return 0f;
        float offset = 10.35f * s;
        if (e instanceof PlayerEntity && mc.player.squaredDistanceTo(e) < 100.0
                && mc.world.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME) != null) {
            offset += 10.35f * s;
        }
        return offset;
    }

    /** "withered_", "poisoned_" or "frozen_" (nothing when none), then "hardcore_" in a hardcore world. */
    private String heartPrefix(LivingEntity living) {
        String prefix = "";
        if (living.hasStatusEffect(StatusEffects.WITHER)) prefix = "withered_";
        else if (living.hasStatusEffect(StatusEffects.POISON)) prefix = "poisoned_";
        else if (living.isFrozen()) prefix = "frozen_";
        if (living.getWorld().getLevelProperties().isHardcore()) prefix += "hardcore_";
        return prefix;
    }

    private static String healthText(LivingEntity living) {
        return String.format(Locale.ROOT, "%.1f / %.1f", living.getHealth() + living.getAbsorptionAmount(), living.getMaxHealth());
    }

    @Override
    public void onRender3D(WorldRenderContext context) {
        Camera camera = context.camera();
        Vec3d cam = camera.getPos();
        float delta = context.tickCounter().getTickDelta(true);
        Matrix4f base = context.matrixStack().peek().getPositionMatrix();
        float s = (float) scale.d();
        int row = perRow.i();

        List<Icon> containers = new ArrayList<>();
        Map<Identifier, List<Icon>> fills = new LinkedHashMap<>();
        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        boolean textDrawn = false;

        int drawn = 0;
        for (Entity e : mc.world.getEntities()) {
            if (drawn >= MAX_ENTITIES) break;
            if (!wanted(e) || mc.player.distanceTo(e) > range.d()) continue;
            LivingEntity living = (LivingEntity) e;
            drawn++;

            Vec3d pos = e.getLerpedPos(delta);
            float rx = (float) (pos.x - cam.x);
            float ry = (float) (pos.y - cam.y) + e.getHeight() + (float) height.d() + labelOffset(living, s);
            float rz = (float) (pos.z - cam.z);

            boolean number = mode.is("Number") || (mode.is("Dynamic") && living.getMaxHealth() > 100f);
            if (number) {
                Matrix4f m = new Matrix4f(base).translate(rx, ry, rz).rotate(camera.getRotation()).scale(s, -s, s);
                String text = healthText(living);
                TextRenderer font = mc.textRenderer;
                font.draw(text, -font.getWidth(text) / 2f, 0f, 0xFFFF0000, false, m, consumers,
                        throughWalls.on() ? TextRenderer.TextLayerType.SEE_THROUGH : TextRenderer.TextLayerType.NORMAL,
                        0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
                textDrawn = true;
                continue;
            }

            // ---- hearts: same counting as the mod
            int healthRed = MathHelper.ceil(living.getHealth());
            int maxHealth = MathHelper.ceil(living.getMaxHealth());
            int healthYellow = MathHelper.ceil(living.getAbsorptionAmount());
            int heartsNormal = Math.min(MAX_ICONS, MathHelper.ceil(maxHealth / 2f));
            int heartsYellow = Math.min(MAX_ICONS - heartsNormal, MathHelper.ceil(healthYellow / 2f));
            int heartsRed = MathHelper.ceil(healthRed / 2f);
            boolean lastRedHalf = (healthRed & 1) == 1;
            boolean lastYellowHalf = (healthYellow & 1) == 1;
            int total = heartsNormal + heartsYellow;
            if (total <= 0) continue;

            int rows = (total + row - 1) / row;
            float maxX = (Math.min(total, row) * 8 + 1) / 2f;
            double density = 50.0 - Math.max(4.0 - Math.ceil(total / (double) row), -3.0) * 5.0;
            float rowScale = s / BASE_SCALE;
            Matrix4f[] rowMatrix = new Matrix4f[rows];
            for (int r = 0; r < rows; r++) {
                float up = (float) (r * row / density) * rowScale;
                rowMatrix[r] = new Matrix4f(base).translate(rx, ry + up, rz).rotate(camera.getRotation()).scale(s, -s, s);
            }

            String prefix = effects.on() ? heartPrefix(living) : "";
            int jumping = -1;
            if (effects.on() && living.hasStatusEffect(StatusEffects.REGENERATION)) {
                jumping = living.age % Math.max(1, maxHealth);
            }

            for (int heart = 0; heart < total; heart++) {
                Matrix4f m = rowMatrix[heart / row];
                float x = -maxX + (heart % row) * 8f;
                float y = heart == jumping ? -1.15f : 0f;
                Icon icon = new Icon(m, x, y);
                containers.add(icon);

                String name = null;
                if (heart < heartsRed) {
                    name = "heart/" + prefix + ((heart == heartsRed - 1 && lastRedHalf) ? "half" : "full");
                } else if (heart >= heartsNormal) {
                    name = (heart == total - 1 && lastYellowHalf) ? "heart/absorbing_half" : "heart/absorbing_full";
                }
                if (name != null) fills.computeIfAbsent(sprite(name), k -> new ArrayList<>()).add(icon);
            }

            // ---- armor icons, one row above the hearts
            int armorValue = Math.min(20, MathHelper.ceil(living.getArmor()));
            if (armor.on() && armorValue > 0) {
                int armorPoints = MathHelper.ceil(armorValue / 2f);
                boolean lastArmorHalf = (armorValue & 1) == 1;
                int armorRows = (10 + row - 1) / row;
                float armorMaxX = (Math.min(10, row) * 8 + 1) / 2f;
                Matrix4f[] armorMatrix = new Matrix4f[armorRows];
                for (int r = 0; r < armorRows; r++) {
                    float up = (float) ((rows + r) * row / density) * rowScale;
                    armorMatrix[r] = new Matrix4f(base).translate(rx, ry + up, rz).rotate(camera.getRotation()).scale(s, -s, s);
                }
                for (int point = 0; point < 10; point++) {
                    Icon icon = new Icon(armorMatrix[point / row], -armorMaxX + (point % row) * 8f, 0f);
                    fills.computeIfAbsent(sprite("armor_empty"), k -> new ArrayList<>()).add(icon);
                    if (point < armorPoints) {
                        String name = (point == armorPoints - 1 && lastArmorHalf) ? "armor_half" : "armor_full";
                        fills.computeIfAbsent(sprite(name), k -> new ArrayList<>()).add(icon);
                    }
                }
            }
        }

        RenderSystem.disableCull();
        if (throughWalls.on()) RenderSystem.disableDepthTest();
        else RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (textDrawn) consumers.draw();

        if (!containers.isEmpty()) {
            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX);
            drawIcons(sprite("heart/container"), containers);
            for (Map.Entry<Identifier, List<Icon>> entry : fills.entrySet()) {
                drawIcons(entry.getKey(), entry.getValue());
            }
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void drawIcons(Identifier texture, List<Icon> icons) {
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        for (Icon icon : icons) {
            float x = icon.x(), y = icon.y();
            b.vertex(icon.matrix(), x, y, 0f).texture(0f, 0f);
            b.vertex(icon.matrix(), x, y + 9f, 0f).texture(0f, 1f);
            b.vertex(icon.matrix(), x + 9f, y + 9f, 0f).texture(1f, 1f);
            b.vertex(icon.matrix(), x + 9f, y, 0f).texture(1f, 0f);
        }
        BuiltBuffer built = b.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);
    }
}
