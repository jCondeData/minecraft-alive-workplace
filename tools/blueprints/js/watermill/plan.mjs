// The Watermill, the plan: drawn before any wall. Story, palette, masses, the machine line (every Create part, its cell
// and state, and the water that drives it), rooms, openings, every furniture footprint and the walking line;
// recipe.mjs assembles it from the parts library (../parts) in passes.
//
// Three rough concepts (ROADMAP 49, REBUILD.md):
//   1. "Race mill": a long, low stone mill beside a straight stone race, an undershot wheel in the race, the stones in
//      one room. Massing: one long box and a ditch. Roof: one 45-degree gable along z. Plan: one room. Story: any
//      village mill. Safe and quick to read, but the water is a ditch, the wheel sits at the ground, and from the
//      street it is a barn with a trench beside it.
//   2. "Tower mill on the weir": a square three-storey stone tower over the wheel pit, the stones on its middle floor,
//      the Farmer's store at its foot. Massing: one tall tower and a shed. Roof: a steep pyramid. Plan: three stacked
//      rooms. Story: a lord's mill, built to be seen. A strong vertical, but the stones end up two flights from the
//      composter and the store, the plot is too small for a tower to step back, and without sails it reads as a
//      windmill that lost them.
//   3. "Pond-and-lucam mill": the water is a story you can follow from the street. A raised mill pond stands on a
//      stone terrace against the mill's back wall; a timber launder carries its water along the west wall, over the
//      TOP of an overshot wheel, down its face and back under it into a stone tail pit (all three wet faces push the
//      wheel the same way). The mill house stands on a flood-proof stone undercroft: a stone ground storey (the meal
//      floor, the stones on a timber hurst, the drive shaft coming through the wall from the wheel) under a steep
//      timber-framed gable whose loft is the grain floor, and from that gable juts the LUCAM, the sack-hoist hood every
//      old mill carries, its floor a canopy over the entrance steps. A low cross-gabled granary wing on the east holds
//      the Farmer's store, his composter in the yard by its door. Massing: tall mill + lucam + pond terrace + launder
//      and pit + granary wing, five heights. Roof: a steep gable along z, the lucam's little gable, the wing's 45-degree
//      gable along x dying into the main slope. Plan: meal floor, grain loft, granary.
// Strongest: 3. Every part of the mill is something a player can point at and name (pond, sluice, launder, wheel, pit,
// lucam, stones, granary), the water runs the whole length of the west side in view, the hero gable with its lucam is
// recognisable in outline from any distance, and the Farmer's walk (composter -> granary chest -> stones) stays on
// one floor. Built here.
//
// Coordinates: x across the front (west 0 -> east 16), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The front is the z = 0 side: the mill's door (up a stone perron under the lucam) and
// the granary's door face it.
//
// The machine (Create; every kinetic part turns, nothing decorative; ids and states checked in
// libs/create-fabric-6.0.10.0+mc1.21.1.jar):
//   water   the builder pours the pond's 15 sources (x 1..5, z 13..15, y 3), open to the sky, last of all. The launder
//           runs north from the pond's mouth at (2, 3, 12) (level 1) through (2, 3, 11) and (2, 3, 10), over the
//           wheel's top at (2, 3, 9) (level 4), to the lip (2, 3, 8) (level 5), falls at (2, 2, 8) past the wheel's
//           north face (stone on its other three sides) into a closed sump (2, 1, 8) and (2, 1, 9) under the wheel
//           (stone on every side and below). Over the top running north, down the north face, back south under it:
//           the same turn. With Create gone the wheel's cell is air, the top water falls through it into the sump, and
//           falling water never spreads sideways over water: no shaft hole or room ever gets wet; nothing water breaks
//           stands anywhere on the path.
//   wheel   create:water_wheel at (2, 2, 9), axle along x (facing east): 8 RPM, 256 stress units. Its west face looks
//           out through an opening in the pit wall at (1, 2, 9).
//   drive   create:shaft (axis x) at (3, 2, 9) (through the west wall) and (4, 2, 9) (into the hurst), create:gearbox
//           (axis z: it takes the drive in from the west and up) at (5, 2, 9), create:millstone at (5, 3, 9) on it.
//   load    the millstone at 4 stress units per RPM at 8 RPM = 32 of the wheel's 256. Mirrored left to right the pit,
//           the launder and the drive mirror together; the wheel turns the other way and the stones still turn.
// New pinned cells for the ship step (it moves the tests): composter (15, 0, 3), water wheel (2, 2, 9), gearbox
// (5, 2, 9), millstone (5, 3, 9). New size for StarterBlueprints.WATERMILL: new Vec3i(17, 16, 17).

