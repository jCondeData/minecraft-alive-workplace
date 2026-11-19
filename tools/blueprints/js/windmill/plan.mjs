// The Windmill, the plan: drawn before any wall. Masses, the machine, rooms, every furniture footprint and the walking
// line come first; recipe.mjs then assembles the mill from the parts library's (../parts) named parts to fit this plan.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Stage Mill: a tarred octagonal smock tower tapering out of a round brick drum, a reefing stage (a gallery on
//      brackets) ringing it at the drum's top, a boat-shaped cap with the bearing in its nose and a tail pole raking down
//      to the stage; the sails sweep just in front of the stage. One round meal floor in the drum: the millstone, the
//      Farmer's composter and his store chest; the stone floor above, up a stair. Story: a Low Countries miller who had
//      to raise his mill above the village roofs to catch the wind, and walks the stage to reef his sails.
//   B. The Post Mill: a weatherboarded buck (the whole mill body) perched on one oak post and its trestle over a squat
//      brick roundhouse, a ladder and tail pole out of its back, the sails on its front gable. Story: the oldest kind of
//      mill, turned bodily to the wind. Its millstone would sit in the buck, up a ladder from the Farmer.
//   C. The Barn Tower: a squat stone tower rising out of a long gabled granary, the cap a low cone. Story: a farm that
//      added a mill to its barn. The safest: a house with a tower.
// Strongest: A. It is the only one whose silhouette says "windmill" from every side and at a glance (the taper, the
// stage's hard horizontal line half way up, the cap's boat and its tail pole), it has real depth (the stage throws a
// shadow ring, the taper steps in twice, the drum's brackets), and its plan works for the Farmer: the millstone, the
// composter and the chest share one round room on the ground. B puts the millstone up a ladder and is one box on a
// stick; C reads as a barn first. Building A.
//
// Coordinates: x across the front (west 0 -> east 14), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The sails turn in the plane z = 0 (the front), at y 5 and up: the only cells the bearing
// carries away, so the compat test's filter (z = 0, y >= 5) keeps holding. Nothing else is drawn in that plane above
// y = 3, and nothing touches a sail edge-on (sailPlaneCheck throws if anything does).
//
// Masses (named parts the owner's feedback can point at):
//   Drum     the round brick base, an octagon 9 across (half 4 about x 7, z 7), walls y 1..3 on a stone plinth;
//            the meal floor inside.
//   Stage    the reefing stage, a deck at y 4 out to half 5 (a block past the drum all round, on brackets) with a
//            fence rail, the stage door on its front.
//   Smock    the tarred timber tower, slimmer than the drum: half 3 at y 5..7, stepping in to half 2 at y 8..9 (a drip
//            of stairs on the step: the taper), the white curb band at y 10 the cap turns on.
//   Cap      the boat cap, a 45-degree slate gable 5 wide along z from z 1 to z 10 (ridge y 13), its nose hooding the
//            windmill bearing at (7, 11, 1). First drawing had it 7 wide over a fatter tower: it read squat, all cap.
//   Sails    four common sails in a pinwheel on the hub at (7, 11, 0): sail-frame stocks 6 long, white cloth 2 wide.
//   Tail     the tail beam out of the cap's back and its post down to the stage rim, where the miller winds the cap.

export const SIZE = [15, 18, 14] // w, h, d (limit 20 x 18 x 27)

// Palette (60/30/10): tarred spruce boarding (the smock, the stage, the cap's boards) / red brick (the drum) with a
// stone plinth / white-painted trim (birch: door and window frames, the curb band) and the white sails, the slate cap.
// Why: a dark smock is what makes white sails read from the street; the brick drum warms the foot; the white trim ties
// the sails to the body.
export const PALETTE = {
  plinth: [['stone_bricks', 5], ['cobblestone', 3], ['andesite', 2], ['mossy_stone_bricks', 1]],
  brick: [['bricks', 9], ['granite', 1], ['mud_bricks', 1]],
  boards: [['spruce_planks', 7], ['dark_oak_planks', 3]], // the tarred smock, weathered patchy (fairMix)
  smockStairs: 'spruce_stairs',
  deck: 'spruce_planks',
  fence: 'spruce_fence',
  bracket: 'dark_oak_stairs',
  floor: 'spruce_planks',
  trim: 'stripped_birch_log',
  trimStairs: 'birch_stairs',
  cap: { stairs: 'deepslate_tile_stairs', ridge: 'deepslate_tile_slab', board: 'dark_oak_planks' },
  pole: 'stripped_dark_oak_log',
  hub: 'stripped_dark_oak_log',
  sails: { stock: 'create:sail_frame', cloth: 'create:white_sail' },
  wood: 'spruce',
  glass: 'glass_pane',
  sack: 'hay_block',
}

export const CENTRE = [7, 7]

export const MASSES = {
  drum: { half: 4, y0: 1, y1: 3 },
  stage: { half: 5, y: 4 },
  smock: [{ half: 3, y0: 5, y1: 7 }, { half: 2, y0: 8, y1: 9 }],
  curb: { half: 2, y: 10, floor: { x0: 5, x1: 9, z0: 2, z1: 9 } },
  cap: { x0: 5, x1: 9, z0: 1, z1: 10, y0: 11, zg0: 2, zg1: 9 },
}

