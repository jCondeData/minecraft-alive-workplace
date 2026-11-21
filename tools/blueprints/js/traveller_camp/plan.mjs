// The Traveler's Camp (`legend/traveller_camp`, one level only), the plan: drawn before any block. ROADMAP 49 (lane B);
// the brief is tools/blueprints/briefs/traveller_camp.md. recipe.mjs assembles it from the parts library (../parts,
// tents.mjs grown for it). The id keeps its British spelling; player text says "traveler".
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Wanderer's Tipi. One tall cone of painted hide (5 across, stepped in three tiers, a ring of stairs sloping
//      each step) on a round trodden pad, its four poles crossing out of the smoke hole 11 blocks up; the door flap open
//      to the south, the traveler standing in it, a sunken fire with a spit in front, the map on a barrel at their
//      elbow, a woodpile and chopping block on one side, a lantern on a planted staff and a lodestone cairn on the
//      others. Story: someone who walks the world for a living pitched the one shelter they carry beside the ruined
//      portal they came to see, set a lodestone so their compass finds the place again, and is studying the map.
//   B. The Boulder Lean-to. A hide sheet on three poles leaning against a mossy boulder, the fire reflected off the
//      rock, a bedroll under the sheet. Honest and cosy, but low (5 high): from 20 blocks it reads as a rock pile, and
//      the boulder's back is one blank side, bad for a camp turned at random.
//   C. The Handcart Bivouac. A two-wheeled handcart tipped back on its handles with a canvas sheet stretched from its
//      side to two stakes, crates in the cart. Clear, but a cart and canvas is the Prisoner Cage's language (a wagon,
//      a ridge tent), and it says "pedlar" more than "wanderer".
// Strongest: A. A tipi is the one shelter that is the same from every side, so whichever way the camp is turned the
// player meets the same strong outline: a cone with poles crossed out of the top, recognisable in silhouette from far
// across the ruined portal's rubble, and the tallest thing there (11 high on a 9-wide pad). It is also the riskiest:
// a round, stepped cone has no box to hide in, so the cone became a part (tents.mjs `coneTent`) any later camp can
// reuse. The camp round it is laid out as a clock (door and traveler south, fire south-east, map and staff west, woodpile
// and chopping block east, cairn north-west), so every side has its own small scene.
//
// Coordinates: x 0..8, y up (0 = the ground layer itself: the template replaces the ground's top block there),
// z 0..8. No front: the camp lands turned a random quarter. The pad is drawn whole (no cell of y = 0 is air): a round
// trodden middle fading through a grassy rim to plain grass in the corners. (Corners left out of the template were
// tried; the preview shows them as pits, and grass reads more honestly as the meadow the camp was pitched in.)
//
// THE TRAVELER'S SPOT STAYS (4, 1, 5) (LegendSites.java, legend_sites.py TRAVELLER_SPOT): in the tipi's door, one
// step out, facing the fire. Feet (4, 1, 5) and head (4, 2, 5) are air; (4, 0, 5) is coarse dirt (solid). A player
// can stand right beside them on open ground at (3, 1, 5) or (5, 1, 5), or anywhere in the open south-west quarter;
// the way out is open to the west, the south-west and the east whichever way the camp lands.

export const SIZE = [9, 11, 9] // w, h, d (limit 9 x 28 x 9)

export const TRAVELER = [4, 1, 5]

