// Crushing Mill, the plan: drawn before any wall (tools/blueprints/briefs/crushing_mill.md). The machine, the water
// that drives it, the rooms, every furniture footprint and the walking line come first; recipe.mjs then assembles the
// mill from the parts library (../parts) to fit this plan.
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. "Crusher shed": one long low stone shed, a shallow gable along x, the wheel outside on the east end in an open
//      leat. Story: a quarry's lean working shed. Safe, but from the street it is a blank wall: the machine is hidden.
//   B. "Timber tipple": a tall timber tower on legs, the belt climbing to wheels in its head, a lean-to below.
//      Story: ore tipped from a height, the old mining way. Striking, but the wheels must sit low (the pit is on the
//      floor) and a leg-tower over 18 tall breaks the plot; the Quarry and Sifting Shed already tell that story.
//   C. "The Fall House": a stone crushing hall with its gable to the street and a wide timber-framed bay that shows the
//      twin wheels straight through the front, a tall square fall tower rising through the back of the west slope with
//      a tiled tank hat on four posts, and a brick furnace stack up the east side. Story: the miners built a head of
//      water first, then the hall against it; the fall turns everything and you see it all from the road.
// Strongest: C. It is the only one where the street reads the machine itself (the wheels framed in the bay like a
// shop window), the silhouette has three heights (hall gable, stack, fall tower) set on a diagonal so neither side is
// heavy, and every kinetic part has a job you can follow by eye: fall -> wheel -> gear wall -> belt and wheels.
//
// Coordinates: x across the front (west 0 -> east 13), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The way in is the door in the middle of the glazed bay in the north gable, up a three-stair step at z = 0.
//
// Masses (named parts the owner's feedback can point at):
//   Hall      stone walls x 1..11, z 1..8, y 1..4 with polished quoins, a dark oak wall plate at y 5; floor y 0.
//             45-degree deepslate roof, ridge along z (y 11), eaves out to x 0 and 12, out over the front at z 0 and the
//             back at z 9. Gables at z 1 (front, the hero face) and z 8: spruce boards between dark oak studs.
//   Bay       the front opening x 5..7, y 1..3, between dark oak jamb posts (x 4 and 8) under a lintel at y 4, glazed
//             like a shop window either side of a spruce door with a transom: the wheels stand 4 blocks behind it.
//   Tower     (dressed bands at y 6 and 12; a sight glass of panes up its back face shows the falling water)
//             the fall tower x 2..4, z 8..10, y 0..13 (stone bricks, polished corners), its shaft at (3, y, 9) open from
//             the sump (y 2) to the poured source (y 13). A hat on four dark oak posts (y 14..15), a tiled cap (y 16..17).
//   Gear wall the back wall's machine band at z 8: the water wheel set in the tower's face (3, 3, 8), a shaft through
//             the tower's corner, then gearboxes and the belt's start pulley in a row along x at y 3, two gearboxes
//             under them at y 2 taking the drive forward to each crushing wheel.
//   Stack     the blast furnace's brick stack x 11..12, z 2..3 up to y 12, its breast inside over the furnace.
//
// The machine (pinned spots from the brief's tests kept exactly):
//   crushing wheels (5, 2, 5) and (7, 2, 5) on axis z, the gap (6, 2, 5) a structure void, the catch pit (6, 1, 5) open
//   over the floor (6, 0, 5), guarded front (6, 1, 4) and back (6, 1, 6) so no villager walks under the crusher.
//   Drive, from the fall: water falls down the shaft (3, 3..12, 9) past the wheel's south side (+x flow score: the
//   wheel at (3, 3, 8) turns at +8 RPM on axis x); shaft (4, 3, 8); gearbox (5, 3, 8, axis z) -> belt start pulley
//   (6, 3, 8) -> gearbox (7, 3, 8, axis z); under each, a gearbox on axis x (5, 2, 8) and (7, 2, 8); shafts (5|7, 2, 6..7)
//   into the wheels. Worked through Create's RotationPropagator rules: west wheel -8, east wheel +8 (each top turning
//   into the gap: the crusher throws down), the belt at -8 on x (items run north, off its end over the gap). Mirrored
//   (Mirror.FRONT_BACK) the same sums give west -8, east +8 again. Stress: one small water wheel (256 su at 8 RPM)
//   against two crushing wheels (2 x 64 su).
//   Belt (6, 3, 8) start -> (6, 3, 7) -> (6, 3, 6) end, facing north: ore on it drops off its end into the gap.
//   Item drain (6, 1, 3) at the pit's front lip, where the crushed ore is rinsed (the brief's "with a drain").
//
// Rooms: one: the crushing floor (x 2..10, z 2..7). West side, the ore stock (coal and cobble heaps, the bench);
// centre, the crusher; east side, the furnace hearth and the Miner's chest.
//
// Walking line: street (z = 0) -> up the step -> the door (6, 1, 1) -> the lip of the pit (x 5 or 7, z 3: 2.5 blocks
// from the gap, feed and collect) -> east to (8, 1, 2..4): the furnace's mouth (9, 1, 2) and the chest (10, 1, 4) both
// within 2. Nothing upstairs.