// The machine: the bearing in the cap's nose faces north (sails at z = 0); the windshaft runs back to a gearbox over the
// tower's centre, the upright shaft drops through both floors to a foot gearbox in the meal floor, and a shaft runs
// two blocks east under the floor boards to the gearbox that turns the millstone up over it. 64 sails: 8 RPM.
export const MACHINE = {
  hub: [7, 11, 0],
  bearing: [7, 11, 1], facing: 'north',
  head: [7, 11, 7], footY: 0, out: 'east', run: 2, // millstone at (9, 1, 7)
  sails: { arm: 6, width: 2, gap: 1 },
}

export const ROOMS = [
  { name: 'Meal floor', use: 'the millstone, the Farmer\'s composter and his store chest; sacks of grain', box: [4, 1, 4, 10, 3, 10] },
  { name: 'Stone floor', use: 'the upright shaft, grain waiting, the stage door', box: [5, 5, 5, 9, 7, 9] },
]

export const OPENINGS = {
  frontDoor: { at: [7, 1, 3], facing: 'south', out: 'north', step: [7, 0, 2] },
  stageDoor: { at: [7, 5, 4], facing: 'south', out: 'north' },
  // a straight flight up the west side of the meal floor rising north, its last step set in the stage deck's boards
  stairs: { flight: [[5, 1, 9], [5, 2, 8], [5, 3, 7], [5, 4, 6]], rises: 'north', well: [[5, 7], [5, 8]] },
  windows: [
    { name: 'drum west', cells: [[3, 2, 7]], out: 'west', dress: 'shutters' },
    { name: 'drum east', cells: [[11, 2, 7]], out: 'east', dress: 'shutters' },
    { name: 'drum back', cells: [[7, 2, 11]], out: 'south', dress: 'shutters' },
    { name: 'smock west', cells: [[4, 6, 7]], out: 'west', dress: null },
    { name: 'smock east', cells: [[10, 6, 7]], out: 'east', dress: null },
    { name: 'smock back', cells: [[7, 6, 10]], out: 'south', dress: null },
    { name: 'smock high front', cells: [[7, 9, 5]], out: 'north', dress: null },
  ],
}

// Every piece of furniture, footprint first (kind: a furniture.py kind, or a fixture the game uses as itself).
export const FURNITURE = [
  // Meal floor: the composter right of the door (the Farmer's job site), the store chest on the east wall by the
  // millstone (walk-up cells (9, 1, 8) and (8, 1, 7)), grain in hay sacks along the west wall and under the stair
  { room: 'Meal floor', kind: 'fixture', part: 'composter', pos: [9, 1, 4], block: 'composter' },
  { room: 'Meal floor', kind: 'fixture', part: 'doubleChest', pos: [10, 1, 8], facing: 'west', along: 'south', block: 'chest' },
  { room: 'Meal floor', kind: 'lamp', part: 'hangingLamp', pos: [8, 3, 5], facing: 'north', wood: 'spruce' },
  { room: 'Meal floor', kind: 'lamp', part: 'hangingLamp', pos: [8, 3, 9], facing: 'north', wood: 'spruce' },
  // Stone floor: a lamp from the curb
  { room: 'Stone floor', kind: 'lamp', part: 'hangingLamp', pos: [8, 9, 7], facing: 'north', wood: 'spruce' },
]

// Grain sacks (hay) stacked where nobody walks
export const SACKS = [[4, 1, 6], [4, 1, 7], [4, 1, 8], [4, 2, 7], [5, 1, 7], [5, 1, 8], [9, 5, 8], [8, 5, 9], [9, 6, 8]]

// The walking line: front step -> door -> composter -> the chest and the millstone (from (10, 1, 7)) -> round the
// upright shaft to the stair's foot -> up the flight -> the stone floor -> the stage door -> the stage.
export const WALK = [
  [7, 0, 1], [7, 0, 2], [7, 1, 3], [7, 1, 4], [8, 1, 5], [9, 1, 5], [9, 1, 6], [8, 1, 7], [9, 1, 8],
  [6, 1, 5], [6, 1, 8], [6, 1, 9], [5, 1, 10], [5, 1, 9], [5, 2, 8], [5, 3, 7], [5, 4, 6], [6, 5, 5],
  [7, 5, 5], [7, 5, 4], [7, 5, 3], [4, 5, 4], [3, 5, 7],
]

// Outside: a lamp post by the door (off the sail plane), hay stacked against the drum's flanks, the tail pole.
export const OUTSIDE = {
  lampPost: [10, 0, 1],
  hay: [[2, 0, 9], [2, 0, 10], [2, 1, 9], [12, 0, 5], [12, 0, 6]],
  tail: [[7, 10, 10, 'z'], [7, 10, 11, 'z'], [7, 10, 12, 'z'], [7, 9, 12, 'y'], [7, 8, 12, 'y'], [7, 7, 12, 'y'], [7, 6, 12, 'y'], [7, 5, 12, 'y']],
  tailBrace: [7, 9, 11],
}
