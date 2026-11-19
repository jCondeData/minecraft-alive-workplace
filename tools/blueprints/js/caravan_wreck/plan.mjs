// The Caravan Wreck (story/caravan_wreck), the plan: drawn before any block. recipe.mjs assembles it from the shared
// parts library (../parts; wrecks.mjs grown for it, trail_marks.mjs reused for the ground and the ruts).
// Brief: tools/blueprints/briefs/caravan_wreck.md. The Lost Caravan's first scene and its clue: nobody lives, works
// or trades here, so no rooms with furniture spots; the "rooms" are the scenes a player walks past.
//
// Three rough concepts first:
//   A. "On the Verge": the covered wagon lies on its side along the road, its white bonnet turned to the sky like a
//      beached hull, its load fanned out down the verge. Massing: one long low mass (8 x 5 x 4) and a fan of props.
//      Roof: the bonnet itself, a half-round of canvas. Plan: wagon in the middle, goods round it. Story: it went off
//      the road. Safe and true, but it reads the same from every side and it answers nothing: where did they go?
//   B. "Reared": the wagon pitched nose-first into the ditch, tail and bonnet up like a sail, wheels in the air. A tall,
//      striking mass (7 high) on a 9 x 11 plot. Story: a crash. But the ground layer can't be dug into a ditch (y 0 is
//      the world's ground), so it would float, and the Trail's mark 4 already rears the cart on its tail: the climax
//      would be spent in the first scene.
//   C. "Rolled and Robbed" (built): the wagon rolled off the road onto its side, its underside turned to the road and
//      bristling with the raiders' arrows, one top wheel still on its axle, the other flung back onto the road; the
//      white bonnet lies along the lee side like a gutted sail, slashed where the raiders cut their way in; the load
//      spilled out of the open tail and picked over; and the raiders' own tracks (the caravan's second vehicle, the
//      carter's cart, loaded with the best of the goods, the horses walking between its ruts) turn off the road and
//      leave the other way, toward the stockade. Massing: the wagon body (a plank wall 4 high), the bonnet (a white
//      half-tunnel 4 high, lower and lumpy between its hoops) and the wheel standing proud on top; around them, a
//      ground drawn as a map. Plan: four sides, four chapters: the road (the swerve, the arrows, the wheel, the
//      lantern), the lee (the canvas), the tail (the spill and the walk-in hollow under the bonnet), the far side
//      (tracks leading away).
//   C is the strongest: it is the only one that holds the clue the story needs ("tracks lead away from the road") in
//   the drawing itself, it reads differently from each of the four sides (the arc turns it at random), and it gives
//   the player something to step into (the hollow under the bonnet) instead of a heap to look at. It also hands the
//   Trail what it needs: the carter's cart leaves here with its gauge-2 ruts, the same ruts the trail marks follow.
//
// Story: a settlers' caravan of two, the covered wagon (the family) and the carter's cart behind. Raiders came up the
// road; the wagon swerved, ran onto the verge and rolled onto its side, underside to the road. The raiders shot into
// it, cut the team loose, slashed the bonnet and threw the load out of the tail, put what they wanted on the carter's
// cart and drove it off the road the other way with the carter on it. Left: the wagon, its top wheel, the wheel that
// came off, the lantern that fell from the tail, the goods they didn't want, and two ruts and hoofprints leaving.
//
// Coordinates: x across (the road on the east, x 10..12), y up (y 0 is the ground layer itself: every cell of it is
// drawn, because the saving adapter fills an undrawn cell with air and air at y 0 is a hole), z along the road (z 0
// north, the wagon's front, to z 12 south, its tail). No front: the arc turns it a random quarter. Middle (6, 6) is
// on the wagon's floor, so "found" is measured from the wreck itself.
//
// Masses (named parts the owner's feedback can point at):
//   Body     the wagon on its side: the floor stands as a plank wall at x 6 (y 0..3, z 2..9, its bottom row sunk in the
//            ground layer), the bottom side board lies in the ground (y 0, x 4..5), the top side board is a ledge at
//            y 3 (x 4..5) with a dark rail on its edge, the front board shuts z 2; the tailgate lies in the ruts.
//   Gear     the underside, on the road side (x 7): two axles standing upright (z 3 and z 8), the reach between them,
//            arrows in the floor between; the front axle's top hub (7, 4, 3) on top of the wreck, its felloes smashed
//            off, four bare spokes against the sky; the rear axle's top end a splintered stub (its big spoked wheel
//            flung back onto the road); the tongue lying north, its doubletree cut.
//   Bonnet   the cover on its side: a half-tunnel of canvas from the top rail out to x 0 and down to the ground at
//            y 1, rounded in one step at its shoulder, on three hoops (z 2, 5, 9) that hold it out square as ribs
//            while the canvas sags between them; the hem dirtied where it lies on the ground; the pucker drawn shut
//            inside the flared front hoop, the tail open; a slit along the top by the rail, the middle hoop's bare rib
//            across it, and a rent in the side at z 6..7 with its flap fallen on the hem.
//   Hollow   inside the bonnet: a walk-in space from the open tail (2 high), the goods the raiders left.
//   Road     the old ruts along the east edge, the swerve's gouges into the verge, the flung wheel lying flat.
//   Spill    the tail's goods thrown out south and the front's crate thrown west.
//   Tracks   the carter's cart's ruts turning off the road at the south-east and leaving west along the south edge,
//            hoofprints between them, a dropped bale and barrel along them.
export const SIZE = [13, 5, 13] // w, h, d: inside the road check's 13 x 13 x 7 (brief, "Fits")

