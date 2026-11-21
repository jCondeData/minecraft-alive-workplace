// Fisher's Hut (fishers_hut, fishers_hut_2), the plan: drawn before any wall (ROADMAP 49, tools/blueprints/REBUILD.md,
// brief tools/blueprints/briefs/fishers_hut.md). recipe.mjs assembles it from the parts library (../parts).
//
// Three rough concepts:
//   A. The Net Loft. A tall, narrow two-storey timber shed, its gable to the water, a loft where the nets hang to dry, a
//      hoist beam over the landing. Story: the gear store of a fishing family. Strong vertical, but alone it is a house
//      with a jetty bolted on: generic.
//   B. The Upturned Boat. The fisher's old boat, too worn to float, turned keel-up on a stone footing above the tide
//      line as his hut: a long red hull on low rubble walls, its black eave ring overhanging them, the keel running out
//      past its end. (After Equihen's "quilles en l'air" and Lindisfarne's boat sheds.)
//   C. The Stilt Shack. A plank shack on piles at the tide line with a wrap-round boardwalk. Lively, but the brief puts
//      the building on land (at most a tenth over water), so its stilts would stand on grass and read as a mistake.
//   Rounds 1-2 built B alone; the critic (round 2: silhouette 4, depth 4, interior 3, detail 3) found it a one-storey
//   box whose outline only the mast carried. Round 3 (the last) builds B + A: the upturned boat is the fisher's cabin,
//   and beside it, where the jetty lands, stands his net shop (after Hastings' tall net shops and Lofoten's red rorbuer):
//   a two-storey tower, rubble below and red-painted boards in a black frame above, its loft jettied out over the
//   landing on brackets, a tarred gable roof to the water with the loft door and a hoist beam in it. A low red hull
//   beside a tall red-and-black net shop: an outline you know without any mast.
//
// Round 3, the critic's notes and the answers:
//   1. Back and sides flat cobble -> every face has a frame proud of its wall: on the hull, dark posts a block out
//      under the eave ring at each end of the long walls' bay, front and back, and the bow window between two jamb
//      posts; on the net shop, proud corner posts on the east and back faces carrying a girt (a log beam a block out at
//      the loft floor) that the loft stands on, studs between the loft windows. Every hull and ground-floor window has
//      a dark sill a block out (the loft windows sit on the girts); the eave ring overhangs the hull's back, front and
//      bow by a block; the net shop's roof overhangs every side by a block; the smokehouse's east face has two proud
//      posts and a sill.
//   2. One-storey box, mast does the work -> the net shop: a second storey (the net loft, a ladder up from the work
//      room) under a roof whose ridge (y 11) stands nearly twice the hull's keel (y 6). The mast is gone.
//   3. Level II only a lean-to -> a real smokehouse behind the net shop with its own lower gable roof (ridge y 7 vs the
//      tower's 11) running back from the tower; its back gable carries a rubble chimney rising from right behind the
//      smoker past the ridge, smoking; fish (dried kelp) hung in the smoke from the roof; a side door with its step and
//      lamp, a drying rack (hjell) and a woodpile of fuel in the yard.
//   4. Two woods -> dark oak (frame, roofs, jetty, floors, joinery, furniture) and mangrove (the hull's red bottom, the
//      net loft's red boards and gables). No spruce.
//   5. A working corner by the barrel -> the net shop's ground floor IS the work room, its door straight off the jetty's
//      landing: the barrel just inside the door, rods on hooks over it, the gutting table (a stone-topped counter)
//      under the windows to the water and the east, a crate of the catch beside it and another on the landing by the
//      jetty door, the store chest, the ladder to the nets behind; the cabin (bunk, table, cupboard) through an open
//      doorway west.
//
// Coordinates: x across the front (west 0 -> east 12), y up (0 = the layer on the clicked block, the water's surface
// one below it), z from the front (z = 0, the jetty's end over the water) to the back. Facing words are Minecraft's.
//
// Masses (named parts the owner's feedback can point at):
//   Jetty      rows z 0..4, x 4..6: a deck of top slabs at y 0 on four log piles (the end pair rising as lit mooring
//              piles, the inner pair capped as bollards). The Fisherman walks out along x 5 and casts off its end.
//   Landing    z 5..6, x 4..10: a board deck where the jetty meets the land, under the net loft's jetty; the catch
//              crate and the fisher's bench under it, a lantern hung from the loft beam over the jetty door.
//   Hull       the cabin (west): rubble walls on an oval plan (long walls z 7 and z 11, x 3..6; steps at x 2 and x 1),
//              the old boat keel-up on them (keel along x on z 9, y 6), its black eave ring overhanging by a block, the
//              keel run out past the west end (x 0). Proud posts under the ring at x 3 and x 6, front and back.
//   Net shop   the tower (east): walls x 7..11, z 7..11. Ground storey rubble (y 1..3) with dark corner posts; the floor
//              beam at y 4, a girt a block out on the east and back faces on proud posts; the loft (y 5..7) red boards
//              in a black frame, jettied a block out over the landing (z 6) on brackets; a wall plate at y 8; a tarred
//              45-degree gable roof, ridge along z on x 9 at y 11, overhanging every side (x 6..12, z 5..12). Loft door,
//              and a hoist beam in the water gable.
//   Level II   (Level I kept but for the back door's step and two post feet, now the smokehouse's floor and walls)
//   Smokehouse behind the net shop: rubble walls x 7..11, z 12..16, a wall plate at y 4, its own gable roof (dark oak,
//              45 degrees, ridge along z on x 9 at y 7, overhanging to z 17), the net shop's back door its way in and a
//              side door (west) from the yard. The smoker against the back gable, the chimney rising behind it.
//   Stack      rubble in the back gable (x 9, z 16) from the ground to y 10, a breast a block out (z 17), smoking.
//   Yard       a hjell (x 3, z 15..18) with fish (dried kelp) hung on chains, a woodpile of fuel against the
//              smokehouse's west wall, a step and a lantern at the side door.
export const LEVELS = {
  1: { size: [13, 12, 13] },
  2: { size: [13, 12, 19] },
}

