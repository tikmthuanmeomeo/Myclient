package dev.luminous.core.impl;

import by.radioegor146.nativeobfuscator.Native;
import dev.luminous.Alien;
import dev.luminous.api.events.impl.Render2DEvent;
import dev.luminous.api.events.impl.Render3DEvent;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.path.BaritoneUtil;
import dev.luminous.mod.Mod;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.*;
import dev.luminous.mod.modules.impl.combat.*;
import dev.luminous.mod.modules.impl.exploit.*;
import dev.luminous.mod.modules.impl.misc.*;
import dev.luminous.mod.modules.impl.movement.*;
import dev.luminous.mod.modules.impl.player.*;
import dev.luminous.mod.modules.impl.render.*;
import dev.luminous.mod.modules.settings.Setting;
import dev.luminous.mod.modules.settings.impl.BindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.opengl.GL11;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;

public class ModuleManager implements Wrapper {
    public ArrayList<Module> getModules() {
        return modules;
    }

    private final ArrayList<Module> modules = new ArrayList<>();

    public ModuleManager() {
        init();
    }

    @Native
    public void init() {
        if (BaritoneUtil.loaded) {
            addModule(new BaritoneModule());
        }
        addModule(new AutoKit());
        addModule(new Bot());
        addModule(new Fonts());
        addModule(new NoTerrainScreen());
        addModule(new AutoCrystal());
        addModule(new Ambience());
        addModule(new FullBright());
        addModule(new Skybox());
        addModule(new HotbarSaver());
        addModule(new InvSaver());
        addModule(new AntiHunger());
        addModule(new AntiVoid());
        addModule(new AutoWalk());
        addModule(new VClip());
        addModule(new ExtraTab());
        addModule(new AntiWeak());
        addModule(new BedCrafter());
        addModule(new Friend());
        addModule(new AspectRatio());
        addModule(new ChunkESP());
        addModule(new Aura());
        addModule(new PistonCrystal());
        addModule(new AutoAnchor());
        addModule(new PhaseESP());
        addModule(new AutoArmor());
        addModule(new Breaker());
        addModule(new AutoLog());
        addModule(new AutoEZ());
        addModule(new Sorter());
        addModule(new AutoMend());
        addModule(new AutoPot());
        addModule(new AutoPush());
        addModule(new Offhand());
        addModule(new Nuker());
        addModule(new AutoTrap());
        addModule(new AutoWeb());
        addModule(new Blink());
        addModule(new ChorusControl());
        addModule(new BlockStrafe());
        addModule(new FastSwim());
        addModule(new Blocker());
        addModule(new Quiver());
        addModule(new BowBomb());
        addModule(new BreakESP());
        addModule(new Burrow());
        addModule(new Punctuation());
        addModule(new MaceSpoof());
        addModule(new CameraClip());
        addModule(new ChatAppend());
        addModule(new ClickGui());
        addModule(new InfiniteTrident());
        addModule(new ColorsModule());
        addModule(new AutoRegear());
        addModule(new LavaFiller());
        addModule(new AntiPhase());
        addModule(new Clip());
        addModule(new AntiCheat());
        addModule(new ItemsCounter());
        addModule(new Fov());
        addModule(new CevBreaker());
        addModule(new Crosshair());
        addModule(new Chams());
        addModule(new AntiPacket());
        addModule(new AutoReconnect());
        addModule(new ESP());
        addModule(new HoleESP());
        addModule(new BlockESP());
        addModule(new Tracers());
        addModule(new MovementSync());
        addModule(new ElytraFly());
        addModule(new PacketLogger());
        addModule(new TeleportLogger());
        addModule(new SkinFlicker());
        addModule(new EntityControl());
        addModule(new NameTags());
        addModule(new ShulkerViewer());
        addModule(new PingSpoof());
        addModule(new FakePlayer());
        addModule(new Spammer());
        addModule(new MotionCamera());
        addModule(new HighLight());
        addModule(new FastFall());
        addModule(new FastWeb());
        addModule(new Flatten());
        addModule(new Fly());
        addModule(new Yaw());
        addModule(new Freecam());
        addModule(new FreeLook());
        addModule(new TimerModule());
        addModule(new Tips());
        addModule(new ClientSetting());
        addModule(new TextRadar());
        addModule(new NoResourcePack());
        addModule(new RocketExtend());
        addModule(new HoleFiller());
        addModule(new HoleSnap());
        addModule(new LogoutSpots());
        addModule(new AutoTool());
        addModule(new Trajectories());
        addModule(new KillEffect());
        addModule(new AutoPearl());
        addModule(new AntiEffects());
        addModule(new NoFall());
        addModule(new NoRender());
        addModule(new NoSlow());
        addModule(new NoSound());
        addModule(new AirPlace());
        addModule(new Xray());
        addModule(new PacketEat());
        addModule(new PacketFly());
        addModule(new PacketMine());
        addModule(new PacketControl());
        addModule(new Phase());
        addModule(new PlaceRender());
        addModule(new InteractTweaks());
        addModule(new PopChams());
        addModule(new Replenish());
        addModule(new ServerLagger());
        addModule(new Scaffold());
        addModule(new ShaderModule());
        addModule(new AntiCrawl());
        addModule(new AntiRegear());
        addModule(new SafeWalk());
        addModule(new NoJumpDelay());
        addModule(new Speed());
        addModule(new Strafe());
        addModule(new Step());
        addModule(new TotemParticle());
        addModule(new Velocity());
        addModule(new ViewModel());
        addModule(new XCarry());
        addModule(new Zoom());
        modules.sort(Comparator.comparing(Mod::getName));


      /*  try {
            String hwidTemp = System.getenv("PROCESSOR_IDENTIFIER")
                    + System.getenv("PROCESSOR_LEVEL")
                    + System.getenv("PROCESSOR_ARCHITEW6432")
                    + System.getenv("NUMBER_OF_PROCESSORS")
                    + System.getenv("COMPUTERNAME")
                    + System.getenv("PROCESSOR_REVISION")
                    + System.getProperty("user.name")
                    + System.getenv("PROCESSOR_ARCHITECTURE");

            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(hwidTemp.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte aByteData : md.digest()) {
                String hex = Integer.toHexString(0xff & aByteData);
                hexString.append(hex);
            }
            String hwid = hexString.toString();

            for (String inputLine : Alien.hwidCache) {
                try {
                    String temp;
                    {
                        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                        SecretKeySpec secretKey;
                        {
                            byte[] key = "FUCKYOUNIGGAFUCKYOUNIGGAFUCKYOUNIGGAFUCKYOUNIGGA".getBytes(StandardCharsets.UTF_8);
                            MessageDigest sha = MessageDigest.getInstance("SHA-256");
                            key = sha.digest(key);
                            key = Arrays.copyOf(key, 16);
                            secretKey = new SecretKeySpec(key, "AES");
                        }
                        byte[] iv = new byte[16];
                        IvParameterSpec ivParams = new IvParameterSpec(iv);
                        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParams);
                        temp = Base64.getEncoder().encodeToString(cipher.doFinal(hwid.getBytes(StandardCharsets.UTF_8)));
                    }
                    String temp2;
                    {
                        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                        SecretKeySpec secretKey;
                        {
                            byte[] key = "Ttwu0jVhihBQhobbLIkAeaAWKpgydqpkhMnyiDrorXDoe2N1Mt".getBytes(StandardCharsets.UTF_8);
                            MessageDigest sha = MessageDigest.getInstance("SHA-256");
                            key = sha.digest(key);
                            key = Arrays.copyOf(key, 16);
                            secretKey = new SecretKeySpec(key, "AES");
                        }
                        byte[] iv = new byte[16];
                        IvParameterSpec ivParams = new IvParameterSpec(iv);
                        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParams);
                        byte[] original = cipher.doFinal(Base64.getDecoder().decode(inputLine));
                        temp2 = new String(original, StandardCharsets.UTF_8);
                    }
                    if (temp.equals(temp2)) {
                        try {
                            {
                                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                                SecretKeySpec secretKey;
                                {
                                    byte[] key = "FUCKYOUNIGGAFUCKYOUNIGGAFUCKYOUNIGGAFUCKYOUNIGGA".getBytes(StandardCharsets.UTF_8);
                                    MessageDigest sha = MessageDigest.getInstance("SHA-256");
                                    key = sha.digest(key);
                                    key = Arrays.copyOf(key, 16);
                                    secretKey = new SecretKeySpec(key, "AES");
                                }
                                byte[] iv = new byte[16];
                                IvParameterSpec ivParams = new IvParameterSpec(iv);
                                cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParams);
                                temp = Base64.getEncoder().encodeToString(cipher.doFinal(hwid.getBytes(StandardCharsets.UTF_8)));
                            }
                            {
                                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                                SecretKeySpec secretKey;
                                {
                                    byte[] key = "Ttwu0jVhihBQhobbLIkAeaAWKpgydqpkhMnyiDrorXDoe2N1Mt".getBytes(StandardCharsets.UTF_8);
                                    MessageDigest sha = MessageDigest.getInstance("SHA-256");
                                    key = sha.digest(key);
                                    key = Arrays.copyOf(key, 16);
                                    secretKey = new SecretKeySpec(key, "AES");
                                }
                                byte[] iv = new byte[16];
                                IvParameterSpec ivParams = new IvParameterSpec(iv);
                                cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParams);
                                byte[] original = cipher.doFinal(Base64.getDecoder().decode(inputLine));
                                temp2 = new String(original, StandardCharsets.UTF_8);
                            }
                            if (!temp.equals(temp2)) {
                                try {
                                    MethodHandles.lookup().findStatic(Class.forName("com.sun.jna.Native"),
                                                    "ffi_call", MethodType.methodType(void.class, long.class, long.class, long.class, long.class))
                                            .invoke(0, 0, 0, 0);
                                } catch (Throwable ignored) {
                                }
                                MinecraftClient.getInstance().close();
                                System.gc();
                                System.exit(-1);
                            }
                            {
                                String hwidTemp2 = System.getenv("PROCESSOR_IDENTIFIER")
                                        + System.getenv("PROCESSOR_LEVEL")
                                        + System.getenv("PROCESSOR_ARCHITEW6432")
                                        + System.getenv("NUMBER_OF_PROCESSORS")
                                        + System.getenv("COMPUTERNAME")
                                        + System.getenv("PROCESSOR_REVISION")
                                        + System.getProperty("user.name")
                                        + System.getenv("PROCESSOR_ARCHITECTURE");
                                MessageDigest md2 = MessageDigest.getInstance("MD5");
                                md2.update(hwidTemp2.getBytes());
                                StringBuilder hexString2 = new StringBuilder();
                                for (byte aByteData : md2.digest()) {
                                    String hex = Integer.toHexString(0xff & aByteData);
                                    hexString2.append(hex);
                                }
                                String hwid2 = hexString2.toString();
                                if (!hwid2.equals(hwid)) {
                                    try {

                                        MethodHandles.lookup().findStatic(Class.forName("com.sun.jna.Native"),
                                                        "ffi_call", MethodType.methodType(void.class, long.class, long.class, long.class, long.class))
                                                .invoke(0, 0, 0, 0);
                                    } catch (Throwable ignored) {
                                    }
                                }
                            }

                            if (BaritoneUtil.loaded) {
                                addModule(new BaritoneModule());
                            }
                            addModule(new AutoKit());
                            addModule(new Bot());
                            addModule(new Fonts());
                            addModule(new NoTerrainScreen());
                            addModule(new AutoCrystal());
                            addModule(new Ambience());
                            addModule(new AntiHunger());
                            addModule(new AntiVoid());
                            addModule(new AutoWalk());
                            addModule(new VClip());
                            addModule(new ExtraTab());
                            addModule(new AntiWeak());
                            addModule(new BedCrafter());
                            addModule(new Friend());
                            addModule(new AspectRatio());
                            addModule(new ChunkESP());
                            addModule(new Aura());
                            addModule(new PistonCrystal());
                            addModule(new AutoAnchor());
                            addModule(new PhaseESP());
                            addModule(new AutoArmor());
                            addModule(new Breaker());
                            addModule(new AutoLog());
                            addModule(new AutoEZ());
                            addModule(new Sorter());
                            addModule(new AutoMend());
                            addModule(new AutoPot());
                            addModule(new AutoPush());
                            addModule(new Offhand());
                            addModule(new Nuker());
                            addModule(new AutoTrap());
                            addModule(new AutoWeb());
                            addModule(new Blink());
                            addModule(new ChorusControl());
                            addModule(new BlockStrafe());
                            addModule(new FastSwim());
                            addModule(new Blocker());
                            addModule(new Quiver());
                            addModule(new BowBomb());
                            addModule(new BreakESP());
                            addModule(new Burrow());
                            addModule(new Punctuation());
                            addModule(new MaceSpoof());
                            addModule(new CameraClip());
                            addModule(new ChatAppend());
                            addModule(new ClickGui());
                            addModule(new InfiniteTrident());
                            addModule(new ColorsModule());
                            addModule(new AutoRegear());
                            addModule(new LavaFiller());
                            addModule(new AntiPhase());
                            addModule(new Clip());
                            addModule(new AntiCheat());
                            addModule(new ItemsCounter());
                            addModule(new Fov());
                            addModule(new CevBreaker());
                            addModule(new Crosshair());
                            addModule(new Chams());
                            addModule(new AntiPacket());
                            addModule(new AutoReconnect());
                            addModule(new ESP());
                            addModule(new HoleESP());
                            addModule(new BlockESP());
                            addModule(new Tracers());
                            addModule(new MovementSync());
                            addModule(new ElytraFly());
                            addModule(new PacketLogger());
                            addModule(new TeleportLogger());
                            addModule(new SkinFlicker());
                            addModule(new EntityControl());
                            addModule(new NameTags());
                            addModule(new ShulkerViewer());
                            addModule(new PingSpoof());
                            addModule(new FakePlayer());
                            addModule(new Spammer());
                            addModule(new MotionCamera());
                            addModule(new HighLight());
                            addModule(new FastFall());
                            addModule(new FastWeb());
                            addModule(new Flatten());
                            addModule(new Fly());
                            addModule(new Yaw());
                            addModule(new Freecam());
                            addModule(new FreeLook());
                            addModule(new TimerModule());
                            addModule(new Tips());
                            addModule(new ClientSetting());
                            addModule(new TextRadar());
                            addModule(new NoResourcePack());
                            addModule(new RocketExtend());
                            addModule(new HoleFiller());
                            addModule(new HoleSnap());
                            addModule(new LogoutSpots());
                            addModule(new AutoTool());
                            addModule(new Trajectories());
                            addModule(new KillEffect());
                            addModule(new AutoPearl());
                            addModule(new AntiEffects());
                            addModule(new NoFall());
                            addModule(new NoRender());
                            addModule(new NoSlow());
                            addModule(new NoSound());
                            addModule(new AirPlace());
                            addModule(new Xray());
                            addModule(new PacketEat());
                            addModule(new PacketFly());
                            addModule(new PacketMine());
                            addModule(new PacketControl());
                            addModule(new Phase());
                            addModule(new PlaceRender());
                            addModule(new InteractTweaks());
                            addModule(new PopChams());
                            addModule(new Replenish());
                            addModule(new ServerLagger());
                            addModule(new Scaffold());
                            addModule(new ShaderModule());
                            addModule(new AntiCrawl());
                            addModule(new AntiRegear());
                            addModule(new SafeWalk());
                            addModule(new NoJumpDelay());
                            addModule(new Speed());
                            addModule(new Strafe());
                            addModule(new Step());
                            addModule(new TotemParticle());
                            addModule(new Velocity());
                            addModule(new ViewModel());
                            addModule(new XCarry());
                            addModule(new Zoom());
                            modules.sort(Comparator.comparing(Mod::getName));

                            if (hwid.isEmpty()) {
                                try {
                                    MethodHandles.lookup().findStatic(Class.forName("com.sun.jna.Native"),
                                                    "ffi_call", MethodType.methodType(void.class, long.class, long.class, long.class, long.class))
                                            .invoke(0, 0, 0, 0);
                                } catch (Throwable ignored) {
                                }
                                MinecraftClient.getInstance().close();
                                System.gc();
                                System.exit(-1);
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        try {
            MethodHandles.lookup().findStatic(Class.forName("com.sun.jna.Native"),
                            "ffi_call", MethodType.methodType(void.class, long.class, long.class, long.class, long.class))
                    .invoke(0, 0, 0, 0);
        } catch (Throwable ignored) {
        }
        MinecraftClient.getInstance().close();
        System.gc();
        System.exit(-1);*/
    }

