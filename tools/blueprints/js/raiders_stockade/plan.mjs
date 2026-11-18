// Raiders' Stockade (story/raiders_stockade), the plan: drawn before any stake. The brief
// (tools/blueprints/briefs/raiders_stockade.md) pins the chest, the cage's door, bars and lever, the fire, the tent's
// ridge, a palisade log and the gate; everything else is drawn here first, then recipe.mjs assembles it in passes from
// the parts library (../parts).
//
// Three rough concepts (massing, roof, plan, one line of story each):
//   A. "The Ring": an even square of sharpened logs, a roofed gatehouse in the front's middle, a corner tower at each
//      back corner, tent, fire and cage loose in the yard. Story: raiders who dug in to stay. The textbook stockade:
//      safe, symmetrical, and the cage is just one more thing in the yard.
//   B. "The Laager": the lost caravan's own wagons drawn up in a ring, stakes driven into the gaps, the carter locked
//      in his own cage wagon. Story: the raiders turned the caravan into their fort. Strong story, but wagons at 19 x 17
//      read as clutter, the pinned palisade log and the gate fight it, and a wagon deck can't be the cage's floor
//      (the carter must stand on the ground).
//   C. "The Lock-up Under the Lookout": a serrated ring of dark oak stakes (a rhythm of heights and spikes, skulls by
//      the gate), a squat gate tower over the way in, and in the back-east corner a lookout on four tall posts whose
//      foot IS the cage: the carter sits barred in under the guards' boots, the lever on the jamb right under their
//      eyes. The caravan's spoils (its red-striped canvas sewn into the tent, its cargo, a cart wheel) are spread
//      through the camp. Story: they kept the one thing they could sell, the man, where the watch stands over him.
//   C is the strongest: it gives the stockade one tall hero mass that is also the story's goal (the player sees the
//   lookout from far off and the cage is under it), an asymmetric silhouette (low gate tower at the front, tall
//   lookout at the back corner, a pale tent in the other corner), and it keeps the 49 guard columns round the fire
//   open. A is the safe one; B is told better in the wreck. Built: C.
//
// Coordinates: x across the front (west 0 -> east 18), y up (0 = the ground layer itself: the template replaces the
// top ground block), z from the front (z = 0, north, the gate) to the back (z = 16). Middle column (9, 1, 8): the fire.
//
// Masses (named parts the owner's feedback can point at):
//   Palisade     the ring of upright dark oak stakes on x 0 / x 18 / z 0 / z 16, y 1..4 to 1..6 with a ragged crest (a
//                13-stake rhythm of heights, every stake of 5 or more spiked with a fence point), a lashing rail on its inner face at y 3, a rubble footing at y 0.
//   Gate tower   over the way in (x 8..10, z 0): posts at x 7 and 11 (z 0 and 2) up to y 6, boarded sides, a fighting
//                deck at y 4 with its rail, a 45-degree spruce roof (eaves y 6, ridge y 9) with a boarded birch gable.
//                Three dark oak fence gates hung open (the carter must walk out through them).
//   Lookout      the hero: four posts at (13, 9), (17, 9), (13, 14), (17, 14) up to y 8, a top plate, a birch deck
//                at y 4 (the cage's ceiling), a board breastwork and shutters of spruce trapdoors, a spruce roof (eaves y 9, ridge y 11) along z, a black banner
//                on a pole over the ridge (y 13). Reached by a ladder on its west side.
//   Cage         under the lookout, inside x 14..16, z 11..13: barred front (z 10) and west side (x 13) toward the
//                yard, boarded birch back and east; the iron door at (14, 1, 10) with a jamb post at x 15 and the lever
//                on the jamb's front at (15, 2, 9); the hay at (15, 1, 13); the carter's cell (14, 1, 12).
//   Tent         the caravan's canvas over a dark ridge pole: x 2..6, z 12..15, ridge (stripped dark oak) at x 4, y 3,
//                mouth to the fire (z 12), red stripe along its hems; a bedroll inside.
//   Spoils       the shipment piled at the back middle (x 7..12, z 13..15): the loot chest at (7, 1, 14), barrels,
//                bolts of cloth, a stack of oak logs, a cart wheel leaning on the back wall.
//   Fire ring    the campfire at the middle (9, 1, 8) in a ring of mossy rubble slabs, three log benches round it, all
//                one block high: the guards' ground (x 6..12, z 5..11) stays open to the sky.
//   Yard         a woodpile and an archery target in the west-front quarter; a hitching rail and a trough in the
//                east-front quarter; trampled paths from the gate to the fire, the cage and the tent.

export const SIZE = [19, 14, 17] // w, h, d (19 x 17 pinned by theStockadeLiesOnUnevenGround)

// Palette (60/30/10): dark oak (stakes, posts, plates) / earth and birch (the trampled ground, decks, boards, gables:
// the pale boards of an illager outpost) / accents: spruce roofs, the caravan's white-and-red canvas, iron, rubble.
// Why: the raiders build like the outposts the player knows (dark oak and birch), the white canvas and red stripe
// are visibly the caravan's, and the pale boards keep the ring from reading as one dark mass.
export const PALETTE = {
  stake: 'dark_oak_log',
  patch: 'spruce_log', // about one stake in four: the ring patched with whatever timber came to hand
  stakeTip: 'dark_oak_fence',
  frame: 'dark_oak_log',
  wale: 'stripped_dark_oak_log',
  boards: 'birch_planks',
  deck: 'birch_planks',
  rail: 'dark_oak_fence',
  screen: 'spruce_trapdoor',
  roof: { full: 'spruce_planks', stairs: 'spruce_stairs', slab: 'spruce_slab' },
  ridgeCap: 'dark_oak_slab',
  gate: 'dark_oak_fence_gate',
  footing: [['cobblestone', 4], ['mossy_cobblestone', 3], ['andesite', 1]],
  ground: [['coarse_dirt', 7], ['rooted_dirt', 2], ['gravel', 1]],
  path: 'packed_mud', // trampled earth: the guards' beaten paths
  canvas: 'white_wool',
  stripe: 'red_wool',
  hem: 'white_carpet',
  tentRidge: 'stripped_dark_oak_log',
  bars: 'iron_bars',
  cageDoor: 'iron_door',
  fireCurb: 'mossy_cobblestone_slab',
  bench: 'stripped_dark_oak_log',
  banner: 'black_banner',
  skull: 'skeleton_skull',
}

