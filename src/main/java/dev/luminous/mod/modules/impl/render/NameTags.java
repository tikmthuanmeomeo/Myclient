package dev.luminous.mod.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.Render3DEvent;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.TextRadar;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NameTags
        extends Module {
    public static NameTags INSTANCE;
    final ColorSetting colorConfig =
            add(new ColorSetting("Color", new Color(255, 255, 255)));
    final ColorSetting friendConfig =
            add(new ColorSetting("Friend", new Color(155, 155, 255)).injectBoolean(true));
    final ColorSetting invisibleConfig =
            add(new ColorSetting("Invisible", new Color(200, 200, 200)).injectBoolean(true));
    final ColorSetting died =
            add(new ColorSetting("Died", new Color(180, 0, 0)).injectBoolean(true));
    final ColorSetting sneakingConfig =
            add(new ColorSetting("Sneaking", new Color(200, 200, 0)).injectBoolean(true));
    final ColorSetting rectConfig =
            add(new ColorSetting("Rectangle", new Color(0, 0, 0, 100)).injectBoolean(true));
    final BooleanSetting armorConfig = add(new BooleanSetting("Armor", true).setParent());
    final BooleanSetting drawItemConfig = add(new BooleanSetting("DrawItem", true, armorConfig::isOpen));
    final SliderSetting offsetConfig = add(new SliderSetting("Offset", -20, -30, 10, 0.01, armorConfig::isOpen));
    final BooleanSetting enchantmentsConfig = add(new BooleanSetting("Enchantments", true));
    final BooleanSetting durabilityConfig = add(new BooleanSetting("Durability", true).setParent());
    final BooleanSetting forceBarConfig = add(new BooleanSetting("ForceBar", true, durabilityConfig::isOpen));
    final BooleanSetting itemNameConfig = add(new BooleanSetting("ItemName", false));
    final BooleanSetting entityIdConfig = add(new BooleanSetting("EntityId", false));
    final BooleanSetting gamemodeConfig = add(new BooleanSetting("Gamemode", false));
    final BooleanSetting pingConfig = add(new BooleanSetting("Ping", true));
    final BooleanSetting healthConfig = add(new BooleanSetting("Health", true));
    final BooleanSetting totemsConfig = add(new BooleanSetting("Totems", false));
    final SliderSetting scaleConfig = add(new SliderSetting("Scale", 1, 0, 3, 0.1));
    final BooleanSetting factorConfig = add(new BooleanSetting("Factor", true).setParent());
    final SliderSetting scalingConfig = add(new SliderSetting("Scaling", 1, 0, 3, 0.1, factorConfig::isOpen));
    final SliderSetting distanceConfig = add(new SliderSetting("Distance", 6, 0, 20, 0.1, factorConfig::isOpen));
    final SliderSetting heightConfig = add(new SliderSetting("Height", 0, -3, 3, 0.01));

    public NameTags() {
        super("NameTags", "Renders info on player NameTags", Category.Render);
        setChinese("名字标签");
        INSTANCE = this;
    }

    @EventListener
    public void onRender3D(Render3DEvent event) {
        if (mc.gameRenderer == null || mc.getCameraEntity() == null) {
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();

        RenderSystem.enableBlend();
        //RenderSystem.disableDepthTest();
        GL11.glDepthFunc(GL11.GL_ALWAYS);
        MatrixStack matrixStack = new MatrixStack();
        for (PlayerEntity player : Alien.THREAD.getPlayers()) {
            if ((!died.booleanValue && !player.isAlive()) || player == mc.player && mc.options.getPerspective().isFirstPerson() || !invisibleConfig.booleanValue && player.isInvisible()) {
                continue;
            }
            String info = getNametagInfo(player);
            Vec3d renderPosition = MathUtil.getRenderPosition(player, event.tickDelta);
            double x = renderPosition.getX();
            double y = renderPosition.getY();
            double z = renderPosition.getZ();
            int width = mc.textRenderer.getWidth(info);
            float hwidth = width / 2.0f;
            renderInfo(info, hwidth, player, x, y, z, camera, matrixStack);
        }
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        //RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void renderInfo(String info, float width, PlayerEntity entity,
                            double x, double y, double z, Camera camera, MatrixStack matrices) {
        final Vec3d pos = camera.getPos();
        double eyeY = y + (double) entity.getHeight() + (entity.isSneaking() ? 0.4f : 0.43f) + heightConfig.getValueFloat();
        float scale = (float) (-0.025f * scaleConfig.getValueFloat() + (factorConfig.getValue() && pos.squaredDistanceTo(x, eyeY, z) > (distanceConfig.getValueFloat() * distanceConfig.getValueFloat()) ? (Math.sqrt(pos.squaredDistanceTo(x, eyeY, z)) - distanceConfig.getValueFloat()) * -0.0025f * scalingConfig.getValueFloat() : 0));
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0f));
        matrices.translate(x - pos.getX(),
                eyeY - pos.getY() + (scale / -0.025f - 1) / 4,
                z - pos.getZ());
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

        matrices.scale(scale, scale, -1.0f);

        if (rectConfig.booleanValue) {
            Render2DUtil.drawRect(matrices, -width - 2.0f, -1.0f, width * 2.0f + 3.0f,
                    mc.textRenderer.fontHeight + 1.0f, rectConfig.getValue());
        }
        drawWithShadow(matrices, info, -width, 0.0f, getNametagColor(entity));
        if (armorConfig.getValue()) {
            renderItems(matrices, entity);
        }

        matrices.pop();
    }

    private void drawWithShadow(MatrixStack matrices, String info, float x, float y, int color) {
        var immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        mc.textRenderer.drawLayer(info, x, y, color, true, matrices.peek().getPositionMatrix(), immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xf000f0);
        immediate.draw();

        mc.textRenderer.draw(info, x, y, color, false, matrices.peek().getPositionMatrix(), immediate, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xf000f0);
        immediate.draw();
    }

    private void renderItems(MatrixStack matrixStack, PlayerEntity player) {
        List<ItemStack> displayItems = new CopyOnWriteArrayList<>();
        if (!player.getOffHandStack().isEmpty()) {
            displayItems.add(player.getOffHandStack());
        }
        player.getInventory().armor.forEach(armorStack ->
        {
            if (!armorStack.isEmpty()) {
                displayItems.add(armorStack);
            }
        });
        if (!player.getMainHandStack().isEmpty()) {
            displayItems.add(player.getMainHandStack());
        }
        Collections.reverse(displayItems);
        float x = 0;
        int n11 = 0;
        for (ItemStack stack : displayItems) {
            x -= 8;
            if (stack.getEnchantments().getSize() > n11) {
                n11 = stack.getEnchantments().getSize();
            }
        }
        float y = offsetConfig.getValueFloat();
        for (ItemStack stack : displayItems) {
            GL11.glDepthFunc(GL11.GL_ALWAYS);
            if (drawItemConfig.getValue()) renderItemStack(matrixStack, stack, x, y + 1f);

            renderItemOverlay(matrixStack, stack, x, y + 2.5f);

            matrixStack.scale(0.5f, 0.5f, 0.5f);
            if (durabilityConfig.getValue()) {
                renderDurability(matrixStack, stack, x + 2.0f, y + 11.5f);
            }
            if (enchantmentsConfig.getValue()) {
                renderEnchants(matrixStack, stack, x + 2.0f, y + 7f);
            }
            matrixStack.scale(2.0f, 2.0f, 2.0f);
            x += 16;
            GL11.glDepthFunc(GL11.GL_LEQUAL);
        }

        ItemStack heldItem = player.getMainHandStack();
        if (heldItem.isEmpty()) {
            return;
        }
        matrixStack.scale(0.5f, 0.5f, 0.5f);
        if (itemNameConfig.getValue()) {
            renderItemName(matrixStack, heldItem, y - 4.5f + enchantOffset(n11));
        }
        matrixStack.scale(2.0f, 2.0f, 2.0f);
    }

    private void renderItemStack(MatrixStack matrixStack, ItemStack stack, float x, float y) {
        matrixStack.push();
        matrixStack.translate(x, y, 0.0f);
        matrixStack.translate(8.0f, 8.0f, 0.0f);
        matrixStack.scale(16.0f, 16.0f, 0.00000001f);
        matrixStack.multiplyPositionMatrix(new Matrix4f().scaling(1.0f, -1.0f, 1.0f));
        Vector3f a = RenderSystem.shaderLightDirections[0];
        Vector3f b = RenderSystem.shaderLightDirections[1];
        DiffuseLighting.disableGuiDepthLighting();
        BakedModel model = mc.getItemRenderer().getModel(stack, mc.world, null, 0);

        var i = mc.getBufferBuilders().getEntityVertexConsumers();
        mc.getItemRenderer().renderItem(stack, ModelTransformationMode.GUI, false, matrixStack, i, 0xff0000, OverlayTexture.DEFAULT_UV, model);
        i.draw();
        RenderSystem.setShaderLights(a, b);

        matrixStack.pop();
    }

    private void renderItemOverlay(MatrixStack matrixStack, ItemStack stack, float x, float y) {
        matrixStack.push();
        if (stack.getCount() != 1) {
            String string = String.valueOf(stack.getCount());
            // this.matrices.translate(0.0f, 0.0f, 200.0f);
            drawWithShadow(matrixStack, string, x + 17 - mc.textRenderer.getWidth(string), y + 9.0f, -1);
        }
        if (stack.isItemBarVisible() || (stack.isDamageable() && forceBarConfig.getValue())) {
            int i = stack.getItemBarStep();
            int j = stack.getItemBarColor();
            float k = x + 2;
            float l = y + 13;
            Render2DUtil.drawRect(matrixStack, k, l, 13, 2, Colors.BLACK);
            Render2DUtil.drawRect(matrixStack, k, l, i, 1, j | Colors.BLACK);
        }
        matrixStack.pop();
    }

    private void renderDurability(MatrixStack matrixStack, ItemStack itemStack, float x, float y) {
        if (!itemStack.isDamageable()) {
            return;
        }
        int n = itemStack.getMaxDamage();
        int n2 = itemStack.getDamage();
        int durability = (int) ((n - n2) / ((float) n) * 100.0f);
        drawWithShadow(matrixStack, durability + "%", x * 2, y * 2,
                ColorUtil.hslToColor((float) (n - n2) / (float) n * 120.0f, 100.0f, 50.0f, 1.0f).getRGB());
    }

    private void renderEnchants(MatrixStack matrixStack, ItemStack itemStack, float x, float y) {
        if (itemStack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) {
            drawWithShadow(matrixStack, "God", x * 2, y * 2, 0xffc34e41);
            return;
        }
//        if (itemStack.getItem() == Items.TOTEM_OF_UNDYING) {
//            drawWithShadow(matrixStack, "Totem", x * 2, y * 2, 0xffc34e41);
//            return;
//        }
        if (!itemStack.hasEnchantments()) {
            return;
        }
        ItemEnchantmentsComponent enchants = EnchantmentHelper.getEnchantments(itemStack);

        float n2 = 0;
        for (RegistryEntry<Enchantment> enchantment : enchants.getEnchantments()) {
            int lvl = enchants.getLevel(enchantment);
            StringBuilder enchantString = new StringBuilder();
            String translatedName = enchantment.value().toString().replace("Enchantment ", "");
            if (translatedName.contains("Vanish")) {
                enchantString.append("§cVan");
            } else if (translatedName.contains("Bind")) {
                enchantString.append("§cBind");
            } else {
                int maxLen = lvl > 1 ? 2 : 3;
                if (translatedName.length() > maxLen) {
                    translatedName = translatedName.substring(0, maxLen);
                }
                enchantString.append(translatedName);
                enchantString.append(lvl);
            }
            drawWithShadow(matrixStack, enchantString.toString(), x * 2, (y + n2) * 2, -1);
            n2 -= 4.5f;
        }
    }

    private float enchantOffset(final int n) {
        if (!enchantmentsConfig.getValue() || n <= 2) {
            return 0;
        }
        float value = -2;
        value -= (n - 3) * 4.5f;
        return value;
    }

    private void renderItemName(MatrixStack matrixStack, ItemStack itemStack, float y) {
        String itemName = itemStack.getName().getString();
        float width = mc.textRenderer.getWidth(itemName) / 4.0f;
        drawWithShadow(matrixStack, itemName, ((float) 0 - width) * 2, y * 2, -1);
    }

    private String getNametagInfo(PlayerEntity player) {
        final StringBuilder info = new StringBuilder();
        if (gamemodeConfig.getValue()) {
            if (player.isCreative()) {
                info.append(Formatting.GOLD);
                info.append("[C] ");
            } else if (player.isSpectator()) {
                info.append(Formatting.GRAY);
                info.append("[I] ");
            } else {
                info.append(Formatting.BOLD);
                info.append("[S] ");
            }
        }
        if (pingConfig.getValue()) {
            info.append(getEntityPing(player));
            info.append("ms ");
            info.append(Formatting.RESET);
        }
        info.append(player.getName().getString());
        info.append(" ");
        if (entityIdConfig.getValue()) {
            info.append("ID: ");
            info.append(player.getId());
            info.append(" ");
        }
        if (healthConfig.getValue()) {
            double health = player.getHealth() + player.getAbsorptionAmount();

            Formatting hcolor;
            if (health > 18) {
                hcolor = Formatting.GREEN;
            } else if (health > 16) {
                hcolor = Formatting.DARK_GREEN;
            } else if (health > 12) {
                hcolor = Formatting.YELLOW;
            } else if (health > 8) {
                hcolor = Formatting.GOLD;
            } else if (health > 4) {
                hcolor = Formatting.RED;
            } else {
                hcolor = Formatting.DARK_RED;
            }
            String phealth = df.format(health);
            info.append(hcolor);
            info.append(phealth);
            info.append(" ");
        }
        if (totemsConfig.getValue()) {
            int totems = Alien.POP.getPop(player);
            if (totems > 0) {

                Formatting c = TextRadar.getPopColor(totems);
                info.append(c);
                info.append(-totems);
                info.append(" ");
            }
        }
        return info.toString().trim();
    }

    private String getEntityPing(PlayerEntity entity) {
        if (mc.getNetworkHandler() == null) return "§7-1";

        PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        if (playerListEntry == null) return "§7-1";

        int ping = playerListEntry.getLatency();
        Formatting color;
        if (ping >= 200) {
            color = Formatting.RED;
        } else if (ping >= 100) {
            color = Formatting.YELLOW;
        } else {
            color = Formatting.GREEN;
        }

        return color.toString() + ping;
    }

    final DecimalFormat df = new DecimalFormat("0.0");

    private int getNametagColor(PlayerEntity player) {
        if (friendConfig.booleanValue && player.getDisplayName() != null && Alien.FRIEND.isFriend(player)) {
            return friendConfig.getValue().getRGB();
        }
        if (invisibleConfig.booleanValue && player.isInvisible()) {
            return invisibleConfig.getValue().getRGB();
        }
        if (sneakingConfig.booleanValue && player.isSneaking()) {
            return sneakingConfig.getValue().getRGB();
        }
        if (!player.isAlive()) {
            return died.getValue().getRGB();
        }
        return colorConfig.getValue().getRGB();
    }
}