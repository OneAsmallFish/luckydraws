package org.luckydraws;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.ChatFormatting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = Luckydraws.MODID)
public class LuckyDrawsEvents {
    private static final String DATA_NAME = "luckydraws_data";
    private static List<Item> cachedItems;
    private static final String REROLL_COMMAND = "/luckydraws reroll";
    private static final double ENCHANT_CHANCE = 0.10;
    private static final String LUCKY_TAG = "luckydraws_tag";
    private static final String LUCKY_TAG_SEED = "luckydraws_seed";
    private static final int LOW_QUALITY_LIMIT = 3;
    private static final int HISTORY_LIMIT = 7;
    private static final double THUNDER_BONUS_CHANCE = 0.10;
    private static final double FULL_MOON_ATTRIBUTE_BONUS = 1.5;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        // 使用主世界时间作为全服统一时间基准
        long dayTime = overworld.getDayTime();
        long currentDay = dayTime / 24000L;
        long timeOfDay = dayTime % 24000L;

        LuckyDrawsSavedData data = LuckyDrawsSavedData.get(overworld);
        boolean dirty = false;
        if (overworld.isThundering() && !data.thunderPending) {
            data.thunderPending = true;
            dirty = true;
        }
        if (isFullMoonNight(overworld, timeOfDay) && !data.fullMoonPending) {
            data.fullMoonPending = true;
            dirty = true;
        }
        if (dirty) {
            data.setDirty();
        }

        if (timeOfDay < Config.drawTime) {
            return;
        }

        if (data.lastDrawDay >= currentDay) {
            return;
        }

        boolean thunderBonus = data.thunderPending;
        boolean fullMoonBonus = data.fullMoonPending;

        // 每位玩家独立抽取物品与数量
        for (Player player : server.getPlayerList().getPlayers()) {
            boolean forceSpecial = data.getLowQualityStreak(player.getUUID()) >= LOW_QUALITY_LIMIT;
            ItemStack stack = rollStack(overworld.getRandom(), forceSpecial, thunderBonus, fullMoonBonus, player);
            if (stack.isEmpty()) {
                continue;
            }

            // 先尝试放入背包，满了则掉落在玩家位置
            grantToPlayer(player, stack);
            player.sendSystemMessage(buildMessage(player, stack));
            data.recordHistory(player, stack, "draw");
            data.updateLowQualityStreak(player.getUUID(), isSpecial(stack));

            // 独立抽取：随机药水效果
            maybeApplyPotion(overworld, player);
            if (data.mobSpawnEnabled) {
                // 独立触发：随机生成附近生物
                LuckyDrawsMobSpawner.maybeSpawnMob(
                        overworld,
                        player,
                        Config.mobSpawnChance,
                        Config.mobSpawnMax,
                        Config.mobSizeBonusMax,
                        Config.creeperRadiusMax,
                        Config.expLambda);
            }
        }

        data.lastDrawDay = currentDay;
        data.thunderPending = false;
        data.fullMoonPending = false;
        data.rerollUsedByDay.clear();
        data.setDirty();

