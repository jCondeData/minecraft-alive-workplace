# Sieges: rams, gates and ladders

ROADMAP 32.4 (rams and gates) and 32.5 (ladders), the first two parts of sieges. Part of Milestone 32 (1.6), so it is **off for players until the milestone is
finished** (`Expansions.M32`); GameTests and the showcase run with it on. `docs/design/M32.md` is the plan; raids are on
[Raids and threats](raids.md), camps and the Defence page on [Lairs and the Defence page](lairs.md).

Roadmap items: 32.4, 32.5

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

## Items, blocks, jobs, commands

None. One new slot on the hall's Defence page (the gates). The ladders are vanilla ladder blocks.

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

## Known limits

- Ladders (32.5): anything at least two blocks high between a climber and the hall counts as a wall, so a house that
  stands in the way inside an open stretch can get a ladder. A hall shut in a room more than 5 blocks from any ground
  a raider can reach reads as walled in.
- Ladders (32.5): a player who breaks a raider's rung by hand, or the wall behind it, gets a ladder item, as from any
  ladder. The ladders the mod takes away drop nothing.
- Ladders (32.5): a wall with a roof over its top, or more than 10 high, is not climbed. Not tested in a GameTest:
  two or three ladders in one siege, a ladder against a player's wall away from any build, a real (walking) guard
  reaching a ladder by himself, and a wall whose top changes while a raider crosses it.

- No culture the mod ships uses `ram_gates` until the pillager warband (32.7); a datapack culture can.
- Battle stations, the siege report, "defences first" for builders and the Ramparts
  research are 32.6; gates shut from noon on a warned day are 32.14. The siege's chat lines are not in the chronicle
  yet (the report is 32.6).
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
