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

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int drawTime;
    public static double drawMean;
    public static double drawStdDev;

    // 由 Forge 在配置装载时写入
    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        drawTime = DRAW_TIME.get();
        drawMean = DRAW_MEAN.get();
        drawStdDev = DRAW_STD_DEV.get();
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
}
