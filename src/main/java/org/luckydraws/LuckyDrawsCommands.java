package org.luckydraws;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

@Mod.EventBusSubscriber(modid = Luckydraws.MODID)
public class LuckyDrawsCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("luckydraws")
                .then(Commands.literal("reroll")
                        .executes(context -> reroll(context.getSource())))
                .then(Commands.literal("settime")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("time", IntegerArgumentType.integer(0, 23999))
                                .executes(context -> setTime(context.getSource(), IntegerArgumentType.getInteger(context, "time")))))
                .then(Commands.literal("setmean")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("mean", DoubleArgumentType.doubleArg(1.0, 64.0))
                                .executes(context -> setMean(context.getSource(), DoubleArgumentType.getDouble(context, "mean")))))
                .then(Commands.literal("setstddev")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("stddev", DoubleArgumentType.doubleArg(0.0, 64.0))
                                .executes(context -> setStdDev(context.getSource(), DoubleArgumentType.getDouble(context, "stddev")))))
                .then(Commands.literal("setpotionchance")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("chance", DoubleArgumentType.doubleArg(0.0, 1.0))
                                .executes(context -> setPotionChance(context.getSource(), DoubleArgumentType.getDouble(context, "chance")))))
                .then(Commands.literal("setmobchance")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("chance", DoubleArgumentType.doubleArg(0.0, 1.0))
                                .executes(context -> setMobChance(context.getSource(), DoubleArgumentType.getDouble(context, "chance")))))
                .then(Commands.literal("setmobmax")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("max", IntegerArgumentType.integer(1, 20))
                                .executes(context -> setMobMax(context.getSource(), IntegerArgumentType.getInteger(context, "max")))))
                .then(Commands.literal("setmobsize")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("max", IntegerArgumentType.integer(0, 20))
                                .executes(context -> setMobSize(context.getSource(), IntegerArgumentType.getInteger(context, "max")))))
                .then(Commands.literal("setcreepradius")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("max", IntegerArgumentType.integer(1, 128))
                                .executes(context -> setCreeperRadius(context.getSource(), IntegerArgumentType.getInteger(context, "max")))))
                .then(Commands.literal("setexplambda")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("lambda", DoubleArgumentType.doubleArg(0.1, 5.0))
                                .executes(context -> setExpLambda(context.getSource(), DoubleArgumentType.getDouble(context, "lambda")))))
                .then(Commands.literal("setenchantmax")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("max", IntegerArgumentType.integer(1, 255))
                                .executes(context -> setEnchantMax(context.getSource(), IntegerArgumentType.getInteger(context, "max")))))
                .then(Commands.literal("setpotionmax")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("max", IntegerArgumentType.integer(1, 255))
                                .executes(context -> setPotionMax(context.getSource(), IntegerArgumentType.getInteger(context, "max")))))
                .then(Commands.literal("history")
                        .executes(context -> showHistory(context.getSource())))
                .then(Commands.literal("help")
                        .executes(context -> showHelp(context.getSource())))
                .then(Commands.literal("mobspawn")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("on")
                                .executes(context -> setMobSpawn(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setMobSpawn(context.getSource(), false)))
                        .then(Commands.literal("status")
                                .executes(context -> showMobSpawn(context.getSource()))))
                .then(Commands.literal("show")
                        .executes(context -> showConfig(context.getSource()))));
    }

    private static int reroll(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            source.sendFailure(Component.literal("主世界不存在，无法抽取。"));
            return 0;
        }

        long dayTime = overworld.getDayTime();
        long currentDay = dayTime / 24000L;
        LuckyDrawsEvents.LuckyDrawsSavedData data = LuckyDrawsEvents.LuckyDrawsSavedData.get(overworld);
        if (data.getLastDrawDay() < currentDay) {
            source.sendFailure(Component.literal("今天还没有进行抽取，无法再抽一次。"));
            return 0;
        }
        if (!data.canReroll(player.getUUID(), currentDay)) {
            source.sendFailure(Component.literal("今天已使用过再抽一次。"));
            return 0;
        }

        boolean forceSpecial = data.getLowQualityStreak(player.getUUID()) >= 3;
        ItemStack stack = LuckyDrawsEvents.rollStack(overworld.getRandom(), forceSpecial, false, false);
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("抽取失败：物品列表为空。"));
            return 0;
        }

        LuckyDrawsEvents.grantToPlayer(player, stack);
        player.sendSystemMessage(LuckyDrawsEvents.buildMessage(stack));
        data.recordHistory(player, stack, "reroll");
        data.updateLowQualityStreak(player.getUUID(), LuckyDrawsEvents.isSpecial(stack));
        data.markRerollUsed(player.getUUID(), currentDay);
        data.setDirty();
        return 1;
    }

    private static int setTime(CommandSourceStack source, int time) {
        Config.setDrawTime(time);
        source.sendSuccess(() -> Component.literal("抽取时间已设置为: " + time), true);
        return 1;
    }

    private static int setMean(CommandSourceStack source, double mean) {
        Config.setDrawMean(mean);
        source.sendSuccess(() -> Component.literal("数量均值已设置为: " + mean), true);
        return 1;
    }

    private static int setStdDev(CommandSourceStack source, double stdDev) {
        Config.setDrawStdDev(stdDev);
        source.sendSuccess(() -> Component.literal("数量标准差已设置为: " + stdDev), true);
        return 1;
    }

    private static int showConfig(CommandSourceStack source) {
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
                + ", 药水上限=" + Config.potionLevelMax), false);
        return 1;
    }

    private static int setPotionChance(CommandSourceStack source, double chance) {
        Config.setPotionChance(chance);
        source.sendSuccess(() -> Component.literal("药水概率已设置为: " + chance), true);
        return 1;
    }

    private static int setMobChance(CommandSourceStack source, double chance) {
        Config.setMobSpawnChance(chance);
        source.sendSuccess(() -> Component.literal("生物概率已设置为: " + chance), true);
        return 1;
    }

    private static int setMobMax(CommandSourceStack source, int max) {
        Config.setMobSpawnMax(max);
        source.sendSuccess(() -> Component.literal("生物数量上限已设置为: " + max), true);
        return 1;
    }

    private static int setMobSize(CommandSourceStack source, int max) {
        Config.setMobSizeBonusMax(max);
        source.sendSuccess(() -> Component.literal("体型加成上限已设置为: " + max), true);
        return 1;
    }

    private static int setCreeperRadius(CommandSourceStack source, int max) {
        Config.setCreeperRadiusMax(max);
        source.sendSuccess(() -> Component.literal("苦力怕爆炸上限已设置为: " + max), true);
        return 1;
    }

    private static int setExpLambda(CommandSourceStack source, double lambda) {
        Config.setExpLambda(lambda);
        source.sendSuccess(() -> Component.literal("指数参数已设置为: " + lambda), true);
        return 1;
    }

    private static int setEnchantMax(CommandSourceStack source, int max) {
        Config.setEnchantLevelMax(max);
        source.sendSuccess(() -> Component.literal("附魔等级上限已设置为: " + max), true);
        return 1;
    }

    private static int setPotionMax(CommandSourceStack source, int max) {
        Config.setPotionLevelMax(max);
        source.sendSuccess(() -> Component.literal("药水等级上限已设置为: " + max), true);
        return 1;
    }

    private static int showHistory(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            source.sendFailure(Component.literal("主世界不存在，无法查看记录。"));
            return 0;
        }

        LuckyDrawsEvents.LuckyDrawsSavedData data = LuckyDrawsEvents.LuckyDrawsSavedData.get(overworld);
        List<LuckyDrawsEvents.HistoryEntry> history = data.getHistory(player.getUUID());
        if (history.isEmpty()) {
            source.sendSuccess(() -> Component.literal("暂无抽取记录。"), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("最近" + history.size() + "次抽取记录："), false);
        for (LuckyDrawsEvents.HistoryEntry entry : history) {
            Component line = buildHistoryLine(entry);
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("LuckyDraws 指令帮助"), false);
        source.sendSuccess(() -> Component.literal("玩家可用:"), false);
        source.sendSuccess(() -> Component.literal("/luckydraws reroll - 再抽一次(每日1次)"), false);
        source.sendSuccess(() -> Component.literal("/luckydraws history - 查看最近抽取记录"), false);
        source.sendSuccess(() -> Component.literal("/luckydraws show - 查看当前配置"), false);

        source.sendSuccess(() -> Component.literal("管理员可用:"), false);
        source.sendSuccess(() -> Component.literal("/luckydraws mobspawn on|off|status - 生物生成开关"), false);
        source.sendSuccess(() -> Component.literal("/luckydraws settime <0-23999>"), false);
        source.sendSuccess(() -> Component.literal("/luckydraws setmean <1-64>"), false);
        source.sendSuccess(() -> Component.literal("/luckydraws setstddev <0-64>"), false);
        source.sendSuccess(() -> Component.literal("/luckydraws setpotionchance <0-1>"), false);
        source.sendSuccess(() -> Component.literal("/luckydraws setmobchance <0-1>"), false);
        source.sendSuccess(() -> Component.literal("/luckydraws setmobmax <1-20>"), false);
        source.sendSuccess(() -> Component.literal("/luckydraws setmobsize <0-20>"), false);
        source.sendSuccess(() -> Component.literal("/luckydraws setcreepradius <1-128>"), false);
        source.sendSuccess(() -> Component.literal("/luckydraws setexplambda <0.1-5>"), false);
        source.sendSuccess(() -> Component.literal("/luckydraws setenchantmax <1-255>"), false);
        source.sendSuccess(() -> Component.literal("/luckydraws setpotionmax <1-255>"), false);
        return 1;
    }

    private static int setMobSpawn(CommandSourceStack source, boolean enabled) {
        ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            source.sendFailure(Component.literal("主世界不存在，无法设置。"));
            return 0;
        }
        LuckyDrawsEvents.LuckyDrawsSavedData data = LuckyDrawsEvents.LuckyDrawsSavedData.get(overworld);
        data.setMobSpawnEnabled(enabled);
        data.setDirty();
        source.sendSuccess(() -> Component.literal("随机生物生成已" + (enabled ? "开启" : "关闭")), true);
        return 1;
    }

    private static int showMobSpawn(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            source.sendFailure(Component.literal("主世界不存在，无法查看。"));
            return 0;
        }
        LuckyDrawsEvents.LuckyDrawsSavedData data = LuckyDrawsEvents.LuckyDrawsSavedData.get(overworld);
        source.sendSuccess(() -> Component.literal("随机生物生成状态: " + (data.isMobSpawnEnabled() ? "开启" : "关闭")), false);
        return 1;
    }

    private static Component buildHistoryLine(LuckyDrawsEvents.HistoryEntry entry) {
        String itemId = entry.getItemId();
        ItemStack stack = ItemStack.EMPTY;
        if (ForgeRegistries.ITEMS.containsKey(new ResourceLocation(itemId))) {
            stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId)), entry.getCount());
        }
        Component name = stack.isEmpty() ? Component.literal(itemId) : stack.getHoverName();
        String flag = entry.isSpecial() ? "（特殊）" : "";
        String source = "抽取";
        if ("reroll".equals(entry.getSource())) {
            source = "再抽";
        }
        return Component.literal("- ")
                .append(name)
                .append(Component.literal(" x " + entry.getCount() + " " + source + flag));
    }
}
