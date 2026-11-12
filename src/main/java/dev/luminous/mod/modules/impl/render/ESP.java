package dev.luminous.mod.modules.impl.render;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.EntitySpawnedEvent;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.asm.accessors.IEntity;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import net.minecraft.block.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;

public class ESP extends Module {
    public static ESP INSTANCE;

    public final BooleanSetting box = add(new BooleanSetting("BoxESP", true).setParent());
    private final ColorSetting endPortalFill = add(new ColorSetting("EndPortalFill", new Color(255, 243, 129, 100), box::isOpen).injectBoolean(false));
    private final ColorSetting endPortalOutline = add(new ColorSetting("EndPortalOutline", new Color(255, 243, 129, 100), box::isOpen).injectBoolean(false));
    private final ColorSetting itemFill = add(new ColorSetting("ItemFill", new Color(255, 255, 255, 100), box::isOpen).injectBoolean(true));
    private final ColorSetting itemOutline = add(new ColorSetting("ItemOutline", new Color(255, 255, 255, 100), box::isOpen).injectBoolean(true));
    private final ColorSetting playerFill = add(new ColorSetting("PlayerFill", new Color(255, 255, 255, 100), box::isOpen).injectBoolean(true));
    private final ColorSetting playerOutline = add(new ColorSetting("PlayerOutline", new Color(255, 255, 255, 100), box::isOpen).injectBoolean(true));
    private final ColorSetting chestFill = add(new ColorSetting("ChestFill", new Color(255, 198, 123, 100), box::isOpen).injectBoolean(false));
    private final ColorSetting chestOutline = add(new ColorSetting("ChestOutline", new Color(255, 198, 123, 100), box::isOpen).injectBoolean(false));
    private final ColorSetting enderChestFill = add(new ColorSetting("EnderChestFill", new Color(255, 100, 255, 100), box::isOpen).injectBoolean(false));
    private final ColorSetting enderChestOutline = add(new ColorSetting("EnderChestOutline", new Color(255, 100, 255, 100), box::isOpen).injectBoolean(false));
    private final ColorSetting shulkerBoxFill = add(new ColorSetting("ShulkerBoxFill", new Color(15, 255, 255, 100), box::isOpen).injectBoolean(false));
    private final ColorSetting shulkerBoxOutline = add(new ColorSetting("ShulkerBoxOutline", new Color(15, 255, 255, 100), box::isOpen).injectBoolean(false));
    public final BooleanSetting item = add(new BooleanSetting("ItemName", false).setParent());
    public final BooleanSetting customName = add(new BooleanSetting("CustomName", false, item::isOpen));
    public final BooleanSetting count = add(new BooleanSetting("Count", true, item::isOpen));
    private final ColorSetting text = add(new ColorSetting("Text", new Color(255, 255, 255, 255), item::isOpen));
    public final BooleanSetting pearl = add(new BooleanSetting("PearlOwner", true));

