package dev.luminous.mod.modules.impl.misc;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.DeathEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.TotemEvent;
import dev.luminous.api.utils.player.InteractUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import dev.luminous.mod.modules.settings.impl.StringSetting;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Chats a message when you pop somebody's totem (sword / crystal / anchor / bed) and another one
 * when somebody you popped or hit dies. Use %player% in a message for the player's name.
 */
public class AutoEZ extends Module {
    public static AutoEZ INSTANCE;

    private final EnumSetting<Page> page = add(new EnumSetting<>("Page", Page.Totem));

    private final BooleanSetting totemChat = add(new BooleanSetting("TotemChat", true, () -> page.getValue() == Page.Totem));
    private final StringSetting totemMsg = add(new StringSetting("TotemMsg", "%player% popped a totem", () -> page.getValue() == Page.Totem));
    private final BooleanSetting showCount = add(new BooleanSetting("ShowCount", true, () -> page.getValue() == Page.Totem));

    private final BooleanSetting deathChat = add(new BooleanSetting("DeathChat", true, () -> page.getValue() == Page.Death));
    private final StringSetting deathMsg = add(new StringSetting("DeathMsg", "EZ %player%", () -> page.getValue() == Page.Death));

    private final SliderSetting range = add(new SliderSetting("Range", 20, 0, 60, 1).setSuffix("m"));
    private final SliderSetting delay = add(new SliderSetting("Delay", 1000, 0, 5000, 50).setSuffix("ms"));

    private final Map<UUID, Long> lastHit = new HashMap<>();
    private final Map<UUID, Long> popCredit = new HashMap<>();
    private final Map<UUID, Integer> popCount = new HashMap<>();
    private int totalPops = 0;
    private long actionTime = 0L;
    private Vec3d actionPos = null;
    private long lastMessage = 0L;

    public AutoEZ() {
        super("AutoEZ", "Chats when you pop a totem or kill someone", Category.Misc);
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        lastHit.clear();
        popCredit.clear();
        popCount.clear();
        totalPops = 0;
        actionPos = null;
    }

    @EventListener
    public void onSend(PacketEvent.Send event) {
        if (nullCheck()) return;
        try {
            if (event.getPacket() instanceof PlayerInteractEntityC2SPacket packet
                    && InteractUtil.getInteractType(packet) == PlayerInteractEntityC2SPacket.InteractType.ATTACK) {
                Entity target = InteractUtil.getEntity(packet);
                if (target instanceof PlayerEntity p) {
                    lastHit.put(p.getUuid(), System.currentTimeMillis());
                } else if (target instanceof EndCrystalEntity) {
                    actionTime = System.currentTimeMillis();
                    actionPos = target.getPos();
                }
            } else if (event.getPacket() instanceof PlayerInteractBlockC2SPacket packet) {
                BlockPos pos = packet.getBlockHitResult().getBlockPos();
                Block block = mc.world.getBlockState(pos).getBlock();
                if (block instanceof RespawnAnchorBlock || block instanceof BedBlock) {
                    actionTime = System.currentTimeMillis();
                    actionPos = Vec3d.ofCenter(pos);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** True when the pop / death can be credited to the local player. */
    private boolean causedByMe(PlayerEntity p) {
        long now = System.currentTimeMillis();
        Long hit = lastHit.get(p.getUuid());
        if (hit != null && now - hit < 1500) return true;
        return actionPos != null && now - actionTime < 1500 && actionPos.distanceTo(p.getPos()) < 12;
    }

    private boolean valid(PlayerEntity p) {
        if (p == null || p == mc.player || Alien.FRIEND.isFriend(p)) return false;
        return range.getValue() <= 0 || mc.player.distanceTo(p) <= range.getValue();
    }

    /**
     * Placeholders: %player% = name, %pops% = totems you popped on that player, %total% = totems you popped on everyone.
     * When appendCount is set and the message has no count placeholder, " (pops)" is added at the end.
     */
    private void say(String template, PlayerEntity p, int pops, boolean appendCount) {
        if (template == null || template.isBlank()) return;
        long now = System.currentTimeMillis();
        if (now - lastMessage < delay.getValue()) return;
        lastMessage = now;
        String name = p.getName().getString();
        boolean hasCount = template.contains("%pops%") || template.contains("{pops}")
                || template.contains("%total%") || template.contains("{total}");
        String msg = template;
        if (appendCount && !hasCount) msg = msg + " (" + pops + ")";
        msg = msg.replace("%player%", name).replace("{player}", name)
                .replace("%pops%", String.valueOf(pops)).replace("{pops}", String.valueOf(pops))
                .replace("%total%", String.valueOf(totalPops)).replace("{total}", String.valueOf(totalPops));
        mc.getNetworkHandler().sendChatMessage(msg);
    }

    @EventListener
    public void onTotem(TotemEvent event) {
        if (nullCheck()) return;
        PlayerEntity p = event.getPlayer();
        if (!valid(p) || !causedByMe(p)) return;
        popCredit.put(p.getUuid(), System.currentTimeMillis());
        int pops = popCount.merge(p.getUuid(), 1, Integer::sum);
        totalPops++;
        if (totemChat.getValue()) say(totemMsg.getValue(), p, pops, showCount.getValue());
    }

    @EventListener
    public void onDeath(DeathEvent event) {
        if (nullCheck()) return;
        PlayerEntity p = event.getPlayer();
        if (!valid(p)) return;
        long now = System.currentTimeMillis();
        Long hit = lastHit.get(p.getUuid());
        Long pop = popCredit.get(p.getUuid());
        boolean credited = (hit != null && now - hit < 8000) || (pop != null && now - pop < 20000) || causedByMe(p);
        int pops = popCount.getOrDefault(p.getUuid(), 0);
        lastHit.remove(p.getUuid());
        popCredit.remove(p.getUuid());
        popCount.remove(p.getUuid());
        if (credited && deathChat.getValue()) say(deathMsg.getValue(), p, pops, false);
    }

    private enum Page {
        Totem, Death
    }
}
