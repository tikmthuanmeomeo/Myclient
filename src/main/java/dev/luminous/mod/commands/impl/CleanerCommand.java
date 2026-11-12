package dev.luminous.mod.commands.impl;

import dev.luminous.Alien;
import dev.luminous.core.impl.PlayerManager;
import dev.luminous.mod.commands.Command;
import dev.luminous.mod.gui.windows.WindowsScreen;
import dev.luminous.mod.gui.windows.impl.ItemSelectWindow;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

public class CleanerCommand extends Command {

    public CleanerCommand() {
        super("cleaner", "[\"\"/name/reset/clear/list] | [add/remove] [name]");
    }

    @Override
    public void runCommand(String[] parameters) {
        if (parameters.length == 0) {
            PlayerManager.screenToOpen = (new WindowsScreen(new ItemSelectWindow(Alien.CLEANER)));
            return;
        }
        switch (parameters[0]) {
            case "reset" -> {
                Alien.CLEANER.clear();
                Alien.CLEANER.add(Items.NETHERITE_SWORD.getTranslationKey());
                Alien.CLEANER.add(Items.NETHERITE_PICKAXE.getTranslationKey());
                Alien.CLEANER.add(Items.NETHERITE_HELMET.getTranslationKey());
                Alien.CLEANER.add(Items.NETHERITE_CHESTPLATE.getTranslationKey());
                Alien.CLEANER.add(Items.NETHERITE_LEGGINGS.getTranslationKey());
                Alien.CLEANER.add(Items.NETHERITE_BOOTS.getTranslationKey());
                Alien.CLEANER.add(Items.OBSIDIAN.getTranslationKey());
                Alien.CLEANER.add(Items.ENDER_CHEST.getTranslationKey());
                Alien.CLEANER.add(Items.ENDER_PEARL.getTranslationKey());
                Alien.CLEANER.add(Items.ENCHANTED_GOLDEN_APPLE.getTranslationKey());
                Alien.CLEANER.add(Items.EXPERIENCE_BOTTLE.getTranslationKey());
                Alien.CLEANER.add(Items.COBWEB.getTranslationKey());
                Alien.CLEANER.add(Items.POTION.getTranslationKey());
                Alien.CLEANER.add(Items.SPLASH_POTION.getTranslationKey());
                Alien.CLEANER.add(Items.TOTEM_OF_UNDYING.getTranslationKey());
                Alien.CLEANER.add(Items.END_CRYSTAL.getTranslationKey());
                Alien.CLEANER.add(Items.ELYTRA.getTranslationKey());
                Alien.CLEANER.add(Items.FLINT_AND_STEEL.getTranslationKey());
                Alien.CLEANER.add(Items.PISTON.getTranslationKey());
                Alien.CLEANER.add(Items.STICKY_PISTON.getTranslationKey());
                Alien.CLEANER.add(Items.REDSTONE_BLOCK.getTranslationKey());
                Alien.CLEANER.add(Items.GLOWSTONE.getTranslationKey());
                Alien.CLEANER.add(Items.RESPAWN_ANCHOR.getTranslationKey());
                Alien.CLEANER.add(Items.ANVIL.getTranslationKey());
                sendChatMessage("§fItems list got reset");
                return;
            }
            case "clear" -> {
                Alien.CLEANER.getList().clear();
                sendChatMessage("§fItems list got clear");
                return;
            }
            case "list" -> {
                if (Alien.CLEANER.getList().isEmpty()) {
                    sendChatMessage("§fItems list is empty");
                    return;
                }

                for (String name : Alien.CLEANER.getList()) {
                    sendChatMessage("§a" + name);
                }
                return;
            }
            case "add" -> {
                if (parameters.length == 2) {
                    Alien.CLEANER.add(parameters[1]);
                    sendChatMessage("§f" + parameters[1] + (Alien.CLEANER.inList(parameters[1]) ? " §ahas been added" : " §chas been removed"));
                    return;
                }
                sendUsage();
                return;
            }
            case "remove" -> {
                if (parameters.length == 2) {
                    Alien.CLEANER.remove(parameters[1]);
                    sendChatMessage("§f" + parameters[1] + (Alien.CLEANER.inList(parameters[1]) ? " §ahas been added" : " §chas been removed"));
                    return;
                }
                sendUsage();
                return;
            }
        }

        if (parameters.length == 1) {
            sendChatMessage("§f" + parameters[0] + (Alien.CLEANER.inList(parameters[0]) ? " §ais in whitelist" : " §cisn't in whitelist"));
            return;
        }

        sendUsage();
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        if (count == 1) {
            String input = seperated.getLast().toLowerCase();
            List<String> correct = new ArrayList<>();
            List<String> list = List.of("add", "remove", "list", "reset", "clear");
            for (String x : list) {
                if (input.equalsIgnoreCase(Alien.getPrefix() + "cleaner") || x.toLowerCase().startsWith(input)) {
                    correct.add(x);
                }
            }
            int numCmds = correct.size();
            String[] commands = new String[numCmds];

            int i = 0;
            for (String x : correct) {
                commands[i++] = x;
            }

            return commands;
        }
        return null;
    }
}
