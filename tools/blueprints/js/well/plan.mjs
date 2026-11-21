// The Well (`well`, Level II `well_2`), the plan: drawn before any block. ROADMAP 49.1, the rebuild's dry run; the
// brief is tools/blueprints/briefs/well.md. recipe.mjs assembles both levels from the parts library (../parts).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Windlass Well. A round rubble curb on a stepped stone dais, two spruce uprights carrying a windlass and a
//      steep shingle gable with bell-cast (flared) eaves, its gable to the road. Story: the first thing a village
//      raises in common once it has a market; you step up to it from any side and lean on the rim.
//   B. The Spring Basin. A low square basin, open 3 x 3 water, a stone column in the middle, no roof; Level II rings it
//      with arches. Story: masons capping a spring. Flat silhouette, nine buckets, reads as a pond from the road.
//   C. The Oak Well. A curb under an old oak whose bough holds the rope; Level II hangs lanterns and a swing seat in
//      the branches. Story: the well was dug beside the tree the village grew round. Lovely in a picture, but leaves
//      in a 7 x 7 plot swallow the curb, and a builder's tree reads as a blob at this scale.
// Strongest: A. It is the only one with a silhouette you know from across the village (the tall flared hat over a
// round mouth), it keeps the water a step up and a lean away (a real well, not a pond), and its parts each say what
// they are for: the dais to step onto, the rim to lean on, the windlass and crank to draw with, the roof to keep leaves
// and rain out. Level II grows straight out of it: it lights and seats the same dais and crowns the same ridge.
//
// Coordinates: x across the front (west 0 -> east 6), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north, the road) to the back. Exactly 7 x 7, centre (3, 3). No door: the lane starts at (3, -1), straight
// onto the front step.
//
// Masses (named parts the owner's feedback can point at):
//   Dais      a stone floor one block up over the whole plot, its edge a ring of steps rising inward (y 0).
//   Curb      a round rubble ring of radius 2 round the centre (y 1), capped with stone-brick slabs (y 2): the rim
//             stands 1.5 blocks over the dais, chest-high, and the water lies a block down inside it.
//   Mouth     five water sources in a cross (round in Minecraft's terms) at y 1, on the dais's stone, shut in by the
//             curb on every side: five buckets, all poured, all picked up again on a take-down.
//   Uprights  two spruce posts on the curb's sides (x 1 and x 5, z 3), y 2..5, so nothing stands between the road and
//             the water: from the front you look straight over the rim into the mouth (critic's note 1).
//   Frame     on the uprights' heads (y 5): a cross beam over the mouth (x 2..4) and an eave plate along each side
//             (z 1..5) carrying the roof's foot; a king post on the cross beam's middle carries the ridge beam (y 7).
//   Windlass  a stripped spruce axle between the uprights at y 4 over the mouth, a chain rope down to the centre
//             source, and a crank (a lever) on the east upright's outer face.
//   Hat       the roof: a 45-degree spruce-shingle gable over the frame (z 1..5, so the dais shows front and back),
//             ridge along z at y 8 on the ridge beam (y 7) whose ends stick a block out of each gable to carry Level
//             II's lanterns, eaves kicking out over the side steps in dark oak (y 5). Its gables are open trusses:
//             the cross-section shows the beam and two dark braces, no infill (critic's notes 2 and 3: a course lower
//             than the dry run's steep hat, the eaves a course higher, so a person stands upright at the rim).
//   Tufts     the four corners the round curb leaves on the dais: grass with a fern or a flower in it.
//   Level II (the Market Well) adds, on the same spot and keeping Level I's blocks but the four corner steps and the
//   two back tufts:
//   Basins    two stone dipping basins in the back corners, each a held water source walled against the curb (y 1):
//             a trough for the village's animals and a dip for a Well Keeper's bucket that never needs the windlass
//             (critic's note 4: a gain at the water itself).
//   Lamps     four stone lamp posts at the dais's corners, lanterns on them under the eaves' corners.
//   Benches   a spruce bench along each side (west and east) on the dais, facing the water.
//   Lights    lanterns hung from the ridge beam's ends over the front and back.
//   Vane      a copper rod on the ridge's middle (y 9) that greens as the village ages: the Market Well's crown.

export const SIZE = { 1: [7, 9, 7], 2: [7, 10, 7] } // w, h, d (brief: exactly 7 x 7, at most 10 tall)

