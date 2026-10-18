# Raid dens, Megas and Cobblecuisine

Cobblemon depth brings three of the pack's Pokémon mods into village life: Cobblemon Raid Dens (the village knows the
dens around it, guards watch them and its trainers challenge them), Mega Showdown (more village trainers Mega Evolve,
and the village makes Mega Stones and Key Stones) and Cobblecuisine (the kitchens cook its dishes and farmers grow its
beans). Part of 2.2, behind its gate (`Expansions.M38`). Built so far: the plan, the gate, the test mods, the dens
the village knows (38.2), Den Wardens (38.3), the village's den challenges (38.4), more Mega trainers with their
bracelets (38.5), the Jeweller's Mega Stones and Key Stones with the realm's Mega Bracelet (38.6) and the Mega
Shrine with its festival show (38.7); the full plan is the design note, docs/design/M38.md.

Roadmap items: 38.1, 38.2, 38.3, 38.4, 38.5, 38.6, 38.7

## What a player sees

With Cobblemon Raid Dens installed (and once 2.2 ships), the hall's Defense page has a row of the raid dens within 160
blocks of the hall, nearest first: "Raid den, tier 3, Fire, active" (the icon glows), or "resting" once it has been
beaten and hasn't come back yet, with where it is and the day it was found. With none known it says "No raid den
known". The village map marks each den on its square with a magenta banner named the same way, and its legend counts
the raid dens. A den shows up once someone has been near enough for its chunk to load; a broken den drops off the
list.

**Den Wardens (38.3).** Under each den on the Defense page, **Post a warden** sends the village's nearest free guard
there. By day the warden walks to the den and stands watch beside it, nudging wild Pokémon off the crystal; at dusk
they walk back to their post and their evening goes on as usual; next morning they go back. The slot then reads
"Warden: <name>", and a click recalls them to normal duty. When a watched den glows again after being beaten, the
chronicle says so: "The Fire raid den (40 blocks north) glows again". A player who joins a raid (or wins a
tier) within 16 blocks of a watched den gets the warden's cheer: one Attack, Defense or Heal Cheer, once a raid.

**Den challenges (38.4).** Every 3 days (`denChallengeDays`), by day, a village with a Trainer Leader or a Master
sends its best one (the highest rank; one who lost lately stays home) against an active den the village knows: up to
tier 3 for a Leader or a Master (tier 4 and up are for players: owner, 2026-10-18), the highest tier first, then the nearest. A den with a player within 8 blocks
is theirs, and nobody is sent to it. The challenge is settled at once, like a Stadium bout: 45% at one rank over the
den's tier, 15% more or less for each rank above or below (never under 10% or over 90%). Players in the village or
within 32 blocks of the den are told as the trainer sets off and how it ended, so they can walk over and watch the den
go dark. A win rolls the den's own tier loot (`cobblemonraiddens:raid/tier/<tier>`, as a Raid Pouch of that tier
would) into the village store, turns the den inactive (`is_active` false; Raid Dens' own reset brings it back,
`can_reset` untouched) and writes the chronicle: "<the trainer> beat the tier 3 Fire raid den and brought home 5
things". A loss leaves the den glowing, sends the trainer home tired for a day and writes "... lost to the tier
4 Fire raid den". Each try counts as a battle for the trainer and earns experience (more for a win).

**More Mega trainers (38.5).** With Mega Showdown installed, Masters still carry a Mega Stone, and now so do: a Trainer
Leader whose village (the nearest Village Hall) is a City, the two trainers of a Festival Cup's final (on the Cup's
day, until the final is decided; a delegate standing in for one too), and the Stadium Day's visiting trainers. One
Pokémon of their team holds its own Mega Stone (the first that has one; otherwise the last is swapped for one that
does, the same level and training), and their battle AI Mega Evolves it the first turn it can, at any tier. A Town's
Leader (or a Hamlet's or a Village's) holds none. In a Cup final the Mega Pokémon must fit the theme (types, stage,
banned labels); with no Mega Evolution fitting it (the Little Cup), the team stays as it is. Every trainer who can
Mega Evolve wears a **Mega Bracelet** over their outfit: a gold band round the left wrist with the rainbow Key Stone
set in it (`textures/entity/villager/mega_bracelet.png`, drawn by `tools/textures/art/megas.py`). Without Mega
Showdown there are no extra stones, no bracelets and nothing is sent.