export const SIZE = [17, 16, 17] // w, h, d (limit 17 x 16 x 17)

// Palette (60/30/10): grey field stone (the undercroft, the meal floor's walls, the pond terrace, the pit; moss where
// the water is) / a warm spruce shingle roof / the timber gables: dark oak frame over white plaster (white terracotta:
// a flour-dusted mill), its gable apexes boarded in dark oak, stone-brick dressings at corners, door and curb. Why: a mill is built of stone where the
// water and the weight are and of timber where the grain is; the white gables say "flour" from the street; the brown
// roof parts the grey below from the white above.
export const PALETTE = {
  stone: [['cobblestone', 6], ['stone', 2], ['andesite', 1], ['mossy_cobblestone', 1]],
  wet: [['mossy_cobblestone', 4], ['cobblestone', 3], ['mossy_stone_bricks', 2], ['stone_bricks', 1]], // the pit and the pond, where the water is
  fill: 'cobblestone', // hidden: under floors and the pond's bed
  dressed: 'stone_bricks',
  dressedAlt: 'polished_andesite',
  dressedStairs: 'stone_brick_stairs',
  key: 'chiseled_stone_bricks',
  frame: 'dark_oak_log',
  infill: [['white_concrete', 1]], // plaster
  gableBoards: 'dark_oak_planks', // the gable apexes over the loft, boarded
  roof: { full: 'spruce_planks', stairs: 'spruce_stairs', slab: 'spruce_slab' },
  trim: 'dark_oak',
  floor: 'spruce_planks',
  granaryFloor: 'oak_planks',
  launderSide: 'stripped_spruce_log',
  launderBed: 'spruce_planks',
  hurst: { post: 'stripped_dark_oak_log', board: 'dark_oak_planks', deck: 'spruce_slab' },
  wood: 'spruce', // doors, trapdoors, fences, chairs
  glass: 'glass_pane',
  sacks: 'hay_block', // grain in sacks
  flour: 'white_wool', // flour in sacks
}

export const MASSES = {
  // the mill house: stone undercroft and ground storey y 0..4 round x 3..11, z 2..12; the beam ring and the loft floor
  // at y 5; the grain loft's timber walls y 6..9; gables at z 2 and 12 under a 45-degree spruce roof (ridge along z, y 14)
  mill: { x0: 3, x1: 11, z0: 2, z1: 12, floorY: 1, wallTop: 4, beamY: 5,
    // the grain loft: timber walls y 6..8 (posts at the corners and between the bays), a top plate at y 9
    upper: { y0: 6, y1: 8, plate: 9, frontPosts: [3, 6, 8, 11], backPosts: [3, 7, 11], sidePosts: [2, 5, 9, 12] },
    roof: { axis: 'z', across: [2, 12], along: [1, 13], eaveY: 9, steep: false } },
  // the lucam: a 3-wide hood out of the front gable over the door, floor y 5, walls to y 9, ridge y 12
  lucam: { x: 7, z0: 0, depth: 2, y0: 5, top: 9 },
  // the granary wing: stone to y 1, timber y 2..4, beam y 5, a 45-degree gable along x (ridge y 9) into the main slope
  granary: { x0: 11, x1: 15, z0: 4, z1: 10, beamY: 5, roof: { axis: 'x', across: [3, 11], along: [11, 16], eaveY: 5, steep: false } },
  // the pond: water x 1..5, z 13..15 at y 3 on a stone terrace, curb at y 4; its front wall is the mill's back wall
  pond: { x0: 1, x1: 5, z0: 13, z1: 15, waterY: 3 },
  // stone steps up the terrace's east side (x 7) from the back of the plot to the curb
  pondSteps: { flight: [[7, 0, 16], [7, 1, 15], [7, 2, 14], [7, 3, 13]], rises: 'north' },
  // the pit and the launder's piers: x 1..2, z 7..12
  pit: { x0: 1, x1: 2, z0: 7, z1: 12 },
}

