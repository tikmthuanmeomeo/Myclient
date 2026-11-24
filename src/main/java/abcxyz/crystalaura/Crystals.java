/*
 * Ported from Anubis Client (GPL-3.0-only, (c) 4ldenz), see THIRD_PARTY_NOTICES/Anubis-LICENSE.txt.
 * Changed to run on Minecraft 1.21.4 inside the abcxyz client.
 */
package abcxyz.crystalaura;

import abcxyz.mixin.ClientPlayerEntityAccessor;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemConvertible;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.util.InputUtil;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.effect.EnchantmentEffectEntry;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.enchantment.effect.value.AddEnchantmentEffect;
import org.lwjgl.glfw.GLFW;

public final class Crystals {
    public static final int READY = -2;
    public static final int NO_ROUTE = -1;
    private static final int MIN_CONFIRM_TICKS = 4;
    private static final int MAX_CONFIRM_TICKS = 20;
    private static final float RTT_SMOOTHING = 0.25f;
    private static final double RESERVE_RANGE = 12.0;
    private static final Set<Block> USABLE_BLOCKS = Set.of(Blocks.RESPAWN_ANCHOR, Blocks.NOTE_BLOCK, Blocks.LEVER, Blocks.REPEATER, Blocks.REDSTONE_WIRE, Blocks.CAKE, Blocks.COMPOSTER, Blocks.DRAGON_EGG);
    private static final Long2IntOpenHashMap PLACED_AT = new Long2IntOpenHashMap();
    private static float measuredRtt;

    private Crystals() {
    }

    public static boolean isBase(BlockState state) {
        return state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
    }

    public static Vec3d explosionAt(BlockPos base) {
        return Vec3d.ofBottomCenter((Vec3i)base.up());
    }

    public static Box boxOn(BlockPos base) {
        return EntityType.END_CRYSTAL.getDimensions().getBoxAt(Crystals.explosionAt(base));
    }

    public static boolean hasCrystal(World level, BlockPos base, Entity ignore) {
        BlockPos above = base.up();
        for (EndCrystalEntity crystal : level.getNonSpectatingEntities(EndCrystalEntity.class, new Box(above))) {
            if (crystal == ignore || !crystal.getBlockPos().equals((Object)above)) continue;
            return true;
        }
        return false;
    }

    public static boolean canHost(World level, BlockPos base, Entity ignore) {
        BlockPos above = base.up();
        if (!level.isAir(above)) {
            return false;
        }
        Box space = new Box((double)above.getX(), (double)above.getY(), (double)above.getZ(), (double)above.getX() + 1.0, (double)above.getY() + 2.0, (double)above.getZ() + 1.0);
        for (Entity entity : level.getOtherEntities(ignore, space)) {
            if (!entity.isAlive()) continue;
            return false;
        }
        return true;
    }

    public static BlockPos obsidianTarget(ClientWorld level, ClientPlayerEntity player, BlockHitResult hit, BlockState clicked) {
        BlockPos target = Crystals.placedObsidian(level, player, hit, clicked);
        return target != null && Crystals.canHost((World)level, target, null) ? target : null;
    }

    public static BlockPos placedObsidian(ClientWorld level, ClientPlayerEntity player, BlockHitResult hit, BlockState clicked) {
        if (!player.shouldCancelInteraction() && Crystals.reactsToUse((World)level, hit.getBlockPos(), clicked)) {
            return null;
        }
        ItemPlacementContext context = new ItemPlacementContext((PlayerEntity)player, Hand.MAIN_HAND, new ItemStack((ItemConvertible)Items.OBSIDIAN), hit);
        if (!context.canPlace()) {
            return null;
        }
        BlockPos target = context.getBlockPos();
        if (level.isOutOfHeightLimit(target) || !level.getWorldBorder().contains(target)) {
            return null;
        }
        if (!level.canPlace(Blocks.OBSIDIAN.getDefaultState(), target, ShapeContext.of((Entity)player))) {
            return null;
        }
        return target;
    }

