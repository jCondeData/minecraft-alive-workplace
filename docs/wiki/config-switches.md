# Config switches

Every setting of the mod in one place: the server's config file, the nine game rules, and how unfinished expansions
stay off. Each feature's own page repeats the switches that matter to it.

Roadmap items: 25.5, B76

## What a player sees

**The config file.** `config/aliveworkplace.json` is written with the defaults the first time the game starts. Edit
it and restart. A value out of range is clamped to the nearest allowed one, a missing value takes its default, and
the file is written back complete, so every option is always listed.

**The settings screen.** With Mod Menu installed, the mod's Configure button opens the same settings as sliders and
on/off buttons, each with a tooltip. Closing the screen saves the file and puts the settings into effect in your own
worlds. A dedicated server keeps its own file.

**Game rules.** Per-world tuning of builders uses vanilla's `/gamerule`, so it can differ from world to world and be
changed without a restart.

**Unfinished expansions stay off.** A switch that belongs to an expansion that isn't released yet (the "Off until"
column) is off whatever the file says, and isn't on the settings screen until then.

## How it works

- The file is read once at start-up (`WorkplaceConfig`), clamped, written back and applied: each value is handed to
  the system that uses it.
- **The expansion gate (B76).** Each expansion that has switches (1.1 to 1.5, 1.7 and 1.8 today) has one flag in the code
  that the release finishing it turns on. Until then its switches are forced off, even in a config file that says on. The release flips the flag
  and nothing else: the switches then default on and obey the file again.
- **Every "needs" system has its own switch** (names, traits, moods, sickness, couples, chatter, markets,
  festivals, raids, bandit camps, the treasury, repairs, paths). Set it to false and the village simply goes without
  it; nothing else needs looking after.
- **For a big server**, the three that keep villages light are `maxWorkersPerVillage`, `workerPathRange` and
  `keepVillagesWorking`.
- In the mod's own tests, the systems that roll dice or act on a timer (names, traits, moods, sickness, raids,
  festivals, markets, classes, strange moods and so on) are held off unless a test turns them on, so one test can't
  move another's numbers. That is why a test report never shows them running by chance.

## Switches

The config file (`config/aliveworkplace.json`). "on" and "off" are written true and false in the file. A number's
range is in brackets.

