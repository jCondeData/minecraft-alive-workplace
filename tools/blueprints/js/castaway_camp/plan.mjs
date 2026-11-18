// The Castaway Camp (`legend/castaway_camp`, one level only), the plan: drawn before any block. ROADMAP 49 (lane B);
// the brief is tools/blueprints/briefs/castaway_camp.md. recipe.mjs assembles it from the parts library (../parts,
// shore.mjs grown for it).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Upturned Boat. The wreck's own jolly boat, turned keel-up on driftwood props as the roof (a rounded hull
//      5 wide and 10 long, both ends dipping to stems in the sand), a sail draped down one side as a windbreak;
//      beside it the ship's broken mast stood up in the sand as a signal pole, its yard still across it, a torn sail
//      hanging from the yard into a cauldron that catches the rain, a red rag flying at the top; in front a stone
//      fire ring with an empty spit. Story: they rowed ashore in the boat, turned it over to sleep under, raised the
//      mast so a passing ship would see them, and have had nothing to put on the spit for days.
//   B. The Sail Tent. A big triangle of sailcloth pitched over a ridge pole between two crossed driftwood shears,
//      pegged out with the ship's rope, crates stacked round it as walls, a signal fire on a stone cairn. Tells
//      "castaway" too, but a white tent on sand reads from 20 blocks as any traveller's or miner's tent.
//   C. The Salvage Ring. A ring of washed-up crates, barrels and planks round a fire with a tarp over one half and a
//      plank lean-to: a camp made only of the wreck's cargo. Honest, but a heap: no single shape to recognise and
//      clutter all the way round.
// Strongest: A. An upturned boat is the one shape a player reads as "shipwrecked" before reading anything else: the
// keel line and the stem curling up are unmistakable from every side, and the mast gives the camp a hero vertical
// (13 high against the hull's 5) that marks it from far down the beach, which is exactly how a castaway would want to
// be found. It is also the riskiest to draw (a rounded hull in stairs, a swept bow), so the hull became a part
// (shore.mjs `beachedBoat`) other shore builds can reuse.
//
// Coordinates: x 0..9, y up (0 = the ground layer itself: the template replaces the beach's top block), z 0..9. No
// front: the camp lands turned a random quarter, so every side is worked (the hull's long side, its stern, its swept
// bow and the mast with its sail each give one side its own face).
//
// THE CASTAWAY'S SPOT STAYS (4, 1, 4): between the fire and the boat's gunwale, warming themselves, out under the sky.
// Feet (4, 1, 4) and head (4, 2, 4) are air; (4, 0, 4) is sand. The player can stand on any of the open cells round
// them (3..5, 1, 3..5 are free but for the fire ring's slab corner at (3, 1, 4), a half block a player steps onto),
// and the way out is open to the north and the west whichever way the camp lands.

export const SIZE = [10, 15, 10] // w, h, d (limit 10 x 19 x 10)

export const CASTAWAY = [4, 1, 4]

// Palette (60/30/10): sand and the boat's weathered spruce (ground and hull) / bleached driftwood and sailcloth
// (props, fuel, windbreak, the torn sail: pale) / dark oak keel and gunwales, the red rag, lanterns and the fire.
// Why: a beach is pale, so the hull is the camp's one dark mass and reads against it; the driftwood and cloth are as
// pale as the sand, as if the sea made them; the dark keel draws the boat's outline, the red rag is the only colour
// that shouts.
export const PALETTE = {
  ground: [['sand', 14], ['gravel', 1], ['sandstone', 1]],
  hearthGround: [['gravel', 3], ['sand', 2], ['cobblestone', 1]],
  hull: {
    planks: [['spruce_planks', 9], ['stripped_spruce_log', 2], ['dark_oak_planks', 1]], // weathered strakes
    stairs: 'spruce_stairs',
    gunwale: 'dark_oak_log',
    keel: 'dark_oak_log',
    stemHead: 'dark_oak_stairs',
    thwart: 'stripped_spruce_log', // the rowers' seats, seen from under the boat
  },
  driftwood: 'birch_log', // pale, but with its bark: it reads as wood against the sand
  mast: 'spruce_log',
  fuel: 'oak_log',
  yard: 'stripped_spruce_log',
  sail: [['white_wool', 5], ['light_gray_wool', 2]],
  bedding: ['white_wool', 'light_gray_wool'],
  pillow: 'hay_block',
  curbStair: 'cobblestone_stairs',
  curbSlab: 'mossy_cobblestone_slab',
  spit: 'spruce_fence',
  rag: 'red_banner',
  lantern: 'lantern',
  shells: { pickle: 'sea_pickle', fans: ['dead_brain_coral_fan', 'dead_tube_coral_fan', 'dead_fire_coral_fan'] },
}

