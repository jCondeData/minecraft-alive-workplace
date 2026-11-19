// Berry Farm (berry_farm, berry_farm_2), the plan: drawn before any wall. The garden's beds, the keeper's walking line,
// every opening and every piece the keeper uses come first; recipe.mjs then assembles both levels from the parts
// library (../parts) to fit this plan. Brief: tools/blueprints/briefs/berry_farm.md.
//
// Three rough concepts (massing, roof, plan, story):
//   A. Bramble Terraces: three stone-walled terraces stepping up the plot to a lean-to picking shed at the top; one
//      mono-pitch roof; a stair up the middle. A hillside picking garden. Costly in fill stone, a climb for the keeper,
//      and Level II has nowhere to go but up another terrace.
//   B. The Lantern Arbor (chosen): a timber arbor walk from a gateway in a clipped hedge straight up the middle of the
//      garden to a small steep-gabled picking lodge, berry beds raised in log kerbs either side; Level II roofs the
//      whole garden in a rafter lattice hung with glow berries and opens the lodge's back door onto a second, glowing
//      cloister behind it. Roofs: one steep red gable (the hero, ridge 11) over a low flat lattice (rafters at 4), a
//      pent roof between them over the picking porch. A keeper's garden that, once it grows, is lit by its own fruit.
//   C. The Bramble Ring: a round hedge with bushes in concentric rings round a pyramid-roofed pavilion. A cottage maze.
//      The composter's fixed cell (4, 1, 7) sits off any circle's centre on a 13 grid, a round plan reads jagged at
//      this size, and every ring needs a gap for the keeper.
// B is the strongest: it is the only one whose Level II changes what the building *is* (the berries become its
// light), its hero gable sits on the axis the keeper walks, and the plan follows his day exactly: in at the gate, pick
// both sides of the walk, tip the basket into the chest at the walk's end. A and C are a shed and a garden; B is a
// place.
//
// Story: the Orchard Keeper grows brambles the way a monk grows a cloister garden. A clipped azalea hedge, a gateway
// of spruce posts, a walk of beaten earth under an open arbor to his lodge: a narrow spruce-framed store of pale birch
// boards under a steep roof of red mangrove shingles, berry-coloured. In front of it, under a pent roof, the composter
// and the harvest chest stand at the end of the walk like an altar at the end of a nave. At Level II the arbor spreads
// into a lattice over the whole garden, glow berries hang from every second rafter, and the lodge's back door opens on
// a second cloister of beds under the same lattice: at night the garden is lit by what grows in it.
//
// Coordinates: x across the front (west 0 -> east 12), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The keeper walks in at the front (z = 0) through the open gateway (no gate: villagers
// don't open fence gates).
//
// Named parts (the owner's feedback can point at them):
//   Hedge      clipped azalea round the plot, one high, two at the corners and the gateway's cheeks; open at x 5..7.
//   Gateway    spruce posts at x 4 and 8 on the front line, the arbor's first beam over them, a lantern hung under it.
//   Arbor      posts at x 4 and 8 (z 0 and 4), beams across at y 4 (z 0: the gateway's lintel; z 4), rafters
//              front-to-back at y 5 on every second column (x 3, 5, 7, 9; open sky between); II runs the beams out to
//              the hedge on posts at x 0 and 12 and adds rafters at x 1 and 11 over the outer beds.
//   Walk       beaten earth (dirt path) x 5..7, z 0..5, three wide, straight to the chest.
//   Beds       raised podzol in stripped-spruce kerbs, ripe sweet berry bushes: west x 1..3 and east x 9..11 (z 1..4)
//              in front; the lodge's flank beds x 1..2 (west) and x 11 (east), z 9..11.
//   Porch      the picking porch z 6..7, a band of stone bricks x 1..11, a pent roof (x 3..9, y 3) along the lodge's
//              front over the composter (4, 1, 7), the harvest chest (6, 1, 7) facing the walk and a hay bale (8, 1, 7);
//              two lanterns hung from the last rafter light it.
//   Lodge      the store: walls x 3..9, z 8..11 (inside x 4..8, z 9..10), spruce frame, birch boards, a band of
//              windows at y 2, a steep mangrove gable roof (ridge along z, y 11) kicked out at its eaves in a spruce
//              trim line (bell-cast, down to y 3 over the flank beds), its gables facing front and back,
//              its side door on the east (9, 1, 10) off the east path.
//   Cloister   (II only) z 12..15 behind the lodge, reached through the lodge's back door (6, 1, 11) or the east path:
//              a walk x 5..7 and a cross path at z 15, beds x 1..3, 9 and 11 (z 12..14), the back hedge at z 16,
//              the same lattice and glow berries.