**Mega Stones and Key Stones from the village (38.6).** With Mega Showdown, a Journeyman Jeweller (level 3) cuts, at the
stonecutter, from the makings in their own chest, the village store and the workers' chests:

| Makings | Makes | Level | Kept in the store |
|---|---|---|---|
| Key Stone Ore, 2 gold ingots, 4 emeralds | a Key Stone | 3 | 2 |
| a Mega Stone Crystal (the block, silk-touched), 2 gold ingots, 4 emeralds | a Mega Stone the village's trainers use | 3 | 1 of each |
| a Mega Stone (what a Mega Stone Crystal drops), 2 gold ingots, 4 emeralds | the same | 3 | 1 of each |
| 4 Mega meteorite ores (any mix, silk-touched), 2 gold ingots, 4 emeralds | the same | 4 | 1 of each |

"A Mega Stone the village's trainers use": the Jeweller looks at the trainers of their village (round the nearest
Village Hall, or within 48 blocks without one; visitors and Cup delegates left out), nearest first, and at the Pokémon
on each one's home team, and cuts the Mega Stone of the first such Pokémon whose stone the store doesn't hold yet
(both Charizardites for a Charizard). A village whose trainers have no Pokémon with a Mega Evolution gets no stones;
the Jeweller never floods the store with all 46. Missing makings go on the requests board, as for their other goods.

A **Miner** drops off what they dig of Key Stone ore (a Key Stone, or the ore itself with a silk-touch pickaxe), Mega
Stone Crystals (a Mega Stone, or the crystal) and Mega meteorite ores (silk-touched; without silk touch they drop
Cobblemon's evolution stones, which stay in the Miner's chest) in the **village store** (the hall's storehouses and
kitchens) for the Jeweller, instead of their own chest; what the store has no room for goes in their chest as before.

A player who beats the **Trainer Leader of every village of a realm** that has one gets a **Mega Bracelet** from the
capital's hall: it goes into their inventory (or drops at their feet when it's full) with "You've beaten every Trainer
Leader of the Barony of Oakmere! The hall of Oakmere gives you a Mega Bracelet." Before that, each new village's Leader
beaten tells them "Trainer Leaders of the Barony of Oakmere beaten: 1 of 3. ...". Once per player per realm. A Leader
beaten counts once and for good; a village counts as having a Leader when its hall's last round saw one (so a village
whose Leader left drops out of the count).

