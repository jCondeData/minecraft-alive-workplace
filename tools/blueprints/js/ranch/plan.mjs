// Ranch (ranch, ranch_2; ROADMAP 49), the plan: drawn before any wall. Story, palette, masses, rooms, openings, every
// furniture footprint and the walking line come first; recipe.mjs then assembles both levels from the parts library
// (../parts) to fit this plan. Brief: tools/blueprints/briefs/ranch.md.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. Shedrow and yard. One long low stable range across the back under a single catslide that sweeps down over a
//      covered walk; stalls open onto the walk, the tack room in the middle; the open front is the breaking yard.
//      Story: a horse-breaker's yard where everything faces the ground he works on. Weak in a 13 x 13 plot: the range
//      eats the yard, the catslide eave is too low to walk under, and from the side it is a wedge.
//   B. Round barn. An octagonal barn under a conical roof and a lantern, stalls radiating round a central tack core.
//      Story: a breeder's show barn. A striking outline, but the herd lives outside, so the round plan shelters
//      nothing the Rancher uses, and Level II can only grow from it as a stranger beside it.
//   C. Monitor barn behind a ranch gate. A tall gabled centre (the "monitor") over a through aisle, two low lean-to
//      stall sheds against its sides, a row of clerestory windows lighting the hay loft between the two roofs, a hay
//      hood with a hoist beam over the loft door, a cupola on the ridge; in front, the ranch gate (two posts and a
//      crossbar with the ranch's banner) over the way into a small fenced yard with a trough. Level II adds a feed
//      and bunk wing on the east and a big paddock behind with a man-gap a horse can't pass.
//      Story: the horse barn of a western ranch, the stepped outline a rider recognises from the road.
//   Strongest: C. Its outline is the one you'd draw for "horse barn" from 20 blocks on every side (tall centre, two
//   lower sheds, a cupola), every part does a ranch's job (stalls, aisle, tack wall, hay loft, hoist, yard, trough),
//   the Rancher's smoker and chest close the aisle's end where he walks straight to them from the gate, and Level II
//   grows out of it naturally: a lower wing beside, the herd's paddock behind. Built: C.
//
// Coordinates: x across the front (west 0 -> east), y up (0 = the layer on the clicked block: the plinth, floors and
// anything standing on the ground), z from the front (z = 0, north) to the back. Level I is 13 x 16 x 13; Level II
// keeps every cell of Level I and adds the wing (x 13..20) and the paddock (z 13..20): 21 x 16 x 21.
//
// Masses (named parts the owner's feedback can point at):
//   Monitor     the tall centre: walls x 4 and 8 (posts at z 3, 7, 11), z 3..11; the aisle (x 5..7) on the ground,
//               the hay loft over it (floor y 4); clerestory windows at y 7..8 above the sheds; a 45-degree
//               deepslate roof, ridge along z at y 12, a block over every side.
//   Sheds       two lean-tos against the monitor's sides, outer walls x 1 and x 11 (3 high), their roofs falling from
//               y 6 at the monitor to the eave at y 3; two stalls in each, iron-barred stall windows.
//   Hay hood    the ridge carried out a block further over the loft door, a hoist beam under it with a lantern.
//   Cupola      a louvred vent on the ridge (x 5..7, z 6..8): dark corner posts, louvres, a little pyramid roof.
//   Chimney     the smoker's flue: a stone stack up the back gable (x 6, z 12) with smoke on top.
//   Ranch gate  two dark posts and a crossbar at the front line (z 0), the ranch's banner on top, lanterns under it.
//   Yard        the fenced front yard (z 0..2) with the horse trough; the ranch gate is its way in (no fence gate).
//   Wing (II)   the feed and bunk house east of the barn: walls x 13..19, z 5..11, a lower gable (ridge along x, y 8).
//   Paddock(II) fenced ground behind (x 0..20, z 13..20): a man-gap on the west a villager walks through and a horse
//               can't, a double gate on the east to drive the herd, a trough, a snubbing post, a run-in shelter.

