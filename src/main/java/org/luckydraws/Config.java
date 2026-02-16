package org.luckydraws;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// 配置项集中管理，便于服务端统一调整抽取行为
@Mod.EventBusSubscriber(modid = Luckydraws.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue DRAW_TIME = BUILDER
            .comment("CN: 每日触发抽取的时间点（0-23999）。",
                    "EN: Daily draw trigger time in ticks (0-23999).")
            .defineInRange("drawTime", 1000, 0, 23999);

    private static final ForgeConfigSpec.DoubleValue DRAW_MEAN = BUILDER
            .comment("CN: 数量正态分布均值（1-64）。",
                    "EN: Mean value of gaussian count roll (1-64).")
            .defineInRange("drawMean", 32.0, 1.0, 64.0);

    private static final ForgeConfigSpec.DoubleValue DRAW_STD_DEV = BUILDER
            .comment("CN: 数量正态分布标准差（0-64）。",
                    "EN: Standard deviation of gaussian count roll (0-64).")
            .defineInRange("drawStdDev", 12.0, 0.0, 64.0);

    private static final ForgeConfigSpec.DoubleValue POTION_CHANCE = BUILDER
            .comment("CN: 随机药水效果概率（0-1）。",
                    "EN: Chance to apply a random potion effect (0-1).")
            .defineInRange("potionChance", 0.20, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue MOB_SPAWN_CHANCE = BUILDER
            .comment("CN: 随机生成生物概率（0-1）。",
                    "EN: Chance to spawn random mobs (0-1).")
            .defineInRange("mobSpawnChance", 0.01, 0.0, 1.0);

    private static final ForgeConfigSpec.IntValue MOB_SPAWN_MAX = BUILDER
            .comment("CN: 随机生成生物的最大数量（1-20）。",
                    "EN: Maximum number of spawned mobs (1-20).")
            .defineInRange("mobSpawnMax", 8, 1, 20);

    private static final ForgeConfigSpec.IntValue MOB_SIZE_BONUS_MAX = BUILDER
            .comment("CN: 可变体型生物的最大加成（0-20）。",
                    "EN: Maximum size bonus for scalable mobs (0-20).")
            .defineInRange("mobSizeBonusMax", 5, 0, 20);

    private static final ForgeConfigSpec.IntValue CREEPER_RADIUS_MAX = BUILDER
            .comment("CN: 苦力怕最大爆炸半径（1-128）。",
                    "EN: Maximum creeper explosion radius (1-128).")
            .defineInRange("creeperRadiusMax", 128, 1, 128);

    private static final ForgeConfigSpec.DoubleValue EXP_LAMBDA = BUILDER
            .comment("CN: 指数分布参数（0.1-5.0）。",
                    "EN: Lambda value for exponential roll (0.1-5.0).")
            .defineInRange("expLambda", 1.0, 0.1, 5.0);

    private static final ForgeConfigSpec.IntValue ENCHANT_LEVEL_MAX = BUILDER
            .comment("CN: 附魔等级上限（1-255）。",
                    "EN: Maximum enchant level cap (1-255).")
            .defineInRange("enchantLevelMax", 255, 1, 255);

    private static final ForgeConfigSpec.IntValue POTION_LEVEL_MAX = BUILDER
            .comment("CN: 药水等级上限（1-255）。",
                    "EN: Maximum potion level cap (1-255).")
            .defineInRange("potionLevelMax", 255, 1, 255);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> ITEM_BLACKLIST = BUILDER
            .comment("CN: 抽奖物品黑名单（按物品ID填写，例如 minecraft:bedrock）。",
                    "EN: Draw item blacklist by item id, e.g. minecraft:bedrock.")
            .defineListAllowEmpty(
                    List.of("drawItemBlacklist"),
                    List.of(),
                    Config::isValidItemIdValue);

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
    public static Set<String> drawItemBlacklist = Set.of();

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
        drawItemBlacklist = normalizeBlacklist(ITEM_BLACKLIST.get());
        LuckyDrawsEvents.invalidateItemCache();
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

    private static boolean isValidItemIdValue(Object value) {
        return value instanceof String;
    }

    private static Set<String> normalizeBlacklist(List<? extends String> source) {
        if (source.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new HashSet<>();
        for (String itemId : source) {
            if (itemId == null) {
                continue;
            }
            String cleaned = itemId.trim().toLowerCase(Locale.ROOT);
            if (!cleaned.isEmpty()) {
                normalized.add(cleaned);
            }
        }
        return Set.copyOf(normalized);
    }

    static ReloadResult reloadFromDisk() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(Luckydraws.MODID + "-common.toml");
        if (!Files.exists(configPath)) {
            return new ReloadResult(false, "配置文件不存在: " + configPath);
        }

        try (CommentedFileConfig file = CommentedFileConfig.builder(configPath).sync().build()) {
            file.load();

            int newDrawTime = readInt(file, "drawTime", DRAW_TIME.get(), 0, 23999);
            double newDrawMean = readDouble(file, "drawMean", DRAW_MEAN.get(), 1.0, 64.0);
            double newDrawStdDev = readDouble(file, "drawStdDev", DRAW_STD_DEV.get(), 0.0, 64.0);
            double newPotionChance = readDouble(file, "potionChance", POTION_CHANCE.get(), 0.0, 1.0);
            double newMobSpawnChance = readDouble(file, "mobSpawnChance", MOB_SPAWN_CHANCE.get(), 0.0, 1.0);
            int newMobSpawnMax = readInt(file, "mobSpawnMax", MOB_SPAWN_MAX.get(), 1, 20);
            int newMobSizeBonusMax = readInt(file, "mobSizeBonusMax", MOB_SIZE_BONUS_MAX.get(), 0, 20);
            int newCreeperRadiusMax = readInt(file, "creeperRadiusMax", CREEPER_RADIUS_MAX.get(), 1, 128);
            double newExpLambda = readDouble(file, "expLambda", EXP_LAMBDA.get(), 0.1, 5.0);
            int newEnchantLevelMax = readInt(file, "enchantLevelMax", ENCHANT_LEVEL_MAX.get(), 1, 255);
            int newPotionLevelMax = readInt(file, "potionLevelMax", POTION_LEVEL_MAX.get(), 1, 255);
            List<String> newBlacklistRaw = readStringList(file, "drawItemBlacklist");

            DRAW_TIME.set(newDrawTime);
            DRAW_MEAN.set(newDrawMean);
            DRAW_STD_DEV.set(newDrawStdDev);
            POTION_CHANCE.set(newPotionChance);
            MOB_SPAWN_CHANCE.set(newMobSpawnChance);
            MOB_SPAWN_MAX.set(newMobSpawnMax);
            MOB_SIZE_BONUS_MAX.set(newMobSizeBonusMax);
            CREEPER_RADIUS_MAX.set(newCreeperRadiusMax);
            EXP_LAMBDA.set(newExpLambda);
            ENCHANT_LEVEL_MAX.set(newEnchantLevelMax);
            POTION_LEVEL_MAX.set(newPotionLevelMax);
            ITEM_BLACKLIST.set(newBlacklistRaw);

            drawTime = newDrawTime;
            drawMean = newDrawMean;
            drawStdDev = newDrawStdDev;
            potionChance = newPotionChance;
            mobSpawnChance = newMobSpawnChance;
            mobSpawnMax = newMobSpawnMax;
            mobSizeBonusMax = newMobSizeBonusMax;
            creeperRadiusMax = newCreeperRadiusMax;
            expLambda = newExpLambda;
            enchantLevelMax = newEnchantLevelMax;
            potionLevelMax = newPotionLevelMax;
            drawItemBlacklist = normalizeBlacklist(newBlacklistRaw);
            LuckyDrawsEvents.invalidateItemCache();
            return new ReloadResult(true, "配置已热重载: " + configPath);
        } catch (Exception e) {
            return new ReloadResult(false, "热重载失败: " + e.getMessage());
        }
    }

    private static int readInt(CommentedFileConfig file, String key, int defaultValue, int min, int max) {
        Object value = file.get(key);
        if (!(value instanceof Number number)) {
            return defaultValue;
        }
        int parsed = number.intValue();
        if (parsed < min) {
            return min;
        }
        return Math.min(parsed, max);
    }

    private static double readDouble(CommentedFileConfig file, String key, double defaultValue, double min, double max) {
        Object value = file.get(key);
        if (!(value instanceof Number number)) {
            return defaultValue;
        }
        double parsed = number.doubleValue();
        if (parsed < min) {
            return min;
        }
        return Math.min(parsed, max);
    }

    private static List<String> readStringList(CommentedFileConfig file, String key) {
        Object value = file.get(key);
        if (!(value instanceof List<?> listValue)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : listValue) {
            if (item instanceof String stringValue) {
                result.add(stringValue);
            }
        }
        return result;
    }

    static final class ReloadResult {
        private final boolean success;
        private final String message;

        private ReloadResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        boolean success() {
            return success;
        }

        String message() {
            return message;
        }
    }
}