With Mega Showdown, the Blueprint Table lists the **Mega Shrine** among the decorations (38.7): Mega Showdown's pedestal
on a two-step dais of Mega meteorite bricks, with chiseled meteorite posts crowned with amethyst and soul lanterns (7 by
6 by 7, beauty 2). Without Mega Showdown it isn't listed (its blocks don't exist). On a festival day, for its first
hour and a half (from the festival's start, 1,500 ticks), a village with a finished shrine holds its show there: the
villagers off work gather in front of the shrine instead of at the square, and a village trainer (one who can Mega
Evolve first, else the nearest) climbs to the pedestal and Mega Evolves their ace: a flash, a burst of light, a beacon
chord, and "<trainer> Mega Evolves their ace at the Mega Shrine, and the village cheers!" in chat for the players near.
Every villager within 12 blocks is the happier for it: +3 mood ("watched a Mega Evolution at the shrine") as long as a
festival's good mood lasts (2 days). Once a festival; then the crowd goes back to the square for the dancing and
fireworks. Cobblecuisine comes with 38.8 to 38.10.

## How it works

Every piece reads the three mods by their ids, tags, data files and advancements only, each behind a check that the mod
is loaded; any Cobblemon class stays in `compat/cobblemon/`. Without a mod, its part of the expansion doesn't exist and
nothing else changes.

The gate: `Expansions.M38` is false until the release that finishes the milestone, so its six switches (denWardens,
denChallenges, denChallengeDays, villageMegas, cuisineDishes, beanFarms) stay off and hidden for players
whatever a config file says. GameTests open the gate. Each switch comes into the config with the item that builds it.

The compat test runtime now loads Cobblemon Raid Dens 0.11.3 (with GeckoLib 4.9.2) and Cobblecuisine 2.0.1, the pack's
versions, next to Mega Showdown 1.8.4, which was already there; so does the screenshot harness's Pokémon set
(`-Pcobblemon=true`). The Cobblemon 1.8 runs swap Raid Dens to 0.13.2 and keep Cobblecuisine 2.0.1, which has no build
for 1.8.

## How the bracelet reaches the players (38.5)

`trainer/MegaBracelets` (core) decides who can Mega Evolve; `compat/cobblemon` builds the team with the stone from it
(`CobblemonTrainers.teamOf`). Every 40 ticks of a trainer's life the server checks again and, when the answer changed,
sends a small packet (`aliveworkplace:mega_bracelet`: the entity id and yes or no) to every player who sees them, and
again whenever a player starts seeing one who wears it. The client draws `MegaBraceletLayer` over the villager's
outfit, class and Legend looks. A village that becomes a City (or drops back), a Cup's final and a visitor's day so
show within two seconds. Nothing is saved: it all follows from the village's rank, the Cup and the visitor records.

## How the Mega Stones are chosen (38.6)

The luxury recipe format grew three optional parts, so the stones stay data: an output named by a tag and a chooser
(`"output": {"tag": "#mega_showdown:mega_stone", "for": "village_trainers"}`), `keep` (how many of each good the store
is kept in; 8 by default) and `when` (a named switch: `village_megas` is `villageMegas` with Jewellers on). The files
`data/aliveworkplace/luxury_recipes/mega_*.json` load only with Cobblemon and Mega Showdown (Fabric load conditions).
The chooser `village_trainers` (core `jeweller/MegaStones`) asks Cobblemon for each trainer's team
(`CobblemonMegas.teamStones`: the same seeded team they field at home) and remembers the answer per trainer until
their rank or Mega changes. The Miner's finds are the item tag `#aliveworkplace:jeweller_finds` (with
`#aliveworkplace:mega_meteorite_ores`), every entry optional, so a pack can add its own.

The bracelet (core `trainer/LeaderBracelets`): each hall's round notes whether its village has a Trainer Leader of its
own; a player's win against a Leader (Cobblemon's battle end, the same call that pays the prize) marks the Leader's
village (the nearest hall) for that player, then checks the realm the village is in.

## How the shrine's show runs (38.7)

`trainer/MegaShrines` (core) reads the shrine by its blueprint id from the finished builds near the hall
(`BuildSiteManager.finishedNear`), and Mega Showdown's pedestal only by its id (`mega_showdown:pedestal`):
`BlueprintLibrary.megaOk` lists the shrine only while that block exists and `villageMegas` is on. `Festivals.tick`
asks it first each festival tick; in the show's hours it takes the crowd (to 4 blocks in front of the dais) and walks
the trainer to the step before the pedestal; there, the show is held once a day per village (kept in memory: a restart
mid-show may hold it again, and the mood is the same). The Mega Evolution is shown as light and sound at the pedestal,
not by a Pokémon sent out.

## How dens are found (38.2)

