// Well House, the plan: drawn before any wall (briefs/well_house.md, ROADMAP 49). recipe.mjs assembles it from the
// parts library (../parts) in passes.
//
// Three rough concepts (massing, roof, plan, story):
//   A. The Aqueduct Head. A square open pavilion on a raised stone cistern podium, a cross-gabled roof (a gable to
//      every side) crowned by a louvre over the crossing, and a stone aqueduct arm running out east on arches: the
//      well's water is drawn up at the windlass in the middle and visibly sent off toward the fields along the
//      channel, the Well Keeper's cauldron standing at the channel's head. Story: the village's water leaves here.
//   B. The Water Tower. A tall 5 x 5 stone tower over the shaft with a pyramid roof and a winch room at the top, a
//      lean-to bucket shed and trough at its foot. Story: the keeper hauls water up from deep under the hill.
//   C. The Spring Grotto. A stone house half sunk into a grassy bank, one big gabled porch facing the front, the shaft
//      in a vaulted back room and the trough outside in the yard. Story: built over a spring in the hillside.
// Strongest: A. B is one box with a hat and hides the well inside; C hides the water in the dark and has no
// silhouette from three sides. A says what the building does from any side: an open-sided hall around a well you can
// see into, and an arm of water pointing at the fields, the very thing the Well Keeper does (carry water out in a
// drought). The aqueduct also gives the plan the lopsided outline a square pavilion lacks, and the cross gable plus the
// louvre gives the roof a crown instead of one ridge. B and C are the safe "house with a well in it".
//
// Coordinates: x across the front (west 0 -> east 16), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. The shaft's water is at y = 0, the deck at y = 2, the kerb and the channel at y = 3
// (the brief's offsets: water 0, air 1-2, kerb and trough 3, roof stairs at 7 and up).
//
// Masses (named parts the owner's feedback can point at):
//   Podium     the cistern base: stone x 0..10, z 2..12, y 0..2 (rubble course at y 0, stone bricks, a coping ring
//              round the deck), a flight of three steps up the front (x 4..6), lamps on the step cheeks.
//   Pavilion   the open hall on the podium: spruce posts x 1..9, z 3..11 (y 3..5), a beam ring at y 6, spruce
//              railings between the posts, corbel brackets under the beam in the wide bays, benches inside.
//   Roof       a cross gable in dark oak over x 0..10, z 2..12 (eaves at y 6, both ridges at y 11, a gable to every
//              side, valleys at the corners), pale birch gables with a spruce king post.
//   Louvre     the crown: a little open ventilator on the crossing (y 11..14) with a lantern hanging in it.
//   Well       the shaft at (5, 7): water at y 0, lined, open to the mouth; a kerb ring of stone bricks at y 3; a
//              windlass (two posts on the kerb and a log axle at y 6) with the chain hanging into the shaft.
//   Aqueduct   the channel: water along z = 7 at y 3 from the kerb (x 7) out through the east posts and along a stone
//              arm on arches (x 11..16, piers at 13 and 16) to a lamp at its end. The keeper's cauldron stands at
//              the channel's head, on its north side.

export const SIZE = [17, 15, 13] // w, h, d (limit 18 x 28 x 22)

// Palette (60/30/10): stone bricks (podium, aqueduct, kerb) / spruce frame and benches + dark oak roof / polished
// andesite (deck, channel coping) and pale birch gables. Why: a water building should look like wet stone at the
// bottom and dry timber above; the cool grey base and the dark roof frame the warm open hall, and the pale gables keep
// the roof from reading as one dark mass. The kerb and the stone bricks carry moss (the damp gathers low, round the
// water); the dry upper courses don't.
export const PALETTE = {
  rubble: [['cobblestone', 5], ['mossy_cobblestone', 3], ['andesite', 1]],
  stone: [['stone_bricks', 7], ['cracked_stone_bricks', 2], ['mossy_stone_bricks', 2]],
  coping: 'stone_bricks',
  kerb: [['stone_bricks', 3], ['mossy_stone_bricks', 2]], // all #minecraft:stone_bricks (the test counts the tag)
  deck: 'polished_andesite',
  channel: 'polished_andesite',
  steps: 'stone_brick_stairs',
  cheekWall: 'stone_brick_wall',
  frame: 'spruce_log',
  wood: 'spruce', // benches (spruce_stairs: the test counts them), brackets, railings
  roof: { full: 'dark_oak_planks', stairs: 'dark_oak_stairs', slab: 'dark_oak_slab' }, // the test wants dark_oak_stairs at y >= 7
  gable: 'birch_planks',
  louvrePost: 'dark_oak_fence',
}

