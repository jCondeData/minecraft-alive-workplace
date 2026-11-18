// The Wall Tower (`wall_tower`, Level II `wall_tower_2`), the plan: drawn before any block. ROADMAP 52.3 (the method of
// Milestone 49); the brief is tools/blueprints/briefs/wall_tower.md. The corner and mid-run tower of the stone wall kit,
// drawn together with its segment (../stone_wall): it takes that plan's KIT palette, its walk height (paving y 3) and
// its string course, so a run of the kit reads as one wall. recipe.mjs assembles both levels from ../parts.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Walk-Through Tower. A square stone tower, cobble foot, the wall's pale string course run round it at the
//      walk's height, dressed quoins; the wall walk passes through its middle storey by an open arch in each side
//      (no door: one gate block only), a guard room between; a door at the foot on the village side, a ladder up the
//      back wall to a crenellated fighting top with two crenels a side. Level II crowns the top: the merlons carried up whole,
//      and over each side's two crenels a little dark-oak gable (four gables round a flat lead), the hoarded wall's roof. Story: the wall's
//      knuckle: the walk runs through it, the guards sleep under it, the archers shoot over it.
//   B. The Round Drum. The 5 x 5 cut to a chunky octagon, a conical hat. Story: an older tower the wall was built up to.
//      In a 5 x 5 the corners cut away leave a 3-wide top: too few station cells, the crenels sit on the diagonals the
//      gallery rule doesn't count, and an octagon butts a straight segment with a gap at every corner.
//   C. The Stair Tower. An open stone stair spiralling round a solid core to a platform. Story: a lookout, no rooms.
//      Reads well from afar, but the stair's open treads would be stations on every level (the brief wants one level),
//      and a 5 x 5 has no room for a core and a stair both.
// Strongest: A. It is the only one that is part of the wall rather than standing beside it: the walk's line (its pale
// course, its arches at the walk's height) runs through it, so segments and towers make one continuous rampart. Its top
// gives every side two crenels to shoot from (the brief's "every side has open shooting cells"), and Level II grows
// from it with nothing pulled down but four finials: the merlons rise whole to carry a crown of four gables, one over
// each side's pair of crenels, so every side shows its own gable (a silhouette no plain pyramid gives).
// Critic round 1 (tools/blueprints/briefs/walls_52_3_critic.md) answered: the crown's own outline (four gables), the
// battered foot, machicolations under the deck and slits with a sill and a head, both rooms furnished as a guardroom
// (no bed, no POI), the wall walk's arches framed in pale stone, a lantern over every side's pair of gallery stations.
//
// Coordinates: x across the front (0..4), y up (0 = the layer on the clicked block), z from the front (z = 0, the
// village side, the wall line) to the back (z = 4, outward). Exactly 5 x 10 x 5 (Level II 5 x 12 x 5).
//
// Masses (named parts the owner's feedback can point at):
//   Shaft     the stone walls, the 5 x 5 ring, y 0..6: cobble foot y 0..1, ashlar y 2..6, the String course y 3 (pale
//             andesite, the wall walk's paving height: where a segment butts a side, its walk's course runs on round
//             the tower), Quoins of dressed andesite and stone brick up the corners.
//   Batter    the foot splayed: cobble stairs facing in at y 0 on every face cell but the corners and the door's sill.
//   Guardroom the ground room inside (x 1..3, z 1..3), a stone floor at y 0 (stood on at y 1), the Door at (2, 1..2, 0)
//             on the plinth, its lintel the string course; a spruce table (a fence and a carpet) between two stair
//             benches on the west side, the walking line from the door to the ladder kept clear on x 2; a lantern hung
//             from the floor above.
//   Walk room the middle storey: a spruce slab floor at y 3 (unsturdy: no stations below the top), the Walk arches
//             open in both sides at (0 / 4, 4..5, 1) on the wall walk's line z 1: pale andesite sill (y 3), jambs (z 0
//             and z 2, y 4..5) and head (the machicolation over it), so the walk visibly runs into the tower; arrow
//             loops out the back and over the door, each with a plunging sill and a pale head; under the back loops the
//             arrow store (a chest, x 1) and a guard's bench (x 3), each on a plank patch of the floor; a lantern.
//   Ladder    up the back wall, (2, 1..7, 3), facing north, through both floors: a spruce hatch where it passes the
//             walk room's floor (y 3), the deck's hole open to the sky.
//   Machicolations  the shaft's top course (y 6) as pale upside-down stairs on the twelve side cells, the corners full
//             as piers: a notched band under the deck's rim, the top reads as a crown, not the shaft cut off.
//   Fighting top  the deck at y 7 (the whole 5 x 5, its rim the pale course), merlons at y 8 on the corners and side
//             middles, each with a stone-brick wall finial (y 9; Level I only): the crenels (two a side) and the deck
//             inside them are the battle stations, all at y 7 (stood on at y 8): 16.
//   Level II (the Gabled Tower) adds, keeping all of Level I but the eight finials:
//   Piers     the corner merlons carried up whole in stone (y 9); the side-middle merlons' heads (y 9) are lanterns, hung
//             from the gables: each lights its side's two loopholes (critic's note 5).
//   Crown     four little dark-oak gables, one over each side (y 10: corner eave stairs and spruce gable bases; y 11: the
//             rakes up to a slab peak over the side's middle), round a flat of slabs where the ridges meet; a boarded
//             spruce ceiling (y 10) over the deck: every station roofed 3 or more over the block stood on, the two
//             loopholes of each side right under its gable.

