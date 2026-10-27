# Miner

```infobox
type: Job
image: https://jcondedata.github.io/minecraft-alive-workplace/quarry/still-2.jpg
caption: A miner digging out a quarry
Workstation: Blast Furnace
Pick it with: a pickaxe
Works at: a quarry you mark with a **Quarry Marker** (a stick, red dye and paper), within 64 blocks of the furnace
Quarry size: up to 32 by 32, 16 deep unless you change it; up to 64 for every miner, deeper as they level up
Strip mine: tunnels up to 64 long and 32 wide
Needs: a pickaxe (or cobblestone and sticks to make one), torches (and ladders for a strip mine down a shaft)
Makes: stone, ore, dirt and whatever else the ground holds
Pokémon helpers: Ground, Rock, Steel
categories: Jobs
related: jobs-land.md, porter.md, armorer.md, toolsmith.md, builder.md, quarry-build.md
```

The Miner digs. Mark out a hole, hand it over, and a villager takes the ground apart layer by layer and fills your chests
with stone and ore while you do something more fun.

## Getting a miner

Put down a **blast furnace**, stand a villager within about 4 blocks of it and sneak-right-click them holding any
**pickaxe**. They're a Miner now. Chests and barrels within 8 blocks of the blast furnace are their supply chests: the
pickaxes and torches come out of them, and everything they dig goes into them.

## Marking the quarry

1. Craft a **Quarry Marker** from a stick, red dye and paper (or buy one from a miner for an emerald).
2. Right-click one corner block, then the opposite one. Each side can be up to 32 blocks.
3. Sneak-right-click the air to change how deep they dig (16 to start with), or to switch to a **strip mine**.
   Sneak-right-click a block to start over. Every miner digs 4 to 64 deep; the deeper choices need a seasoned miner
   (see below).
4. Hand the marker to the miner. They tell you what to put in the chests.

The quarry has to be within 64 blocks of their blast furnace, and it can't swallow the furnace itself.

## A day at the quarry

The miner digs from the top down, back and forth across each layer. They light the pit with torches from the chests
every few blocks, cut a **spiral stair** into the wall of any pit 3 by 3 and 3 deep or more so you can climb out, and keep
the pit dry by leaving the blocks next to water and lava standing. Everything they dig goes into the chests: stone,
cobblestone, deepslate, andesite, diorite, granite, tuff, gravel, dirt and the ores. When the chests are full, or there
are none, the rest goes to the village Storehouse, and ores always get the room first (a stack of stone moves on to the
Storehouse to make way). When they finish, they tell you how many blocks they
dug and how many they left (next to water or lava, or too hard for their pickaxe).

Over their head you see the quarry's size and how far along it is. Sneak-right-click them with an empty hand for the
full status and a **[Stop this quarry]** button: they stop and give the marker back, and what was dug stays dug.

## Digging deeper

Miners dig deeper as they level up. Every miner digs up to 64 blocks down; the marker also offers:

| Depth | Who digs it |
|---|---|
| 96 blocks | Journeyman miners and up |
| 128 blocks | Expert miners and up |
| Down to just above bedrock | Master miners |

Hand a deeper marker to a miner who isn't there yet and they still take it, but they stop at their own depth (64 for
a Novice), and tell you which level the full depth needs. If they level up before they finish, they keep going down
to their new depth. If they finish first, hand them the marker again once they've leveled up. The spiral stair goes
all the way to the bottom, and they still leave the blocks next to water and lava standing.

![A Master miner at the bottom of a pit 96 blocks deep](https://jcondedata.github.io/minecraft-alive-workplace/deep_quarry/still-3.jpg)

## When the pickaxe wears out

A miner looks after their own tools. When their pickaxe breaks (or they never had one), they look in their chests for
another; if there's none, they walk back to the blast furnace and **make one** out of what they dug: three cobblestone
(cobbled deepslate or blackstone do too) and two sticks. The sticks can come from their chests or the village store,
and two planks will do instead. The line over their head says "making a pickaxe", you hear that they made one, and
they go straight back to digging.

![Making a new pickaxe at the blast furnace](https://jcondedata.github.io/minecraft-alive-workplace/miner_pickaxe/still-4.jpg)

As they gain levels they make better ones: an **iron pickaxe** from Journeyman when there are three iron ingots in
their chests (their blast furnace smelts some for them), a **diamond pickaxe** from Expert with three diamonds. They
always make the best one they can.

Nothing to make one from? They wait by the furnace ("needs a pickaxe"), and the **Storehouse** board asks for what's
missing, the cobblestone first and then the sticks. Hand it over there and they get to work. If you'd rather they
always wait for you or the Toolsmith, turn off **Miners Make Pickaxes** in the settings.

## A quarry that grows back

Build the [Quarry](quarry-build.md) from the Blueprint Table and the Miner at its shed's blast furnace digs its pit by
themselves, no marker needed. What they dig grows back slowly from the bottom up, so the pit never runs out.

## Strip mines

A strip mine takes just the ore. The miner digs tunnels two blocks high with two blocks of rock between them, and mines
every ore in that rock. Mark the corners at head height for tunnels at that height, or set a depth and they first dig a
**ladder shaft** down from the corner nearest the blast furnace (put ladders in the chests). The marker tells you which
ores live at each height: iron around Y=16, redstone, gold and lapis around Y=-16, diamonds near Y=-53. Holding the
marker, you can also type `/workplace strip <height>`.

## How the miner fits in

- **Fed by:** they make their own stone pickaxes; a [Toolsmith](toolsmith.md) makes one when they can't, and a
  [Weaponsmith](weaponsmith.md) mends worn ones. A [Librarian](librarian.md) with an enchanting table enchants their
  pickaxe.
- **Feeds:** the [Porter](porter.md) carries the stone and ore to the storehouse, but leaves the torches, ladders and
  32 of each kind of stone so the miner can patch the stairs. With coal in the chests, the miner's own blast furnace
  smelts the ore; an [Armorer](armorer.md) fetches the rest and smelts it for the village. The
  [Builder](builder.md) takes stone from the miner's chests or the storehouse when a build is short.
- **Stops when:** there's no pickaxe in the chests and nothing to make one from ("needs a pickaxe"), the blast furnace is broken, or a strip mine
  down a shaft has no ladders.

## Trading

Miners buy coal, raw copper and raw iron, and sell Quarry Markers, torches, an iron pickaxe and, at Master, a diamond
pickaxe.

See also [Jobs on the land](jobs-land.md) and [Giving villagers jobs](jobs.md).
