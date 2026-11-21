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
//   Stake wall   the ring x 0..28, z 0..28: bark-on spruce stakes in panels of three between taller stripped posts
//                (panels 6 high, posts 7, the front 5 and 6, a dripstone point on each), the back corners raised to y 8 with a skull on the tallest
//                stake; the front keeps a low earth-and-cobble fire-step (y 1, a slab at y 2).            (I, II, III)
//   Wall-walk    a plank walk at y 3 one in from the stakes on the two sides and the back (x 1, x 27, z 27, from
//                z 5 back), on posts every four with knee braces, the space under it open; ladders up from the yard.
//   Fighting     three roofed stretches of the walk, two deep, the stakes behind cut level to y 7 as a screen, a
//   stages       little gable (ridge y 9) over the walk on stripped posts, a lantern on a chain under it: West
//                stage z 11..15, East stage z 20..24, Back stage x 2..5 (the back-west corner).          (I, II, III)
//   Gatehouse    a 3-wide gateway x 13..15 at z 0 shut by fence gates two high, cheeks of logs, a 45-degree spruce
//                gable roof over x 11..17, z 0..2 (ridge y 7), lanterns under a tie beam.                (I, II, III)
//   Fire         the campfire (14, 1, 11) in a stone ring with a spit, log benches either side.        (I, II, III)
//   Tents        A-frame tents of undyed (light grey) canvas on stripped dark oak ridge poles, a grey hem, a red
//                pennant on each ridge's front end: West (x 3..7, z 5..10), Back-west (x 3..7, z 14..19), East
//                (x 21..25, z 5..9). The West tent is furnished (bedrolls, a barrel, a lantern).          (I, II, III)
//   Pavilion     the King's tent x 10..18, z 20..25, ridge y 5, the same canvas with a black skirt, his standard
//                before it.                                                                               (I, II)
//   Yard work    archery butts by the front-west wall, a horse trough and hitching rail by the front-east, a
//                captured merchant's cart under a torn cover in the back-west corner, a woodpile and barrels in
//                the back-east, a grindstone by the fire.                                                 (I, II, III)
//   Watchtowers  log legs x 0..4 / 24..28, z 0..4, deck y 5, parapet y 6, hat y 9..11, a red pennant on each
//                apex, a lantern on a chain over the deck, a barrel of arrows and a bench of logs.          (II, III)
//   Stable       a lean-to on the east wall, x 22..27, z 11..19, stepped spruce roof y 5..7, two stalls. (II, III)
//   Keep         robbed stone x 9..19, z 17..27 (y 1..8), batter at its foot, a dark oak jetty band at y 9..10
//                (one block out all round, over the buttresses at the back), a 45-degree dark oak gable roof
//                across x 8..20, eaves y 11, ridge y 17. Its back (z 28, the fort's back edge) is not flat: clasping
//                buttresses at its corners and two piers, the postern deep-set in a stone porch with a timber
//                bretesche over it, arrow slits upstairs and a window in the back gable. Three floors joined by a
//                stair and a ladder: the throne hall y 1..4, the King's chamber y 6..8, the war loft y 10.     (III)
//
// Palette (60/30/10): spruce (stakes, logs, boards, roofs) / robbed stone (cobble, stone bricks, cracked and mossy,
// with stone-brick quoins) and dark oak (keep roof and frame), with undyed canvas for the tents / black and red as
// trim only: pennants, hems of the King's things, banners, the hall's runner, lantern and fire light. Why: a raw
// brown ring that is the bandits' own, one grey-and-dark mass that is the King's, pale canvas that reads as tents
// without shouting, and the King's black and red kept small so the eye goes from the gate to the fire to the black
// banner over his door.
//
// Round 2 (the critic's round-1 notes, and what changed):
//   1 The keep's upper floors couldn't be reached. The stair up the hall's west wall (five steps, x 10, z 19..23) keeps
//     its open well in the chamber floor, railed, with a lantern at its foot; a ladder in the chamber's
//     north-east corner (18, 6..9, 19) climbs through a hatch to the new war loft under the roof (y 10: bedrolls, a
//     weapon rack, a lantern, the front gable's window over the yard and the back gable's over the postern).
//   2 Level I was a uniform crenellated box. The stakes now stand in panels between taller posts; a raised wall-walk
//     on posts and braces runs round the sides and back with three roofed fighting stages (little gables to y 9)
//     that break the long walls' line; the back corners rise to y 8 (a skull on the tallest stake). The front keeps its low fire-step, so the
//     gate side reads different from the rest.
//   3 The keep's back was flat stone with one door. The back wall moved in to z 27, so z 28 carries the depth:
//     clasping buttresses at the corners (to y 7), two piers stepped at y 5, the postern deep-set in a stone porch
//     with a bretesche corbelled out over it (a murder hole, a slit), arrow slits in the chamber and a glazed window
//     in the back gable.
//   4 Red and black wool dominated the top view. Every tent is now one muted undyed canvas (light grey, a grey hem);
//     red is a pennant on each ridge and black a skirt on the King's pavilion. The bare front yard has the archery
//     butts, the trough and hitching rail; the back-west corner a captured cart, the back-east a woodpile and barrels.
//   5 Eye-level shots: the throne hall has braziers (campfires on the dais) beside the throne, a feasting table and
//     benches, banners, a red runner with a black border and six lanterns; the West tent has bedrolls, a barrel and a
//     lantern on the ridge; each tower platform has a lantern on a chain, a barrel and a log bench.

