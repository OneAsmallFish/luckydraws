package org.luckydraws;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// 配置项集中管理，便于服务端统一调整抽取行为
@Mod.EventBusSubscriber(modid = Luckydraws.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue DRAW_TIME = BUILDER.comment("每日触发抽取的时间点(0-23999)。").defineInRange("drawTime", 1000, 0, 23999);

    private static final ForgeConfigSpec.DoubleValue DRAW_MEAN = BUILDER.comment("数量正态分布均值(1-64)。").defineInRange("drawMean", 32.0, 1.0, 64.0);

    private static final ForgeConfigSpec.DoubleValue DRAW_STD_DEV = BUILDER.comment("数量正态分布标准差(0-64)。").defineInRange("drawStdDev", 12.0, 0.0, 64.0);

    private static final ForgeConfigSpec.DoubleValue POTION_CHANCE = BUILDER.comment("随机药水效果概率(0-1)。").defineInRange("potionChance", 0.20, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue MOB_SPAWN_CHANCE = BUILDER.comment("随机生成生物概率(0-1)。").defineInRange("mobSpawnChance", 0.01, 0.0, 1.0);

    private static final ForgeConfigSpec.IntValue MOB_SPAWN_MAX = BUILDER.comment("随机生成生物的最大数量(1-20)。").defineInRange("mobSpawnMax", 8, 1, 20);

    private static final ForgeConfigSpec.IntValue MOB_SIZE_BONUS_MAX = BUILDER.comment("可变体型生物的最大加成(0-20)。").defineInRange("mobSizeBonusMax", 5, 0, 20);

    private static final ForgeConfigSpec.IntValue CREEPER_RADIUS_MAX = BUILDER.comment("苦力怕最大爆炸半径(1-128)。").defineInRange("creeperRadiusMax", 128, 1, 128);

    private static final ForgeConfigSpec.DoubleValue EXP_LAMBDA = BUILDER.comment("指数分布参数(0.1-5.0)。").defineInRange("expLambda", 1.0, 0.1, 5.0);

    private static final ForgeConfigSpec.IntValue ENCHANT_LEVEL_MAX = BUILDER.comment("附魔等级上限(1-255)。").defineInRange("enchantLevelMax", 255, 1, 255);

    private static final ForgeConfigSpec.IntValue POTION_LEVEL_MAX = BUILDER.comment("药水等级上限(1-255)。").defineInRange("potionLevelMax", 255, 1, 255);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int drawTime;
    public static double drawMean;
    public static double drawStdDev;
    public static double potionChance;
    public static double mobSpawnChance;
    public static int mobSpawnMax;
    public static int mobSizeBonusMax;
    public static int creeperRadiusMax;
    public static double expLambda;
    public static int enchantLevelMax;
    public static int potionLevelMax;

    // 由 Forge 在配置装载时写入
    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        drawTime = DRAW_TIME.get();
        drawMean = DRAW_MEAN.get();
        drawStdDev = DRAW_STD_DEV.get();
        potionChance = POTION_CHANCE.get();
        mobSpawnChance = MOB_SPAWN_CHANCE.get();
        mobSpawnMax = MOB_SPAWN_MAX.get();
        mobSizeBonusMax = MOB_SIZE_BONUS_MAX.get();
        creeperRadiusMax = CREEPER_RADIUS_MAX.get();
        expLambda = EXP_LAMBDA.get();
        enchantLevelMax = ENCHANT_LEVEL_MAX.get();
        potionLevelMax = POTION_LEVEL_MAX.get();
    }

    static void setDrawTime(int value) {
        DRAW_TIME.set(value);
        drawTime = value;
    }

    static void setDrawMean(double value) {
        DRAW_MEAN.set(value);
        drawMean = value;
    }

    static void setDrawStdDev(double value) {
        DRAW_STD_DEV.set(value);
        drawStdDev = value;
    }

    static void setPotionChance(double value) {
        POTION_CHANCE.set(value);
        potionChance = value;
    }

    static void setMobSpawnChance(double value) {
        MOB_SPAWN_CHANCE.set(value);
        mobSpawnChance = value;
    }

    static void setMobSpawnMax(int value) {
        MOB_SPAWN_MAX.set(value);
        mobSpawnMax = value;
    }

    static void setMobSizeBonusMax(int value) {
        MOB_SIZE_BONUS_MAX.set(value);
        mobSizeBonusMax = value;
    }

    static void setCreeperRadiusMax(int value) {
        CREEPER_RADIUS_MAX.set(value);
        creeperRadiusMax = value;
    }

    static void setExpLambda(double value) {
        EXP_LAMBDA.set(value);
        expLambda = value;
    }

    static void setEnchantLevelMax(int value) {
        ENCHANT_LEVEL_MAX.set(value);
        enchantLevelMax = value;
    }

    static void setPotionLevelMax(int value) {
        POTION_LEVEL_MAX.set(value);
        potionLevelMax = value;
    }
}