export const SIZE = { 1: [13, 16, 13], 2: [21, 16, 21] }

// Palette (60/30/10): spruce board walls / dark oak frame and a gray deepslate-tile roof (the tin roof of a working
// barn) / white birch trim on the big doors' leaves, shutters and the cupola, hay gold, fieldstone at the foot. Why: warm brown boards
// under a cool gray roof read as a barn rather than a house; the white trim is the barn's own, the one bright note.
export const PALETTE = {
  stone: [['cobblestone', 4], ['mossy_cobblestone', 1]], // fieldstone only (the critic's note 1: no andesite or stone)
  boards: 'spruce_planks',
  frame: 'dark_oak_log',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  trim: 'dark_oak', // barge boards, the door track, beams' slabs
  accent: 'birch', // shutters, the big doors' leaves, the cupola
  door: 'dark_oak',
  glass: 'glass_pane',
  bars: 'iron_bars', // stall windows: a grille, not glass
  aisle: 'packed_mud',
  bedding: 'hay_block', // the stalls' floor
  boardsFloor: 'spruce_planks', // the loft and the wing
  rim: 'stripped_spruce_log', // the troughs' log sides
  hay: 'hay_block',
  firewood: 'spruce_log', // stacked in the slot between the barn and the wing (II)
  banner: 'brown_banner',
}

export const MONITOR = {
  walls: { x0: 4, x1: 8, z0: 3, z1: 11 }, aisle: { x0: 5, x1: 7, z0: 4, z1: 10 },
  posts: [3, 7, 11], wallTop: 8, beamY: 9, loftY: 4,
  roof: { axis: 'z', across: [3, 9], along: [2, 12], eaveY: 9, steep: false },
  hood: { z: 1, hoist: { x: 6, y: 11, z0: 1, z1: 3 } },
  cupola: { cx: 6, cz: 7, y0: 12, roofY: 13 },
  chimney: { x: 6, z: 12, top: 13 },
}

export const SHEDS = {
  west: { outer: 1, x0: 2, x1: 3, roof: { axis: 'z', across: [0, 3], ridge: 4, along: [2, 12], ridgeY: 7 } },
  east: { outer: 11, x0: 9, x1: 10, roof: { axis: 'z', across: [9, 12], ridge: 8, along: [2, 12], ridgeY: 7 } },
  z0: 3, z1: 11, posts: [3, 7, 11], wallTop: 3,
  stalls: [{ z0: 4, z1: 6, gate: 5 }, { z0: 8, z1: 10, gate: 9 }], partition: 7,
}

export const GATE = { posts: [4, 8], z: 0, top: 5, barY: 6, bar: [3, 9], banner: [6, 7, 0], lamps: [[5, 5, 0], [7, 5, 0]] }

export const YARD = {
  fence: [[[3, 0, 0], [0, 0, 0], [0, 0, 2]], [[9, 0, 0], [12, 0, 0], [12, 0, 2]]],
  trough: { water: [[1, 1], [2, 1]], y: 0 },
  hay: [[11, 0, 1], [11, 0, 2], [11, 1, 2]],
}

export const WING = {
  walls: { x0: 13, x1: 19, z0: 5, z1: 11 }, room: { x0: 14, x1: 18, z0: 6, z1: 10 },
  wallTop: 3, beamY: 4, posts: { front: [13, 15, 17, 19], side: [5, 11] },
  roof: { axis: 'x', across: [4, 12], along: [13, 20], eaveY: 4, steep: false },
}

export const PADDOCK = {
  ring: [[0, 0, 13], [20, 0, 13], [20, 0, 20], [0, 0, 20], [0, 0, 13]],
  manGap: [0, 0, 16], // one block wide: a villager walks through, a horse (1.4 wide) can't
  gates: [[20, 0, 16], [20, 0, 17]], // a double gate to drive the herd in and out
  lampPosts: [[0, 13], [20, 13], [20, 20], [0, 20]],
  trough: { water: [[9, 19], [10, 19]], y: 0 },
  snubbingPost: [10, 16],
  shelter: { x0: 1, x1: 4, z0: 18, z1: 19, y: 3, posts: [[1, 18], [4, 18], [1, 19], [4, 19]], hay: [[2, 0, 19], [3, 0, 19]] },
}

