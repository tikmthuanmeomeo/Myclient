package dev.luminous.core.impl;

import by.radioegor146.nativeobfuscator.Native;
import dev.luminous.Alien;
import dev.luminous.core.Manager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.apache.commons.io.IOUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class FriendManager extends Manager {
    public final ArrayList<String> friendList = new ArrayList<>();

    public FriendManager() {
        read();
    }

    public boolean isFriend(String name) {
        return friendList.contains(name);
    }

    public boolean isFriend(PlayerEntity entity) {
        return isFriend(entity.getGameProfile().getName());
    }

    public void remove(String name) {
        friendList.remove(name);
    }

    public void add(String name) {
        if (!friendList.contains(name)) {
            friendList.add(name);
        }
    }

    public void friend(PlayerEntity entity) {
        friend(entity.getGameProfile().getName());
    }

    public void friend(String name) {
        if (friendList.contains(name)) {
            friendList.remove(name);
        } else {
            friendList.add(name);
        }
    }

    @Native
    public void read() {

        try {
            File friendFile = getFile("friends.txt");
            if (!friendFile.exists())
                return;
            List<String> list = IOUtils.readLines(new FileInputStream(friendFile), StandardCharsets.UTF_8);

            for (String s : list) {
                add(s);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
     /*
        try {
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
                            try {
                                File friendFile = getFile("friends.txt");
                                if (!friendFile.exists())
                                    return;
                                List<String> list = IOUtils.readLines(new FileInputStream(friendFile), StandardCharsets.UTF_8);

                                for (String s : list) {
                                    add(s);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
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

    public void save() {
        try {
            File friendFile = getFile("friends.txt");
            PrintWriter printwriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(friendFile), StandardCharsets.UTF_8));
            for (String str : friendList) {
                printwriter.println(str);
            }
            printwriter.close();
        } catch (Exception exception) {
            exception.printStackTrace();
            System.out.println("[" + Alien.NAME + "] Failed to save friends");
        }
    }
}
