# Raids and threats

ROADMAP 32.2 (the threat engine). The rest of Milestone 32 (lairs for every culture, sieges, warnings) builds on it;
`docs/design/M32.md` is the plan.

## What a player sees

Nothing new yet. Monsters still raid a village with a Village Hall and 8 villagers or more at night, and bandits still
raid from their camp until their chief falls. Two things changed under the same look:

- A raid under way **survives a restart**. Before, a server that stopped mid-raid forgot the raid: the raiders stayed,
  but the village no longer counted as raided and never got its "fought off" or "fled at dawn" line.
- An attack is now **decided a day ahead**. At dusk each hall rolls whether raiders come the *next* night, so there is a
  day in which a warning can be given (32.14 adds the warnings; until then the horn is still the first you hear of it).

## How it works

- **Raider cultures are data.** One file is one culture: `data/<namespace>/raider_cultures/<id>.json`, read on every
  load and `/reload`; a datapack can add, change or remove them. The mod ships two, with the numbers raids always had:
  - `monsters`: zombies 50, skeletons 30, spiders 20; they gather at the village's edge; villages of 8 or more.
  - `bandits`: pillagers 50, vindicators 50, each called "Bandit"; they come from their camp (their lair); villages of
    Village rank or more; the chief is a vindicator in an iron helmet and chestplate with 36 extra health.
- **Who comes.** While a lair stands by the village, its culture raids (the bandit camp: bandits). Otherwise a culture
  is picked by `weight` among those switched on, without a lair, whose `where` fits the village.
- **How many.** As before: 3, one more for every 4 villagers, plus up to 4 in iron (helmet and chestplate) for a village
  with guards, 16 at most. Each raider is picked from the roster by its share and gets its role's gear, which never
  drops.
- **The threat clock.** At dusk (18:00, day time 12000) the hall's round rolls the next night: the chance is the usual
  one (15% at 8 villagers, +1% a villager, at most 35%; twice that with a bandit camp; times the chance hook below), and
  never within 3 days of the last raid. If raiders are to come, the clock keeps the day, the culture, the side and the
  hour (between 19:30 and midnight). The night after, at that hour, they come from that side. They don't if their culture
  was switched off meanwhile, or if they needed a lair and it was broken up during the day: killing the chief in time
  calls the attack off.
- **With a Seer** (Legends) nothing changes: the Seer still rolls tonight at dawn and that night goes as foretold; the
  clock is not asked on such a day (32.14 makes the two tell one story).
- **How long.** `hours: night` raiders flee at dawn, as before. `hours: until_noon` raiders stay through the morning.
- **Guards** fight anything that carries the raider tag (`aliveworkplace_raider`), so a culture may bring hoglins, which
  the game doesn't count as monsters. Raiders also carry their role as a tag (`aliveworkplace_role_melee`, …).
- **The chance hook.** `Threats.chanceFactor(level, hall)` multiplies a village's chance of an attack; other systems add
  to it with `Threats.addFactor(id, (level, hall) -> factor)`. Curfew and the research topic that makes raids rarer
  already go through it.

### The culture file

| Key | What it says | If left out |
|---|---|---|
| `where` | conditions, all of which must hold (below) | comes anywhere |
| `weight` | how often it is picked among the cultures that fit (1 to 1000) | 10 |
| `arrival` | `edge` (the village's edge), `lair` (the side its lair is on), `shore` (dry ground by water at the edge), `portal` (out of the village's Nether portal); when there is no such place, the edge | `edge` |
| `hours` | `night` or `until_noon` | `night` |
| `roster` | a list of `entity`, `share` (1 to 1000), `role` (`melee`, `ranged`, `ram`, `climber`, `healer`; `melee` if left out) and `gear` | the file is skipped |
| `gear` | slot (`head`, `chest`, `legs`, `feet`, `mainhand`, `offhand`) → an item id, or `ominous_banner` | what the mob spawns with |
| `name` | the lang key of what its raiders are called | unnamed |
| `captain` | `entity`, `gear`, `health` (extra health), `names` (the lang key of his list of names) | no captain |
| `tactics` | names such as `ram_gates`, `ladders`, `sand_ramps`, `plunder`, `hex`; one the mod doesn't know (yet) is skipped | none |
| `lair` | `structure`, `strength`, and `growth` a day (0), `max` (the strength), `home_max` (8); a culture with a lair raids only from its lair | no lair |
| `loot` | a loot table (also read from `lair.loot`) | none |
| `messages`, `chronicle` | a key prefix (`<prefix>.raid`), or the keys by event: `{"raid": "…"}` | the monsters' lines |

