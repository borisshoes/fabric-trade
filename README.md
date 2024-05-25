# Fabric Trade

A simple server-sided mod to give players the ability to trade items safely with other players anywhere else on the server.

##### This mod should only be installed on a server.

### Player Commands
* ```/trade <player>``` Requests to trade with the specified player
* ```/tradeaccept [player]``` Accepts an incoming trade request from the specified player
* ```/tradedeny [player]``` Denies an incoming trade request from the specified player
* ```/tradecancel [player]``` Cancels an outgoing trade request to the specified player

### Admin Commands & Configuration
Configuration can be done through the properties file generated when loaded on a server or through commands.
* ```/tradeconfig cooldown <0+>``` Sets the number of seconds before a player can trade with another player again
* ```/tradeconfig timeout <0+>``` Sets the number of seconds before a trade request expires
* ```/tradeconfig cooldown-mode <WhoInitiated/BothUsers>``` Sets whether only the trade initiator must wait the cooldown, or both participants

### LICENSE NOTICE
By using this project in any form, you hereby give your "express assent" for the terms of the license of this project, and acknowledge that I, BorisShoes, have fulfilled my obligation under the license to "make a reasonable effort under the circumstances to obtain the express assent of recipients to the terms of this License.