export const SIZE = [29, 18, 29]

export const PALETTE = {
  ground: [['coarse_dirt', 6], ['dirt', 3], ['gravel', 1], ['packed_mud', 2]],
  path: 'dirt_path',
  stake: 'spruce_log',
  tip: 'pointed_dripstone',
  skull: 'skeleton_skull',
  bank: [['cobblestone', 3], ['mossy_cobblestone', 2], ['coarse_dirt', 2], ['rooted_dirt', 1]],
  step: 'spruce_slab',
  wood: 'spruce', // boards, fences, gates, doors outside the keep
  pole: 'stripped_dark_oak_log',
  timberRoof: { full: 'spruce_planks', stairs: 'spruce_stairs', slab: 'spruce_slab' },
  keepStone: [['cobblestone', 4], ['stone_bricks', 4], ['cracked_stone_bricks', 2], ['mossy_stone_bricks', 1], ['andesite', 1]],
  quoin: ['stone_bricks', 'polished_andesite'],
  keepStairs: 'stone_brick_stairs',
  keepSlab: 'stone_brick_slab',
  keepRoof: { full: 'dark_oak_planks', stairs: 'dark_oak_stairs', slab: 'dark_oak_slab' },
  trim: 'dark_oak',
  keepFloor: 'spruce_planks',
  dais: 'polished_deepslate',
  daisStairs: 'polished_deepslate_stairs',
  canvas: 'light_gray_wool', hem: 'gray_carpet',             // one muted canvas for every tent
  kingSkirt: 'black_wool', kingHem: 'black_carpet',            // the King's colours, as trim
  pennant: 'red_banner', kingBanner: 'black_banner',
  runner: 'red_carpet', runnerEdge: 'black_carpet',
  bedroll: { mat: 'brown_carpet', pillow: 'white_carpet' },
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
  wall: { x0: 0, z0: 0, x1: 28, z1: 28, panel: 4, panelY: 6, postY: 7, frontY: 5, cornerY: 8 },
  walk: { y: 3, from: 5, every: 4 },                                     // x 1, x 27 and z 27, z 5 back
  stages: [
    { name: 'West stage', along: 'z', wall: 0, l0: 11, l1: 15, inward: 1, screenTop: 7 },
    { name: 'East stage', along: 'z', wall: 28, l0: 20, l1: 24, inward: -1, screenTop: 7 },
    { name: 'Back stage', along: 'x', wall: 28, l0: 2, l1: 5, inward: -1, screenTop: 7 },
  ],
  walkLadders: [[3, 13, 'east', 2], [25, 23, 'west', 26], [26, 10, 'west', 27]], // [x, z, facing, post x]
  gate: { x0: 11, x1: 17, z0: 0, z1: 2, way: [13, 15], eaveY: 4 },
  fire: { at: [14, 1, 11] },
  tents: [
    { name: 'West tent', a0: 3, a1: 7, l0: 5, l1: 10, back: 'l0', furnished: true },        // mouth to the yard's middle
    { name: 'Back-west tent', a0: 3, a1: 7, l0: 14, l1: 19, back: 'l1' },
    { name: 'East tent', a0: 21, a1: 25, l0: 5, l1: 9, back: 'l0' },
  ],
  pavilion: { a0: 10, a1: 18, l0: 20, l1: 25, back: 'l1' },
  butts: { targets: [[6, 2], [8, 2]], bale: [7, 2] },
  trough: { water: [[19, 3], [20, 3], [21, 3]], rail: { x0: 18, x1: 22, z: 1 } },
  cart: { bed: { x0: 3, x1: 6, z0: 22, z1: 24, y: 1 }, wheels: [[4, 1, 21], [4, 1, 25]] },
  towers: [
    { name: 'West tower', x0: 0, z0: 0, x1: 4, z1: 4, deckY: 5, topY: 8, ladder: [1, 2, 'east'], outward: ['north', 'west'] },
    { name: 'East tower', x0: 24, z0: 0, x1: 28, z1: 4, deckY: 5, topY: 8, ladder: [27, 2, 'west'], outward: ['north', 'east'] },
  ],
  stable: { low: 22, high: 27, z0: 11, z1: 19, eaveY: 5, posts: [11, 15, 19], stalls: [[12, 14], [16, 18]] },
  keep: {
    body: { x0: 9, x1: 19, z0: 17, z1: 27 },
    room: { x0: 10, x1: 18, z0: 18, z1: 26 },
    floorY: 5, wallTop: 8, jettyY: 9,
    roof: { axis: 'z', across: [8, 20], along: [16, 28], eaveY: 11, steep: false },
    buttresses: [[9, 7], [19, 7], [11, 4], [17, 4]],                      // [x, top] at z 28, capped a course up
    porch: { x0: 13, x1: 15, top: 3 },
    bretesche: { x0: 13, x1: 15, y: 5 },
  },
}