export const MACHINE = {
  wheel: { at: [2, 2, 9], axle: 'east', flow: 'north', run: 3 },
  shafts: [[3, 2, 9], [4, 2, 9]],
  gearbox: [5, 2, 9],
  millstone: [5, 3, 9],
  sluice: [2, 4, 12], // the raised gate over the launder's mouth
}

export const ROOMS = [
  { name: 'Meal floor', use: 'the stones on their hurst, the drive through the west wall, the miller\'s desk, sacks of grain and flour', box: [4, 2, 3, 10, 4, 11] },
  { name: 'Grain loft', use: 'the grain floor under the roof: sacks, the hoist door into the lucam', box: [4, 6, 3, 10, 12, 11] },
  { name: 'Granary', use: "the Farmer's store: his chest, a weighing counter, sacks", box: [12, 2, 5, 14, 4, 9] },
]

export const OPENINGS = {
  millDoor: { at: [7, 2, 2], facing: 'south', out: 'north', steps: [[7, 1, 1], [7, 0, 0]] },
  granaryDoor: { at: [13, 2, 4], facing: 'south', out: 'north', steps: [[13, 1, 3], [13, 0, 2]] },
  innerDoor: { at: [11, 2, 5], height: 2 },
  stairs: { flight: [[10, 2, 7], [10, 3, 8], [10, 4, 9], [10, 5, 10]], rises: 'south', well: [[10, 5, 7], [10, 5, 8], [10, 5, 9]],
    rail: [[10, 6, 6], [9, 6, 6], [9, 6, 7], [9, 6, 8], [9, 6, 9]] },
  // windows: the ground storey's with stone sills and a pot; the gables' and the granary's as listed
  windows: [
    { name: 'front west', cells: [[5, 3, 2], [5, 4, 2]], out: 'north', dress: 'sill' },
    { name: 'front east', cells: [[9, 3, 2], [9, 4, 2]], out: 'north', dress: 'sill' },
    { name: 'west', cells: [[3, 3, 4], [3, 4, 4], [3, 3, 5], [3, 4, 5]], out: 'west', dress: 'sill' },
    { name: 'back', cells: [[9, 3, 12], [9, 4, 12]], out: 'south', dress: 'sill' },
    { name: 'loft front west', cells: [[4, 7, 2], [5, 7, 2], [4, 8, 2], [5, 8, 2]], out: 'north', dress: null },
    { name: 'loft front east', cells: [[9, 7, 2], [10, 7, 2], [9, 8, 2], [10, 8, 2]], out: 'north', dress: null },
    { name: 'loft back west', cells: [[5, 7, 12], [5, 8, 12]], out: 'south', dress: null },
    { name: 'loft back east', cells: [[9, 7, 12], [9, 8, 12]], out: 'south', dress: null },
    { name: 'loft west', cells: [[3, 7, 7], [3, 8, 7]], out: 'west', dress: null },
    { name: 'loft west front', cells: [[3, 7, 3], [3, 8, 3], [3, 7, 4], [3, 8, 4]], out: 'west', dress: null },
    { name: 'loft west back', cells: [[3, 7, 10], [3, 8, 10], [3, 7, 11], [3, 8, 11]], out: 'west', dress: null },
    { name: 'loft east front', cells: [[11, 7, 3], [11, 8, 3], [11, 7, 4], [11, 8, 4]], out: 'east', dress: null },
    { name: 'loft east back', cells: [[11, 7, 10], [11, 8, 10], [11, 7, 11], [11, 8, 11]], out: 'east', dress: null },
    { name: 'back attic', cells: [[7, 10, 12], [7, 11, 12]], out: 'south', dress: null },
    { name: 'granary east', cells: [[15, 3, 7], [15, 4, 7]], out: 'east', dress: 'shutters' },
    { name: 'granary back', cells: [[13, 3, 10], [13, 4, 10]], out: 'south', dress: 'shutters' },
    { name: 'granary gable', cells: [[15, 7, 7]], out: 'east', dress: null },
  ],
}

