package org.luckydraws;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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

        ItemStack stack = LuckyDrawsEvents.rollStack(overworld.getRandom());
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal("抽取失败：物品列表为空。"));
            return 0;
        }

        LuckyDrawsEvents.grantToPlayer(player, stack);
        player.sendSystemMessage(LuckyDrawsEvents.buildMessage(stack));
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
                + ", 标准差=" + Config.drawStdDev), false);
        return 1;
    }
}
