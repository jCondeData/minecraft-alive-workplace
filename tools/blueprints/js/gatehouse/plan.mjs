// The Gatehouse (`gatehouse`, Level II `gatehouse_2`), the plan: drawn before any block. ROADMAP 49 (the rebuild
// method, tools/blueprints/REBUILD.md); the brief is tools/blueprints/briefs/gatehouse.md. The gate piece of the stone
// wall kit: it takes the kit's palette from ../stone_wall (KIT) and its walk line (the pale course at y 3), so a
// wall run, its towers and its gate read as one rampart. recipe.mjs assembles both levels from ../parts.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Twin Drums. Two stout stone towers clasp the road, their outer shoulders cut back like half-drums, each
//      under a steep hipped dark-oak cap; between them, over the portcullis, a gabled fighting loggia: two open lights
//      either side of a stone pier, the archers' walk behind them; a standard flies from the loggia's ridge. Level II
//      throws a barbican forward: a walled, decked passage with a second gate, a timber deck with murder holes, a
//      crenellated front and corner turrets. Story: where the town's wall meets the high road it bunches into two
//      towers and holds the road between them like a fist.
//   B. The Tower Gate. One tall keep astride the road (all 11 wide), the road through a vaulted tunnel at its foot,
//      a pyramid roof. Story: the old keep the town grew round. In a 5-deep plot the keep is a slab, the archers need
//      the front open from y 4 to 7 over the gate (a keep with a hole in its face), and every floor or roof flat at
//      another height would be a battle station the brief forbids (all of them at y 6).
//   C. The Toll Hall. A long two-storey hall across the road, the road through its ground floor, a steep gable roof
//      along the wall, the archers in a dormer. Story: the toll keeper's house that grew a portcullis. Friendly, but it
//      reads as a house sat on the road, not as the strongest point of a stone wall, and its long eave buries the view
//      the over-gate stations need.
// Strongest: A. It is the gatehouse a player recognises in outline from 20 blocks (two towers and the gate between
// them), its massing answers the brief's hardest rule for free: the archers' stations sit in the low gap between the
// towers, so nothing stands in front of them but a sill, while the middle one looks straight into the loggia's pier
// (it must see less). The towers' cut shoulders, the corbelled caps and the stepped loggia give every face depth in a
// plot only 5 deep, and Level II grows forward from it without taking anything down.
//
// Coordinates: x across the road (0..10), y up (0 = the layer on the clicked block), z from the village face (z = 0,
// the wall line, north) out to the field (z = 4, Level II z = 9). The road runs along z through x 4..6, the middle
// way x = 5. Level I exactly 11 x 16 x 5, Level II exactly 11 x 16 x 10 (the brief's plots; the height is 16, not the
// declared 9: the ship step changes StarterBlueprints' Vec3i).
//
// Masses (named parts the owner's feedback can point at):
//   Towers    West x 0..3 and East x 7..10, the full depth z 0..4: a cobble plinth (y 0..1), ashlar to y 11, the kit's
//             pale String course at y 3 (the wall walk's line) round both, dressed Quoins up the corners and the
//             gateway's jambs. Each outer front corner (x 0 / 10, z 4) is cut back from the ground to y 10 (the
//             Shoulder) and carried out square again at y 11 on a corbel, so the towers read as half-drums from the
//             field. A pale Corbel table (upside-down stairs) round each tower at y 11, the parapet's line.
//   Tops      a fighting top on each tower with no deck (crenelTop): a parapet ring at y 12, merlons (y 12..13, a pale
//             coping slab) on the corners and the long sides' middles, pale slab sills in the crenels, a low dark-oak
//             roof inside the ring hidden behind the merlons. Every top unsturdy: no battle station up there (the
//             brief wants them all at y 6). The towers rise 40% over the loggia's ridge: two towers, a gate between.
//   Gateway   the road through, x 4..6, floor y 0 (stone bricks, a pale sill under the portcullis at z 3), open y 1..3,
//             vaulted at y 4 (stone bricks). Village Arch at z 0, four high (pale haunch stairs at y 4, a keystone at y 5). The three
//             Gates at (4..6, 1, 2), spruce fence gates, shut. The Portcullis at (4..6, 3, 3), iron bars, drawn up into
//             the pale Lintel (y 4) with (4..6, 1..2, 3) empty over the pale sill. The Ram's stand (4..6, 1..8, 4)
//             open in front of it, the towers' inner faces its sides.
//   Loggia    the archers' timber gallery slung between the towers over the gate, x 4..6, z 0..3: a pale paved walk
//             at y 5 (stood on at y 6: the battle stations), open on both faces. Field front (z 3): spruce sill slabs at
//             (4 / 6, 5, 3) (an unsturdy top), the Pier (5, 5..8, 3), a spruce post, between the two Lights, which open
//             up to the eave; the town's red banner hangs on the pier over the portcullis. Village back (z 0): open
//             over a spruce rail, under a dark-oak tie beam (y 8). Its Roof: a little dark-oak gable (ridge along z,
//             y 9..10) between the towers, z 0..3; the Standard on its ridge (a spruce pole, a red banner facing the
//             field) is the highest point of the build.
//   Guardrooms  each tower's ground room (x 1..2 / 8..9, z 1..3, y 1..4): the doorway off the gateway inside the gates
//             (x 3 / 7, z 1), its plank leaves (open trapdoors) folded against the jamb; a ladder up the field wall to a
//             hatch in the upper floor (y 5).
//   Watch rooms each tower's upper room (y 6..11, under the top's roof), a spruce floor at y 5, a doorway onto the
//             loggia (x 3 / 7, z 2) beside the stations, a window to the village (glass set back one block in its
//             reveal), glazed arrow slits to the field and to the flank, two stacked (y 6..7, 9..10), plunging sills.
//   Level II (the Barbican) adds, keeping every block of Level I:
//   Barbican  walls x 1..3 and 7..9, z 5..9, y 0..3 (plinth, ashlar, the pale course at y 3), each with a mural Gallery
//             (x 2 / 8, y 1..2, z 5..8, a torch at its end, a glazed slit to the flank) entered from the guardroom
//             through the tower's front wall: the guards' way in under the deck (B1427). A Deck at y 4 over x 1..9,
//             z 4..9 (spruce boards over the passage between the gate lines with two Murder holes at (4 / 6, 4, 5),
//             stone elsewhere). The outer Gates at (4..6, 1, 8), the outer Portcullis at (4..6, 3, 7) over a pale sill,
//             the Outer stand (4..6, 1..3, 9) under the deck's pale lintel. Its Parapet at y 5 round the outer edges
//             (x 1, x 9, z 9), merlons at y 6 with wall-post finials, two 2 x 2 corner Turrets (x 1..2 / 8..9,
//             z 8..9) up to y 7 under a pale coping, a lantern on a post at each outer corner. The deck's inner cells
//             (x 2..8) are the barbican's battle stations (y 5), each within climbing reach of a gallery.

