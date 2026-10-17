# Raid dens, Megas and Cobblecuisine

Cobblemon depth brings three of the pack's Pokémon mods into village life: Cobblemon Raid Dens (the village knows the
dens around it, guards watch them and its trainers challenge them), Mega Showdown (more village trainers Mega Evolve,
and the village makes Mega Stones and Key Stones) and Cobblecuisine (the kitchens cook its dishes and farmers grow its
beans). Part of 2.2, behind its gate (`Expansions.M38`). Built so far: the plan, the gate, the test mods, the dens
the village knows (38.2) and Den Wardens (38.3); the full plan is the design note, docs/design/M38.md.

Roadmap items: 38.1, 38.2, 38.3

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
chronicle says so: "The Fire raid den (north, about 40 blocks) glows again". A player who joins a raid (or wins a
tier) within 16 blocks of a watched den gets the warden's cheer: one Attack, Defense or Heal Cheer, once a raid.

The rest (challenges, Megas, Cobblecuisine) comes with 38.4 to 38.10.

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

The other five are mapped to the gate and come into the config with 38.4 to 38.10.

## Saved data

Each dimension saves its known dens (`aliveworkplace_raid_dens`): a list of `pos`, `tier` (1 to 7), `type`,
`active` and `firstSeen` (the day). An older save has none, and dens are found again as their chunks load. The wardens are saved per dimension too
(`aliveworkplace_den_wardens`): each `guard` (their id) with their `den` and the players `cheered` this raid. An older
save has no wardens. The
planned fields of later items are listed in the design note.

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
- In local compat runs on the 7 GB dev box, `-PcompatHeap=2G` is needed now that the runtime holds two more mods
  (38.1); CI keeps the default.

## Known limits

Only dens within the map's 128-block square get a banner; farther ones (up to 160 blocks) are on the Defense page
only. A den nobody has been near isn't known. A player who joins a raid from farther than 16 blocks (or whose advancement was granted earlier) gets no cheer. Only
one warden per den. Challenges, Megas and Cobblecuisine aren't built yet.
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
  `aRecalledWardenGoesBackToNormalDuty`.

Showcase scenes: `raid_dens`, `den_warden`
