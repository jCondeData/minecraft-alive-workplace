// The Prisoner Cage (`legend/prisoner_cage`, one level only), the plan: drawn before any block. ROADMAP 49 (lane B);
// the brief is tools/blueprints/briefs/prisoner_cage.md. recipe.mjs assembles it from the parts library (../parts,
// wagons.mjs grown for it).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Cage Wagon. A narrow prison wagon along the plot's west side: a barred box on a two-beam bed, four disc
//      wheels on two axles sunk to the hub in mud, a 45-degree birch roof with a dark ridge crest over the cage and the
//      driver's bench, the shafts dropped on the ground with no horse between them; beside it the guards' camp: a
//      white canvas tent, a cook fire with a spit, a woodpile, a log seat and the raiders' black standard.
//      Story: the pillagers were carting their prize to the mansion; the wagon bogged down at the foot of the
//      tower, the horse was taken on, and the guards made camp to wait.
//   B. The Gibbet. A tall dark-oak gallows (8-9 high) with a bar cage hanging from its arm over a stone pit. The best
//      silhouette, but the prisoner would hang 3-4 blocks up: out of reach of a sword at the edge of a player's
//      reach, a fall when freed, and a prisoner with no AI has nothing to stand on but the cage's own floor.
//   C. The Pen. A 5 x 5 square of dark-oak posts and iron bars under a pyramid roof, a stone floor, a fence yard
//      round it. Safe and clear, but it is the generic cage: a box with bars, the same from all four sides and with
//      nothing that says who built it or why it stands here.
// Strongest: A. It is the only one a player knows in outline from across the outpost (wheels, a pitched roof on a
// barred box, shafts on the ground), it tells the whole story without a sign (taken, carted, stuck, guarded), and it
// borrows C's virtue (the prisoner stands at ground height, the bars in easy reach) without C's blandness. It is
// asymmetric on purpose: the tall wagon on the west, the low camp on the east, the standard the one vertical.
//
// Coordinates: x across (west 0 -> east 8), y up (0 = the ground layer itself: the template replaces the top ground
// block), z north 0 -> south 8. No front: the camp lands turned a random quarter, so every side is worked.
//
// THE PRISONER'S SPOT MOVES: (2, 3, 3), on the wagon's deck (2, 2, 3), not the old (2, 1, 2). The ship step changes
// `LegendSites.site("outpost")`'s BlockPos to new BlockPos(2, 3, 3) and nothing else (the brief allows it).
// (2, 3, 3) and (2, 4, 3) are air inside the cage; (2, 2, 3) is the deck's planks. Freed, the prisoner steps out of
// the broken bars and drops 2 blocks to the ground (no fall damage under 4).
//
// Bars (plain minecraft:iron_bars, the only bars in the camp): the cage's two long walls x 1 and x 3 at z 2..4 and its
// two ends z 1 and z 5 at x 2, each at y 3 and 4. Measured from the prisoner's feet (2.5, 3, 3.5) to each bar's
// centre: side bars 1.1 to 1.5 at feet height and 1.8 to 2.1 at head height, end bars 2.1 (feet) and 2.5 (head). Every one < 3;
// no other bar anywhere in the box (x -1..5, z 0..6, y 2..6 round the prisoner). Every bar run ends against a log
// post (B648).
//
// Masses (named parts the owner's feedback can point at):
//   Wagon     the main mass, x 0..4, z 0..8, 8 high (y 0..7):
//     Bed       y 2 (wheel-top height), x 1..3, z 0..6: dark-oak log beams down x 1 and x 3, planks between, open
//               underneath but for the two axles (y 1, z 1 and z 5) and a tail board (top slabs) hung under its end.
//     Wheels    four 3 x 3 disc wheels at x 0 and x 4 on the axles (hubs at y 1, z 1 and z 5), their bottom row
//               sunk in the ground layer, mud in the gap between them: the reason the wagon stopped here. They
//               stand under the cage's line, so every bar shows above them; pale stripped-spruce hubs.
//     Cage      x 1..3, z 1..5, y 3..4: log posts at the corners, bars between (above), a log-ringed plank lid at y 5,
//               a chain hanging inside, straw (yellow carpet) on the deck either side of the prisoner.
//     Roof      a 45-degree birch roof over x 0..4, z 0..6 (eaves y 5, ridge y 7), a dark-oak slab
//               ridge crest and barge boards; its overhang at z 6 shelters the driver's bench.
//     Bench     the driver's seat at z 6, y 3 (stairs with their backs to the cage), a lantern under each end of the
//               roof (the bench end and the tail).
//     Shafts    slabs from under the bed at z 7 down to the ground at z 8 at x 1 and x 3, joined by a swingletree; a hay
//               bale by them, the horse's feed, with no horse.
//   Tent      the guards' tent, x 5..8, z 0..4, ridge along x: white canvas stepped to a one-block peak (y 3), a
//             carpet hem on each step, fence pole tips through the canvas at both ends, its back (x 8) closed, its mouth (x 5) open to the cage across the gap
//             between the wheels: the guards sleep watching their prize. Two barrels of loot at its back.
//   Fire      the cook fire at (7, 1, 6): a campfire under a dark-oak spit (posts at x 6 and 8), a woodpile at x 5,
//             a log seat at z 8 facing it, on trodden coarse dirt.
//   Standard  the hero vertical at (8, 1, 8): a dark-oak stump and a fence pole to y 8, the raiders' black banner on
//             top (y 9, reads to y 10), a lantern hung from an arm: 25 % over the wagon's ridge, the camp's landmark.
export const SIZE = [9, 11, 9] // w, h, d (brief: at most 9 x 18 x 9)

