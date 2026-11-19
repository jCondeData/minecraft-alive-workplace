// Terminal Nook, the plan (ROADMAP 49 / 50.13; brief: tools/blueprints/briefs/terminal_nook.md): drawn before any wall.
// The store's cells (Storehouse block, stores, connector, terminal) and the walking line come first; recipe.mjs then
// assembles the Nook from the parts library (../parts) to fit this plan, once as the vanilla build and once (TOMS=1)
// as its Tom's Simple Storage copy, cell for cell.
//
// Three rough concepts
//   A. The Staddle Granary. Massing: one square timber store room lifted two blocks clear of the ground on a grid of
//      nine mushroom staddle stones, a stone perron and landing at the front. Roof: a bell-cast slate roof,
//      gable to the street. Plan: one room, the chest wall at the back round the terminal, the Storehouse desk by the
//      door. Story: a village's goods kept dry and out of the rats' reach, the way farms kept their grain.
//   B. The Lean-to Strongroom. Massing: a low cobbled box with a catslide roof, meant to be pushed against a house's
//      flank. Roof: one long slope falling to the street. Plan: a counter hatch in the front wall, chests behind it.
//      Story: a store room borrowed off a neighbour's wall.
//   C. The Counting Kiosk. Massing: a square brick booth with a pyramid roof and a lantern cupola. Roof: a four-sided
//      hip to a point. Plan: the terminal behind a street window, chests round the inside. Story: the village's
//      tally booth on the square.
// Strongest: A. It is the only one with a silhouette you'd know in outline from twenty blocks (a house standing on
// stone mushrooms under a tall hat), the staddles explain themselves (a store kept off the ground), and lifting it
// gives the small room a real arrival: up a flight along the front, onto a railed landing, in under the gable. B
// leans on a neighbour it may not have; C is a box with a hat. A is also the hardest: the floor, the stair and the
// stores' reach all have to work one level up, and they do.
//
// Story: the Staddle Granary. A square store of pale oak boards on a dark oak frame, set on nine staddle stones so
// the damp and the rats stay under it, under a bell-cast roof of slate with a grain louvre on its ridge. Up a stone flight along its front to a
// railed landing with a lamp on its post; inside, one boarded room open to the rafters: a wall of chests at the back
// round the tally (the terminal on Tom's network), the Storehouse block at the porter's desk by the door, barrels and
// hay along the sides, two lanterns hanging from the tie beam.
//
// Coordinates: x across the front (west 0 -> east 10), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The room's floor is the deck at y 2; people stand at y 3.
//
// Masses (named parts the owner's feedback can point at):
//   Staddles   nine stone stems with flat caps under the frame's posts (x 1, 5, 9 by z 2, 6, 10), y 0..1.
//   Store      the boarded room: frame x 1..9, z 2..10, sill beams and deck y 2, walls y 3..5, wall plate y 6.
//   Roof       a 45-degree slate gable (ridge along z, y 11), its eaves kicked out a block (bell-cast) at y 6, one block
//              over the gables (z 1 and 11), barge boards under the rakes.
//   Gables     boards between dark studs, a two-pane vent window in each, a king post to the apex.
//   Perron     a stone flight along the front (x 1..3, z 0) up to a timber landing on two more staddles (x 4..6,
//              z 0), a step in between the door's proud posts (5, 2, 1), the lamp on the landing's corner post.
//   Frame      proud of the boards on all four sides: posts and a sill rail one block out, the side windows'
//              shutters and sills in the recesses, sills with pots under the front windows, an eave course of dark
//              stairs under the roof's kick.
//   Louvre     a little open ventilator astride the ridge over the truss (z 6), a lamp under its cap, a rod on top.
//   Truss      a tie beam across the room at z 6 (over the side posts) with a king post to the ridge.

export const SIZE = [11, 15, 12] // w, h, d (limit 12 x 18 x 12)

