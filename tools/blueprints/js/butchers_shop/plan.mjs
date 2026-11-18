// The Butcher's Shop (butchers_shop, one level; ROADMAP 49, brief tools/blueprints/briefs/butchers_shop.md), the plan:
// drawn before any wall. recipe.mjs assembles it from the parts library (../parts).
//
// Design notes. Three rough concepts first:
//   A. "The Shambles Shop and Smoke Tower": a white, dark-framed shop with its steep gable to the street, a wide
//      display board under each front window and a slate pentice over them (a medieval shambles front); a squat red
//      brick smoke tower bonded to its east side under a jettied slate pyramid, smoking at the peak; the stock pen
//      leaning on its west wall; the freezer under the shop's back half. Plan: customers' half at the front, the cold
//      counter across the shop, the butcher's half behind with the block, the hooks and the hatch down, the smoker in
//      the tower's foot through the side wall. Story: a town butcher who smokes his own hams and keeps his herd beside
//      the shop.
//   B. "The Ice House Farmstead": a long low stone range, ridge along x, its eaves to the street, a turf-roofed ice
//      house mound at one end over the freezer, a byre with a hayloft at the other. Plan: three rooms in a row, the
//      smoker in the middle one. Story: a farm butcher. Reads as a barn: the low long roof gives no hero, and the
//      mound has to be drawn above ground, where the brief says the freezer isn't.
//   C. "The Market Stall": an open-fronted stall under a deep canopy on posts, the counter facing the street, a
//      little cold store behind. Plan: one room open to the road. Story: a butcher at the market. Open sides fail the
//      open-wall check, and the stall has nowhere to keep the smoker indoors; the safest and the weakest.
//   A is strongest: the two masses tell the trade at a glance (the shop sells, the tower smokes), the tower is the hero
//   rising past the shop's ridge with smoke at its tip, the street front has the depth of a real shop (proud posts,
//   boards, pentice, a jettied gable with the shop's banner), and every function gets a place in the story: the herd
//   in the pen he can see from his window, the smoker where the smoke goes up, the freezer straight under the
//   butcher's half. Built: A.
//
// Coordinates: x across the front (west 0 -> east 20), y up (0 = the layer on the clicked block; the freezer is
// y -4..-1), z from the front (z = 0, north) to the back.
//
// Masses (named parts the owner's feedback can point at):
//   Shop       the main mass: dark oak posts on the plinth line x 5..15, z 2..14 (a block proud of the walls), white
//              panels set a block in (x 6..14, z 3..13), a wall plate and beams at y 4, a 45-degree slate roof (ridge
//              along z at x 10, y 9) over jettied gables at the post line, the front one carrying the shop's red
//              banner over the door.
//   Front      the shambles front: two wide windows with display boards (stone slabs on the plinth) and a slate
//              pentice over the whole front at y 4, the door recessed between two posts, a lantern under the pentice.
//   Smoke Tower the hero: red brick, 5 x 5 at x 15..19, z 6..10, walls to y 7 with a stone string course at y 4 and a sooted top course, brackets
//              under a 45-degree slate pyramid cap one block out (y 8..11), the smoke at its peak (y 12). Inside: the smoke room
//              (the Butcher's smoker), hooks hung from its vault, vents of iron bars in three faces.
//   Pen        (left out when it shipped, 50.4: the build overran its tests' one-day budget with it) the stock pen
//              against the shop's west wall (x 0..4, z 6..14), a gate to the street, a slate lean-to over hay at its
//              back. The plan keeps it (MASSES.pen, MASSES.shelter, OUTSIDE.hay); recipe.mjs no longer draws it.
//   Freezer    the cellar under the butcher's half (walls x 8..14, z 7..13, floor y -4): packed-ice walls on stone
//              piers, a blue-ice panel in its floor, hooks, snow and ice stacked, two chests, two lanterns, a ladder up
//              through a hatch.

export const SIZE = [21, 13, 16] // w, h above the ground, d (limit 21 x 13 x 21)
export const BASEMENT = 4

// Palette (60/30/10): white plaster panels (a butcher's clean white walls) / dark oak frame and a deepslate slate
// roof / red brick (the smoke tower: where the heat is), with the stone plinth darkest at the foot. Ice for the
// freezer. Why: white and dark is the old shambles' look and reads "clean" from the street; the one red mass is the
// smokehouse; one roof material over both masses ties them; a builder buys clay, bricks, dark oak and deepslate,
// all gatherable.
export const PALETTE = {
  plinth: [['cobbled_deepslate', 3], ['cobblestone', 3], ['stone', 2], ['andesite', 1]],
  frame: 'dark_oak_log',
  infill: [['white_concrete', 6], ['polished_diorite', 1]], // plaster: white with a little grey in it
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  tower: [['bricks', 9], ['mud_bricks', 1]], // fired brick, a few paler ones
  soot: 'deepslate_bricks',
  bracket: 'brick_stairs',
  floor: 'smooth_stone', // the shop's cold flags (and the freezer's ceiling)
  trim: 'dark_oak',
  wood: 'spruce', // the door, counters, hatch, gate, posts of the pen and shelter
  glass: 'glass_pane',
  sill: 'smooth_stone_slab', // the display boards and sills
  banner: 'red_wall_banner',
  freezerWall: 'packed_ice',
  freezerPier: 'stone_bricks',
  freezerFloor: 'polished_andesite',
  freezerIce: 'blue_ice',
  penGround: [['coarse_dirt', 3], ['rooted_dirt', 1]], // trodden earth
  band: 'stone_bricks', // the tower's string course
}