// The prisoner's cell (the ship step's new BlockPos).
export const PRISONER = [2, 3, 3]

// Palette (60/30/10): dark oak (the wagon's frame, wheels, bed, lid, the spit, the standard: the raiders' timber, the
// same as their tower) / pale birch roof and white canvas (the wagon's roof and the tent, the outpost's other two
// materials) / accents: the iron bars, the fire, lanterns, straw and the black banner. Ground: a separate mix, grass
// at the rim so the camp sits into whatever it lands on, trodden coarse dirt in the camp, mud round the wheels.
// Why: the camp must read as the outpost's own (pillagers build in dark oak, birch and white wool), the pale roof and
// canvas lift the dark wagon off the ground, and the cold gray of the bars is the one thing the eye goes to.
export const PALETTE = {
  frame: { log: 'dark_oak_log', planks: 'dark_oak_planks', stairs: 'dark_oak_stairs', slab: 'dark_oak_slab', fence: 'dark_oak_fence' },
  hubLog: 'stripped_spruce_log', // the hubs and axles: pale worn ends that make the wheels read round
  roof: { full: 'birch_planks', stairs: 'birch_stairs', slab: 'birch_slab' },
  trim: 'dark_oak_slab',
  canvas: 'white_wool',
  canvasEdge: 'white_carpet',
  bars: 'iron_bars',
  banner: 'black_banner',
  lantern: 'lantern',
  straw: 'yellow_carpet',
  hay: 'hay_block',
  fuel: 'dark_oak_log',
  seat: 'stripped_dark_oak_log',
  ground: {
    rim: [['grass_block', 6], ['coarse_dirt', 1]],
    camp: [['coarse_dirt', 4], ['dirt', 2], ['grass_block', 1], ['gravel', 1]],
    covered: [['dirt', 3], ['coarse_dirt', 2]], // under the wagon and the tent: no grass under a block
    mud: 'mud',
    trod: 'coarse_dirt', // trodden earth (no dirt path: none may lie under a block, and none is needed)
  },
}

