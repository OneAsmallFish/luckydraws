package org.luckydraws;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = Luckydraws.MODID)
public class LuckyDrawsCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("luckydraws")
                .then(Commands.literal("reroll")
                        .executes(context -> reroll(context.getSource())))
                .then(Commands.literal("history")
                        .executes(context -> showHistory(context.getSource())))
                .then(Commands.literal("help")
                        .executes(context -> showHelp(context.getSource())))
                .then(Commands.literal("config")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("reload")
                                .executes(context -> reloadConfig(context.getSource()))))
                .then(Commands.literal("mobspawn")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("on")
                                .executes(context -> setMobSpawn(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setMobSpawn(context.getSource(), false)))
                        .then(Commands.literal("status")
                                .executes(context -> showMobSpawn(context.getSource()))))
                .then(Commands.literal("show")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> showConfig(context.getSource()))));
    }

    private static int reroll(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            source.sendFailure(localized(source, "主世界不存在，无法抽取。", "Overworld is missing, unable to draw."));
            return 0;
        }

        long dayTime = overworld.getDayTime();
        long currentDay = dayTime / 24000L;
        LuckyDrawsEvents.LuckyDrawsSavedData data = LuckyDrawsEvents.LuckyDrawsSavedData.get(overworld);
        if (data.getLastDrawDay() < currentDay) {
            source.sendFailure(localized(source, "今天还没有进行抽取，无法再抽一次。", "Today's draw has not happened yet, reroll is unavailable."));
            return 0;
        }
        if (!data.canReroll(player.getUUID(), currentDay)) {
            source.sendFailure(localized(source, "今天已使用过再抽一次。", "You have already used today's reroll."));
            return 0;
        }

        boolean forceSpecial = data.getLowQualityStreak(player.getUUID()) >= 3;
        ItemStack stack = LuckyDrawsEvents.rollStack(overworld.getRandom(), forceSpecial, false, false, player);
        if (stack.isEmpty()) {
            source.sendFailure(localized(source, "抽取失败：物品列表为空。", "Draw failed: item pool is empty."));
            return 0;
        }

        LuckyDrawsEvents.grantToPlayer(player, stack);
        player.sendSystemMessage(LuckyDrawsEvents.buildMessage(player, stack));
        data.recordHistory(player, stack, "reroll");
        data.updateLowQualityStreak(player.getUUID(), LuckyDrawsEvents.isSpecial(stack));
        data.markRerollUsed(player.getUUID(), currentDay);
        data.setDirty();
        return 1;
    }

    private static int showConfig(CommandSourceStack source) {
        if (isChinese(source)) {
            source.sendSuccess(() -> Component.literal("当前配置: 抽取时间=" + Config.drawTime
                    + ", 均值=" + Config.drawMean
                    + ", 标准差=" + Config.drawStdDev
                    + ", 药水概率=" + Config.potionChance
                    + ", 生物概率=" + Config.mobSpawnChance
                    + ", 生物数量上限=" + Config.mobSpawnMax
                    + ", 体型加成上限=" + Config.mobSizeBonusMax
                    + ", 苦力怕爆炸上限=" + Config.creeperRadiusMax
                    + ", 指数参数=" + Config.expLambda
                    + ", 附魔上限=" + Config.enchantLevelMax
                    + ", 药水上限=" + Config.potionLevelMax
                    + ", 物品黑名单数量=" + Config.drawItemBlacklist.size()), false);
        } else {
            source.sendSuccess(() -> Component.literal("Current config: drawTime=" + Config.drawTime
                    + ", drawMean=" + Config.drawMean
                    + ", drawStdDev=" + Config.drawStdDev
                    + ", potionChance=" + Config.potionChance
                    + ", mobSpawnChance=" + Config.mobSpawnChance
                    + ", mobSpawnMax=" + Config.mobSpawnMax
                    + ", mobSizeBonusMax=" + Config.mobSizeBonusMax
                    + ", creeperRadiusMax=" + Config.creeperRadiusMax
                    + ", expLambda=" + Config.expLambda
                    + ", enchantLevelMax=" + Config.enchantLevelMax
                    + ", potionLevelMax=" + Config.potionLevelMax
                    + ", drawItemBlacklistSize=" + Config.drawItemBlacklist.size()), false);
        }
        return 1;
    }

    private static int showHistory(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            source.sendFailure(localized(source, "主世界不存在，无法查看记录。", "Overworld is missing, unable to view history."));
            return 0;
        }

        LuckyDrawsEvents.LuckyDrawsSavedData data = LuckyDrawsEvents.LuckyDrawsSavedData.get(overworld);
        List<LuckyDrawsEvents.HistoryEntry> history = data.getHistory(player.getUUID());
        if (history.isEmpty()) {
            source.sendSuccess(() -> localized(source, "暂无抽取记录。", "No draw history yet."), false);
            return 1;
        }

        source.sendSuccess(() -> localized(source, "最近" + history.size() + "次抽取记录：", "Latest " + history.size() + " draw records:"), false);
        for (LuckyDrawsEvents.HistoryEntry entry : history) {
            Component line = buildHistoryLine(source, entry);
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> localized(source, "LuckyDraws 指令帮助", "LuckyDraws command help"), false);
        source.sendSuccess(() -> localized(source, "玩家可用:", "Player commands:"), false);
        source.sendSuccess(() -> localized(source, "/luckydraws reroll - 再抽一次(每日1次)", "/luckydraws reroll - reroll once per day"), false);
        source.sendSuccess(() -> localized(source, "/luckydraws history - 查看最近抽取记录", "/luckydraws history - show recent draw history"), false);
        source.sendSuccess(() -> localized(source, "/luckydraws show - 查看当前配置", "/luckydraws show - show current config"), false);

        source.sendSuccess(() -> localized(source, "管理员可用:", "Admin commands:"), false);
        source.sendSuccess(() -> localized(source, "/luckydraws config reload - 热重载配置", "/luckydraws config reload - hot reload config"), false);
        source.sendSuccess(() -> localized(source, "/luckydraws mobspawn on | off | status - 生物生成开关", "/luckydraws mobspawn on | off | status - mob spawn toggle"), false);
        source.sendSuccess(() -> localized(source, "配置文件路径: .minecraft/config/luckydraws-common.toml", "Config path: .minecraft/config/luckydraws-common.toml"), false);
        source.sendSuccess(() -> localized(source, "编辑后热重载生效（或重启服务器）", "Apply changes with hot reload (or restart server)"), false);
        return 1;
    }

    private static int reloadConfig(CommandSourceStack source) {
        Config.ReloadResult result = Config.reloadFromDisk();
        if (result.success()) {
            source.sendSuccess(() -> localized(source, "配置已热重载。详情: " + result.message(), "Config hot reloaded. Detail: " + result.message()), true);
            return 1;
        }
        source.sendFailure(localized(source, "配置热重载失败。详情: " + result.message(), "Config hot reload failed. Detail: " + result.message()));
        return 0;
    }

    private static int setMobSpawn(CommandSourceStack source, boolean enabled) {
        ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            source.sendFailure(localized(source, "主世界不存在，无法设置。", "Overworld is missing, unable to update setting."));
            return 0;
        }
        LuckyDrawsEvents.LuckyDrawsSavedData data = LuckyDrawsEvents.LuckyDrawsSavedData.get(overworld);
        data.setMobSpawnEnabled(enabled);
        data.setDirty();
        source.sendSuccess(() -> localized(source, "随机生物生成已" + (enabled ? "开启" : "关闭"), "Random mob spawning is now " + (enabled ? "enabled" : "disabled")), true);
        return 1;
    }

    private static int showMobSpawn(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            source.sendFailure(localized(source, "主世界不存在，无法查看。", "Overworld is missing, unable to query status."));
            return 0;
        }
        LuckyDrawsEvents.LuckyDrawsSavedData data = LuckyDrawsEvents.LuckyDrawsSavedData.get(overworld);
        source.sendSuccess(() -> localized(source, "随机生物生成状态: " + (data.isMobSpawnEnabled() ? "开启" : "关闭"), "Random mob spawn status: " + (data.isMobSpawnEnabled() ? "enabled" : "disabled")), false);
        return 1;
    }

    private static Component buildHistoryLine(CommandSourceStack source, LuckyDrawsEvents.HistoryEntry entry) {
        String itemId = entry.getItemId();
        ItemStack stack = ItemStack.EMPTY;
        if (ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemId))) {
            stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId)), entry.getCount());
        }
        Component name = stack.isEmpty() ? Component.literal(itemId) : stack.getHoverName();
        boolean zh = isChinese(source);
        String flag = entry.isSpecial() ? (zh ? "（特殊）" : " (special)") : "";
        String sourceText = zh ? "抽取" : "draw";
        if ("reroll".equals(entry.getSource())) {
            sourceText = zh ? "再抽" : "reroll";
        }
        return Component.literal("- ")
                .append(name)
                .append(Component.literal(" x " + entry.getCount() + " " + sourceText + flag));
    }

    private static Component localized(CommandSourceStack source, String zh, String en) {
        return Component.literal(isChinese(source) ? zh : en);
    }

    private static boolean isChinese(CommandSourceStack source) {
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayer player) {
            String lang = player.getLanguage();
            return lang != null && lang.toLowerCase(Locale.ROOT).startsWith("zh");
        }
        return false;
    }
}
