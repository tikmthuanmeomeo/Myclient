package dev.luminous.mod.modules.impl.client;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.StringSetting;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;

/**
 * Makes the game report a different client brand ("vanilla" by default) and stops the plugin-channel
 * registration packet that lists mod channels. Applies when connecting, so it must be on before you join.
 * This only changes what the client announces; it does not hide how you play.
 */
public class ClientSpoof extends Module {
    public static ClientSpoof INSTANCE;

    private final StringSetting brand = add(new StringSetting("Brand", "vanilla"));
    private final BooleanSetting hideChannels = add(new BooleanSetting("BlockChannels", false));

    public ClientSpoof() {
        super("ClientSpoof", "Reports a vanilla client brand to servers", Category.Client);
        INSTANCE = this;
    }

    public String brand() {
        String b = brand.getValue();
        return b == null || b.isBlank() ? "vanilla" : b.trim();
    }

    @EventListener
    public void onSend(PacketEvent.Send event) {
        if (!hideChannels.getValue()) return;
        try {
            if (event.getPacket() instanceof CustomPayloadC2SPacket packet) {
                String id = packet.payload().getId().id().toString();
                if (id.equals("minecraft:register") || id.equals("minecraft:unregister")) {
                    event.cancel();
                }
            }
        } catch (Exception ignored) {
        }
    }
}