export const ROOMS = [
  { name: 'Aisle', use: 'the walk from the big doors to the tack wall: the Rancher\'s smoker and chest at its end', box: [5, 1, 4, 7, 3, 10] },
  { name: 'Stalls', use: 'four box stalls, hay-bedded, gated off the aisle', box: [2, 1, 4, 10, 3, 10] },
  { name: 'Hay loft', use: 'hay stacked under the monitor roof, lit by the clerestory, up a ladder from the aisle', box: [5, 5, 4, 7, 10, 10] },
  { name: 'Feed and bunk room (II)', use: 'the feed barrel, more chests, hay, the ranch hand\'s bunk and the ledger desk', box: [14, 1, 6, 18, 3, 10] },
]

export const OPENINGS = {
  // the wicket door in the big barn doors (whose two birch leaves stand either side), a stair step before it
  barnDoor: { at: [6, 1, 3], facing: 'south', out: 'north', step: [6, 0, 2], leaves: [[5, 3], [7, 3]] },
  hayDoor: { at: [6, 5, 3], facing: 'south', out: 'north' },
  ladder: { x: 7, z: 4, y0: 1, top: 4, facing: 'south' }, // against the front wall, up into the loft's hatch
  windows: [
    { name: 'west shed front', cells: [[2, 2, 3], [2, 3, 3]], out: 'north', dress: 'shutters' },
    { name: 'east shed front', cells: [[10, 2, 3], [10, 3, 3]], out: 'north', dress: 'shutters' },
    { name: 'west shed back', cells: [[2, 2, 11], [2, 3, 11]], out: 'south', dress: 'shutters' },
    { name: 'east shed back', cells: [[10, 2, 11], [10, 3, 11]], out: 'south', dress: 'shutters' },
    { name: 'loft back west', cells: [[5, 6, 11], [5, 7, 11]], out: 'south', dress: null },
    { name: 'loft back east', cells: [[7, 6, 11], [7, 7, 11]], out: 'south', dress: null },
  ],
  clerestory: { xs: [4, 8], zs: [5, 9], ys: [7, 8] }, // the monitor's high windows over the sheds
  stallWindows: { xs: [1, 11], zs: [5, 9], y: 2 }, // iron bars in the sheds' outer walls
  wing: {
    door: { at: [16, 1, 5], facing: 'south', out: 'north', step: [16, 0, 4] },
    windows: [
      { name: 'wing front west', cells: [[14, 2, 5], [14, 3, 5]], out: 'north', dress: 'shutters' },
      { name: 'wing front east', cells: [[18, 2, 5], [18, 3, 5]], out: 'north', dress: 'shutters' },
      { name: 'wing east', cells: [[19, 2, 8], [19, 3, 8]], out: 'east', dress: 'shutters' },
      { name: 'wing back', cells: [[14, 2, 11], [14, 3, 11]], out: 'south', dress: 'shutters' },
      { name: 'wing gable', cells: [[19, 6, 8]], out: 'east', dress: null },
    ],
  },
}

