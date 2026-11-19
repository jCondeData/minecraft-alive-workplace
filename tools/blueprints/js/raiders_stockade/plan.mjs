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
// Round 2 (REBUILD.md step 3, the critic's round-1 notes; concept, pins, lever wiring and loot.py kept):
//   1. Yard front half bare (cut2_top): it is now where the raid is shared out. The caravan's own cover, white with
//      its red hem, is spread on the ground between the gate and the fire with the goods sorted on it (a pot, a
//      barrel, bolts of cloth); its cart lies stripped in the front-east quarter, wheels off, hoops bare, the load still
//      aboard, shafts dropped; the front-west quarter is the woodpile, a chopping stump and the ladder up the new
//      perch (note 4), whose underside is the raiders' armoury (an anvil, a grindstone, a barrel of arrows).
//   2. Palisade one flat plane (back/left/right): the stakes now stand one block in (the S-line), and on the outer
//      line stand proud posts (taller than the stakes, spiked, the corners tallest) with a stepped rubble bank
//      between them on every side: three planes (post, bank, stakes) and a shadow under every stake run. The inner
//      lashing rail stays.
//   3. Pale birch walls (front/back): the cage tower's and gatehouse's boards, decks and breastworks are spruce,
//      framed by the dark oak posts and lintels, with stripped dark oak tie logs along every deck edge and across
//      every gable's foot (a paler line round each panel), the cage's outer walls are stakes; birch is left only as
//      an accent (the perch's propped shutters).
//   4. Roofs in one lump (left): the west side gets its own skyline: a fighting perch built over the west wall's
//      middle (the pinned palisade log is its front corner post), set where, seen from the west, it stands clear of
//      the lookout behind it, its deck hung out over the bank as a hoarding on corbels, a hipped hat and the
//      caravan's captured red banner on a pole. The skyline now has three forms at three heights: the gate's gable
//      (ridge y 9), the perch's hat (cap y 10, banner y 12), the lookout's gable (ridge y 11, banner y 13).
//   Aside (guard on the campfire): the fire's cell is pinned in the middle column, which ArcEffects.offset can pick;
//      a design can't keep it out of the 49 columns. Noted for the ship step: the arc could skip a campfire column.
//   Interiors: every room is furnished and lit for eye-level shots: the cage (a straw bale, his water, a chain, a
//      bedroll, a lantern), the tent (bed, lectern with the caravan's route, barrel, lantern), the lookout
//      (fletching table, barrel, a seat, a lamp on a chain), the gate deck (a barrel, a lamp from the ridge), the
//      perch (a lamp from its cap), the armoury (anvil, grindstone, barrel, lamp), the cage porch (the guards' keg).
//   Also: the showcase films the carter from (15, 2.9, 6); the recipe now checks that view stays clear.
//
// Coordinates: x across the front (west 0 -> east 18), y up (0 = the ground layer itself: the template replaces the
// top ground block), z from the front (z = 0, north, the gate) to the back (z = 16). Middle column (9, 1, 8): the fire.
//
// Masses (named parts the owner's feedback can point at):
//   Palisade     two lines: the outer line (x 0 / x 18 / z 0 / z 16) is a rubble footing, a stepped bank of rubble
//                stairs and proud dark oak posts every 3 to 6 blocks (the corners 7 high, the others 6, spiked; skulls
//                either side of the gate); the stake line one in (x 1 / x 17 / z 1 / z 15), y 1..4 or 5 in a ragged
//                rhythm, about one stake in four a patched spruce log, with a lashing rail on its inner face at y 3.
//   Gate tower   over the way in (x 8..10, z 0..3): posts at x 7 and 11 (z 0 and 3) up to y 6, spruce-boarded sides
//                under a stripped tie rail, a lintel over the gates, a fighting deck at y 4 edged with tie logs, a
//                breastwork of propped spruce planks, boarded sides up to the roof, a 45-degree spruce roof (eaves
//                y 6, ridge y 9) with spruce gables round a dark king post over a tie beam. Three dark oak fence
//                gates hung open (the carter must walk out through them). Ladder up its west side.
//   Lookout      the hero: four posts at (13, 9), (17, 9), (13, 14), (17, 14) up to y 8, a top plate, a spruce deck
//                at y 4 (the cage's ceiling), a spruce breastwork and shutters of spruce trapdoors, a spruce roof
//                (eaves y 9, ridge y 11) along z, a black banner on a pole over the ridge (y 13). Its east posts stand
//                in the stake line with proud buttress posts outside them. Reached by a ladder on its west side.
//   Cage         under the lookout, inside x 14..16, z 11..13: barred front (z 10) and west side (x 13) toward the
//                yard, stakes at the back and east (the palisade itself), a stake cheek beside the door's porch; the iron door at (14, 1, 10) with a jamb post at x 15 and the
//                lever on the jamb's front at (15, 2, 9); the hay at (15, 1, 13); the carter's cell (14, 1, 12).
//   Perch        over the west wall's middle, x 0..4, z 6..10: posts at the corners (the pinned log (0, 6) is its
//                front corner post) and a middle post at (0, 8), a deck at y 3 hung out over the bank on dark oak
//                corbels, a spruce breastwork, propped birch shutters, a hipped hat (eaves y 8, cap y 10) and the
//                captured banner over it (y 12). Under it, the armoury, open to the yard. Ladder up its north side.
//   Tent         the caravan's canvas over a dark ridge pole: x 2..6, z 12..14, ridge (stripped dark oak) at x 4, y 3,
//                from z 11 (out over the mouth on a forked pole) to z 14; mouth to the fire (z 12), red stripe
//                along its hems.
//   Spoils       the shipment piled at the back middle (x 7..12, z 12..14): the loot chest at (7, 1, 14), barrels,
//                bolts of cloth, a stack of oak logs, a cart wheel leaning on the back wall.
//   Fire ring    the campfire at the middle (9, 1, 8) in a ring of mossy rubble slabs, three log benches round it, all
//                one block high: the guards' ground (x 6..12, z 5..11) stays open to the sky.
//   Share-out    the caravan's cover spread on the ground (x 6..9, z 4..6), the goods sorted on it.
//   Wagon        the caravan's cart, stripped: its bed x 13..16, z 3..5 down on the ground (y 1), its wheels taken
//                off (one leans on the front wall behind it, hub (15, 2, 2)), bare hoops, the load left aboard, shafts
//                dropped at x 12. Everything at z 5 or more on the cage's side stays at most 1 high: the showcase's
//                camera stands at (15, 2.9, 6) looking into the cage.

