// The Bandit Fort (story/bandit_fort, _2, _3), the plan: drawn before any wall. The brief is
// tools/blueprints/briefs/bandit_fort.md; recipe.mjs assembles each level from the parts library (../parts) to fit it.
//
// Three rough concepts (massing, roof, plan, story):
//   A  "Stakes and a stolen keep". A ragged ring of sharpened spruce stakes on an earth fire-step, a roofed gate,
//      tents of dyed wool on ridge poles; II puts two log watchtowers on legs over the front corners and a lean-to
//      stable on the east wall; III raises the only stone thing in the fort, a keep of robbed road-stone with a
//      jettied timber war-band and a steep dark gable facing the yard, its back wall the fort's back wall.
//      Story: the men build in wood with what they cut; only the King builds in stone, and it shows.
//   B  "Wagon laager". Captured caravans chained wheel to wheel make the wall, canvas over them; the keep a
//      captured merchant's house on a mound. Story: a band of road thieves who never stop moving.
//   C  "Earthwork ring". A ditch and bank with a low stake fence, a squat round stone tower for a keep, tents in rows.
//      Story: old soldiers gone bad, building the way they were drilled.
// Strongest: A. The 29 x 29 box and the pinned cells (a palisade log at the west wall, ladders on the front corners,
// a door and banner on the yard side of a keep at the back) want a walled yard; B's wagons can't make a wall that
// closes a 29-block ring and would read as clutter, and C's round tower can't hold the pinned door, throne and
// postern on one axis. A's contrast (raw timber everywhere, one dressed stone mass at the back) makes the King's
// power readable from 20 blocks away, and every level grows out of the last: the King's black pavilion in I and II
// stands exactly where his keep rises in III, the woodpiles in the front corners end up under the towers' legs.
//
// Coordinates: x across the front (west 0 -> east 28), y up (0 = the ground itself, every cell solid), z from the front
// (z = 0, north, the gate) to the back (z = 28, the postern). Everything fits 29 x 18 x 29 (y 0..17).
//
// Masses (named parts the owner's feedback can point at):
//   Stake wall   the ring x 0..28, z 0..28: spruce stakes y 1..4 or 5 (ragged), a dripstone point on each, corner
//                posts to y 6; an earth-and-cobble fire-step (y 1, a slab at y 2) one in, all round.     (I, II, III)
//   Gatehouse    a 3-wide gateway x 13..15 at z 0 shut by fence gates two high, cheeks of logs, a 45-degree spruce
//                gable roof over x 11..17, z 0..2 (ridge y 7), lanterns under a tie beam.                (I, II, III)
//   Fire         the campfire (14, 1, 11) in a stone ring with a spit, log benches either side.        (I, II, III)
//   Tents        A-frame tents on stripped dark oak ridge poles: West (x 3..7, z 5..10, red), Back-west (x 3..7,
//                z 14..19, black), East (x 21..25, z 5..9, red).                                        (I, II, III)
//   Pavilion     the King's black tent x 10..18, z 20..26, ridge y 5, his standard before it.            (I, II)
//   Watchtowers  log legs x 0..4 / 24..28, z 0..4, deck y 5, parapet y 6, hat y 9..11.                   (II, III)
//   Stable       a lean-to on the east wall, x 22..27, z 11..19, stepped spruce roof y 5..7, two stalls. (II, III)
//   Keep         robbed stone x 9..19, z 17..28 (y 1..8), batter at its foot, a dark oak jetty band at y 9..10
//                (one block out at the front and sides), a 45-degree dark oak gable roof across x 8..20,
//                eaves y 11, ridge y 17; the throne hall y 1..4, the King's chamber y 6..8.                (III)
//
// Palette (60/30/10): spruce (stakes, logs, boards, roofs) / robbed stone (cobble, stone bricks, cracked and mossy,
// with stone-brick quoins) and dark oak (keep roof and frame) / black and red wool and banners, lantern light. Why:
// a raw brown ring that is the bandits' own, one grey-and-dark mass that is the King's, and the black and red of his
// colours the only strong hues, so the eye goes from the gate to the fire to the black banner over his door.

export const SIZE = [29, 18, 29]

export const PALETTE = {
  ground: [['coarse_dirt', 6], ['dirt', 3], ['gravel', 1], ['packed_mud', 2]],
  path: 'dirt_path',
  stake: 'spruce_log',
  tip: 'pointed_dripstone',
  bank: [['cobblestone', 3], ['mossy_cobblestone', 2], ['coarse_dirt', 2], ['rooted_dirt', 1]],
  step: 'spruce_slab',
  wood: 'spruce', // boards, fences, gates, doors outside the keep
  pole: 'stripped_dark_oak_log',
  timberRoof: { full: 'spruce_planks', stairs: 'spruce_stairs', slab: 'spruce_slab' },
  keepStone: [['cobblestone', 4], ['stone_bricks', 4], ['cracked_stone_bricks', 2], ['mossy_stone_bricks', 1], ['andesite', 1]],
  quoin: ['stone_bricks', 'polished_andesite'],
  keepStairs: 'stone_brick_stairs',
  keepRoof: { full: 'dark_oak_planks', stairs: 'dark_oak_stairs', slab: 'dark_oak_slab' },
  trim: 'dark_oak',
  keepFloor: 'spruce_planks',
  dais: 'polished_deepslate',
  daisStairs: 'polished_deepslate_stairs',
  canvas: { red: 'red_wool', black: 'black_wool' },
  hem: { red: 'red_carpet', black: 'black_carpet' },
}

