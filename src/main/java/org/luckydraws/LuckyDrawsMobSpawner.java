package org.luckydraws;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class LuckyDrawsMobSpawner {
    private static List<Item> cachedItems;

    public static void maybeSpawnMob(ServerLevel level,
                                     Player player,
                                     double spawnChance,
                                     int maxSpawn,
                                     int maxSizeBonus,
                                     int maxCreeperRadius,
                                     double expLambda) {
        // 独立概率触发随机生成
        if (level.getRandom().nextDouble() >= spawnChance) {
            return;
        }
        List<EntityType<?>> types = new ArrayList<>(ForgeRegistries.ENTITY_TYPES.getValues());
        if (types.isEmpty()) {
            return;
        }
        int count = rollExponentialInt(level.getRandom(), maxSpawn, expLambda);
        for (int i = 0; i < count; i++) {
            Mob mob = createRandomMob(level, types);
            if (mob == null) {
                continue;
            }
            double dx = level.getRandom().nextInt(7) - 3;
            double dz = level.getRandom().nextInt(7) - 3;
            mob.moveTo(player.getX() + dx, player.getY(), player.getZ() + dz, level.getRandom().nextFloat() * 360.0f, 0.0f);
            // 应用所有随机 NBT/属性规则
            applyMobTags(level, mob, expLambda, maxSizeBonus, maxCreeperRadius);
            level.addFreshEntity(mob);
        }
        Component message = Component.literal("附近出现了神秘生物！");
        for (Player online : level.getServer().getPlayerList().getPlayers()) {
            online.sendSystemMessage(message);
        }
    }

    private static Mob createRandomMob(ServerLevel level, List<EntityType<?>> types) {
        for (int attempts = 0; attempts < 10; attempts++) {
            EntityType<?> type = types.get(level.getRandom().nextInt(types.size()));
            if (type == EntityType.PLAYER) {
                continue;
            }
            Entity entity = type.create(level);
            if (entity instanceof Mob mob) {
                return mob;
            }
        }
        return null;
    }

    private static void applyMobTags(ServerLevel level, Mob mob, double expLambda, int maxSizeBonus, int maxCreeperRadius) {
        RandomSource random = level.getRandom();
        // 不被自然清除
        mob.setPersistenceRequired();

        AttributeInstance maxHealthAttr = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttr != null) {
            double base = maxHealthAttr.getBaseValue();
            // 生命值在基础上叠加指数分布
            double bonus = rollExponentialUnit(random, expLambda) * 40.0;
            maxHealthAttr.setBaseValue(base + bonus);
            mob.setHealth((float) maxHealthAttr.getBaseValue());
        }

        // 属性/状态/装备/骑乘/视觉等统一处理
        applyMobAttributes(random, mob, expLambda);
        applyMobEffects(random, mob, expLambda);
        applyMobEquipment(random, mob);
        applyMobArmor(random, mob, expLambda);
        applyMobPassengers(level, random, mob, expLambda, maxSizeBonus, maxCreeperRadius);
        applyMobFire(random, mob);
        applyMobSize(random, mob, expLambda, maxSizeBonus);
        applyCreeperSpecial(random, mob, expLambda, maxCreeperRadius);
        applyVariant(random, mob);
    }

    private static void applyMobAttributes(RandomSource random, Mob mob, double expLambda) {
        // 随机叠加基础属性
        MobAttributeRange[] ranges = new MobAttributeRange[] {
                new MobAttributeRange(Attributes.MAX_HEALTH, 40.0),
                new MobAttributeRange(Attributes.MOVEMENT_SPEED, 0.4),
                new MobAttributeRange(Attributes.ATTACK_DAMAGE, 12.0),
                new MobAttributeRange(Attributes.FOLLOW_RANGE, 32.0),
                new MobAttributeRange(Attributes.ARMOR, 10.0),
                new MobAttributeRange(Attributes.ARMOR_TOUGHNESS, 6.0),
                new MobAttributeRange(Attributes.KNOCKBACK_RESISTANCE, 0.6)
        };
        int count = 1 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            MobAttributeRange range = ranges[random.nextInt(ranges.length)];
            AttributeInstance instance = mob.getAttribute(range.attribute());
            if (instance == null) {
                continue;
            }
            double bonus = rollExponentialUnit(random, expLambda) * range.maxBonus();
            instance.setBaseValue(instance.getBaseValue() + bonus);
        }
    }

    private static void applyMobEffects(RandomSource random, Mob mob, double expLambda) {
        // 随机状态效果
        List<MobEffect> effects = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getValues());
        if (effects.isEmpty()) {
            return;
        }
        MobEffect effect = effects.get(random.nextInt(effects.size()));
        int amplifier = rollExponentialInt(random, 4, expLambda) - 1;
        int duration = 200 + random.nextInt(1200);
        mob.addEffect(new MobEffectInstance(effect, duration, amplifier));
    }

    private static void applyMobEquipment(RandomSource random, Mob mob) {
        // 主手必定，副手 50%
        Item mainhand = pickRandomItem(random);
        if (mainhand != null) {
            ItemStack stack = new ItemStack(mainhand, 1);
            mob.setItemSlot(EquipmentSlot.MAINHAND, stack);
            mob.setDropChance(EquipmentSlot.MAINHAND, 1.0f);
        }
        if (random.nextDouble() < 0.5) {
            Item offhand = pickRandomItem(random);
            if (offhand != null) {
                ItemStack stack = new ItemStack(offhand, 1);
                mob.setItemSlot(EquipmentSlot.OFFHAND, stack);
                mob.setDropChance(EquipmentSlot.OFFHAND, 1.0f);
            }
        }
    }

    private static void applyMobArmor(RandomSource random, Mob mob, double expLambda) {
        // 50% 概率穿全套护甲
        if (random.nextDouble() >= 0.5) {
            return;
        }
        ArmorSet set = rollArmorSet(random);
        ItemStack boots = new ItemStack(set.boots(), 1);
        ItemStack leggings = new ItemStack(set.leggings(), 1);
        ItemStack chest = new ItemStack(set.chest(), 1);
        ItemStack helmet = new ItemStack(set.helmet(), 1);
        applyRandomEnchantment(random, boots, expLambda);
        applyRandomEnchantment(random, leggings, expLambda);
        applyRandomEnchantment(random, chest, expLambda);
        applyRandomEnchantment(random, helmet, expLambda);
        mob.setItemSlot(EquipmentSlot.FEET, boots);
        mob.setItemSlot(EquipmentSlot.LEGS, leggings);
        mob.setItemSlot(EquipmentSlot.CHEST, chest);
        mob.setItemSlot(EquipmentSlot.HEAD, helmet);
        mob.setDropChance(EquipmentSlot.FEET, 1.0f);
        mob.setDropChance(EquipmentSlot.LEGS, 1.0f);
        mob.setDropChance(EquipmentSlot.CHEST, 1.0f);
        mob.setDropChance(EquipmentSlot.HEAD, 1.0f);
    }

    private static ArmorSet rollArmorSet(RandomSource random) {
        // 金/锁链/铁/钻石/下界合金 权重 60/40/30/20/10
        int[] weights = new int[] { 60, 40, 30, 20, 10 };
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        int roll = random.nextInt(total);
        int running = 0;
        for (int i = 0; i < weights.length; i++) {
            running += weights[i];
            if (roll < running) {
                return switch (i) {
                    case 0 -> ArmorSet.gold();
                    case 1 -> ArmorSet.chain();
                    case 2 -> ArmorSet.iron();
                    case 3 -> ArmorSet.diamond();
                    default -> ArmorSet.netherite();
                };
            }
        }
        return ArmorSet.gold();
    }

    private static void applyMobPassengers(ServerLevel level, RandomSource random, Mob mob, double expLambda, int maxSizeBonus, int maxCreeperRadius) {
        // 30% 概率生成骑乘实体
        if (random.nextDouble() >= 0.3) {
            return;
        }
        Mob passenger = createRandomMob(level, new ArrayList<>(ForgeRegistries.ENTITY_TYPES.getValues()));
        if (passenger == null) {
            return;
        }
        passenger.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
        applyMobTags(level, passenger, expLambda, maxSizeBonus, maxCreeperRadius);
        level.addFreshEntity(passenger);
        passenger.startRiding(mob, true);
    }

    private static void applyMobFire(RandomSource random, Mob mob) {
        // 10% 负火焰时间，20% 短时燃烧
        if (random.nextDouble() < 0.1) {
            mob.setRemainingFireTicks(-20);
        } else if (random.nextDouble() < 0.2) {
            mob.setSecondsOnFire(5 + random.nextInt(10));
        }
    }

    private static void applyMobSize(RandomSource random, Mob mob, double expLambda, int maxSizeBonus) {
        // 可变体型生物尝试变大
        int bonus = rollExponentialInt(random, maxSizeBonus, expLambda) - 1;
        if (bonus <= 0) {
            return;
        }
        if (mob instanceof Slime slime) {
            slime.setSize(slime.getSize() + bonus, true);
        } else if (mob instanceof MagmaCube magma) {
            magma.setSize(magma.getSize() + bonus, true);
        } else if (mob.getType() == EntityType.PHANTOM) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Size", bonus + 1);
            mob.readAdditionalSaveData(tag);
        }
    }

    private static void applyCreeperSpecial(RandomSource random, Mob mob, double expLambda, int maxCreeperRadius) {
        // 苦力怕强制充能与爆炸半径
        if (!(mob instanceof Creeper creeper)) {
            return;
        }
        int radius = rollExponentialInt(random, maxCreeperRadius, expLambda);
        CompoundTag tag = new CompoundTag();
        tag.putInt("ExplosionRadius", radius);
        tag.putBoolean("powered", true);
        creeper.readAdditionalSaveData(tag);
    }

    private static void applyVariant(RandomSource random, Mob mob) {
        // 5% 概率改变外观变种
        if (random.nextDouble() >= 0.05) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("Variant", random.nextInt(6));
        mob.readAdditionalSaveData(tag);
    }

    private static Item pickRandomItem(RandomSource random) {
        // 物品表缓存，避免频繁遍历
        if (cachedItems == null) {
            cachedItems = new ArrayList<>(ForgeRegistries.ITEMS.getValues());
            cachedItems.remove(Items.AIR);
        }
        if (cachedItems.isEmpty()) {
            return null;
        }
        return cachedItems.get(random.nextInt(cachedItems.size()));
    }

    private static void applyRandomEnchantment(RandomSource random, ItemStack stack, double expLambda) {
        // 随机附魔 + 指数分布等级
        List<Enchantment> enchantments = new ArrayList<>(ForgeRegistries.ENCHANTMENTS.getValues());
        if (enchantments.isEmpty()) {
            return;
        }
        Enchantment enchantment = enchantments.get(random.nextInt(enchantments.size()));
        int maxLevel = Math.max(1, enchantment.getMaxLevel());
        int level = rollExponentialInt(random, maxLevel, expLambda);
        stack.enchant(enchantment, level);
    }

    private static double rollExponentialUnit(RandomSource random, double lambda) {
        // 指数分布映射到 [0,1]
        double u = random.nextDouble();
        double limit = 1.0 - Math.exp(-lambda);
        double x = -Math.log(1.0 - u * limit) / lambda;
        return Mth.clamp(x, 0.0, 1.0);
    }

    private static int rollExponentialInt(RandomSource random, int max, double lambda) {
        // 指数分布映射到 [1,max]
        double x = rollExponentialUnit(random, lambda);
        if (max <= 1) {
            return 1;
        }
        int value = 1 + (int) Math.floor(x * (double) (max - 1));
        return Mth.clamp(value, 1, max);
    }

    private record ArmorSet(Item boots, Item leggings, Item chest, Item helmet) {
        private static ArmorSet gold() {
            return new ArmorSet(Items.GOLDEN_BOOTS, Items.GOLDEN_LEGGINGS, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_HELMET);
        }

        private static ArmorSet chain() {
            return new ArmorSet(Items.CHAINMAIL_BOOTS, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_HELMET);
        }

        private static ArmorSet iron() {
            return new ArmorSet(Items.IRON_BOOTS, Items.IRON_LEGGINGS, Items.IRON_CHESTPLATE, Items.IRON_HELMET);
        }

        private static ArmorSet diamond() {
            return new ArmorSet(Items.DIAMOND_BOOTS, Items.DIAMOND_LEGGINGS, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_HELMET);
        }

        private static ArmorSet netherite() {
            return new ArmorSet(Items.NETHERITE_BOOTS, Items.NETHERITE_LEGGINGS, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_HELMET);
        }
    }

    private record MobAttributeRange(Attribute attribute, double maxBonus) {}
}