// Every piece of furniture, footprint first (furnitureParts draws each and records its spot; chests are fixtures).
// Kept cells (the brief's): smoker (6, 1, 10) and chest (5, 1, 10) where they were; (7, 1, 10) stays open floor; the
// old yard cauldrons' cells (9, 0, 2) and (10, 0, 2) stay open ground; Level II's barrel (14, 1, 10) where it was.
export const FURNITURE = {
  1: [
    { room: 'Aisle', kind: 'fixture', part: 'stove', pos: [6, 1, 10], facing: 'north', block: 'smoker' }, // the Rancher's job block
    { room: 'Aisle', kind: 'fixture', part: 'pantry', pos: [5, 1, 10], facing: 'north', block: 'chest' }, // tack: saddles, armor, carpets, feed
    { room: 'Aisle', kind: 'fixture', part: 'pantry', pos: [5, 1, 9], facing: 'east', block: 'chest' }, // more tack, against the first
    { room: 'Aisle', kind: 'shelf', part: 'shelf', pos: [7, 2, 10], facing: 'north', wood: 'spruce', plant: 'potted_cactus' }, // a plant, not crockery
    { room: 'Aisle', kind: 'lamp', part: 'hangingLamp', pos: [6, 3, 6], facing: 'north', wood: 'spruce' },
    { room: 'Aisle', kind: 'lamp', part: 'hangingLamp', pos: [6, 3, 9], facing: 'north', wood: 'spruce' },
    { room: 'Stalls', kind: 'lamp', part: 'hangingLamp', pos: [3, 5, 5], facing: 'east', wood: 'spruce' },
    { room: 'Stalls', kind: 'lamp', part: 'hangingLamp', pos: [3, 5, 9], facing: 'east', wood: 'spruce' },
    { room: 'Stalls', kind: 'lamp', part: 'hangingLamp', pos: [9, 5, 5], facing: 'west', wood: 'spruce' },
    { room: 'Stalls', kind: 'lamp', part: 'hangingLamp', pos: [9, 5, 9], facing: 'west', wood: 'spruce' },
    { room: 'Hay loft', kind: 'lamp', part: 'hangingLamp', pos: [6, 11, 7], facing: 'north', wood: 'spruce' },
  ],
  2: [
    { room: 'Feed and bunk room (II)', kind: 'fixture', part: 'pantry', pos: [14, 1, 10], facing: 'north', block: 'barrel' }, // feed
    { room: 'Feed and bunk room (II)', kind: 'fixture', part: 'doubleChest', pos: [15, 1, 10], facing: 'north', along: 'east' },
    { room: 'Feed and bunk room (II)', kind: 'bed', part: 'bed', pos: [17, 1, 7], facing: 'east', colour: 'brown' },
    { room: 'Feed and bunk room (II)', kind: 'nightstand', part: 'nightstand', pos: [18, 1, 6], facing: 'west', wood: 'spruce' },
    { room: 'Feed and bunk room (II)', kind: 'desk', part: 'desk', pos: [14, 1, 7], facing: 'west', wood: 'spruce' },
    { room: 'Feed and bunk room (II)', kind: 'chair', part: 'chair', pos: [15, 1, 7], facing: 'west', wood: 'spruce' },
    { room: 'Feed and bunk room (II)', kind: 'rug', part: 'rug', cellsAbs: [[16, 1, 8], [17, 1, 8], [16, 1, 9], [17, 1, 9]], colour: 'red' },
    { room: 'Feed and bunk room (II)', kind: 'lamp', part: 'hangingLamp', pos: [16, 5, 8], facing: 'north', chain: 2, wood: 'spruce' }, // low enough to light the bunk's nightstand
  ],
}

// The walking line: through the ranch gate, across the yard, up the step, in at the wicket door, down the aisle to the
// smoker (he works it from (6, 1, 9)) and the chests; up the ladder to the loft. Level II: along the barn's front to the
// wing's door, in to the barrel; round the west side through the man-gap into the paddock.
export const WALK = {
  1: [[6, 0, 0], [6, 0, 1], [6, 0, 2], [6, 1, 3], [6, 1, 4], [6, 1, 5], [6, 1, 6], [6, 1, 7], [6, 1, 8], [6, 1, 9],
    [7, 1, 5], [7, 1, 4], [7, 5, 4], [6, 5, 5]],
  2: [[13, 0, 2], [15, 0, 3], [16, 0, 4], [16, 1, 5], [16, 1, 6], [15, 1, 8], [14, 1, 9], [-1, 0, 16], [0, 0, 16], [3, 0, 16]],
}
