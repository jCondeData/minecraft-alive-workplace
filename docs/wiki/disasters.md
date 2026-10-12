# Disasters

ROADMAP 32.15. Part of Milestone 32 (1.6), so it is **off for players until the milestone is finished**
(`Expansions.M32`); GameTests run with it on. The engine that lets a fire, a drought or a plague strike a village,
described as data the way the raider cultures are (see [Raids and threats](raids.md)). The village fire ships with
32.16 ([Village fire and the Firewarden](village-fire.md)) and the drought with 32.17 ([Drought and the Well
Keeper](drought.md)) and the plague with 32.18 ([Plague and the Physician](plague.md)); a datapack can add its own.

Roadmap items: 32.15, 32.19

## What a player sees

- **The news.** When a disaster comes, players in the village and the hall's owner (wherever they are) read
  "Drought has come to Oakvale." If the village has someone who foretells it (a job the disaster names, or a guard or
  scout by a lookout tower: the day's-warning sources of 32.14), they read it a day ahead instead: "Ana foretells it:
  Drought will come to Oakvale. Days until it strikes: 1." When it ends: "Drought is over in Oakvale."
- **The hall.** The wellbeing icon has a red first line ("Drought (days left: 2)", "Drought, until it rains" or
  "Drought foretold (days until it strikes: 1)"). What next? lists it, and names the job that answers it when the village has none.
- **Houses for the answering jobs** (32.19). The Blueprint Table has three one-tier builds, each with its job's block
  and no other job block: the **Fire Station** (a stone ground floor with the Firewarden's grindstone, a water basin
  and buckets on racks, a ladder up to a timber watch loft with windows all round), the **Well House** (a roofed well
  on a stone platform, its shaft three deep down to water, the Well Keeper's cauldron, a water trough and benches) and
  the **Physician's House** (a white ward with the brewing stand on a counter of herbs, four beds each between white
  screens, the quarantine ward, and a herb garden out front). A builder builds them from the chests like any blueprint;
  hand a jobless villager there the job's item (a water bucket, a shovel, a glistering melon slice) and they take the
  job at that block. On the Village Map the Fire Station is a defense, the other two care.
- **What next? suggests them** (32.19): "Drought can come here: build a Well House", with "No Well Keeper answers it
  yet. Build the Well House and hand a villager by its Cauldron the job's item (A shovel)". One tip for each disaster
  that's switched on and can come to this village (its `when` holds: wood enough for a fire, a Town for the plague; a
  drought can come anywhere), whose answering job is switched on and missing from the village. It goes once the
  village has that job, or with the disaster or the job switched off.
- **The chronicle** has it begin, foretold and end, under the new kinds **Fire**, **Drought** and **Plague**.
- **What it does** depends on the disaster's effects: crops grow slower, farmland dries even by water, water cauldrons
  lose a level a day, villagers fall ill and pass it on, are unhappy (a mood reason, "Drought in the village"), fires
  start, or one job works slower ("A disaster in the village" in the pace list).

## How it works

- `threat/Disasters` reads `data/<namespace>/disasters/<id>.json` (`threat/Disaster` is one file). The shape is in
  `docs/design/M32.md`: `when` (the conditions), `chance` a day, `factors`, `requires`, `duration` (`min_days`,
  `max_days`, `ends_with_rain`, and `ends_when_well` for the plague: it ends the day after the last day anyone was ill), `effects`, `answered_by`, `foretold_by`, `foretell_days`, `messages`, `chronicle`
  and `kind` (`fire`, `drought` or `plague`; by default from its effects).