export { KIT } from '../stone_wall/plan.mjs'

// The tower's own picks beside the kit's palette (the kit's file stays as the shipped Stone Wall drew it)
export const TOWER = {
  machicolation: 'polished_andesite_stairs', // pale, so the band of notches reads against the stone bricks
  batter: 'cobblestone_stairs',
  jamb: 'polished_andesite',
  gable: 'spruce_planks', crownStairs: 'dark_oak_stairs', crownSlab: 'dark_oak_slab',
  table: { leg: 'spruce_fence', top: 'brown_carpet' }, bench: 'spruce_stairs', store: 'chest', patch: 'spruce_planks',
}

export const SIZE = { 1: [5, 10, 5], 2: [5, 12, 5] } // w, h, d: fixed by the brief

export const MASSES = {
  shaft: { x0: 0, z0: 0, x1: 4, z1: 4, y0: 0, plinthTop: 1, top: 6, courseY: 3 },
  groundFloor: { y: 0 }, // full stone: the door stands on the plinth at y 1 (a villager's 1.95 under its lintel)
  walkFloor: { y: 3 }, // spruce slabs: stood on at y 3.5, half a step under the wall walk (y 4)
  door: [2, 1, 0], // lower half; facing south (into the tower)
  arches: [[0, 1], [4, 1]], // [x, z], open y 4..5, sill y 3, jambs at z 0 and z 2
  ladder: { x: 2, z: 3, y0: 1, y1: 7, facing: 'north' },
  loops: [ // [x, y, z, inward]: slit two tall from y, sill at y - 1, head at y + 2 (a machicolation)
    [1, 4, 4, 'north'], [3, 4, 4, 'north'], [2, 4, 0, 'south'], // the walk room: two out, one over the door (its sides
    // have the arches: a loop beside each would leave the shaft looking like a sieve, so the guardroom has none)
  ],
  corbels: { y: 6 }, // the machicolations
  deck: { y: 7 },
  merlons: { y: 8, at: (x, z) => x % 2 === 0 && z % 2 === 0 }, // corners and side middles; crenels at 1 and 3
  hat: { y: 10 }, ceiling: { y: 10 }, // Level II
}

// Rooms, from the ground up (no furniture is read; nobody lives here)
export const ROOMS = [
  { name: 'Guardroom', use: 'the guards come in from the village, sit at the table; ladder up', box: [1, 1, 1, 3, 2, 3] },
  { name: 'Walk room', use: 'the wall walk passes through; the arrow store under the loops', box: [1, 4, 1, 3, 6, 3] },
  { name: 'Fighting top', use: 'battle stations: the deck and its eight crenels', box: [0, 8, 0, 4, 9, 4] },
]

export const OPENINGS = {
  door: { at: [2, 1, 0], face: 'z 0, the village side; the only gate block' },
  arches: 'the wall walk: open (no door, no gate) at x 0 and x 4, z 1, y 4..5, framed in pale andesite',
  loops: 'two tall, one wide, plunging sills and pale heads (no station in a loop)',
}

// Every piece of furniture, footprint first. Drawn as fixtures (the brief needs none; no furniture file is shipped);
// the table and its chairs are found by rebuild.py's furniture.detect. No bed, no job block, no POI.
export const FIXTURES = [
  { room: 'Guardroom', kind: 'chair', pos: [1, 1, 1], facing: 'north' }, // its back to the front wall, facing the table
  { room: 'Guardroom', kind: 'table', pos: [1, 1, 2] }, // a spruce fence leg under a carpet
  { room: 'Guardroom', kind: 'chair', pos: [1, 1, 3], facing: 'south' },
  { room: 'Walk room', kind: 'storage', pos: [1, 4, 3], facing: 'north', on: [1, 3, 3] }, // the arrow store, under the west loop
  { room: 'Walk room', kind: 'chair', pos: [3, 4, 3], facing: 'south', on: [3, 3, 3] }, // a bench under the east loop
]
export const FURNITURE = { 1: [], 2: [] }

// Lights hung from blocks no upgrade replaces (under the walk room's floor, under the deck) and, on Level II, from the
// four gables' bases over each side's middle merlon: between and lighting each side's two gallery stations.
export const LIGHTS = { 1: [[3, 2, 1], [1, 6, 2]], 2: [[3, 2, 1], [1, 6, 2], [2, 9, 0], [0, 9, 2], [4, 9, 2], [2, 9, 4]] }

// The walking line: the village ground (2, 0, -1) -> up onto the Door's sill (2, 1, 0) -> the guardroom (2, 1, 2) -> the
// Ladder (2, 1..7, 3) -> the walk room (stood on at y 3.5; out by an arch onto the wall walk at y 4) -> on up the Ladder to the
// deck (stood on at y 8) -> any crenel.
export const WALK = [[2, 0, -1], [2, 1, 0], [2, 1, 2], [2, 1, 3], [2, 4, 2], [1, 4, 1], [0, 4, 1], [2, 8, 3], [1, 8, 1], [1, 8, 0]]
