[English](README.md) | [中文](README_zh.md)

# LuckyDraws

LuckyDraws 是一个适用于 Minecraft 1.20.1 的服务端 Forge 模组，每日为全服玩家进行一次幸运抽取。

## 功能

- 可配置的每日抽取时间
- 每位玩家获得不同物品与数量
- 数量采用正态分布
- 特殊抽取含附魔与属性标签
- 每日一次再抽
- 可选事件加成与随机药水效果
- 可选随机附近生成生物
- 支持物品黑名单（`drawItemBlacklist`）
- 支持配置热重载（`/luckydraws config reload`）
- 指令返回文本会根据玩家客户端语言自动适配（`zh_*` 中文，否则英文）

## 运行环境

- Minecraft 1.20.1
- Forge 47.x
- Java 17

## 安装（服务端）

1. 构建模组 jar。
2. 将 jar 放入服务器 `mods` 文件夹。
3. 启动服务器。

## 构建

```
gradlew build
```

输出位置：`build/libs/`

## 指令（概览）

玩家：
- `/luckydraws reroll`
- `/luckydraws history`
- `/luckydraws help`

管理员：
- `/luckydraws show`
- `/luckydraws config reload`
- `/luckydraws mobspawn on|off|status`

## 配置说明

- 配置文件路径：`.minecraft/config/luckydraws-common.toml`
- 首次启动模组时会自动生成默认配置。
- 修改配置后可执行 `/luckydraws config reload`（或重启服务器）生效。
- 黑名单示例：

```toml
drawItemBlacklist = ["minecraft:bedrock", "minecraft:command_block"]
```

## 许可

MIT
