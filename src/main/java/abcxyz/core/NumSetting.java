package abcxyz.core;

public class NumSetting extends Setting<Double> {
    public final double min;
    public final double max;
    public final double step;

    public NumSetting(String name, double value, double min, double max, double step) {
        super(name, value);
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public double d() {
        return value;
    }

    public int i() {
        return (int) Math.round(value);
    }

    public void set(double v) {
        v = Math.max(min, Math.min(max, v));
        if (step > 0) v = Math.round(v / step) * step;
        value = Math.max(min, Math.min(max, v));
    }

    @Override
    public String save() {
        return String.valueOf(value);
    }

    @Override
    public void load(String text) {
        try {
            set(Double.parseDouble(text));
        } catch (NumberFormatException ignored) {
        }
    }
}
