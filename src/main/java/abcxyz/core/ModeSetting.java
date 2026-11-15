package abcxyz.core;

public class ModeSetting extends Setting<String> {
    public String[] modes;
    private boolean dynamic = false;

    public ModeSetting(String name, String value, String... modes) {
        super(name, value);
        this.modes = modes;
    }

    /** A mode list that is filled at runtime (for example file names). Any saved value is accepted on load. */
    public ModeSetting dynamic() {
        dynamic = true;
        return this;
    }

    public void setModes(String... newModes) {
        modes = newModes;
        for (String m : modes) {
            if (m.equals(value)) return;
        }
        if (modes.length > 0) value = modes[0];
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
        if (modes.length > 0) value = modes[0];
    }

    @Override
    public String save() {
        return value;
    }

    @Override
    public void load(String text) {
        if (dynamic) {
            value = text;
            return;
        }
        for (String m : modes) {
            if (m.equals(text)) {
                value = m;
                return;
            }
        }
    }
}