export const SIZE = [19, 14, 17] // w, h, d (19 x 17 pinned by theStockadeLiesOnUnevenGround)

// Palette (60/30/10): dark oak (stakes, posts, plates, lintels) / spruce and earth (the boards, decks, roofs, and the
// trampled ground) / accents: the caravan's white-and-red canvas, rubble, iron, birch shutters on the perch.
// Why: the raiders build like the outposts the player knows (dark oak frames), the spruce infill keeps the towers a
// shade lighter than the stakes without the pale flat birch of round 1, and the white canvas and red stripe are
// visibly the caravan's: they mark the tent, the share-out cloth and the bare wagon as the stolen goods.
export const PALETTE = {
  stake: 'dark_oak_log',
  patch: 'spruce_log', // about one stake in four: the ring patched with whatever timber came to hand
  stakeTip: 'dark_oak_fence',
  frame: 'dark_oak_log',
  tie: 'stripped_dark_oak_log', // the tie rails and deck edges: a paler line across the dark frames
  bracket: 'dark_oak_stairs', // the corbels under the perch's hoarding
  wale: 'stripped_dark_oak_log',
  boards: 'spruce_planks',
  deck: 'spruce_planks',
  rail: 'dark_oak_fence',
  screen: 'spruce_trapdoor',
  accentScreen: 'birch_trapdoor', // the only birch: the perch's propped shutters
  roof: { full: 'spruce_planks', stairs: 'spruce_stairs', slab: 'spruce_slab' },
  ridgeCap: 'dark_oak_slab',
  gate: 'dark_oak_fence_gate',
  footing: [['cobblestone', 4], ['mossy_cobblestone', 3], ['andesite', 1]],
  bank: [['cobblestone_stairs', 3], ['mossy_cobblestone_stairs', 2]],
  ground: [['coarse_dirt', 6], ['rooted_dirt', 2], ['gravel', 1], ['packed_mud', 1]],
  path: 'packed_mud', // trampled earth: the guards' beaten paths
  canvas: 'white_wool',
  stripe: 'red_wool',
  hem: 'white_carpet',
  cloth: { border: 'red_carpet', field: 'white_carpet' },
  tentRidge: 'stripped_dark_oak_log',
  bars: 'iron_bars',
  cageDoor: 'iron_door',
  fireCurb: 'mossy_cobblestone_slab',
  bench: 'stripped_dark_oak_log',
  banner: 'black_banner',
  captured: 'red_banner', // the caravan's own red, flown by the raiders as a trophy
  skull: 'skeleton_skull',
  wagon: { beam: 'spruce_log', deck: 'spruce_planks', slab: 'spruce_slab', rim: { full: 'spruce_planks', stairs: 'spruce_stairs' }, hub: 'stripped_spruce_log', hoop: 'spruce_fence' },
}

