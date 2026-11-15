package abcxyz.core;

public abstract class Setting<T> {
    public final String name;
    protected T value;

    protected Setting(String name, T value) {
        this.name = name;
        this.value = value;
    }

    public T get() {
        return value;
    }

    public abstract String save();

    public abstract void load(String text);
}