When a chunk loads, its block entities are looked at (never an area of blocks); a Raid Dens crystal among them is
looked at on the level's next tick and recorded if a Village Hall is within 160 blocks. Its tier, type and whether it's
active are read from its block state by property name (`raid_tier`, `raid_type`, `is_active`), so no Raid Dens class
is needed. Every 10 seconds, and whenever the Defense page or the map asks, the dens in loaded chunks are read again:
one that changed is updated, one that's gone is dropped. A den in an unloaded chunk keeps what was last seen.

## Switches

| Key | Default | What it does |
|---|---|---|
| `denWardens` | on (once 2.2 ships) | Guards can be posted to raid dens. Off: no new wardens, and those posted go back to normal duty. |
| `denChallenges` | on (once 2.2 ships) | Village trainers challenge active dens. Off: no challenges. |
| `denChallengeDays` | 3 (1 to 30) | Days between a village's challenges. |
| `villageMegas` | on (once 2.2 ships) | More Mega trainers and their bracelets (38.5), the Jeweller's Mega Stones and Key Stones, the Miner's finds to the store and the realm's Mega Bracelet (38.6), and the Mega Shrine at the Table with its festival show (38.7). Off: only Masters Mega Evolve, as before, nobody wears a bracelet, the Mega recipes don't run, the Miner keeps everything and no bracelet is given. With Jewelers off (M34) the Mega recipes don't run either, and the Miner keeps everything. |

The other two are mapped to the gate and come into the config with 38.8 to 38.10.

## Saved data

Each dimension saves its known dens (`aliveworkplace_raid_dens`): a list of `pos`, `tier` (1 to 7), `type`,
`active` and `firstSeen` (the day). An older save has none, and dens are found again as their chunks load. The wardens are saved per dimension too
(`aliveworkplace_den_wardens`): each `guard` (their id) with their `den` and the players `cheered` this raid. An older
save has no wardens. The den challenges are saved per dimension (`aliveworkplace_den_challenges`): each hall's
`lastDenChallenge` (the day) and each tired trainer's `tiredUntil` (game time). An older save has none: every village
may challenge at once. The Mega Bracelets are saved on the overworld (`aliveworkplace_leader_bracelets`): the villages
known to have a Trainer Leader (`leaderHalls`) and, per player, the villages whose Leader they beat (`beaten`) and the
realms whose bracelet they had (`megaBracelet`). An older save has none: nobody has a bracelet yet. A villager who
watched a Mega Shrine's show keeps the day on them (`mega_show_day`, 38.7); an older save has none. The planned fields
of later items are listed in the design note.

## Items, blocks, jobs, commands

None of our own yet; 38.6 adds four Jeweller recipes (`mega_keystone`, `mega_stone`, `mega_stone_cut`,
`mega_stone_meteorite`) and two item tags (`jeweller_finds`, `mega_meteorite_ores`); 38.7 adds the decoration
blueprint `aliveworkplace:mega_shrine` (tools/blueprints/decor.py, `mega_shrine`). The ids the milestone reads from the three mods (the den block `cobblemonraiddens:raid_crystal_block`
and its states, the cheers, `mega_showdown:keystone`, `cobblecuisine:bean_crop`, the beans and the dishes) are listed in
the design note and checked by `CobblemonDepthCompatTests`.

## Decisions

- Cobblecuisine has seven bean colours, not six as the roadmap said; the plan uses all seven (38.1).
- A Cobblecuisine dish is found by the recipe whose result it is, not by the recipe's id: some differ
  (`cake_honey_fruit` makes `fruit_honey_cake`) (38.1).
