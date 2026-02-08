[English](README.md) | [中文](README_zh.md)

# LuckyDraws

LuckyDraws is a server-side Forge mod for Minecraft 1.20.1 that runs a daily lucky draw for all players.

## Features

- Daily draw at a configurable time
- Unique item and quantity per player
- Normal distribution for item count
- Special draws with enchants and custom attributes
- One-time daily reroll
- Optional bonus events and random potion effects
- Optional random nearby mob spawns

## Requirements

- Minecraft 1.20.1
- Forge 47.x
- Java 17

## Install (Server)

1. Build the mod jar.
2. Drop the jar into your server `mods` folder.
3. Start the server.

## Build

```
gradlew build
```

Output jar: `build/libs/`

## Commands (Summary)

Player:
- `/luckydraws reroll`
- `/luckydraws history`
- `/luckydraws show`
- `/luckydraws help`

Admin:
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

## License

MIT