export const MASSES = {
  shop: {
    posts: { x0: 5, x1: 15, z0: 2, z1: 14 },
    panels: { x0: 6, x1: 14, z0: 3, z1: 13 },
    room: { x0: 7, x1: 13, z0: 4, z1: 12 },
    beamY: 4,
    frontPosts: [5, 9, 11, 15], backPosts: [5, 8, 12, 15],
    westPosts: [2, 6, 10, 14], eastPosts: [2, 14], // the tower's wall stands on the east line at z 6..10
    ties: [6, 10], // tie beams across the shop at y 4 (lamps and hooks hang from them)
    roof: { axis: 'z', across: [5, 15], along: [1, 15], eaveY: 4, steep: false },
  },
  tower: { x0: 15, z0: 6, x1: 19, z1: 10, top: 7, room: { x0: 16, z0: 7, x1: 18, z1: 9 } },
  pen: { x0: 0, z0: 6, x1: 4, z1: 14, gateAt: [2, 6] },
  shelter: { x0: 0, z0: 11, x1: 5, z1: 14, y: 3, posts: [[0, 11], [0, 14]] },
  freezer: { x0: 8, z0: 7, x1: 14, z1: 13, depth: 4, panel: [10, 9, 12, 11] }, // inside x 9..13, z 8..12
}

export const ROOMS = [
  { name: 'Shop', use: "customers' half: the door, the display counters under the windows, the cold counter", box: [7, 1, 4, 13, 3, 6] },
  { name: 'Block', use: "the butcher's half: the counter, the chopping block, hooks, the back counters, the hatch down", box: [7, 1, 7, 13, 3, 12] },
  { name: 'Smoke room', use: "the tower's foot: the Butcher's smoker, hams on hooks, his chest", box: [16, 1, 7, 18, 5, 9] },
  { name: 'Freezer', use: 'the ice-lined cellar: chests of meat, milk and eggs kept cold, ice and snow stacked', box: [9, -3, 8, 13, -1, 12] },
]

export const OPENINGS = {
  frontDoor: { at: [10, 1, 3], facing: 'south', step: [10, 0, 2] },
  towerDoorway: [[14, 1, 8], [15, 1, 8]], // two high, through the shop's panel and the tower's wall
  hatch: { x: 9, z: 12, facing: 'north' }, // the ladder down to the freezer against its back wall
  windows: [
    { name: 'front west', cells: [[7, 2, 3], [8, 2, 3], [7, 3, 3], [8, 3, 3]], out: 'north' },
    { name: 'front east', cells: [[12, 2, 3], [13, 2, 3], [12, 3, 3], [13, 3, 3]], out: 'north' },
    { name: 'west front', cells: [[6, 2, 4], [6, 3, 4]], out: 'west' },
    { name: 'west middle', cells: [[6, 2, 8], [6, 3, 8]], out: 'west' }, // over the pen
    { name: 'west back', cells: [[6, 2, 12], [6, 3, 12]], out: 'west' },
    { name: 'east front', cells: [[14, 2, 4], [14, 3, 4]], out: 'east' },
    { name: 'east back', cells: [[14, 2, 12], [14, 3, 12]], out: 'east' },
    { name: 'back', cells: [[10, 2, 13], [10, 3, 13]], out: 'south' },
    { name: 'front gable', cells: [[10, 7, 2]], out: 'north', bare: true },
    { name: 'back gable', cells: [[10, 6, 14], [10, 7, 14]], out: 'south', bare: true },
  ],
  vents: [[17, 3, 6], [19, 3, 8], [17, 3, 10], [17, 6, 6], [19, 6, 8], [17, 6, 10]], // iron bars in the tower's north,
  // east and south faces: a draught at the fire's height, the smoke's way out under the cap
}