// Palette (60/30/10): spruce wagon wood and trodden earth (body, boards, ruts, the churned verge) / the white canvas
// (bonnet, scraps) / dark oak running gear and the goods' colours (hay gold, barrel, a red blanket, the lantern).
// Why: the Trail's caravan is spruce and white canvas (js/trail), so the wreck and the marks read as one wagon train;
// the dark gear against the pale planks draws the underside so the "on its side" reads from the road; the white is
// the thing a player sees from far off in any biome.
export const PALETTE = {
  ground: {
    verge: [['grass_block', 7], ['coarse_dirt', 1]], // the untouched edge, blending into the world's grass
    trodden: [['coarse_dirt', 5], ['rooted_dirt', 2], ['dirt', 2], ['podzol', 1]], // where feet and hooves went
    churned: [['rooted_dirt', 3], ['coarse_dirt', 3], ['dirt', 2], ['gravel', 1]], // where the wagon slid and rolled
    road: [['coarse_dirt', 4], ['dirt', 1], ['grass_block', 1]], // between and beside the old ruts
    rut: 'dirt_path',
    under: 'coarse_dirt', // a rut cell with something on it (a path block may not be under a solid block)
    hoof: 'podzol', // hoofprints between the cart's ruts
    shade: [['grass_block', 3], ['moss_block', 1], ['dirt', 1]], // under the bonnet's lee
  },
  wagon: {
    deck: 'spruce_planks', board: 'spruce_planks', rail: 'dark_oak_log', slab: 'spruce_slab',
    trapdoor: 'spruce_trapdoor', fence: 'spruce_fence',
  },
  gear: { axle: 'dark_oak_log', hub: 'stripped_dark_oak_log', rim: 'dark_oak_slab', spoke: 'dark_oak_trapdoor', bareSpoke: 'dark_oak_fence', stub: 'dark_oak_fence', tongue: 'dark_oak_log' },
  bonnet: { canvas: 'white_wool', stain: 'light_gray_wool', hoop: 'stripped_birch_log', scrap: 'white_carpet' }, // stain: the hem dirtied where it lies on the ground
  goods: {
    crate: 'composter', barrel: 'barrel', bale: 'hay_block', blanket: 'red_carpet', pot: 'decorated_pot',
    stave: 'spruce_trapdoor', chest: 'chest', loom: 'loom', cauldron: 'cauldron',
  },
  lantern: 'lantern',
  arrow: 'tripwire_hook',
  plants: { grass: 'short_grass', fern: 'fern' },
}