export const MASSES = {
  wagon: {
    bed: { x0: 1, x1: 3, z0: 0, z1: 6, y: 2 },
    axles: { x0: 1, x1: 3, y: 1, zs: [1, 5] },
    tail: { x0: 1, x1: 3, y: 1, z: 0 }, // a tail board of top slabs hung under the deck's end: the step up
    wheels: [[0, 1, 1], [0, 1, 5], [4, 1, 1], [4, 1, 5]], // hubs; axles along x
    cage: { x0: 1, x1: 3, z0: 1, z1: 5, y0: 3, y1: 4 },
    lid: { x0: 1, x1: 3, z0: 1, z1: 5, y: 5 },
    roof: { axis: 'z', across: [0, 4], along: [0, 6], eaveY: 5, steep: false },
    bench: { z: 6, y: 3, x0: 1, x1: 3 },
    shafts: { xs: [1, 3], y: 1, z0: 6, z1: 8 }, // from under the bench's end down to the ground
    lanterns: [[0, 4, 1], [4, 4, 5]], // hung under the eave stairs, inside the barge boards
    chain: [2, 4, 4],
    straw: [[2, 3, 2], [2, 3, 4]],
  },
  tent: { axis: 'x', across: [0, 4], along: [5, 8], y0: 1, back: 'l1', barrels: [[7, 1, 1], [7, 1, 3]], poles: [[5, 4, 2], [8, 4, 2]] },
  fire: { campfire: [7, 1, 6], spit: { xa: 6, xb: 8, z: 6, y0: 1 }, woodpile: [[5, 1, 6], [5, 1, 7], [5, 2, 7]], seat: [[6, 1, 8], [7, 1, 8]] },
  standard: { at: [8, 1, 8], logHeight: 1, fenceHeight: 7, arm: [0, -1], rotation: 12 },
  hay: [0, 1, 8],
  ground: { mud: [[0, 0, 3], [4, 0, 3], [0, 0, 7]], trod: [[5, 0, 5], [6, 0, 5], [7, 0, 5], [4, 0, 7], [4, 0, 8], [5, 0, 8]] },
}

// Rooms: none a villager lives in. The cage is the prisoner's cell; the tent is the guards' (one low layer).
export const ROOMS = [
  { name: 'Cage', use: 'the prisoner stands here until a bar is broken', box: [2, 3, 2, 2, 4, 4] },
  { name: 'Tent', use: 'the guards sleep here (crawl height)', box: [5, 1, 1, 7, 2, 3] },
]

// Openings: none with a door. The cage's tail end (z 1) is the cage door, barred: breaking its bars lets the prisoner
// step out onto the tail step and down. The tent's mouth opens west to the cage.
export const OPENINGS = { cageDoor: { bars: [[2, 3, 1], [2, 4, 1]], step: [[1, 1, 0], [2, 1, 0], [3, 1, 0]] }, tentMouth: { x: 5, z: [1, 3] } }

// Furniture: none is a furniture spot (no job, no resident's room, no test reads any; the brief: recording spots would
// bring every FurnishedRooms rule). The props, footprint first:
export const PROPS = [
  { name: 'driver bench', cells: [[1, 3, 6], [2, 3, 6], [3, 3, 6]], block: 'dark_oak_stairs', facing: 'north' },
  { name: 'straw', cells: [[2, 3, 2], [2, 3, 4]], block: 'yellow_carpet' },
  { name: 'chain', cells: [[2, 4, 4]], block: 'chain' },
  { name: 'loot barrels', cells: [[7, 1, 1], [7, 1, 3]], block: 'barrel' }, // no bedroll: a carpet under the peak leaves 1.94
  { name: 'campfire and spit', cells: [[6, 1, 6], [7, 1, 6], [8, 1, 6], [6, 2, 6], [7, 2, 6], [8, 2, 6]] },
  { name: 'woodpile', cells: [[5, 1, 6], [5, 1, 7], [5, 2, 7]] },
  { name: 'log seat', cells: [[6, 1, 8], [7, 1, 8]] },
  { name: 'standard', cells: [[8, 1, 8], [8, 9, 8], [8, 8, 7], [8, 7, 7]] },
  { name: 'hay bale', cells: [[0, 1, 8]] },
]
export const FURNITURE = []

// The walking line: a player comes in from any side (it lands turned at random); round the wagon on the ground
// (x 0 and x 4 past the wheels, z 0 behind the tail, z 7 between the shafts), every bar within reach from outside;
// the tent's mouth (5, 1, 3) faces the gap between the wheels (4, 1, 3);
// the camp's path from the fire's north side (5..7, 1, 5) past the woodpile to the bench (2, 3, 6) over the shafts and
// round to the cage's tail. A freed prisoner steps out of the broken tail bars onto the step (2, 1, 0) and away.
export const WALK = [[2, 1, -1], [2, 1, 0], [4, 1, 3], [5, 1, 3], [5, 1, 5], [7, 1, 5], [4, 1, 7], [2, 1, 7], [0, 1, 7], [0, 1, 3], [0, 1, -1]]
