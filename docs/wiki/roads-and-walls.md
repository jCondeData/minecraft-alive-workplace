# Roads and walls

The roads and the wall you draw on a City Plan get built by the village's builders: streets with lamps, bridges and
steps, a road to each trading partner, and a wall once the village has been raided. Part of 1.1 (Villages that build
themselves): off until that expansion is finished.

Roadmap items: 27.4, 27.15, 27.16, 27.17, 27.18

## What a player sees

On the City Plan's screen two tools draw lines: **Road** (click points, double-click to end; a lane 1 block wide, a
street 3 or an avenue 5) and **Wall line** (one line round the village, open or closed). Holding the plan shows them
on the ground as coloured dust.

Once the village has a Steward, the approved roads are built a stretch at a time by the nearest free builder, in the
road style of the zone they start in. Every newly finished building gets a lane from its door to the nearest road.
Streets get a lamp every 16 blocks and at crossings; lanes get a lantern post every 12. Water or a drop gets a
bridge; a one-block rise gets stairs.

Villages joined by a caravan route each build their half of a road towards the other. If a half stops short it ends
at a milestone: a stone post with a lantern and a sign naming the other village and how far it is.

After a raid (or with a bandit camp near) the Steward proposes a wall on his desk. The line goes on the plan first,
so you see what he means before you approve it. A Hamlet or Village gets a palisade; a Town or City gets stone, and a
Town replaces its palisade a piece at a time.

## How it works

**Finding the way.** For each approved road the Steward's hall searches a way over the ground (at most 600 steps of
the search a tick per hall): it keeps the road's width clear, goes round buildings and anything a player placed, and
climbs at most one block at a time. The way is saved on the road and cut into stretches of 24 blocks. Each stretch
becomes a small generated blueprint and an ordinary build site.

**Roads wait their turn.** At most 2 stretches are open at once, and none while a building of the village waits for
a builder. Finished stretches are remembered on the plan, not in the list of finished buildings, so roads never
change the village's rank, its map or who lives where.

**Styles.** One file per style in `data/aliveworkplace/road_styles/`: the middle and edge blocks, the slab and
stairs for steps, the bridge's deck, rail and pillar, the lamp and the lantern post. Six ship: as drawn (dirt path
with coarse dirt and gravel), stonework, sandstone, dark oak, cherry and, with Cobblemon, apricorn. A zone's building
style picks its road style.

**Bridges and steps (27.16).** A bridge spans at most 16 blocks, with a pillar every 4 blocks going down at most 12.

**Roads between villages (27.17).** Each village plans a street from its own roads towards the point halfway to the
other hall, up to `caravanRoadReach` blocks or halfway, whichever is less. When the two halves would end within 32
blocks of each other, both go on to the halfway point so they meet. The way is only searched where the world is
loaded: no chunk is loaded for it, and the search waits. On a finished road caravans arrive in three quarters of the
time.

**Walls (27.18).** A wall is made from a kit (`data/aliveworkplace/wall_kits/`): a segment, a corner tower and a
gate, for a range of ranks. The line is laid out into pieces: a tower at every corner and at least every 28 blocks,
whole segments between them, and a gate wherever a road of the plan crosses. Each piece sits at its own ground
height. With no wall line drawn, the Steward draws one of his own, 4 blocks outside the zones. At most 3 wall pieces
are open at once.

## Switches

| Key | Default | What it does |
|---|---|---|
| `stewardRoads` | on from 1.1 | Off: roads are drawn on the plan but never built |
| `caravanRoads` | on from 1.1 | Off: no roads between villages |
| `caravanRoadReach` | 256 (32 to 512) | The longest half of a road towards another village |
| `stewardWalls` | on from 1.1 | Off: the Steward never proposes a wall |
| `steward` | on from 1.1 | Roads and walls need a Steward |

## Saved data

All on the hall, inside `plan`:

- Each road: its points (as offsets from the hall), `width` (3 when missing: a street), its style, whether it is
  approved, the way found, the stretches built, and whether it is a lane or leads towards another village. A road
  saved before 27.15 loads with no way found and nothing built. At most 24 roads of 64 points each, 64 lanes and 8
  roads to other villages.
- The wall line and the approved wall (an approved wall survives a save and reload).
- Each caravan route keeps both halves' end and state, so a village knows the other half even where the world isn't
  loaded.

The generated blueprints for stretches are saved the way the Shape Planner saves its blueprints.

## Items, blocks, jobs, commands

- Item: City Plan (`aliveworkplace:city_plan`).
- Road styles: `aliveworkplace:as_drawn`, `aliveworkplace:stonework`, `aliveworkplace:sandstone`,
  `aliveworkplace:dark_oak`, `aliveworkplace:cherry`, `aliveworkplace:apricorn`.
- Wall kits: `aliveworkplace:palisade` (Hamlet and Village), `aliveworkplace:stone` (Town and City).
- Built by ordinary Builders; planned by the Steward. No commands.

## Decisions

- 27.15: roads are not buildings. They are kept on the plan, so they never raise a village's rank.
- 27.18: a whole wall counts as one building for the rank, not one per piece.
- 27.15: a road goes round a player's fence or house and leaves it standing; it never clears what a player placed.
- 27.17: no chunk is ever loaded to plan or build a road between villages; the work waits until a player is near.
- 27.18: a wall is only proposed after a raid in the last 7 days or with a bandit camp near; a village never raided
  gets no wall proposal.

## Known limits

- A bridge longer than 16 blocks isn't built; the way has to go round.
- A road between villages reaches 256 blocks at most by default, so far-apart villages get two halves and two
  milestones, not one road.
- Roads are built only while no building waits for a builder, so in a busy village they come last.
- Not checked for this page: how gates open and close (the README says they are shut at night).

## Proof

GameTests: `RoadGameTests` (12: a 40-block street three wide in stonework with stair steps, a player's fence left
standing, save and reload mid-stretch, the rank left alone, a new building's lane, the two-stretch limit, the switch
off), `CaravanRoadGameTests` (4), `WallGameTests` (9), `CityPlanRoadGameTests` (4).

Showcase scenes: `roads` (a street between two houses), `bridges`, `caravan_road`, `walls` (a palisade going up),
`city_plan_ground` (the plan on the ground).