export const MASSES = {
  // The boat: a double-ender, keel along z, its two stems at z 0 and z 9 standing on the sand, gunwales on x 5 and 9
  // at y 3 amidships (two clear blocks under them) dipping to y 2 at the narrowed ends (z 1 and z 8), keel log at y 5. Under it, x 6..8 z 2..7, two blocks of headroom under the boat's bottom boards and thwarts (y 3):
  // the castaway's bed, a crawl-in shelter, not a room.
  boat: { axis: 'z', across: [5, 9], ends: [0, 9], g: 3 },
  props: [[5, 2], [9, 2], [5, 7], [9, 7]], // driftwood posts under the gunwales, y 1..2
  windbreak: { x: 9, z0: 4, drops: [2, 1] }, // sailcloth hung from the east gunwale between the props, ragged at the foot
  // The mast: stepped in the sand at (1, 1, 8), 12 logs, the yard across along x at y 10 from x 0 to 4, a red rag on
  // top at y 13, a lantern hanging from the yard's west end, the torn sail under its east arm (x 2..4).
  mast: { at: [1, 1, 8], height: 12, yardY: 10, yardAxis: 'x', from: 0, to: 4 },
  // the torn sail: strips under the yard's east arm, their lower ends blown a block south (z 9), the last one dripping
  // toward the cauldron
  sail: { axis: 'x', at: 8, a: 2, y: 10, drops: [4, 3, 2] },
  sailBlown: [[2, 5, 9], [2, 4, 9], [3, 6, 9], [3, 5, 9], [4, 7, 9]],
  rainCatch: [3, 1, 8], // a cauldron full of rain water under the sail's torn corner: the castaway's only fresh water
  mastFoot: [[0, 1, 8, 'east'], [1, 1, 9, 'north'], [2, 1, 8, 'west']], // stones wedging the mast upright (stairs)
  // The fire: a campfire sunk in the ground layer at (2, 0, 3), a curb of beach stones round it, an empty spit over it.
  fire: { at: [2, 0, 3], spit: { xa: 1, xb: 3, z: 3, y0: 1 } },
  fuel: [[0, 1, 2], [0, 1, 3], [0, 2, 2]], // dark wood stacked by the fire, along z (dried before it burns)
  driftLog: [[4, 1, 7], [4, 1, 8], [4, 1, 9]], // a log the sea brought, lying along z beside the boat (a seat by the mast)
  // ground: sand in a ragged round of r^2 <= 25 about the middle; the corners outside it stay the beach's own
  ground: { cx: 4.5, cz: 4.5, r2: 25, hearth: [[1, 2], [3, 2], [1, 4], [2, 4], [2, 2], [3, 4], [4, 3], [1, 5]] },
}

export const ROOMS = [
  { name: 'Shelter', use: 'the castaway sleeps here on a folded sail, out of the wind, the sea chest at their head', box: [6, 1, 2, 8, 2, 7] },
  { name: 'Hearth', use: 'the fire, the empty spit and the castaway standing over it', box: [0, 1, 1, 4, 2, 5] },
  { name: 'Signal', use: 'the mast with its rag, the sail that catches rain, the cauldron', box: [0, 1, 7, 3, 13, 9] },
]

export const OPENINGS = {
  // No walls: the shelter is open on three sides between its props (the windbreak closes the fourth), the hearth and
  // the signal are open sand. The castaway's way out runs north (z 0) or west (x 0) from (4, 1, 4).
  shelterSides: ['west between the props at z 3..5', 'north under the stern transom (y 1..2)', 'south under the bow'],
}

// Every piece, footprint first. No code reads any of them (the brief: no furniture required); they make the scene.
// The camp records no furniture spots (furniture.json is empty): a castaway's bed of sailcloth is not a bed.
export const FURNITURE = [
  { room: 'Shelter', kind: 'fixture', block: 'chest', pos: [6, 1, 2], facing: 'south', note: 'the sea chest washed ashore with them' },
  { room: 'Shelter', kind: 'fixture', block: 'barrel', pos: [8, 1, 2], facing: 'up', note: 'a water barrel (empty)' },
  { room: 'Shelter', kind: 'bedding', cellsAbs: [[7, 0, 4], [7, 0, 5], [7, 0, 6], [8, 0, 5], [6, 0, 6]], note: 'a sail spread on the sand as a bed: drawn in the ground layer, so the two-high space under the boat keeps its full headroom' },
  { room: 'Shelter', kind: 'pillow', pos: [7, 1, 7], note: 'a hay bale from the ship\'s stores' },
  { room: 'Shelter', kind: 'lamp', pos: [8, 2, 2], standing: true, note: 'a lantern standing on the water barrel' },
  { room: 'Hearth', kind: 'fixture', block: 'campfire', pos: [2, 0, 3], note: 'lit, with nothing on the spit' },
  { room: 'Signal', kind: 'lamp', pos: [0, 9, 8], note: 'a lantern hanging from the yard\'s west end' },
  { room: 'Signal', kind: 'fixture', block: 'water_cauldron', pos: [3, 1, 8], note: 'rain water' },
]

// The walking line: the player comes up the beach from any side to the castaway at (4, 1, 4) (standing at (4, 1, 2),
// (5, 1, 4) under the gunwale, or (4, 1, 6)), steps under the gunwale into the shelter between the props at z 3..5,
// and round the fire to the mast.
export const WALK = [[4, 1, 0], [4, 1, 2], [4, 1, 3], [5, 1, 4], [6, 1, 4], [5, 1, 5], [4, 1, 6], [3, 1, 6], [2, 1, 7]]

// Small things along the strandline (on sand, never in the walking line).
export const SHORE = {
  pickles: [[8, 1, 0, 2], [0, 1, 6, 1], [9, 1, 8, 3]],
  fans: [[6, 1, 0], [2, 1, 9], [9, 1, 1]],
}