// Palette (60/30/10): rubble (cobble, mossy, stone, andesite) / red mangrove (the hull's bottom, the loft's boards) /
// black dark oak (frame, eave ring, tarred roofs, trim). Two woods: dark oak and mangrove. Why: red over black reads as
// boats and fishermen's sheds, and grey rubble puts them on the shore.
export const PALETTE = {
  hull: { side: null, turn: 'dark_oak_stairs', full: 'mangrove_planks', stairs: 'mangrove_stairs', slab: 'mangrove_slab', keel: 'dark_oak_log' },
  stone: [['cobblestone', 5], ['mossy_cobblestone', 2], ['stone', 2], ['andesite', 1]],
  post: 'dark_oak_log',
  boards: 'mangrove_planks', // the net loft's red-painted boards (a rorbu's)
  sill: 'dark_oak_slab',
  floor: 'dark_oak_planks',
  smokeFloor: 'stone_bricks',
  roof: { full: 'dark_oak_planks', stairs: 'dark_oak_stairs', slab: 'dark_oak_slab' }, // the net shop, tarred
  smokeRoof: { full: 'dark_oak_planks', stairs: 'dark_oak_stairs', slab: 'dark_oak_slab' }, // the smokehouse, sooty
  barge: 'dark_oak_stairs',
  bracket: 'dark_oak_stairs',
  jetty: { deck: 'dark_oak_slab', pile: 'dark_oak_log', cap: 'dark_oak_fence' },
  landing: 'dark_oak_planks',
  rack: { post: 'dark_oak_fence', beam: 'dark_oak_log', hang: 'dried_kelp_block', link: 'chain', links: 1 },
  door: 'dark_oak',
  wood: 'dark_oak', // furniture
  glass: 'glass_pane',
  path: 'dirt_path',
}