export { KIT } from '../stone_wall/plan.mjs'
import { KIT } from '../stone_wall/plan.mjs'

export const SIZE = { 1: [11, 16, 5], 2: [11, 16, 10] } // w, h, d: the brief's plots

// The palette (60/30/10), the kit's: stone (cobble plinth, stone-brick ashlar, the merlons and the pier) / timber
// (dark-oak caps and the loggia roof, spruce floors, leaves, the deck, the standard's pole) / accents (the pale
// polished andesite of the walk, the course, the quoins, the sills and lintels; lantern light; the red standard).
// Why: the gate is the wall's strongest point, so it is the wall's own stone, heavier at the foot; the dark caps
// echo the Wall Tower's crown and the Stone Wall's hoarding roof so the kit reads as one; the pale andesite draws
// every line a defender uses (the walk, the sills he shoots over, the portcullis' lintel) and the course at y 3 runs
// on from the wall walk round both towers.
export const PALETTE = {
  plinth: KIT.plinth,
  ashlar: KIT.ashlar,
  course: KIT.course, courseSlab: KIT.courseSlab, courseStairs: KIT.courseStairs,
  quoin: KIT.quoin, quoinAlt: KIT.quoinAlt,
  merlon: KIT.merlon, cap: KIT.cap,
  stoneStairs: KIT.steps, // stone_brick_stairs: corbels, haunches
  key: 'chiseled_stone_bricks',
  road: KIT.groundFloor, // stone_bricks
  roof: { stairs: KIT.wood.roof, slab: KIT.wood.roofCap, full: 'dark_oak_planks' },
  wood: 'spruce', // leaves, hatches, ladders' trapdoors, furniture
  floor: 'spruce_planks',
  deck: 'spruce_planks',
  pole: 'spruce_fence',
  post: 'stripped_spruce_log', // the loggia's pier
  beam: 'dark_oak_log', // the loggia's tie beam over the village side
  glass: 'glass_pane',
  gate: 'spruce_fence_gate', // pinned by the brief
  bars: 'iron_bars', // pinned by the brief
  standard: 'red',
}

