# Tonics

Six drinks that make one kind of worker 25% faster for a day. The village's Cleric brews some and its Chef cooks the
others; the player hands them out. Part of 1.4 (Edicts and civic items): switched on in 1.0 with every other
expansion.

Roadmap items: 30.15, 30.16

## What a player sees

A Cleric with the makings in the chests by their brewing stand brews tonics into those chests; a Chef cooks theirs
after the menu. Take one and right-click a villager it suits: they drink it ("Bo drinks the Smith's Draught: 25%
faster for 20 minutes.") and their status line shows "25% faster (Smith's Draught, 20 min left)". A villager it
doesn't suit refuses it and you keep the bottle ("Ash has no use for Smith's Draught."). In creative the bottle isn't
used up. Players can't craft tonics.

| Tonic | Made by, from | For |
|---|---|---|
| Miner's Brew | Cleric: a glass bottle, glowstone dust, coal, sugar | Miners, Sifters, Netherworkers |
| Builder's Tea | Chef: a glass bottle, two sweet berries, sugar | Builders, Carpenters, Masons, Dyers |
| Smith's Draught | Cleric: a glass bottle, blaze powder, two iron nuggets | Armorers, Toolsmiths, Weaponsmiths, Tinkerers, Ball Smiths |
| Scholar's Infusion | Cleric: a glass bottle, an amethyst shard, glow berries | Scholars, Teachers, Librarians, Cartographers, Fossil Scientists |
| Harvest Cordial | Chef: a glass bottle, an apple, wheat, sugar | Farmers, Orchard Keepers, Florists, Beekeepers, Composters, Shepherds, Butchers, Ranchers, Chefs |
| Woodsman's Broth | Chef: a bowl, a cooked salmon, a carrot, a brown mushroom | Lumberjacks, Fletchers, Fishermen, Porters, Postmen |

There is no tonic for guards (they have the Cleric's potions), nurses, clerics, undertakers, innkeepers,
shopkeepers, ferrymen, bards, trainers, tutors or traders. Children drink none.

## How it works

**Tonics are data.** One file each in `data/aliveworkplace/tonics/`: the item, its maker (the alchemist, which is the
Cleric, or the chef), its ingredients, the jobs it suits, how much faster (25%) and for how long (24000 ticks, one
day). A data pack can make any item a tonic; a reload reads the files again.

**A day, not stacked.** A tonic works from the tick it is drunk until the tick it runs out: on its last tick it still
works, on the next it is gone. Drinking another starts the day again; tonics never stack. The 25% goes through the one
pace rule, so with guilds, edicts and the rest it can't pass the cap.

**Making.** The maker keeps 4 of each tonic in the chests by their station and makes more from those chests and the
store when there are fewer.

## Switches

| Key | Default | What it does |
|---|---|---|
| `tonics` | on | Off: nobody makes them, villagers refuse them ("Tonics are switched off on this server"), and one already drunk does nothing |

## Saved data

- On the villager: the attachment `tonic` (which tonic and the tick it runs out); absent when none was drunk. It is
  kept over a save and reload, with the same end.

## Items, blocks, jobs, commands

- Items: `aliveworkplace:miners_brew`, `aliveworkplace:builders_tea`, `aliveworkplace:smiths_draught`,
  `aliveworkplace:scholars_infusion`, `aliveworkplace:harvest_cordial`, `aliveworkplace:woodsmans_broth`.
- Texts: `message.aliveworkplace.tonic.drunk`, `message.aliveworkplace.tonic.no_use`,
  `message.aliveworkplace.tonic.disabled`, `pace.aliveworkplace.source.tonic`.
- No blocks, jobs or commands.

## Decisions

- 30.15: tonics are data files, so a server owner can make any item a tonic; players can't craft them, only the
  village's Cleric and Chef make them.
- 30.16: no tonic for guards, who already have the Cleric's potions, nor for the service trades (nurses, clerics,
  undertakers, innkeepers, shopkeepers, ferrymen, bards, trainers, tutors, traders).

## Known limits

- One tonic at a time: a second replaces the first rather than adding to it.
- Without a Cleric (or a Chef) and the makings in the chests by their station or the store, nobody makes them.

## Proof

GameTests: `TonicGameTests` (each tonic made by its maker, drunk by one of its jobs at 25% for a day, refused by a job
outside its list, the tooltips, a data-pack tonic, a reload, the cap, the switch off) and `QaTonicsGuildsGameTests`
(`QaTonicsGuildsGameTests.everyListedJobSuitsItsTonicAndTheLeftOutJobsNone`,
`QaTonicsGuildsGameTests.theRarerJobsDrinkAndTheTonicEndsOnItsLastTick`,
`QaTonicsGuildsGameTests.aTonicIsKeptOverASaveAndReload`).

Showcase scenes: `tonics` (all six).
