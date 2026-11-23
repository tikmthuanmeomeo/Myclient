package abcxyz.compat;

/** The colour option of a ported module (a fixed colour, not shown in the GUI). */
public class SettingColor extends Color {
    public SettingColor(int r, int g, int b) {
        super(r, g, b);
    }

    public SettingColor(int r, int g, int b, int a) {
        super(r, g, b, a);
    }
}
