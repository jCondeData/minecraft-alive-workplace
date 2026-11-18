// The Stone Wall (`stone_wall`, Level II `stone_wall_2`), the plan: drawn before any block. ROADMAP 52.3 (the method of
// Milestone 49); the brief is tools/blueprints/briefs/stone_wall.md. The segment of the stone wall kit, drawn together
// with its tower (../wall_tower, which imports this file's KIT palette so the two are one set). recipe.mjs assembles
// both levels from the parts library (../parts, ramparts.mjs).
//
// Three rough concepts (massing, roof, plan; one line of story each):
//   A. The Stepped Curtain. A flat ashlar curtain, a cobble plinth, a pale string course at the walk's level and square
//      merlons; on the village side every segment carries its own flight of stone steps up its face to the walk, a
//      lantern newel at the flight's foot. Level II hangs a timber hoarding on the outer face: posts over the merlons,
//      murder holes over the crenels, a lopsided dark-oak roof over the walk. Story: the masons build a wall
//      the town can man from anywhere: wherever you are inside, a flight of steps is a few blocks away.
//   B. The Arcaded Rampart. The inside face a row of blind round arches carrying a walk, a ladder in one arch. Story: a
//      wall whose arches the market leans its stalls into. In a 3-deep plot the arches are a block deep and read as
//      dents, a ladder is no way up for a guard or a villager, and the walk can't widen (a second row of stations).
//   C. The Field Dyke. Rough cobble to the top, a sloped coping of stairs, no merlons. Story: a field wall grown tall
//      when the raids came. Honest, but the walk reads as a ledge, the outer face has nothing to say, and Level II's
//      hoarding has no merlons to sit on.
// Strongest: A. It is the only one whose inside says "climb me" (a flight in every segment, so a run of wall reads as
// a rhythm of steps and lanterns, not a blank back), its outer face is the hard, plain cliff the siege rules need
// (ladders and sand ramps), and Level II grows from it naturally: the hoarding's posts stand on the merlons and its
// holes open over the crenels, so the timber gallery is the stone's own rhythm in wood.
//
// Coordinates: x along the wall (0..6), y up (0 = the layer on the clicked block), z across it: z = 0 the inside (the
// village, the wall line), z = 2 the outer face (Level II: z = 3, the hoarding). Level I exactly 7 x 6 x 3, Level II
// exactly 7 x 9 x 4 (StarterBlueprints, about twenty siege tests).
//
// Masses (named parts the owner's feedback can point at):
//   Curtain   the wall itself, rows z 1..2: cobble plinth y 0..1, ashlar y 2, the walk's course y 3 (z 1 the walk's
//             paving, z 2 a pale string course in the outer face), the parapet y 4 on z 2.
//   Walk      the row z = 1, its paving at y 3, stood on at y 4, open to the sky (Level I) over all seven columns: seven
//             battle stations.
//   Merlons   on the parapet at y 5, x 0, 2, 4, 6; the crenels x 1, 3, 5 (x 3 is the ladder column: its top is y 4).
//   Flight    on the inside face (z 0): four stone steps rising east from x 2 (y 0) to x 5 (y 3), filled solid under,
//             its top step level with the walk. No sturdy top at y 2 beside the walk (a bottom stair is no station).
//   Newel     the pier at the flight's foot side (x 0, z 0), y 0..2, a wall post on it (y 3) and a lantern on that (y 4).
//   Level II (the Hoarded Wall) adds, keeping every block of Level I:
//   Hoarding  a spruce gallery hung on the outer face over the foot (row z 3): on each merlon's line (x 0, 2, 4, 6) an
//             upside-down stair bracket (y 3), a board (y 4) and a post (y 5); between them (x 1, 3, 5) the murder
//             holes, open to the ground under the crenel behind, and the screen open over them.
//   Roof      a lopsided gable of dark oak: the long slope from the hoarding's eave (z 3, y 6) up over the parapet (z 2,
//             y 7) to a slab ridge over the walk (z 1, y 8), a short slope back down over the inside (z 0, y 7), on a
//             joist laid on each merlon (y 6): the walk roofed, its headroom open (y 4..7), an eave on both faces.
//   Lights    the newel's lantern (Level I); two lanterns hung under the roof over the walk (Level II).