        for (Player player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(buildDailyAnnouncement(player, thunderBonus, fullMoonBonus));
        }
    }

    // 缓存物品表，避免每 tick 枚举注册表
    private static Item pickRandomItem(RandomSource random) {
        if (cachedItems == null) {
            cachedItems = new ArrayList<>(ForgeRegistries.ITEMS.getValues());
            cachedItems.remove(Items.AIR);
            Set<String> blacklist = Config.drawItemBlacklist;
            if (!blacklist.isEmpty()) {
                cachedItems.removeIf(item -> {
                    if (item == null) {
                        return true;
                    }
                    if (item == Items.AIR) {
                        return true;
                    }
                    return ForgeRegistries.ITEMS.getKey(item) != null
                            && blacklist.contains(ForgeRegistries.ITEMS.getKey(item).toString());
                });
            }
        }
        if (cachedItems.isEmpty()) {
            return null;
        }
        return cachedItems.get(random.nextInt(cachedItems.size()));
    }

    static void invalidateItemCache() {
        cachedItems = null;
    }

    // 正态分布抽取数量，并限制在 1-64
    private static int rollCount(RandomSource random) {
        double gaussian = random.nextGaussian() * Config.drawStdDev + Config.drawMean;
        return Mth.clamp((int) Math.round(gaussian), 1, 64);
    }

    static ItemStack rollStack(RandomSource random, boolean forceSpecial, boolean thunderBonus, boolean fullMoonBonus, Player player) {
        Item item = pickRandomItem(random);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        int count = rollCount(random);
        ItemStack stack = new ItemStack(item, count);
        double chance = ENCHANT_CHANCE + (thunderBonus ? THUNDER_BONUS_CHANCE : 0.0);
        if (forceSpecial || random.nextDouble() < chance) {
            // 特殊抽取：随机附魔 + 标签
            applyRandomEnchantment(random, stack);
            applyLuckyTag(random, stack, fullMoonBonus, player);
        }
        return stack;
    }

    static void grantToPlayer(Player player, ItemStack stack) {
        ItemStack grantStack = stack.copy();
        player.getInventory().add(grantStack);
        if (!grantStack.isEmpty()) {
            player.drop(grantStack, false);
        }
    }

    static Component buildMessage(Player player, ItemStack stack) {
        MutableComponent base = Component.literal(localizedText(player, "幸运抽取: ", "Lucky Draw: "))
                .append(Component.literal(String.valueOf(stack.getCount())))
                .append(Component.literal(" x "))
                .append(formatDisplayName(stack));
        MutableComponent reroll = Component.literal(localizedText(player, " 不满意？", " Not satisfied? "))
                .append(Component.literal(localizedText(player, "再抽一次", "Reroll"))
                        .setStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, REROLL_COMMAND))
                                .withColor(ChatFormatting.AQUA)
                                .withUnderlined(true)));
        return base.append(reroll);
    }

    private static Component buildDailyAnnouncement(Player player, boolean thunderBonus, boolean fullMoonBonus) {
        MutableComponent announcement = Component.literal(localizedText(player, "今日抽取已完成", "Today's draw is complete"));
        if (thunderBonus || fullMoonBonus) {
            StringBuilder bonus = new StringBuilder(" (");
            if (thunderBonus) {
                bonus.append(localizedText(player, "雷雨加成", "Thunder bonus"));
            }
            if (fullMoonBonus) {
                if (thunderBonus) {
                    bonus.append(" + ");
                }
                bonus.append(localizedText(player, "满月加成", "Full moon bonus"));
            }
            bonus.append(")");
            announcement = announcement.append(Component.literal(bonus.toString()));
        }
        return announcement;
    }

    private static String localizedText(Player player, String zh, String en) {
        return isChinese(player) ? zh : en;
    }

    private static boolean isChinese(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            String language = serverPlayer.getLanguage();
            return language != null && language.toLowerCase(Locale.ROOT).startsWith("zh");
        }
        return false;
    }

    private static Component formatDisplayName(ItemStack stack) {
        MutableComponent name = stack.getHoverName().copy();
        if (stack.isEnchanted() || (stack.hasTag() && stack.getTag().getBoolean(LUCKY_TAG))) {
            name.withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(true));
        }
        return name;
    }

    private static void applyRandomEnchantment(RandomSource random, ItemStack stack) {
        List<Enchantment> enchantments = new ArrayList<>(ForgeRegistries.ENCHANTMENTS.getValues());
        if (enchantments.isEmpty()) {
            return;
        }
        Enchantment enchantment = enchantments.get(random.nextInt(enchantments.size()));
        int level = rollExponentialInt(random, Config.enchantLevelMax, Config.expLambda);
        stack.enchant(enchantment, level);
    }

    private static void applyLuckyTag(RandomSource random, ItemStack stack, boolean fullMoonBonus, Player player) {
        stack.getOrCreateTag().putBoolean(LUCKY_TAG, true);
        stack.getOrCreateTag().putInt(LUCKY_TAG_SEED, random.nextInt());

        applyDisplayTag(random, stack, player);
        applyAttributeTag(random, stack, fullMoonBonus);
    }

    private static void applyDisplayTag(RandomSource random, ItemStack stack, Player player) {
        CompoundTag display = stack.getOrCreateTag().getCompound("display");
        Component original = stack.getHoverName().copy()
                .withStyle(style -> style.withColor(ChatFormatting.AQUA).withItalic(true));
        ListTag lore = new ListTag();
        for (String line : rollLore(random, player)) {
            lore.add(StringTag.valueOf(line));
        }
        display.putString("Name", Component.Serializer.toJson(original));
        display.put("Lore", lore);
        display.putInt("color", random.nextInt(0x1000000));
        stack.getOrCreateTag().put("display", display);
    }

    private static void applyAttributeTag(RandomSource random, ItemStack stack, boolean fullMoonBonus) {
        ListTag modifiers = new ListTag();
        CompoundTag modifier = new CompoundTag();
        modifier.putString("Slot", "mainhand");
        AttributeRange range = rollAttribute(random);
        modifier.putString("AttributeName", range.name);
        modifier.putDouble("Amount", rollAttributeAmount(random, range.min, range.max, fullMoonBonus));
        modifier.putInt("Operation", 0);
        modifier.putIntArray("UUID", new int[] { random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt() });
        modifiers.add(modifier);
        stack.getOrCreateTag().put("AttributeModifiers", modifiers);
    }

    private static String[] rollLore(RandomSource random, Player player) {
        if (isChinese(player)) {
            String[][] loreOptions = new String[][] {
                    new String[] {
                            "{\"text\":\"表面上普普通通\",\"color\":\"yellow\"}",
                            "{\"text\":\"但是蕴含着无穷的力量\",\"color\":\"yellow\"}"
                    },
                    new String[] {
                            "{\"text\":\"在某个夜晚被发现\",\"color\":\"aqua\"}",
                            "{\"text\":\"仍散发着微弱光芒\",\"color\":\"aqua\"}"
                    },
                    new String[] {
                            "{\"text\":\"轻若鸿毛\",\"color\":\"gray\"}",
                            "{\"text\":\"却重似千钧\",\"color\":\"gray\"}"
                    }
            };
            return loreOptions[random.nextInt(loreOptions.length)];
        }

        String[][] loreOptions = new String[][] {
                new String[] {
                        "{\"text\":\"Looks ordinary at first glance\",\"color\":\"yellow\"}",
                        "{\"text\":\"Yet it hides endless power\",\"color\":\"yellow\"}"
                },
                new String[] {
                        "{\"text\":\"Discovered in a distant night\",\"color\":\"aqua\"}",
                        "{\"text\":\"Still glows with a faint light\",\"color\":\"aqua\"}"
                },
                new String[] {
                        "{\"text\":\"Light as a feather\",\"color\":\"gray\"}",
                        "{\"text\":\"Heavy as a mountain\",\"color\":\"gray\"}"
                }
        };
        return loreOptions[random.nextInt(loreOptions.length)];
    }

    private static double rollAttributeAmount(RandomSource random, double min, double max, boolean fullMoonBonus) {
        double bonusMax = fullMoonBonus ? max * FULL_MOON_ATTRIBUTE_BONUS : max;
        double value = min + (bonusMax - min) * rollExponentialUnit(random, Config.expLambda);
        return Math.round(value * 100.0) / 100.0;
    }

    private static double rollExponentialUnit(RandomSource random, double lambda) {
        double u = random.nextDouble();
        double limit = 1.0 - Math.exp(-lambda);
        double x = -Math.log(1.0 - u * limit) / lambda;
        return Mth.clamp(x, 0.0, 1.0);
    }

    private static int rollExponentialInt(RandomSource random, int max, double lambda) {
        double x = rollExponentialUnit(random, lambda);
        if (max <= 1) {
            return 1;
        }
        int value = 1 + (int) Math.floor(x * (double) (max - 1));
        return Mth.clamp(value, 1, max);
    }

    private static AttributeRange rollAttribute(RandomSource random) {
        AttributeRange[] ranges = new AttributeRange[] {
                new AttributeRange("generic.attack_damage", 4.0, 18.0),
                new AttributeRange("generic.attack_speed", 0.5, 2.5),
                new AttributeRange("generic.armor", 1.0, 12.0),
                new AttributeRange("generic.armor_toughness", 0.5, 6.0),
                new AttributeRange("generic.max_health", 2.0, 20.0),
                new AttributeRange("generic.movement_speed", 0.02, 0.2),
                new AttributeRange("generic.knockback_resistance", 0.05, 0.6),
                new AttributeRange("generic.luck", 0.5, 5.0)
        };
        return ranges[random.nextInt(ranges.length)];
    }

    private static void maybeApplyPotion(ServerLevel level, Player player) {
        if (level.getRandom().nextDouble() >= Config.potionChance) {
            return;
        }
        List<MobEffect> effects = new ArrayList<>(ForgeRegistries.MOB_EFFECTS.getValues());
        if (effects.isEmpty()) {
            return;
        }
        MobEffect effect = effects.get(level.getRandom().nextInt(effects.size()));
        int amplifier = rollExponentialInt(level.getRandom(), Config.potionLevelMax, Config.expLambda) - 1;
        int duration = 200 + level.getRandom().nextInt(800);
        player.addEffect(new MobEffectInstance(effect, duration, amplifier));
    }

    private static boolean isFullMoonNight(ServerLevel level, long timeOfDay) {
        return level.getMoonPhase() == 0 && timeOfDay >= 13000L;
    }

    static boolean isSpecial(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(LUCKY_TAG);
    }

    private record AttributeRange(String name, double min, double max) {}

    static class LuckyDrawsSavedData extends SavedData {
        private static final String LAST_DRAW_DAY = "LastDrawDay";
        private static final String REROLL_USED = "RerollUsed";
        private static final String LOW_QUALITY = "LowQuality";
        private static final String HISTORY = "History";
        private static final String THUNDER_PENDING = "ThunderPending";
        private static final String FULL_MOON_PENDING = "FullMoonPending";
        private static final String MOB_SPAWN_ENABLED = "MobSpawnEnabled";
        private long lastDrawDay;
        private boolean thunderPending;
        private boolean fullMoonPending;
        private boolean mobSpawnEnabled = true;
        private final Map<UUID, Long> rerollUsedByDay = new HashMap<>();
        private final Map<UUID, Integer> lowQualityStreak = new HashMap<>();
        private final Map<UUID, Deque<HistoryEntry>> historyByPlayer = new HashMap<>();

        private static LuckyDrawsSavedData load(CompoundTag tag) {
            LuckyDrawsSavedData data = new LuckyDrawsSavedData();
            data.lastDrawDay = tag.getLong(LAST_DRAW_DAY);
            data.thunderPending = tag.getBoolean(THUNDER_PENDING);
            data.fullMoonPending = tag.getBoolean(FULL_MOON_PENDING);
            if (tag.contains(MOB_SPAWN_ENABLED)) {
                data.mobSpawnEnabled = tag.getBoolean(MOB_SPAWN_ENABLED);
            }
            CompoundTag rerollTag = tag.getCompound(REROLL_USED);
            for (String key : rerollTag.getAllKeys()) {
                data.rerollUsedByDay.put(UUID.fromString(key), rerollTag.getLong(key));
            }
            CompoundTag lowQualityTag = tag.getCompound(LOW_QUALITY);
            for (String key : lowQualityTag.getAllKeys()) {
                data.lowQualityStreak.put(UUID.fromString(key), lowQualityTag.getInt(key));
            }
            CompoundTag historyTag = tag.getCompound(HISTORY);
            for (String key : historyTag.getAllKeys()) {
                UUID playerId = UUID.fromString(key);
                ListTag entries = historyTag.getList(key, Tag.TAG_COMPOUND);
                Deque<HistoryEntry> deque = new ArrayDeque<>();
                for (int i = 0; i < entries.size(); i++) {
                    deque.addLast(HistoryEntry.fromTag(entries.getCompound(i)));
                }
                data.historyByPlayer.put(playerId, deque);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLong(LAST_DRAW_DAY, lastDrawDay);
            tag.putBoolean(THUNDER_PENDING, thunderPending);
            tag.putBoolean(FULL_MOON_PENDING, fullMoonPending);
            tag.putBoolean(MOB_SPAWN_ENABLED, mobSpawnEnabled);
            CompoundTag rerollTag = new CompoundTag();
            for (Map.Entry<UUID, Long> entry : rerollUsedByDay.entrySet()) {
                rerollTag.putLong(entry.getKey().toString(), entry.getValue());
            }
            tag.put(REROLL_USED, rerollTag);
            CompoundTag lowQualityTag = new CompoundTag();
            for (Map.Entry<UUID, Integer> entry : lowQualityStreak.entrySet()) {
                lowQualityTag.putInt(entry.getKey().toString(), entry.getValue());
            }
            tag.put(LOW_QUALITY, lowQualityTag);
            CompoundTag historyTag = new CompoundTag();
            for (Map.Entry<UUID, Deque<HistoryEntry>> entry : historyByPlayer.entrySet()) {
                ListTag entries = new ListTag();
                for (HistoryEntry historyEntry : entry.getValue()) {
                    entries.add(historyEntry.toTag());
                }
                historyTag.put(entry.getKey().toString(), entries);
            }
            tag.put(HISTORY, historyTag);
            return tag;
        }

        // 记录每天是否已抽取，避免重复触发
        static LuckyDrawsSavedData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(LuckyDrawsSavedData::load, LuckyDrawsSavedData::new, DATA_NAME);
        }

        boolean canReroll(UUID playerId, long currentDay) {
            return !rerollUsedByDay.containsKey(playerId) || rerollUsedByDay.get(playerId) < currentDay;
        }

        void markRerollUsed(UUID playerId, long currentDay) {
            rerollUsedByDay.put(playerId, currentDay);
        }

        long getLastDrawDay() {
            return lastDrawDay;
        }

        boolean isMobSpawnEnabled() {
            return mobSpawnEnabled;
        }

        void setMobSpawnEnabled(boolean enabled) {
            mobSpawnEnabled = enabled;
        }

        int getLowQualityStreak(UUID playerId) {
            return lowQualityStreak.getOrDefault(playerId, 0);
        }

        void updateLowQualityStreak(UUID playerId, boolean special) {
            if (special) {
                lowQualityStreak.put(playerId, 0);
            } else {
                lowQualityStreak.put(playerId, getLowQualityStreak(playerId) + 1);
            }
        }

        void recordHistory(Player player, ItemStack stack, String source) {
            Deque<HistoryEntry> deque = historyByPlayer.computeIfAbsent(player.getUUID(), key -> new ArrayDeque<>());
            deque.addFirst(new HistoryEntry(player.getName().getString(), stack, source));
            while (deque.size() > HISTORY_LIMIT) {
                deque.removeLast();
            }
        }

        List<HistoryEntry> getHistory(UUID playerId) {
            Deque<HistoryEntry> deque = historyByPlayer.get(playerId);
            if (deque == null) {
                return List.of();
            }
            return List.copyOf(deque);
        }
    }

    static class HistoryEntry {
        private static final String PLAYER_NAME = "PlayerName";
        private static final String ITEM = "Item";
        private static final String COUNT = "Count";
        private static final String SPECIAL = "Special";
        private static final String SOURCE = "Source";
        private static final String TIME = "Time";

        private final String playerName;
        private final String itemId;
        private final int count;
        private final boolean special;
        private final String source;
        private final long time;

        private HistoryEntry(String playerName, ItemStack stack, String source) {
            this.playerName = playerName;
            this.itemId = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
            this.count = stack.getCount();
            this.special = LuckyDrawsEvents.isSpecial(stack);
            this.source = source;
            this.time = System.currentTimeMillis();
        }

        private HistoryEntry(String playerName, String itemId, int count, boolean special, String source, long time) {
            this.playerName = playerName;
            this.itemId = itemId;
            this.count = count;
            this.special = special;
            this.source = source;
            this.time = time;
        }

        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString(PLAYER_NAME, playerName);
            tag.putString(ITEM, itemId);
            tag.putInt(COUNT, count);
            tag.putBoolean(SPECIAL, special);
            tag.putString(SOURCE, source);
            tag.putLong(TIME, time);
            return tag;
        }

        private static HistoryEntry fromTag(CompoundTag tag) {
            return new HistoryEntry(
                    tag.getString(PLAYER_NAME),
                    tag.getString(ITEM),
                    tag.getInt(COUNT),
                    tag.getBoolean(SPECIAL),
                    tag.getString(SOURCE),
                    tag.getLong(TIME));
        }

        String getItemId() {
            return itemId;
        }

        int getCount() {
            return count;
        }

        boolean isSpecial() {
            return special;
        }

        String getSource() {
            return source;
        }
    }
}
