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
- `/luckydraws show`
- `/luckydraws help`

管理员：
- `/luckydraws mobspawn on|off|status`
- `/luckydraws settime <0-23999>`
- `/luckydraws setmean <1-64>`
- `/luckydraws setstddev <0-64>`
- `/luckydraws setpotionchance <0-1>`
- `/luckydraws setmobchance <0-1>`
- `/luckydraws setmobmax <1-20>`
- `/luckydraws setmobsize <0-20>`
- `/luckydraws setcreepradius <1-128>`
- `/luckydraws setexplambda <0.1-5>`
- `/luckydraws setenchantmax <1-255>`
- `/luckydraws setpotionmax <1-255>`

## 许可

MIT
