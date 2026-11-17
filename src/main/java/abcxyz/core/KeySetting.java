package abcxyz.core;

/** A keyboard key (GLFW key code, -1 = none). Click it in the GUI and press a key to change it. */
public class KeySetting extends Setting<Integer> {
    public KeySetting(String name, int key) {
        super(name, key);
    }

    public int key() {
        return value;
    }

    public void set(int key) {
        value = key;
    }

    @Override
    public String save() {
        return String.valueOf(value);
    }

    @Override
    public void load(String text) {
        try {
            value = Integer.parseInt(text.trim());
        } catch (NumberFormatException ignored) {
        }
    }
}
