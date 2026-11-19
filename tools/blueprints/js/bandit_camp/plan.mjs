// The Bandit Camp (camp/bandit_camp), the plan: drawn before any tent. The brief is tools/blueprints/briefs/bandit_camp.md;
// recipe.mjs assembles this plan from the parts library (../parts, bandit_camp_lair.mjs grown for it).
//
// Three rough concepts (massing, roof, plan, story):
//   1. Ring of tents: four equal ridge tents on the four sides, mouths to the fire, all peaking at y 3. Story: a band on
//      the move pitches what it carries. Safe and readable, but a flat, even ring with no hero, the same from every
//      side (the cleanup's turn-matching likes a camp that is NOT the same turned a quarter), and nothing says "bandit".
//   2. Wagon laager: three stolen carts drawn up in a horseshoe round the fire, canvas strung from their hoops to
//      stakes as lean-tos, the chief sleeping in the covered one. Story: they ride the trade roads and live in what they
//      took. A good story, but long, low wagons do not fit 4-wide strips round a 7-wide plaza, the silhouette stays
//      under y 4, and the hoops crowd the open ground the code stands men on.
//   3. The Lookout Camp: an asymmetric horseshoe of mended tents round the fire (two low bunk tents of patched,
//      stolen cloth in the west corners, the chief's black-and-red marquee across the back with his standard), one
//      stolen cart heaped with plunder by the way in, and a crow's nest on four legs in the south-east corner rising
//      three times the tents' height to a red flag, with a village bell they stole hung under its deck as the alarm.
//      Story: they watch the village road from the nest; the smoke climbs past it. One hero you'd know in outline from
//      20 blocks, a clear front-to-back order (way in, fire, chief), and every corner different.
// Built: 3, the strongest. The nest gives the camp the vertical hero and the "they are watching you" story the
// village's warning line wants; the patchwork cloth and the stolen bell and cart tell who these people are; and its
// lopsided plan makes the right turn the clear best for CampCleanup.turn.
//
// Coordinates: x 0..14 (west -> east), z 0..12 (north -> south), y 0..11; y = 0 is the ground layer itself (it
// replaces the world's top block). There is no front (it is set down turned at random); "north" below is only the
// drawing's z = 0 side, where the way in is.
//
// Named parts (masses):
//   Fire        the lit campfire at (7, 1, 6) (fixed by the brief), open sky above, in a ring of rough stone; a log
//               bench west of it and two stumps east (the bounty captain's pole lands between them, at (9, 1, 6)).
//   Plaza       the trodden round of ground r² <= 13 about the fire kept open (feet and head free) for the band, the
//               chief and the banners; its ground drawn as trampled earth out to r² 18.
//   Bunk tents  A (north-west, x 0..4, z 0..3, mouth south) and B (south-west, x 0..4, z 9..12, mouth north): 5-wide
//               pitched tents of patched brown cloth peaking at y 3, a ridge pole on top running a cell out over the
//               mouth with a lantern under it, two beds each either side of a 2-high aisle.
//   Marquee     the chief's tent across the back (x 5..9, z 10..12): black cloth with red stripes, open along its front
//               on two posts so the chief stands in its mouth at (7, 1, 9); its roof climbs from the eave (y 4) to the
//               back (y 6); his bed, his seat facing the fire, the loot chest, his colours on the back wall, his
//               standard on the roof.
//   Nest        the crow's nest (x 11..14, z 9..12): four spruce legs, braces, brackets, a plank deck at y 6 with a
//               rail, a ladder up the south side, the flag leg to y 11; the stolen bell and the camp's stash under it.
//   Cart        the stolen handcart (x 11..14, z 0..3) on two wheels, heaped with plunder, its shafts down.
//   Way in      the north gap (x 6..8) between two trophy stakes (a skull, a lantern); gaps to the west (z 6..7),
//               east (z 5..6) and south (x 10) reach every edge, as the brief's open camp ground asks.
//   Edges       a woodpile and a crossbow target in the west gap, a hitching rail and hay in the east gap.

export const SIZE = [15, 12, 13] // fixed by the brief: size / 2 is the fire