- Dens are recorded when their chunk loads near a village, never by scanning (38.2's spec).
- The den list isn't switched: it only records and shows (design note M38). It's behind the 2.2 gate and needs Raid
  Dens (38.2).
- The Defense page shows up to seven dens, nearest first (38.2).
- A raid is noticed by Raid Dens' own advancements (`join_raid` and the seven tiers) being granted near a watched den,
  since players fight raids in the mod's own dimension; "once a raid" means until the den glows again (38.3).
- A warden stands two blocks off the crystal on the side toward home, and walks a leg of 16 blocks at a time, as
  scouts do (38.3).
- A Raid Dens tier table can't be rolled with a plain chest context: its pools check the Raid Pouch being opened, a
  loot parameter only Raid Dens holds. `compat/raiddens` reads its three public fields (`RaidLootContexts.RAID_POUCH`,
  `BONUS_LOOT`, `RAID_POUCH_USE`) by name and fills `RaidDenLoot`; Raid Dens stays off the compile classpath. If the
  fields are gone in a later Raid Dens, a won challenge still turns the den dark but brings no loot, with a line in the
  log (38.4).
- The challenge is looked at every 30 seconds by day; a village that tried (won or lost) waits `denChallengeDays`
  days, and a village with no free Leader or Master, or no den in reach, tries again next look (38.4).
- The village's Megas come from where the trainer stands now: a Leader's village is the nearest hall's, so a Leader of
  a City who walks into a Town's reach counts as the Town's (38.5).
- In a Cup final's simulated bouts (two villagers) the Mega Stone holder fights as the Pokémon it is: the bout's small
  formula has no Mega Evolution; only real battles against a player Mega Evolve (38.5).
- The Stadium Day's visitors Mega Evolve in a player's challenge; their exhibition bouts against the home trainers are
  settled with each side's home team, as before (38.5).
- A Mega Stone holder's battle AI Mega Evolves at any tier, Novice included (a Cup finalist can be one) (38.5).
- The Key Stone and Mega Stone recipes take 2 gold ingots and 4 emeralds each; the Key Stone is kept 2 in the store,
  each Mega Stone 1 (38.6).
- Besides the Crystal block the spec names, the Jeweller also cuts a stone from the plain Mega Stone a Crystal drops
  without silk touch, and (as a Master, level 4) from 4 Mega meteorite ores, which is what the Miner keeps them for
  (38.6).
- A Miner's Key Stone ore drops a Key Stone without silk touch: that goes to the store too (38.6).
- The Mega Bracelet is once per player per realm, as the roadmap says (the design note's open question 2 stays the
  owner's); it is Mega Showdown's plain `mega_bracelet`, given straight into the inventory with the capital's hall
  named, not picked up at the hall (38.6).
- A realm's Leader count is the member villages whose hall's last round saw a Trainer Leader of their own (38.6).
- In local compat runs on the 7 GB dev box, `-PcompatHeap=2G` is needed now that the runtime holds two more mods
  (38.1); CI keeps the default.

## Known limits

Only dens within the map's 128-block square get a banner; farther ones (up to 160 blocks) are on the Defense page
only. A den nobody has been near isn't known. A player who joins a raid from farther than 16 blocks (or whose advancement was granted earlier) gets no cheer. Only
one warden per den. A challenge is settled at once: there's no battle to watch, only the den going dark. A won
challenge gives no Raid Dens Pokémon, only the loot. On Cobblemon 1.8 (Raid Dens 0.13.2) the loot fields are untested.
Cobblecuisine isn't built yet. The shrine's Mega Evolution is light, sound and a chat line at the pedestal, not a
Pokémon sent out and Mega Evolved: our trainers' Pokémon exist only in battle. The show takes the festival's first
hour and a half, then the square has the crowd. Our renderer has no model for the pedestal (a block entity), so the
shrine's render shows its plinth bare; the showcase scene shows it in the game. A realm whose villages have no hall round yet (none loaded since 38.6)
counts only the Leaders already beaten; a village whose Leader dies counts until its hall's next round. The bracelet's look is checked in the
showcase scene only (a client is needed to draw it).
Cobblecuisine on Cobblemon 1.8 is untested beyond the id test.

## Proof

- `CobblemonDepthCompatTests`: `theThreeModsAreLoaded`, `raidDenIdsExist`, `megaIdsExist` and `cuisineIdsExist`
  list every id the milestone reads and fail naming any that's missing.
- `ExpansionGateGameTests.cobblemonDepthIsGated`: the gate is shut for players and open in GameTests, for each of the
  six switches.
- `RaidDenCompatTests`: `aDenNearAHallIsListed` (its states, its line, the Defense page, dropped once broken),
  `aDenTurnedInactiveShowsInactive`, `aDenFarOffIsntListed` (200 blocks), `aReloadKeepsTheList`, `theMapMarksTheDen`.
- `DenWardenCompatTests`: `aWardenWalksToTheDenAndBackAtDusk`, `aDenGlowingAgainIsWrittenOnce`,
  `aRaiderByAWatchedDenGetsOneCheer` (one a raid, none 40 blocks off, another next raid),
  `aRecalledWardenGoesBackToNormalDuty`, `aWardenOfAGoneDenOrSwitchedOffIsFree`, `wardensSurviveASave`.
- `DenChallengeCompatTests`: `aWinBringsTheLootHomeAndTheDenRests` (the tier's loot in the store, the den inactive, the
  chronicle line), `aLossLeavesTheDenActive` (and the trainer tired, not sent again), `aTierSixDenIsNeverChallenged`,
  `aDenInUseByAPlayerIsntChallenged`, `aBrokenDenIsntChallenged`, `tiredTrainersSurviveASave`.

- `MegaTrainerCompatTests` (with Mega Showdown): `aCityLeaderMegaEvolvesATownLeaderDoesNot`,
  `theCupFinalsTrainersHoldAMegaStone` (six seeds, the theme kept, as fighters too, not in earlier rounds; finalists
  in a Cup today wear the bracelet), `theBraceletIsOnExactlyTheMegaTrainers` (City Leader, Master and visitor do;
  Expert, Novice and farmer don't; the clients are told the same), `switchedOffOnlyMastersMegaEvolve`,
  `aLeaderSavedAndLoadedIsTheSame`.
- `MegaTrainerGameTests` (without Mega Showdown): `withoutMegaShowdownNothingChanges`, `aCupFinalistIsKnown`.
- `MegaShrineCompatTests` (with Mega Showdown): `theShrineIsListedWithMegaShowdown`,
  `theFestivalShowIsHeldAtAFinishedShrine` (the pedestal's place, the watchers' +3, once a festival, through a save),
  `noShowWhenOffWithoutATrainerOrLate` (villageMegas off: not listed, no show; no trainer; past its hours).
- `MegaShrineGameTests` (without Mega Showdown): `hiddenWithoutMegaShowdown`, `theShowsMoodAndText`.
- `MegaStoneCompatTests` (with Mega Showdown, 38.6): `theRecipesAndFindsLoad`,
  `aJewellerCutsAMegaStoneForTheVillagesTrainers` (a crystal, gold and emeralds become one stone of the Master's team),
  `aJewellerCutsAKeyStone`, `onlyTheStonesTheStoreLacks` (and none with no trainer), `switchedOffTheRecipesDontRun`
  (`villageMegas` off, Jewelers off), `aMinersKeyStonesGoToTheStore` (dug Key Stone ore: the stones in the store,
  the cobblestone in the Miner's chest), `beatingEveryLeaderOfARealmGivesOneBracelet` (one Leader of two: nothing; a
  loss: nothing; both: one; again: still one; save and load), `braceletEdges` (switch off, a Leader who left, no realm).
- `MegaStoneGameTests` (without Mega Showdown): `withoutMegaShowdownNothingChanges`, `withoutMegaShowdownNoBracelet`,
  `aTagOutputWithAChooser` (the recipe format's new parts and their errors), `theText`.

Showcase scenes: `raid_dens`, `den_warden`, `den_challenge`, `mega_trainers`, `mega_jeweller`