    public ESP() {
        super("ESP", Category.Render);
        setChinese("透视");
        INSTANCE = this;
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        if (item.getValue()) {
            for (Entity entity : Alien.THREAD.getEntities()) {
                if (entity instanceof ItemEntity itemEntity) {
                    int itemCount = itemEntity.getStack().getCount();
                    String s = count.getValue() && itemCount > 1 ? " x" + itemCount : "";
                    String name = (customName.getValue() ? itemEntity.getStack().getName() : itemEntity.getStack().getItem().getName()).getString();

                    Render3DUtil.drawText3D(name + s, ((IEntity) itemEntity).getDimensions().getBoxAt(new Vec3d(MathUtil.interpolate(itemEntity.lastRenderX, itemEntity.getX(), mc.getRenderTickCounter().getTickDelta(true)), MathUtil.interpolate(itemEntity.lastRenderY, itemEntity.getY(), mc.getRenderTickCounter().getTickDelta(true)), MathUtil.interpolate(itemEntity.lastRenderZ, itemEntity.getZ(), mc.getRenderTickCounter().getTickDelta(true)))).expand(0, 0.1, 0).getCenter().add(0, 0.5, 0), text.getValue());
                }
            }
        }
        if (box.getValue()) {
            if (itemFill.booleanValue || playerFill.booleanValue) {
                for (Entity entity : Alien.THREAD.getEntities()) {
                    if (entity instanceof ItemEntity && (itemFill.booleanValue || itemOutline.booleanValue)) {
                        Color color = this.itemFill.getValue();
                        Render3DUtil.draw3DBox(matrixStack, ((IEntity) entity).getDimensions().getBoxAt(new Vec3d(MathUtil.interpolate(entity.lastRenderX, entity.getX(), mc.getRenderTickCounter().getTickDelta(true)), MathUtil.interpolate(entity.lastRenderY, entity.getY(), mc.getRenderTickCounter().getTickDelta(true)), MathUtil.interpolate(entity.lastRenderZ, entity.getZ(), mc.getRenderTickCounter().getTickDelta(true)))), color, itemOutline.getValue(), itemOutline.booleanValue, itemFill.booleanValue);
                    } else if (entity instanceof PlayerEntity && (playerFill.booleanValue || playerOutline.booleanValue)) {
                        Color color = this.playerFill.getValue();
                        Render3DUtil.draw3DBox(matrixStack, ((IEntity) entity).getDimensions().getBoxAt(new Vec3d(MathUtil.interpolate(entity.lastRenderX, entity.getX(), mc.getRenderTickCounter().getTickDelta(true)), MathUtil.interpolate(entity.lastRenderY, entity.getY(), mc.getRenderTickCounter().getTickDelta(true)), MathUtil.interpolate(entity.lastRenderZ, entity.getZ(), mc.getRenderTickCounter().getTickDelta(true)))).expand(0, 0.1, 0), color, playerOutline.getValue(), playerOutline.booleanValue, playerFill.booleanValue);
                    }
                }
            }
            ArrayList<BlockEntity> blockEntities = BlockUtil.getTileEntities();
            for (BlockEntity blockEntity : blockEntities) {
                if (blockEntity instanceof ChestBlockEntity && (chestFill.booleanValue || chestOutline.booleanValue)) {
                    Box box = new Box(blockEntity.getPos());
                    Render3DUtil.draw3DBox(matrixStack, box, chestFill.getValue(), chestOutline.getValue(), chestOutline.booleanValue, chestFill.booleanValue);
                } else if (blockEntity instanceof EnderChestBlockEntity && (enderChestFill.booleanValue || enderChestOutline.booleanValue)) {
                    Box box = new Box(blockEntity.getPos());
                    Render3DUtil.draw3DBox(matrixStack, box, enderChestFill.getValue(), enderChestOutline.getValue(), enderChestOutline.booleanValue, enderChestFill.booleanValue);
                } else if (blockEntity instanceof ShulkerBoxBlockEntity && (shulkerBoxFill.booleanValue || shulkerBoxOutline.booleanValue)) {
                    Box box = new Box(blockEntity.getPos());
                    Render3DUtil.draw3DBox(matrixStack, box, shulkerBoxFill.getValue(), shulkerBoxOutline.getValue(), shulkerBoxOutline.booleanValue, shulkerBoxFill.booleanValue);
                } else if (blockEntity instanceof EndPortalBlockEntity && (endPortalFill.booleanValue || endPortalOutline.booleanValue)) {
                    Box box = new Box(blockEntity.getPos());
                    Render3DUtil.draw3DBox(matrixStack, box, endPortalFill.getValue(), endPortalOutline.getValue(), endPortalOutline.booleanValue, endPortalFill.booleanValue);
                }
            }
        }
    }

    @EventListener
    public void onReceivePacket(EntitySpawnedEvent event) {
        if (nullCheck()) return;
        if (pearl.getValue() && event.getEntity() instanceof EnderPearlEntity pearlEntity) {
            if (pearlEntity.getOwner() != null) {
                pearlEntity.setCustomName(pearlEntity.getOwner().getName());
                pearlEntity.setCustomNameVisible(true);
            } else {
                mc.world.getPlayers().stream().min(Comparator.comparingDouble((p) -> p.getPos().distanceTo(new Vec3d(pearlEntity.getX(), pearlEntity.getY(), pearlEntity.getZ())))).ifPresent((player) -> {
                    pearlEntity.setCustomName(player.getName());
                    pearlEntity.setCustomNameVisible(true);
                });
            }
        }
    }
}