const W = { x0: 0, x1: 3, inner: 3, outer: 0, dx: 1 } // West tower: inner (passage-side) wall x 3, outer x 0
const E = { x0: 7, x1: 10, inner: 7, outer: 10, dx: -1 } // East tower, mirrored
export const MASSES = {
  towers: [W, E],
  tower: { z0: 0, z1: 4, wallTop: 11, courseY: 3, plinthTop: 1, capY: 12, shoulder: { z: 4, y0: 1, y1: 10 } },
  gateway: { x0: 4, x1: 5 + 1, z0: 0, z1: 4, vaultY: 4, archZ: 0 },
  loggia: { x0: 4, x1: 6, z0: 0, z1: 3, walkY: 5, sillY: 5, headY: 8, pier: 5, lights: [4, 6], roofY: 9, roofZ: [0, 3] },
  standard: { x: 5, z: 2, y: 11, height: 5, field: [5, 7, 4] }, // pole y 11..15; a second banner on the loggia's pier, the banner at the top facing the field
  barbican: { // Level II
    walls: [{ x0: 1, x1: 3 }, { x0: 7, x1: 9 }], z0: 5, z1: 9, deckY: 4, deck: { x0: 1, x1: 9, z0: 4, z1: 9 },
    // B1427: a mural gallery in each wall (x 2 / 8, y 1..2, z 5..8), entered from the guardroom through the tower's
    // front wall (x 2 / 8, z 4): ground on the village side under the deck, so a guard sent to a barbican station
    // walks in under it and climbs (no deck cell is more than 3.5 blocks flat from a gallery cell)
    galleries: [{ x: 2, door: [2, 1, 4], slit: [1, 2, 6], torch: [2, 2, 8] }, { x: 8, door: [8, 1, 4], slit: [9, 2, 6], torch: [8, 2, 8] }], galleryZ: [5, 8],
    timber: { x0: 4, x1: 6, z0: 4, z1: 6 }, holes: [[4, 5], [6, 5]],
    parapetY: 5, merlonY: 6, turretTop: 7,
    turrets: [{ cells: [[1, 8], [2, 8], [1, 9], [2, 9]], corner: [1, 9] }, { cells: [[8, 8], [9, 8], [8, 9], [9, 9]], corner: [9, 9] }],
  },
}

// The pinned cells (the brief: keep them exactly)
export const GATES = {
  1: { gates: [[4, 1, 2], [5, 1, 2], [6, 1, 2]], bars: [[4, 3, 3], [5, 3, 3], [6, 3, 3]] },
  2: { gates: [[4, 1, 8], [5, 1, 8], [6, 1, 8]], bars: [[4, 3, 7], [5, 3, 7], [6, 3, 7]] },
}

// Rooms (nobody lives or works here: guards come and go). No bed, no point of interest, no job block.
export const ROOMS = [
  { name: 'Gateway', use: 'the road through: gates, portcullis, the knights\' stand inside', box: [4, 1, 0, 6, 3, 4] },
  { name: 'Loggia', use: 'the archers\' walk over the gate: the battle stations (4, 6, 2) and (6, 6, 2)', box: [4, 6, 0, 6, 8, 3] },
  { name: 'West guardroom', use: 'the porter\'s room off the gateway: a table, a stool, the ladder up', box: [1, 1, 1, 2, 4, 3] },
  { name: 'East guardroom', use: 'the same, mirrored', box: [8, 1, 1, 9, 4, 3] },
  { name: 'West watch room', use: 'the arrow store over the guardroom, onto the loggia', box: [1, 6, 1, 2, 10, 3] },
  { name: 'East watch room', use: 'the same, mirrored', box: [8, 6, 1, 9, 10, 3] },
]

export const OPENINGS = {
  // the guardroom doorways off the gateway, inside the gates, their leaves folded against the village-side jamb
  guardDoors: [{ at: [3, 1, 1], hinge: 'north' }, { at: [7, 1, 1], hinge: 'north' }],
  // the watch rooms' doorways onto the loggia (beside the stations, never in front of them)
  loggiaDoors: [{ at: [3, 6, 2], hinge: 'north' }, { at: [7, 6, 2], hinge: 'north' }],
  // ladders up the field wall to a hatch in the upper floor
  ladders: [{ x: 1, z: 3, y0: 1, top: 5, facing: 'north' }, { x: 9, z: 3, y0: 1, top: 5, facing: 'north' }],
  windows: [
    // to the village: two lights set back in a one-block reveal (the glass at z 1)
    { name: 'west watch village', glass: [[1, 6, 1], [2, 6, 1], [1, 7, 1], [2, 7, 1]], reveal: 0 },
    { name: 'east watch village', glass: [[8, 6, 1], [9, 6, 1], [8, 7, 1], [9, 7, 1]], reveal: 0 },
    // the guardrooms' small window to the village, flush
    { name: 'west guard village', glass: [[1, 2, 0]] },
    { name: 'east guard village', glass: [[9, 2, 0]] },
  ],
  // glazed arrow slits (two high, a plunging sill under each): to the field and to each flank
  slits: [
    { x: 2, ys: [6, 9], z: 4, inward: 'north' }, { x: 8, ys: [6, 9], z: 4, inward: 'north' },
    { x: 0, ys: [6, 9], z: 2, inward: 'east' }, { x: 10, ys: [6, 9], z: 2, inward: 'west' },
  ],
}