- **Conditions** (`threat/DisasterConditions`) are the raider cultures' toolbox (`biomes`, `coast`, `nether_link`,
  `min_rank`, `min_villagers`) plus `weather` (`thunder` or `rain` now), `dry_days` (days since it last rained),
  `wood_share` (the share of wood among the blocks round the village's finished builds, sampled once a day),
  `crowded` (more villagers than beds) and `lair_standing` (a culture's lair near the village).
- **The roll.** The hall's round rolls once a day: each disaster switched on whose conditions hold, by its chance
  (after `factors`; a factor's `chance` replaces the base, `times` multiplies it). **One disaster at a time per
  village, and 7 days' rest after one.**
- **Foretold.** If a villager of a `foretold_by` job lives in the village, or `seer` is listed and the village has a
  32.14 source of warning, it begins `foretell_days` later (default 1).
- **The end.** After its days (`min_days` to `max_days`, picked when it starts) or, with `ends_with_rain`, as soon as
  it rains. Switched off in the config, one under way ends at once.
- **The effects** (each a `type`): `crop_growth` (`factor`: the share of the crop's random ticks that may grow),
  `farmland_dries` (wet farmland dries a step a random tick, water or not), `cauldrons_drain` (`levels_a_day`),
  `illness_chance` (`times`: each villager's daily chance of falling ill, multiplied), `contagion` (`chance`, `range`:
  every round each ill villager may pass it to the well ones within range), `mood` (`amount`, `reason`,
  `unless_built`: no mood while one of these builds stands finished), `fires_a_day` (`count`: a fire on top of a
  finished build, or by the hall) and `job_pace` (`job`, `factor`: 0.5 is half pace).
- **Cheap.** Crop growth and farmland are one check in the block's random tick against the short list of halls with a
  disaster in effect; nothing scans the area. Cauldrons are found as POIs, the ill through the hall's census.

## Switches

| Key | Default | What it does |
|---|---|---|
| `disasters` | every disaster id → true | false: that disaster never starts, and one under way ends at once. Ours by their path, a datapack's by its whole id; a new one is added, on, when the world's data loads. |

## Saved data

On the Village Hall: `disaster` (the id under way or foretold; absent: none), `disasterSince` (the day it begins;
-1), `disasterEnds` (its last day; absent: until rain) and `lastDisasterDay` (the day the last one ended; -100: never).
A hall saved before 32.15 has none. The list of afflicted halls, the day each hall last rolled, the last rain and the
wood share are kept in memory and rebuilt by the halls' rounds after a restart.

## Items, blocks, jobs, commands

No new item or block; the chronicle gains the kinds `FIRE`, `DROUGHT` and `PLAGUE`. Blueprints (32.19):
`aliveworkplace:fire_station`, `aliveworkplace:well_house`, `aliveworkplace:physicians_house`, drawn by
`tools/blueprints/answering.py`.

## Decisions

- The `messages` and `chronicle` keys are lang-key prefixes (`.name`, `.start`, `.foretold`, `.end`); a file without
  them gets the engine's own lines, naming it by its id (32.15: no disaster ships yet, so a datapack's works without
  a resource pack).
- `answered_by` is shown in What next? only; what the answering job does is its own item (32.16 to 32.18).
- A house tip (32.19) asks only the disaster's `when`, not its `requires`: those wait for the weather (days without
  rain), and a village should build before the drought, not during it. A house tip and the `disaster_answer` tip can
  show together while that disaster is on.
- The buckets on the Fire Station's racks are empty flower pots (a bucket has no block); the basin is real water, a
  water bucket from the chests.
- The three houses have no upgrade (one tier, like the Map Room and the Bandstand).
- Rain is the weather as set, not the sky's slow fade, so a disaster ends the round the rain begins.

## Known limits

- The village fire, the drought and the plague ship with the mod.
- The days since rain count from the first round the server saw after a restart.
- After a restart, the daily effects (a fire, a cauldron's level) may happen a second time that day.
- `wood_share` samples round each finished build's corner, so a very large build is only partly seen.

## Proof

`DisasterGameTests` (with the test datapack's `aliveworkplace_test:test_blight`): crops don't grow and grow again
after, farmland dries by water, cauldrons lose one level a day, villagers fall ill, illness passes on within range,
villagers are unhappy, a fire starts, farmers work at half pace, it ends after its two days and with rain (and none
starts in the days of rest), it survives a save and load of the hall (and an old hall loads with none), a librarian
foretells it a day ahead, switched off it never starts, and every new sentence has its text. No scene (nothing ships).

`AnsweringHousesGameTests` (32.19): the three houses are in the Blueprint Table, named and on the Village Map; each
has its job's block once and no other job block, and what the item asks (the basin, the pails on racks, the loft and
its windows; the shaft open down to water, the curb, the trough, the benches, the roof; four beds between screens,
the herb garden); a builder builds each from chests and a jobless villager there handed the job's item takes the job
at the house's block; What next? suggests the Well House to a hamlet without a Well Keeper (with its title and how),
the Physician's House only to a town, neither once the village has those jobs, and none with drought switched off;
every new sentence has its text. Showcase scene `defences` films the three after the walls and gates.