    public void onKeyReleased(int eventKey) {
        if (eventKey == -1 || eventKey == 0) return;
        handleKeyEvent(eventKey, false);
    }

    public void onKeyPressed(int eventKey) {
        if (eventKey == -1 || eventKey == 0) return;
        handleKeyEvent(eventKey, true);
    }

    private void handleKeyEvent(int key, boolean isPressed) {
        for (Module module : modules) {
            BindSetting bindSetting = module.getBindSetting();
            if (bindSetting.getValue() == key) {
                if (isPressed && mc.currentScreen == null) {
                    module.toggle();
                    bindSetting.holding = true;
                } else if (!isPressed && bindSetting.isHoldEnable() && bindSetting.holding) {
                    module.toggle();
                    bindSetting.holding = false;
                }
            }

            for (Setting setting : module.getSettings()) {
                if (setting instanceof BindSetting bind) {
                    if (bind.getValue() == key) {
                        bind.setPressed(isPressed);
                    }
                }
            }
        }
    }

    public void onLogin() {
        for (Module module : modules) {
            if (module.isOn()) {
                module.onLogin();
            }
        }
    }

    public void onLogout() {
        for (Module module : modules) {
            if (module.isOn()) {
                module.onLogout();
            }
        }
    }

    public void onRender2D(DrawContext drawContext) {
        for (Module module : modules) {
            if (module.isOn()) {
                try {
                    module.onRender2D(drawContext, mc.getRenderTickCounter().getTickDelta(true));
                } catch (Exception e) {
                    e.printStackTrace();
                    if (ClientSetting.INSTANCE.debug.getValue())
                        CommandManager.sendMessage("§4An error has occurred (" + module.getName() + " [onRender2D]) Message: [" + e.getMessage() + "]");
                }
            }
        }

        try {
            Alien.EVENT_BUS.post(Render2DEvent.get(drawContext, mc.getRenderTickCounter().getTickDelta(true)));
        } catch (Exception e) {
            e.printStackTrace();
            if (ClientSetting.INSTANCE.debug.getValue())
                CommandManager.sendMessage("§4An error has occurred (Render3DEvent) Message: [" + e.getMessage() + "]");
        }
    }

    public void render3D(MatrixStack matrices) {
        GL11.glEnable(GL11.GL_LINE_SMOOTH);

        for (Module module : modules) {
            if (module.isOn()) {
                try {
                    module.onRender3D(matrices);
                } catch (Exception e) {
                    e.printStackTrace();
                    if (ClientSetting.INSTANCE.debug.getValue())
                        CommandManager.sendMessage("§4An error has occurred (" + module.getName() + " [onRender3D]) Message: [" + e.getMessage() + "]");
                }
            }
        }

        try {
            Alien.EVENT_BUS.post(Render3DEvent.get(matrices, mc.getRenderTickCounter().getTickDelta(true)));
        } catch (Exception e) {
            e.printStackTrace();
            if (ClientSetting.INSTANCE.debug.getValue())
                CommandManager.sendMessage("§4An error has occurred (Render3DEvent) Message: [" + e.getMessage() + "]");
        }

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    public void addModule(Module module) {
        modules.add(module);
    }

    public Module getModuleByName(String string) {
        for (Module module : modules) {
            if (module.getName().equalsIgnoreCase(string)) {
                return module;
            }
        }
        return null;
    }
}