// The masses in numbers (recipe.mjs draws each from one part).
export const BODY = { floorX: 6, under: 'east', y0: 0, y1: 3, z0: 2, z1: 9, boards: [4, 5], frontZ: 2, tailOpen: true }
export const GEAR = {
  axles: [3, 8], // z of the two axles, upright on the underside
  reachY: 2,
  topHub: [7, 4, 3], // the front axle's top hub: its spokes on top of the wreck
  stub: [7, 4, 8], // the rear axle's top end, the wheel gone
  tongue: { cells: [[7, 1, 1], [7, 1, 0]], bar: [[6, 1, 0], [8, 1, 0]] }, // the tongue lying toward the road, the cut doubletree
  arrows: [[7, 1, 5, 'east'], [7, 3, 6, 'east'], [7, 1, 7, 'east'], [7, 3, 4, 'east']], // stuck in the floor, from the road
}
export const BONNET = {
  outer: 0, top: 4, foot: 1, attach: 4, // the canvas bellies out to x 0, up to y 4, lies on the ground at y 1, meets the rail at x 4
  z0: 2, z1: 9, hoops: [2, 5, 9], // the front and tail hoops flared, the middle one under the slit
  front: 'shut', // the pucker drawn shut at z 3, inside the flared front hoop (z 2); the tail (z 9) open
  // cut by the raiders, [x, y, z]: a slit along the top by the rail, either side of the middle hoop (which still spans
  // it, a bare rib), and a rent in the side; they let the daylight into the hollow
  slash: [[3, 4, 3], [3, 4, 4], [3, 4, 6], [3, 4, 7], [0, 2, 6], [1, 3, 6], [1, 3, 7]],
  bare: [[3, 4, 5]],
  // the hem's dirtied cells (a cluster where it was dragged through the mud, by the rent and the tail; never the pin)
  stain: [[0, 1, 6], [0, 1, 7], [1, 1, 7], [0, 1, 8], [0, 1, 9], [1, 1, 9], [0, 1, 2], [0, 1, 3]], // the middle hoop's rib, left bare across the slit
}
export const WHEELS = {
  // the front axle's top wheel, still on its axle on top of the wreck, but its felloes smashed off in the roll: the
  // hub and four bare spokes against the sky, one felloe left (a front wheel is the small one: it turns under the bed)
  top: { hub: [7, 4, 3], felloes: [[1, 1]] },
  // the rear axle's top wheel, the big one, 5 across and spoked, torn off and flung back onto the road: a felloe
  // smashed out, a spoke snapped, the pieces lying by it
  flung: { hub: [10, 1, 7], radius: 2, broken: [[12, 1, 6], [12, 1, 7], [11, 1, 7]], pieces: [[12, 1, 10], [11, 1, 4]] },
}

// The ground (y 0, every cell), as zones: the recipe picks each cell's block from the zone it lies in.
export const GROUND = {
  roadRuts: { axis: 'z', a: 10, gauge: 2, along: [0, 12] }, // the old road: worn, not fresh (half the cells grassed over)
  // the cart's ruts: off the road at the south-east, then west along the south edge and out (cells listed, a curve)
  cartRuts: [
    [[10, 8], [9, 9], [9, 10], [8, 10], [7, 10], [6, 10], [5, 10], [4, 10], [3, 10], [2, 10], [1, 10], [0, 10]],
    [[12, 9], [12, 10], [11, 11], [11, 12], [10, 12], [9, 12], [8, 12], [7, 12], [6, 12], [5, 12], [4, 12], [3, 12], [2, 12], [1, 12], [0, 12]],
  ],
  hoofs: [[10, 10], [8, 11], [6, 11], [5, 11], [3, 11], [1, 11], [0, 11]], // between the ruts, where the horses walked
  swerve: [[9, 0], [9, 1], [8, 1], [8, 2], [9, 3], [8, 4], [8, 9]], // gouges off the road into the verge (dirt path)
  churned: { x0: 7, x1: 9, z0: 1, z1: 9 }, // where the wagon slid on its side
  shade: { x0: 0, x1: 3, z0: 2, z1: 9 }, // the lee under and beside the bonnet
}