    private static boolean reactsToUse(World level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockEntityProvider || USABLE_BLOCKS.contains(block)) {
            return true;
        }
        if (state.isIn(BlockTags.DOORS) || state.isIn(BlockTags.TRAPDOORS) || state.isIn(BlockTags.FENCE_GATES) || state.isIn(BlockTags.BUTTONS) || state.isIn(BlockTags.CAULDRONS) || state.isIn(BlockTags.CANDLE_CAKES) || state.isIn(BlockTags.FLOWER_POTS)) {
            return true;
        }
        return state.createScreenHandlerFactory(level, pos) != null;
    }

    public static int route(ClientPlayerEntity player, Item item, BlockState clicked, int preferredSlot) {
        ItemStack main = player.getMainHandStack();
        if (main.isOf(item)) {
            return -2;
        }
        boolean inOffhand = player.getOffHandStack().isOf(item);
        if (inOffhand && Crystals.letsClickThrough(main, clicked)) {
            return -2;
        }
        PlayerInventory inventory = player.getInventory();
        int slot = Crystals.hotbarSlotOf(inventory, item);
        if (slot != -1) {
            return slot;
        }
        if (!inOffhand) {
            return -1;
        }
        if (PlayerInventory.isValidHotbarIndex((int)preferredSlot) && Crystals.letsClickThrough(inventory.getStack(preferredSlot), clicked)) {
            return preferredSlot;
        }
        for (slot = 0; slot < PlayerInventory.getHotbarSize(); ++slot) {
            if (!Crystals.letsClickThrough(inventory.getStack(slot), clicked)) continue;
            return slot;
        }
        return -1;
    }

    public static boolean letsClickThrough(ItemStack stack, BlockState clicked) {
        if (stack.isEmpty()) {
            return true;
        }
        if (stack.contains(DataComponentTypes.CONSUMABLE) || stack.isOf(Items.SHIELD)) {
            return false;
        }
        EquippableComponent equippable = (EquippableComponent)stack.get(DataComponentTypes.EQUIPPABLE);
        if (equippable != null && equippable.swappable()) {
            return false;
        }
        return stack.isIn(ItemTags.SWORDS) || stack.isOf(Items.MACE) || stack.isOf(Items.TOTEM_OF_UNDYING) || stack.isIn(ItemTags.AXES) && Crystals.isBase(clicked);
    }

    public static boolean isCombatItem(ItemStack stack) {
        return stack.isEmpty() || stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES) || stack.isOf(Items.MACE) || stack.isOf(Items.TOTEM_OF_UNDYING);
    }

    public static int hotbarSlotOf(PlayerInventory inventory, Item item) {
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); ++slot) {
            if (!inventory.getStack(slot).isOf(item)) continue;
            return slot;
        }
        return -1;
    }

    public static boolean strike(MinecraftClient mc, ClientPlayerEntity player, Entity target) {
        if (player == null || mc.interactionManager == null || target == null || target.isRemoved() || player.isSpectator()) {
            return false;
        }
        if (MeleeAttacks.actedThisTick((PlayerEntity)player) || !MeleeAttacks.handsFree(mc, player) || player.isSprinting() != ((ClientPlayerEntityAccessor)player).abcxyz$wasSprinting()) {
            return false;
        }
        mc.interactionManager.attackEntity((PlayerEntity)player, target);
        player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    public static boolean rightClick(MinecraftClient mc, ClientPlayerEntity player, BlockHitResult hit) {
        ActionResult.Success success;
        if (MeleeAttacks.actedThisTick((PlayerEntity)player) || !MeleeAttacks.handsFree(mc, player)) {
            return false;
        }
        Hand hand = Crystals.letsClickThrough(player.getMainHandStack(), mc.world.getBlockState(hit.getBlockPos())) ? Hand.OFF_HAND : Hand.MAIN_HAND;
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isItemEnabled(mc.world.getEnabledFeatures())) {
            return false;
        }
        int countBefore = stack.getCount();
        ActionResult result = mc.interactionManager.interactBlock(player, hand, hit);
        if (result instanceof ActionResult.Success && (success = (ActionResult.Success)result).swingSource() == ActionResult.SwingSource.CLIENT) {
            player.swingHand(hand);
            if (!stack.isEmpty() && (stack.getCount() != countBefore || player.isInCreativeMode())) {
                mc.gameRenderer.firstPersonRenderer.resetEquipProgress(hand);
            }
        }
        return true;
    }

    public static int weaponRoute(ClientPlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        if (Crystals.swings(player, main) && (Crystals.enchantsAddDamage(main) || Crystals.dealsDamage((LivingEntity)player, main))) {
            return -2;
        }
        PlayerInventory inventory = player.getInventory();
        int fallback = -1;
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); ++slot) {
            ItemStack stack = inventory.getStack(slot);
            if (!Crystals.swings(player, stack)) continue;
            if (Crystals.enchantsAddDamage(stack)) {
                return slot;
            }
            if (fallback != -1 || !Crystals.dealsDamage((LivingEntity)player, stack)) continue;
            fallback = slot;
        }
        return fallback;
    }

    private static boolean swings(ClientPlayerEntity player, ItemStack stack) {
        return true;
    }

    public static boolean enchantsAddDamage(ItemStack weapon) {
        float bonus = 0.0f;
        for (Object2IntMap.Entry entry : weapon.getEnchantments().getEnchantmentEntries()) {
            Enchantment enchantment = (Enchantment)((RegistryEntry)entry.getKey()).value();
            for (EnchantmentEffectEntry effect : enchantment.getEffect(EnchantmentEffectComponentTypes.DAMAGE)) {
                Object object;
                if (effect.requirements().isPresent() || !((object = effect.effect()) instanceof AddEnchantmentEffect)) {
                    return false;
                }
                AddEnchantmentEffect add = (AddEnchantmentEffect)object;
                bonus += add.value().getValue(entry.getIntValue());
            }
        }
        return bonus > 0.0f;
    }

    public static boolean dealsDamage(LivingEntity player, ItemStack mainHand) {
        EntityAttributeInstance damage = new EntityAttributeInstance(EntityAttributes.ATTACK_DAMAGE, changed -> {});
        damage.setBaseValue(player.getAttributeBaseValue(EntityAttributes.ATTACK_DAMAGE));
        for (StatusEffectInstance effect : player.getStatusEffects()) {
            ((StatusEffect)effect.getEffectType().value()).forEachAttributeModifier(effect.getAmplifier(), (attribute, modifier) -> Crystals.include(damage, (RegistryEntry<EntityAttribute>)attribute, modifier));
        }
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack stack = slot == EquipmentSlot.MAINHAND ? mainHand : player.getEquippedStack(slot);
            if (stack.isEmpty() || stack.shouldBreak()) continue;
            stack.applyAttributeModifiers(slot, (attribute, modifier) -> Crystals.include(damage, (RegistryEntry<EntityAttribute>)attribute, modifier));
        }
        return (float)damage.getValue() > 0.0f;
    }

    private static void include(EntityAttributeInstance damage, RegistryEntry<EntityAttribute> attribute, EntityAttributeModifier modifier) {
        if (!EntityAttributes.ATTACK_DAMAGE.equals(attribute)) {
            return;
        }
        damage.removeModifier(modifier.id());
        damage.addTemporaryModifier(modifier);
    }

    public static int confirmWindow(MinecraftClient mc, ClientPlayerEntity player) {
        return MathHelper.clamp((int)(Crystals.rttTicks(mc, player) + 2), (int)4, (int)20);
    }

    public static int rttTicks(MinecraftClient mc, ClientPlayerEntity player) {
        return measuredRtt > 0.0f ? Math.round(measuredRtt) : Crystals.latencyMs(mc, player) / 50 + 1;
    }

    public static void notePlace(ClientPlayerEntity player, BlockPos base) {
        ObjectIterator iterator = PLACED_AT.long2IntEntrySet().iterator();
        while (iterator.hasNext()) {
            int age = player.age - ((Long2IntMap.Entry)iterator.next()).getIntValue();
            if (age >= 0 && age <= 20) continue;
            iterator.remove();
        }
        PLACED_AT.put(base.asLong(), player.age);
    }

    public static void noteSpawn(ClientPlayerEntity player, BlockPos base) {
        long key = base.asLong();
        if (!PLACED_AT.containsKey(key)) {
            return;
        }
        int ticks = player.age - PLACED_AT.remove(key);
        if (ticks <= 0 || ticks > 20) {
            return;
        }
        measuredRtt = measuredRtt > 0.0f ? measuredRtt + ((float)ticks - measuredRtt) * 0.25f : (float)ticks;
    }

    public static boolean placing(ClientPlayerEntity player, BlockPos base, int window) {
        long key = base.asLong();
        return PLACED_AT.containsKey(key) && Crystals.sent(player, PLACED_AT.get(key), window);
    }

    public static boolean anyPlacing(ClientPlayerEntity player, int window) {
        IntIterator intIterator = PLACED_AT.values().iterator();
        while (intIterator.hasNext()) {
            int tick = (Integer)intIterator.next();
            if (!Crystals.sent(player, tick, window)) continue;
            return true;
        }
        return false;
    }

    public static List<Box> reservedBoxes(ClientPlayerEntity player, int window) {
        ArrayList<Box> boxes = new ArrayList<Box>();
        for (EndCrystalEntity crystal : player.getWorld().getNonSpectatingEntities(EndCrystalEntity.class, player.getBoundingBox().expand(12.0))) {
            if (!crystal.isAlive()) continue;
            boxes.add(crystal.getBoundingBox());
        }
        for (Long2IntMap.Entry entry : PLACED_AT.long2IntEntrySet()) {
            if (!Crystals.sent(player, entry.getIntValue(), window)) continue;
            boxes.add(Crystals.boxOn(BlockPos.fromLong((long)entry.getLongKey())));
        }
        return boxes;
    }

    private static boolean sent(ClientPlayerEntity player, int tick, int window) {
        int age = player.age - tick;
        return age >= 0 && age < window;
    }

    public static int humanGap() {
        double roll = ThreadLocalRandom.current().nextDouble();
        return roll < 0.7 ? 2 : (roll < 0.95 ? 3 : 4);
    }

    public static void forgetRtt() {
        PLACED_AT.clear();
        measuredRtt = 0.0f;
    }

    private static int latencyMs(MinecraftClient mc, ClientPlayerEntity player) {
        ClientPlayNetworkHandler connection = mc.getNetworkHandler();
        if (connection == null) {
            return 0;
        }
        PlayerListEntry info = connection.getPlayerListEntry(player.getUuid());
        return info == null ? 0 : Math.max(info.getLatency(), 0);
    }

    public static boolean isKeyDown(MinecraftClient mc, int key) {
        if (key < 0) {
            return false;
        }
        if (key >= 1000) {
            return GLFW.glfwGetMouseButton((long)mc.getWindow().getHandle(), (int)(key - 1000)) == 1;
        }
        return InputUtil.isKeyPressed(mc.getWindow().getHandle(), key);
    }

    public static boolean isPhysicallyDown(MinecraftClient mc, KeyBinding mapping) {
        InputUtil.Key key;
        try {
            key = InputUtil.fromTranslationKey((String)mapping.getBoundKeyTranslationKey());
        }
        catch (IllegalArgumentException unknown) {
            return false;
        }
        int value = key.getCode();
        if (value < 0) {
            return false;
        }
        return switch (key.getCategory()) {
            case InputUtil.Type.MOUSE -> {
                if (GLFW.glfwGetMouseButton((long)mc.getWindow().getHandle(), (int)value) == 1) {
                    yield true;
                }
                yield false;
            }
            case InputUtil.Type.KEYSYM -> InputUtil.isKeyPressed(mc.getWindow().getHandle(), value);
            default -> false;
        };
    }
}