export const MASSES = {
  jetty: { x0: 4, x1: 6, z0: 0, z1: 4, piles: [[4, 0, 1], [6, 0, 1], [4, 3, 0], [6, 3, 0]] }, // [x, z, rise]
  landing: { x0: 4, x1: 10, z0: 5, z1: 6 },
  bench: { x0: 10, x1: 10, y: 1, z: 6, facing: 'north' }, // under the loft, looking out to sea
  hull: {
    axis: 'x', along: [1, 6], centre: 9, wall: 3, inset: true,
    width: x => (x < 1 ? 0 : Math.min(x, 3)), // 1, 2, 3, 3, 3, 3: the bow west, the stern against the net shop
    keelEnd: 0,
    // proud posts: one at each end of the long walls' bay, a block out under the eave ring, front and back (the bow's
    // stepped rubble is left bare, so the oval reads)
    posts: [[1, 8], [1, 10], [3, 6], [6, 6], [3, 12], [6, 12]], // [1, 8] and [1, 10]: the bow window's jambs
  },
  shop: {
    x0: 7, x1: 11, z0: 7, z1: 11, loftZ0: 6, floorY: 4, plateY: 8,
    roof: { axis: 'z', across: [6, 12], along: [5, 12], eaveY: 8 },
    brackets: [8, 10], // under the loft's jetty
    eastPosts: [7, 11], // ground-storey posts a block proud of the east face (x 12) under a girt that carries the loft
    backPosts: [7, 11], // the same on the back face (z 12)
    hoist: { x: 9, y: 10, z: 5, chain: 2 },
  },
  // Level II
  smokehouse: { x0: 7, x1: 11, z0: 12, z1: 16, plateY: 4, roof: { axis: 'z', across: [6, 12], along: [12, 17], eaveY: 4 } },
  stack: { x: 9, z: 16, top: 10 },
  rack: { axis: 'z', line: 3, span: [15, 18], posts: [15, 18], bundles: [16, 17], top: 3 },
  woodpile: { x: 6, z0: 14, z1: 16 },
}

export const ROOMS = [
  { name: 'Work room', use: 'the net shop ground floor: barrel, rods, gutting table, catch, ladder to the loft', box: [8, 1, 8, 10, 3, 10] },
  { name: 'Net loft', use: 'the nets and lines, dried kelp, a lamp; ladder from the work room', box: [8, 5, 7, 10, 7, 10] },
  { name: 'Cabin', use: "the fisher's bunk, table and cupboard under the boat", box: [2, 1, 8, 6, 5, 10] },
  { name: 'Smokehouse', use: 'the smoker under the chimney, the store chest, fish hung in the smoke (Level II)', box: [8, 1, 12, 10, 5, 15], level: 2 },
]

export const OPENINGS = {
  jettyDoor: { at: [9, 1, 7], facing: 'south' }, // the net shop's door off the landing
  backDoor: { at: [9, 1, 11], facing: 'north' }, // to the land behind (Level II: into the smokehouse)
  cabinDoor: [7, 1, 9], // an open doorway from the work room into the cabin
  loftDoor: { at: [9, 5, 6], facing: 'south' },
  // two-high windows: [x, y, z, out]
  windows: [[4, 2, 7, 'north', 'potted_fern'], [5, 2, 7, 'north', 'potted_fern'], [4, 2, 11, 'south', 'potted_fern'], [5, 2, 11, 'south', 'potted_fern'], [1, 2, 9, 'west'], [11, 2, 8, 'east'], [11, 2, 10, 'east'],
    [11, 5, 8, 'east'], [11, 5, 10, 'east'], [9, 5, 11, 'south']],
  gable: [], // the water gable: boards behind the hoist's tackle
  smokeDoor: { at: [7, 1, 13], facing: 'east', level: 2 },
  smokeWindows: [[11, 2, 14, 'east']],
}

