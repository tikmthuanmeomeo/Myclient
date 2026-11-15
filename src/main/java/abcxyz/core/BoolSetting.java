package abcxyz.core;

public class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String name, boolean value) {
        super(name, value);
    }

    public boolean on() {
        return value;
    }

    public void set(boolean v) {
        value = v;
    }

    public void toggle() {
        value = !value;
    }

    @Override
    public String save() {
        return String.valueOf(value);
    }

    @Override
    public void load(String text) {
        value = Boolean.parseBoolean(text);
    }
}
