package abcxyz.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

/** The short sound played when a new suspicious zone is found (assets/abcxyz/sounds.json: sus_chunk). */
public final class SusSound {
    private static final SoundEvent SUS_CHUNK = SoundEvent.of(Identifier.of("abcxyz", "sus_chunk"));
    public static float volume = 1.0f;
    private static long lastSoundMs = 0L;

    private SusSound() {
    }

    public static void playFoundSound() {
        long now = System.currentTimeMillis();
        if (now - lastSoundMs < 250L) return;
        lastSoundMs = now;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getSoundManager() == null) return;
            mc.getSoundManager().play(new Instance(SUS_CHUNK, volume));
        } catch (Throwable ignored) {
        }
    }

    private static final class Instance extends AbstractSoundInstance {
        Instance(SoundEvent event, float volume) {
            super(event, SoundCategory.MASTER, Random.create());
            this.volume = volume;
            this.pitch = 1.0f;
            this.repeat = false;
            this.relative = true;
            this.attenuationType = SoundInstance.AttenuationType.NONE;
        }
    }
}
