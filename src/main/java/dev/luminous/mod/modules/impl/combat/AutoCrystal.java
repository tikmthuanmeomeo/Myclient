package dev.luminous.mod.modules.impl.combat;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.*;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.entity.PlayerEntityPredict;
import dev.luminous.api.utils.math.*;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.render.JelloUtil;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.asm.accessors.IEntity;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.ClickGui;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.impl.movement.ElytraFly;
import dev.luminous.mod.modules.impl.movement.Velocity;
import dev.luminous.mod.modules.impl.player.PacketMine;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import dev.luminous.mod.modules.settings.enums.Timing;
import dev.luminous.mod.modules.settings.impl.*;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.text.DecimalFormat;
import java.util.ArrayList;

public class AutoCrystal extends Module {
    public static AutoCrystal INSTANCE;
    public BlockPos crystalPos;
    public final Timer lastBreakTimer = new Timer();
    private final EnumSetting<Page> page = add(new EnumSetting<>("Page", Page.General));
    final Animation animation = new Animation();
    final DecimalFormat df = new DecimalFormat("0.0");
    private final Timer baseTimer = new Timer();
    private final Timer placeTimer = new Timer();
    private final Timer noPosTimer = new Timer();
    private final Timer switchTimer = new Timer();
    private final Timer calcDelay = new Timer();
    //Check
    private final BindSetting pause = add(new BindSetting("Pause", -1, () -> page.is(Page.Check)));
    private final BooleanSetting preferAnchor = add(new BooleanSetting("PreferAnchor", true, () -> page.getValue() == Page.Check));
    private final BooleanSetting breakOnlyHasCrystal = add(new BooleanSetting("OnlyHold", true, () -> page.getValue() == Page.Check));
    private final BooleanSetting eatingPause = add(new BooleanSetting("EatingPause", true, () -> page.getValue() == Page.Check));
    private final SliderSetting switchCooldown = add(new SliderSetting("SwitchPause", 100, 0, 1000, () -> page.getValue() == Page.Check).setSuffix("ms"));
    private final SliderSetting targetRange = add(new SliderSetting("TargetRange", 12.0, 0.0, 20.0, () -> page.getValue() == Page.Check).setSuffix("m"));
    private final SliderSetting updateDelay = add(new SliderSetting("UpdateDelay", 50, 0, 1000, () -> page.getValue() == Page.Check).setSuffix("ms"));
    //Rotate
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true, () -> page.getValue() == Page.Rotation).setParent());
    private final BooleanSetting onPlace = add(new BooleanSetting("OnPlace", false, () -> rotate.isOpen() && page.getValue() == Page.Rotation));
    private final BooleanSetting onBreak = add(new BooleanSetting("OnBreak", false, () -> rotate.isOpen() && page.getValue() == Page.Rotation));
    private final BooleanSetting yawStep = add(new BooleanSetting("YawStep", false, () -> rotate.isOpen() && page.getValue() == Page.Rotation).setParent());
    private final BooleanSetting whenElytra = add(new BooleanSetting("FallFlying", true, () -> rotate.isOpen() && page.getValue() == Page.Rotation && yawStep.isOpen()));
    private final SliderSetting steps = add(new SliderSetting("Steps", 0.05, 0, 1, 0.01, () -> rotate.isOpen() && yawStep.isOpen() && page.getValue() == Page.Rotation));
    private final BooleanSetting checkFov = add(new BooleanSetting("OnlyLooking", true, () -> rotate.isOpen() && yawStep.isOpen() && page.getValue() == Page.Rotation));
    private final SliderSetting fov = add(new SliderSetting("Fov", 20, 0, 360, 0.1, () -> rotate.isOpen() && yawStep.isOpen() && checkFov.getValue() && page.getValue() == Page.Rotation));
    private final SliderSetting priority = add(new SliderSetting("Priority", 10, 0, 100, () -> rotate.isOpen() && yawStep.isOpen() && page.getValue() == Page.Rotation));
    //Place
    private final SliderSetting minDamage = add(new SliderSetting("Min", 5.0, 0.0, 36.0, () -> page.getValue() == Page.General).setSuffix("dmg"));
    private final SliderSetting maxSelf = add(new SliderSetting("Max", 12.0, 0.0, 36.0, () -> page.getValue() == Page.General).setSuffix("dmg"));
    private final SliderSetting reserve = add(new SliderSetting("Reserve", 2.0, 0.0, 10.0, () -> page.getValue() == Page.General).setSuffix("hp"));
    private final BooleanSetting balance = add(new BooleanSetting("Balance", true, () -> page.getValue() == Page.General).setParent());
    private final SliderSetting balanceOffset = add(new SliderSetting("BalanceOffset", 0, -20, 20, .1, () -> page.getValue() == Page.General && balance.isOpen()).setSuffix("hp"));
    private final BooleanSetting place = add(new BooleanSetting("Place", true, () -> page.getValue() == Page.General).setParent());
    public final SliderSetting placeRange = add(new SliderSetting("PlaceRange", 5.0, 0.0, 6, 0.01, () -> page.getValue() == Page.General && place.isOpen()).setSuffix("m"));
    private final SliderSetting placeDelay = add(new SliderSetting("PlaceDelay", 300, 0, 1000, () -> page.getValue() == Page.General && place.isOpen()).setSuffix("ms"));
    private final EnumSetting<SwapMode> autoSwap = add(new EnumSetting<>("AutoSwap", SwapMode.None, () -> page.getValue() == Page.General && place.isOpen()));
    private final BooleanSetting afterBreak = add(new BooleanSetting("AfterBreak", true, () -> page.getValue() == Page.General && place.isOpen()));
    private final BooleanSetting forcePlace = add(new BooleanSetting("ForcePlace", false, () -> page.getValue() == Page.General && place.isOpen()));
    private final BooleanSetting breakSetting = add(new BooleanSetting("Break", true, () -> page.getValue() == Page.General).setParent());
    public final SliderSetting breakRange = add(new SliderSetting("BreakRange", 4.0, 0.0, 6, 0.01, () -> page.getValue() == Page.General && breakSetting.isOpen()).setSuffix("m"));
    private final SliderSetting breakDelay = add(new SliderSetting("BreakDelay", 300, 0, 1000, () -> page.getValue() == Page.General && breakSetting.isOpen()).setSuffix("ms"));
    private final SliderSetting minAge = add(new SliderSetting("MinAge", 0, 0, 20, () -> page.getValue() == Page.General && breakSetting.isOpen()).setSuffix("tick"));
    private final BooleanSetting breakRemove = add(new BooleanSetting("Remove", false, () -> page.getValue() == Page.General && breakSetting.isOpen()));
    private final BooleanSetting onAdd = add(new BooleanSetting("OnAdd", false, () -> page.getValue() == Page.General && breakSetting.isOpen()));
    private final BooleanSetting resetCD = add(new BooleanSetting("ResetAttack", true, () -> page.getValue() == Page.General && breakSetting.isOpen()));
    private final EnumSetting<Timing> timing =
            add(new EnumSetting<>("Timing", Timing.All, () -> page.getValue() == Page.General));
    private final BooleanSetting interactOnRender = add(new BooleanSetting("InteractOnRender", false, () -> page.getValue() == Page.General));
    private final SliderSetting wallRange = add(new SliderSetting("WallRange", 6.0, 0.0, 6.0, () -> page.getValue() == Page.General).setSuffix("m"));
    private final EnumSetting<SwingSide> swingMode = add(new EnumSetting<>("Swing", SwingSide.All, () -> page.getValue() == Page.General));

    //Render
    private final ColorSetting text = add(new ColorSetting("Text", new Color(-1), () -> page.getValue() == Page.Render).injectBoolean(true));
    private final EnumSetting<TargetESP> mode = add(new EnumSetting<>("TargetESP", TargetESP.Fill, () -> page.getValue() == Page.Render));
    private final SliderSetting animationTime = add(new SliderSetting("AnimationTime", 200, 0, 2000, 1, () -> page.getValue() == Page.Render));
    private final EnumSetting<Easing> ease = add(new EnumSetting<>("Ease", Easing.CubicInOut, () -> page.getValue() == Page.Render));
    private final ColorSetting color = add(new ColorSetting("TargetColor", new Color(255, 255, 255, 50), () -> page.getValue() == Page.Render));
    private final ColorSetting outlineColor = add(new ColorSetting("TargetOutlineColor", new Color(255, 255, 255, 50), () -> page.getValue() == Page.Render));
    private final ColorSetting hitColor = add(new ColorSetting("HitColor", new Color(255, 255, 255, 150), () -> page.getValue() == Page.Render));
    private final ColorSetting hitOutlineColor = add(new ColorSetting("HitOutlineColor", new Color(255, 255, 255, 150), () -> page.getValue() == Page.Render));
    private final BooleanSetting render = add(new BooleanSetting("Render", true, () -> page.getValue() == Page.Render));
    private final BooleanSetting sync = add(new BooleanSetting("Sync", true, () -> page.getValue() == Page.Render && render.getValue()));
    private final BooleanSetting shrink = add(new BooleanSetting("Shrink", true, () -> page.getValue() == Page.Render && render.getValue()));
    private final ColorSetting box = add(new ColorSetting("Box", new Color(255, 255, 255, 255), () -> page.getValue() == Page.Render && render.getValue()).injectBoolean(true));
    private final SliderSetting lineWidth = add(new SliderSetting("LineWidth", 1.5d, 0.01d, 3d, 0.01, () -> page.getValue() == Page.Render && render.getValue()));
    private final ColorSetting fill = add(new ColorSetting("Fill", new Color(255, 255, 255, 100), () -> page.getValue() == Page.Render && render.getValue()).injectBoolean(true));
    private final SliderSetting sliderSpeed = add(new SliderSetting("SliderSpeed", 0.2, 0.01, 1, 0.01, () -> page.getValue() == Page.Render && render.getValue()));
    private final SliderSetting startFadeTime = add(new SliderSetting("StartFade", 0.3d, 0d, 2d, 0.01, () -> page.getValue() == Page.Render && render.getValue()).setSuffix("s"));
    private final SliderSetting fadeSpeed = add(new SliderSetting("FadeSpeed", 0.2d, 0.01d, 1d, 0.01, () -> page.getValue() == Page.Render && render.getValue()));
    //Calc
    private final SliderSetting attackVecStep =
            add(new SliderSetting("AttackVecStep", 0.1, 0.01, 1, 0.01, () -> page.getValue() == Page.Calc));
    private final BooleanSetting thread = add(new BooleanSetting("Thread", false, () -> page.getValue() == Page.Calc));
    private final BooleanSetting doCrystal = add(new BooleanSetting("InteractInCalc", false, () -> page.getValue() == Page.Calc));
    private final SliderSetting selfPredict = add(new SliderSetting("SelfPredict", 0, 0, 20, () -> page.getValue() == Page.Calc).setSuffix("ticks"));
    private final SliderSetting predictTicks = add(new SliderSetting("Predict", 4, 0, 20, () -> page.getValue() == Page.Calc).setSuffix("ticks"));
    private final SliderSetting simulation =
            add(new SliderSetting("Simulation", 5, 0, 20, 1, () -> page.getValue() == Page.Calc));
    private final SliderSetting maxMotionY =
            add(new SliderSetting("MaxMotionY", 0.34, 0, 2, 0.01, () -> page.getValue() == Page.Calc));
    private final BooleanSetting step =
            add(new BooleanSetting("Step", false, () -> page.getValue() == Page.Calc));
    private final BooleanSetting doubleStep =
            add(new BooleanSetting("DoubleStep", false, () -> page.getValue() == Page.Calc));
    private final BooleanSetting jump =
            add(new BooleanSetting("Jump", false, () -> page.getValue() == Page.Calc));
    private final BooleanSetting inBlockPause =
            add(new BooleanSetting("InBlockPause", true, () -> page.getValue() == Page.Calc));
    private final BooleanSetting terrainIgnore = add(new BooleanSetting("TerrainIgnore", true, () -> page.getValue() == Page.Calc));
    //Base
    private final BooleanSetting basePlace = add(new BooleanSetting("BasePlace", true, () -> page.getValue() == Page.Base));
    private final SliderSetting baseMin = add(new SliderSetting("BaseMin", 6, 0, 36, 0.1, () -> page.getValue() == Page.Base).setSuffix("hp"));
    private final SliderSetting baseMax = add(new SliderSetting("BaseMax", 12, 0, 36, 0.1, () -> page.getValue() == Page.Base).setSuffix("hp"));
    private final SliderSetting overrideMax = add(new SliderSetting("MaxOverride", 8, 0, 36, 0.1, () -> page.getValue() == Page.Base).setSuffix("hp"));
    private final BooleanSetting baseBalance = add(new BooleanSetting("BaseBalance", true, () -> page.getValue() == Page.Base));
    private final BooleanSetting onlyBelow = add(new BooleanSetting("OnlyBelow", true, () -> page.getValue() == Page.Base));
    private final BooleanSetting inventory = add(new BooleanSetting("InventorySwap", true, () -> page.getValue() == Page.Base));
    private final BooleanSetting detectMining = add(new BooleanSetting("DetectMining", true, () -> page.getValue() == Page.Base));
    private final SliderSetting delay = add(new SliderSetting("Delay", 3000, 0, 10000, () -> page.getValue() == Page.Base).setSuffix("ms"));
    //Misc
    private final BooleanSetting ignoreMine = add(new BooleanSetting("IgnoreMine", true, () -> page.getValue() == Page.Misc).setParent());
    private final SliderSetting constantProgress = add(new SliderSetting("Progress", 90.0, 0.0, 100.0, () -> page.getValue() == Page.Misc && ignoreMine.isOpen()).setSuffix("%"));
    private final BooleanSetting antiSurround = add(new BooleanSetting("AntiSurround", false, () -> page.getValue() == Page.Misc).setParent());
    private final SliderSetting miningProgress = add(new SliderSetting("MiningProgress", 90.0, 0.0, 100.0, () -> page.getValue() == Page.Misc && antiSurround.isOpen()).setSuffix("%"));
    private final SliderSetting antiSurroundMax = add(new SliderSetting("WhenLower", 5.0, 0.0, 36.0, () -> page.getValue() == Page.Misc && antiSurround.isOpen()).setSuffix("dmg"));
    private final BooleanSetting slowPlace = add(new BooleanSetting("Timeout", true, () -> page.getValue() == Page.Misc).setParent());
    private final SliderSetting slowDelay = add(new SliderSetting("TimeoutDelay", 600, 0, 2000, () -> page.getValue() == Page.Misc && slowPlace.isOpen()).setSuffix("ms"));
    private final SliderSetting slowMinDamage = add(new SliderSetting("TimeoutMin", 1.5, 0.0, 36.0, () -> page.getValue() == Page.Misc && slowPlace.isOpen()).setSuffix("dmg"));
    private final BooleanSetting lethalOverride = add(new BooleanSetting("LethalOverride", true, () -> page.getValue() == Page.Misc).setParent());
    private final SliderSetting forceMaxHealth = add(new SliderSetting("LowerThan", 7, 0, 36, .1, () -> page.getValue() == Page.Misc && lethalOverride.isOpen()).setSuffix("health"));
    private final SliderSetting forceMin = add(new SliderSetting("ForceMin", 1.5, 0.0, 36.0, () -> page.getValue() == Page.Misc && lethalOverride.isOpen()).setSuffix("dmg"));
    private final BooleanSetting armorBreaker = add(new BooleanSetting("ArmorBreaker", true, () -> page.getValue() == Page.Misc).setParent());
    private final SliderSetting maxDurable = add(new SliderSetting("MaxDurable", 8, 0, 100, () -> page.getValue() == Page.Misc && armorBreaker.isOpen()).setSuffix("%"));
    private final SliderSetting armorBreakerDamage = add(new SliderSetting("BreakerMin", 3.0, 0.0, 36.0, () -> page.getValue() == Page.Misc && armorBreaker.isOpen()).setSuffix("dmg"));
    private final BooleanSetting forceWeb = add(new BooleanSetting("WebReset", true, () -> page.getValue() == Page.Misc).setParent());
    public final BooleanSetting airPlace = add(new BooleanSetting("AirPlace", false, () -> page.getValue() == Page.Misc && forceWeb.isOpen()));
    public final BooleanSetting replace = add(new BooleanSetting("Replace", false, () -> page.getValue() == Page.Misc && forceWeb.isOpen()));
    private final SliderSetting hurtTime = add(new SliderSetting("HurtTime", 10, 0, 10, 1, () -> page.getValue() == Page.Misc));
    private final SliderSetting waitHurt = add(new SliderSetting("WaitHurt", 10, 0, 10, 1, () -> page.getValue() == Page.Misc));
    private final SliderSetting syncTimeout = add(new SliderSetting("WaitTimeOut", 500, 0, 2000, 10, () -> page.getValue() == Page.Misc));
    private final Timer syncTimer = new Timer();
    public PlayerEntity displayTarget;
    public float breakDamage, tempDamage, lastDamage;
    public Vec3d directionVec = null;
    double currentFade = 0;
    private EndCrystalEntity tempBreakCrystal;
    private EndCrystalEntity breakCrystal;
    private BlockPos tempPos, syncPos;
    private Vec3d placeVec3d, curVec3d;

    public AutoCrystal() {
        super("AutoCrystal", Category.Combat);
        setChinese("自动水晶");
        INSTANCE = this;
        Alien.EVENT_BUS.subscribe(new CrystalRender());
    }

    @Override
    public String getInfo() {
        if (displayTarget != null && lastDamage > 0) {
            //return displayTarget.getName().getString() + ", " + new DecimalFormat("0.0").format(lastDamage);
            return df.format(lastDamage);
        }
        return null;
    }

    @Override
    public void onDisable() {
        crystalPos = null;
        tempPos = null;
    }

    @Override
    public void onEnable() {
        crystalPos = null;
        tempPos = null;
        tempBreakCrystal = null;
        displayTarget = null;
        syncTimer.reset();
        lastBreakTimer.reset();
    }

    public void onThread() {
        if (isOff()) return;
        if (thread.getValue()) {
            updateCrystalPos();
        }
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck()) return;
        if (timing.is(Timing.Pre) && event.isPost() || timing.is(Timing.Post) && event.isPre()) return;
        if (!thread.getValue()) {
            updateCrystalPos();
        }
        if (shouldReturn()) {
            return;
        }
        doInteract();
        BlockPos basePos = this.basePos;
        if (basePlace.getValue() && basePos != null && BlockUtil.canPlace(basePos)) {
            doPlace(basePos);
        }
    }

    @Override
    public void onRender3D(MatrixStack matrixStack) {
        if (interactOnRender.getValue()) {
            if (!shouldReturn()) {
                doInteract();
                BlockPos basePos = this.basePos;
                if (basePlace.getValue() && basePos != null && BlockUtil.canPlace(basePos)) {
                    doPlace(basePos);
                }
            }
        }
        if (displayTarget != null && !noPosTimer.passed(500)) {
            doRender(matrixStack, mc.getRenderTickCounter().getTickDelta(true), displayTarget, mode.getValue());
        }
    }

    public void doRender(MatrixStack stack, float partialTicks, Entity entity, TargetESP mode) {
        switch (mode) {
            case Box ->
                    Render3DUtil.draw3DBox(stack, ((IEntity) entity).getDimensions().getBoxAt(new Vec3d(MathUtil.interpolate(entity.lastRenderX, entity.getX(), partialTicks), MathUtil.interpolate(entity.lastRenderY, entity.getY(), partialTicks), MathUtil.interpolate(entity.lastRenderZ, entity.getZ(), partialTicks))).expand(0, 0.1, 0), ColorUtil.fadeColor(color.getValue(), hitColor.getValue(), animation.get(0, animationTime.getValueInt(), ease.getValue())), ColorUtil.fadeColor(outlineColor.getValue(), hitOutlineColor.getValue(), animation.get(0, animationTime.getValueInt(), ease.getValue())), true, true);
            case Fill ->
                    Render3DUtil.draw3DBox(stack, ((IEntity) entity).getDimensions().getBoxAt(new Vec3d(MathUtil.interpolate(entity.lastRenderX, entity.getX(), partialTicks), MathUtil.interpolate(entity.lastRenderY, entity.getY(), partialTicks), MathUtil.interpolate(entity.lastRenderZ, entity.getZ(), partialTicks))).expand(0, 0.1, 0), ColorUtil.fadeColor(color.getValue(), hitColor.getValue(), animation.get(0, animationTime.getValueInt(), ease.getValue())), ColorUtil.fadeColor(outlineColor.getValue(), hitOutlineColor.getValue(), animation.get(0, animationTime.getValueInt(), ease.getValue())), false, true);
            case Jello -> JelloUtil.drawJello(stack, entity, color.getValue());
            case ThunderHack -> Render3DUtil.drawTargetEsp(stack, displayTarget, color.getValue());
        }
    }

    private void doInteract() {
        BlockPos crystalPos = this.crystalPos;
        if (crystalPos != null) {
            doCrystal(crystalPos);
        }
        if (breakCrystal != null) {
            doBreak(breakCrystal);
            breakCrystal = null;
        }
    }

    @EventListener
    public void onRotate(RotationEvent event) {
        if (rotate.getValue() && shouldYawStep() && directionVec != null && displayTarget != null && !noPosTimer.passed(1000) && !shouldReturn()) {
            event.setTarget(directionVec, steps.getValueFloat(), priority.getValueFloat());
        }
    }

    int lastSlot;

    @EventListener(priority = -199)
    public void onPacketSend(PacketEvent.Send event) {
        if (event.isCancelled()) return;
        if (event.getPacket() instanceof UpdateSelectedSlotC2SPacket packet) {
            if (lastSlot != packet.getSelectedSlot()) {
                lastSlot = packet.getSelectedSlot();
                switchTimer.reset();
            }
        }
    }

    public Vec3d getAttackVec(Vec3d feetPos) {
        return MathUtil.getPointToBoxFromBottom(mc.player.getEyePos(), feetPos, breakRange.getValue(), 2, attackVecStep.getValue());
    }

    private void updateCrystalPos() {
        if (calcDelay.passedMs(updateDelay.getValue())) {
            calcDelay.reset();
            calcCrystalPos();
            CombatUtil.modifyPos = null;
            CombatUtil.modifyBlockState = null;
            basePos = tempBasePos;
            lastDamage = tempDamage;
            breakCrystal = tempBreakCrystal;
            crystalPos = tempPos;
        }
    }

    private boolean shouldReturn() {
        if (eatingPause.getValue() && mc.player.isUsingItem() || Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) {
            lastBreakTimer.reset();
            return true;
        } else if (preferAnchor.getValue() && AutoAnchor.INSTANCE.currentPos != null) {
            lastBreakTimer.reset();
            return true;
        } else if (pause.isPressed()) {
            lastBreakTimer.reset();
            return true;
        }
        return false;
    }

    BlockPos tempBasePos;
    BlockPos basePos;

    private void calcCrystalPos() {
        if (nullCheck()) return;
        if (breakOnlyHasCrystal.getValue() && !mc.player.getMainHandStack().getItem().equals(Items.END_CRYSTAL) && !mc.player.getOffHandStack().getItem().equals(Items.END_CRYSTAL) && !hasCrystal()) {
            tempPos = null;
            tempBreakCrystal = null;
            lastBreakTimer.reset();
            return;
        }
        boolean shouldReturn = shouldReturn();
        boolean needBasePlace = basePlace.getValue() && baseTimer.passedMs(delay.getValue()) && getBlock() != -1;
        tempBreakCrystal = null;
        breakDamage = 0;
        tempPos = null;
        tempDamage = 0f;
        tempBasePos = null;
        float baseDamage = 0;
        ArrayList<PlayerEntityPredict> list = new ArrayList<>();
        for (PlayerEntity target : CombatUtil.getEnemies(targetRange.getValueFloat())) {
            if (target.hurtTime <= hurtTime.getValueInt()) {
                list.add(new PlayerEntityPredict(target, maxMotionY.getValue(), predictTicks.getValueInt(), simulation.getValueInt(), step.getValue(), doubleStep.getValue(), jump.getValue(), inBlockPause.getValue()));
            }
        }
        PlayerEntityPredict self = new PlayerEntityPredict(mc.player, maxMotionY.getValue(), selfPredict.getValueInt(), simulation.getValueInt(), step.getValue(), doubleStep.getValue(), jump.getValue(), inBlockPause.getValue());
        if (list.isEmpty()) {
            lastBreakTimer.reset();
        } else {
            for (Entity entity : Alien.THREAD.getEntities()) {
                if (entity instanceof EndCrystalEntity crystal) {
                    if (entity.age < minAge.getValueInt()) continue;
                    Vec3d attackVec = getAttackVec(crystal.getPos());
                    if (attackVec == null) {
                        continue;
                    }
                    if (!mc.player.canSee(crystal) && mc.player.getEyePos().distanceTo(attackVec) > wallRange.getValue())
                        continue;
                    float selfDamage = calculateDamage(crystal.getPos(), self.player, self.predict);
                    for (PlayerEntityPredict pap : list) {
                        float damage = calculateDamage(crystal.getPos(), pap.player, pap.predict);
                        if (damage > breakDamage) {
                            if (selfDamage > maxSelf.getValue()) continue;
                            if (reserve.getValue() > 0 && selfDamage > mc.player.getHealth() + mc.player.getAbsorptionAmount() - reserve.getValue())
                                continue;
                            if (damage < EntityUtil.getHealth(pap.player)) {
                                if (damage < getDamage(pap.player)) continue;
                                if (balance.getValue()) {
                                    if (getDamage(pap.player) == forceMin.getValue()) {
                                        if (damage < selfDamage - 2.5) {
                                            continue;
                                        }
                                    } else {
                                        if (damage < selfDamage + balanceOffset.getValue()) {
                                            continue;
                                        }
                                    }
                                }
                            }
                            breakDamage = damage;
                            tempBreakCrystal = crystal;
                            displayTarget = pap.player;
                            //tempDamage = damage;
                        }
                    }
                }
            }
            if (doCrystal.getValue() && tempBreakCrystal != null && !shouldReturn) {
                doBreak(tempBreakCrystal);
                tempBreakCrystal = null;
            }

            for (BlockPos pos : BlockUtil.getSphere((float) breakRange.getValue() + 1.5f)) {
                boolean base = false;
                CombatUtil.modifyPos = null;
                CombatUtil.modifyBlockState = null;
                if (needBasePlace && BlockUtil.canPlace(pos.down())) {
                    CombatUtil.modifyPos = pos.down();
                    CombatUtil.modifyBlockState = Blocks.OBSIDIAN.getDefaultState();
                    base = true;
                }
                if (base && Alien.BREAK.isMining(pos.down()) && detectMining.getValue()) {
                    continue;
                }
                Vec3d attackVec = getAttackVec(pos.toBottomCenterPos());
                if (attackVec == null) {
                    continue;
                }
                if (behindWall(pos, attackVec)) continue;
                if (!canTouch(pos.down())) continue;
                if (!canPlaceCrystal(pos, true, false)) continue;
                float selfDamage = base ? calculateBaseDamage(pos, self.player, self.predict) : calculateDamage(pos, self.player, self.predict);
                for (PlayerEntityPredict pap : list) {
                    if (base && onlyBelow.getValue()) {
                        if (pos.getY() - 0.5 > pap.player.getY()) continue;
                    }
                    float damage = base ? calculateBaseDamage(pos, pap.player, pap.predict) : calculateDamage(pos, pap.player, pap.predict);
                    if (base) {
                        if (tempDamage <= overrideMax.getValue() && (damage > tempDamage && damage > baseDamage)) {
                            if (selfDamage > baseMax.getValue()) continue;
                            if (reserve.getValue() > 0 && selfDamage > mc.player.getHealth() + mc.player.getAbsorptionAmount() - reserve.getValue())
                                continue;
                            if (damage < EntityUtil.getHealth(pap.player)) {
                                if (damage < baseMin.getValue()) continue;
                                if (baseBalance.getValue()) {
                                    if (damage < selfDamage) {
                                        continue;
                                    }
                                }
                            }
                            displayTarget = pap.player;
                            baseDamage = damage;
                            tempBasePos = pos.down();
                            tempPos = null;
                            //CommandManager.sendMessage(damage + " " + pos);
                        }
                    } else {
                        if (damage > tempDamage && (damage >= baseDamage || tempDamage > overrideMax.getValue())) {
                            if (selfDamage > maxSelf.getValue()) continue;
                            if (reserve.getValue() > 0 && selfDamage > mc.player.getHealth() + mc.player.getAbsorptionAmount() - reserve.getValue())
                                continue;
                            if (damage < EntityUtil.getHealth(pap.player)) {
                                if (damage < getDamage(pap.player)) continue;
                                if (balance.getValue()) {
                                    if (getDamage(pap.player) == forceMin.getValue()) {
                                        if (damage < selfDamage - 2.5) {
                                            continue;
                                        }
                                    } else {
                                        if (damage < selfDamage + balanceOffset.getValue()) {
                                            continue;
                                        }
                                    }
                                }
                            }
                            displayTarget = pap.player;
                            tempPos = pos;
                            tempBasePos = null;
                            tempDamage = damage;
                        }
                    }
                }
            }
            CombatUtil.modifyPos = null;
            CombatUtil.modifyBlockState = null;
            if (antiSurround.getValue() && PacketMine.getBreakPos() != null && PacketMine.progress >= miningProgress.getValueFloat() && !BlockUtil.hasEntity(PacketMine.getBreakPos(), false)) {
                if (tempDamage <= antiSurroundMax.getValueFloat()) {
                    for (PlayerEntityPredict pap : list) {
                        BlockPos pos = new BlockPosX(pap.player.getPos().add(0, 0.5, 0));
                        if (BlockUtil.canCollide(pap.player, new Box(pos))) continue;
                        for (Direction i : Direction.values()) {
                            if (i == Direction.DOWN || i == Direction.UP) continue;
                            BlockPos offsetPos = pos.offset(i);
                            if (offsetPos.equals(PacketMine.getBreakPos())) {
                                for (Direction direction : Direction.values()) {
                                    if (direction == Direction.DOWN || direction == Direction.UP) continue;
                                    if (canPlaceCrystal(offsetPos.offset(direction), false, false)) {
                                        float selfDamage = calculateDamage(offsetPos.offset(direction), self.player, self.predict);
                                        if (selfDamage < maxSelf.getValue() && !(reserve.getValue() > 0 && selfDamage > mc.player.getHealth() + mc.player.getAbsorptionAmount() - reserve.getValue())) {
                                            tempPos = offsetPos.offset(direction);
                                            if (doCrystal.getValue() && tempPos != null && !shouldReturn) {
                                                doCrystal(tempPos);
                                            }
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (doCrystal.getValue() && tempPos != null && !shouldReturn) {
            doCrystal(tempPos);
        }
    }

    @EventListener
    private void onEntity(EntitySpawnedEvent event) {
        Entity entity = event.getEntity();
        if (onAdd.getValue() && entity instanceof EndCrystalEntity crystal && crystal.getBlockPos().equals(syncPos)) {
            doBreak(crystal);
        }
    }
    public boolean canPlaceCrystal(BlockPos pos, boolean ignoreCrystal, boolean ignoreItem) {
        BlockPos obsPos = pos.down();
        BlockPos boost = obsPos.up();
        BlockPos boost2 = boost.up();

        return (BlockUtil.getBlock(obsPos) == Blocks.BEDROCK || BlockUtil.getBlock(obsPos) == Blocks.OBSIDIAN)
                && BlockUtil.getClickSideStrict(obsPos) != null
                && noEntityBlockCrystal(boost, ignoreCrystal, ignoreItem)
                && noEntityBlockCrystal(boost2, ignoreCrystal, ignoreItem)
                && (mc.world.isAir(boost) || BlockUtil.hasCrystal(boost) && BlockUtil.getBlock(boost) == Blocks.FIRE)
                && (!ClientSetting.INSTANCE.lowVersion.getValue() || mc.world.isAir(boost2));
    }

    private boolean noEntityBlockCrystal(BlockPos pos, boolean ignoreCrystal, boolean ignoreItem) {
        for (Entity entity : BlockUtil.getEntities(new Box(pos))) {
            if (!entity.isAlive()) {
                continue;
            }
            if (entity instanceof ItemEntity && ignoreItem) {
                continue;
            }
            if (entity instanceof EndCrystalEntity) {
                //if (entity.age < minAge.getValueInt()) return false;
                if (ignoreCrystal &&
                        getAttackVec(entity.getPos()) != null &&
                        (mc.player.canSee(entity) || mc.player.getEyePos().distanceTo(entity.getPos()) <= wallRange.getValue())
                ) {
                    continue;
                }
            }
            return false;
        }
        return true;
    }

    public boolean behindWall(BlockPos pos, Vec3d attackVec) {
        Vec3d crystalEyePos = new Vec3d(pos.getX() + 0.5, pos.getY() + 2 * 0.85, pos.getZ() + 0.5);
        HitResult result = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), crystalEyePos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (result == null || result.getType() == HitResult.Type.MISS) return false;
        return mc.player.getEyePos().distanceTo(attackVec) > wallRange.getValue();
    }

    private boolean canTouch(BlockPos pos) {
        Direction side = BlockUtil.getClickSideStrict(pos);
        return side != null && pos.toCenterPos().add(new Vec3d(side.getVector().getX() * 0.5, side.getVector().getY() * 0.5, side.getVector().getZ() * 0.5)).distanceTo(mc.player.getEyePos()) <= placeRange.getValue();
    }

    private void doCrystal(BlockPos pos) {
        if (canPlaceCrystal(pos, false, false)) {
            doPlace(pos, rotate.getValue() && onPlace.getValue());
        }
        doBreak(pos);
    }

    private void doPlace(BlockPos pos) {
        if (!baseTimer.passed((long) delay.getValue())) return;
        if (detectMining.getValue() && Alien.BREAK.isMining(pos)) return;
        int block = getBlock();
        if (block == -1) return;
        int old = mc.player.getInventory().selectedSlot;
        baseSwap(block);
        BlockUtil.placeBlock(pos, rotate.getValue());
        if (inventory.getValue()) {
            baseSwap(block);
            EntityUtil.syncInventory();
        } else {
            baseSwap(old);
        }
        baseTimer.reset();
    }

    public float calculateDamage(BlockPos pos, PlayerEntity player, PlayerEntity predict) {
        return calculateDamage(new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), player, predict);
    }

    public float calculateDamage(Vec3d pos, PlayerEntity player, PlayerEntity predict) {
        if (ignoreMine.getValue() && PacketMine.getBreakPos() != null) {
            if (mc.player.getEyePos().distanceTo(PacketMine.getBreakPos().toCenterPos()) <= PacketMine.INSTANCE.range.getValue()) {
                if (PacketMine.progress >= constantProgress.getValue() / 100) {
                    CombatUtil.modifyPos = PacketMine.getBreakPos();
                    CombatUtil.modifyBlockState = Blocks.AIR.getDefaultState();
                }
            }
        }
        if (terrainIgnore.getValue()) {
            CombatUtil.terrainIgnore = true;
        }
        float damage = ExplosionUtil.calculateDamage(pos, player, predict, 6);
        CombatUtil.modifyPos = null;
        CombatUtil.terrainIgnore = false;
        return damage;
    }

    public float calculateBaseDamage(BlockPos pos, PlayerEntity player, PlayerEntity predict) {
        if (terrainIgnore.getValue()) {
            CombatUtil.terrainIgnore = true;
        }
        float damage = DamageUtils.overridingExplosionDamage(player, predict, new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), 12f, pos.down(), Blocks.OBSIDIAN.getDefaultState());
        CombatUtil.terrainIgnore = false;
        return damage;
    }

    private double getDamage(PlayerEntity target) {
        if (!PacketMine.INSTANCE.obsidian.isPressed() && slowPlace.getValue() && lastBreakTimer.passed((long) slowDelay.getValue())) {
            return slowMinDamage.getValue();
        }
        if (lethalOverride.getValue() && EntityUtil.getHealth(target) <= forceMaxHealth.getValue() && !PacketMine.INSTANCE.obsidian.isPressed()) {
            return forceMin.getValue();
        }
        if (armorBreaker.getValue()) {
            DefaultedList<ItemStack> armors = target.getInventory().armor;
            for (ItemStack armor : armors) {
                if (armor.isEmpty()) continue;
                if (EntityUtil.getDamagePercent(armor) > maxDurable.getValue()) continue;
                return armorBreakerDamage.getValue();
            }
        }
        return minDamage.getValue();
    }

    private boolean shouldYawStep() {
        if (!whenElytra.getValue() && (mc.player.isFallFlying() || ElytraFly.INSTANCE.isOn() && ElytraFly.INSTANCE.isFallFlying()))
            return false;
        return yawStep.getValue() && !Velocity.INSTANCE.noRotation();
    }

    public boolean hasCrystal() {
        if (autoSwap.getValue() == SwapMode.None) return false;
        return getCrystal() != -1;
    }

    private void doBreak(EndCrystalEntity entity) {
        noPosTimer.reset();
        if (!breakSetting.getValue()) return;
        if (!entity.isAlive()) return;
        if (displayTarget != null && displayTarget.hurtTime > waitHurt.getValueInt() && !syncTimer.passedMs(syncTimeout.getValue())) {
            return;
        }
        lastBreakTimer.reset();
        if (!switchTimer.passed((long) switchCooldown.getValue())) {
            return;
        }
        syncTimer.reset();
        if (entity.age < minAge.getValueInt()) return;
        if (!shouldYawStep() && !CombatUtil.breakTimer.passed((long) breakDelay.getValue())) {
            if (forcePlace.getValue() && crystalPos != null) {
                doPlace(crystalPos, false);
            }
            return;
        }
        if (rotate.getValue() && onBreak.getValue()) {
            Vec3d attackVec = getAttackVec(entity.getPos());
            if (!faceVector(attackVec == null ? entity.getPos() : attackVec)) {
                if (forcePlace.getValue() && crystalPos != null) {
                    doPlace(crystalPos, false);
                }
                return;
            }
        }
        if (shouldYawStep() && !CombatUtil.breakTimer.passed((long) breakDelay.getValue())) {
            if (forcePlace.getValue() && crystalPos != null) {
                doPlace(crystalPos, false);
            }
            return;
        }
        animation.to = 1;
        animation.from = 1;
        CombatUtil.breakTimer.reset();
        syncPos = entity.getBlockPos();
        mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
        if (resetCD.getValue()) {
            mc.player.resetLastAttackedTicks();
        }
        EntityUtil.swingHand(Hand.MAIN_HAND, swingMode.getValue());
        if (breakRemove.getValue()) {
            mc.world.removeEntity(entity.getId(), Entity.RemovalReason.KILLED);
        }
        BlockPos crystalPos = this.crystalPos;
        if (crystalPos != null && displayTarget != null && lastDamage >= getDamage(displayTarget) && afterBreak.getValue()) {
            if (!rotate.getValue() || !shouldYawStep() || !checkFov.getValue() || Alien.ROTATION.inFov(entity.getPos(), fov.getValueFloat())) {
                doPlace(crystalPos, false);
            }
        }
        if (forceWeb.getValue() && AutoWeb.INSTANCE.isOn()) {
            AutoWeb.force = true;
        }
        if (rotate.getValue() && !shouldYawStep()) {
            Alien.ROTATION.snapBack();
        }
    }

    private void doBreak(BlockPos pos) {
        noPosTimer.reset();
        if (!breakSetting.getValue()) return;
        if (displayTarget != null && displayTarget.hurtTime > waitHurt.getValueInt() && !syncTimer.passedMs(syncTimeout.getValue())) {
            return;
        }
        lastBreakTimer.reset();
        if (!switchTimer.passed((long) switchCooldown.getValue())) {
            return;
        }
        syncTimer.reset();
        for (EndCrystalEntity entity : BlockUtil.getEndCrystals(new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1))) {
            if (entity.age < minAge.getValueInt()) continue;
            if (!entity.isAlive()) continue;
            if (!shouldYawStep() && !CombatUtil.breakTimer.passed((long) breakDelay.getValue())) {
                if (forcePlace.getValue() && crystalPos != null) {
                    doPlace(crystalPos, false);
                }
                return;
            }
            if (rotate.getValue() && onBreak.getValue()) {
                Vec3d attackVec = getAttackVec(entity.getPos());
                if (!faceVector(attackVec == null ? entity.getPos() : attackVec)) {
                    if (forcePlace.getValue() && crystalPos != null) {
                        doPlace(crystalPos, false);
                    }
                    return;
                }
            }
            if (shouldYawStep() && !CombatUtil.breakTimer.passed((long) breakDelay.getValue())) {
                if (forcePlace.getValue() && crystalPos != null) {
                    doPlace(crystalPos, false);
                }
                return;
            }
            animation.to = 1;
            animation.from = 1;
            CombatUtil.breakTimer.reset();
            syncPos = pos;
            mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(entity, mc.player.isSneaking()));
            if (resetCD.getValue()) {
                mc.player.resetLastAttackedTicks();
            }
            EntityUtil.swingHand(Hand.MAIN_HAND, swingMode.getValue());
            if (breakRemove.getValue()) {
                mc.world.removeEntity(entity.getId(), Entity.RemovalReason.KILLED);
            }
            BlockPos crystalPos = this.crystalPos;
            if (crystalPos != null && displayTarget != null && lastDamage >= getDamage(displayTarget) && afterBreak.getValue()) {
                if (!rotate.getValue() || !shouldYawStep() || !checkFov.getValue() || Alien.ROTATION.inFov(entity.getPos(), fov.getValueFloat())) {
                    doPlace(crystalPos, false);
                }
            }
            if (forceWeb.getValue() && AutoWeb.INSTANCE.isOn()) {
                AutoWeb.force = true;
            }
            if (rotate.getValue() && !shouldYawStep()) {
                Alien.ROTATION.snapBack();
            }
            return;
        }
        if (forcePlace.getValue() && crystalPos != null) {
            doPlace(crystalPos, false);
        }
    }

    private void doPlace(BlockPos pos, boolean rotate) {
        noPosTimer.reset();
        if (!place.getValue()) return;
        if (!mc.player.getMainHandStack().getItem().equals(Items.END_CRYSTAL) && !mc.player.getOffHandStack().getItem().equals(Items.END_CRYSTAL) && !hasCrystal()) {
            return;
        }
        if (!canTouch(pos.down())) {
            return;
        }
        BlockPos obsPos = pos.down();
        Direction facing = BlockUtil.getClickSide(obsPos);
        Vec3d vec = obsPos.toCenterPos().add(facing.getVector().getX() * 0.5, facing.getVector().getY() * 0.5, facing.getVector().getZ() * 0.5);
        if (facing != Direction.UP && facing != Direction.DOWN) {
            vec = vec.add(0, 0.45, 0);
        }
        if (!shouldYawStep() && !placeTimer.passed((long) placeDelay.getValue())) return;
        if (rotate) {
            if (!faceVector(vec)) return;
        }
        if (!placeTimer.passed((long) placeDelay.getValue())) return;
        if (mc.player.getMainHandStack().getItem().equals(Items.END_CRYSTAL) || mc.player.getOffHandStack().getItem().equals(Items.END_CRYSTAL)) {
            placeTimer.reset();
            syncPos = pos;
            placeCrystal(pos);
        } else {
            placeTimer.reset();
            syncPos = pos;
            int old = mc.player.getInventory().selectedSlot;
            int crystal = getCrystal();
            if (crystal == -1) return;
            doSwap(crystal);
            placeCrystal(pos);
            if (autoSwap.getValue() == SwapMode.Silent) {
                doSwap(old);
            } else if (autoSwap.getValue() == SwapMode.Inventory) {
                doSwap(crystal);
                EntityUtil.syncInventory();
            }
        }
        if (rotate && !shouldYawStep()) {
            Alien.ROTATION.snapBack();
        }
    }

    private void doSwap(int slot) {
        if (autoSwap.getValue() == SwapMode.Silent || autoSwap.getValue() == SwapMode.Normal) {
            InventoryUtil.switchToSlot(slot);
        } else if (autoSwap.getValue() == SwapMode.Inventory) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
        }
    }

    private void baseSwap(int slot) {
        if (!inventory.getValue()) {
            InventoryUtil.switchToSlot(slot);
        } else {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().selectedSlot);
        }
    }

    private int getBlock() {
        if (inventory.getValue()) {
            return InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN);
        } else {
            return InventoryUtil.findBlock(Blocks.OBSIDIAN);
        }
    }

    private int getCrystal() {
        if (autoSwap.getValue() == SwapMode.Silent || autoSwap.getValue() == SwapMode.Normal) {
            return InventoryUtil.findItem(Items.END_CRYSTAL);
        } else if (autoSwap.getValue() == SwapMode.Inventory) {
            return InventoryUtil.findItemInventorySlot(Items.END_CRYSTAL);
        }
        return -1;
    }

    private void placeCrystal(BlockPos pos) {
        //PlaceRender.PlaceMap.put(pos, new PlaceRender.placePosition(pos));
        boolean offhand = mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL;
        BlockPos obsPos = pos.down();
        Direction facing = BlockUtil.getClickSide(obsPos);
        BlockUtil.clickBlock(obsPos, facing, false, offhand ? Hand.OFF_HAND : Hand.MAIN_HAND, swingMode.getValue());
    }

    private boolean faceVector(Vec3d directionVec) {
        if (directionVec == null) return false;
        if (!shouldYawStep()) {
            Alien.ROTATION.lookAt(directionVec);
            return true;
        } else {
            this.directionVec = directionVec;
            if (Alien.ROTATION.inFov(directionVec, fov.getValueFloat())) {
                return true;
            }
        }
        return !checkFov.getValue();
    }

    public enum TargetESP {
        Box,
        Fill,
        Jello,
        ThunderHack,
        None
    }

    private enum Page {
        General, Base, Misc, Rotation, Check, Calc, Render
    }

    private enum SwapMode {
        None, Normal, Silent, Inventory
    }

    private class CrystalRender {
        @EventListener
        public void onRender3D(Render3DEvent event) {
            BlockPos cpos = sync.getValue() && crystalPos != null ? syncPos : crystalPos;
            if (cpos != null) {
                placeVec3d = cpos.down().toCenterPos();
            }
            if (placeVec3d == null) {
                return;
            }
            if (fadeSpeed.getValue() >= 1) {
                currentFade = noPosTimer.passed((long) (startFadeTime.getValue() * 1000)) ? 0 : 0.5;
            } else {
                currentFade = AnimateUtil.animate(currentFade, noPosTimer.passed((long) (startFadeTime.getValue() * 1000)) ? 0 : 0.5, fadeSpeed.getValue() / 10);
            }
            if (currentFade == 0) {
                curVec3d = null;
                return;
            }
            if (curVec3d == null || sliderSpeed.getValue() >= 1) {
                curVec3d = placeVec3d;
            } else {
                curVec3d = new Vec3d(AnimateUtil.animate(curVec3d.x, placeVec3d.x, sliderSpeed.getValue() / 10), AnimateUtil.animate(curVec3d.y, placeVec3d.y, sliderSpeed.getValue() / 10), AnimateUtil.animate(curVec3d.z, placeVec3d.z, sliderSpeed.getValue() / 10));
            }
            if (render.getValue()) {
                Box cbox = new Box(curVec3d, curVec3d);
                if (shrink.getValue()) {
                    cbox = cbox.expand(currentFade);
                } else {
                    cbox = cbox.expand(0.5);
                }
                MatrixStack matrixStack = event.matrixStack;
                if (fill.booleanValue) {
                    Render3DUtil.drawFill(matrixStack, cbox, ColorUtil.injectAlpha(fill.getValue(), (int) (fill.getValue().getAlpha() * currentFade * 2D)));
                }
                if (box.booleanValue) {
                    Render3DUtil.drawBox(matrixStack, cbox, ColorUtil.injectAlpha(box.getValue(), (int) (box.getValue().getAlpha() * currentFade * 2D)), lineWidth.getValueFloat());
                }
            }
            if (text.booleanValue && lastDamage > 0) {
                if (!noPosTimer.passed((long) (startFadeTime.getValue() * 1000)))
                    Render3DUtil.drawText3D(df.format(lastDamage), curVec3d, text.getValue());
            }
        }
    }
}