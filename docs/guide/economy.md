# Economy

```infobox
type: Trade
expansion: M33
image: https://jcondedata.github.io/minecraft-alive-workplace/price_board/still-1.jpg
caption: The Trade page's Prices tab
Where: the Prices tab, behind the minecart on the Village Hall's screen
Goods: 25 kinds, 28 with Cobblemon
Counted: once a day, at the hall's first round after dawn
Known for and short of: up to 3 goods each
Prices: move a third of the way to where supply and demand point, each dawn
Never: below half or above twice a good's base price
Money: emeralds to the cent, or CobbleDollars with that mod
categories: Trade, Village Hall
related: price-board.md, caravan-sales.md, trade-and-caravans.md, treasury.md
```

Every village with a [Village Hall](village-hall.md) runs a little economy. It knows what it's good at, what it lacks,
and what everything is worth today, and you can buy and sell with it.

## Known for, short of

Once a day each village looks at itself: its workers' jobs, the land its hall stands in, what's in its
[Storehouse](storehouse.md) and what its workers are waiting for. From that it is **known for** up to three goods and
**short of** up to three, never both for the same good.

- **Known for:** goods its workers make (two workers of a making job count for a lot), goods its land is good for, and
  goods it holds two bundles or more of.
- **Short of:** goods its land wants, goods its workers use and are waiting for, and goods it holds less than a bundle
  of.

The hall's name icon says so ("Known for: Timber, Wool. Short of: Bread"), and villagers chat about it: a good that
sells well in a partner village, a glut at home, something dear.

## Prices

Every good has a base price for a bundle, and every village its own price for it. Each dawn the price moves a third of
the way toward where supply and demand point:

- **Supply** goes up with bundles in the Storehouse (up to five) and with being known for it.
- **Demand** goes up with being short of it, with workers waiting for it, and for food when the store holds fewer than
  16 meals. After a raid, arms and armor, arrows and remedies are wanted for three days; while anyone is ill, remedies
  too.

A price never falls below half its base or climbs above twice it. Trading at the board moves it 2% a bundle as well.
Money reads in emeralds to the cent (a good may cost less than one emerald), or in CobbleDollars with that mod (100 to the emerald unless the
[CobbleDollar Rate](settings-cobbledollar-rate.md) says otherwise).

## The goods

A bundle is how much of a good trades at once. The base price is in emeralds.

| Good | Bundle | Base | Made by | Wanted by |
|---|---|---|---|---|
| Grain | 20 | 1 | Farmer | Chef, Rancher |
| Bread | 6 | 1 | Farmer, Chef | Innkeeper |
| Roots | 22 | 1 | Farmer | Chef |
| Fish | 6 | 1 | Fisherman | Chef, Innkeeper |
| Meat | 5 | 1 | Butcher, Rancher | Chef |
| Fine Meals | 4 | 1 | Chef | Innkeeper, Nurse, Bard |
| Fruit | 12 | 1 | Orchard Keeper | Chef, Composter |
| Honey | 4 | 1 | Beekeeper | Nurse, Chef, Cleric |
| Bone Meal | 16 | 1 | Composter | Farmer, Florist, Orchard Keeper |
| Timber | 16 | 1 | Lumberjack | Builder, Carpenter |
| Stone | 64 | 1 | Miner, Mason | Builder, Mason |
| Glass | 8 | 1 | Mason | Builder |
| Bricks and Clay | 10 | 1 | Mason, Sifter | Builder, Mason |
| Coal | 15 | 1 | Miner | Armorer, Chef, Mason, Fisherman |
| Iron | 4 | 1 | Miner, Armorer | Armorer, Toolsmith, Weaponsmith, Tinkerer |
| Gold | 3 | 1 | Miner | Tinkerer, Undertaker, Ball Smith |
| Wool | 18 | 1 | Shepherd | Leatherworker, Innkeeper |
| Leather | 6 | 1 | Butcher, Rancher | Leatherworker, Librarian |
| Dyes and Flowers | 12 | 1 | Florist, Leatherworker | Leatherworker |
| Paper | 24 | 1 | Librarian, Cartographer | Scholar, Teacher, Librarian, Postman |
| Tools | 1 | 2 | Toolsmith | Miner, Lumberjack, Farmer, Builder |
| Arms and Armor | 1 | 3 | Armorer, Weaponsmith | Guard |
| Arrows | 16 | 1 | Fletcher | Guard |
| Remedies (healing potions) | 1 | 2 | Cleric | Nurse, Guard |
| Nether Goods | 8 | 1 | Netherworker | Cleric, Tinkerer, Mason |
| Apricorns (Cobblemon) | 8 | 1 | Orchard Keeper | Ball Smith |
| Berries (Cobblemon) | 12 | 1 | Orchard Keeper | Nurse, Rancher |
| Poké Balls (Cobblemon) | 8 | 1 | Ball Smith | Trainer, Pokémon Trader |

## Where the money goes

- **At the board.** Players buy and sell bundles at a village's [price board](price-board.md), paid from and into its
  [treasury](treasury.md).
- **By caravan.** A village's caravans also [sell](caravan-sales.md) what it's known for where it's short or pays
  more.
- **Across a realm.** A [realm](realms.md)'s levy takes a share of each member's takings, and its research and edicts
  can make trade cheaper and faster.
- **Between players.** [Trade pacts](trade-pacts.md) make caravans to other players' villages paid work;
  [feuds](feuds.md) make your rivals pay a quarter more.
- **Bragging rights.** The [weekly leaderboard](weekly-leaderboard.md)'s Richest board counts what a village earned.

## How it fits in

- **What stops it:** **Village Economy** off in the settings screen. Nothing is counted, the minecart opens the trade
  routes page as before, and the last prices are kept for when it's back on.
- **What it needs:** a Village Hall and a Storehouse with a chest by it.