Ids of the mod's own things may leave the namespace off. A key the reader doesn't know is ignored with a line in the
log. A file that can't be read (not JSON, an unknown mob or item, an empty roster, a wrong `role`, `arrival`, `hours`,
slot or rank) is skipped with a line in the log that names it and says why; the other cultures still load.

Conditions (`where`), shared with the disasters of 32.15:

| Key | Holds when |
|---|---|
| `biomes` | the hall's biome is one of these ids or in one of these tags (`"#minecraft:is_forest"`) |
| `coast` | `true`: an ocean or beach biome within 48 blocks of the hall |
| `nether_link` | `true`: a lit Nether portal, or a finished Nether Gate, in the hall's area |
| `min_rank` | the village is at least `hamlet`, `village`, `town` or `city` |
| `min_villagers` | the village has at least this many villagers |

An example, a pack's desert raiders:

```json
{
  "where": { "biomes": ["minecraft:desert"], "min_rank": "town" },
  "weight": 20,
  "arrival": "edge",
  "hours": "until_noon",
  "name": "entity.mypack.desert_raider",
  "roster": [
    { "entity": "minecraft:husk", "share": 70, "role": "melee", "gear": { "mainhand": "minecraft:golden_sword" } },
    { "entity": "minecraft:stray", "share": 30, "role": "ranged" }
  ],
  "tactics": ["sand_ramps"]
}
```

## Switches

- `villageRaids` (true): raids by chance at all (the clock), and the `monsters` culture.
- `banditCamps` (true): bandit camps, and the `bandits` culture.
- `raiderCultures` (every culture → true): one set to false is never picked, and bandits switched off here make no
  camp. Ours are written by name (`"monsters": true`), a datapack's by its id (`"mypack:desert_raiders": true`). The
  file always lists every culture: one it doesn't name yet is added, on, when a world's data loads. It is in the file
  only (a map isn't on the settings screen).

## Saved data

`aliveworkplace_threats` (per dimension, new; a world without it loads with none of this):
- `raids`: the raids under way, each with `hall`, `culture` (`aliveworkplace:monsters` if missing), `raiders` (how many
  came, 0 if missing) and `began` (game time);
- `clock`: per `hall`, `rolled` (the last day whose dusk was rolled; -1 if missing, so it rolls at the next dusk) and
  `attacks`, each with `day`, `culture` (monsters if missing), `angle` (the side) and `at` (the hour; nightfall if
  missing).

The hall's `lastRaidDay` and the bandit camps' file (`aliveworkplace_bandit_camps`) are as they were.

## Items, blocks, jobs, commands

None.

## Decisions

- 32.2: raids come from the standing lair; with none, a culture without a lair is picked by weight (design note M32).
- 32.2: `VillageRaids.start` is an order. When no culture's `where` fits the village (it is smaller than any culture
  asks for: only a Seer's foretelling in a tiny village, a command or a test gets there, the clock never does), the
  plain monsters come, if they are switched on. With them off, no raid starts.
- 32.2: the clock rolls one night ahead (the item's spec). The design note's "two nights" and the Seer reading the clock
  belong to 32.14.
- 32.2: an attack whose lair was broken up before its hour is called off, so striking first is worth it.
- 32.2: `villageRaids` stays the switch of all raids by chance, as it always was, and also switches `monsters` off;
  `banditCamps` also switches `bandits` off.
- 32.2: today's lang keys stay as they are, so a culture names its lines either by prefix or key by key.
- 32.2: the bandits' `captain` has no `names` yet; the list of names comes with 32.3.

## Known limits

- The first night after updating is quiet: a hall first rolls at its next dusk, for the night after.
- A village whose chunks aren't loaded at dusk doesn't roll that day.
- `coast` is checked when the clock rolls (once a day), not cached.
- `shore` and `portal` arrivals pick a place only; wading in from a ship and the piglins' outpost come with their
  cultures (32.8, 32.10). The lair's `strength`, the `loot` table and the captain's `names` are read but not used until
  32.3.

## Proof

- GameTests: `ThreatGameTests` (the two shipped cultures and their numbers, a culture from the test datapack, broken
  files skipped; 1000 roster picks within 3% of each share; a raid with each role's gear, the raider tag and hoglins as
  foes, a known tactic run and an unknown one skipped; a raid saved and loaded half-way ending at dawn, `until_noon`;
  the clock's attack a day after the roll, the rest days, raids off; the conditions on a real hall, the portal arrival,
  the chance hook; cultures switched off never picked, the config file listing every culture).
- Unchanged and passing: `RaidGameTests`, `HallSpecGameTests`, `CheckBugGameTests`.
- No showcase scene: nothing new to see.
