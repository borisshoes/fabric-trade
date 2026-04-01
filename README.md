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

### Translation Credits:
- Russian - Reset1712

### LICENSE NOTICE
By using this project in any form, you hereby give your "express assent" for the terms of the license of this project, and acknowledge that I, BorisShoes, have fulfilled my obligation under the license to "make a reasonable effort under the circumstances to obtain the express assent of recipients to the terms of this License.