// Palette (60/30/10): cloth / timber / accents.
//   60 cloth: weathered canvas (white wool) for the band's tents, mended with rectangles of brown and gray (stolen
//      cloth), brown hems at their feet; black wool for the chief's marquee. (Round 1 drew the bunk tents in brown wool:
//      in the renders they melted into the spruce timber, so the canvas went pale and the timber stands out on it.) Wool is also what the camp-count tests read as "the camp's shelters".
//   30 timber: spruce (legs, posts, deck, cart, rails), stripped spruce for the seats and ridge poles.
//   10 accents: red (the band's colour: the marquee's stripes, the standard and the flag, the hems), rough stone round
//      the fire, iron (the stolen bell, the grindstone).
// Why: dark, dirty, mended cloth reads as outlaws, not a village fair; red marks every place the band claims (the
// chief, the flag, the hems), and the light spruce timber picks out the nest against the cloth.
export const PALETTE = {
  clothA: { base: 'white_wool', patches: [{ x: [3, 4], y: [1, 2], z: [1, 2], cloth: 'light_gray_wool' }, { x: [0, 1], y: [1, 1], z: [0, 0], cloth: 'brown_wool' }] },
  clothB: { base: 'white_wool', patches: [{ x: [0, 1], y: [1, 2], z: [10, 11], cloth: 'brown_wool' }, { x: [3, 3], y: [2, 2], z: [12, 12], cloth: 'light_gray_wool' }] },
  hemA: 'brown_carpet', hemB: 'brown_carpet', // on the lowest ledge only (one up: a man can step there)
  chief: 'black_wool', stripe: 'red_wool', valance: 'red_carpet',
  timber: { wheel: { full: 'dark_oak_planks', stairs: 'dark_oak_stairs' }, log: 'spruce_log', planks: 'spruce_planks', fence: 'spruce_fence', stairs: 'spruce_stairs', slab: 'spruce_slab', stripped: 'stripped_spruce_log' },
  stone: { stairs: [['cobblestone_stairs', 3], ['mossy_cobblestone_stairs', 1]], slabs: [['cobblestone_slab', 2], ['mossy_cobblestone_slab', 1], ['andesite_slab', 1]] },
  // ground layer (y = 0): trampled earth round the fire (never only dirt, so the drawn pad shows), packed mud in the
  // bunk tents, boards in the chief's, worn paths out of the gaps; every other cell is left to the world.
  trampled: [['coarse_dirt', 5], ['dirt', 2], ['packed_mud', 2], ['rooted_dirt', 1]],
  hearth: 'cobblestone', tentFloor: 'packed_mud', chiefFloor: 'spruce_planks', path: 'dirt_path', pad: 'cobblestone', nestFloor: 'coarse_dirt',
  flag: 'red_banner', colours: 'red_wall_banner',
}

export const FIRE = [7, 1, 6] // the brief's fixed fire
export const CHIEF_SPOT = [7, 1, 9] // unchanged from the brief: in the marquee's mouth, facing the fire
export const BAND_SPOTS = [[4, 1, 7], [10, 1, 7], [7, 1, 3], [6, 1, 4], [8, 1, 4], [3, 1, 6], [11, 1, 6], [8, 1, 3]] // unchanged
export const POLE = [9, 1, 6] // unchanged: open, between the two stumps

export const MASSES = {
  plaza: { r2Open: 13, r2Drawn: 18 },
  tentA: { axis: 'z', across: [0, 4], along: [0, 3], y0: 1, wall: 1, back: 'l0', mouth: { width: 1, height: 2 }, pole: { from: 0, to: 4 }, lamp: [2, 3, 4] },
  tentB: { axis: 'z', across: [0, 4], along: [9, 12], y0: 1, wall: 1, back: 'l1', mouth: { width: 1, height: 2 }, pole: { from: 8, to: 12 }, lamp: [2, 3, 8] },
  marquee: { x0: 5, x1: 9, zFront: 10, zBack: 12, y0: 1, eaveY: 4, standard: [7, 7, 12] },
  nest: { x0: 11, x1: 14, z0: 9, z1: 12, y0: 1, deckY: 6, braceY: 3, hatch: [12, 12], ladderFacing: 'east', openSide: 'south', flagLeg: { x: 14, z: 9, top: 9 }, bell: [13, 5, 11], lamp: [11, 8, 9] },
  cart: { hubs: [[12, 2, 0], [12, 2, 3]], bed: { x0: 11, x1: 13, z0: 1, z1: 2, y: 2 }, shafts: [[14, 1, 1], [14, 1, 2]] },
}

export const OPENINGS = {
  wayIn: { x: [6, 8], z: [0, 2], stakes: [[5, 1, 1], [9, 1, 1]] }, // north
  west: { x: [0, 2], z: [6, 7] },
  east: { x: [11, 14], z: [5, 6] },
  south: { x: [10, 10], z: [9, 12] }, // the alley between the marquee and the nest
  mouths: { tentA: [2, 1, 3], tentB: [2, 1, 9], marquee: [[6, 1, 10], [8, 1, 10]] },
}

