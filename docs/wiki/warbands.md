# Pillager warbands

ROADMAP 32.7. Part of Milestone 32 (1.6), so it is **off for players until the milestone is finished**
(`Expansions.M32`); GameTests and the showcase run with it on. The warband is a raider culture (see
[Raids and threats](raids.md)) with a lair (see [Lairs and the Defence page](lairs.md)) that lays sieges (see
[Sieges](sieges.md)).

Roadmap items: 32.7

## What a player sees

- **A war camp.** By a Town or a City in the plains, meadows, forests (birch, dark, flower, cherry), the taiga, the
  snowy lands or the savanna, a pillager warband may make its War Camp 80 to 104 blocks out: a ring of sharpened logs
  with a gate, three canvas tents, the Warlord's dark tent with the loot chest, a ravager pen, a siege yard of logs and
  ladders, and a pole flying the ominous banner. Chat says so ("War drums beyond Thornholm: Warlord Grath Ironmaw and his
  pillager warband have raised a war camp 92 blocks south").
- **The Warlord.** A vindicator in iron with the ominous banner on his head and 40 more health, with one of twenty
  names ("Warlord Kael Blackthorne") over his head. His band: pillagers with crossbows (the climbers, 55%) and
  vindicators with axes (45%).
- **Its raids are sieges.** Its raids come from the camp, by night. On a village with walls the pillagers set ladders
  against them and the rams go for the gate. Besides the band, every raid brings **ravager rams: one at a Town, two at
  a City, and an evoker at a City**.
- **At a City the Warlord leads the siege himself.** The horn's message says so ("Warlord Grath Ironmaw himself leads 9
  of his warband against Thornholm! Bring him down and his war camp breaks."). **Kill him at the walls and the camp
  breaks**: its band at the camp scatters, its chest is yours, the chronicle says who did it; the raiders already at the
  walls fight on. If he lives through the night, he goes home to his camp at dawn.
- **The chest** (`aliveworkplace:chests/war_camp`): emeralds, iron, a crossbow or two, an ominous bottle, often arrows,
  an axe or food, and now and then a totem of undying.

## How it works

- The culture is the data file `raider_cultures/pillager_warband.json`: `where` (Town and up, the lands above),
  weight 30, arrival from its lair, the roster, the Warlord as its `captain`, tactics `ram_gates` and `ladders`, the
  lair `camp/war_camp` and the loot.
- **Bandits or a warband.** In these lands bandit camps still come at Village rank (the warband needs a Town). From
  Town on both fit, and the lair is picked by weight: the warband's 30 against the bandits' 10, so a warband is three
  times as likely as bandits. Elsewhere only bandits come.
- **Strength.** 10 when the camp is made, 2 more a day, at most 18; at most 10 stand at the camp. A raid takes its band
  from the strength as every lair's does (32.3).
- **Extras.** A culture file may list `extras`: who comes with every raid on top of the roster, by the village's rank
  (`{"town": 1, "city": 2}`: the highest rank listed that the village has reached). The warband's are its ravagers
  (role `ram`) and its evoker. They are no part of the camp's strength: the band never loses them, and they don't
  rejoin the camp after the raid.
- **The captain leads.** A captain with `leads` (the Warlord: `city`) leaves his camp at the head of a raid on a village
  of that rank or higher: he walks with the raiders and is one of them till the raid ends. While he is out his camp's
  round doesn't take his absence for his death (his death is told when it happens, wherever he is). At dawn, if he
  lived, he goes back to his camp; the others vanish as raiders always do.
- The camp's middle is the yard by the fire, where the Warlord stands.

## Switches

| Key | Default | What it does |
|---|---|---|
| `raiderCultures` | `pillager_warband: true` | false: no warband makes camp, and a standing camp sends no raids. |
| `sieges` | on with Milestone 32 | off: the warband's raids are plain raids (no ram at the gate, no ladders). |
| `siegeDamage` | on with Milestone 32 | off: the rams pound and the gates hold. |

## Saved data

Nothing new: the camp is a lair saved as every lair is (`aliveworkplace_bandit_camps`, 32.3). The Warlord out on a raid
carries the raiders' entity tags, so a restart mid-raid keeps him with it.

## Items, blocks, jobs, commands

- Raider culture `aliveworkplace:pillager_warband`.
- Structure `aliveworkplace:camp/war_camp` (23 x 9 x 19), drawn in `tools/blueprints/bandits.py` (`war_camp`).
- Loot table `aliveworkplace:chests/war_camp`.

## Decisions

- Every raid of the warband brings its ravagers and evoker, siege or not: a raid on a village without walls is a plain
  raid, but it is the same warband (32.7).
- The extras and the Warlord don't count against the camp's strength: the strength is the band of pillagers and
  vindicators (32.7).
- While Milestone 32 is closed only bandits make camp by chance; no other culture's lair comes (the expansion gate,
  B76).

## Known limits

- The ravager pen at the camp stands empty: the ravagers turn up with the raid.
- A Warlord killed by something other than a player or a villager is named in the chronicle without who did it.

## Proof

- `WarbandGameTests.aTownInThePlainsGetsAWarCamp`: the three-to-one rule by a Town in the plains, the war camp with
  its named Warlord, band of 10 and loot; one ravager at a Town; a save and load mid-raid; the band's survivors rejoin.
- `WarbandGameTests.atACityTheWarlordLeadsAndFalls`: two ravagers and an evoker at a City, the Warlord among the
  raiders, the camp standing while he's away, and broken by his death at the village.
- `WarbandGameTests.theWarlordGoesHomeAndTheSwitches`: the Warlord home at dawn; the lines a player reads; the config
  switch, the expansion gate, a Village and a desert Town get no warband.
- Showcase scene `warband`: the war camp south of a palisaded Town, and the warband's siege on its gate.
