# Config switches

Every setting of the mod in one place: the server's config file, the ten game rules, and how unfinished expansions
stay off. Each feature's own page repeats the switches that matter to it.

Roadmap items: 25.5, B76, B119, B131, B161, 36.15, 36.20

## What a player sees

**The config file.** `config/aliveworkplace.json` is written with the defaults the first time the game starts. Edit
it and restart. A value out of range is clamped to the nearest allowed one, however large (3000000000 gives the most allowed, B477), a missing value takes its default, and
the file is written back complete, so every option is always listed. A number or a switch written in quotes ("1",
"false") is read as meant, and an empty value (null) takes its default (`QaConfigFileGameTests`). The same holds for
the two keys the mod keeps for itself: an empty or odd file format number ("configVersion": null, words, a list) is
read as a file without one, and entries of "expansionsOpen" that aren't names (null, a list) are skipped, so the rest
of the file is still read (B457). A value of the wrong kind (a switch written 0, a number written "high") takes its
default alone, with a warning in the log naming it, and every other value in the file is kept and written back
(B411, `QaConfigFileBugTests`). A file that can't be read at all (empty, cut short, not a `{...}` object) gives the
defaults and is written over with them, but is first kept beside it as aliveworkplace.json.bad, so a hand edit is
never lost (a list, a bare word or a number instead of `{...}` too: B477, `QaConfigHandEditsBugTests`; an empty file, spaces, null or true too, and 2147483648 or -2147483649 clamp like any number past the range: `QaFixEdges1027GameTests`); nothing in the file stops the game from starting. In the lists and maps an
empty entry takes its default (a Wonder cap, a disaster or raider culture switch: on) and a cap past its range is
clamped (`QaConfigOddFilesGameTests`). A bad list or map costs only that option; comments and a byte-order mark are read as meant, while a
trailing comma makes the file unreadable (the defaults, the file kept aside); a second start reads exactly what the first wrote (`QaConfigHandEditsGameTests`).