export const SIZES = { 1: [13, 12, 13], 2: [13, 12, 17] } // w, h, d (I: limit 13 x 16 x 13)

// Palette (60/30/10): the garden's green and earth (grass, azalea hedge, podzol, bushes, the earth walk) / spruce
// timber and pale birch boards (frame, arbor, kerbs, walls) / berry red (the mangrove roof and the fruit) with the
// lanterns' and glow berries' warm light. Why: the red roof reads as the farm's fruit from across the village, the
// pale boards keep a small store light among dark bushes, and spruce ties lodge and arbor into one frame.
export const PALETTE = {
  ground: 'grass_block',
  under: 'dirt', // under the raised beds and the hedge's ends
  soil: 'podzol', // the beds: a bush needs grass, dirt, podzol, coarse dirt or farmland
  walk: 'dirt_path',
  porch: [['stone_bricks', 6], ['mossy_stone_bricks', 2], ['cracked_stone_bricks', 1]], // (weights summing to a power of two defeat mix()'s hash: keep the totals odd)
  plinth: [['cobblestone', 6], ['mossy_cobblestone', 2], ['andesite', 1]],
  hedge: [['azalea_leaves', 5], ['flowering_azalea_leaves', 2]],
  frame: 'spruce_log',
  poles: 'stripped_spruce_log', // the arbor's beams and rafters: peeled poles, paler than the lodge's frame
  kerb: 'stripped_spruce_log',
  infill: 'birch_planks',
  floor: 'spruce_planks',
  roof: { full: 'mangrove_planks', stairs: 'mangrove_stairs', slab: 'mangrove_slab' },
  wood: 'spruce', // door, rafter ends (stairs), barge boards
  glass: 'glass_pane',
  bush: 'sweet_berry_bush',
  vine: { tip: 'cave_vines', stem: 'cave_vines_plant' },
}

// Masses and the frame's lines (shared by both levels; II adds to them, never moves them)
export const LODGE = {
  walls: { x0: 3, x1: 9, z0: 8, z1: 11 }, room: { x0: 4, x1: 8, z0: 9, z1: 10 },
  wallTop: 3, beamY: 4,
  roof: { axis: 'z', across: [1, 11], along: [7, 12], eaveY: 3, steep: true }, // bell-cast: kick at 1 and 11 (y 3), ridge y 11
  // the band of windows at y 2 (boards at y 1 and 3): front either side of the king post, back, west, east
  windows: [
    { cells: [[4, 2, 8], [5, 2, 8]], out: 'north' }, { cells: [[7, 2, 8], [8, 2, 8]], out: 'north' },
    { cells: [[4, 2, 11], [5, 2, 11]], out: 'south' }, { cells: [[7, 2, 11], [8, 2, 11]], out: 'south' },
    { cells: [[3, 2, 9], [3, 2, 10]], out: 'west' }, { cells: [[9, 2, 9]], out: 'east' },
  ],
  dormers: [{ z: 10, face: 3, eave: 2, y0: 5 }, { z: 10, face: 9, eave: 10, y0: 5 }], // on the wall lines, z 8..12
  gableGlass: [[6, 5], [6, 6]], // a tall slit up each gable's king post line, lighting the store
  posts: [[3, 8], [6, 8], [9, 8], [3, 11], [6, 11], [9, 11]],
}

export const ARBOR = {
  // posts y 1..3, beams across (along x) at y 4 tapered at their ends, rafters front-to-back (along z) at y 5 on every
  // second column: from the front the arbor reads as a gateway beam and a row of rafter ends, open sky between them
  1: {
    posts: [[4, 0], [8, 0], [4, 4], [8, 4]], beamY: 4,
    beams: [0, 4].map(z => ({ axis: 'x', at: z, a: 3, b: 9, taper: 'ab' })),
    rafters: [3, 5, 7, 9].map(x => ({ axis: 'z', at: x, a: 0, b: 5, taper: '' })),
  },
  2: { // the lattice: the beams run out to the hedge on posts at x 0 and 12, two more rafters over the outer beds
    posts: [[0, 0], [12, 0], [0, 4], [12, 4]], beamY: 4,
    beams: [0, 4].map(z => ({ axis: 'x', at: z, a: 0, b: 12 })),
    rafters: [1, 11].map(x => ({ axis: 'z', at: x, a: 0, b: 5, taper: '' })),
  },
  cloister: { // II: behind the lodge, beams on the back hedge line and one row off the lodge's back eave
    posts: [0, 4, 8, 12].flatMap(x => [[x, 13], [x, 16]]), beamY: 4,
    beams: [13, 16].map(z => ({ axis: 'x', at: z, a: 0, b: 12 })),
    rafters: [1, 3, 5, 7, 9, 11].map(x => ({ axis: 'z', at: x, a: 13, b: 16, taper: '' })),
  },
}

