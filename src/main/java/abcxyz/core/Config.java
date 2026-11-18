package abcxyz.core;

import abcxyz.AbcxyzClient;
import abcxyz.gui.ClickGuiScreen;
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

    /** Reads a saved value of a module; HudEdit (the old ModuleList) also reads what was saved under the old name. */
    private static String prop(Properties p, Module m, String suffix) {
        String v = p.getProperty(m.name + "." + suffix);
        if (v == null && m.name.equals("HudEdit")) v = p.getProperty("ModuleList." + suffix);
        return v;
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
            String enabled = prop(p, m, "enabled");
            if (enabled != null) m.setEnabled(Boolean.parseBoolean(enabled), false);
            String key = prop(p, m, "key");
            if (key != null) {
                try {
                    m.key = Integer.parseInt(key);
                } catch (NumberFormatException ignored) {
                }
            }
            for (Setting<?> s : m.settings) {
                String v = prop(p, m, s.name);
                if (v != null) s.load(v);
            }
        }
        for (Category c : Category.values()) {
            String x = p.getProperty("gui." + c.name() + ".x");
            String y = p.getProperty("gui." + c.name() + ".y");
            if (x == null || y == null) continue;
            try {
                ClickGuiScreen.POSITIONS.put(c, new int[]{Integer.parseInt(x), Integer.parseInt(y)});
            } catch (NumberFormatException ignored) {
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
        for (java.util.Map.Entry<Category, int[]> e : ClickGuiScreen.POSITIONS.entrySet()) {
            p.setProperty("gui." + e.getKey().name() + ".x", String.valueOf(e.getValue()[0]));
            p.setProperty("gui." + e.getKey().name() + ".y", String.valueOf(e.getValue()[1]));
        }
        try (OutputStream out = Files.newOutputStream(file())) {
            p.store(out, "abcxyz");
        } catch (IOException ignored) {
        }
    }
}
