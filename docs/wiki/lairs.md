# Lairs and the Defense page

ROADMAP 32.3. Part of Milestone 32 (1.6), switched on for players in 1.0 with every other expansion
(`Expansions.M32`); GameTests and the showcase run with it on. `docs/design/M32.md` is the plan; raids themselves are
on [Raids and threats](raids.md).

Roadmap items: 32.3, B208, B209, B211, B216, B225, B288, B708, B709

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
- **The broken camp comes down** (B209, `threat/CampCleanup`). Its record, the scouts' report of it and its Defense page
  row go at once; 40 ticks later its blocks above the ground layer go, top down over 100 ticks, with smoke. Only the
  camp's own blocks still as the blueprint set them (`MaterialRules.matches`) go: a block a player put there or changed
  stays, and the trampled ground stays. A chest's or barrel's items spill out as items. The camp's turn isn't saved: it
  is found by matching the blueprint round the camp's middle in each of the four turns (at least a quarter must match).
  A camp whose ground isn't loaded waits; the list is saved, so a restart mid-clearing carries on. A lair that leaves
  in peace (`/workplace threat clear`, a quiet village) packs up the same way. A ship sinks or sails off as before.
- **The land comes back** (B225, `threat/CampGround`). A lair on land records the ground it covers when it is made
  (its box and 4 blocks under it, where it fills hollows with dirt; blocks with contents recorded as air), saved in the
  lairs' file under `grounds` by the lair's middle and moved into its clearing when it breaks. In the tick the camp is
  all down the ground is put back bottom up, but only where the camp's doing is still there: air, the camp's own blocks,
  its dirt filling and floor (also once grass or mycelium has crept over them while it stood, B288), fire, or plants
  grown since; a player's block and every block entity stay. Anything left inside
  ground that came back is lifted onto it. A camp made before B225 (no record) has its pad blended instead: each column
  goes to the height the natural land 3 blocks round the footprint has there (weighted by nearness), topped with that
  land's surface (grass, sand, snow...), the trampled earth, gravel and fire gone. With `campRuins` the columns under
  the ruins' standing blocks are kept; the rest goes back the same way.
- **The Defense page.** Click the guards icon (the iron sword) on the Village Hall. Everyone who may open the hall may
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

None. One new screen page (the Defense page).

## Switches

- `banditCamps` (true): bandit camps. `raiderCultures`: each culture; one switched off founds no lair.
- While the 1.6 gate (`Expansions.M32`) is closed, which ends in 1.0, a camp is the bandit camp as it was: an unnamed "Bandit Chief", three or four men, no
  strength limit on raids, and a guards icon that opens nothing.

