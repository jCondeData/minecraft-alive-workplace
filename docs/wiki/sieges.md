# Sieges: rams, gates, ladders and battle stations

ROADMAP 32.4 (rams and gates), 32.5 (ladders) and 32.6 (battle stations and the morning after), the three parts of sieges. Part of Milestone 32 (1.6), so it is **off for players until the milestone is
finished** (`Expansions.M32`); GameTests and the showcase run with it on. `docs/design/M32.md` is the plan; raids are on
[Raids and threats](raids.md), camps and the Defence page on [Lairs and the Defence page](lairs.md).

Roadmap items: 32.4, 32.5, 32.6

## What a player sees

- **A raid on a walled village is a siege.** When raiders who ram gates come to a village with at least one finished
  wall or gate build, the chat says "Oakbrook is under siege! The gates are shut and the portcullis is down."
- **The gates shut at once**, whatever the hour and with or without guards, and the **portcullis drops**: iron bars
  come down across the gateway of every finished Gatehouse, under the drawn-up ones. Players can still open the fence
  gates by hand. Both open again at the first dawn after, once the raid is over.
- **The ram goes for the gate** nearest the raiders' side: a ravager, or without one the raiders with axes. Every blow
  is heard, the block cracks a little more with each, and at the end it breaks: nothing drops. The chat says "The
  raiders have broken through a gate of Oakbrook!" The other raiders wait a few blocks behind the ram and walk in
  through the gap.
- **A Palisade Gate holds about 10 seconds, a Gatehouse about a minute**: its portcullis has to go first, then the
  fence gate behind it.
- **Only gates the builders made break**: the fence gates, doors and iron bars of finished wall and gate builds. A
  gate you placed yourself is never hit, even right beside one.
- **Ladders go up the walls** (32.5). Raiders who carry ladders (the pillagers of the warband, 32.7) walk to the wall
  in their way and set a ladder up its outer face, a rung every half second, with the sound of a ladder being placed.
  When the first one stands the chat says "Ladders at the walls of Oakbrook! A guard on the wall can throw them down."
  They climb it, cross the wall's top and drop inside; the other raiders follow up the same ladder while the gate is
  still shut.
- **A guard on the walkway throws a ladder down** when he is within 2 blocks of its top: the whole ladder breaks away
  at once, whoever is on it falls, and the raiders need 10 seconds before they raise it again.
- **Ladders go on any wall, yours too, but only into air**, and never against a gate. When the raid is over every
  ladder is taken away; nothing else of the wall is touched and no ladder item is left behind.
- **No raider appears inside the walls**: a siege gathers beyond the outermost finished wall on the side it comes from.
- **Guards take battle stations** (32.6) when a siege begins, instead of rallying at the bell. Archers go up to the
  tops of finished Wall Towers and Lookout Towers and the walkways of the Gatehouse, Stone Wall and Palisade, on the
  side the raiders came from, and spread over the builds before two share one. Knights stand 3 blocks inside the gate
  the rams go for, medics 3 blocks behind them. Guards with only a sword, and anyone who finds no free station, answer
  the bell as before. Their card says "going to their battle station" and "at their battle station".
- **From a station an archer shoots 24 blocks** (16 from the ground) and does a quarter more damage to a foe 3 or
  more blocks below. On a station he never climbs down to chase.
- **The morning after**, at the dawn the gates open, the chronicle has a siege report (a new kind with the iron bars
  icon): "The siege: 9 raiders came and 7 fell, 5 to the guards and 2 to players. The gate held. Hero of the night:
  Brannoc, with 3 kills."
- **Players who killed a raider in a siege that was fought off are Heroes of the Village for a day**, and every
  villager of the village is 10 happier for a day: "we held against the siege" on their mood. A siege the raiders
  walked away from at dawn gives neither.
- **For 2 days after a siege (and during it) builders mend walls, gates and towers first**, before any house.
- **New research, Ramparts** (one level, after Fortification I, the stone brick wall icon): in a siege the gates have
  twice the hit points, and archers on stations shoot 28 blocks.