// Palette (60/30/10): pale stripped-oak boards (walls, the colour of grain) / dark oak frame + deepslate-tile roof /
// stone staddles and perron, spruce joinery, hay. Why: a light box between a dark frame and a dark hat reads crisp
// from the street, and grey stone at the foot is the darkest-at-the-bottom rule turned into the building's story.
export const PALETTE = {
  stone: [['cobblestone', 5], ['stone', 2], ['mossy_cobblestone', 2], ['andesite', 1]],
  stem: [['cobblestone_wall', 3], ['mossy_cobblestone_wall', 1]],
  cap: 'smooth_stone_slab',
  steps: 'stone_brick_stairs',
  landing: 'stone_bricks',
  frame: 'dark_oak_log',
  boards: 'stripped_oak_log', // laid along each wall: weatherboards
  floor: 'spruce_planks',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  trim: 'dark_oak',
  wood: 'spruce', // the door, shutters, railing, desk, chair
  glass: 'glass_pane',
  // the store
  job: 'aliveworkplace:storehouse',
  chest: 'chest',
  barrel: 'barrel',
  louvreBase: 'dark_oak_planks',
  tally: 'stripped_dark_oak_wood', // the tally block before the chest wall (the terminal in the Tom's copy)
  // Tom's Simple Storage 2.3.0 (the copy only)
  connector: 'toms_storage:inventory_connector',
  terminal: 'toms_storage:storage_terminal',
}

export const MASSES = {
  staddles: { xs: [1, 5, 9], zs: [2, 6, 10] },
  store: {
    frame: { x0: 1, x1: 9, z0: 2, z1: 10 },
    room: { x0: 2, x1: 8, z0: 3, z1: 9 },
    deckY: 2, wallY: [3, 5], plateY: 6,
    frontPosts: [1, 4, 6, 9], backPosts: [1, 5, 9], sidePosts: [2, 6, 10],
  },
  roof: { axis: 'z', across: [0, 10], along: [1, 11], eaveY: 6, steep: false },
  truss: { z: 6, y: 6, kingX: 5 },
  louvre: { x: 5, z: 6 },
  perron: { flight: [1, 0, 0], steps: 3, rises: 'east', landing: { x0: 4, x1: 6, z: 0, y: 2, staddles: [4, 6] } },
  // the proud frame: a second skin of posts and rails one block out from the boards on every side (x 0 and 10, z 1
  // and 11), so each board panel sits in a recess; a girt at y 4 across the back carries the vents' sills
  proud: { y: [2, 5], front: { z: 1, posts: [1, 4, 6, 9] }, back: { z: 11, posts: [1, 5, 9], girtY: 4 }, sides: { xs: [0, 10], posts: [2, 6, 10] } },
}

export const ROOMS = [
  { name: 'Store', use: 'the village store: the chest wall, the tally (terminal), the porter\'s desk', box: [2, 3, 3, 8, 5, 9] },
]

// The store's pinned cells (the GameTests are moved to these; see the report in the ship step).
export const STORE = {
  storehouse: { at: [2, 3, 4], facing: 'east' },
  // the chest wall against the back wall, every chest single (each its own store; on Tom's each one member)
  chestWall: { xs: [3, 4, 6, 7], ys: [3, 4], z: 9, facing: 'north' },
  // the middle of the chest wall: a lone chest (vanilla) / the inventory connector (Tom's), nothing above it so the
  // chest's lid opens and the tally before it leaves its top reachable
  hub: { at: [5, 3, 9], facing: 'north' },
  // before the hub: the tally block (vanilla) / the storage terminal reading the connector behind it, screen north
  tally: { at: [5, 3, 8], reads: 'south', userAt: [5, 3, 7] },
  // the grain barrels on the west wall: off the network on Tom's (they touch no member), the Storehouse's own store
  barrels: { at: [2, 6], ys: [3, 4], facing: 'east' },
}

