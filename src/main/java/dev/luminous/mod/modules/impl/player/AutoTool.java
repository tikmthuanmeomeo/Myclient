package dev.luminous.mod.modules.impl.player;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PlaySoundEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.block.AirBlock;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.awt.event.KeyEvent;

public class AutoTool extends Module {

    public AutoTool() {
        super("AutoTool", Category.Player);
        setChinese("自动工具");
    }

    private final BooleanSetting mine = add(new BooleanSetting("Mine", true));
    private final BooleanSetting fish = add(new BooleanSetting("Fish", true).setParent());
    private final BooleanSetting autoCast = add(new BooleanSetting("AutoCast", true, fish::isOpen));
    private final SliderSetting ticksAutoCast = add(new SliderSetting("TicksAutoCast", 10, 0, 60, fish::isOpen));
    private final SliderSetting ticksCatch = add(new SliderSetting("TicksCatch", 6, 0, 60, fish::isOpen));
    private final SliderSetting ticksThrow = add(new SliderSetting("TicksThrow", 14, 0, 60, fish::isOpen));
    private final BooleanSetting splashDetection = add(new BooleanSetting("SplashDetection", false, fish::isOpen));
    private final SliderSetting splashDetectionRange = add(new SliderSetting("DetectionRange", 10, 0, 60, fish::isOpen));

    @EventListener
    public void onUpdate(UpdateEvent event) {
        autoFish();
        autoTool();
    }

    public void autoTool() {
        if (!mine.getValue()) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult result)) return;
        BlockPos pos = result.getBlockPos();
        if (mc.world.isAir(pos))
            return;
        int tool = getTool(pos);
        if (tool != -1 && mc.options.attackKey.isPressed()) {
            mc.player.getInventory().selectedSlot = tool;
        }
    }

    private boolean ticksEnabled;
    private int ticksToRightClick;
    private int ticksData;

    private int autoCastTimer;
    private boolean autoCastEnabled;

    private int autoCastCheckTimer;

    @Override
    public void onEnable() {
        ticksEnabled = false;
        autoCastEnabled = false;
        autoCastCheckTimer = 0;
    }

    @EventListener
    private void onPlaySound(PlaySoundEvent event) {
        if (nullCheck()) return;
        if (!fish.getValue()) return;
        SoundInstance p = event.sound;
        FishingBobberEntity b = mc.player.fishHook;
        if (b == null) return;
        if (p.getId().getPath().equals("entity.fishing_bobber.splash")) {
            if (!splashDetection.getValue() || MathUtil.distance(b.getX(), b.getY(), b.getZ(), p.getX(), p.getY(), p.getZ()) <= splashDetectionRange.getValue()) {
                ticksEnabled = true;
                ticksToRightClick = ticksCatch.getValueInt();
                ticksData = 0;
            }
        }
    }

    public void autoFish() {
        if (!fish.getValue()) return;
        // Auto cast
        if (autoCastCheckTimer <= 0) {
            autoCastCheckTimer = 30;

            if (autoCast.getValue() && !ticksEnabled && !autoCastEnabled && mc.player.fishHook == null && mc.player.getMainHandStack().getItem() == Items.FISHING_ROD) {
                autoCastTimer = 0;
                autoCastEnabled = true;
            }
        } else {
            autoCastCheckTimer--;
        }

        // Check for auto cast timer
        if (autoCastEnabled) {
            autoCastTimer++;

            if (autoCastTimer > ticksAutoCast.getValue()) {
                autoCastEnabled = false;
                mc.doItemUse();
            }
        }

        // Handle logic
        if (ticksEnabled && ticksToRightClick <= 0) {
            if (ticksData == 0) {
                mc.doItemUse();
                ticksToRightClick = ticksThrow.getValueInt();
                ticksData = 1;
            } else if (ticksData == 1) {
                mc.doItemUse();
                ticksEnabled = false;
            }
        }

        ticksToRightClick--;
    }

    @EventListener
    private void onKey(KeyEvent event) {
        if (mc.options.useKey.isPressed()) ticksEnabled = false;
    }

    public static int getTool(final BlockPos pos) {
        int index = -1;
        float CurrentFastest = 1.0f;
        for (int i = 0; i < 9; ++i) {
            final ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack != ItemStack.EMPTY) {
                final float digSpeed = EnchantmentHelper.getLevel(mc.world.getRegistryManager().get(Enchantments.EFFICIENCY.getRegistryRef()).getEntry(Enchantments.EFFICIENCY).get(), stack);
                final float destroySpeed = stack.getMiningSpeedMultiplier(mc.world.getBlockState(pos));

                if (mc.world.getBlockState(pos).getBlock() instanceof AirBlock) return -1;
                if (digSpeed + destroySpeed > CurrentFastest) {
                    CurrentFastest = digSpeed + destroySpeed;
                    index = i;
                }
            }
        }
        return index;
    }
}