// Beds: soil cells [x, z] (raised: soil at y 1, a ripe bush on it at y 2) and their kerbs (stripped logs at y 1)
const box = (x0, x1, z0, z1) => { const o = []; for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) o.push([x, z]); return o }
export const BEDS = {
  1: [
    { name: 'front west', cells: box(1, 3, 1, 4) }, { name: 'front east', cells: box(9, 11, 1, 4) },
    { name: 'flank west', cells: box(1, 2, 9, 11) }, { name: 'flank east', cells: box(11, 11, 9, 11) },
  ],
  2: [ // the cloister's beds, the flank beds running on into them
    { name: 'cloister west', cells: box(1, 3, 12, 14) }, { name: 'cloister mid east', cells: box(9, 9, 12, 14) },
    { name: 'cloister east', cells: box(11, 11, 12, 14) },
  ],
}
export const KERBS = {
  1: [
    { axis: 'z', at: 4, a: 1, b: 5 }, { axis: 'x', at: 5, a: 1, b: 3 }, // front west: along the walk, along the porch
    { axis: 'z', at: 8, a: 1, b: 5 }, { axis: 'x', at: 5, a: 9, b: 11 },
    { axis: 'x', at: 8, a: 1, b: 2 }, { axis: 'x', at: 8, a: 11, b: 11 }, // the flank beds' ends on the porch
  ],
  2: [{ axis: 'z', at: 4, a: 12, b: 14 }, { axis: 'z', at: 8, a: 12, b: 14 }],
}

// Ground at y 0 (everything not listed is grass): the walk, the porch band, the paths round the lodge
export const GROUND = {
  walk: [box(5, 7, 0, 5)],
  porch: box(1, 11, 6, 7),
  paths: { 1: [box(10, 10, 8, 11)], 2: [box(10, 10, 12, 14), box(5, 7, 12, 14), box(1, 11, 15, 15)] },
}

export const HEDGE = {
  // [axis, at, a, b]; one high (the raised beds show over it), two at the corners and the gateway's cheeks
  1: [['x', 0, 0, 3], ['x', 0, 9, 12], ['z', 0, 1, 12], ['z', 12, 1, 12], ['x', 12, 1, 11]],
  2: [['z', 0, 13, 16], ['z', 12, 13, 16], ['x', 16, 1, 11]],
  tall: [[0, 0], [12, 0], [3, 0], [9, 0], [0, 12], [12, 12], [0, 16], [12, 16]],
}

// The pieces the keeper uses (fixtures: the game reads them as themselves; no furniture spots in this family)
export const PIECES = {
  composter: [4, 1, 7], // kept cell (refreshes never move a job site)
  chest: { at: [6, 1, 7], facing: 'north' }, // kept cell: faces the walk, backed by the lodge's king post
  hay: [[8, 1, 7], [4, 1, 9], [4, 1, 10], [4, 2, 10], [8, 1, 9]], // a bale on the porch; the store's bales
  pentice: { rows: [[7, 3]], x0: 3, x1: 9 }, // [z, y]: a pent roof over the porch, rising to the lodge's front wall
  lanterns: [[6, 3, 0], [5, 3, 4], [7, 3, 4], [10, 3, 10], [6, 3, 9]], // hung: gateway, the walk's end, east door, store
  tieBeam: { z: 9, x0: 4, x1: 8, y: 4 },
  doors: { 1: [{ at: [9, 1, 10], facing: 'west', out: 'east' }], 2: [{ at: [6, 1, 11], facing: 'north', out: 'south' }] },
}

// Glow berries (II): [x, y, z, length] hanging two long from the rafters (y 5), the tip at y 3 just over the bushes:
// a curtain under each rafter between the beams over the beds, every second cell over the walk (vines don't stop a
// villager), and a fringe on the rafters' front-to-back overhang over the porch kerbs
const vines = []
for (const x of [1, 3, 9, 11]) for (const z of [1, 2, 3, 5, 14, 15]) vines.push([x, 3, z, 2])
for (const x of [5, 7]) for (const z of [1, 3, 5, 14]) vines.push([x, 3, z, 2])
export const VINES = vines

// The walking line: in at the gateway, up the walk to the chest, round the porch to the flank beds and the store's
// door; at II through the store's back door into the cloister.
export const WALK = {
  1: [[6, 1, 0], [6, 1, 3], [6, 1, 6], [5, 1, 7], [3, 1, 7], [1, 1, 7], [10, 1, 7], [10, 1, 10], [9, 1, 10], [6, 1, 10]],
  2: [[6, 1, 10], [6, 1, 11], [6, 1, 13], [6, 1, 15], [10, 1, 15], [10, 1, 7]],
}