export const OPENINGS = {
  door: { at: [5, 3, 2], facing: 'south' },
  windows: [
    // the front windows sit between the proud posts: sills with pots (no room for shutters)
    { name: 'front west', cells: [[2, 4, 2], [3, 4, 2]], out: 'north', dress: 'sill', pots: ['potted_dandelion', 'potted_cornflower'] },
    { name: 'front east', cells: [[7, 4, 2], [8, 4, 2]], out: 'north', dress: 'sill', pots: ['potted_cornflower', 'potted_dandelion'] },
    { name: 'west front', cells: [[1, 4, 4]], out: 'west' },
    { name: 'west back', cells: [[1, 4, 8]], out: 'west' },
    { name: 'east front', cells: [[9, 4, 4]], out: 'east' },
    { name: 'east back', cells: [[9, 4, 8]], out: 'east' },
    // high vents in the back wall, over the chest wall (they light the chests' lids)
    { name: 'back west vent', cells: [[3, 5, 10]], out: 'south', sill: 'girt' },
    { name: 'back east vent', cells: [[7, 5, 10]], out: 'south', sill: 'girt' },
  ],
  gableVents: [{ z: 2, cells: [[5, 8, 2], [5, 9, 2]] }, { z: 10, cells: [[5, 8, 10], [5, 9, 10]] }],
}

// Every piece of furniture, footprint first (kind: a furniture.py kind, recorded as a spot; fixture: a vanilla
// block the game uses as itself).
export const FURNITURE = [
  // the porter's desk on the east wall under its front window, a chair at it
  { room: 'Store', kind: 'desk', part: 'desk', pos: [8, 3, 4], facing: 'east', wood: 'spruce' },
  { room: 'Store', kind: 'chair', part: 'chair', pos: [7, 3, 4], facing: 'east', wood: 'spruce' },
  // the ledgers: a low bookcase on the east wall between the desk and the hay, a pot of fern on it
  { room: 'Store', kind: 'fixture', part: 'bookcase', pos: [8, 3, 6], facing: 'west', plant: 'potted_fern' },
  // a lamp by the door on a little side table, in the corner beside the Storehouse block
  { room: 'Store', kind: 'lamp', part: 'tableLamp', pos: [2, 3, 3], facing: 'east', wood: 'spruce' },
  // two lanterns on the tie beam, one over each side of the room
  { room: 'Store', kind: 'lamp', part: 'hangingLamp', pos: [3, 5, 6], facing: 'north', wood: 'spruce' },
  { room: 'Store', kind: 'lamp', part: 'hangingLamp', pos: [7, 5, 6], facing: 'north', wood: 'spruce' },
  // the store's mark over the tally: a cloth on the back wall above the hub
  { room: 'Store', kind: 'picture', part: 'picture', pos: [5, 5, 9], facing: 'north', wood: 'spruce', colour: 'orange' },
  // a runner from the door to the tally
  { room: 'Store', kind: 'rug', part: 'rug', cellsAbs: [[5, 3, 4], [5, 3, 5], [4, 3, 6], [5, 3, 6], [6, 3, 6], [4, 3, 7], [5, 3, 7], [6, 3, 7]], colour: 'brown' },
]

// Hay, the granary's grain, banked in the back corners (blocks, not furniture spots)
export const HAY = [[2, 3, 9], [2, 4, 9], [2, 3, 8], [8, 3, 9], [8, 4, 9], [8, 3, 8]]

// The walking line: ground -> flight along the front -> landing -> door -> runner -> the tally's screen; the desk,
// the barrels and every chest open off it.
export const WALK = [
  [0, 0, 0], [1, 0, 0], [2, 1, 0], [3, 2, 0], [4, 3, 0], [5, 3, 0], [5, 3, 1], [5, 3, 2], [5, 3, 3], [5, 3, 4], [5, 3, 5],
  [5, 3, 6], [5, 3, 7],
]

// Outside: nothing loose on the ground (the plot may be any terrain); the staddles and the flight are the ground floor.
