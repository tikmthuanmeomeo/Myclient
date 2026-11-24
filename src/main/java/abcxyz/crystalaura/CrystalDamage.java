/*
 * Ported from Anubis Client (GPL-3.0-only, (c) 4ldenz), see THIRD_PARTY_NOTICES/Anubis-LICENSE.txt.
 * Changed to run on Minecraft 1.21.4 inside the abcxyz client.
 */
package abcxyz.crystalaura;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.Difficulty;
import net.minecraft.entity.DamageUtil;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.block.ShapeContext;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.component.DataComponentTypes;

public final class CrystalDamage {
    private static final double REACH = 12.0;
    private static final float SELF_SCALE = 1.15f;
    private static final float SELF_MARGIN = 2.0f;
    private static final EquipmentSlot[] ARMOR = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private CrystalDamage() {
    }

    public static float estimate(Vec3d center, LivingEntity entity) {
        return new Victim(entity, Vec3d.ZERO).damage(center);
    }

    public static float estimate(Vec3d center, LivingEntity entity, float power, BlockPos ignored) {
        return new Victim(entity, Vec3d.ZERO).damage(center, power, ignored);
    }

    public static float health(LivingEntity entity) {
        return entity.getHealth() + entity.getAbsorptionAmount();
    }

    public static boolean endangers(PlayerEntity self, float damage) {
        return !self.isCreative() && damage * 1.15f + 2.0f >= CrystalDamage.health((LivingEntity)self);
    }

    private static float falloff(double exposure) {
        return CrystalDamage.falloff(exposure, 12.0);
    }

    private static float falloff(double exposure, double diameter) {
        return (float)((exposure * exposure + exposure) / 2.0 * 7.0 * diameter + 1.0);
    }