export const SIZE = [14, 18, 12] // w, h, d (limit 20 x 18 x 16)

// Palette (60/30/10): grey field stone (andesite and cobble, Create's own grey) / dark oak frame + deepslate tile roof
// and spruce boards / brick on the furnace stack and its breast. Why: a working mill in the Create grey, the dark
// frame and roof carrying it to the hills, the one warm brick column saying "fire here".
export const PALETTE = {
  stone: [['cobblestone', 5], ['andesite', 4], ['stone', 2], ['tuff', 1]],
  dressed: 'polished_andesite', // quoins, the crusher bed
  tower: [['stone_bricks', 6], ['cracked_stone_bricks', 1], ['andesite', 2]],
  frame: 'dark_oak_log',
  boards: 'spruce_planks',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  brick: 'bricks',
  brickStairs: 'brick_stairs',
  guard: 'andesite_wall',
  wood: 'spruce',
  fence: 'dark_oak_fence',
  glass: 'glass_pane',
  stepStairs: 'cobblestone_stairs',
}

export const MASSES = {
  hall: { x0: 1, x1: 11, z0: 1, z1: 8, wallTop: 4, plateY: 5 },
  roof: { axis: 'z', across: [0, 12], along: [0, 9], eaveY: 5, steep: false },
  bay: { x0: 5, x1: 7, top: 3, jambs: [4, 8], lintelY: 4 },
  tower: { x0: 2, x1: 4, z0: 8, z1: 10, top: 13, shaft: [3, 9], sumpY: 2, hatPosts: [14, 15], capY: 16, bands: [6, 12], sightGlass: [4, 5, 6, 7, 8, 9, 10, 11] },
  stack: { x0: 11, x1: 12, z0: 2, z1: 3, top: 12 },
  breast: { cells: [[9, 2], [10, 2], [9, 3], [10, 3]], y0: 2, y1: 4, back: [[10, 2], [10, 3]] },
  tieBeams: [3, 6], // z of the tie beams across the hall at the plate (lamps hang from them)
}

export const MACHINE = {
  gap: [6, 2, 5],
  wheelAxis: 'z',
  bedCells: [[5, 6], [7, 6]], // under the axles behind the pit
  guardCells: [[5, 4], [7, 4]], // the low wall either side of the pit's guard, a run of three
  waterWheel: { at: [3, 3, 8], facing: 'east' },
  shafts: [
    { cells: [[4, 3, 8]], axis: 'x' },
    { cells: [[5, 2, 7], [5, 2, 6], [7, 2, 7], [7, 2, 6]], axis: 'z' },
  ],
  drops: [[5, 8], [7, 8]], // twinDrop: the gearboxes at y 3 and y 2
  belt: { cells: [[6, 3, 8], [6, 3, 7], [6, 3, 6]], facing: 'north' },
  drain: [6, 1, 3],
}

export const OPENINGS = {
  bay: { cells: [[5, 6, 7], [1, 3]], z: 1 }, // x 5..7, y 1..3
  door: { at: [6, 1, 1], facing: 'south' }, // the way in, in the middle of the glazed bay
  step: { cells: [[5, 0, 0], [6, 0, 0], [7, 0, 0]], facing: 'south' },
  windows: [
    { name: 'front west', cells: [[2, 2, 1], [3, 2, 1], [2, 3, 1], [3, 3, 1]] },
    { name: 'west', cells: [[1, 2, 3], [1, 3, 3]] },
    { name: 'west back', cells: [[1, 2, 6], [1, 3, 6]] },
    { name: 'east', cells: [[11, 2, 6], [11, 3, 6]] },
    { name: 'front gable', cells: [[6, 7, 1], [6, 8, 1]] },
    { name: 'back gable', cells: [[8, 7, 8]] },
  ],
}

// Every piece of furniture, footprint first. kind: a furniture.py kind (recorded as a furniture spot) or a fixture.
export const FURNITURE = [
  { room: 'Crushing floor', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 6], facing: 'north', wood: 'dark_oak' },
  { room: 'Crushing floor', kind: 'lamp', part: 'hangingLamp', pos: [8, 4, 6], facing: 'north', wood: 'dark_oak' },
  { room: 'Crushing floor', kind: 'lamp', part: 'hangingLamp', pos: [3, 4, 3], facing: 'north', wood: 'dark_oak' },
  { room: 'Crushing floor', kind: 'lamp', part: 'hangingLamp', pos: [8, 4, 3], facing: 'north', wood: 'dark_oak' },
]

export const FIXTURES = {
  blastFurnace: { at: [9, 1, 2], facing: 'west' }, // the Miner's (and Smelter's) job block, pinned
  chest: { at: [10, 1, 4], facing: 'west' }, // against the east wall and the hearth's brick
  bench: { at: [2, 1, 2], block: 'crafting_table' },
  heaps: [['coal_block', [2, 1, 7]], ['coal_block', [3, 1, 7]], ['coal_block', [2, 2, 7]], ['cobblestone', [2, 1, 6]], ['gravel', [3, 1, 6]]],
}

export const OUTSIDE = {
  lamp: [0, 0, 0], // a lamp post at the front west corner, by the step
}
