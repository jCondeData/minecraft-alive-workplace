// Fisher's Hut (fishers_hut, fishers_hut_2), the plan: drawn before any wall (ROADMAP 49, tools/blueprints/REBUILD.md,
// brief tools/blueprints/briefs/fishers_hut.md). recipe.mjs assembles it from the parts library (../parts).
//
// Three rough concepts:
//   A. The Net Loft. A tall, narrow two-storey timber shed, its steep gable to the water, a gallery round the loft where
//      the nets hang to dry, a hoist beam over the jetty. Story: the gear store of a fishing family. Strong vertical,
//      but it is a house with a jetty bolted on: generic.
//   B. The Upturned Boat. The fisher's old boat, too worn to float, turned keel-up on a stone footing above the tide
//      line as his hut: a long red hull resting on low rubble walls, its black eave ring overhanging them, the keel
//      running out past both ends, a door cut in its flank under a little dark pent, the jetty running straight out
//      from it and the boat's old mast standing beside it as a lit flagpole. Level II: his old dinghy turned over the
//      same way as the roof of a smokehouse built onto the back, a stone stack behind it, a fish-drying rack (a hjell). (After Equihen's "quilles en l'air", boats keel-up on walls, and Lindisfarne's boat sheds.)
//   C. The Stilt Shack. A plank shack on piles at the tide line with a wrap-round boardwalk, the jetty its boardwalk run
//      out over the water, nets on poles. Story: a camp built where the land runs out. Lively, but the brief puts the
//      building on land (at most a tenth over water), so its stilts would stand on grass and read as a mistake.
//   B is the strongest: the silhouette says "fisherman" from across the village before any detail does, the colours
//   tell its story (red antifouling on the old bottom, now the roof; its black rubbing strake, now the eave ring; the
//   black keel on top), the mast gives the low hull a tall partner, and Level II repeats the idea at a smaller scale instead of bolting on a shed. Built: B.
//
// Coordinates: x across the front (west 0 -> east 12), y up (0 = the layer on the clicked block, the water's surface
// one below it), z from the front (z = 0, the jetty's end over the water) to the back. Facing words are Minecraft's.
//
// Masses (named parts the owner's feedback can point at):
//   Jetty      rows z 0..4, x 4..6: a deck of top slabs at y 0 on four log piles (the end pair rising as lit mooring
//              piles, the inner pair capped as bollards). The Fisherman walks out along x 5 and casts off its end.
//   Porch      z 5..6, x 4..6: a plank stoop where the deck meets the land, a pent of dark stairs flaring out under
//              the eave over the front door, a lantern hanging either side; the fisher's bench (x 9..10) looks to sea.
//   Walls      rubble, 3 high (y 1..3), on an oval plan stepping in to the ends: the long walls z 7 and z 11 (x 3..9),
//              then z 8 / z 10 (x 2, 10), then the end walls (x 1, 11) with a window each; a proud dark log post at
//              every step's corner. Two-high windows with dark sills.
//   Hull       the main mass, the roof: the old boat keel-up, keel along x on z 9, resting on the walls. A black eave
//              ring (dark oak stairs, y 4) overhangs every wall by a block, the red bottom planking rises to the keel
//              log at y 6 midships, drawing in and coming down toward both ends; the keel runs out a block past each
//              end wall (x 0, 12) as the stem and sternpost.
//   Mast       the boat's old mast stood up at the front east corner (x 11, z 6) to y 9, a yard across it, lanterns
//              hung from the yard and one on its head: a light for boats coming in.
//   Level II   (Level I untouched)
//   Smokehouse built onto the back (walls x 3..7, z 12..16, the hut's back door its way in), roofed with the old
//              dinghy keel-up (keel along z on x 5, its eave ring over the walls, its bow running into the hut's roof,
//              its stern overhanging at z 17): the smoker against the back wall, the store chest beside it, a side door
//              to the land, a window to the east.
//   Stack      a stone chimney behind the smokehouse (x 5, z 18), smoking.
//   Hjell      the drying rack east of the smokehouse (x 10, z 13..17): fence posts, a log beam, bundles of dried kelp
//              hung on chains; a woodpile of fuel against the smokehouse (x 8).
//   Yard       a trodden path round the west side from the land behind to the smokehouse's side door.
export const LEVELS = {
  1: { size: [13, 11, 13] },
  2: { size: [13, 11, 19] },
}

// Palette (60/30/10): rubble walls (cobble, mossy, stone) / the hull's old bottom in red mangrove (antifouling paint) /
// black dark oak (eave ring, keel, corner posts, sills, porch pent); spruce for the jetty, mast, floors and joinery.
// Two woods on the building. Why: the boat reads by its colours, red over the black line, and grey walls under it
// put it on the shore; the dark lines draw the oval and the overhang.
export const PALETTE = {
  hull: {
    side: null, // the walls: the stone mix (recipe)
    turn: 'dark_oak_stairs', full: 'mangrove_planks', stairs: 'mangrove_stairs', slab: 'mangrove_slab', keel: 'dark_oak_log',
  },
  stone: [['cobblestone', 5], ['mossy_cobblestone', 2], ['stone', 2], ['andesite', 1]],
  post: 'dark_oak_log',
  sill: 'dark_oak_slab',
  floor: 'spruce_planks',
  smokeFloor: 'stone_bricks',
  jetty: { deck: 'spruce_slab', pile: 'spruce_log', cap: 'spruce_fence' },
  porch: { floor: 'spruce_planks', pent: 'dark_oak_stairs' },
  mast: { spar: 'spruce_log', yard: 'spruce_fence' },
  rack: { post: 'spruce_fence', beam: 'spruce_log', hang: 'dried_kelp_block', link: 'chain', links: 1 },
  door: 'dark_oak',
  wood: 'spruce', // furniture
  glass: 'glass_pane',
  path: 'dirt_path',
}

