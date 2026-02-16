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
- Item blacklist support (`drawItemBlacklist`)
- Config hot reload (`/luckydraws config reload`)
- Command message language auto-adapts by player locale (`zh_*` or fallback English)

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
- `/luckydraws help`

Admin:
- `/luckydraws show`
- `/luckydraws config reload`
- `/luckydraws mobspawn on|off|status`

## Configuration

- Config file: `.minecraft/config/luckydraws-common.toml`
- The mod generates default config on first launch.
- After editing, run `/luckydraws config reload` (or restart server).
- Example blacklist:

```toml
drawItemBlacklist = ["minecraft:bedrock", "minecraft:command_block"]
```

## License

MIT