export const SIZE = { 1: [7, 6, 3], 2: [7, 9, 4] } // w, h, d: fixed by the brief

// The kit's palette (60/30/10), shared with the Wall Tower: stone (plinth, ashlar, merlons) / spruce (the hoardings,
// the tower's loft and roof, doors) / accents: the pale andesite string course and walk, moss, lantern light. Why: grey
// ashlar on a darker cobble foot reads as a cut wall on a rubble footing (weight at the bottom); one pale course at the
// walk's height draws the line the archers stand on along every segment and round every tower, so a run of the kit
// reads as one wall; warm spruce timber on top says "added in wartime", and keeps the roofs from reading as dark lids.
export const KIT = {
  plinth: [['cobblestone', 5], ['mossy_cobblestone', 2], ['andesite', 1]],
  ashlar: [['stone_bricks', 7], ['cracked_stone_bricks', 1], ['mossy_stone_bricks', 1]],
  course: 'polished_andesite', // the walk's paving, the string course, the tower's crenel sills
  courseSlab: 'polished_andesite_slab',
  courseStairs: 'polished_andesite_stairs',
  merlon: 'stone_bricks',
  cap: 'stone_brick_wall', // a merlon's finial (an unsturdy top: no station on a merlon; not a roof slab)
  steps: 'stone_brick_stairs',
  corbel: 'stone_brick_stairs', // the tower's shadow line under its deck
  quoin: 'polished_andesite', quoinAlt: 'stone_bricks',
  newelPost: 'stone_brick_wall',
  wood: {
    bracket: 'spruce_stairs', board: 'spruce_planks', post: 'spruce_log', beam: 'stripped_spruce_log',
    roof: 'dark_oak_stairs', roofCap: 'dark_oak_slab', floor: 'spruce_slab', door: 'spruce_door', hatch: 'spruce_trapdoor',
  },
  groundFloor: 'stone_bricks',
}

export const MASSES = {
  curtain: { x0: 0, x1: 6, inner: 1, outer: 2, plinthTop: 1, walkY: 3, parapetY: 4 },
  merlons: { y: 5, at: x => x % 2 === 0 }, // crenels at x 1, 3, 5
  ladderColumn: 3, // Ladders.height climbs (3, 0..4, 2): five rungs, its top a crenel
  flight: { x0: 2, y0: 0, z: 0, steps: 4, rise: 'east' }, // top step (5, 3, 0), level with the walk
  newel: { x: 0, z: 0, y0: 0, y1: 2 },
  hoarding: { x0: 0, x1: 6, wallZ: 2, outZ: 3, floorY: 4, topZ: 1, bay: x => x % 2 === 0, backEave: true }, // Level II
}

// Rooms: none (nobody lives or works in it). The Walk is the one place to stand.
export const ROOMS = [{ name: 'Walk', use: 'battle stations: seven archers, one per column', box: [0, 4, 1, 6, 5, 1] }]

// Openings: no way through (roads cross at gatehouses); no gate block of any kind.
export const OPENINGS = { through: null, wayUp: 'the Flight, from the village side' }

// Furniture: none (no blueprint_furniture/stone_wall.json; no POI may stand in a piece laid by the dozen).
export const FURNITURE = { 1: [], 2: [] }

// Lights: the newel's lantern (part of the Newel); Level II hangs two under the roof's ridge over the walk.
export const LIGHTS = { 1: [], 2: [[1, 7, 1], [5, 7, 1]] }

// The walking line: the village ground at (2, 0, -1) -> up the Flight (2, 0, 0) .. (5, 3, 0) -> onto the Walk at
// (5, 3, 1) (stood on at y 4) -> along it to x 0 and x 6, on into the next segment or tower; a guard steps up onto a
// crenel (x 1, 3, 5, stood on at y 5) to lean out (Level II: over a murder hole).
export const WALK = [[2, 0, -1], [2, 1, 0], [3, 2, 0], [4, 3, 0], [5, 4, 0], [5, 4, 1], [3, 4, 1], [0, 4, 1], [6, 4, 1]]