export const MASSES = {
  podium: { x0: 0, x1: 10, z0: 2, z1: 12, deckY: 2 },
  pavilion: {
    posts: { x0: 1, x1: 9, z0: 3, z1: 11 },
    postTop: 5, beamY: 6,
    frontPosts: [1, 3, 7, 9], backPosts: [1, 3, 7, 9], // the 3-wide middle bay is the way in (front) and the bench (back)
    westPosts: [3, 7, 11], eastPosts: [3, 6, 8, 11], // the east middle bay (z 7) is the channel's gate
  },
  roof: { x0: 0, x1: 10, z0: 2, z1: 12, eaveY: 6 }, // ridges at y 11, crossing at (5, 7)
  louvre: { x: 5, z: 7, y: 11 },
  well: { x: 5, z: 7, waterY: 0, mouthY: 3 },
  aqueduct: { xa: 11, xb: 16, cz: 7, y: 3, bay: 3, waterFrom: 7 },
}

export const ROOMS = [
  { name: 'Well hall', use: 'the open pavilion round the well: draw water, sit in the shade, the keeper fills his buckets', box: [2, 3, 4, 8, 5, 10] },
]

export const OPENINGS = {
  steps: { x0: 4, x1: 6, z: [0, 1, 2] }, // three stairs up (the last cut into the coping), on the doorstep's line (x 5)
  way: { at: [5, 3, 3], width: 3 }, // the front middle bay, open
  gate: { at: [9, 3, 7] }, // where the channel leaves the hall (east middle bay)
  // railings (spruce fence, y 3) in every other bay; corbels in the bays three wide (front, back, west)
  railings: [
    [[2, 3, 3]], [[8, 3, 3]],
    [[2, 3, 11]], [[4, 3, 11], [6, 3, 11]], [[8, 3, 11]],
    [[1, 3, 4], [1, 3, 6]], [[1, 3, 8], [1, 3, 10]],
    [[9, 3, 4], [9, 3, 5]], [[9, 3, 9], [9, 3, 10]],
  ],
  corbels: [
    [4, 5, 3, 'west'], [6, 5, 3, 'east'], [4, 5, 11, 'west'], [6, 5, 11, 'east'],
    [1, 5, 4, 'north'], [1, 5, 6, 'south'], [1, 5, 8, 'north'], [1, 5, 10, 'south'],
    [9, 5, 4, 'north'], [9, 5, 5, 'south'], [9, 5, 9, 'north'], [9, 5, 10, 'south'],
  ],
}

// Every piece the hall holds. The brief needs no furniture spots (no job reads one), so these are fixtures only:
// drawn as themselves, recorded in no furniture.json spot.
export const FURNITURE = [
  { room: 'Well hall', kind: 'fixture', what: 'cauldron (the job block, empty)', pos: [8, 3, 6] },
  { room: 'Well hall', kind: 'fixture', what: 'keeper\'s standing spot (the test\'s villager spot)', pos: [8, 3, 5] },
  // benches: spruce stairs, backs to the railing, facing the well
  ...[4, 5, 6, 8, 9, 10].map(z => ({ room: 'Well hall', kind: 'bench', pos: [2, 3, z], facing: 'west' })),
  ...[4, 5, 6].map(x => ({ room: 'Well hall', kind: 'bench', pos: [x, 3, 10], facing: 'south' })),
  { room: 'Well hall', kind: 'windlass', posts: [[4, 4, 7], [6, 4, 7]], axle: [[4, 6, 7], [5, 6, 7], [6, 6, 7]], chain: [[5, 4, 7], [5, 5, 7]] },
  // lanterns hang from the beam in the middle bays, one in the louvre, two on the step cheeks, one at the aqueduct's end
  { room: 'Well hall', kind: 'lamp', hanging: [[5, 5, 3], [5, 5, 11], [1, 5, 5], [1, 5, 9], [9, 5, 7]] },
]

// The walking line: the doorstep (5, -, -1 outside) -> two steps -> the deck -> round the kerb to the keeper's spot by
// the cauldron (the channel's head); the aisles: x 3 between the west benches and the kerb, z 9 behind it.
export const WALK = [
  [5, 0, 0], [5, 1, 1], [5, 2, 2], [5, 3, 3], [5, 3, 4], [6, 3, 4], [7, 3, 4], [7, 3, 5], [8, 3, 5],
  [3, 3, 5], [3, 3, 7], [3, 3, 9], [5, 3, 9], [8, 3, 9],
]

export const OUTSIDE = {
  flowerBeds: [{ x0: 0, x1: 2, z: 1, plants: ['cornflower', 'fern', 'blue_orchid'] }, { x0: 8, x1: 10, z: 1, plants: ['blue_orchid', 'fern', 'cornflower'] }],
  cheekLamps: [[3, 1], [7, 1]],
  endLamp: [16, 4, 7],
}
