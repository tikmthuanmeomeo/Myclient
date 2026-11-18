package abcxyz.module.render;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;

/**
 * Moves, turns and scales the hand/item you see in first person (like Meteor's HandView), and sets how long a swing
 * takes. SwingSpeed is the length of a swing in ticks: 6 is vanilla (and Meteor's default), higher is slower.
 * The swing is also what you see while mining, so a high value makes the arm move slowly while you dig.
 * The transform is applied by HeldItemRendererMixin, the swing length by LivingEntityMixin.
 */
public class HandView extends Module {
    private final NumSetting swingSpeed = add(new NumSetting("SwingSpeed", 12, 1, 60, 1));

    private final NumSetting[] main = group("Main");
    private final NumSetting[] off = group("Off");
    private final BoolSetting mainHand = add(new BoolSetting("MainHand", true)).with(main);
    private final BoolSetting offHand = add(new BoolSetting("OffHand", true)).with(off);

    public HandView() {
        super("HandView", "Move, turn and scale your hands, and set the swing speed", Category.Render);
        swingSpeed.label = "Swing speed";
        mainHand.label = "Main hand";
        offHand.label = "Off hand";
    }

    /** position x/y/z, rotation x/y/z, scale x/y/z */
    private NumSetting[] group(String prefix) {
        String[] axes = {"X", "Y", "Z"};
        NumSetting[] list = new NumSetting[9];
        for (int i = 0; i < 3; i++) {
            list[i] = num(prefix + "Pos" + axes[i], "Pos " + axes[i], 0.0, -2.0, 2.0, 0.05);
            list[3 + i] = num(prefix + "Rot" + axes[i], "Rot " + axes[i], 0.0, -180.0, 180.0, 1.0);
            list[6 + i] = num(prefix + "Scale" + axes[i], "Scale " + axes[i], 1.0, 0.1, 3.0, 0.05);
        }
        return list;
    }

    private NumSetting num(String name, String label, double def, double min, double max, double step) {
        NumSetting n = add(new NumSetting(name, def, min, max, step));
        n.label = label;
        return n;
    }

    @Override
    public String info() {
        return String.valueOf(swingSpeed.i());
    }

    /** Length of a swing in ticks (vanilla 6). */
    public int swingDuration() {
        return swingSpeed.i();
    }

    /** Called at the start of rendering the item/hand in first person. */
    public void transform(Hand hand, MatrixStack matrices) {
        NumSetting[] s;
        if (hand == Hand.MAIN_HAND) {
            if (!mainHand.on()) return;
            s = main;
        } else {
            if (!offHand.on()) return;
            s = off;
        }
        matrices.translate(s[0].d(), s[1].d(), s[2].d());
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) s[3].d()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) s[4].d()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) s[5].d()));
        matrices.scale((float) s[6].d(), (float) s[7].d(), (float) s[8].d());
    }
}