export const MIDDLE = [9, 1, 8]
export const GUARD_GROUND = { x0: 6, x1: 12, z0: 5, z1: 11 } // ±3 of the middle: nothing over y 1 here

export const MASSES = {
  palisade: { x0: 0, z0: 0, x1: 18, z1: 16, y0: 1, rhythm: [5, 4, 6, 4, 4, 5, 4, 6, 5, 4, 5, 4, 4].map(h => ({ h, tip: h >= 5 })) },
  gateTower: { posts: [[7, 0], [11, 0], [7, 2], [11, 2]], top: 6, deckY: 4, way: { x0: 8, x1: 10 }, roof: { x0: 6, x1: 12, z0: 0, z1: 3, eaveY: 6 } },
  lookout: { x0: 13, z0: 9, x1: 17, z1: 14, deckY: 4, topY: 8, roof: { axis: 'z', across: [13, 17], along: [8, 14], eaveY: 9, steep: false } },
  cage: { inside: { x0: 14, x1: 16, z0: 11, z1: 13 }, y0: 1, y1: 3 },
  tent: { axis: 'z', across: [2, 6], along: [12, 15], y0: 1, ridge: { x: 4, y: 3, z0: 11, z1: 15 } },
}

// The pinned blocks (CaravanTrailGameTests.theStockadeLiesOnUnevenGround, CaravanTrail.LOOT_CHEST / CAGE_DOOR)
export const PINS = {
  palisadeLog: [0, 2, 6],
  gate: [8, 1, 0],
  fire: [9, 1, 8],
  tentRidge: [4, 3, 12],
  bars: [13, 1, 10],
  lever: [15, 2, 9],
  door: [14, 1, 10],
  chest: [7, 1, 14],
  hay: [15, 1, 13],
  carter: [14, 1, 12],
}

export const ROOMS = [
  { name: 'Cage', use: 'the carter locked in, the straw marker at the back', box: [14, 1, 11, 16, 3, 13] },
  { name: 'Tent', use: 'the raider captain\'s bedroll', box: [3, 1, 12, 5, 1, 14] },
  { name: 'Lookout', use: 'the watch over the cage and the gate', box: [14, 5, 10, 16, 8, 13] },
  { name: 'Gate deck', use: 'the fighting deck over the way in', box: [8, 5, 0, 10, 6, 2] },
]

export const OPENINGS = {
  gateway: { cells: [[8, 0], [9, 0], [10, 0]], height: 3, gatesOpen: true },
  cageDoor: { at: PINS.door, wall: 'x', jambSide: 1, out: 'north' },
  doorRecess: { cells: [[14, 9], [15, 9], [16, 9]] }, // the open porch under the lookout's front, the lever on its jamb
  lookoutLadder: { x: 12, z: 13, y0: 1, y1: 4, facing: 'west', gap: [13, 13] },
  gateLadder: { x: 6, z: 1, y0: 1, y1: 4, facing: 'west', gap: [7, 1] },
  tentMouth: { cells: [[3, 12], [4, 12], [5, 12]] },
}

// Every piece of furniture, footprint first (kind: a furniture.py kind recorded as a spot, or a fixture).
export const FURNITURE = [
  { room: 'Tent', kind: 'bed', part: 'bed', pos: [5, 1, 13], facing: 'south', colour: 'brown' }, // head to the tent's back
  { room: 'Tent', kind: 'fixture', block: 'barrel', pos: [3, 1, 14] },
  { room: 'Cage', kind: 'fixture', block: 'hay_block', pos: PINS.hay }, // the straw marker
  { room: 'Cage', kind: 'fixture', block: 'cauldron', pos: [16, 1, 11] }, // his water
  { room: 'Spoils', kind: 'fixture', block: 'chest', pos: PINS.chest, facing: 'north' }, // the loot chest (the only plain chest)
]

// The walking line: the carter's way out, from his cell through the opened door, across the yard and out of the gate
// (2 high clear, no shut gate on it); and the branches to the tent's mouth and the lookout's ladder.
export const WALK = [
  [14, 1, 12], [14, 1, 11], [14, 1, 10], [14, 1, 9], [14, 1, 8], [14, 1, 7], [13, 1, 6], [13, 1, 5], [12, 1, 4],
  [11, 1, 3], [10, 1, 2], [9, 1, 1], [9, 1, 0],
]
export const BRANCHES = [
  [[9, 1, 2], [8, 1, 3], [7, 1, 4], [6, 1, 5], [5, 1, 6], [5, 1, 7], [5, 1, 8], [5, 1, 9], [5, 1, 10], [5, 1, 11]], // to the tent's mouth
]

export const OUTSIDE = {
  woodpile: { x: 1, z0: 2, z1: 5 },
  target: { post: [4, 1, 2] },
  hitchRail: { z: 3, x0: 14, x1: 17 },
  trough: [17, 1, 5],
  lamps: [[2, 1, 9], [12, 1, 2]], // lanterns on fence posts
  skulls: [6, 12], // the stakes either side of the gate tower wear skulls
}
