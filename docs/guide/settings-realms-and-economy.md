# Realm and economy settings

```infobox
type: Setting
expansion: M33
Village Economy: on
Caravans You Can See: on
Colonies: on
Colonies per Village: 3 (0 to 16)
Days Between Colonies: 7 (0 to 60)
Realms: on
Villages per Realm: 12 (2 to 32)
Relief in a Raid: on
Trade Pacts: on
Alliances: on
Feuds: on
Feud Wars: on
Realm Map: on
Weekly Leaderboard: on
Leaderboard Hour: 19 (0 to 23)
categories: Settings, Trade
related: settings.md, economy.md, realms.md, colonies.md
```

These settings shape how villages trade, spread and band together. See [Settings](settings.md) for where to change
them. Everything starts on; turning a switch off keeps what was saved, so it comes back when you turn it on again.

## Trade

- **Village Economy:** every village works out what it's known for, what it's short of and a price for each good, and
  the [price board](price-board.md) and [caravan sales](caravan-sales.md) run on it. Off: nothing is counted, the
  minecart opens the plain trade routes page, and caravans carry only what the other village waits for. See
  [Economy](economy.md).
- **Caravans You Can See:** a [carter and two pack llamas](caravan-carters.md) walk each caravan in and out when a
  player is within 96 blocks. Off: none is shown; the goods travel the same.

## Colonies

- **Colonies:** a City can buy a [Colony Charter](colony-charter.md) and found a [colony](colonies.md). Off: no Colonies
  tab or charters; a colony already on the road still arrives.
- **Colonies per Village:** how many colonies one village may found in all (0: none).
- **Days Between Colonies:** how long a village waits after founding one.
- In the config file only, `colonyRank` (`hamlet`, `village`, `town` or `city`) sets the rank a village needs.

## Realms

- **Realms:** a Town can found a [realm](realms.md) and gather villages into it. Off: no Realm tab; realms stay saved.
- **Villages per Realm:** the most members one realm may hold.
- **Relief in a Raid:** [relief riders](relief-riders.md) from the capital and nearby members. Off: nobody comes.
- **Realm Map:** the Realm tab's [Draw the Realm Map](realm-map.md) button.

## Other players' villages

- **Trade Pacts:** a route to another player's village needs a [trade pact](trade-pacts.md). Off: routes need no pact.
- **Alliances:** [alliances](alliances.md) can be offered and send riders.
- **Feuds:** [feuds](feuds.md) can be declared. Off: existing feuds end quietly.
- **Feud Wars:** a feud can become a war, on a server that also has PvP on.

## The leaderboard

- **Weekly Leaderboard:** the [weekly leaderboard](weekly-leaderboard.md), its ribbons and the
  [/workplace leaderboard](command-leaderboard.md) command.
- **Leaderboard Hour:** the hour it's read out, server time. The day is `leaderboardDay` (`monday` to `sunday`) in the
  config file.