// Every piece of furniture, footprint first (see house_t1_b/plan.mjs for the fields).
export const FURNITURE = [
  // Work room: the job barrel by the jetty door, the gutting table under the east window, the store chest, the ladder
  { room: 'Work room', kind: 'fixture', part: 'pantry', pos: [8, 1, 8], facing: 'north', block: 'barrel' },
  { room: 'Work room', kind: 'counter', part: 'counter', pos: [10, 1, 8], facing: 'west', wood: 'dark_oak', cells: [[0, 0, 0], [0, 1, 0]] },
  { room: 'Work room', kind: 'fixture', part: 'chest', pos: [8, 1, 10], facing: 'east', block: 'chest' },
  { room: 'Work room', kind: 'lamp', part: 'hangingLamp', pos: [9, 3, 9], facing: 'north', chain: 0, wood: 'dark_oak' },
  // Net loft: a mending desk under the east window, its stool, coils of net (kelp blocks, recipe) along the west wall
  { room: 'Net loft', kind: 'desk', part: 'desk', pos: [10, 5, 8], facing: 'west', wood: 'dark_oak' },
  { room: 'Net loft', kind: 'chair', part: 'chair', pos: [9, 5, 8], facing: 'east', wood: 'dark_oak' },
  { room: 'Net loft', kind: 'lamp', part: 'hangingLamp', pos: [9, 8, 9], facing: 'north', chain: 2, wood: 'dark_oak' },
  // Cabin: the bunk along the back wall, a table and chair under the front window, the cupboard in the bow
  { room: 'Cabin', kind: 'bed', part: 'bed', pos: [4, 1, 10], facing: 'west', colour: 'blue' },
  { room: 'Cabin', kind: 'nightstand', part: 'nightstand', pos: [5, 1, 10], facing: 'north', wood: 'dark_oak' },
  { room: 'Cabin', kind: 'table', part: 'table', pos: [4, 1, 8], facing: 'north', wood: 'dark_oak', colour: 'white', cells: [[0, 0, 0], [0, 1, 0]] },
  { room: 'Cabin', kind: 'chair', part: 'chair', pos: [5, 1, 8], facing: 'west', wood: 'dark_oak' },
  { room: 'Cabin', kind: 'cupboard', part: 'cupboard', pos: [2, 1, 9], facing: 'east', wood: 'dark_oak', cells: [[0, 0, 0], [0, 1, 0]] },
  { room: 'Cabin', kind: 'rug', part: 'rug', cellsAbs: [[3, 1, 9], [4, 1, 9]], colour: 'light_blue' },
  { room: 'Cabin', kind: 'lamp', part: 'hangingLamp', pos: [4, 4, 9], facing: 'north', chain: 1, wood: 'dark_oak' },
  // Smokehouse (Level II): the smoker against the back gable under the stack, the store chest beside it, a lamp
  { room: 'Smokehouse', level: 2, kind: 'fixture', part: 'stove', pos: [9, 1, 15], facing: 'north', block: 'smoker' },
  { room: 'Smokehouse', level: 2, kind: 'fixture', part: 'chest', pos: [10, 1, 15], facing: 'north', block: 'chest' },
  { room: 'Smokehouse', level: 2, kind: 'counter', part: 'counter', pos: [8, 1, 15], facing: 'north', wood: 'dark_oak', cells: [[0, 0, 0], [0, 1, 0]] }, // the salting bench
  { room: 'Smokehouse', level: 2, kind: 'lamp', part: 'hangingLamp', pos: [9, 4, 13], facing: 'north', chain: 2, wood: 'dark_oak' },
]

// The walking line: out along the jetty to its end, back to the landing, east under the loft to the jetty door, the
// barrel at the door's left, on to the back door; the cabin through the open doorway west; Level II through the back
// door into the smokehouse and out of its side door, round the west to the land behind (workers wait at (1, 0, d + 1)).
export const WALK = [
  [5, 1, 0], [5, 1, 4], [5, 1, 5], [8, 1, 5], [9, 1, 6], [9, 1, 7], [9, 1, 8], // barrel at (8, 1, 8)
  [9, 1, 9], [9, 1, 10], [9, 1, 11], [9, 1, 12], // Level I: out the back door; Level II: into the smokehouse
  [8, 1, 13], [7, 1, 13], [6, 1, 13], [1, 1, 13], [1, 0, 19],
]
