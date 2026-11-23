package abcxyz.compat;

/** A plain RGBA colour (0 to 255 per channel). Small stand-in for the colour class of the code ported from ArrowAddon. */
public class Color {
    public int r;
    public int g;
    public int b;
    public int a;

    public Color(int r, int g, int b) {
        this(r, g, b, 255);
    }

    public Color(int r, int g, int b, int a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    public int argb() {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
