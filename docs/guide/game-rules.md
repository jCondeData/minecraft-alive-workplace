# Game rules

```infobox
type: Setting
How: /gamerule <name> <value>, in game, no restart
Who: operators
Scope: each world on its own
workplaceFreeMaterials: false
workplaceBuildDelay: 8 ticks a block
workplaceBuildersHelp: true
workplaceBuilderOwnership: true
workplaceFoundationDepth: 12 blocks
workplaceLevelGround: 2 blocks (0 to 8)
workplaceAllowUploads: true
workplaceKeepWorkLoaded: true
workplaceVillageFarms: true
workplaceRetraining: true
categories: Settings, Builder
related: settings.md, builder.md, command-import.md, settings-worker-limits-and-performance.md
```

Ten of the mod's settings are **game rules**: they belong to the world, not the server's settings file, and an
operator changes them in game with `/gamerule <name> <value>`. They take effect at once. Most are about builders.

## The rules

| Game rule | Normally | What it does |
|---|---|---|
| `workplaceFreeMaterials` | false | true: builders place blocks without needing materials. Handy for creative towns. |
| `workplaceBuildDelay` | 8 | ticks a builder spends on each block (20 ticks are a second). Lower is faster; 1 is the fastest. |
| `workplaceBuildersHelp` | true | builders with nothing to do help with builds near their table. false: each builder only works on their own. |
| `workplaceBuilderOwnership` | true | a builder only takes orders from the player who hired them, their friends and operators. false: anyone can give orders to any builder. |
| `workplaceFoundationDepth` | 12 | how deep builders fill under a build that stands on uneven ground. 0: no foundations. |
| `workplaceLevelGround` | 2 | how many blocks round a finished build the builder levels: ground sticking up is dug away, holes are filled with dirt. 0 to 8; 0 leaves the ground alone. |
| `workplaceAllowUploads` | true | any player can upload blueprint files at a Blueprint Table. false: only operators can. |
| `workplaceKeepWorkLoaded` | true | builds and quarries keep running while the player who ordered them is online but far away. false: they stop when nobody is near. |
| `workplaceVillageFarms` | true | a village farmer with no field takes on the farm by their composter by themselves, once there's a chest by the composter for the harvest. false: you give farmers their fields yourself. |
| `workplaceRetraining` | true | a villager who already has a job can be retrained as a Builder for emeralds at a Blueprint Table ([Giving villagers jobs](jobs.md)). false: only jobless villagers become Builders. |

For example, `/gamerule workplaceBuildDelay 4` makes every builder in the world twice as fast.

## How they interact

- **Speed.** `workplaceBuildDelay` sets the base speed; a builder's level, the village's [work pace](work-pace.md)
  bonuses and Pokémon partners all speed them up from there.
- **Friends.** With `workplaceBuilderOwnership` on, add friends with [/workplace friend](command-friend.md) so they can
  give your builders orders too.
- **Uploads.** `workplaceAllowUploads` is about the Blueprint Table's **Upload a File** button. Operators can always
  bring in files from the server's folder with [/workplace import](command-import.md).
- **Loaded work.** `workplaceKeepWorkLoaded` is only about builds and quarries. Whole villages have their own switch,
  **Villages Work Unseen** ([Worker limits and performance](settings-worker-limits-and-performance.md)). Nothing is kept
  loaded for players who are offline.
- **Per build.** Right-click the air with a placed blueprint to switch leveling off for that one build (see
  [The Builder and blueprints](builder.md)).