    private static float protection(LivingEntity entity) {
        Registry enchantments = entity.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        int total = 0;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = entity.getEquippedStack(slot);
            if (stack.isEmpty()) continue;
            total += CrystalDamage.level((Registry<Enchantment>)enchantments, (RegistryKey<Enchantment>)Enchantments.PROTECTION, stack) + 2 * CrystalDamage.level((Registry<Enchantment>)enchantments, (RegistryKey<Enchantment>)Enchantments.BLAST_PROTECTION, stack);
        }
        return total;
    }

    private static int level(Registry<Enchantment> enchantments, RegistryKey<Enchantment> key, ItemStack stack) {
        return enchantments.getOptional(key).map(holder -> EnchantmentHelper.getLevel((RegistryEntry)holder, (ItemStack)stack)).orElse(0);
    }

        public static final class Victim {
        private final LivingEntity entity;
        private final Vec3d position;
        private final Box box;
        private final DamageSource source;
        private final float armor;
        private final float toughness;
        private final float protection;
        private final float resistance;
        private final Map<Vec3d, Float> exposures = new HashMap<Vec3d, Float>();
        private final Map<Blast, Float> blastExposures = new HashMap<Blast, Float>();

        public Victim(LivingEntity entity, Vec3d offset) {
            this.entity = entity;
            this.position = entity.getPos().add(offset);
            this.box = entity.getBoundingBox().offset(offset);
            this.source = entity.getDamageSources().explosion(null, null);
            this.armor = entity.getArmor();
            this.toughness = (float)entity.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
            this.protection = CrystalDamage.protection(entity);
            StatusEffectInstance effect = entity.getStatusEffect(StatusEffects.RESISTANCE);
            this.resistance = effect == null ? 1.0f : (float)Math.max(25 - (effect.getAmplifier() + 1) * 5, 0) / 25.0f;
        }

        public LivingEntity entity() {
            return this.entity;
        }

        public float damage(Vec3d center) {
            return this.damage(center, false);
        }

        public float damage(Vec3d center, boolean baseToCome) {
            return this.reduce(this.raw(center, baseToCome));
        }

        public float raw(Vec3d center, boolean baseToCome) {
            double distance = Math.sqrt(this.position.squaredDistanceTo(center)) / 12.0;
            if (distance > 1.0) {
                return 0.0f;
            }
            float damage = this.scaled(CrystalDamage.falloff((1.0 - distance) * (double)this.exposure(center, baseToCome)));
            return damage - this.blocked(center, damage);
        }

        public float reduce(float raw) {
            float damage = DamageUtil.getDamageLeft((LivingEntity)this.entity, (float)raw, (DamageSource)this.source, (float)this.armor, (float)this.toughness);
            return DamageUtil.getInflictedDamage((float)(damage * this.resistance), (float)this.protection);
        }

        public float damage(Vec3d center, float power, BlockPos ignored) {
            return this.reduce(this.raw(center, power, ignored));
        }

        public float raw(Vec3d center, float power, BlockPos ignored) {
            double diameter = (double)power * 2.0;
            double distance = Math.sqrt(this.position.squaredDistanceTo(center)) / diameter;
            if (distance > 1.0) {
                return 0.0f;
            }
            float damage = this.scaled(CrystalDamage.falloff((1.0 - distance) * (double)this.blastExposure(center, ignored), diameter));
            return damage - this.blocked(center, damage);
        }

        private float blocked(Vec3d center, float damage) {
            return 0.0f; // shield blocking is not modelled on 1.21.4
        }

        public float upperBound(Vec3d center) {
            double distance = Math.sqrt(this.position.squaredDistanceTo(center)) / 12.0;
            return distance > 1.0 ? 0.0f : this.reduce(this.scaled(CrystalDamage.falloff(1.0 - distance)));
        }

        public float upperBound(Vec3d center, float power) {
            double diameter = (double)power * 2.0;
            double distance = Math.sqrt(this.position.squaredDistanceTo(center)) / diameter;
            return distance > 1.0 ? 0.0f : this.reduce(this.scaled(CrystalDamage.falloff(1.0 - distance, diameter)));
        }

        private float exposure(Vec3d center, boolean baseToCome) {
            Float known = this.exposures.get(center);
            if (known != null) {
                return known.floatValue();
            }
            Box base = baseToCome ? new Box(BlockPos.ofFloored((double)center.x, (double)(center.y - 0.5), (double)center.z)) : null;
            float seen = this.openAir(center, null) && (base == null || !this.hull(center).intersects(base)) ? 1.0f : this.seenPercent(center, base, null);
            this.exposures.put(center, Float.valueOf(seen));
            return seen;
        }

        private float blastExposure(Vec3d center, BlockPos ignored) {
            Blast key = new Blast(center, ignored);
            Float known = this.blastExposures.get(key);
            if (known != null) {
                return known.floatValue();
            }
            float seen = this.openAir(center, ignored) ? 1.0f : this.seenPercent(center, null, ignored);
            this.blastExposures.put(key, Float.valueOf(seen));
            return seen;
        }

        private Box hull(Vec3d center) {
            return this.box.union(new Box(center, center));
        }

        private boolean openAir(Vec3d center, BlockPos ignored) {
            World level = this.entity.getWorld();
            ShapeContext context = ShapeContext.of((Entity)this.entity);
            int minX = MathHelper.floor((double)Math.min(this.box.minX, center.x));
            int minY = MathHelper.floor((double)Math.min(this.box.minY, center.y));
            int minZ = MathHelper.floor((double)Math.min(this.box.minZ, center.z));
            int maxX = MathHelper.floor((double)Math.max(this.box.maxX, center.x));
            int maxY = MathHelper.floor((double)Math.max(this.box.maxY, center.y));
            int maxZ = MathHelper.floor((double)Math.max(this.box.maxZ, center.z));
            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (int x = minX; x <= maxX; ++x) {
                for (int y = minY; y <= maxY; ++y) {
                    for (int z = minZ; z <= maxZ; ++z) {
                        pos.set(x, y, z);
                        if (pos.equals((Object)ignored) || level.getBlockState((BlockPos)pos).getCollisionShape((BlockView)level, (BlockPos)pos, context).isEmpty()) continue;
                        return false;
                    }
                }
            }
            return true;
        }

        private float seenPercent(Vec3d center, Box base, BlockPos ignored) {
            double stepX = 1.0 / ((this.box.maxX - this.box.minX) * 2.0 + 1.0);
            double stepY = 1.0 / ((this.box.maxY - this.box.minY) * 2.0 + 1.0);
            double stepZ = 1.0 / ((this.box.maxZ - this.box.minZ) * 2.0 + 1.0);
            double offsetX = (1.0 - Math.floor(1.0 / stepX) * stepX) / 2.0;
            double offsetZ = (1.0 - Math.floor(1.0 / stepZ) * stepZ) / 2.0;
            if (stepX < 0.0 || stepY < 0.0 || stepZ < 0.0) {
                return 0.0f;
            }
            World level = this.entity.getWorld();
            int seen = 0;
            int total = 0;
            for (double fx = 0.0; fx <= 1.0; fx += stepX) {
                for (double fy = 0.0; fy <= 1.0; fy += stepY) {
                    for (double fz = 0.0; fz <= 1.0; fz += stepZ) {
                        Vec3d from = new Vec3d(MathHelper.lerp((double)fx, (double)this.box.minX, (double)this.box.maxX) + offsetX, MathHelper.lerp((double)fy, (double)this.box.minY, (double)this.box.maxY), MathHelper.lerp((double)fz, (double)this.box.minZ, (double)this.box.maxZ) + offsetZ);
                        BlockHitResult hit = level.raycast(new RaycastContext(from, center, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, (Entity)this.entity));
                        if (!(hit.getType() != HitResult.Type.MISS && !hit.getBlockPos().equals((Object)ignored) || base != null && Victim.throughBlock(base, from, center))) {
                            ++seen;
                        }
                        ++total;
                    }
                }
            }
            return (float)seen / (float)total;
        }

        private static boolean throughBlock(Box block, Vec3d from, Vec3d to) {
            Vec3d end = new Vec3d(MathHelper.lerp((double)1.0E-7, (double)to.x, (double)from.x), MathHelper.lerp((double)1.0E-7, (double)to.y, (double)from.y), MathHelper.lerp((double)1.0E-7, (double)to.z, (double)from.z));
            return block.contains(from) || block.raycast(from, end).isPresent();
        }

        private float scaled(float damage) {
            if (!(this.entity instanceof PlayerEntity)) {
                return damage;
            }
            return switch (this.entity.getWorld().getDifficulty()) {
                default -> throw new MatchException(null, null);
                case Difficulty.PEACEFUL -> 0.0f;
                case Difficulty.EASY -> Math.min(damage / 2.0f + 1.0f, damage);
                case Difficulty.NORMAL -> damage;
                case Difficulty.HARD -> damage * 1.5f;
            };
        }
    }

        private record Blast(Vec3d center, BlockPos ignored) {
    }
}

