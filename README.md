# Fabric Trade

A simple server-sided mod to give players the ability to trade items safely with other players anywhere else on the server.

##### This mod should only be installed on a server.

### Player Commands
* ```/trade``` Opens a GUI to select a player to trade
* ```/trade <player>``` Requests to trade with the specified player
* ```/tradeaccept [player]``` Accepts an incoming trade request from the specified player
* ```/tradedeny [player]``` Denies an incoming trade request from the specified player
* ```/tradecancel [player]``` Cancels an outgoing trade request to the specified player

### Admin Commands & Configuration
Configuration can be done through the properties file generated when loaded on a server or through commands.
* ```/tradeconfig cooldown <0+>``` Sets the number of seconds before a player can trade with another player again
* ```/tradeconfig timeout <0+>``` Sets the number of seconds before a trade request expires
* ```/tradeconfig cooldown-mode <WHO_INITIATED/BOTH_USERS>``` Sets whether only the trade initiator must wait the cooldown, or both participants
* ```/tradeconfig logCommandUsage <true/false>``` Sets whether successful command executions are logged to the server console

### Permission Nodes
Trade uses the [Fabric Permissions API](https://github.com/lucko/fabric-permissions-api) (bundled via BorisLib) for command permissions. Each node has a fallback vanilla permission level for servers without a permissions mod.

#### Player Commands
| Node | Default | Description |
|------|---------|-------------|
| `trade.trade.gui` | `ALL` | Open the trade GUI (`/trade` with no argument) |
| `trade.trade.others` | `ALL` | Initiate a trade request with another player (`/trade <player>`) |
| `trade.tradeaccept` | `ALL` | Accept an incoming trade request |
| `trade.tradedeny` | `ALL` | Deny an incoming trade request |
| `trade.tradecancel` | `ALL` | Cancel an outgoing trade request |

#### Config
Config commands are generated dynamically by BorisLib per config value.

| Node | Default | Description |
|------|---------|-------------|
| `trade.config` | `GAMEMASTERS` | List all config values |
| `trade.config.<name>.get` | `GAMEMASTERS` | Read a specific config value |
| `trade.config.<name>.set` | `GAMEMASTERS` | Change a specific config value |

### Try My Other Mods!
All server-side Fabric mods — no client installation required.

|                                                                                                                | Mod                      | Description                                                                                               | Links                                                                                                                                                                                                                                                                                                                                                                                                                                              |
|:--------------------------------------------------------------------------------------------------------------:|--------------------------|-----------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| <img src="https://cdn.modrinth.com/data/9J7sCd3t/e6ce366187de25be0efc7ecc736fc27f05452888_96.webp" width="32"> | **Arcana Novum**         | Minecraft's biggest server-only full-feature Magic Mod! Adds powerful items, multiblocks and bosses!      | [![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white)](https://github.com/borisshoes/ArcanaNovum/) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/arcana-novum) [![CurseForge](https://img.shields.io/badge/CurseForge-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/arcana-novum)                        |
| <img src="https://cdn.modrinth.com/data/xHHbHfVj/c6c224a3d8068cfb9b054e2a03eb9704906dd8cb_96.webp" width="32"> | **Ancestral Archetypes** | A highly configurable, Origins-style mod that lets players pick a mob to gain unique abilities!           | [![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white)](https://github.com/borisshoes/AncestralArchetypes) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/ancestral-archetypes) [![CurseForge](https://img.shields.io/badge/CurseForge-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/ancestral-archetypes) |
| <img src="https://cdn.modrinth.com/data/QfXOzeIK/b35cbf33da842f170d0aa562033aaddc2a9ab653_96.webp" width="32"> | **Ender Nexus**          | Highly configurable /home, /spawn, /warp, /tpa and /rtp commands all in one, and individually disablable. | [![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white)](https://github.com/borisshoes/EnderNexus/) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/ender-nexus) [![CurseForge](https://img.shields.io/badge/CurseForge-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/ender-nexus-fabric-teleports)          |
| <img src="https://cdn.modrinth.com/data/Z63eULDV/dae01789d609498b8f1637ab31d8fe20b6108020_96.webp" width="32"> | **Fabric Mail**          | An in-game virtual mailbox system for sending packages and messages between online and offline players.   | [![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white)](https://github.com/borisshoes/fabric-mail/) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/fabric-mail) [![CurseForge](https://img.shields.io/badge/CurseForge-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/fabric-mail)                          |
| <img src="https://cdn.modrinth.com/data/u40ARaBc/028062616fc2fb729afdbdc697d60f93ff61a918_96.webp" width="32"> | **Fabric Trade**         | Adds /trade, a secure player-to-player trading interface.                                                 | [![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white)](https://github.com/borisshoes/fabric-trade/) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/fabric-trade) [![CurseForge](https://img.shields.io/badge/CurseForge-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/fabric-trade)                       |
| <img src="https://cdn.modrinth.com/data/WdlqG9Gd/a401b9bf08c33d85c907025d6689c657b5168508_96.webp" width="32"> | **Limited AFK**          | AFK detection and management with configurable kick thresholds for servers.                               | [![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white)](https://github.com/borisshoes/LimitedAFK/) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/limited-afk) [![CurseForge](https://img.shields.io/badge/CurseForge-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/limited-afk)                           |
| <img src="https://cdn.modrinth.com/data/klpvLefw/97afbda2e56c3f14e04d0f9e0e1fe99db6bd2f27_96.webp" width="32"> | **Links in Chat**        | Makes URLs posted in chat clickable.                                                                      | [![GitHub](https://img.shields.io/badge/GitHub-181717?logo=github&logoColor=white)](https://github.com/borisshoes/fabric-linksinchat/) [![Modrinth](https://img.shields.io/badge/Modrinth-00AF5C?logo=modrinth&logoColor=white)](https://modrinth.com/mod/links-in-chat) [![CurseForge](https://img.shields.io/badge/CurseForge-F16436?logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/links-in-chat)               |


### Translation Credits:
- Russian - Reset1712

### LICENSE NOTICE
By using this project in any form, you hereby give your "express assent" for the terms of the license of this project, and acknowledge that I, BorisShoes, have fulfilled my obligation under the license to "make a reasonable effort under the circumstances to obtain the express assent of recipients to the terms of this License.