export const ROOMS = [
  { name: 'Throne hall', use: 'the King holds court and feasts; the treasure, the throne on its dais between two braziers, the postern behind', box: [10, 1, 18, 18, 4, 26] },
  { name: "King's chamber", use: 'his maps, his loot and his bed, over the hall', box: [10, 6, 18, 18, 8, 26] },
  { name: 'War loft', use: 'the King\'s guard sleeps under the roof, by the gable windows', box: [9, 10, 17, 19, 12, 27] },
  { name: 'Stable', use: 'two stalls for the raiders\' horses, hay and water', box: [23, 1, 12, 27, 4, 18] },
  { name: 'West tent', use: 'two bandits\' bedrolls, their kit barrel', box: [4, 1, 6, 6, 2, 10] },
  { name: 'Tower platforms', use: 'a pillager\'s post: a lantern, arrows, a seat', box: [1, 6, 1, 3, 7, 3] },
]

export const OPENINGS = {
  gate: { gates: [13, 14, 15], ys: [1, 2], z: 0 },
  keepDoor: { at: [14, 1, 17], facing: 'south' },          // dark oak, the yard side
  banner: { at: [14, 4, 16], facing: 'north' },            // black, on the stone over the door
  postern: { at: [14, 1, 28], facing: 'north' },           // a spruce door in the porch behind the keep, shut
  loops: [[11, 2, 17, 'south'], [17, 2, 17, 'south'], [9, 2, 21, 'east'], [9, 2, 25, 'east'], [19, 2, 21, 'west'], [19, 2, 25, 'west']],
  chamberWindows: [[11, 6, 17], [17, 6, 17], [9, 6, 23], [19, 6, 23]],  // two-high iron bars
  backSlits: [[12, 6, 27], [16, 6, 27]],                                 // the chamber's slits over the postern
  stair: { cells: [[10, 1, 19], [10, 2, 20], [10, 3, 21], [10, 4, 22], [10, 5, 23]], rises: 'south', well: [[10, 5, 19], [10, 5, 20], [10, 5, 21], [10, 5, 22]] },
  loftLadder: { x: 18, z: 19, facing: 'west', y0: 6, y1: 9 },            // through a hatch in the loft floor
}

// No furniture spots: nobody lives here (the brief: no beds, no job blocks; the residents are mobs). Bedrolls are
// carpets, seats are logs and stairs; furniture.json is {"spots": []}. The fixtures the code reads are pinned above.
export const FURNITURE = []

// The walking line: through the gate, past the fire to the keep's door, up the hall's red runner to the King's
// spot, round the throne to the postern; up the stair to the chamber and the ladder to the loft; the stable, the
// tower ladders and the wall-walk's ladders off the yard.
export const WALK = [
  [14, 1, 1], [14, 1, 4], [14, 1, 8], [13, 1, 13], [14, 1, 15], [14, 1, 17], [14, 1, 19], [14, 1, 21], [14, 1, 22],
  [13, 2, 23], [12, 1, 25], [13, 1, 26], [14, 1, 26], [14, 1, 27], [14, 1, 28],
]
