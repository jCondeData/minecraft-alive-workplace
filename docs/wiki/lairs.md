# Lairs and the Defence page

ROADMAP 32.3. Part of Milestone 32 (1.6), so it is **off for players until the milestone is finished**
(`Expansions.M32`); GameTests and the showcase run with it on. `docs/design/M32.md` is the plan; raids themselves are
on [Raids and threats](raids.md).

Roadmap items: 32.3

## What a player sees

- **A camp out beyond the village.** Now and then (12% a day, as bandit camps always did) raiders make camp 80 to 104
  blocks from the hall, on dry, fairly flat, open ground. Today that is the bandits; any raider culture whose file has a
  `lair` does the same. One lair stands by a village at a time. While it stands the village's night raids come from it,
  twice as often.
- **The captain has a name.** "Chief Harl Ashgrave": one of his culture's twenty, picked when the camp is made. It
  floats over his head and is in every line about the camp: the chat message when it is made, the raid's horn message,
  the chronicle ("Chief Harl Ashgrave and a band of bandits made camp 92 blocks north-east", "5 of Chief Harl
  Ashgrave's bandits raided the village", "Jesse brought down Chief Harl Ashgrave and broke up the bandit camp").
- **The camp has a strength.** Bandits: 6 at first, one more each day, 10 at most. The band standing at the camp is the
  strength (at most 8 stand there). A raid takes its raiders from it: never more than the usual raid size, never more
  than the camp has at home. Those alive when the raid ends go back and rejoin; the dead are gone, and so is anyone you
  kill at the camp. After a costly night the camp is weak, and a camp with nobody left sends no raid until it grows.
- **Kill the captain** and the camp is broken up: the band scatters, the chest (the culture's loot table) is yours, the
  chronicle names who did it, and no camp comes for 5 days.
- **The Defence page.** Click the guards icon (the iron sword) on the Village Hall. Everyone who may open the hall may
  read it:
  - second row: the camp (whose it is), the captain by name, the strength (the icon counts it; the tooltip says how many
    are at the camp, out raiding, lost in the last raid, and how fast it grows), roughly where ("south-east, about 90
    blocks": a side and the distance to the nearest ten), and the days it has stood. With no camp the row says "No camp
    near", and how many days none will come after one was broken up;
  - bottom row: the last three attacks, newest first: the day, who came, how many came and fell, and whether they were
    fought off or the rest got away.
  The guards icon's tooltip names the captain and where he camps.

## How it works

- `threat/Lairs` does it for every culture; `guard/BanditCamps` is the bandits' face of it and keeps its methods.
- **Who camps.** Among the cultures with a `lair` that are switched on and whose `where` fits the village, one is
  picked by `weight`. The bandits also need `banditCamps`; other cultures come only through their `raiderCultures`
  entry.
- **Founding** sets the culture's `lair.structure` down (today's site rules), puts the culture's `loot` table in its
  chests, and spawns the `captain` (his mob, gear, extra health) and a band from the `roster`, in turn.
- **Strength** is `lair.strength`, grows by `growth` a day up to `max` in the hall's round, and `home_max` of it stand
  at the camp. While raiders are out they are counted as `out`; the raid's end settles it.
- **Lines.** A culture's `messages` and `chronicle` keys for a lair are `camp`, `broken`, `broken_by` (chronicle) and
  `raid`; once the captain has a name the keys `camp_named`, `broken_named`, `broken_by_named` and `raid_named` are
  used instead, and get his name as their last argument. A culture that gives none gets the plain "raiders" lines.
- **Names.** `captain.names` is a lang key prefix: `<names>.1` to `<names>.20`. `captain.title` is the lang key of what
  goes before the name. A culture's own name and its lair's are `threat.<namespace>.<id>.name` and
  `threat.<namespace>.<id>.lair`. `lair.icon` is the item that stands for it on the page (a campfire if left out).

## Items, blocks, jobs, commands

None. One new screen page (the Defence page).

## Switches

- `banditCamps` (true): bandit camps. `raiderCultures`: each culture; one switched off founds no lair.
- Until Milestone 32 is finished a camp is the bandit camp as it was: an unnamed "Bandit Chief", three or four men, no
  strength limit on raids, and a guards icon that opens nothing.

## Saved data

`aliveworkplace_bandit_camps` (per dimension; the bandit camps' file, name kept). Each entry of `camps` keeps `pos`,
`hall`, `chief`, `day` and gains, each with a default so an older world loads unchanged:

| Key | Holds | A camp saved before 32.3 |
|---|---|---|
| `culture` | whose lair | `aliveworkplace:bandits` |
| `strength` | the band, raiders out included | the culture's `lair.strength` (6) |
| `grown` | the day the strength last grew | the day the camp was made (so it catches up at once) |
| `out` | raiders out on a raid now | 0 |
| `lost` | how many the last raid lost | 0 |
| `name` | which of the twenty names | picked from the chief's UUID, so it is the same on every load |

`broken_up` entries gain `rest` (days; 5 if missing). `aliveworkplace_threats` gains `history`: per hall, its last three
attacks (`day`, `culture`, `came`, `fell`, `fled`).

## Decisions

- 32.3: the strength is the band without the captain; he never raids (the City rule that sends him comes with 32.7).
- 32.3: raiders "walk back" by leaving at the raid's end as raiders always did (they vanish at the village) and turning
  up at the camp when it is next loaded; nobody walks 100 blocks through unloaded chunks. Scouts trailing them is 32.12.
- 32.3: a bandit killed at the camp lowers the strength at once; a raider's death is counted when the raid ends.
- 32.3: `BanditCamps.near` and `all` answer for bandit camps only, so the lines that say "bandits" (advice, chatter,
  the Steward's rule) stay true when other cultures get lairs; `Lairs.near` answers for any lair. Raids, the raid
  chance and the Defence page ask `Lairs`.
- 32.3: the named lines use their own keys (`camp_named`, …) rather than changing today's, so the bandit camp reads as
  it always did while Milestone 32 is closed.
- 32.3: the lines avoid "he" and "his", so a culture's list may hold any name.

## Known limits

- A lair whose culture is switched off in `raiderCultures` stays until its captain falls (it sends no raids); the
  design's "leaves at the next dawn" is not built yet.
- The Defence page's other rows (gates, scouts, war party, next attack, At peace) come with 32.4, 32.12 to 32.14 and
  32.21; "where" is always rough until scouts exist.
- A lair of a structure other than the bandit camp puts its captain and band round the middle of its floor; cultures
  that need their own spots, water or a portal add them in their own items (32.7 to 32.11).

## Proof

- GameTests: `LairGameTests`: a raid of 5 from a lair of strength 8 leaves 3, two alive at dawn make it 5, each day
  adds one up to 10, a costly night leaves a lair that can't raid, a save and load mid-raid; the captain's name on him,
  on the Defence page and in the chronicle's three lines, every name a real line, the chest's loot, the lair broken up
  mid-raid, the rest days; the fixture `fixtures/bandit_camps_0.138.0.snbt` loading with its camp, chief and rest days;
  another culture's lair; the switches and the closed gate.
- Unchanged: `RaidGameTests`, `ThreatGameTests`, `HallSpecGameTests` and every other caller of `BanditCamps`.
- Showcase scene `defence_page`: the page with a bandit camp standing.
