package abcxyz.core;

public class ModeSetting extends Setting<String> {
    public final String[] modes;

    public ModeSetting(String name, String value, String... modes) {
        super(name, value);
        this.modes = modes;
    }

    public boolean is(String mode) {
        return value.equals(mode);
    }

    public void cycle() {
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(value)) {
                value = modes[(i + 1) % modes.length];
                return;
            }
        }
        value = modes[0];
    }

    @Override
    public String save() {
        return value;
    }

    @Override
    public void load(String text) {
        for (String m : modes) {
            if (m.equals(text)) {
                value = m;
                return;
            }
        }
    }
}