// Every piece of furniture, footprint first. kind: a furniture.py kind (recorded as a furniture spot) or a fixture
// (a vanilla block the game uses as itself). part: the furnitureParts part that draws it. facing: the way a person
// using it faces.
export const FURNITURE = [
  // Shop: display counters under the front windows (cold stone tops), the counter across the shop with a gap at x 10
  { room: 'Shop', kind: 'counter', part: 'counter', pos: [7, 1, 4], facing: 'north', wood: 'spruce' },
  { room: 'Shop', kind: 'counter', part: 'counter', pos: [8, 1, 4], facing: 'north', wood: 'spruce' },
  { room: 'Shop', kind: 'counter', part: 'counter', pos: [12, 1, 4], facing: 'north', wood: 'spruce' },
  { room: 'Shop', kind: 'counter', part: 'counter', pos: [13, 1, 4], facing: 'north', wood: 'spruce' },
  { room: 'Block', kind: 'counter', part: 'counter', pos: [7, 1, 7], facing: 'north', wood: 'spruce' },
  { room: 'Block', kind: 'counter', part: 'counter', pos: [8, 1, 7], facing: 'north', wood: 'spruce' },
  { room: 'Block', kind: 'counter', part: 'counter', pos: [9, 1, 7], facing: 'north', wood: 'spruce' },
  { room: 'Block', kind: 'counter', part: 'counter', pos: [11, 1, 7], facing: 'north', wood: 'spruce' },
  { room: 'Block', kind: 'counter', part: 'counter', pos: [12, 1, 7], facing: 'north', wood: 'spruce' },
  { room: 'Block', kind: 'counter', part: 'counter', pos: [13, 1, 7], facing: 'north', wood: 'spruce' },
  { room: 'Shop', kind: 'lamp', part: 'hangingLamp', pos: [10, 3, 6], facing: 'north', wood: 'spruce' },
  // Block: the back counters, the chopping block in the middle, the chests, a lamp and the hooks on the back tie
  { room: 'Block', kind: 'counter', part: 'counter', pos: [10, 1, 12], facing: 'south', wood: 'spruce' },
  { room: 'Block', kind: 'counter', part: 'counter', pos: [11, 1, 12], facing: 'south', wood: 'spruce' },
  { room: 'Block', kind: 'counter', part: 'counter', pos: [12, 1, 12], facing: 'south', wood: 'spruce' },
  { room: 'Block', kind: 'fixture', part: 'chest', pos: [13, 1, 12], facing: 'north', block: 'chest' },
  { room: 'Block', kind: 'fixture', part: 'chest', pos: [7, 1, 12], facing: 'east', block: 'chest' },
  { room: 'Block', kind: 'lamp', part: 'hangingLamp', pos: [10, 3, 10], facing: 'north', wood: 'spruce' },
  // Smoke room: the smoker against the east wall, his chest beside it, a lamp from the vault
  { room: 'Smoke room', kind: 'fixture', part: 'stove', pos: [18, 1, 8], facing: 'west', block: 'smoker' },
  { room: 'Smoke room', kind: 'fixture', part: 'chest', pos: [18, 1, 9], facing: 'west', block: 'chest' },
  { room: 'Smoke room', kind: 'lamp', part: 'hangingLamp', pos: [17, 7, 9], facing: 'north', chain: 0, wood: 'spruce' },
  // Freezer: two chests on the east wall (within 4 under the smoker and 8 across), two lamps from the ceiling
  { room: 'Freezer', kind: 'fixture', part: 'chest', pos: [13, -3, 9], facing: 'west', block: 'chest' },
  { room: 'Freezer', kind: 'fixture', part: 'chest', pos: [13, -3, 11], facing: 'west', block: 'chest' },
  { room: 'Freezer', kind: 'lamp', part: 'hangingLamp', pos: [10, -1, 9], facing: 'north', wood: 'spruce' },
  { room: 'Freezer', kind: 'lamp', part: 'hangingLamp', pos: [12, -1, 11], facing: 'north', wood: 'spruce' },
]

// Fixtures that aren't furniture spots: the chopping block, the hooks (chains under a beam), the stacked ice and snow.
export const FITTINGS = {
  choppingBlock: [11, 1, 10],
  hooks: [[8, 3, 10], [9, 3, 10], [12, 3, 10], [13, 3, 10]], // under the back tie beam
  towerHooks: [[16, 7, 7], [17, 7, 7], [18, 7, 7], [16, 7, 9]], // hams in the smoke, three links long, from the vault
  iceStack: [[9, -3, 8, 'snow_block'], [10, -3, 8, 'snow_block'], [9, -2, 8, 'snow_block'], [13, -3, 10, 'packed_ice'], [13, -2, 10, 'packed_ice']],
  freezerHooks: [[11, -1, 8]],
}

// The walking line: the street -> the step -> the door -> through the counter's gap -> the tower doorway -> the
// smoker's front (17, 1, 8); from (10, 1, 11) to the hatch (9, 0, 12) and down the ladder to the freezer floor.
export const WALK = [
  [10, 0, 0], [10, 0, 1], [10, 0, 2], [10, 1, 3], [10, 1, 4], [10, 1, 5], [10, 1, 6], [10, 1, 7], [10, 1, 8],
  [11, 1, 8], [12, 1, 8], [13, 1, 8], [14, 1, 8], [15, 1, 8], [16, 1, 8], [17, 1, 8],
  [10, 1, 9], [9, 1, 10], [9, 1, 11], [9, 1, 12], [9, -1, 12], [9, -3, 12], [10, -3, 12], [12, -3, 10],
]

// Outside: hay under the pen's shelter, a lamp post by the pen's gate, a woodpile for the smoker against the tower.
export const OUTSIDE = {
  hay: [[1, 1, 13], [2, 1, 13], [1, 1, 12], [1, 2, 13]],
  lamp: [4, 1, 1], // on a stone footing at y 0
  woodpile: { x: 20, z0: 6, z1: 10 },
}
