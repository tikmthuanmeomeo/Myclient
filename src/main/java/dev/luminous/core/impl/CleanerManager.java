package dev.luminous.core.impl;

import dev.luminous.core.Manager;
import net.minecraft.item.Items;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CleanerManager extends Manager {
    private final ArrayList<String> list = new ArrayList<>();

    public CleanerManager() {
        read();
    }

    public ArrayList<String> getList() {
        return list;
    }

    public boolean inList(String name) {
        return list.contains(name) || list.contains(name.replace("block.minecraft.", "").replace("item.minecraft.", ""));
    }

    public void clear() {
        list.clear();
    }

    public void remove(String name) {
        name = name.replace("block.minecraft.", "").replace("item.minecraft.", "");
        list.remove(name);
    }

    public void add(String name) {
        name = name.replace("block.minecraft.", "").replace("item.minecraft.", "");
        if (!list.contains(name)) {
            list.add(name);
        }
    }

    public void read() {
        try {
            File friendFile = getFile("cleaner.txt");
            if (!friendFile.exists()) {
                add(Items.NETHERITE_SWORD.getTranslationKey());
                add(Items.NETHERITE_PICKAXE.getTranslationKey());
                add(Items.NETHERITE_HELMET.getTranslationKey());
                add(Items.NETHERITE_CHESTPLATE.getTranslationKey());
                add(Items.NETHERITE_LEGGINGS.getTranslationKey());
                add(Items.NETHERITE_BOOTS.getTranslationKey());
                add(Items.OBSIDIAN.getTranslationKey());
                add(Items.ENDER_CHEST.getTranslationKey());
                add(Items.ENDER_PEARL.getTranslationKey());
                add(Items.ENCHANTED_GOLDEN_APPLE.getTranslationKey());
                add(Items.EXPERIENCE_BOTTLE.getTranslationKey());
                add(Items.COBWEB.getTranslationKey());
                add(Items.POTION.getTranslationKey());
                add(Items.SPLASH_POTION.getTranslationKey());
                add(Items.TOTEM_OF_UNDYING.getTranslationKey());
                add(Items.END_CRYSTAL.getTranslationKey());
                add(Items.ELYTRA.getTranslationKey());
                add(Items.FLINT_AND_STEEL.getTranslationKey());
                add(Items.PISTON.getTranslationKey());
                add(Items.STICKY_PISTON.getTranslationKey());
                add(Items.REDSTONE_BLOCK.getTranslationKey());
                add(Items.GLOWSTONE.getTranslationKey());
                add(Items.RESPAWN_ANCHOR.getTranslationKey());
                add(Items.ANVIL.getTranslationKey());
                return;
            }
            List<String> list = IOUtils.readLines(new FileInputStream(friendFile), StandardCharsets.UTF_8);

            for (String s : list) {
                add(s);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void save() {
        try {
            File friendFile = getFile("cleaner.txt");
            PrintWriter printwriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(friendFile), StandardCharsets.UTF_8));
            for (String str : list) {
                printwriter.println(str);
            }
            printwriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}