export const MIDDLE = [9, 1, 8]
export const GUARD_GROUND = { x0: 6, x1: 12, z0: 5, z1: 11 } // ±3 of the middle: nothing over y 1 here

const OUTER = { x0: 0, z0: 0, x1: 18, z1: 16 }
const STAKES = { x0: 1, z0: 1, x1: 17, z1: 15 }
export const MASSES = {
  palisade: {
    outer: OUTER,
    stakes: STAKES,
    y0: 1,
    rhythm: [4, 5, 4, 4, 5, 4, 5, 4, 4, 5, 4].map(h => ({ h, tip: h >= 5 })),
    // the proud posts on the outer line, [x, z]: corners, the gate tower's own posts are its, the perch's are its
    posts: [
      [0, 0], [3, 0], [15, 0], [18, 0], // front (the gate tower's posts at 7 and 11 stand in this line too)
      [0, 16], [4, 16], [9, 16], [14, 16], [18, 16], // back
      [0, 3], [0, 13], // west (0, 6), (0, 8), (0, 10) are the perch's
      [18, 4], [18, 9], [18, 14], // east: 9 and 14 buttress the lookout's posts
    ],
    skulls: [[3, 0], [15, 0]], // the front posts either side of the gate wear skulls
  },
  gateTower: { posts: [[7, 0], [11, 0], [7, 3], [11, 3]], top: 6, deckY: 4, way: { x0: 8, x1: 10 }, roof: { x0: 6, x1: 12, z0: 0, z1: 3, eaveY: 6 } },
  lookout: { x0: 13, z0: 9, x1: 17, z1: 14, deckY: 4, topY: 8, roof: { axis: 'z', across: [13, 17], along: [8, 14], eaveY: 9, steep: false } },
  cage: { inside: { x0: 14, x1: 16, z0: 11, z1: 13 }, y0: 1, y1: 3 },
  perch: { x0: 0, z0: 6, x1: 4, z1: 10, deckY: 3, topY: 7, midPost: [0, 8], hatY: 8 },
  tent: { axis: 'z', across: [2, 6], along: [12, 14], y0: 1, ridge: { x: 4, y: 3, z0: 11, z1: 14 }, forks: [[3, 11]] },
  wagon: { bed: { x0: 13, x1: 16, z0: 3, z1: 5, y: 1 }, wheel: [15, 2, 2], hoops: [14, 16], shafts: [[12, 3], [12, 5]] },
  shareOut: { x0: 6, z0: 4, x1: 9, z1: 6, y: 1 },
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
  { name: 'Tent', use: 'the raider captain\'s bed and his table', box: [3, 1, 12, 5, 2, 13] },
  { name: 'Lookout', use: 'the watch over the cage and the gate', box: [14, 5, 10, 16, 8, 13] },
  { name: 'Gate deck', use: 'the fighting deck over the way in', box: [8, 5, 0, 10, 6, 3] },
  { name: 'Perch', use: 'the archers\' hoarding over the west wall', box: [1, 4, 7, 3, 6, 9] },
  { name: 'Armoury', use: 'under the perch: the raiders whet their axes', box: [2, 1, 7, 3, 2, 9] },
]

export const OPENINGS = {
  gateway: { cells: [[8, 0], [9, 0], [10, 0]], height: 3, gatesOpen: true },
  cageDoor: { at: PINS.door, wall: 'x', jambSide: 1, out: 'north' },
  doorRecess: { cells: [[14, 9], [15, 9], [16, 9]] }, // the open porch under the lookout's front, the lever on its jamb
  lookoutLadder: { x: 12, z: 13, y0: 1, y1: 4, facing: 'west', gap: [13, 13] },
  gateLadder: { x: 6, z: 2, y0: 1, y1: 4, facing: 'west', gap: [7, 2] },
  perchLadder: { x: 2, z: 5, y0: 1, y1: 3, facing: 'north', gap: [2, 6] },
  armouryMouth: { x: 4, z0: 7, z1: 9 }, // the armoury opens to the yard between the perch's east posts
  tentMouth: { cells: [[3, 12], [4, 12], [5, 12]] },
}