| Key | Default | Off until | What it does |
|---|---|---|---|
| `supplyRadius` | 8 (2 to 32) |  | Chests and barrels within this many blocks of a workstation are that villager's supply chests. |
| `maxSiteDistance` | 48 (16 to 256) |  | How far from their Blueprint Table (or bench) a builder takes a build; also how far upkeep looks. |
| `guardRadius` | 24 (8 to 64) |  | How far from their workstation guards patrol and fight. |
| `lumberjackRadius` | 16 (4 to 48) |  | How far from their workstation lumberjacks cut. |
| `orchardRadius` | 16 (4 to 48) |  | How far from their workstation orchard keepers pick. |
| `fisherRadius` | 16 (4 to 48) |  | How far from their workstation fishermen look for water. |
| `explorerRange` | 48 (16 to 128) |  | How far from their cartography table explorers go on an expedition. |
| `partnerRadius` | 16 (4 to 48) |  | How far from a workstation pastured Pokémon count as partners. |
| `postmanRange` | 64 (16 to 256) |  | How far from their Mailbox a postman walks to deliver (farther mail arrives at dawn). |
| `maxWorkersPerVillage` | 0 (0 to 500) |  | At most this many workers per village (taken workstations within the village radius); 0: no cap. Nobody loses a job they have. |
| `workerPathRange` | 48 (16 to 128) |  | How far villagers with a job look for a path in one go (vanilla: 48). |
| `villageRadius` | 48 (0 to 128) |  | Workers whose workstations are this close together are one village and share their chests. |
| `villageHallRadius` | 64 (16 to 160) |  | How far from a Village Hall its village reaches. |
| `keepVillagesWorking` | on |  | Villages keep working while no player is near them (their chunks stay loaded while anyone is online). |
| `builderPaths` | on |  | Whether builders lay a dirt path from each finished building to the village's bell or hall. |
| `villageGrowthCap` | 40 (0 to 500) |  | A village with a hall stops having babies at this many villagers (0: villages don't grow). |
| `villagerNames` | on |  | Villagers in a village with a Village Hall get names. |
| `villagerTraits` | on |  | Villagers have traits (diligent, lazy, nimble...). |
| `villagerSickness` | on |  | Villagers in a village with a Village Hall fall ill now and then (a Nurse cures them). |
| `villagerMoods` | on |  | Villagers in a village with a Village Hall have moods that change how fast they work. |
| `legends` | on | 1.3 | Legends (rare named villagers with powers) can come to villages that earn them. |
| `legendNeeds` | on | 1.3 | A settled Legend needs a home of their own, their luxury and a happy village, and strikes without them. |
| `legendSites` | on | 1.3 | Legends can be found at ruined portals, pillager outposts and shipwrecks (a camp set down for a player who qualifies). |
| `strangeMoods` | on | 1.3 | Once a day a Master in a happy village may be taken by a strange mood, asking for three rare materials to make a Masterwork and become a Legend. |
| `giftedChance` | 30 (0 to 1000) | 1.3 | One villager in this many is Gifted, with a rare trait (0: nobody is; nothing is erased). |
| `mythicLegendCap` | 0, 0, 1, 2 |  | Mythic Legends a village may hold, by its rank: Hamlet, Village, Town, City (each 0 to 10). In the file only: a list isn't on the settings screen. |
| `builderRepairs` | on |  | Idle builders repair the buildings they finished when blocks go missing. |
| `marketDays` | on |  | A village with a Village Hall and a Market Square holds a market once a week. |
| `villageRaids` | on |  | Monsters raid bigger villages with a Village Hall at night now and then. |
| `banditCamps` | on |  | Bandits make camp near villages of Village rank or more now and then, and raid them until their chief falls. |
| `raiderCultures` | every culture on |  | One on/off switch per raider culture, by its id (`monsters`, `bandits`, and any a data pack adds); a culture set to false never raids or makes camp. `villageRaids` and `banditCamps` still switch `monsters` and `bandits`. In the file only: a map isn't on the settings screen. |
| `festivals` | on |  | Villages with a Village Hall hold a festival every eight days (players can still call one with a cake). |
| `villagerChatter` | on |  | Villagers near a player now and then say something about their day, over their heads. |
| `villagerCouples` | on |  | Villagers court, marry (a wedding at the bell) and mourn. |
| `villageTreasury` | on |  | Villages with a Village Hall put by takings every morning for players to collect at the hall. |
| `villageQuests` | on |  | Village Halls post quests for players. Off: no new quests; open ones can still be finished. |
| `friendship` | on | 1.5 | Named villagers keep a friendship with each player, shown in hearts. Off: no points, no hearts shown; saved friendship stays. |
| `heartEvents` | on | 1.5 | At 2, 4, 6, 8 and 10 hearts a villager walks up when they're off work and tells you something about their life; it becomes part of their life story at the hall. Off: nobody starts telling; what was told stays. See [Heart events](heart-events.md). |
| `storyArcs` | on | 1.5 | Story arcs (roadmap 31.4) unfold in villages, chapter by chapter. Off: none starts, and a running one ends quietly at its next round. |
| `arcCooldownDays` | 8 (0 to 60) | 1.5 | Days between two story arcs in one village (also before a village's first). |
| `arcsAtOnce` | 3 (0 to 20) | 1.5 | Story arcs running at once on the whole server (side arcs not counted); 0: none. |
| `disabledArcs` | empty |  | Story arc ids that never start; a running one ends at its next round. In the file only. |
| `villageProtection` | on |  | A Village Hall's owner may protect the village from other players (a setting on the hall, off until they turn it on). |
| `partnerShows` | on | 1.2 | Pokémon pastured by a workstation are seen helping at work (with Cobblemon): they carry, water, spark... |
| `nurseHealingMachine` | on | 1.2 | A nurse at Cobblemon's Healing Machine heals your team in it, and keeps it charged while on shift. |
| `maxWorkPace` | 200 (100 to 400) |  | How fast every bonus together can make a worker, in percent of the usual pace. Sickness and bad moods still slow them after that; their level doesn't count. |
| `villageEdicts` | on | 1.4 | Villages' owners proclaim edicts at the hall (off: none can be, and those in force do nothing but stay saved). |
| `workHorns` | on | 1.4 | The Work Horn calls a rush when blown in a village. Off: it only sounds. |
| `villageBanners` | on | 1.4 | Village Banners can be crafted and set a village's colours at its hall. Off: neither; colours stay saved. |
| `cradles` | on | 1.4 | A Cradle near a bed makes a nursery village: children grow up twice as fast, one more baby a day. Off: cradles are furniture. |
| `harvestIdols` | on | 1.4 | Harvest Idols: in harvest season the crops within 32 blocks of one grow 25% faster. Off: idols are ornaments. |
| `tonics` | on | 1.4 | Tonics: the alchemist and the chef make them and villagers drink them. Off: neither; a tonic drunk does nothing. |
| `guilds` | on | 1.4 | Guilds: Guild Charters make Masters Guild Masters, and founded guilds' perks reach their members. Off: charters are refused and perks are off; guilds stay saved. |
| `guildsPerRank` | 1 (1 to 4) | 1.4 | Guilds a village may have per rank above Hamlet (Village 1x, Town 2x, City 3x). |
| `edictMinDays` | 3 (0 to 30) | 1.4 | Days an edict stays in force before it can be lifted. |
| `villageEconomy` | on | 1.7 | Once a day every village with a hall works out the goods it's known for and short of, and a price for each good. Off: nothing is worked out; the last prices are kept. See [The price board](price-board.md). |
| `visibleCaravans` | on | 1.7 | A caravan leaving or arriving in a village with a player within 96 blocks is seen: a carter leading two pack llamas between the Storehouse and the village's edge. Off: none is shown; the goods travel the same. See [Caravans you can see](visible-caravans.md). |
| `colonies` | on | 1.7 | A City can found a sister village: the hall's Trade page gets a Colonies tab that sells a Colony Charter, whose map chooses where the colony goes. Off: no tab, no charters, and a charter chooses no spot. See [The Colony Charter](colony-charter.md). |
| `colonyRank` | `city` | 1.7 | The rank a village needs to buy a Colony Charter: `hamlet`, `village`, `town` or `city`. In the file only: a word isn't on the settings screen. |
| `colonyCooldownDays` | 7 (0 to 60) | 1.7 | Days a village waits after founding a colony before the next. |
| `coloniesPerVillage` | 3 (0 to 16) | 1.7 | Colonies one village may found in all. |
| `villageClasses` | on | 1.8 | Households in villages with a hall climb the class ladder. Off: no classes; classes and progress stay saved. |
| `classRiseDays` | 2 (1 to 30) | 1.8 | Dawns running the next class's needs must hold for a household to rise one class. |
| `classFallDays` | 3 (1 to 30) | 1.8 | Dawns running a need of their own class must fail for a household to fall one class. |
| `vintners` | on | 1.8 | Villagers at a cauldron can be made Vintners with sweet berries, glow berries or an apple. Off: no Vintner job, and Vintners already hired stand idle. |
| `tailors` | on | 1.8 | Villagers at a loom can be made Tailors with string. Off: no Tailor job, and Tailors already hired stand idle. |
| `printers` | on | 1.8 | Villagers at a cartography table can be made Printers with an ink sac. Off: no Printer job, Printers already hired stand idle, and no Spread the news quest goes up. |
| `villagerAges` | on | 1.8 | Grown villagers count their days and become elders (a slower walk, a quiet old age, their own talk). Off: nobody is an elder, so nobody passes; the day each grew up stays saved. See [Elders](elders.md). |
| `villagerElderDays` | 120 (20 to 1000) | 1.8 | Grown days before a villager is an elder. |
| `elderPassing` | on | 1.8 | Elders pass in their sleep after 40 elder days and leave a grave. Off: nobody dies of old age, and an Evergreen Charm is refused as not needed. |
| `agelessElders` | on | 1.8 | Evergreen Charms work: a good elder who takes one never passes. Off: charms are refused and kept; elders already ageless stay ageless. |
| `berryBreeders` | on | 1.2 | Villagers at a composter can be made Berry Breeders with a Cobblemon berry. Off: no Berry Breeder job. |
| `steward` | on | 1.1 | A Journeyman Builder (or higher) by a Village Hall can be made its Steward with the hall's City Plan. Off: no new Stewards, and those appointed stand idle. |
| `stewardMaxOpenBuilds` | 4 (1 to 8) | 1.1 | The most builds a Steward may have open at once, whatever his level and the village's rank. |
| `stewardSelfRun` | on | 1.1 | A Steward set to "Run the village" starts the builds he proposes by himself. Off: every village asks first. |
| `stewardRoads` | on | 1.1 | A Steward's builders build the approved roads on the plan, and new buildings' doors join them with lanes. Off: roads are drawn but not built. |
| `caravanRoads` | on | 1.1 | Villages with a trade route each build their half of a road to the other, ending at a milestone if it stops short. Off: no roads between villages. |
| `caravanRoadReach` | 256 (32 to 512) | 1.1 | The longest half of a road a village builds towards another; it goes halfway at most. |
| `stewardWalls` | on | 1.1 | A raided village's Steward proposes a wall along the plan's wall line, built from a wall kit. Off: he never proposes walls. |
| `stewardRenewal` | on | 1.1 | A Steward rebuilds the old village houses in zones whose "renew old houses" switch is on, one at a time, in the zone's style. Off: he never proposes to renew a house. |
| `campCooks` | on | 1.2 | Villagers at a Campfire Pot can be made Camp Cooks with Hearty Grains. Off: no Camp Cook job. |
| `habitatKeepers` | on | 1.2 | Villagers at a Pasture Block can be made Habitat Keepers with a honey bottle. Off: no Habitat Keeper job. |
| `daycareKeepers` | on | 1.2 | Villagers at a Pasture Block can be made Daycare Keepers with an egg. Off: no Daycare Keeper job, and no eggs. |
| `jewellers` | on | 1.8 | Villagers at a stonecutter can be made Jewellers with a gold nugget. Off: no Jeweller job, and Jewellers already hired stand idle. |
| `gemGrowers` | on | 1.2 | Villagers at a stonecutter can be made Gem Growers with an amethyst shard. Off: no Gem Grower job. |
| `habitatSightings` | on | 1.2 | Habitat Keepers tell the village of shiny, rare and Alpha wild Pokémon near their pasture. |
| `villageHabitats` | on | 1.2 | An Expert Habitat Keeper puts one Habitat Block in a finished Habitat Garden, with Cobblemon 1.8. Off: none founded. |
| `pokemonVillageHouses` | on | 1.2 | With Cobblemon, villages grow a Pokémon Center, Camp Kitchen, Berry Nursery, Daycare and Gem Grotto, each with its worker. Off: they don't (from the next server start). |
| `festivalCup` | on | 1.2 | With Cobblemon, a village with a hall, a finished Arena and Village rank holds its festivals as a Festival Cup. Off: no Cups. |
| `cupEveryFestivals` | 1 (1 to 8) | 1.2 | Which of a host's festivals are Cups: every one (1), every second (2), and so on. |
| `seasonDays` | 16 (1 to 120) |  | Days in each of the village calendar's four seasons (each has a festival on its middle day). |
| `treasuryPerWorker` | 20 (0 to 500) |  | Hundredths of an emerald each worker brings the treasury a day (before wellbeing and rank). |
| `dollarsPerEmerald` | 100 (1 to 10000) |  | What an emerald price comes to in CobbleDollars (lessons, shops, fares). |

Game rules (per world, `/gamerule`):

| Rule | Default | What it does |
|---|---|---|
| `workplaceFreeMaterials` | false | Builders place blocks without needing materials (creative towns, testing) |
| `workplaceBuildDelay` | 8 (1 or more) | Ticks a builder spends on each block; lower is faster |
| `workplaceAllowUploads` | true | Any player may upload blueprint files at a Blueprint Table (operators always can) |
| `workplaceFoundationDepth` | 12 (0 or more) | How deep builders fill under a build on uneven ground; 0: no foundations |
| `workplaceBuildersHelp` | true | Idle builders help with builds near their bench |
| `workplaceBuilderOwnership` | true | A builder takes orders only from who hired it, their friends and operators |
| `workplaceKeepWorkLoaded` | true | Builds and quarries keep running while the player who ordered them is online but far away |
| `workplaceLevelGround` | 2 (0 to 8) | Blocks around a finished build that get levelled; 0: leave the ground alone |
| `workplaceVillageFarms` | true | A village farmer with no field takes on the farm by their composter, once a chest is near it |

## Saved data

- The config file itself, plus one private field, the file's format number (2). A file without it was written
  before seasons grew to 16 days: its season length of 8 is read as "the old default" and becomes 16.
- Game rules are saved by vanilla with each world.
- Switching a system off never erases what it saved: edicts, guilds, classes, Legends, friendship and colours all
  stay in the save and come back when the switch is on again.

## Items, blocks, jobs, commands

None. The texts of the settings screen are in the lang file as `aliveworkplace.config.supplyRadius` and
`aliveworkplace.config.supplyRadius.tooltip` (one pair per option); the game rules' as
`gamerule.workplaceBuildDelay`.

## Decisions

- B76 (owner, 2026-10-05): an expansion's switches stay off until every item of its milestone is done, so a
  half-built expansion never reaches a player. Version 0.139.0 had written them on; the gate overrides such files.
- 25.5: caps like MineColonies' for server owners, and every "needs" system easy to switch off, because
  "babysitting" is the top complaint about big colony mods.
- 23.6 (owner's call): villages keep working when no player is near; `keepVillagesWorking` is the way out.
- 30.2 (owner's call): every speed bonus together stops at twice the usual pace by default (`maxWorkPace`).
- 28.1a (owner): every festival of a qualifying host is a Festival Cup (`cupEveryFestivals` is 1).
- 22.6 (owner, 2026-10-04): seasons are 16 days.
- Per-world tuning of builders stays in game rules; server-wide distances and switches are in the file.

## Known limits

- The file is read at start-up: a change needs a restart (or the settings screen, in your own worlds).
- Lists (`mythicLegendCap`, `disabledArcs`) can only be edited in the file.
- A dedicated server's settings don't follow a player's own file.
- The build delay and the foundation depth have no upper limit; a build delay of 1 is the fastest.

## Proof

GameTests: `ConfigGameTests` (4: the file is read, clamped and applied; every radius and village number; every
setting has its words and range; saved settings are read back), `ExpansionGateGameTests` (3: an old config with the
switches on leaves them off; unfinished switches default off and are hidden; tests can open the gates),
`HallSpecGameTests.theConfigsSwitchesOutsideTests`, `ProtectionSpecGameTests.theConfigSwitchesProtectionOff`,
`RoadGameTests.withStewardRoadsOffNothingIsBuilt`, `WallGameTests.withStewardWallsOffNothingIsProposedOrBuilt`,
`CaravanRoadGameTests.withCaravanRoadsOffNoRoadIsPlanned`, `GiftedGameTests.giftedChanceZeroMeansNobody`.

Showcase scenes: `config` (the settings screen).