- **The Defence page shows the gates** (the guards icon on the Village Hall, third row): open or shut, how many stand,
  the portcullis up or down, "Under siege", and how many gate blocks rams broke that wait for a builder.

## How it works

- `threat/Sieges` registers the tactic `ram_gates` with the threat engine. A raid by a culture whose file lists that
  tactic, on a village with a finished build from the walls and gates family (Palisade, Palisade Gate, Palisade Tower,
  Stone Wall, Wall Tower, Gatehouse, any style, their later tiers, and the wall kits' pieces), is a siege. No culture
  the mod ships rams gates yet: the pillager warband (32.7) is the first.
- **Hit points**: fence gate 60, door 80 (both halves as one), iron bars 150. A ram (role `ram`) does 12 every 40
  ticks; an axe does 4 every 20. So a fence gate takes a ravager 5 blows, and a bar 13.
- **The breach** is the gate build with the gate block nearest the middle of the raiders when the siege begins. Its
  blocks are broken in a fixed order: the middle way through first, from the outside in (at a Gatehouse: the two
  dropped bars, then the fence gate), then the ways beside it, and last what hangs above head height (the drawn-up
  bars), which only the ram itself needs gone.
- **The cracks** are the block's destroy progress, 0 to 9, from the share of hit points gone, shown again every 10
  seconds because a client forgets them after 20.
- **One director a siege**, every 20 ticks, asking for at most 4 paths a tick. It walks the rams to the block, has
  them strike, keeps the others 5 blocks out while the way is shut and sends them 6 blocks in once it is open. A raider
  with a foe in sight is left to fight. Each raider carries a goal that holds it to the director's order; without it
  their own hunting walks them round the walls after villagers they cannot reach.
- **The portcullis** is found from the blueprint: under every iron bar a gate build's blueprint has, bars are set down
  through the blueprint's air to the floor. The siege remembers each one and takes those still standing away when it
  lifts.
- **Lifting**: the siege is kept until the raid is over and the first dawn after its start (23500) has come. Then the
  bars go up, the gates open, the cracks go, and the siege is forgotten. Hit points belong to the siege, so a gate that
  was only damaged is whole again.
- **Ladders** (`threat/Ladders`, tactic `ladders`, role `climber`) run beside the director, every 10 ticks and never
  in the director's own tick, with at most 4 path requests a tick. A raid by a culture that names `ladders` on a walled
  village is a siege even without `ram_gates`.
  - A climber asks for its path to the hall (to within 5 blocks). If the path gets there, there is no wall in its way
    and it needs no ladder. If not, the wall is looked for from where the path ends, straight toward the hall: the
    first thing at least two blocks high. A ladder already within 8 blocks of that spot is used; otherwise a new one is
    begun there, or up to 3 blocks to either side, wherever every rung goes into air. At most 3 ladders a siege.
  - The ladder reaches to where the wall's column leaves two blocks free, at most 10 rungs: over a Stone Wall's
    crenel that is 5 rungs, over a merlon 6. One rung is set every 10 ticks while a climber stands within 3 blocks of
    the foot.
  - A goal on the raider holds it to its ladder: it waits at the foot, climbs at a ladder's speed, walks across the
    top and is let go once it is past the wall's inner face, where it drops. The others (never the rams) are sent to
    the nearest standing ladder while the breach is shut.
  - **Throwing down**: a grown guard, awake, standing on something at least 2 blocks above the ladder's foot, within
    2 blocks of its top rung. The rungs go (break particles, no drops) and the ladder is not raised again for 200
    ticks. A raider on it falls and takes the fall's damage.
  - Every rung is recorded in the siege's saved data before anything else happens to it. Each tick the ladders are
    checked against the world: a rung someone took, and every rung above it, comes off the list and is set again.
    `Ladders.takeAway` runs when the raid ends and again when the siege lifts, and works from the saved list alone, so
    ladders standing when the server stopped go too.
- **Battle stations** (`guard/BattleStations`, 32.6) run beside the director: one watch a siege, every 20 ticks in
  a tick of its own, sending at most 4 guards on their way a tick.
  - A station is a block of a finished wall, gate or Lookout Tower blueprint, at least 2 above its floor row, with a
    whole top face and nothing in the two blocks above it, that is not a merlon (a spot one higher than a spot beside
    it). Each blueprint is read once and kept in memory. A Wall Tower has 7: its top floor's planks without the
    ladder's hole and the lantern's plank.
  - When a station is taken it is checked against the world: its floor must stand and two blocks be free. A station
    built over or with its floor gone is skipped, and a guard on it gets another.
  - An archer's choice among the free stations within 24 blocks of his Guard Post: the breach's side of the village
    first, then the build with the fewest archers, then the highest, then the nearest the breach (with no breach, the
    farthest out from the hall).
  - **The climb**: villagers cannot path up a ladder. A guard walks as near his station as his path gets; once he
    is within 3.5 blocks of it and below it he is set on it, with a ladder's step sound.
  - On the way an archer fights only what comes within 8 blocks. From a station his bow's arrow flies faster and
    flatter so that it carries the range; its damage is scaled back so only the height rule adds to it.
  - A knight or medic fights only a foe within 6 blocks of his spot, so the gate's defenders wait for what comes
    through instead of pressing against the bars.
  - An archer on a station is kept on it: any other walk of a villager's day is stopped while he stands there.
  - A guard is an archer, knight or medic by what is in the off hand (bow or crossbow, shield, healing potion), as
    everywhere else. A guard whose kind changes gives his place up.
  - Nothing of it is saved: after a restart the stations are read and handed out again.
- **The report** (`threat/SiegeReport`, 32.6): every death of a raider (the raid's tag) within the village's radius
  plus 64 blocks of a hall under siege is counted in the siege's saved data: to a guard and which, to a player, or to
  anything else (golems, mercenaries, falls). The hero is the guard with the most kills, the first to get there on a
  tie. The report is written when the siege lifts. `Tactic.end`'s `fled` decides "won".
- **Defences first**: `build/Upkeep` sorts a builder's finished builds so the walls and gates family comes first
  while his bench is in a village under siege or within 48000 ticks after one.
- **Ramparts**: the siege notes the factor (2 or 1) when it begins, so research finished in mid-siege changes the
  next siege, not this one. The archers' reach follows the research at once.
- **Gathering** (`Sieges.outside`, used by `VillageRaids.start` for cultures with `ram_gates` or `ladders`): the line
  from the hall through the raid's gathering point is followed to where it leaves the footprint of each finished wall
  or gate build (3 blocks wider each way, so corners and joints count); if the point is not at least 7 blocks beyond
  the farthest of them it is moved out to there. Raiders who come through a Nether portal are not moved.
- **Repair**: a broken gate block is a hole in a finished build, which a builder's upkeep fills like any other; the
  list on the Defence page drops a block once something stands there again.

## Switches

| Key | Default | What it does |
|---|---|---|
| `sieges` | on from 1.6 (off until then) | Off: every raid is a plain one. No shut gates, no portcullis, no rams, no ladders, and raids gather where they always did. |
| `siegeDamage` | on from 1.6 (off until then) | Off: it is still a siege and the rams still pound, but gates lose no hit points and never break. |

The vanilla game rule mobGriefing set to false does what `siegeDamage` off does, and with it off no ladder is set
either. `siegeDamage` off alone leaves the ladders: then a siege is decided at the walls. Ladders have no switch of
their own.

## Saved data

In `aliveworkplace_threats` (per dimension), all new and empty by default, so an older world loads unchanged:

| Key | Holds | Default |
|---|---|---|
| `sieges` | per hall, the siege laid to it: `began`, `dawn` (the day time the gates open again), `over`, `breached`, `breach` (the gate block the rams go for), `out` (the side the raiders came from), `gates` (the breach's blocks in breaking order: `pos`, `hp`, `state` standing, broken or gone, `lane`, `high`, `dropped`), `portcullis` (every bar dropped), and since 32.5 `ladders` (every rung set, to take away) and `laddered` (the players were told) | none; `ladders` empty and `laddered` false in a siege saved before 32.5 |
| `broken` | per hall, the gate blocks rams broke that wait for a builder | none |
| in each of `sieges` (32.6) | `hp_factor` (what gate hit points were multiplied by), `came`, `fled`, `to_guards`, `to_players`, `to_others`, `kills` (guard, count, name), `players` (who killed a raider) | 1, 0, false, 0, 0, 0, none, none |
| `after` (32.6) | per hall, the game time until which villagers are glad they held (`held`) and builders mend defences first (`mending`) | none |

## Items, blocks, jobs, commands

None. One new slot on the hall's Defence page (the gates). The ladders are vanilla ladder blocks. 32.6 adds the
research topic Ramparts and the chronicle kind SIEGE; both use vanilla item icons, so no new art.

## Decisions

- 32.4: the siege is saved beside the raid, not inside it as the design note drew it, because it outlives the raid:
  the gates stay shut until dawn after the last raider is gone.
- 32.4: every block has its own hit points, so "about a minute through a Gatehouse" is two bars (26 seconds each) and
  a fence gate (10 seconds) for the middle way.
- 32.4: the dropped bars can be broken like the build's own gate blocks, but they are not on the list for the builder:
  they are not in the blueprint, and the portcullis is whole again the next time it drops.
- 32.4: a gate block a player breaks during a siege is noted as gone, not as broken by the rams; the way counts as
  open and the rams turn to the next block.
- 32.4: with the gates holding (the game rule or `siegeDamage`), the rams still strike and are heard; nothing cracks.
- 32.4: a door counts once, by its lower half, and both halves go together.

- 32.5: ladders are taken away as soon as the raid is over, not at dawn with the portcullis: with no raider left
  they would only be a way in for whatever comes next.
- 32.5: no switch of their own. The design's table has ladders under `sieges`; mobGriefing off stops them because
  setting a rung is a mob changing the world.
- 32.5: "the spot nearest them" is where the climber's own path ends. Raiders close together end at the same spot and
  share one ladder; raiders far apart along a long wall get their own, up to 3.
- 32.5: the other raiders use ladders only while the breach is shut; once a gate is open they go through it as before.
- 32.5: a guard needs no weapon and no order to throw a ladder down; standing there is enough. He is found by his job
  (profession Guard), so mercenaries and golems do not.
- 32.5: the gathering point is moved only for cultures that lay sieges; the plain monsters gather as they always did.

- 32.6: the class is `guard/BattleStations`, not the design's `threat/Stations`: `work/Stations` already exists,
  and `threat/` does not import `guard/`, which a class that orders guards about has to.
- 32.6: no new switch. Stations and the report are part of a siege, so `sieges` off turns them off.
- 32.6: a guard is set on his station for the last step instead of climbing rung by rung, because villagers have no
  ladder pathfinding. He has to walk to the foot first.
- 32.6: archers spread over the builds before taking the highest station twice, so one tower does not get them all.
- 32.6: "players who fought" are the players who killed a raider of the siege. A player who only wounded one, or
  who is offline at dawn, is not made a hero.
- 32.6: the report counts what died, so raiders who fled are "came" but not "fell". Kills by golems and mercenaries
  count as fallen but are in neither of the two named numbers.
- 32.6: "the morning after" is the dawn the siege lifts (23500), the same moment the gates open.
- 32.6: "when a warned siege is near" needs the warnings of 32.14, which does not exist yet. Stations are taken
  when the siege begins; 32.14 can call the same watch earlier.

## Known limits

- Battle stations (32.6): a station only counts if the guard can get within 3.5 blocks of its foot. Merlons can
  hide a foe from an archer standing behind one; he shoots when he has a line of sight. A blueprint whose walkway is
  made of slabs' lower halves or stairs is not read as stations.

- Ladders (32.5): anything at least two blocks high between a climber and the hall counts as a wall, so a house that
  stands in the way inside an open stretch can get a ladder. A hall shut in a room more than 5 blocks from any ground
  a raider can reach reads as walled in.
- Ladders (32.5): a player who breaks a raider's rung by hand, or the wall behind it, gets a ladder item, as from any
  ladder. The ladders the mod takes away drop nothing.
- Ladders (32.5): a wall with a roof over its top, or more than 10 high, is not climbed. Not tested in a GameTest:
  two or three ladders in one siege, a ladder against a player's wall away from any build, a real (walking) guard
  reaching a ladder by himself, and a wall whose top changes while a raider crosses it.

- No culture the mod ships uses `ram_gates` until the pillager warband (32.7); a datapack culture can.
- Gates shut from noon on a warned day, and stations taken before the raiders are there, are 32.14.
- A village with walls but no gate build is besieged (the message, the day's end) but there is nothing for a ram to do.
- The breach is picked once, when the siege begins; if every block of it is open the rams fight as other raiders do.
- After a restart the director sends raiders that were already inside back through the breach once, if they have
  nothing else to do.
- Not tested in a GameTest: doors (no build of ours has one in a gate), a raider with an axe as the ram, wall kits'
  gates, two sieges at once, and what a client sees of the cracks (the showcase scene films them).

## Proof

- GameTests: `SiegeGameTests`: a ravager ram breaks a Palisade Gate's block within 400 ticks and a pillager is through
  within 600, nothing dropped, and a player's fence gate beside it untouched; a Gatehouse with its portcullis down takes
  at least three times as long; the portcullis and the gates through a raid's own start, shut at noon without a guard,
  still shut after the raid, open at dawn, with the Defence page's lines; the gate standing after 2400 ticks with
  the game rule off; the two switches; a save and load half-way through a gate; a gate the player breaks first; the
  director's four paths a tick; a village without a finished wall or gate, and a culture that does not ram.
- GameTests: `LadderGameTests` (32.5): a pillager climber is over a Stone Wall within 600 ticks by a ladder of 5
  rungs set one at a time on its outer face, and a vindicator follows up the same ladder; a guard on the walkway
  throws the ladder down with the climber on it, no raider ladder is left within 3 blocks of him and the climber is
  back on the ground outside; a save and load with the ladder half up and another with it standing, then the raid
  ends: no ladder left and every block of the area as before; the wall broken behind a rung; mobGriefing off and
  `sieges` off; a ring of Stone Walls, with the gathering point beyond it from 16 sides and a started raid's raiders
  all outside.
- Showcase scene `siege_gate`: a ravager breaking a Palisade Gate, the cracks showing.
- Showcase scene `siege_ladders`: pillagers laddering a Stone Wall, one climbing over, and a guard throwing the ladder
  down.
- GameTests: `BattleStationGameTests` (32.6): an archer whose Guard Post is 6 blocks from a finished Wall
  Tower is on its top within 400 ticks of the siege starting, on the station nearest the breach's outside, and from
  there shoots and hits a raider 22 blocks off; the tower's blueprint read once; at most 4 guards sent a tick; 28
  blocks with Ramparts. With the tower's top built over the same archer has no station and shoots only from 16
  blocks or less. A station's floor broken under the archer: he takes another. A knight's place 3 blocks inside the
  Palisade Gate and a medic's 6, both walked to, none for a plain guard, and none at all with the sieges switch off. The
  morning after: two guards and a player kill raiders, a save and load in mid-siege, the raid ends, dawn: the
  chronicle's one SIEGE entry word for word with the hero's name, the player a Hero of the Village, a villager's mood
  10 higher with "we held", defences first for 2 days, all kept through another load. Ramparts: fence gates of 60 hit
  points become 120, the topic needs Fortification I and has its slot on the research screen; a save without the 32.6
  fields loads with defaults. A builder with planks and fence gates in his chest puts the broken gate block back
  before the missing plank of a house recorded first. Every new sentence has its text. All 8 passed twice here
  together with `SiegeGameTests` and `LadderGameTests`; not checked for flakiness with the repeat generator, and not
  tested: Lookout Tower, Gatehouse, Stone Wall and Palisade stations in a GameTest (the scene uses a Gatehouse), a
  crossbow from a station, the damage an arrow does with the height bonus (only the factor), an offline player.
- Showcase scene `battle_stations`: two archers leaving their Guard Post for stations on a Gatehouse and a Wall Tower
  and shooting down at raiders hacking at the portcullis. Not filmed yet when this was written.