// Every piece of furniture, footprint first (kind: a furniture.py kind, or a fixture the game uses as itself). No job
// block but the composter (a barrel would be a Fisherman's, a lectern a Librarian's): sacks are hay and wool blocks.
export const FURNITURE = [
  // Meal floor: the miller's desk and chair under the west window, a store chest by the door, lamps under the loft
  { room: 'Meal floor', kind: 'desk', part: 'desk', pos: [4, 2, 5], facing: 'west', wood: 'spruce' },
  { room: 'Meal floor', kind: 'chair', part: 'chair', pos: [5, 2, 5], facing: 'west', wood: 'spruce' },
  { room: 'Meal floor', kind: 'fixture', part: 'chest', pos: [10, 2, 3], facing: 'west', block: 'chest' },
  { room: 'Meal floor', kind: 'lamp', part: 'hangingLamp', pos: [7, 4, 5], facing: 'north', wood: 'spruce' },
  { room: 'Meal floor', kind: 'lamp', part: 'hangingLamp', pos: [8, 4, 9], facing: 'north', wood: 'spruce' },
  // Granary: the Farmer's store chest against the east wall, a weighing counter beside it, a lamp from the ridge
  { room: 'Granary', kind: 'fixture', part: 'chest', pos: [14, 2, 8], facing: 'west', block: 'chest' },
  { room: 'Granary', kind: 'counter', part: 'counter', pos: [14, 2, 6], facing: 'east', wood: 'spruce' },
  { room: 'Granary', kind: 'counter', part: 'counter', pos: [14, 2, 9], facing: 'east', wood: 'spruce' },
  { room: 'Granary', kind: 'lamp', part: 'hangingLamp', pos: [13, 6, 7], facing: 'north', chain: 2, wood: 'spruce' },
  // Grain loft: a lamp from the ridge, a table and bench by the back gable for the tally
  { room: 'Grain loft', kind: 'lamp', part: 'hangingLamp', pos: [7, 11, 7], facing: 'north', chain: 2, wood: 'spruce' },
  { room: 'Grain loft', kind: 'table', part: 'table', pos: [7, 6, 10], facing: 'south', wood: 'spruce', colour: 'white' },
  { room: 'Grain loft', kind: 'chair', part: 'chair', pos: [7, 6, 9], facing: 'south', wood: 'spruce' },
]

// Sacks (not furniture spots): grain by the stones and in the loft, flour by the back wall, the granary's back corner
export const SACKS = {
  grain: [[4, 2, 11], [5, 2, 11], [4, 3, 11], [12, 2, 9], [12, 3, 9], [4, 6, 9], [4, 6, 10], [4, 6, 11], [5, 6, 11], [5, 7, 11], [10, 6, 3], [9, 6, 3], [10, 7, 3], [4, 6, 3], [4, 6, 4]],
  flour: [[7, 2, 11], [8, 2, 11], [7, 3, 11]],
}

// The walking line: the street -> the perron (7, 0, 0) and (7, 1, 1) under the lucam -> the door (7, 2, 2) -> the meal
// floor to the stones' east side (7, 2, 9), within 3 of the millstone; the chest by the door (9, 2, 3); through the
// inner door (11, 2, 5) into the granary to the store chest (13, 2, 8); out of the granary door (13, 2, 4) down its
// steps to the composter (15, 0, 3) in the yard. Up the flight on the east wall (10, 2..5, 7..10) to the loft and
// through the gable (7, 6, 2) into the lucam.
export const WALK = [
  [7, 0, 0], [7, 1, 1], [7, 2, 2], [7, 2, 3], [7, 2, 6], [7, 2, 9], [9, 2, 3], [10, 2, 5], [11, 2, 5], [12, 2, 5],
  [13, 2, 7], [13, 2, 8], [13, 2, 5], [13, 2, 4], [13, 1, 3], [13, 0, 2], [14, 0, 2], [15, 0, 2],
  [10, 2, 6], [10, 2, 7], [10, 3, 8], [10, 4, 9], [10, 5, 10], [9, 6, 10], [7, 6, 4], [7, 6, 2], [7, 6, 1],
]

// The yard: the Farmer's composter by the granary steps with a hay stack, a bed of flowers in its own soil along the
// front, lamps on the perron's cheeks and a post lighting the wheel.
export const OUTSIDE = {
  composter: [15, 0, 3],
  hay: [[16, 0, 3], [16, 0, 4], [16, 1, 4]],
  flowers: { x0: 12, x1: 16, z: 0, plants: ['poppy', 'cornflower', 'oxeye_daisy', 'allium', 'azure_bluet'] },
  cheekLamps: [[6, 2, 0], [8, 2, 0]],
  wheelLamp: [0, 0, 6],
}