// Palette (60/30/10): tan hide and trodden earth (smooth sandstone: flat and leathery, with its own stairs so the cone
// slopes; the pad) / dark spruce and oak (poles, staff, firewood, the seat log) / red ochre paint (the base band and the
// smoke-dark crown in smooth red sandstone), the carved glyphs, the fire, the lanterns and the lodestone's iron.
// Why: the cone is the camp's one big mass, so it is pale and reads against any biome's ground (dark forest floor,
// grass; on sand the red bands carry it); the spruce poles crossing out of the top are the darkest marks, drawing the
// silhouette's tip; red ochre at the foot and the crown is how a painted hide tent is banded, and it tells "painted by
// hand" from far off. Wool was tried first (render r1): with no wool stairs the cone read as a stacked cake.
export const PALETTE = {
  ground: [['coarse_dirt', 6], ['dirt', 2], ['rooted_dirt', 2], ['podzol', 1]], // the trodden pad
  rim: [['grass_block', 5], ['coarse_dirt', 2], ['moss_block', 1]], // where the pad meets the world
  corner: 'grass_block', // beyond the rim
  grassProps: { snowy: 'false' },
  standing: 'coarse_dirt', // under the traveler's feet: solid, never a path
  path: 'packed_mud', // worn from the door to the fire and the map (the preview can't draw dirt paths)
  floor: [['coarse_dirt', 3], ['rooted_dirt', 1]], // inside the tipi
  hearthGround: [['gravel', 3], ['cobblestone', 1]],
  hide: { body: 'smooth_sandstone', band: 'smooth_red_sandstone', mark: 'chiseled_red_sandstone', crown: 'smooth_red_sandstone' }, // the marks: a carved red glyph
  hem: { low: 'smooth_sandstone_stairs', high: 'smooth_red_sandstone_stairs' }, // the slope of each step, climbing inward
  pole: 'spruce_fence',
  flap: 'birch_trapdoor', // the door flap, folded back against the doorway's west side (pale like the hide)
  curbStair: 'cobblestone_stairs',
  curbSlab: 'cobblestone_slab',
  spit: 'spruce_fence',
  firewood: 'oak_log', // stacked along z: bark to the east and west, cut ends north and south
  seat: 'stripped_spruce_log',
  chopping: 'oak_log',
  staff: 'spruce_fence',
  cairn: { stones: 'mossy_cobblestone', cap: 'lodestone', slab: 'cobblestone_slab', slab2: 'andesite_slab' },
  bed: 'brown', // the bedroll: a brown bed, low and dark like a rolled blanket
  rug: 'red',
}

// Masses (named parts the critic and the owner can point at):
//   Tipi      the cone about (4, 2): tier 1 the ground ring (disc r² 5: 5 across, x 2..6, z 0..4) y 1..3, the red
//             ochre band at y 1, the tan hide above with a carved red glyph on the back and both sides at y 2; a ring
//             of tan stairs on its ledge at y 4 sloping in to tier 2 (3 x 3 ring) y 4..5; red stairs on that ledge at
//             y 6 sloping in to the peak column y 6..7, red (the smoke-dark crown). Four spruce poles stand on the
//             last ledge beside the peak, lashed to it at y 7, two of them up to y 10 and two to y 9: a ragged bundle,
//             the smoke hole open between them. (Poles kinked out in a pinwheel were tried, render r3: they read as
//             aerials.) Inside: 3 high under tier 2, the
//             shaft up the middle to the peak. Door: the south middle (4, 1..2, 4), the hide above it the lintel;
//             its flap folded back against the west jamb.
//   Fire      a sunken campfire at (5, 0, 7) in a cobblestone curb (x 4..6, z 6..8), a spit across it east-west.
//   Map       the camp's only barrel at (2, 1, 5), lid up, the map lying on it in a frame at (2, 2, 5).
//   Woodpile  oak logs lying along the tipi's east side (x 7, z 1..3), two high, cut ends north and south; the chopping block (7, 1, 4).
//   Cairn     a mossy stone at (1, 1, 1), behind the tipi, capped with the traveler's lodestone, slabs fallen round its foot.
//   Staff     the traveler's walking staff planted at (1, 1, 4) by the map barrel, a lantern on its head.
//   Seat      a stripped log lying along z at (2, 1, 7..8), facing the fire.
export const MASSES = {
  tipi: {
    cx: 4, cz: 2, y0: 1, door: 'south', doorHeight: 2,
    tiers: [{ r2: 5, height: 3 }, { r2: 2, height: 2 }, { r2: 0, height: 2 }], hemStairs: true,
    poleHeight: 4, poleHeights: [4, 3, 3, 4], // beside the peak, y 7..10 and y 7..9: a ragged bundle, lashed to the peak at y 7
    marks: [[4, 2, 0], [2, 2, 2], [6, 2, 2]], // the carved glyphs: back and both sides, at shoulder height
  },
  fire: { at: [5, 0, 7], open: ['west', 'east'], spit: { xa: 4, xb: 6, z: 7, y0: 1 } },
  barrel: [2, 1, 5],
  frame: [2, 2, 5], // the map's frame, lying on the barrel (Facing 1, up): TileY 2 as the brief asks
  woodpile: [[7, 1, 1], [7, 1, 2], [7, 1, 3], [7, 2, 2], [7, 2, 3]], // long logs lying along z, two high: bark east and west
  chopping: [7, 1, 4],
  cairn: { at: [1, 1, 1], skirt: [{ at: [-1, 0], slab: 'slab' }, { at: [0, -1], slab: 'slab2' }] },
  staff: { at: [1, 1, 4], height: 2 }, // beside the map barrel: the light to read it by
  seat: [[2, 1, 7], [2, 1, 8]],
  // the round pad: every cell with dx² + dz² <= 21 about (4, 4), the outer ring of it (>= 17) the grassy rim; the
  // corners beyond it plain grass
  pad: { cx: 4, cz: 4, r2: 21, rimFrom: 17 },
  paths: [[4, 0, 4], [3, 0, 5], [3, 0, 6], [2, 0, 6], [5, 0, 5], [6, 0, 5]], // worn: the threshold, round the traveler (whose own cell stays coarse dirt) to the map and to the east
}