export const PINS = {
  yard: [14, 1, 14],
  yardMen: [[12, 1, 8], [16, 1, 8], [11, 1, 14], [17, 1, 14]],
  towerMen: [[2, 6, 2], [26, 6, 2]],
  keepDoorClear: { x0: 13, x1: 15, z0: 14, z1: 16 },
  throne: [14, 1, 21],
  treasure: [18, 1, 24],
  postern: [14, 1, 28],
  hillGone: [[9, 1, 3], [9, 2, 3]],
}

export const MASSES = {
  wall: { x0: 0, z0: 0, x1: 28, z1: 28 },
  gate: { x0: 11, x1: 17, z0: 0, z1: 2, way: [13, 15], eaveY: 4 },
  fire: { at: [14, 1, 11] },
  tents: [
    { name: 'West tent', a0: 3, a1: 7, l0: 5, l1: 10, colour: 'red', back: 'l0' },        // mouth to the yard's middle
    { name: 'Back-west tent', a0: 3, a1: 7, l0: 14, l1: 19, colour: 'black', back: 'l1' },
    { name: 'East tent', a0: 21, a1: 25, l0: 5, l1: 9, colour: 'red', back: 'l0' },
  ],
  pavilion: { a0: 10, a1: 18, l0: 20, l1: 26, colour: 'black', back: 'l1' },
  towers: [
    { name: 'West tower', x0: 0, z0: 0, x1: 4, z1: 4, deckY: 5, topY: 8, ladder: [1, 2, 'east'], outward: ['north', 'west'] },
    { name: 'East tower', x0: 24, z0: 0, x1: 28, z1: 4, deckY: 5, topY: 8, ladder: [27, 2, 'west'], outward: ['north', 'east'] },
  ],
  stable: { low: 22, high: 27, z0: 11, z1: 19, eaveY: 5, posts: [11, 15, 19], stalls: [[12, 14], [16, 18]] },
  keep: {
    body: { x0: 9, x1: 19, z0: 17, z1: 28 },
    room: { x0: 10, x1: 18, z0: 18, z1: 27 },
    floorY: 5, wallTop: 8, jettyY: 9,
    roof: { axis: 'z', across: [8, 20], along: [16, 28], eaveY: 11, steep: false },
  },
}

export const ROOMS = [
  { name: 'Throne hall', use: 'the King holds court; the treasure, the throne on its dais, the postern behind it', box: [10, 1, 18, 18, 4, 27] },
  { name: "King's chamber", use: 'his maps, his loot and his bedroll, over the hall', box: [10, 6, 18, 18, 8, 27] },
  { name: 'Stable', use: 'two stalls for the raiders\' horses, hay and water', box: [23, 1, 12, 27, 4, 18] },
]

export const OPENINGS = {
  gate: { gates: [13, 14, 15], ys: [1, 2], z: 0 },
  keepDoor: { at: [14, 1, 17], facing: 'south' },          // dark oak, the yard side
  banner: { at: [14, 4, 16], facing: 'north' },            // black, on the stone over the door
  postern: { at: [14, 1, 28], facing: 'north' },           // a spruce door in the keep's back wall, shut
  loops: [[11, 2, 17, 'south'], [17, 2, 17, 'south'], [9, 2, 21, 'east'], [9, 2, 25, 'east'], [19, 2, 21, 'west'], [19, 2, 25, 'west']],
  chamberWindows: [[11, 6, 17], [17, 6, 17], [9, 6, 23], [19, 6, 23]],  // two-high iron bars
  stair: { cells: [[10, 1, 19], [10, 2, 20], [10, 3, 21], [10, 4, 22], [10, 5, 23]], rises: 'south', well: [[10, 5, 19], [10, 5, 20], [10, 5, 21], [10, 5, 22]] },
}

// No furniture spots: nobody lives here (the brief: no beds, no job blocks; the residents are mobs). Bedrolls are
// carpets, seats are logs and stairs; furniture.json is {"spots": []}. The fixtures the code reads are pinned above.
export const FURNITURE = []

// The walking line: through the gate, past the fire to the keep's door, up the hall's red runner to the King's
// spot, round the throne to the postern; and the stable and tower ladders off the yard.
export const WALK = [
  [14, 1, 1], [14, 1, 4], [14, 1, 8], [13, 1, 13], [14, 1, 15], [14, 1, 17], [14, 1, 19], [14, 1, 21], [14, 1, 22],
  [13, 2, 23], [13, 1, 25], [14, 1, 26], [14, 1, 27], [14, 1, 28],
]