// Palette (60/30/10): stone (dais, curb, rim) / spruce (uprights, windlass, shingles) with dark-oak trim / accents:
// water, green tufts, lantern light and the copper vane. Why: grey rubble and moss read as old and dug, not
// bought; the warm spruce hat lifts it from the grey so the roof doesn't read as one dark mass; the dark oak only
// draws the eave and rake lines; everything that catches the eye (water, green, light, copper) is the 10 %.
export const PALETTE = {
  dais: [['stone_bricks', 5], ['mossy_stone_bricks', 2], ['cracked_stone_bricks', 1]],
  step: [['cobblestone_stairs', 3], ['mossy_cobblestone_stairs', 2], ['stone_brick_stairs', 1]],
  curb: [['cobblestone', 5], ['mossy_cobblestone', 3], ['stone', 1], ['andesite', 1]],
  rim: 'stone_brick_slab',
  frame: 'spruce_log',
  axle: 'stripped_spruce_log',
  roof: { full: 'spruce_planks', stairs: 'spruce_stairs', slab: 'spruce_slab' },
  kick: 'dark_oak_stairs', // the bell-cast eave line
  ridge: 'dark_oak_planks', // the ridge cap
  trim: 'dark_oak', // barge boards
  brace: 'dark_oak_stairs', // the open gables' braces, upside down under the rake
  basin: 'mossy_stone_bricks', // Level II's dipping basins
  wood: 'spruce', // benches
  lampPost: 'stone_brick_wall',
  lampFoot: 'mossy_stone_bricks',
  soil: 'grass_block',
  vane: 'lightning_rod',
}

export const MASSES = {
  dais: { x0: 0, z0: 0, x1: 6, z1: 6, y: 0 },
  curb: { cx: 3, cz: 3, r: 2, y: 1, rimY: 2 },
  mouth: { y: 1, cells: [[3, 2], [2, 3], [3, 3], [4, 3], [3, 4]] }, // [x, z]
  uprights: { xs: [1, 5], z: 3, y0: 2, y1: 5 }, // on the curb's sides, the front left open
  frame: { y: 5, cross: [2, 4], plates: [1, 5], king: [3, 6, 3] }, // cross beam z 3, plates along z at x 1 and x 5
  windlass: { z: 3, y: 4, x0: 2, x1: 4, rope: [[3, 3, 3], [3, 2, 3]], crank: [6, 4, 3] },
  hat: { axis: 'z', across: [0, 6], along: [1, 5], eaveY: 5, steep: false, beamY: 7, beam: [0, 6] },
  basins: [[1, 5, [[0, 5], [1, 6]]], [5, 5, [[6, 5], [5, 6]]]], // Level II: [x, z, its outer walls [x, z]], y 1
  tufts: [[1, 1, 'fern'], [5, 1, 'cornflower'], [1, 5, 'oxeye_daisy'], [5, 5, 'fern']], // [x, z, plant]
  lamps: [[0, 0], [6, 0], [0, 6], [6, 6]], // Level II: [x, z], post y 1..2, lantern y 3
  vane: [3, 9, 3], // Level II
}

// Rooms: none (an open well-head). The space under the hat is the Mouth.
export const ROOMS = [
  { name: 'Mouth', use: 'drawing water: lean on the rim, wind the bucket up', box: [1, 1, 1, 5, 3, 5] },
]

// Openings: none drawn (no walls); the dais is open on all four sides, the step ring its way up from any side.
export const OPENINGS = { approach: { lane: [3, 0, -1], step: [3, 0, 0], standAt: [3, 1, 0] } }

// Every piece of furniture, footprint first (kind: a furniture.py kind, recorded as a furniture spot; part: the
// furnitureParts part that draws it). Level I has none; Level II seats the dais. The lanterns are part of the frame
// (LIGHTS), not furniture spots: a furniture mod's lamp has no business on a well's beam, and a lamp spot would make
// the open well-head a "room" for RebuildScene to film from a floor it doesn't have.
export const FURNITURE = {
  1: [],
  2: [
    ...[2, 3, 4].map(z => ({ room: 'Mouth', kind: 'chair', part: 'chair', pos: [0, 1, z], facing: 'east', wood: 'spruce' })),
    ...[2, 3, 4].map(z => ({ room: 'Mouth', kind: 'chair', part: 'chair', pos: [6, 1, z], facing: 'west', wood: 'spruce' })),
  ],
}

// Lanterns hung from the ridge beam: Level I's over the front source (it lights the water, seen from the road),
// Level II's also at the beam's two proud ends, over the front and the back.
export const LIGHTS = { 1: [[3, 6, 2]], 2: [[3, 6, 2], [3, 6, 0], [3, 6, 6]] }

// The walking line: the lane's end -> the front step -> the rim (a Well Keeper stands at (3, 1, 0), two blocks from the
// source at (3, 1, 2), and reaches it; the chain rope hangs over the centre source, the others have air over them;
// Level II's basins are reached from the side benches' ends).
// Round the dais on the step ring (Level I), or between the benches and the lamp posts (Level II).
export const WALK = [[3, 0, -1], [3, 1, 0], [2, 1, 0], [1, 1, 0], [0, 1, 1], [0, 1, 3], [0, 1, 5], [1, 1, 6], [3, 1, 6]]
