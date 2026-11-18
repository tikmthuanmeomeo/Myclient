package abcxyz.core;

public abstract class Setting<T> {
    public final String name;
    /** Text shown in the GUI (the name is what is saved in the config). */
    public String label;
    /** When set, this setting is shown inside the parent toggle and only while it is opened. */
    public BoolSetting parent;
    /** Hidden settings are saved in the config but not shown in the GUI. */
    public boolean hidden;
    protected T value;

    protected Setting(String name, T value) {
        this.name = name;
        this.label = name;
        this.value = value;
    }

    public T get() {
        return value;
    }

    public abstract String save();

    public abstract void load(String text);
}