export const MASSES = {
  jetty: { x0: 4, x1: 6, z0: 0, z1: 4, piles: [[4, 0, 1], [6, 0, 1], [4, 3, 0], [6, 3, 0]] }, // [x, z, rise]
  porch: { x0: 4, x1: 6, z0: 5, z1: 6, pentY: 3 },
  bench: { x0: 9, x1: 10, y: 1, z: 6, facing: 'north' }, // the fisher's bench under the eave, looking out to sea
  hull: {
    axis: 'x', along: [1, 11], centre: 9, wall: 3, inset: true,
    width: x => (x < 1 || x > 11 ? 0 : Math.min(x, 12 - x, 3)), // 1, 2, 3 ... 3, 2, 1
    keelEnds: [0, 12], // the keel run out past the end walls
  },
  // posts at the oval's steps: [x, z]
  posts: [[2, 7], [10, 7], [2, 11], [10, 11], [1, 8], [11, 8], [1, 10], [11, 10]],
  mast: { x: 11, z: 6, top: 9, yardY: 8, yard: [10, 12] },
  // Level II
  smokehouse: { axis: 'z', along: [12, 17], centre: 5, wall: 3, inset: true, walls: [12, 16] }, // walls: front (open to the hut) and back rows
  stack: { x: 5, z: 18, top: 8 },
  rack: { axis: 'z', line: 10, span: [13, 17], posts: [13, 15, 17], bundles: [14, 16], top: 4 },
  woodpile: { x: 8, z0: 13, z1: 15 },
}

export const ROOMS = [
  { name: 'Hut', use: "the fisher's gear end (west), his bunk and table (east), a passage along the keel", box: [2, 1, 8, 10, 5, 10] },
  { name: 'Smokehouse', use: 'the smoker and the store of smoked fish (Level II)', box: [4, 1, 12, 6, 5, 15], level: 2 },
]

export const OPENINGS = {
  frontDoor: { at: [5, 1, 7], facing: 'south' },
  backDoor: { at: [5, 1, 11], facing: 'north' },
  // two-high windows: [x, z, out]
  windows: [[3, 7, 'north'], [7, 7, 'north'], [8, 11, 'south'], [1, 9, 'west'], [11, 9, 'east']],
  smokeDoor: { at: [3, 1, 14], facing: 'east', level: 2 },
  smokeWindows: [[7, 14, 'east']],
}

// Every piece of furniture, footprint first (see house_t1_b/plan.mjs for the fields).
export const FURNITURE = [
  // the gear end (west): the job barrel by the back door, the sea chest under the front window, a gutting bench
  // under the end window
  { room: 'Hut', kind: 'fixture', part: 'pantry', pos: [3, 1, 10], facing: 'north', block: 'barrel' },
  { room: 'Hut', kind: 'fixture', part: 'chest', pos: [3, 1, 8], facing: 'south', block: 'chest' },
  { room: 'Hut', kind: 'counter', part: 'counter', pos: [2, 1, 9], facing: 'east', wood: 'spruce', cells: [[0, 0, 0], [0, 1, 0]] },
  { room: 'Hut', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 9], facing: 'north', chain: 1, wood: 'spruce' },
  // the cabin end (east): a bunk under the back window with a candle by it, a table and chair under the front window,
  // a locker in the end
  { room: 'Hut', kind: 'bed', part: 'bed', pos: [7, 1, 10], facing: 'east', colour: 'blue' },
  { room: 'Hut', kind: 'nightstand', part: 'nightstand', pos: [9, 1, 10], facing: 'west', wood: 'spruce' },
  { room: 'Hut', kind: 'table', part: 'table', pos: [8, 1, 8], facing: 'north', wood: 'spruce', colour: 'white', cells: [[0, 0, 0], [0, 1, 0]] },
  { room: 'Hut', kind: 'chair', part: 'chair', pos: [7, 1, 8], facing: 'east', wood: 'spruce' },
  { room: 'Hut', kind: 'cupboard', part: 'cupboard', pos: [10, 1, 9], facing: 'west', wood: 'spruce', cells: [[0, 0, 0], [0, 1, 0]] },
  { room: 'Hut', kind: 'lamp', part: 'hangingLamp', pos: [8, 4, 9], facing: 'north', chain: 1, wood: 'spruce' },
  // Smokehouse (Level II): the smoker against the back wall, the store chest beside it, a lamp under the keel
  { room: 'Smokehouse', level: 2, kind: 'fixture', part: 'stove', pos: [5, 1, 15], facing: 'north', block: 'smoker' },
  { room: 'Smokehouse', level: 2, kind: 'fixture', part: 'chest', pos: [6, 1, 15], facing: 'north', block: 'chest' },
  { room: 'Smokehouse', level: 2, kind: 'lamp', part: 'hangingLamp', pos: [5, 4, 14], facing: 'north', chain: 1, wood: 'spruce' },
]

// The walking line: out along the jetty to its end, back through the porch and the front door, along the keel to
// the barrel and the back door; Level II through the back door into the smokehouse and out of its side door, round
// the west side to the land behind (the arriving workers wait at (1, 0, depth + 1)).
export const WALK = [
  [5, 1, 0], [5, 1, 2], [5, 1, 4], [5, 1, 5], [5, 1, 6], [5, 1, 7], [5, 1, 8], [4, 1, 9], [4, 1, 10], // barrel at (3, 1, 10)
  [5, 1, 10], [5, 1, 11], [5, 1, 12], [5, 1, 14], // Level II: the smoker at (5, 1, 15)
  [4, 1, 14], [3, 1, 14], [2, 1, 14], [1, 1, 14], [1, 1, 18], [1, 0, 19],
]
