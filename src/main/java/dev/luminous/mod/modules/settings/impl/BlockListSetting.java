package dev.luminous.mod.modules.settings.impl;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * A set of blocks. It is stored as a comma separated list of block ids in a StringSetting,
 * so the existing config code saves and loads it without changes.
 */
public class BlockListSetting extends StringSetting {
    private String parsedFrom = null;
    private final Set<Block> blocks = new HashSet<>();

    public BlockListSetting(String name, Block... defaults) {
        super(name, join(defaults));
    }

    public BlockListSetting(String name, BooleanSupplier visibility, Block... defaults) {
        super(name, join(defaults), visibility);
    }

    private static String join(Block[] list) {
        StringBuilder sb = new StringBuilder();
        for (Block b : list) {
            if (sb.length() > 0) sb.append(',');
            sb.append(Registries.BLOCK.getId(b));
        }
        return sb.toString();
    }

    private void parse() {
        String raw = getValue();
        if (raw == null) raw = "";
        if (raw.equals(parsedFrom)) return;
        parsedFrom = raw;
        blocks.clear();
        for (String part : raw.split(",")) {
            if (part.isBlank()) continue;
            Identifier id = Identifier.tryParse(part.trim());
            if (id == null) continue;
            Block block = Registries.BLOCK.get(id);
            if (block != Blocks.AIR) blocks.add(block);
        }
    }

    private void write() {
        List<Block> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparing(b -> Registries.BLOCK.getId(b).toString()));
        String text = join(sorted.toArray(new Block[0]));
        setValue(text);
        parsedFrom = text;
    }

    public Set<Block> getBlocks() {
        parse();
        return blocks;
    }

    public boolean contains(Block block) {
        parse();
        return blocks.contains(block);
    }

    public int size() {
        parse();
        return blocks.size();
    }

    public void toggle(Block block) {
        parse();
        if (!blocks.remove(block)) blocks.add(block);
        write();
    }

    public void clear() {
        parse();
        blocks.clear();
        write();
    }
}
