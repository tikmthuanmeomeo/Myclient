package dev.luminous.mod.modules.impl.movement;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.InteractItemEvent;
import dev.luminous.api.events.impl.KeyboardInputEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

public class NoSlow extends Module {
    public static NoSlow INSTANCE;

    final Queue<ClickSlotC2SPacket> storedClicks = new LinkedList<>();
    final AtomicBoolean pause = new AtomicBoolean();

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Vanilla));
    private final BooleanSetting soulSand = add(new BooleanSetting("SoulSand", true));
    private final BooleanSetting sneak = add(new BooleanSetting("Sneak", false));
    private final BooleanSetting climb = add(new BooleanSetting("Climb", false));
    private final BooleanSetting gui = add(new BooleanSetting("Gui", true));
    private final BooleanSetting allowSneak = add(new BooleanSetting("AllowSneak", false, gui::getValue));
    private final EnumSetting<Bypass> clickBypass = add(new EnumSetting<>("GuiMoveBypass", Bypass.None));
    boolean using = false;
    int delay = 0;

    public NoSlow() {
        super("NoSlow", Category.Movement);
        setChinese("无减速");
        INSTANCE = this;
    }

    private static float getMovementMultiplier(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0F;
        } else {
            return positive ? 1.0F : -1.0F;
        }
    }

    @Override
    public String getInfo() {
        return mode.getValue().name();
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        using = mc.player.isUsingItem();
        delay--;
        if (using) {
            delay = 2;
        }
        if (using && !mc.player.isRiding() && !mc.player.isFallFlying()) {
            switch (mode.getValue()) {
                case NCP ->
                        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
                case Grim -> {
                    if (mc.player.getActiveHand() == Hand.OFF_HAND) {
                        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
                    } else {
                        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
                    }
                }
                case GrimPacket -> {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 1, 0, SlotActionType.PICKUP, mc.player);
                    if (mc.player.getActiveHand() == Hand.OFF_HAND) {
                        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
                    } else {
                        sendSequencedPacket(id -> new PlayerInteractItemC2SPacket(Hand.OFF_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
                    }
                }
            }
        }
        if (gui.getValue()) {
            if (!(mc.currentScreen instanceof ChatScreen)) {
                for (KeyBinding k : new KeyBinding[]{mc.options.backKey, mc.options.leftKey, mc.options.rightKey}) {
                    k.setPressed(InputUtil.isKeyPressed(mc.getWindow().getHandle(), InputUtil.fromTranslationKey(k.getBoundKeyTranslationKey()).getCode()));
                }
                mc.options.jumpKey.setPressed(ElytraFly.INSTANCE.isOn() && ElytraFly.INSTANCE.mode.is(ElytraFly.Mode.Bounce) && ElytraFly.INSTANCE.autoJump.getValue() || InputUtil.isKeyPressed(mc.getWindow().getHandle(), InputUtil.fromTranslationKey(mc.options.jumpKey.getBoundKeyTranslationKey()).getCode()));
                mc.options.forwardKey.setPressed(AutoWalk.INSTANCE.forward() || InputUtil.isKeyPressed(mc.getWindow().getHandle(), InputUtil.fromTranslationKey(mc.options.forwardKey.getBoundKeyTranslationKey()).getCode()));
                mc.options.sprintKey.setPressed(Sprint.INSTANCE.isOn() && !Sprint.INSTANCE.inWater() || InputUtil.isKeyPressed(mc.getWindow().getHandle(), InputUtil.fromTranslationKey(mc.options.sprintKey.getBoundKeyTranslationKey()).getCode()));

                if (allowSneak.getValue()) {
                    mc.options.sneakKey.setPressed(InputUtil.isKeyPressed(mc.getWindow().getHandle(), InputUtil.fromTranslationKey(mc.options.sneakKey.getBoundKeyTranslationKey()).getCode()));
                }
            }
        }
    }

    @EventListener(priority = EventPriority.HIGH)
    public void keyboard(KeyboardInputEvent event) {
        if (sneak.getValue()) {
            event.cancel();
        }
        if (gui.getValue()) {
            if (!(mc.currentScreen instanceof ChatScreen)) {
                for (KeyBinding k : new KeyBinding[]{mc.options.backKey, mc.options.leftKey, mc.options.rightKey}) {
                    k.setPressed(InputUtil.isKeyPressed(mc.getWindow().getHandle(), InputUtil.fromTranslationKey(k.getBoundKeyTranslationKey()).getCode()));
                }
                mc.options.jumpKey.setPressed(ElytraFly.INSTANCE.isOn() && ElytraFly.INSTANCE.mode.is(ElytraFly.Mode.Bounce) && ElytraFly.INSTANCE.autoJump.getValue() || InputUtil.isKeyPressed(mc.getWindow().getHandle(), InputUtil.fromTranslationKey(mc.options.jumpKey.getBoundKeyTranslationKey()).getCode()));
                mc.options.forwardKey.setPressed(AutoWalk.INSTANCE.forward() || InputUtil.isKeyPressed(mc.getWindow().getHandle(), InputUtil.fromTranslationKey(mc.options.forwardKey.getBoundKeyTranslationKey()).getCode()));
                mc.options.sprintKey.setPressed(Sprint.INSTANCE.isOn() && !Sprint.INSTANCE.inWater() || InputUtil.isKeyPressed(mc.getWindow().getHandle(), InputUtil.fromTranslationKey(mc.options.sprintKey.getBoundKeyTranslationKey()).getCode()));

                if (allowSneak.getValue()) {
                    mc.options.sneakKey.setPressed(InputUtil.isKeyPressed(mc.getWindow().getHandle(), InputUtil.fromTranslationKey(mc.options.sneakKey.getBoundKeyTranslationKey()).getCode()));
                }
                mc.player.input.pressingForward = mc.options.forwardKey.isPressed();
                mc.player.input.pressingBack = mc.options.backKey.isPressed();
                mc.player.input.pressingLeft = mc.options.leftKey.isPressed();
                mc.player.input.pressingRight = mc.options.rightKey.isPressed();
                mc.player.input.movementForward = getMovementMultiplier(mc.player.input.pressingForward, mc.player.input.pressingBack);
                mc.player.input.movementSideways = getMovementMultiplier(mc.player.input.pressingLeft, mc.player.input.pressingRight);
                mc.player.input.jumping = mc.options.jumpKey.isPressed();
                mc.player.input.sneaking = mc.options.sneakKey.isPressed();
            }
        }
    }

    @EventListener
    public void onUse(InteractItemEvent event) {
        if (event.isPre()) {
            if (delay > 0) {
                mc.itemUseCooldown = 0;
                event.cancel();
            } else if (mode.is(Mode.GrimPacket) && mc.player != null && mc.player.getStackInHand(event.hand).getItem().getComponents().contains(DataComponentTypes.FOOD)) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 1, 0, SlotActionType.PICKUP, mc.player);
            }
        }
    }

    @EventListener
    public void onPacketSend(PacketEvent.Send e) {
        if (nullCheck()) {
            return;
        }
        if (mode.is(Mode.Drop) && e.getPacket() instanceof PlayerInteractItemC2SPacket packet) {
            if (packet.getHand() == Hand.MAIN_HAND && mc.player.getMainHandStack().getItem().getComponents().contains(DataComponentTypes.FOOD)) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.DROP_ITEM, BlockPos.ORIGIN, Direction.DOWN));
                return;
            }
        }
        if (!MovementUtil.isMoving() || pause.get()) {
            return;
        }

        if (e.getPacket() instanceof ClickSlotC2SPacket click) {
            switch (clickBypass.getValue()) {
                case Grim -> {
                    if (click.getActionType() != SlotActionType.PICKUP && click.getActionType() != SlotActionType.PICKUP_ALL)
                        mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(0));
                }

                case NCP -> {
                    if (mc.player.isOnGround() && !mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(0.0, 0.0656, 0.0)).iterator().hasNext()) {
                        if (mc.player.isSprinting())
                            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.0656, mc.player.getZ(), false));
                    }
                }

                case NCP2 -> {
                    if (mc.player.isOnGround() && !mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().offset(0.0, 0.000000271875, 0.0)).iterator().hasNext()) {
                        if (mc.player.isSprinting())
                            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY() + 0.000000271875, mc.player.getZ(), false));
                    }
                }

                case Delay -> {
                    storedClicks.add(click);
                    e.cancel();
                }
            }
        }

        if (e.getPacket() instanceof CloseHandledScreenC2SPacket) {
            if (clickBypass.is(Bypass.Delay)) {
                pause.set(true);
                while (!storedClicks.isEmpty())
                    mc.getNetworkHandler().sendPacket(storedClicks.poll());
                pause.set(false);
            }
        }
    }

    @EventListener
    public void onPacketSendPost(PacketEvent.Sent e) {
        if (e.getPacket() instanceof ClickSlotC2SPacket) {
            if (mc.player.isSprinting() && clickBypass.is(Bypass.NCP))
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
        }
    }

    public boolean noSlow() {
        return isOn() && mode.getValue() != Mode.None && (mode.getValue() != Mode.Drop && mode.getValue() != Mode.GrimPacket || using);
    }

    public boolean soulSand() {
        return isOn() && soulSand.getValue();
    }

    public boolean climb() {
        return isOn() && climb.getValue();
    }

    private enum Bypass {
        None, NCP, NCP2, Grim, Delay,
    }

    public enum Mode {
        Vanilla,
        NCP,
        Grim,
        GrimPacket,
        Drop,
        None
    }
}
