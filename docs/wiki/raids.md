# Raids and threats

ROADMAP 32.2 (the threat engine). The rest of Milestone 32 (lairs for every culture, sieges, warnings) builds on it;
`docs/design/M32.md` is the plan.

Roadmap items: 32.2, 32.14, B133, B824, B856, B919, B1063, B1054, B1146

## What a player sees

Monsters raid a village with a Village Hall and 8 villagers or more at night, and raiders come from their lair until
its captain falls. Under that look:

- A raid under way **survives a restart**. Before, a server that stopped mid-raid forgot the raid: the raiders stayed,
  but the village no longer counted as raided and never got its "fought off" or "fled at dawn" line.
- An attack is now **decided a day ahead**. At dusk each hall rolls whether raiders come the *next* night, so there is a
  day in which a warning can be given (below); a village with nobody keeping watch still hears the horn first.
- The horn's chat line and the chronicle count the raiders in proper English: "1 drowned pirate wades ashore", "1 of
  Captain Brine's warband marches on", "3 desert raiders march on" (B824: a raid of one used to read "1 ... wade"). Each
  counted raid line has a `.one` key beside it in the language file; a culture's data file may leave it out.
  A raid of one fought off reads "The village fought off the raid (1 monster)" in the chronicle, and still counts in
  the Founder's story of the village (B833).
- Two raided villages side by side keep their raids apart: a hall's round counts only its own raid's raiders, not a
  raider standing within its reach but nearer another village with a raid under way (that raid's). So a village whose
  raiders are all dead has fought its raid off even with the neighbor's raiders about, and when its raiders leave at
  their hour the neighbor's stay (B856, the rule a call-off already used, B849).
  Every raider belongs to exactly one raid: a village's reach is measured from where the raider stands (its feet),
  RADIUS + 32 across and 48 blocks up or down from the hall, the same rule for who a village counts, whose raid a
  raider is and a siege's raiders; of the raids that reach it, the nearest hall's (the first raided, on a tie). So one
  raider 49 blocks below a second raided village no longer keeps both raids going (B915).
  A raider led out of every raided village's reach (a player kiting it away, or it chasing someone) still belongs to
  the raid whose hall is nearest: that raid isn't fought off while it lives, and at the raid's hour it leaves with the
  others, wherever it is (B1055: the raid used to be booked as fought off, and the raider stayed in the world for good).
  Out there, a raider only counts for the raid it came with (B1063): each raider is stamped with its raid (the hall and
  the moment it began) when it spawns and whenever that raid holds it in its reach, so a leftover of an older raid (one
  that stood in unloaded ground when its raid ended) never keeps a later raid going, however near or far it is. A raider
  with no stamp (from a save made before this) counts only within 64 blocks beyond the reach of the nearest raided hall,
  across and up or down (so out to RADIUS + 96 across and 112 blocks above or below the hall).
  A raider that turns into another mob the vanilla way (a husk drowning in a moat becomes a zombie, a zombie a drowned,
  a skeleton in powder snow a stray) is still that raid's raider (B1054): every tag the mod put on it (raider, stamp,
  role, and a lair's, bandit camp's, bounty's or rogue's mark) goes over to the new mob, so the raid isn't booked as
  fought off while it walks the village, it leaves at dawn, and builders' blocks are spared from it as before. A
  fallen Warlord's band that zombifies stays the raid's but is no lair's any more (32.10).

## A day's warning (32.14)

A village that keeps watch hears of an attack **the evening before**, when the clock sets it:

