package abcxyz.core;

import java.util.ArrayList;
import java.util.List;

public class BoolSetting extends Setting<Boolean> {
    /** Settings shown under this toggle after a right click (they are still saved like any other setting). */
    public final List<Setting<?>> children = new ArrayList<>();
    public boolean open = false;

    public BoolSetting(String name, boolean value) {
        super(name, value);
    }

    public BoolSetting with(Setting<?>... settings) {
        for (Setting<?> s : settings) {
            children.add(s);
            s.parent = this;
        }
        return this;
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
