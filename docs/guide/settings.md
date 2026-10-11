# Settings

Everything here starts on with sensible numbers, so most servers never need to touch it. This page is for whoever runs
the server.

## Where to change them

- **On a server:** the mod writes its settings file, `config/aliveworkplace.json`, the first time it starts. Change a
  value and restart the server. A number out of range is set to the nearest allowed one.
- **In your own worlds:** with Mod Menu installed, the mod's **Configure** button opens the same settings as sliders and
  on/off buttons, each with a tooltip. Closing the screen saves them.
- **Per world:** a few builder settings are game rules, changed in game with `/gamerule` (no restart).

![The settings screen](https://jcondedata.github.io/minecraft-alive-workplace/config/still-1.jpg)

## Switch a part of village life off

Every part of village life has its own on/off switch. Turn one off and villages simply go without it; nothing else needs
looking after. On the settings screen they are:

| Setting | When it's on |
|---|---|
| Villager Names | villagers in a village with a hall get names |
| Villager Traits | villagers have traits (diligent, lazy, nimble...) |
| Moods | villagers' moods change how fast they work |
| Sickness | villagers fall ill now and then (a Nurse cures them) |
| Weddings | villagers court, marry and mourn |
| Villager Chatter | villagers now and then say something to players nearby |
| Festivals | villages hold a festival every eight days (a cake still calls one when it's off) |
| Market Days | a village with a Market Square holds a weekly market |
| Village Quests | halls post quests for players |
| Village Treasury | villages put by takings every morning |
| Village Protection | a hall's owner may protect their village from other players |
| Monster Raids | monsters raid bigger villages at night |
| Bandit Camps | bandits make camp near villages and raid them until their chief falls |
| Builder Repairs | idle builders repair what they built |
| Builder Paths | builders lay a dirt path from each new building to the village centre |
| Villages Work Unseen | villages keep working while no player is near (as long as someone is online) |

## Tune the numbers

| Setting | Normally | What it changes |
|---|---|---|
| Chest Reach | 8 blocks | how close chests must be to a workstation to be its supply chests |
| Build Distance | 48 blocks | how far from their table a builder takes a build |
| Guard Patrol Range | 24 blocks | how far from their post guards fight |
| Lumberjack Range, Orchard Range, Fisherman Range | 16 blocks | how far those workers look for trees, fruit and water |
| Explorer Range | 48 blocks | how far explorers go on an expedition |
| Partner Reach | 16 blocks | how close pastured Pokémon must be to help a worker |
| Postman Round | 64 blocks | how far a postman walks (farther mail arrives at dawn) |
| Village Size | 48 blocks | workers this close together share their chests |
| Village Hall Reach | 64 blocks | how far from its hall a village reaches |
| Population Cap | 40 | a village stops having babies at this many villagers |
| Days per Season | 16 | how long each of the four seasons lasts |
| Daily Takings | a fifth of an emerald | what each worker brings the treasury a day |
| Fastest Work Pace | twice the usual | the most all bonuses together can speed a worker up |
| CobbleDollar Rate | 100 | CobbleDollars per emerald for shops, lessons and fares |
| Threat Level | 2 | how much trouble comes to villages: 0 none at all, 1 gentle (half as often), 2 normal, 3 hard (half again as often, and a quarter more raiders). On Peaceful nothing comes. |

## For a big server

Three settings keep many villages light on the server: **Workers per Village** (a limit on workers in one village; nobody
loses a job they have), **Worker Path Range** (how far workers plan a walk at once) and **Villages Work Unseen** (off:
villages nobody is near take a break).

## Builder game rules

Change these in game with `/gamerule <name> <value>`:

| Game rule | What it does |
|---|---|
| `workplaceFreeMaterials` | true: builders need no materials (handy for creative towns) |
| `workplaceBuildDelay` | how long a builder takes per block (normally 8; lower is faster) |
| `workplaceBuildersHelp` | false: idle builders stop helping with other builds |
| `workplaceBuilderOwnership` | false: anyone can give orders to any builder |
| `workplaceFoundationDepth` | how far down builders fill under a build on uneven ground (normally 12; 0: never) |
| `workplaceLevelGround` | how many blocks round a build get levelled (normally 2; 0: leave the ground alone) |
| `workplaceAllowUploads` | false: only operators can upload blueprint files |
| `workplaceVillageFarms` | false: village farmers don't take on the farm by their composter by themselves |
| `workplaceKeepWorkLoaded` | false: builds and quarries stop when nobody is near them |

## Commands for everyone

| Command | What it does |
|---|---|
| `/workplace friend add <name>` | lets a friend give orders to your builders and open your things (`remove` and `list` too) |
| `/workplace sites` | your builds in progress, with a cancel button |
| `/workplace cancel <build>` | stops a build; what's built stays and you get the blueprint back |
| `/workplace mail` | where your parcels are |
| `/workplace strip <height>` | with a Quarry Marker in hand: a strip mine at that height |

Operators also have `/workplace blueprint <name>` to get any blueprint, and `/workplace threat` for events at the
nearest Village Hall: `start monsters` (or `bandits`) sends a raid now, `lair bandits` sets a bandit camp down by the
village, `end` sends the raiders away, and `clear` clears the village of raiders, camps and the raids it was due.
