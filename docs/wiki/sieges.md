# Sieges: rams and gates

ROADMAP 32.4, the first part of sieges. Part of Milestone 32 (1.6), so it is **off for players until the milestone is
finished** (`Expansions.M32`); GameTests and the showcase run with it on. `docs/design/M32.md` is the plan; raids are on
[Raids and threats](raids.md), camps and the Defence page on [Lairs and the Defence page](lairs.md).

Roadmap items: 32.4

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
- **Repair**: a broken gate block is a hole in a finished build, which a builder's upkeep fills like any other; the
  list on the Defence page drops a block once something stands there again.

## Switches

| Key | Default | What it does |
|---|---|---|
| `sieges` | on from 1.6 (off until then) | Off: every raid is a plain one. No shut gates, no portcullis, no rams. |
| `siegeDamage` | on from 1.6 (off until then) | Off: it is still a siege and the rams still pound, but gates lose no hit points and never break. |

The vanilla game rule mobGriefing set to false does what `siegeDamage` off does.

## Saved data

In `aliveworkplace_threats` (per dimension), both new and empty by default, so an older world loads unchanged:

| Key | Holds | Default |
|---|---|---|
| `sieges` | per hall, the siege laid to it: `began`, `dawn` (the day time the gates open again), `over`, `breached`, `breach` (the gate block the rams go for), `out` (the side the raiders came from), `gates` (the breach's blocks in breaking order: `pos`, `hp`, `state` standing, broken or gone, `lane`, `high`, `dropped`) and `portcullis` (every bar dropped) | none |
| `broken` | per hall, the gate blocks rams broke that wait for a builder | none |

## Items, blocks, jobs, commands

None. One new slot on the hall's Defence page (the gates).

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

## Known limits

- No culture the mod ships uses `ram_gates` until the pillager warband (32.7); a datapack culture can.
- Ladders over the walls are 32.5; battle stations, the siege report, "defences first" for builders and the Ramparts
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
- Showcase scene `siege_gate`: a ravager breaking a Palisade Gate, the cracks showing.