// Every piece of furniture, footprint first (kind: a furniture.py kind recorded as a spot, or a fixture).
export const FURNITURE = [
  { room: 'Tent', kind: 'bed', part: 'bed', pos: [5, 1, 12], facing: 'south', colour: 'brown' }, // head to the tent's back
  { room: 'Tent', kind: 'fixture', block: 'barrel', pos: [3, 1, 13] },
  { room: 'Tent', kind: 'fixture', block: 'lectern', pos: [3, 1, 12], facing: 'east' }, // the caravan's route, read by the captain
  { room: 'Cage', kind: 'fixture', block: 'hay_block', pos: PINS.hay }, // the straw marker
  { room: 'Cage', kind: 'fixture', block: 'cauldron', pos: [16, 1, 11] }, // his water
  { room: 'Cage', kind: 'fixture', block: 'brown_carpet', pos: [16, 1, 12] }, // his bedroll
  { room: 'Lookout', kind: 'fixture', block: 'fletching_table', pos: [16, 5, 12] },
  { room: 'Lookout', kind: 'fixture', block: 'barrel', pos: [16, 5, 13] },
  { room: 'Lookout', kind: 'fixture', block: 'spruce_stairs', pos: [14, 5, 10], facing: 'north' }, // a seat looking out over the yard
  { room: 'Gate deck', kind: 'fixture', block: 'barrel', pos: [10, 5, 2] },
  { room: 'Cage porch', kind: 'fixture', block: 'barrel', pos: [15, 1, 9] }, // the guards' keg, under the lever
  { room: 'Armoury', kind: 'fixture', block: 'anvil', pos: [2, 1, 7], facing: 'east' },
  { room: 'Armoury', kind: 'fixture', block: 'barrel', pos: [2, 1, 8] },
  { room: 'Armoury', kind: 'fixture', block: 'grindstone', pos: [2, 1, 9], facing: 'east' },
  { room: 'Spoils', kind: 'fixture', block: 'chest', pos: PINS.chest, facing: 'north' }, // the loot chest (the only plain chest)
]

// The walking line: the carter's way out, from his cell through the opened door, across the yard and out of the gate
// (2 high clear, no shut gate on it, orthogonal steps); and the branches to the tent's mouth, the lookout's ladder
// and the chest.
export const WALK = [
  [14, 1, 12], [14, 1, 11], [14, 1, 10], [14, 1, 9], [14, 1, 8], [14, 1, 7], [13, 1, 7], [12, 1, 7], [12, 1, 6],
  [11, 1, 6], [11, 1, 5], [11, 1, 4], [10, 1, 4], [10, 1, 3], [9, 1, 3], [9, 1, 2], [9, 1, 1], [9, 1, 0],
]
export const BRANCHES = [
  [[9, 1, 4], [8, 1, 4], [7, 1, 4], [6, 1, 4], [5, 1, 4], [5, 1, 5], [5, 1, 6], [5, 1, 7], [5, 1, 8], [5, 1, 9], [5, 1, 10], [5, 1, 11], [4, 1, 11], [4, 1, 12]], // to the tent's mouth
  [[12, 1, 11], [12, 1, 12]], // to the lookout's ladder
  [[7, 1, 11], [7, 1, 12], [7, 1, 13]], // to the chest
]

export const OUTSIDE = {
  woodpile: { x0: 2, x1: 5, z0: 2, z1: 2, h: 2 }, // against the front wall, ends to the yard
  stump: [4, 1, 3],
  trough: [16, 1, 8],
  lamps: [[2, 1, 11], [12, 1, 2]], // lanterns on fence posts
  goods: [ // sorted on the share-out cloth
    ['decorated_pot', [7, 1, 5], { facing: 'north', cracked: 'false', waterlogged: 'false' }],
    ['barrel', [8, 1, 6], { facing: 'up', open: 'true' }],
    ['white_wool', [9, 1, 5], {}], ['red_wool', [6, 1, 6], {}],
  ],
}
