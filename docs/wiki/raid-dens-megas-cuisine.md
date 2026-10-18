# Raid dens, Megas and Cobblecuisine

Cobblemon depth brings three of the pack's Pokémon mods into village life: Cobblemon Raid Dens (the village knows the
dens around it, guards watch them and its trainers challenge them), Mega Showdown (more village trainers Mega Evolve,
and the village makes Mega Stones and Key Stones) and Cobblecuisine (the kitchens cook its dishes and farmers grow its
beans). Part of 2.2, behind its gate (`Expansions.M38`). Built so far: the plan, the gate, the test mods, the dens
the village knows (38.2), Den Wardens (38.3), the village's den challenges (38.4) and more Mega trainers with their
bracelets (38.5); the full plan is the design note, docs/design/M38.md.

Roadmap items: 38.1, 38.2, 38.3, 38.4, 38.5

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
tier 3 for a Leader, tier 4 for a Master, the highest tier first, then the nearest. A den with a player within 8 blocks
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

The rest (Mega Stones from the Jeweller, the Mega Shrine, Cobblecuisine) comes with 38.6 to 38.10.

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
| `villageMegas` | on (once 2.2 ships) | More Mega trainers and their bracelets (38.5; 38.6 and 38.7 will use it too). Off: only Masters Mega Evolve, as before, and nobody wears a bracelet. |

The other two are mapped to the gate and come into the config with 38.8 to 38.10.

## Saved data

Each dimension saves its known dens (`aliveworkplace_raid_dens`): a list of `pos`, `tier` (1 to 7), `type`,
`active` and `firstSeen` (the day). An older save has none, and dens are found again as their chunks load. The wardens are saved per dimension too
(`aliveworkplace_den_wardens`): each `guard` (their id) with their `den` and the players `cheered` this raid. An older
save has no wardens. The den challenges are saved per dimension (`aliveworkplace_den_challenges`): each hall's
`lastDenChallenge` (the day) and each tired trainer's `tiredUntil` (game time). An older save has none: every village
may challenge at once. The planned fields of later items are listed in the design note.

## Items, blocks, jobs, commands

None of our own yet. The ids the milestone reads from the three mods (the den block `cobblemonraiddens:raid_crystal_block`
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
- In local compat runs on the 7 GB dev box, `-PcompatHeap=2G` is needed now that the runtime holds two more mods
  (38.1); CI keeps the default.

## Known limits

Only dens within the map's 128-block square get a banner; farther ones (up to 160 blocks) are on the Defense page
only. A den nobody has been near isn't known. A player who joins a raid from farther than 16 blocks (or whose advancement was granted earlier) gets no cheer. Only
one warden per den. A challenge is settled at once: there's no battle to watch, only the den going dark. A won
challenge gives no Raid Dens Pokémon, only the loot. On Cobblemon 1.8 (Raid Dens 0.13.2) the loot fields are untested.
The Jeweller's Mega Stones, the shrine and Cobblecuisine aren't built yet. The bracelet's look is checked in the
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

Showcase scenes: `raid_dens`, `den_warden`, `den_challenge`, `mega_trainers`
