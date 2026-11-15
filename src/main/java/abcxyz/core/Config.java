package abcxyz.core;

import abcxyz.AbcxyzClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Saves module states, keybinds and settings to config/abcxyz.properties. */
public final class Config {
    private Config() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("abcxyz.properties");
    }

    /** @return false when there was no config file yet (first start). */
    public static boolean load(ModuleManager manager) {
        Path path = file();
        if (!Files.exists(path)) return false;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
        } catch (IOException e) {
            return false;
        }
        for (Module m : manager.modules) {
            String enabled = p.getProperty(m.name + ".enabled");
            if (enabled != null) m.setEnabled(Boolean.parseBoolean(enabled), false);
            String key = p.getProperty(m.name + ".key");
            if (key != null) {
                try {
                    m.key = Integer.parseInt(key);
                } catch (NumberFormatException ignored) {
                }
            }
            for (Setting<?> s : m.settings) {
                String v = p.getProperty(m.name + "." + s.name);
                if (v != null) s.load(v);
            }
        }
        return true;
    }

    public static void save() {
        ModuleManager manager = AbcxyzClient.modules;
        if (manager == null) return;
        Properties p = new Properties();
        for (Module m : manager.modules) {
            p.setProperty(m.name + ".enabled", String.valueOf(m.isEnabled()));
            p.setProperty(m.name + ".key", String.valueOf(m.key));
            for (Setting<?> s : m.settings) {
                p.setProperty(m.name + "." + s.name, s.save());
            }
        }
        try (OutputStream out = Files.newOutputStream(file())) {
            p.store(out, "abcxyz");
        } catch (IOException ignored) {
        }
    }
}
