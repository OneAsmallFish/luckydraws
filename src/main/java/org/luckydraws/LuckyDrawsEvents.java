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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
import java.util.UUID;

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
            ItemStack stack = rollStack(overworld.getRandom(), forceSpecial, thunderBonus, fullMoonBonus);
            if (stack.isEmpty()) {
                continue;
            }

            // 先尝试放入背包，满了则掉落在玩家位置
            grantToPlayer(player, stack);
            player.sendSystemMessage(buildMessage(stack));
            data.recordHistory(player, stack, "draw");
            data.updateLowQualityStreak(player.getUUID(), isSpecial(stack));
        }

        data.lastDrawDay = currentDay;
        data.thunderPending = false;
        data.fullMoonPending = false;
        data.rerollUsedByDay.clear();
        data.setDirty();

        MutableComponent announcement = Component.literal("今日抽取已完成");
        if (thunderBonus || fullMoonBonus) {
            StringBuilder bonus = new StringBuilder("（");
            if (thunderBonus) {
                bonus.append("雷雨加成");
            }
            if (fullMoonBonus) {
                if (thunderBonus) {
                    bonus.append("、");
                }
                bonus.append("满月加成");
            }
            bonus.append("）");
            announcement = announcement.append(Component.literal(bonus.toString()));
        }
        for (Player player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(announcement);
        }
    }

    // 缓存物品表，避免每 tick 枚举注册表
    private static Item pickRandomItem(RandomSource random) {
        if (cachedItems == null) {
            cachedItems = new ArrayList<>(ForgeRegistries.ITEMS.getValues());
            cachedItems.remove(Items.AIR);
        }
        if (cachedItems.isEmpty()) {
            return null;
        }
        return cachedItems.get(random.nextInt(cachedItems.size()));
    }

    // 正态分布抽取数量，并限制在 1-64
    private static int rollCount(RandomSource random) {
        double gaussian = random.nextGaussian() * Config.drawStdDev + Config.drawMean;
        return Mth.clamp((int) Math.round(gaussian), 1, 64);
    }

    static ItemStack rollStack(RandomSource random, boolean forceSpecial, boolean thunderBonus, boolean fullMoonBonus) {
        Item item = pickRandomItem(random);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        int count = rollCount(random);
        ItemStack stack = new ItemStack(item, count);
        double chance = ENCHANT_CHANCE + (thunderBonus ? THUNDER_BONUS_CHANCE : 0.0);
        if (forceSpecial || random.nextDouble() < chance) {
            // 为物品添加随机附魔或自定义标签，避免抛出异常影响流程
            if (stack.isEnchantable()) {
                int level = rollEnchantLevel(random, 30);
                stack = EnchantmentHelper.enchantItem(random, stack, level, false);
            } else {
                applyLuckyTag(random, stack, fullMoonBonus);
            }
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

    static Component buildMessage(ItemStack stack) {
        MutableComponent base = Component.literal("幸运抽取: ")
                .append(Component.literal(String.valueOf(stack.getCount())))
                .append(Component.literal(" x "))
                .append(formatDisplayName(stack));
        MutableComponent reroll = Component.literal(" 不满意？")
                .append(Component.literal("再抽一次")
                        .setStyle(Style.EMPTY
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, REROLL_COMMAND))
                                .withColor(ChatFormatting.AQUA)
                                .withUnderlined(true)));
        return base.append(reroll);
    }

    private static Component formatDisplayName(ItemStack stack) {
        MutableComponent name = stack.getHoverName().copy();
        if (stack.isEnchanted() || (stack.hasTag() && stack.getTag().getBoolean(LUCKY_TAG))) {
            name.withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE).withItalic(true));
        }
        return name;
    }

    // 等级概率从小到大递减：低等级更常见
    private static int rollEnchantLevel(RandomSource random, int maxLevel) {
        double total = 0.0;
        for (int level = 1; level <= maxLevel; level++) {
            total += 1.0 / level;
        }
        double pick = random.nextDouble() * total;
        double running = 0.0;
        for (int level = 1; level <= maxLevel; level++) {
            running += 1.0 / level;
            if (pick <= running) {
                return level;
            }
        }
        return maxLevel;
    }

    private static void applyLuckyTag(RandomSource random, ItemStack stack, boolean fullMoonBonus) {
        stack.getOrCreateTag().putBoolean(LUCKY_TAG, true);
        stack.getOrCreateTag().putInt(LUCKY_TAG_SEED, random.nextInt());

        applyDisplayTag(random, stack);
        applyAttributeTag(random, stack, fullMoonBonus);
    }

    private static void applyDisplayTag(RandomSource random, ItemStack stack) {
        CompoundTag display = stack.getOrCreateTag().getCompound("display");
        String name = rollName(random);
        ListTag lore = new ListTag();
        for (String line : rollLore(random)) {
            lore.add(StringTag.valueOf(line));
        }
        display.putString("Name", Component.Serializer.toJson(Component.literal(name)));
        display.put("Lore", lore);
        stack.getOrCreateTag().put("display", display);
    }

    private static void applyAttributeTag(RandomSource random, ItemStack stack, boolean fullMoonBonus) {
        ListTag modifiers = new ListTag();
        CompoundTag modifier = new CompoundTag();
        modifier.putString("Slot", "mainhand");
        if (random.nextBoolean()) {
            modifier.putString("AttributeName", "generic.attack_damage");
            modifier.putDouble("Amount", rollAttributeAmount(random, 6.0, 20.0, fullMoonBonus));
            modifier.putInt("Operation", 0);
        } else {
            modifier.putString("AttributeName", "generic.attack_speed");
            modifier.putDouble("Amount", rollAttributeAmount(random, 0.5, 2.5, fullMoonBonus));
            modifier.putInt("Operation", 0);
        }
        modifier.putIntArray("UUID", new int[] { random.nextInt(), random.nextInt(), random.nextInt(), random.nextInt() });
        modifiers.add(modifier);
        stack.getOrCreateTag().put("AttributeModifiers", modifiers);
    }

    private static String rollName(RandomSource random) {
        String[] names = new String[] {
                "寰宇支配之咸鱼",
                "命运回响",
                "流光碎片",
                "尘世遗珍",
                "星辰残响"
        };
        return names[random.nextInt(names.length)];
    }

    private static String[] rollLore(RandomSource random) {
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

    private static double rollAttributeAmount(RandomSource random, double min, double max, boolean fullMoonBonus) {
        double bonusMax = fullMoonBonus ? max * FULL_MOON_ATTRIBUTE_BONUS : max;
        double value = min + (bonusMax - min) * random.nextDouble();
        return Math.round(value * 100.0) / 100.0;
    }

    private static boolean isFullMoonNight(ServerLevel level, long timeOfDay) {
        return level.getMoonPhase() == 0 && timeOfDay >= 13000L;
    }

    static boolean isSpecial(ItemStack stack) {
        return stack.isEnchanted() || (stack.hasTag() && stack.getTag().getBoolean(LUCKY_TAG));
    }

    static class LuckyDrawsSavedData extends SavedData {
        private static final String LAST_DRAW_DAY = "LastDrawDay";
        private static final String REROLL_USED = "RerollUsed";
        private static final String LOW_QUALITY = "LowQuality";
        private static final String HISTORY = "History";
        private static final String THUNDER_PENDING = "ThunderPending";
        private static final String FULL_MOON_PENDING = "FullMoonPending";
        private long lastDrawDay;
        private boolean thunderPending;
        private boolean fullMoonPending;
        private final Map<UUID, Long> rerollUsedByDay = new HashMap<>();
        private final Map<UUID, Integer> lowQualityStreak = new HashMap<>();
        private final Map<UUID, Deque<HistoryEntry>> historyByPlayer = new HashMap<>();

        private static LuckyDrawsSavedData load(CompoundTag tag) {
            LuckyDrawsSavedData data = new LuckyDrawsSavedData();
            data.lastDrawDay = tag.getLong(LAST_DRAW_DAY);
            data.thunderPending = tag.getBoolean(THUNDER_PENDING);
            data.fullMoonPending = tag.getBoolean(FULL_MOON_PENDING);
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