- `forceThreatSpawns` (true, B208): every foe (a lair's captain and band, a raid's raiders and extras, the Bandit
  King and his fort's men, a bounty's band, the risen) goes in through `threat/HostileSpawns.add`, which checks the
  result. Another mod refusing the mob at `ServerLevel.addEntity` (Cobbleverse's MobsBeGone lists pillagers,
  vindicators, zombies and nearly every vanilla mob in `config/mobsbegone-blacklist.json`, and discards them there) is
  logged once per mob type and, with the switch on, the mob is put in through the entity manager directly; off, or
  still refused, it's left out and logged. A lair whose captain can't spawn isn't made (before B208 it was recorded
  with no captain and "broken up" at the next round, by nobody), and the land is left untouched: the captain is put in
  the world before a block is placed (before B709 the tents, fire and chests stayed for good); a raid counts only the
  raiders in the world. On Peaceful nothing hostile is spawned and each player within 96 blocks is told once a day (a
  refusal where nobody is near no longer uses up the day's word, B708). A story arc's mobs go the same
  way (B211).
- `forceVillageSpawns` (true, B211): the same blocklist names iron golems, wolves, horses, cats and llamas, so the
  village's own go in through `HostileSpawns.addVillage`: the Golem Smith's golems and the Iron Pact's golem, relief
  riders' horses (a rider whose horse is refused comes on foot), the Beastmaster's foals and wolf pups (bred as vanilla
  breeds them, then added checked), a caravan's pack llamas and a story arc's non-hostile mobs. Forced when on (log:
  "Another mod refused to let minecraft:iron_golem spawn for the ...; Alive Workplace placed it directly"), left out and
  logged ("... and forceVillageSpawns is off") when off. The mod spawns no cats or war dogs itself (war dogs are wolves
  already about, tamed), and vanilla's own spawns and breeding are never forced.
- `campRuins` (false, B209): a broken camp stays as ruins, its fires put out, instead of coming down (the open ground
  round it still goes back, B225). Either way it is forgotten at once.
- Camps from before B209: a save without the `b209` mark lists every village that ever broke up a camp as unswept. The
  hall's round looks 64 to 120 blocks out for a campfire on top with the bandit camp's blueprint round it (half of it
  must match), with no living foe of ours (an `Enemy` with an `aliveworkplace_` tag) within 32 blocks and no standing
  lair, running Bandit King story camp or bounty hideout within 24, and takes it down as above. The village is marked
  swept once every column of that ring was loaded on one round (and its band, if any, is gone).

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
  chance and the Defense page ask `Lairs`.
- 32.3: the named lines use their own keys (`camp_named`, …) rather than changing today's, so the bandit camp reads as
  it always did while Milestone 32 is closed.
- 32.3: the lines avoid "he" and "his", so a culture's list may hold any name.

## Known limits

- A lair whose culture is switched off in `raiderCultures` stays until its captain falls (it sends no raids); the
  design's "leaves at the next dawn" holds only for a quiet village ([Peaceful settings](peace.md), 32.21).
- The Defense page's gates slot is on [Sieges](sieges.md); its other rows are on [Scouts](scouts.md) (32.12),
  [Marching on the lair](war-party.md) (32.13), [Raids and threats](raids.md) (the next attack, 32.14) and [Peaceful settings](peace.md) (32.21); "where" stays rough until a
  scout has been.
- A lair of a structure other than the bandit camp puts its captain and band round the middle of its floor; cultures
  that need their own spots, water or a portal add them on their own pages (32.7 to 32.11).

## Proof

- GameTests: `LairGameTests`: a raid of 5 from a lair of strength 8 leaves 3, two alive at dawn make it 5, each day
  adds one up to 10, a costly night leaves a lair that can't raid, a save and load mid-raid; the captain's name on him,
  on the Defense page and in the chronicle's three lines, every name a real line, the chest's loot, the lair broken up
  mid-raid, the rest days; the fixture `fixtures/bandit_camps_0.138.0.snbt` loading with its camp, chief and rest days;
  another culture's lair; the switches and the closed gate.
- Unchanged: `RaidGameTests`, `ThreatGameTests`, `HallSpecGameTests` and every other caller of `BanditCamps`.
- Showcase scene `defence_page`: the page with a bandit camp standing.
- `B208GameTests`: with the world refusing hostile mobs as MobsBeGone does, a camp's chief and band of 6 and a night
  attack's raiders are in the world, persistent, and still there 100 ticks later; with `forceThreatSpawns` off no camp is
  made or broken up, no raid begins, and the log says why; on Peaceful nothing spawns and a player near is told once.
- `QaRefusedCampBlocksBugTests` (B709): a camp whose chief is refused changes no block round it.
  `QaPeacefulToldBugTests` (B708): a Peaceful refusal 500 blocks from everyone, then one by a player: he is told once.
- `QaRefusedThreatsGameTests`: with the world refusing every hostile mob, `/workplace threat start` brings each of the
  seven cultures' raiders into the world, persistent, and ending the raid takes them away; a war camp, tomb camp, Blackstone
  Outpost and Coven Hut each have their captain and band in the world, still there 100 ticks on and not broken up.
- `B211GameTests`: with the world refusing iron golems, wolves, horses and llamas, the Golem Smith's golem, the Iron
  Pact's golem, a relief rider's horse (with its rider on it), a Beastmaster foal and wolf pup and a caravan llama are in
  the world and still there 100 ticks later; with `forceVillageSpawns` off they are left out and the log says why.
- `B209GameTests`: the chief killed, the camp's record, band and scouts' report are gone at once and its campfire, two
  chests, tents and five beds are gone within 150 ticks, while a player's gold block in a tent stays, the ground under
  the fire is back to the land's and
  the chests' loot lies on the ground; a new camp is made after and stands; with `campRuins` the camp stays, its fire
  out; a save and reload before and halfway through the clearing carries on; a save from before B209 with a broken
  camp still standing is swept by the hall's round (not while its chief is alive), and only once.
- `B225GameTests`: a camp on a grassy slope taken down leaves every block round it as it was before the camp; the same
  after a save and reload before and halfway through the clearing; with `campRuins` the tents stay and every open column
  is as it was; a camp whose record was saved without its ground (before B225) is blended: grass on every top, no
  trampled earth, gravel, cobblestone or fire, no step over one block, each column within 2 of the slope; grass crept
  over the camp's bare fill and floor while it stood still goes (B288).
- Showcase scene `camp_cleared`: a bandit camp on a grassy rise, its chief falling, the camp coming down and the rise
  back as it was.