**The settings screen.** With Mod Menu installed, the mod's Configure button opens the same settings as sliders and
on/off buttons, each with a tooltip. Every setting's name fits its 150-pixel button at its widest value
(`ConfigGameTests.everySettingsLabelFitsItsButton` measures them with vanilla's font widths, B161). Closing the screen saves the file and puts the settings into effect in your own
worlds. A dedicated server keeps its own file.

**Game rules.** Per-world tuning of builders uses vanilla's `/gamerule`, so it can differ from world to world and be
changed without a restart.

**Expansions open together in 1.0.** A switch that belongs to an expansion (the "Expansion" column) is off whatever
the file says, and isn't on the settings screen, while that expansion's gate is closed. 1.0 opens every gate (1.1 to
2.0) at once (owner, 2026-10-13); from then on these switches default on and obey the file.

## How it works

- The file is read once at start-up (`WorkplaceConfig`), clamped, written back and applied: each value is handed to
  the system that uses it.
- **The expansion gate (B76).** Each expansion (1.1 to 2.0) has one flag in the code
  (`Expansions.M27` to `M35`) that the release finishing it turns on; 1.0 turns all nine on together. Until then its switches are forced off, even in a config file that says on. The release flips the flag
  and nothing else: the switches then default on and obey the file again.
- **A file from before the release (34.23).** The file records which expansions were open when it was written
  (a list in the file; files written by 0.139.0 to 0.142.0 have none, so none were). A switch of an expansion that was
  still closed then holds `false` only because that was its default, so once the expansion is out it takes its new
  default, on; a switch turned off after the release stays off. A file with neither the list nor a format number
  (written by hand) is taken as it says.
  Without this, a server that ran an earlier version kept 1.8's classes off (found by the update rehearsal).
- **Builds wait too (B119).** A build that belongs to an expansion (the Arena and the Pokémon job builds, the Guildhall,
  the Fire Station, Well House and Physician's House, the luxury workshops) is tied to its job's switch
  (`StarterBlueprints.expansionSwitch`). While that switch's expansion is closed the Blueprint Table, its creative tab
  and `/workplace blueprints` leave it out and the Table refuses it by id; it still loads by id, so a blueprint already
  taken and a build site keep working.
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

| Key | Default | Expansion | What it does |
|---|---|---|---|
| `supplyRadius` | 8 (2 to 32) |  | Chests and barrels within this many blocks of a workstation are that villager's supply chests. |
| `maxSiteDistance` | 128 (16 to 256) |  | How far from their Blueprint Table (or bench) a builder takes a build outside the village; anywhere in the village the table stands in is always in reach (36.20). Also how far upkeep looks. |
| `quarryReach` | 128 (16 to 256) |  | How far from their Miner's Bench a miner takes a quarry outside the village (anywhere in the village is in reach). |
| `fieldReach` | 128 (16 to 256) |  | How far from their composter a farmer takes a marked field outside the village (anywhere in the village is in reach). |
| `treeFarmReach` | 128 (16 to 256) |  | How far from their Chopping Block a lumberjack takes a tree farm outside the village (anywhere in the village is in reach). |
| `orchardAreaReach` | 128 (16 to 256) |  | How far from their workstation an orchard keeper takes a marked orchard outside the village (anywhere in the village is in reach). |
| `berryPlotReach` | 128 (16 to 256) |  | How far from their workstation a berry breeder takes a berry plot outside the village (anywhere in the village is in reach). |
| `guardRadius` | 24 (8 to 64) |  | How far from their workstation guards patrol and fight. |
| `lumberjackRadius` | 16 (4 to 48) |  | How far from their workstation lumberjacks cut. |
| `orchardRadius` | 16 (4 to 48) |  | How far from their workstation orchard keepers pick. |
| `fisherRadius` | 16 (4 to 48) |  | How far from their workstation fishermen look for water. |
| `explorerRange` | 48 (16 to 128) |  | How far from their cartography table explorers go on an expedition. |
| `partnerRadius` | 16 (4 to 48) |  | How far from a workstation pastured Pokémon count as partners. |
| `postmanRange` | 64 (16 to 256) |  | How far from their Mailbox a postman walks to deliver (farther mail arrives at dawn). |
| `maxWorkersPerVillage` | 0 (0 to 500) |  | At most this many workers per village (taken workstations within the village radius); 0: no cap. Nobody loses a job they have. |
| `randomStationJobs` | on |  | A jobless villager who takes a block several jobs share (a composter, a lectern, a Shop Counter...) by themselves gets one of its jobs at random, likelier the fewer of it the village has (36.17). Off: always the block's vanilla job, as before. See [Random jobs at shared blocks](random-jobs.md). |
| `minerCraftsPickaxe` | on |  | A Miner whose pickaxe wore out, or who has none, makes a new one at their workstation from what they mined (stone, iron from Journeyman, diamond from Expert). Off: they wait for a pickaxe from you or the Toolsmith. |
| `quarryBuild` | on |  | The Quarry build (36.12): the Miner at its blast furnace digs its pit without a Quarry Marker, and what they dig grows back slowly from the bottom up. Off: the Quarry isn't offered and nothing grows back; the Quarry Marker works as before. |
| `villageWaystones` | on |  | With Waystones installed, the Steward proposes a waystone plaza by the Village Hall in the village's own style, its waystone named after the village and renamed with it (41.1). Off: the plaza isn't proposed; stones already standing stay. Without Waystones it does nothing. See [Waystones](waystones.md). |
| `quarryRegrowth` | 100 (0 to 2400) |  | Blocks that grow back in a Quarry's pit in a Minecraft day (0: none). At 100 a pit dug to the bottom takes about a week to fill. |
| `fishermanCap` | on |  | A village takes only so many fishermen: other jobless villagers pass free barrels (storage in builds) by for another workstation. A fisherman a village already has keeps his job. Off: as in vanilla. |
| `fishermenPerVillage` | 1 (1 to 20) |  | With the fisherman cap on, the fishermen every village takes. |
| `villagersPerFisherman` | 10 (1 to 100) |  | With the fisherman cap on, one more fisherman for every this many villagers within the village radius. |
| `workerPathRange` | 48 (16 to 128) |  | How far villagers with a job look for a path in one go (vanilla: 48). |
| `villageRadius` | 48 (0 to 128) |  | Workers whose workstations are this close together are one village and share their chests. |
| `villageHallRadius` | 64 (16 to 160) |  | How far from a Village Hall its village reaches. |
| `keepVillagesWorking` | on |  | Villages keep working while no player is near them (their chunks stay loaded while anyone is online). |
| `builderPaths` | on |  | Whether builders lay a dirt path from each finished building to the village's bell or hall. |
| `villageGrowthCap` | 40 (0 to 500) |  | A village with a hall stops having babies at this many villagers (0: villages don't grow, and the hall says so). |
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
| `repairAsk` | on |  | A builder who finds blocks missing from a finished build asks first (Repair it? Yes / Not now, in chat and on the Village Hall's Repairs page) and repairs only what was agreed (36.15). Off: they repair by themselves, as before. What raiders and village fires took is always repaired without asking. |
| `marketDays` | on |  | A village with a Village Hall and a Market Square holds a market once a week. |
| `villageRaids` | on |  | Monsters raid bigger villages with a Village Hall at night now and then. |
| `banditCamps` | on |  | Bandits make camp near villages of Village rank or more now and then, and raid them until their chief falls. |
| `threatLevel` | 2 (0 to 3) |  | How hard the village's threats are, beside each system's own switch: 0, none at all (no raids, lairs, sieges or disasters); 1, gentle (half as often, no sieges, no hexes, no plague); 2, normal; 3, hard (half again as often, a quarter more raiders). On Peaceful nothing comes whatever it says. See [Peaceful settings and the threat level](peace.md). |
| `forceThreatSpawns` | on |  | A raider, bandit or camp mob another mod refuses to spawn (a mob blocklist such as MobsBeGone, which Cobbleverse ships with pillagers and zombies on it) is put in the world anyway, so camps and raids aren't empty (B208). Off: such mobs are left out, and the server log says which. |
| `forceVillageSpawns` | on |  | The village's own iron golems (Golem Smith, Iron Pact), relief riders' horses, the Beastmaster's foals and wolf pups and a caravan's pack llamas, when another mod refuses them (MobsBeGone, which Cobbleverse ships with golems, wolves, horses, cats and llamas on it), are put in the world anyway (B211). Off: they are left out, and the server log says which. Vanilla's own spawns are never forced. |
| `gravesToGraveyard` | on |  | The Undertaker moves each grave lying about the village (a street, a field, a doorstep) to the village's Graveyard, in rows, and still brings villagers back from there; a village with no Graveyard gets a small plot near the hall (B222). Off: graves stay where villagers fall. |
| `campRuins` | off |  | A bandit camp or other raiders' lair that is broken up (its captain killed) or leaves stays as ruins, its fire put out, instead of coming down a few seconds later with its chests' loot spilled (B209). |
| `raiderCultures` | every culture on |  | One on/off switch per raider culture, by its id (`monsters`, `bandits`, and any a data pack adds); a culture set to false never raids or makes camp. `villageRaids` and `banditCamps` still switch `monsters` and `bandits`. In the file only: a map isn't on the settings screen. |
| `disasters` | every disaster on |  | One on/off switch per disaster, by its id (ours by their path, a data pack's by its whole id); one set to false never starts, and one under way ends at once (32.15). In the file only: a map isn't on the settings screen. |
| `sieges` | on | 1.6 | A raid by raiders who ram gates on a village with a finished wall or gate build is a siege: the gates shut, the portcullis drops and the rams go for the gate; raiders with ladders set them against the walls (32.5). Off: every raid is a plain one, with no rams and no ladders. See [Sieges](sieges.md). |
| `firewardens` | on | 1.6 | Villagers at a grindstone can be made Firewardens with a water bucket: they put out the village's fires. Off: no Firewarden job, and Firewardens already hired stand idle. |
| `wellKeepers` | on | 1.6 | Villagers at a cauldron can be made Well Keepers with a shovel: they water the fields in a drought and keep the cauldrons full and the farmland wet between droughts. Off: no Well Keeper job, and Well Keepers already hired stand idle. |
| `physicians` | on | 1.6 | Villagers at a brewing stand can be made Physicians with a glistering melon slice: they go house to house in a plague, send the ill home and cure them with Remedies. Off: no Physician job, and Physicians already hired stand idle. |
| `siegeDamage` | on | 1.6 | In a siege, rams break the gate blocks of finished wall and gate builds (builders put them back). Off: gates hold however hard they are hit; the vanilla mobGriefing game rule off does the same. |
| `festivals` | on |  | Villages with a Village Hall hold a festival every eight days (players can still call one with a cake). |
| `villagerChatter` | on |  | Villagers near a player now and then say something about their day, over their heads. |
| `villagerCouples` | on |  | Villagers court, marry (a wedding at the bell) and mourn. |
| `villageTreasury` | on |  | Villages with a Village Hall put by takings every morning for players to collect at the hall. |
| `villageQuests` | on |  | Village Halls post quests for players. Off: no new quests; open ones can still be finished. |
| `questbookKeepEdits` | off |  | With FTB Quests: on, the questbook chapters already on the server are kept as they are; off, each start brings the new version of every chapter (a deleted chapter stays deleted). |
| `blueprintUnlocks` | on |  | The Blueprint Table is a collection (26.10): each village starts with the basics and unlocks the rest (buying, builders' level-ups, questbook rewards, blueprints used on the table). Off: every build is unlocked, as before. |
| `friendship` | on | 1.5 | Named villagers keep a friendship with each player, shown in hearts. Off: no points, no hearts shown; saved friendship stays. |
| `heartEvents` | on | 1.5 | At 2, 4, 6, 8 and 10 hearts a villager walks up when they're off work and tells you something about their life; it becomes part of their life story at the hall. Off: nobody starts telling; what was told stays. See [Heart events](heart-events.md). |
| `personalRequests` | on | 1.5 | A villager with 3 hearts or more may walk up and ask you for help (a level before the festival, a home, a taste of home, the tools of their trade); helping earns friendship, a missed deadline costs some. Off: nobody asks; requests already accepted can still be finished. See [Personal requests](personal-requests.md). |
| `reputation` | on | 1.5 | Your deeds earn you a standing in each village (quests, requests, story chapters, bounties, raids, festivals, gifts) and harm to its people costs it; enough makes you a Friend (50), a Hero (300) or a Lord (1000, in a Town). Off: nothing is earned, lost, told or shown; saved standings stay. See [Reputation and titles](reputation.md). |
| `titlesInChat` | on | 1.5 | Your title in the village you stand in (else your best anywhere) goes before what you say in chat. Off: chat lines are left alone; titles still count and are told. See [Reputation and titles](reputation.md). |
| `storyArcs` | on | 1.5 | Story arcs (roadmap 31.4) unfold in villages, chapter by chapter. Off: none starts, and a running one ends quietly at its next round. |
| `bountyBoard` | on | 1.5 | From Village rank, villages post bounties on named bandit captains (two at most, one every 3 days, 7 days each). Off: nothing new is posted; open bounties still pay. See [The bounty board](bounties.md). |
| `rogueBounties` | on | 1.5 | With Cobblemon, half the bounties are on a rogue Pokémon: huge, level 35 to 65 by rank, roaming 150 to 250 blocks out; beaten or caught it pays 16 emeralds times the rank factor. Off: no rogue is posted. Never without Cobblemon. See [The bounty board](bounties.md). |
| `arcCooldownDays` | 8 (0 to 60) | 1.5 | Days between two story arcs in one village (also before a village's first). |
| `arcsAtOnce` | 3 (0 to 20) | 1.5 | Story arcs running at once on the whole server (side arcs not counted); 0: none. |
| `lostCaravanChance` | 20 (0 to 1000) | 1.5 | One caravan in this many on a road over 300 blocks goes missing and starts the Lost Caravan; 0: none. |
| `disabledArcs` | empty |  | Story arc ids that never start, not even by `/workplace story start` (it says the arc is switched off); a running one ends at its next round. In the file only. |
| `villageProtection` | on |  | A Village Hall's owner may protect the village from other players (a setting on the hall, off until they turn it on). |
| `partnerShows` | on | 1.2 | Pokémon pastured by a workstation are seen helping at work (with Cobblemon): they carry, water, spark... |
| `nurseHealingMachine` | on | 1.2 | A nurse at Cobblemon's Healing Machine heals your team in it, and keeps it charged while on shift. |
| `maxWorkPace` | 200 (100 to 400) |  | How fast every bonus together can make a worker, in percent of the usual pace. Sickness and bad moods still slow them after that; their level doesn't count. |
| `villageEdicts` | on | 1.4 | Villages' owners proclaim edicts at the hall (off: none can be, and those in force do nothing but stay saved). |
| `workHorns` | on | 1.4 | The Work Horn calls a rush when blown in a village. Off: it only sounds. |
| `villageBanners` | on | 1.4 | Village Banners can be crafted and set a village's colors at its hall. Off: neither; colors stay saved. |
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
| `realms` | on | 1.7 | A Town or better can found a realm on its hall's Realm tab and add its owner's (and friends') villages within 2,048 blocks; colonies join their mother's realm. Off: no Realm tab, nothing founded or joined; realms stay saved and come back. See [Founding a realm](realms.md). |
| `realmMaxMembers` | 12 (2 to 32) | 1.7 | Villages one realm may hold. |
| `realmRelief` | on | 1.7 | When a realm village is raided, the capital and members within 1,024 blocks send riders, an emerald each from the realm treasury. |
| `tradePacts` | on | 1.7 | A caravan route to another player's village needs a trade pact, offered and accepted on the hall's Pacts tab; pact caravans are paid for at board prices unless the sender gives. Off: routes need no pact. See [Trade pacts and alliances](pacts.md). |
| `alliances` | on | 1.7 | Allied villages of different owners send relief riders to each other's raids within 1,024 blocks. Off: no alliances offered, no riders. |
| `feuds` | on | 1.7 | An owner can declare a feud on another player's village or realm on the Pacts tab: pacts and caravans end, each board pays the rival's people 25% less and charges them 25% more; peace by letter, or it fades after 7 days. Off: no feuds; existing ones end quietly. See [Feuds and war](feuds.md). |
| `feudWars` | on | 1.7 | With PvP on, a feud can become a war of up to 3 days in which guards fight the other side's owners inside their own village. Off: no wars, even with PvP on. |
| `realmMap` | on | 1.7 | A member's Realm tab draws the realm on an empty map: every member's banner in the realm's color, colonies, allies and rivals in their own, the caravan routes dotted, a colony on the road as a cross. Off: no map button. See [The Realm Map](realm-map.md). |
| `weeklyLeaderboard` | on | 1.7 | Once a week everyone online hears the Biggest, Richest, Happiest, Most Legends and Greatest realm; winners wear a ribbon for a week. Off: nothing counted or announced. See [The weekly leaderboard](leaderboard.md). |
| `leaderboardDay` | `sunday` | 1.7 | The day the leaderboard is read out, `monday` to `sunday`, server time. In the file only. |
| `leaderboardHour` | 19 (0 to 23) | 1.7 | The hour the leaderboard is read out, server time. |
| `villageClasses` | on | 1.8 | Households in villages with a hall climb the class ladder. Off: no classes; classes and progress stay saved. |
| `classRiseDays` | 2 (1 to 30) | 1.8 | Dawns running the next class's needs must hold for a household to rise one class. |
| `classFallDays` | 3 (1 to 30) | 1.8 | Dawns running a need of their own class must fail for a household to fall one class. |
| `classGraceDays` | 7 (0 to 30) | 1.8 | Days a village settles in after classes are switched on there: nobody falls a class, luxuries aren't needed yet, nobody retires. |
| `vintners` | on | 1.8 | Villagers at a cauldron can be made Vintners with sweet berries, glow berries or an apple. Off: no Vintner job, and Vintners already hired stand idle. |
| `tailors` | on | 1.8 | Villagers at a loom can be made Tailors with string. Off: no Tailor job, and Tailors already hired stand idle. |
| `printers` | on | 1.8 | Villagers at a cartography table can be made Printers with an ink sac. Off: no Printer job, Printers already hired stand idle, and no Spread the news quest goes up. |
| `villagerAges` | on | 1.8 | Grown villagers count their days and become elders (a slower walk, a quiet old age, their own talk). Off: nobody is an elder, so nobody passes; the day each grew up stays saved. See [Elders](elders.md). |
| `villagerElderDays` | 1200 (20 to 10000) | 1.8 | Grown days before a villager is an elder (36.16: 120 before; a file of before with 120 moves to 1200). |
| `villageMourning` | on |  | A death matters (36.16): the village is told who died, their job and years, their family grieve 3 days (lower mood, slower work), and the Undertaker leads a funeral at the grave. Off: none of it (a partner still mourns). |
| `elderPassing` | on | 1.8 | Elders pass in their sleep after 400 elder days and leave a grave. Off: nobody dies of old age, and an Evergreen Charm is refused as not needed. |
| `agelessElders` | on | 1.8 | Evergreen Charms work: a good elder who takes one never passes. Off: charms are refused and kept; elders already ageless stay ageless. |
| `classOutfits` | on | 1.8 | Villagers wear their class over their job's outfit, and elders gray brows and a beard. Off: only the job's outfit is drawn. |
| `elderRetirement` | on | 1.8 | An elder with a job retires once a jobless villager can take over (one a village every 3 days) and mentors Novices and Apprentices of their old trade. Off: nobody starts retiring; those retired stay retired. |
| `familyNames` | on | 1.8 | Every villager in a village with a hall has a family name, shown on the hall ("Dara (Ashby)") and in the chronicle; children take a parent's. Off: no family names are shown (they are still kept); the Book of Families, the Family page and printed trees go by first names. See [Elders](elders.md). |
| `wonders` | on | 2.0 | Towns and Cities can raise Wonders. Off: no new Wonders; finished ones keep their blocks, their powers stop, and everything stays saved. See [Wonders](wonders.md). |
| `wonderCaps` | 0, 0, 1, 2 (each 0 to 8) | 2.0 | Wonders a village may hold by rank: Hamlet, Village, Town, City. In the file only; a short list is filled from the defaults. |
| `wonderLegends` | on | 2.0 | A Wonder needs its Legends in the village. Off: each Legend's stand-in is asked for instead. |
| `builderScaffolding` | on | 2.0 | Builders put up scaffolding to reach what has nowhere to stand near it, and take it down after. Off: those blocks are skipped. |
| `wonderServerLimit` | 0 (0 to 64) | 2.0 | How many villages on the server may each build the same kind of Wonder; 0 is no limit. |
| `disabledWonders` | empty | 2.0 | Wonder ids that can't be started (`great_cathedral` or `pack:id`); one already finished keeps its blocks and powers. In the file only. |
| `wonderCrew` | 12 (1 to 32) | 2.0 | The most builders on one Wonder's crew, its lead included; a Wonder's own file may allow fewer. See [Wonders](wonders.md). |
| `wonderStanding` | 90 (50 to 100) | 2.0 | The share (%) of a finished Wonder that must still stand for its powers; under it the Wonder is damaged, its powers stop and the village's builders repair it. See [Wonders](wonders.md). |
| `furnishedBuilds` | on from 2.1 | 2.1 | Builds use the furniture mods the server has (Handcrafted, Lucky's Cozy Home, CobbleFurnies, Beautify) for chairs, tables, beds, lamps and blinds. Off: every build is the vanilla build. See [Furniture and storage mods](furniture-and-storage.md). |
| `furnitureMods` | handcrafted, cozyhome, cobblefurnies, beautify | 2.1 | The order the furniture mods are tried in for each piece; a mod that isn't installed is passed over, an empty list means vanilla. In the file only. |
| `moddedStorageNetworks` | on from 2.1 | 2.1 | A Sophisticated Storage controller or a Tom's Simple Storage connector in the Storehouse counts as one store for everything it links. Off: each linked chest is its own store. |
| `moddedLibrary` | on from 2.1 | 2.1 | Homes and workshops are built from the modded library (the same builds dressed with Rechiseled, Supplementaries, Carved Wood, Chipped and Handcrafted blocks) when all of those mods are installed and `furnishedBuilds` is on; a build keeps the library it was planned with. Off, or a mod missing: the vanilla library. See [Furniture and storage mods](furniture-and-storage.md). |
| `denHelpers` | on | 2.2 | Players hire the village's trainers from the hall's Defense page to help in a raid at a den the village knows (Cobblemon Raid Dens): they follow, cheer once a raid with a cheer item each, and wear the boss down. Off: no hiring, and those hired walk home. Replaced denWardens, denChallenges and denChallengeDays (38.1a). See [Raid dens, Megas and Cobblecuisine](raid-dens-megas-cuisine.md). |
| `denHelperPrice` | 5 (1 to 64) | 2.2 | Emeralds one raid helper costs. |
| `villageMegas` | on | 2.2 | With Mega Showdown, a City's Trainer Leaders, the Festival Cup's finalists and the Stadium Day's visitors carry a Mega Stone too, and every trainer who can Mega Evolve wears a Mega Bracelet; the Jeweler cuts Mega Stones and Key Stones, Miners take Key Stone ore and Mega Stone Crystals to the store, and beating every Trainer Leader of a realm earns a Mega Bracelet (38.6); the Mega Shrine is at the Blueprint Table and holds a festival show (38.7). Off: only Masters Mega Evolve, nobody wears a bracelet, the village makes no Mega Stones and the Mega Shrine isn't listed. See [Raid dens, Megas and Cobblecuisine](raid-dens-megas-cuisine.md). |
| `cuisineDishes` | on | 2.2 | With Cobblecuisine, the Chef cooks the dishes players ask for on the hall board's Dishes tab (38.8a; locked until the village has a Chef) from their own recipes by his level (salads and sandwiches first, drinks, curries, cakes), each counts as its food group for the villagers' diet, the Camp Cook cooks three of its curries and the Innkeeper serves its drinks. Off: the menus and the diet are as before. See [Raid dens, Megas and Cobblecuisine](raid-dens-megas-cuisine.md). |
| `beanFarms` | on | 2.2 | With Cobblecuisine, farmers sow its beans when the store holds fewer than 16 of a color a dish on the menu needs, and harvest them when fully grown, into the store. Off: farmers never sow beans. See [Raid dens, Megas and Cobblecuisine](raid-dens-megas-cuisine.md). |
| `furnicrafters` | on | 2.2 | With CobbleFurnies, a jobless villager by its Furni Crafter becomes the Furnicrafter, who makes the CobbleFurnies furniture the builders are waiting for and the storehouse's stock orders ask for, from the store's materials. Off: no new Furnicrafters, and those hired stand idle. See [The Furnicrafter](furnicrafter.md). |
| `mechanics` | on from 2.3 | 2.3 | With Create installed, a jobless villager by a Create Toolbox becomes the village's Mechanic: they put back shafts, cogwheels and belts missing from finished builds and keep the millstones fed and emptied. Off: no Mechanic job, and Mechanics already hired stand idle. Without Create it does nothing. See [Create](create.md). |
| `machineWork` | on from 2.3 | 2.3 | With Create installed, villagers work Create's machines: the farmer mills wheat into flour, the armorer presses ingots into sheets, the chef mixes dough and the miner crushes raw ore, taking the input from the store and bringing back what's made, only while the machine turns and the store has room. Off: nobody feeds a machine. Without Create it does nothing. See [Create](create.md). |
| `createBuilds` | on from 2.3 | 2.3 | With Create installed, the Blueprint Table offers the Watermill, the Windmill, the Workshop, the Brass Foundry, the Sawmill, the Crushing Mill, the Greenhouse, the Clock Tower and the Pump House, workplaces whose machines turn as soon as they're built. Off: they aren't offered (sites already started still finish). Without Create it does nothing. See [Create](create.md). |
| `createSchematics` | on from 2.3 | 2.3 | With Create installed, the Blueprint Table lists the schematics players uploaded with Create's Schematic Table (each becomes a blueprint when a table opens), and a written Schematic used on the table imports its file; blocks of mods that aren't installed are counted and named. Off: the table leaves Create's schematics alone (those already imported stay). Without Create it does nothing. See [Create](create.md). |
| `villageTrains` | on from 2.3 | 2.3 | With Create installed, the Blueprint Table offers the Train Station; two villages with a caravan route and a Create station each get Create track laid along the road between them by their builders, and their caravans go by train (a third of the trip, never lost). A jobless villager by a Train Station becomes the village's Conductor, who drives its train and calls its stops. Off: no station at the table, no track laid, no new Conductors, caravans walk (a train already on its way still comes in). Without Create, or with `caravanRoads` off, it does nothing. See [Create](create.md). |
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
| `jewellers` | on | 1.8 | Villagers at a stonecutter can be made Jewelers with a gold nugget. Off: no Jeweler job, and Jewelers already hired stand idle. |
| `gemGrowers` | on | 1.2 | Villagers at a stonecutter can be made Gem Growers with an amethyst shard. Off: no Gem Grower job. |
| `habitatSightings` | on | 1.2 | Habitat Keepers tell the village of shiny, rare and Alpha wild Pokémon near their pasture. |
| `villageHabitats` | on | 1.2 | An Expert Habitat Keeper puts one Habitat Block in a finished Habitat Garden, with Cobblemon 1.8. Off: none founded. |
| `pokemonVillageHouses` | on | 1.2 | With Cobblemon, villages grow a Pokémon Center, Camp Kitchen, Berry Nursery, Daycare and Gem Grotto, each with its worker. Off: they don't (from the next server start). |
| `festivalCup` | on | 1.2 | With Cobblemon, a village with a hall, a finished Arena and Village rank holds its festivals as a Festival Cup. Off: no Cups. |
| `cupEveryFestivals` | 1 (1 to 8) | 1.2 | Which of a host's festivals are Cups: every one (1), every second (2), and so on. |
| `seasonDays` | 16 (1 to 120) |  | Days in each of the village calendar's four seasons (each has a festival on its middle day). |
| `treasuryPerWorker` | 20 (0 to 500) |  | Hundredths of an emerald each worker brings the treasury a day (before wellbeing and rank). |
| `dollarsPerEmerald` | 100 (1 to 10000) |  | What an emerald price comes to in CobbleDollars (lessons, shops, fares). |
| `retrainPrices` | 5, 10, 20, 35, 50 (each 0 to 1000) |  | Emeralds to retrain a villager who has a job as a Builder, by their level: Novice to Master (26.6; in CobbleDollars, times `dollarsPerEmerald`). In the file only; a short list is filled from the defaults. |

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
| `workplaceLevelGround` | 2 (0 to 8) | Blocks around a finished build that get leveled; 0: leave the ground alone |
| `workplaceVillageFarms` | true | A village farmer with no field takes on the farm by their composter, once a chest is near it |
| `workplaceRetraining` | true | A villager who has a job can be retrained for a price: to any job from the Village Hall's Change Job page (36.18), or as a Builder at a free Blueprint Table (26.6); false: the old rule, only jobless villagers |

## Saved data

- The config file itself, plus one private field, the file's format number (2). A file without it was written
  before seasons grew to 16 days: its season length of 8 is read as "the old default" and becomes 16.
- Game rules are saved by vanilla with each world.
- Switching a system off never erases what it saved: edicts, guilds, classes, Legends, friendship and colors all
  stay in the save and come back when the switch is on again.

## Items, blocks, jobs, commands

None. The texts of the settings screen are in the lang file as `aliveworkplace.config.supplyRadius` and
`aliveworkplace.config.supplyRadius.tooltip` (one pair per option); the game rules' as
`gamerule.workplaceBuildDelay`.

## Decisions

- B76 (owner, 2026-10-05): an expansion's switches stay off until every item of its milestone is done, so a
  half-built expansion never reaches a player. Version 0.139.0 had written them on; the gate overrides such files.
- 34.23: files written while a gate was closed hold `false` for its switches; the release that opens it treats that as
  the old default, not a choice, so updating switches the expansion on.
- 25.5: caps like MineColonies' for server owners, and every "needs" system easy to switch off, because
  "babysitting" is the top complaint about big colony mods.
- 23.6 (owner's call): villages keep working when no player is near; `keepVillagesWorking` is the way out.
- 30.2 (owner's call): every speed bonus together stops at twice the usual pace by default (`maxWorkPace`).
- 28.1a (owner): every festival of a qualifying host is a Festival Cup (`cupEveryFestivals` is 1).
- 22.6 (owner, 2026-10-04): seasons are 16 days.
- Per-world tuning of builders stays in game rules; server-wide distances and switches are in the file.

## Known limits

- The file is read at start-up: a change needs a restart (or the settings screen, in your own worlds).
- Lists (`mythicLegendCap`, `disabledArcs`, `wonderCaps`, `disabledWonders`, `furnitureMods`, `retrainPrices`) can only be edited in the file.
- A dedicated server's settings don't follow a player's own file.
- The build delay and the foundation depth have no upper limit; a build delay of 1 is the fastest.

## Proof

GameTests: `ConfigGameTests` (4: the file is read, clamped and applied; every radius and village number; every
setting has its words and range; saved settings are read back), `ExpansionGateGameTests` (4: an old config with the
switches on leaves them off; unfinished switches default off and are hidden; tests can open the gates; a file from before
the release takes the new defaults, a later choice holds),
`ExpansionBuildsGameTests` (3: the Table holds no 1.2 build while 1.2 is off and holds them when on; the other
expansions' builds wait the same way; the Table refuses a closed expansion's build by id),
`HallSpecGameTests.theConfigsSwitchesOutsideTests`, `ProtectionSpecGameTests.theConfigSwitchesProtectionOff`,
`RoadGameTests.withStewardRoadsOffNothingIsBuilt`, `WallGameTests.withStewardWallsOffNothingIsProposedOrBuilt`,
`CaravanRoadGameTests.withCaravanRoadsOffNoRoadIsPlanned`, `GiftedGameTests.giftedChanceZeroMeansNobody`.

Showcase scenes: `config` (the settings screen).