// Every piece of furniture, footprint first (furniture spots; the guards' rooms only).
export const FURNITURE = [
  { room: 'West guardroom', kind: 'table', part: 'table', pos: [2, 1, 3], facing: 'north', wood: 'spruce', colour: 'green' },
  { room: 'West guardroom', kind: 'chair', part: 'chair', pos: [2, 1, 2], facing: 'south', wood: 'spruce' },
  { room: 'West guardroom', kind: 'lamp', part: 'hangingLamp', pos: [1, 4, 1], facing: 'south', wood: 'spruce' },
  { room: 'East guardroom', kind: 'table', part: 'table', pos: [8, 1, 3], facing: 'north', wood: 'spruce', colour: 'green' },
  { room: 'East guardroom', kind: 'chair', part: 'chair', pos: [8, 1, 2], facing: 'south', wood: 'spruce' },
  { room: 'East guardroom', kind: 'lamp', part: 'hangingLamp', pos: [9, 4, 1], facing: 'south', wood: 'spruce' },
  { room: 'West watch room', kind: 'fixture', part: 'chest', pos: [2, 6, 3], facing: 'north', block: 'chest' },
  { room: 'West watch room', kind: 'lamp', part: 'hangingLamp', pos: [1, 10, 2], facing: 'south', chain: 1, wood: 'spruce' },
  { room: 'East watch room', kind: 'fixture', part: 'chest', pos: [8, 6, 3], facing: 'north', block: 'chest' },
  { room: 'East watch room', kind: 'lamp', part: 'hangingLamp', pos: [9, 10, 2], facing: 'south', chain: 1, wood: 'spruce' },
  { room: 'Gateway', kind: 'lamp', part: 'hangingLamp', pos: [4, 3, 1], facing: 'south', wood: 'spruce' },
  { room: 'Gateway', kind: 'lamp', part: 'hangingLamp', pos: [6, 3, 1], facing: 'south', wood: 'spruce' },
  { room: 'Loggia', kind: 'lamp', part: 'hangingLamp', pos: [5, 8, 1], facing: 'south', wood: 'spruce' },
]

// Level II (B1427): the guardrooms are the way into the barbican's galleries, so their table and chair stand along the
// outer wall (x 1 / 9), the doorways off the gateway have no leaves, and the way (x 2 / 8, z 1..3) is clear.
export const FURNITURE_2 = FURNITURE.map(f => {
  if (f.room === 'West guardroom' && f.kind === 'table') return { ...f, pos: [1, 1, 2] }
  if (f.room === 'West guardroom' && f.kind === 'chair') return { ...f, pos: [1, 1, 1] }
  if (f.room === 'East guardroom' && f.kind === 'table') return { ...f, pos: [9, 1, 2] }
  if (f.room === 'East guardroom' && f.kind === 'chair') return { ...f, pos: [9, 1, 1] }
  return f
})

// Lights outside the rooms: Level II's on its two turrets (Level I's field face stays dark: the raiders' side).
export const LIGHTS = { 1: [], 2: [[1, 9, 9, 'standing'], [9, 9, 9, 'standing']] }

// The walking line: a guard from the village ground through the arch into the gateway; left into the guardroom and
// up the ladder to the watch room; out onto the loggia (the stations); Level II: over the light's sill down onto the
// barbican's deck.
export const WALK = [
  [5, 1, 0], [5, 1, 1], [4, 1, 1], [3, 1, 1], [2, 1, 1], [1, 1, 1], [1, 1, 2], [1, 1, 3], [1, 5, 3], [1, 6, 3],
  [2, 6, 2], [3, 6, 2], [4, 6, 2], [4, 6, 3], [4, 5, 4], [4, 5, 6], [4, 5, 8],
]
