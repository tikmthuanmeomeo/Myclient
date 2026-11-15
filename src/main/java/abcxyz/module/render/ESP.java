package abcxyz.module.render;

import abcxyz.core.BoolSetting;
import abcxyz.core.Category;
import abcxyz.core.Module;
import abcxyz.core.NumSetting;
import abcxyz.render.Render3D;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Draws a box around entities, visible through walls. */
public class ESP extends Module {
    private final NumSetting range = add(new NumSetting("Range", 64, 8, 256, 8));
    private final BoolSetting players = add(new BoolSetting("Players", true));
    private final BoolSetting mobs = add(new BoolSetting("Mobs", false));

    public ESP() {
        super("ESP", "Boxes around entities through walls", Category.Render);
    }

    @Override
    public void onRender3D(WorldRenderContext context) {
        float delta = context.tickCounter().getTickDelta(true);
        Matrix4f m = Render3D.push(context);
        Render3D.begin();
        BufferBuilder b = Render3D.lines();
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || !(e instanceof LivingEntity) || !e.isAlive()) continue;
            boolean isPlayer = e instanceof PlayerEntity;
            if (isPlayer ? !players.on() : !mobs.on()) continue;
            if (mc.player.distanceTo(e) > range.d()) continue;
            Vec3d lerped = e.getLerpedPos(delta);
            Box box = e.getBoundingBox().offset(lerped.x - e.getX(), lerped.y - e.getY(), lerped.z - e.getZ());
            Render3D.box(b, m, box, isPlayer ? 0xFF55FFFF : 0xFFFFAA00);
        }
        Render3D.flush(b);
        Render3D.end();
        Render3D.pop(context);
    }
}
