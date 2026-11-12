package dev.luminous.mod.modules.impl.combat;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.DeathEvent;
import dev.luminous.api.events.impl.TotemEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Prints a chat message when a player pops a totem or dies after popping.
 */
public class TotemPops extends Module {
    public static TotemPops INSTANCE;

    private final BooleanSetting self = add(new BooleanSetting("Self", true));
    private final BooleanSetting friends = add(new BooleanSetting("Friends", true));
    private final BooleanSetting enemies = add(new BooleanSetting("Enemies", true));
    private final BooleanSetting deaths = add(new BooleanSetting("Deaths", true));

    public TotemPops() {
        super("TotemPops", "Notifies you when players pop totems", Category.Combat);
        INSTANCE = this;
    }

    private boolean allowed(PlayerEntity player) {
        if (player == mc.player) return self.getValue();
        if (Alien.FRIEND.isFriend(player)) return friends.getValue();
        return enemies.getValue();
    }

    @EventListener
    public void onTotem(TotemEvent event) {
        if (nullCheck()) return;
        PlayerEntity player = event.getPlayer();
        if (player == null || !allowed(player)) return;
        int pops = Alien.POP.getPop(player);
        sendMessage("§f" + player.getName().getString() + " §7popped §c" + pops + (pops == 1 ? " §7totem" : " §7totems"));
    }

    @EventListener
    public void onDeath(DeathEvent event) {
        if (nullCheck() || !deaths.getValue()) return;
        PlayerEntity player = event.getPlayer();
        if (player == null || !allowed(player)) return;
        // PopManager clears the counter after posting DeathEvent, so the value is still valid here.
        int pops = Alien.POP.getPop(player);
        if (pops > 0) {
            sendMessage("§f" + player.getName().getString() + " §7died after popping §c" + pops + (pops == 1 ? " §7totem" : " §7totems"));
        } else {
            sendMessage("§f" + player.getName().getString() + " §7died");
        }
    }
}