// Every piece of furniture, footprint first (furniture.py kinds; fixtures are vanilla blocks used as themselves).
// The loot chest is (8, 1, 11): the ship step gives it LootTable "aliveworkplace:chests/bandit_camp". The other chest
// (13, 1, 10, under the nest) has none. Five beds (10 bed blocks), as the camp-count tests expect.
export const FURNITURE = [
  // Bunk tent A: two bedrolls either side of the aisle, heads to the closed back
  { room: 'Bunk tent A', kind: 'bed', part: 'bed', pos: [1, 1, 2], facing: 'north', colour: 'brown' },
  { room: 'Bunk tent A', kind: 'bed', part: 'bed', pos: [3, 1, 2], facing: 'north', colour: 'gray' },
  // Bunk tent B: the same, heads to the south
  { room: 'Bunk tent B', kind: 'bed', part: 'bed', pos: [1, 1, 10], facing: 'south', colour: 'gray' },
  { room: 'Bunk tent B', kind: 'bed', part: 'bed', pos: [3, 1, 10], facing: 'south', colour: 'brown' },
  // The chief's marquee: his bed on the west, his seat in the middle facing the fire, the loot chest on the east,
  // a lantern on a chain over the seat, his colours on the back wall
  { room: 'Marquee', kind: 'bed', part: 'bed', pos: [6, 1, 10], facing: 'south', colour: 'red' },
  { room: 'Marquee', kind: 'chair', part: 'chair', pos: [7, 1, 11], facing: 'north', wood: 'spruce' },
  { room: 'Marquee', kind: 'fixture', part: 'chest', pos: [8, 1, 11], facing: 'west', block: 'chest' }, // the loot chest
  { room: 'Marquee', kind: 'lamp', part: 'hangingLamp', pos: [7, 4, 11], facing: 'north', wood: 'spruce' },
  { room: 'Marquee', kind: 'picture', part: 'picture', pos: [6, 3, 11], facing: 'north', colour: 'red', wood: 'spruce' },
  { room: 'Marquee', kind: 'picture', part: 'picture', pos: [8, 3, 11], facing: 'north', colour: 'red', wood: 'spruce' },
  // Under the nest: the stash (the second chest, no loot table) and a barrel
  { room: 'Nest', kind: 'fixture', part: 'chest', pos: [13, 1, 10], facing: 'west', block: 'chest' },
  { room: 'Nest', kind: 'fixture', part: 'chest', pos: [12, 1, 10], facing: 'up', block: 'barrel' },
]

// Outside pieces (not furniture spots)
export const OUTSIDE = {
  bench: { cells: [[5, 1, 5], [5, 1, 6], [5, 1, 7]] }, // a log along z, west of the fire
  stumps: [[9, 1, 5], [9, 1, 7]],
  woodpile: [[0, 1, 4], [0, 1, 5], [0, 2, 4]],
  target: { hay: [0, 1, 8], target: [0, 2, 8] },
  hitch: { x: 13, z0: 4, z1: 8, y: 1 }, hay: [[14, 1, 7], [14, 1, 4]],
  grindstone: [12, 1, 4],
  plunder: { barrels: [[11, 3, 1], [12, 3, 2]], hay: [[11, 3, 2]], bale: [[12, 3, 1]], tarp: [[13, 3, 1], [13, 3, 2]], heap: [[10, 1, 0]] },
}

// The walking line: in at the north gap, past the stakes, to the fire, round it to the chief's mouth; off it to each
// tent's mouth and up the nest's ladder.
export const WALK = [
  [7, 1, 0], [7, 1, 1], [7, 1, 2], [7, 1, 3], [7, 1, 4], [6, 1, 5], [5, 1, 4], [4, 1, 5], [4, 1, 7], [5, 1, 8], [6, 1, 9], [7, 1, 9],
  [7, 1, 10], [7, 1, 11],
  [3, 1, 4], [2, 1, 4], [2, 1, 3], [2, 1, 2], // tent A's aisle
  [3, 1, 8], [2, 1, 8], [2, 1, 9], [2, 1, 10], // tent B's aisle
  [9, 1, 8], [10, 1, 8], [10, 1, 9], [10, 1, 11], [11, 1, 11], [12, 1, 11], [13, 1, 11], [13, 1, 12], // round the marquee, under the nest to its ladder
]