export const ROOMS = [
  { name: 'Tipi', use: 'the traveler\'s bed and pack, under the smoke hole\'s lantern', box: [3, 1, 1, 5, 3, 3] },
  { name: 'Camp', use: 'the fire, the map barrel, the seat log: where the traveler waits', box: [0, 1, 4, 8, 2, 8] },
]

export const OPENINGS = {
  tipiDoor: { at: [4, 1, 4], height: 2, out: 'south', flap: 'east' }, // the flap: open trapdoors facing east, so they lie against the west jamb
}

// Every piece of furniture, footprint first (the parts record each as a furniture spot). Inside the tipi: the bedroll
// along the back wall (head west), a water jug at its foot, the pack chest by the door, a red rug on the walking
// line, and a lantern on a chain down the smoke shaft. Nothing outside is furniture (the barrel, the fire and the
// seat log are the camp itself).
export const FURNITURE = [
  { room: 'Tipi', kind: 'bed', part: 'bed', pos: [4, 1, 1], facing: 'west', colour: PALETTE.bed },
  { room: 'Tipi', kind: 'fixture', part: 'chest', pos: [5, 1, 3], facing: 'west', block: 'chest' },
  { room: 'Tipi', kind: 'rug', part: 'rug', cellsAbs: [[3, 1, 2], [4, 1, 2], [3, 1, 3]], colour: PALETTE.rug }, // the door's inner cell stays bare: full headroom under the lintel
  { room: 'Tipi', kind: 'lamp', part: 'hangingLamp', pos: [4, 3, 2], facing: 'south', chain: 2, wood: 'spruce' },
]
export const JUG = { at: [5, 1, 1], block: 'decorated_pot' } // the traveler's water jug at the bed's foot

// The walking line: a player arrives from any side across the open pad to stand beside the traveler, at (3, 1, 5);
// from there into the tipi through the door. The traveler's way out is the same open ground.
export const WALK = [
  [0, 1, 5], [1, 1, 5], [1, 1, 6], [2, 1, 6], [3, 1, 6], [3, 1, 5], // from the west, past the map barrel
  [8, 1, 5], [7, 1, 5], [6, 1, 5], [5, 1, 5], // from the east, past the chopping block
  [4, 1, 4], [4, 1, 3], [4, 1, 2], // into the tipi
]