- **Who keeps watch.** A guard whose Guard Post is within 24 blocks of a finished **Lookout Tower** or **Wall Tower**
  (any style or tier) says who comes and from which side; a **Scout** posted there (not out on a mission) also counts
  them ("about 9 strong"). Other systems add sources to `guard/Warnings.SOURCES`: M35's Lighthouse, and the **Seer**
  (29.16), who gives **two days' warning** (B133): with a Seer settled in the village, each dawn's foretelling rolls the
  threat clock two nights ahead, so an attack is told the moment it is set, two days before it comes ("A vision of the
  north road: Warlord Harl's band will strike Oakvale in 2 days."). The Seer's dawn line for tonight is read from the
  same clock, so the two always agree. A guard or scout sees only a day ahead; the Seer doesn't count the raiders.
- **Told at once.** Players in the village, and the hall's owner wherever they are online, read "Smoke on the north
  road: Warlord Harl's band will strike Oakvale tomorrow night." The chronicle has it; the guards icon says "Raiders
  tomorrow night, from the north" ("Raiders in 2 days" after a Seer's warning) and the Defense page's new slot shows
  the attack, who and the side. While no attack is seen, that slot says who keeps watch, each in their own words: a
  guard "keeps watch from a tower: a raid is seen a day ahead", a Seer "sees raids in visions, 2 days before they
  come", the Lighthouse's keeper "watches the sea from the lighthouse" (B919).
- **On the day.** From noon the gates shut and the portcullis of every gate build drops; at dusk the guards take their
  battle stations ([Sieges](sieges.md)) and the villagers go in, before the horn. The siege that begins takes the
  dropped bars over; if no attack came, the bars go up the next morning. A village with no guard (warned by a Seer or
  the Lighthouse) opens the gates the warning shut again the morning after, or as soon as the attack is called off
  (B1158).
- **Called off.** An attack called off after the warning (threat level 0, Peaceful, the village set At peace, or
  `/workplace threat clear`) takes its warning with it (`ThreatData.callOff`), and `Warnings.ready` is false in a quiet
  village: no gates shut, no battle stations, nobody kept in (B1146). So does a lair broken up before its band comes:
  its culture's attacks and their warning are off, other cultures' stay (B1155).
- **Without a source** the horn is the only warning, as before; the Defense page says "No lookout" and What next? says
  to build a Lookout Tower and post a guard by it.
- The warning and the dropped bars are saved on the hall's clock (`warned`, `braced`; a save from before has none).
- **The hall broken or moved.** Only the hall's own round draws the bars up and ends its raid, so a hall taken away
  that day (broken, or moved by a Mover, which sets a new hall down) draws its dropped bars up at once and calls off
  a raid under way, which ends its siege; the siege lifts at the next dawn as always (`Warnings.hallGone`, from
  `VillageHallBlock.onRemove`, B1176). The warning stays on the clock: a hall put back on its spot braces again.

## How it works

- **Raider cultures are data.** One file is one culture: `data/<namespace>/raider_cultures/<id>.json`, read on every
  load and `/reload`; a datapack can add, change or remove them. The mod ships two, with the numbers raids always had:
  - `monsters`: zombies 50, skeletons 30, spiders 20; they gather at the village's edge; villages of 8 or more.
  - `bandits`: pillagers 50, vindicators 50, each called "Bandit"; they come from their camp (their lair); villages of
    Village rank or more; the chief is a vindicator in an iron helmet and chestplate with 36 extra health.
- **Who comes.** While a lair stands by the village, its culture raids (the bandit camp: bandits). Otherwise a culture
  is picked by `weight` among those switched on, without a lair, whose `where` fits the village.
- **How many.** As before: 3, one more for every 4 villagers, plus up to 4 in iron (helmet and chestplate) for a village
  with guards, 16 at most; from a lair, no more than the band it has at home (32.3, [Lairs](lairs.md)). Each raider is picked from the roster by its share and gets its role's gear, which never
  drops.
- **Where they appear.** Round the gathering point, a few blocks each way, each on ground a mob can stand and walk
  from: never on a fence, a wall or a roof (a tent in their own war camp), and no higher than a step above the point
  (B130). When no such spot is drawn, the nearest good ground round the point is taken.
- **The threat clock.** At dusk (18:00, day time 12000) the hall's round rolls the next night: the chance is the usual
  one (15% at 8 villagers, +1% a villager, at most 35%; twice that with a bandit camp; times the chance hook below), and
  never within 3 days of the last raid. If raiders are to come, the clock keeps the day, the culture, the side and the
  hour (between 19:30 and midnight). The night after, at that hour, they come from that side. They don't if their culture
  was switched off meanwhile, or if they needed a lair and it was broken up during the day: killing the chief in time
  calls the attack off.
- **With a Seer** (Legends) the clock rolls at the Seer's dawn instead of at dusk, as many nights ahead as the Seer
  warns of (`warning_days`, 2), with the same chance and rest days; tonight goes as the clock holds it, so the Seer is
  never wrong and the raiders are the culture the clock set (B133). A night already rolled is never rolled again, so a
  Seer arriving or leaving changes nothing already set; without the Seer the dusk roll takes over from the last night
  rolled.
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
| `captain` | `entity`, `gear`, `health` (extra health), `names` (the lang key of his list of names: `<names>.1` to `<names>.20`) and `title` (the lang key of what goes before it); see [Lairs](lairs.md) | no captain |
| `tactics` | names such as `ram_gates`, `ladders`, `sand_ramps`, `plunder`, `hex`; one the mod doesn't know (yet) is skipped | none |
| `lair` | `structure`, `strength`, and `growth` a day (0), `max` (the strength), `home_max` (8), `icon` (an item, for the Defense page; a campfire); a culture with a lair raids only from its lair ([Lairs](lairs.md)) | no lair |
| `loot` | a loot table (also read from `lair.loot`): the chests of its lair hold it | none |
| `messages`, `chronicle` | a key prefix (`<prefix>.raid`), or the keys by event: `{"raid": "…"}`; a lair's events are on [Lairs](lairs.md) | the monsters' lines |

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

The hall's `lastRaidDay` is as it was. The bandit camps' file (`aliveworkplace_bandit_camps`) became the lairs' file in
32.3, and `aliveworkplace_threats` gained `history` (the last three attacks per hall): see [Lairs](lairs.md).

## Items, blocks, jobs, commands

None.

## Decisions

- 32.2: raids come from the standing lair; with none, a culture without a lair is picked by weight (design note M32).
- 32.2: `VillageRaids.start` is an order. When no culture's `where` fits the village (it is smaller than any culture
  asks for: only a Seer's foretelling in a tiny village, a command or a test gets there, the clock never does), the
  plain monsters come, if they are switched on. With them off, no raid starts.
- 32.2: the clock rolls one night ahead (the item's spec). The design note's "two nights" and the Seer reading the clock
  belong to 32.14.
- B133: the Seer's two nights are rolled at the Seer's dawn (with the foretelling's own randomness), not at dusk, so a
  village without a Seer rolls exactly as before. A Seer's raid with no culture fitting the village brings the plain
  monsters, as a foretold raid always did. `Warnings.Source` gained how many days ahead it sees (1 unless said), and a
  warning two days or more ahead reads "A vision of ..." (`message.aliveworkplace.warning.later`).
- 32.2: an attack whose lair was broken up before its hour is called off, so striking first is worth it.
- 32.2: `villageRaids` stays the switch of all raids by chance, as it always was, and also switches `monsters` off;
  `banditCamps` also switches `bandits` off.
- 32.2: today's lang keys stay as they are, so a culture names its lines either by prefix or key by key.
- 32.2: the bandits' `captain` had no `names` yet; 32.3 gave him his twenty.

## Known limits

- The first night after updating is quiet: a hall first rolls at its next dusk, for the night after.
- A village whose chunks aren't loaded at dusk doesn't roll that day.
- `coast` is checked when the clock rolls (once a day), not cached.
- `shore` and `portal` arrivals pick a place only; wading in from a ship and the piglins' outpost are their
  cultures' own ([Drowned pirates](pirates.md), [Piglin incursions](piglins.md)). The lair's `strength`, the `loot` table and the captain's `names` are used from 32.3 on
  ([Lairs](lairs.md)).

## Proof

- GameTests: `ThreatGameTests` (the two shipped cultures and their numbers, a culture from the test datapack, broken
  files skipped; 1000 roster picks within 3% of each share; a raid with each role's gear, the raider tag and hoglins as
  foes, a known tactic run and an unknown one skipped; a raid saved and loaded half-way ending at dawn, `until_noon`;
  the clock's attack a day after the roll, the rest days, raids off; the conditions on a real hall, the portal arrival,
  the chance hook; cultures switched off never picked, the config file listing every culture).
- Unchanged and passing: `RaidGameTests`, `HallSpecGameTests`, `CheckBugGameTests`.
- No showcase scene for 32.2: nothing new to see.
- 32.14: `WarningGameTests` (a village with an archer by its Wall Tower warned at dusk, 25500 ticks ahead, in the
  chronicle and on the Defense page, saved and loaded, a scout's count; a village without one not told and asked for a
  lookout; on the warned day the gates shut and the portcullis down at noon, the archer on the tower within 400 ticks
  of dusk and the villagers in, the siege taking the bars over; the bars up when no attack came, raids and sieges
  switched off, every new sentence). Scene `warning`.
- B1146: `QaThreatSwitches1110dBugTests` (warned at level 2, then the level set to 0, the world saved and loaded at
  level 0, or the village set At peace: nothing shuts on the day).
- B1158: `QaGuardlessGates1110dBugTests` (a guardless village's gates shut at noon of the warned day are open the
  morning after and when the attack is called off; a guarded village's the morning after, as before).
- 32.14 (QA, lane D): `QaWarningGameTests` (an archer posted exactly 24 blocks from a Lookout Tower's middle keeps
  watch, 25 blocks off doesn't and nobody is warned; an upgraded Lookout Tower still counts; a player in the village and
  the hall's owner 300 blocks off are each told once, in gold, and a stranger 300 blocks off isn't).
- B133: `SeerWarningGameTests` (a village with a settled Seer and a player by the hall: a dawn that sets an attack
  two days off tells the player, the chronicle and the Defense page at once, more than 48000 ticks ahead, once; the
  warning and the clock saved and loaded; on the day the Seer foretells it for tonight from the side told, the gates shut
  and the portcullis down at noon, the villagers in at dusk, the clock's raiders at the hour; with the Legends switch off no
  warning, then told when it's on again; every new sentence). Unchanged and passing: `SeerGameTests`,
  `WarningGameTests`, `LighthousePowerGameTests`, `LegendGuestsGameTests`.
- B133 (QA, lane D): `QaLaneD1104GameTests` (2: a Seer's village isn't told of an attack three days off, is told the
  next dawn, two days off, once, in the Seer's words, and the Defense page counts it down from "Attack in 2 days" to
  "Attack tonight"; a Seer who is gone tells nothing and the page reads "No lookout"). The page's idle line for a Seer
  once said they kept watch from a tower and saw a day ahead (B919, fixed).
- B856: `QaRisingsVerify1102dGameTests.aVillagesRoundEndsItsRaidOnceItsOwnRaidersAreGoneThoughANeighboursAreNear`
  (two desert raids 25 blocks apart; the first's raider killed, its round ends its raid though the second's raider
  stands within its reach; the second's raid goes on) and `aVillagesRaidersLeavingAtTheirHourLeaveANeighboursRaiderBehind`
  (the first's raiders leave at noon, the second's raider stays and its raid goes on).
- B915: `QaRaidOwnership1104cBugTests.aRaider49BlocksBelowAnotherRaidedHallKeepsOnlyOneRaidGoing` (one raider 49
  blocks below a second raided hall: after both halls' rounds only one raid is still under way), with
  `QaRaidOwnership1104cGameTests`; unchanged and passing: `QaRaidVertical1103dGameTests`,
  `QaRaidReachHeight1104dGameTests`, `QaRaidNeighbours1103dGameTests`, `QaRisingsVerify1102dGameTests`.
- B915, B919 (QA, lane D): `QaThreatFixes1104dGameTests`. B915: a Ravager, a Husk and a Silverfish are each in a
  hall's reach at 48 blocks above and below their feet and out at 49, and the raid-of check follows the same edge; two raided
  villages with one raider between them (a tall and a short mob, 48 and 49 above and below the second hall) keep
  exactly one raid going, the one that owns it, and the other is fought off; killing that raider ends only its own
  raid. B919: the Seer's idle line word for word ("sees raids in visions, 2 days before they come", never a tower),
  "a day before" for a one-day Seer and "3 days" for a three-day one, "No lookout" once the Seer is gone, and the
  Lighthouse keeper's sea-watch line.
- B1055: `QaRaiderLedAway1108dBugTests.aRaiderLedOutOfTheVillagesReachIsNotFoughtOffAndLeavesAtDawn` (a raid's
  last raider one block past the reach: the night round keeps the raid going, and the morning round sends it away).
- B1063: `QaRaidOrphan1108dBugTests` (an unstamped leftover 200 blocks off doesn't keep a new raid going) and
  `QaRaidStamp1108aGameTests` (a raid's own raider led 200 blocks off still keeps it going; a leftover stamped by an
  ended raid, 40 blocks off, doesn't).
- QA (lane D, 2026-11-09, B1063): `QaRaidStampEdges1109dGameTests` (7): with two raids under way, one raid's stray
  standing nearer the other raided hall keeps only its own raid going; a leftover stamped by the same hall's raid one
  tick older, or by a hall one block over, keeps no new raid going and isn't sent away; an unstamped stray counts at 64
  blocks beyond the reach and not at 65, across and up; a stamped stray still counts after the stray and the raids are
  saved and loaded; a leftover the new raid holds in its reach takes its stamp (one stamp, not two) and counts when led
  out again; at dawn the raid's own far stray leaves with it; and a raider's arrow keeps its mark through bounces and a
  save, loses it to a real new owner and doesn't get it back. Mutants killed: 4 of 4 (strays to the nearest hall, 65
  for 64, the old stamp kept, the mark dropped on a null owner).
- B1054: `QaRaiderDrowns1108dBugTests` (a raider husk held under water turns into a zombie that is still a raider) and
  `ConvertedMobsGameTests` (a raider skeleton frozen into a stray keeps its raider tag, stamp and role; a bandit zombie
  that drowns keeps its band's tags; a skeleton the mod never touched converts with no tag of ours; a zombified lair
  piglin keeps the raid but not the lair).
- B1054, B1071, B1072 (QA): `QaConvertedThreats1109dGameTests` (a skeleton frozen in powder snow keeps the raid going
  until killed; a drowned raid zombie leaves at dawn; a converted raider counts only for its own village's raid and still
  spares builders' blocks; every threat tag survives conversion; a raider's burning arrow still lights a roof after its
  shooter is killed, unloaded or converted; a drowned lair captain goes home as captain and survives a restart; a
  converted band member leaves with the band). `QaSiegeConverts1109dGameTests`: a climber that drowns finishes its
  ladder; killing it ends the siege and takes the ladder away. A raider that converts after getting through the gate is
  walked back toward it (B1085, open).
