package org.luckydraws;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

// 入口类仅做配置注册，服务端即可生效
@Mod(Luckydraws.MODID)
public class Luckydraws {

    // 与 mods.toml 中的 modId 一致
    public static final String MODID = "luckydraws";

    public Luckydraws() {
        // 注册通用配置，服务端读取抽取参数
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