// What lies around: [x, y, z, id key or id, props]. Containers are empty props (brief: none filled).
export const SPILL = {
  front: [ // the crate thrown off the driver's box, west of the front
    [2, 1, 1, 'crate', {}], // pin: "a spilled crate"
    [1, 1, 0, 'stave', { facing: 'west', open: 'false' }], [3, 1, 0, 'stave', { facing: 'north', open: 'true' }],
  ],
  tail: [ // thrown out of the open tail and picked over
    [4, 1, 10, 'stave', { facing: 'south', open: 'false' }], [5, 1, 10, 'stave', { facing: 'south', open: 'false' }], // the tailgate, flat in the rut
    [3, 1, 10, 'barrel', { facing: 'west' }], // a barrel on its side, rolled out of the tail
    [2, 1, 11, 'barrel', { facing: 'up' }], // dropped off the cart, standing
    [6, 1, 11, 'bale', { axis: 'x' }], // a bale off the cart, in its tracks
    [4, 1, 11, 'pot', { facing: 'south' }], // the family's pot, rolled clear
    [3, 1, 11, 'scrap', {}], [1, 1, 10, 'scrap', {}], [0, 1, 11, 'scrap', {}], // the torn hem, dragged
    [8, 1, 11, 'blanket', {}], // the family's red blanket, trampled in the tracks
  ],
  inside: [ // left in the hollow along the low side (x 5), what the raiders didn't want; the aisle (x 4) stays free
    [5, 1, 3, 'bale', { axis: 'z' }], [5, 2, 3, 'barrel', { facing: 'west' }], [5, 1, 4, 'barrel', { facing: 'up' }],
    [5, 1, 5, 'cauldron', {}], [5, 1, 6, 'loom', { facing: 'west' }], [5, 1, 7, 'bale', { axis: 'x' }],
    [5, 1, 8, 'barrel', { facing: 'south' }], [1, 2, 4, 'blanket', {}], [2, 2, 4, 'blanket', {}], [1, 2, 5, 'blanket', {}],
    [0, 2, 6, 'scrap', {}], // the slashed flap, fallen onto the hem
  ],
}

// Pins (brief, "Must have": the test reads each block at exactly this cell, unturned).
export const PINS = [
  [[6, 2, 5], 'spruce_planks'], // the wagon's floor
  [[2, 1, 5], 'white_wool'], // the canvas
  [[7, 4, 3], 'stripped_dark_oak_log'], // the upper wheel's hub
  [[10, 1, 7], 'stripped_dark_oak_log'], // the broken wheel
  [[9, 1, 10], 'lantern'], // the lantern in the grass: standing on the fallen tail-lamp board (a trapdoor in the ground layer)
  [[2, 1, 1], 'composter'], // a spilled crate
]
export const LANTERN = { at: [9, 1, 10], board: [9, 0, 10, 'south'] }

// No furniture spots (nobody uses the wreck; containers are props). The walking line is the player's: up the road,
// round the underside past the arrows, past the flung wheel and the lantern, into the hollow by the open tail, out,
// and away along the cart's ruts.
export const FURNITURE = []
export const WALK = [
  [11, 1, 0], [11, 1, 4], [9, 1, 6], [9, 1, 9], [7, 1, 11], [4, 1, 9], [4, 1, 5], [4, 1, 9], [3, 1, 11], [0, 1, 11],
]
