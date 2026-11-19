// The Clinic, the plan: drawn before any wall. Rooms, every furniture footprint (kind, cells, facing) and the walking
// line come first; recipe.mjs then assembles it from the parts library (../parts) in passes.
//
// Three rough concepts (ROADMAP 49, tools/blueprints/REBUILD.md):
//   A. The pavilion ward. One long, tall ward (gable to the street, ridge along z) with beds heads to both long walls
//      and a tall window between every pair, the Nurse's station across the front end where she sees every bed, a
//      catslide roof running on past the west eave over a sun veranda where the mending sit out, and a ventilator
//      lantern on the ridge. Story: a cottage hospital built on the pavilion principle (light and moving air between
//      every bed), its sick taken out into the sun.
//   B. The cloister clinic. A U of low wings round a herb garth: beds in the two arms, the dispensary across the back,
//      a lean-to walk round the garth. Story: an infirmary of the old religious houses. Its beds scatter round three
//      sides, away from the Nurse's eye, and three wings with four roof joins blow the one-day block budget.
//   C. The apothecary tower. A square, steep-capped tower over the brewing stand (a herb-drying loft above, reached by
//      a ladder) beside a low ward lean-to. Story: the village's still-room grown a sick bay. The hero tower is all
//      height and holds nothing the Nurse needs; the ward, the clinic's reason, becomes the afterthought.
// Strongest: A. It is the only one whose shape IS its use: the long ward with its window-bed-window rhythm says
// "beds in rows" from the street, the saltbox front (one long catslide, one short eave) reads in outline from 20
// blocks, the ventilator gives the ridge a hero point, and the whole ward is one room the Nurse can watch from her
// station. It is also lean: one roof over one room, so it builds in a day.
//
// Coordinates: x across the front (west 0 -> east 14), y up (0 = the layer on the clicked block), z from the front
// (z = 0, north) to the back. Front door on the north side with a stair step at y = 0 before it.
//
// Masses (named parts the owner's feedback can point at):
//   Ward       the main mass: walls x 4..12, z 2..10 (white render on a stone plinth a block proud, log corners, a log
//              wall plate at y 5), a window at every even z, a white pier at every odd z for a bed's head.
//   Roof       a 45-degree slate gable along z (z 1..11, a block over each gable), eaves at x 3 and x 13 on y 5, the ridge
//              cell at y 9 closed under a slab cap at y 10 (the ward's "ridge cells": 8 of them, the ventilator over 3).
//   Catslide   the west slope running on two more columns (x 2 at y 4, x 1 at y 3) over the Sun veranda.
//   Veranda    the sun walk along the west wall: open, its roof on four log posts at x 1, seats set into the wall's
//              recesses under the windows, a door from the ward at z 6.
//   Ventilator a louvred lantern on the ridge over the middle of the ward (z 5..7), a lamp inside it.
//   Station    the Nurse's end of the ward (z 3..4): brewing stand, remedy chest, medicine cupboard, her desk.
//   Herb beds  two beds of medicinal flowers in their own soil either side of the front step (z 0).

export const SIZE = [15, 14, 12] // w, h, d (limit 18 x 18 x 16)

// Palette (60/30/10): white render (walls, gables) / spruce frame and slate roof / stone plinth, birch floor, the
// herb beds' colour. Why: a clinic should look clean and airy, so the walls are white; a slate roof (not the Healing
// Center's red) keeps it sober and contrasts hard with the render; warm spruce logs draw the bays and carry the eaves;
// the pale birch floor keeps the ward light inside. Everything a miner or lumberjack brings, bar the concrete.
export const PALETTE = {
  stone: [['stone_bricks', 6], ['cobblestone', 2], ['andesite', 1], ['mossy_stone_bricks', 1]],
  render: [['white_concrete', 9], ['polished_diorite', 1]],
  frame: 'spruce_log',
  roof: { full: 'deepslate_tiles', stairs: 'deepslate_tile_stairs', slab: 'deepslate_tile_slab' },
  trim: 'spruce_stairs', // barge boards under the rakes (45 degrees: stairs, not slabs)
  verandaRoof: 'spruce_stairs', // the catslide's last two courses over the veranda: boards, not slate, so the long west
  // slope reads as the ward's roof running on into a timber veranda roof, not one dark mass
  floor: 'birch_planks',
  wood: 'spruce', // doors, sills, seats, slabs, fences
  glass: 'glass_pane',
  step: 'stone_brick_stairs',
  bed: 'white',
}

export const WARD = {
  walls: { x0: 4, x1: 12, z0: 2, z1: 10 }, // the render's wall line (corners are logs)
  room: { x0: 5, x1: 11, z0: 3, z1: 9 },
  plinth: { x0: 3, x1: 13, z0: 1, z1: 11 }, // a block proud of the walls all round: sills and seats stand on it
  wallTop: 4, plateY: 5,
  corners: [[4, 2], [12, 2], [4, 10], [12, 10]],
  roof: { axis: 'z', across: [3, 13], along: [1, 11], eaveY: 5, extend: { a0: 2 } },
  vent: { x: 8, z: 6 },
  beams: [{ z: 4, lampAt: 8 }, { z: 8, lampAt: 8 }], // tie beams on the wall plate, a lantern hung under each over the aisle
  // A small wall dormer breaking the east eave over the middle east window (the ward's high light, polish of the 50.8
  // critic: the east roof plane was one bare slope): its face in the wall plane (x 12) on the plate, a pane between
  // two log cheeks, a little capped gable (ridge along x) running back into the main slope at x 11.
  dormer: { face: 12, z: 6, y: 6, back: 11 },
}

export const VERANDA = {
  posts: [[1, 1], [1, 4], [1, 8], [1, 11]], postTop: 2, deck: { x0: 1, x1: 2, z0: 1, z1: 11 }, rails: [[[1, 1, 2], [1, 1, 3]], [[1, 1, 9], [1, 1, 10]]],
  // The lean-to's two ends closed (polish of the 50.8 critic: seen low, each end was a hollow ring of stairs): a
  // board end wall from the corner post to the ward's plinth, a pane in it at sitting height, under the barge boards.
  // The way on stays the open middle bay opposite the ward's door.
  ends: [1, 11], endCells: [[2, 1], [3, 1], [3, 2], [3, 3]], endPane: [2, 2],
}

export const ROOMS = [
  { name: 'Station', use: "the Nurse's end: brewing stand, remedy chest, medicine cupboard, desk", box: [5, 1, 3, 11, 4, 4] },
  { name: 'Ward', use: 'six white beds heads to the long walls, nightstands between them, the aisle down the middle', box: [5, 1, 5, 11, 4, 9] },
  { name: 'Veranda', use: 'the sun walk: seats under the windows, a door from the ward', box: [1, 1, 1, 3, 3, 11] },
]

export const OPENINGS = {
  frontDoor: { at: [8, 1, 2], facing: 'south', out: 'north', step: [8, 0, 1] },
  verandaDoor: { at: [4, 1, 6], facing: 'east', out: 'west', transom: [[4, 3, 6]] },
  // windows: glass cells, the side they look out of, their dressing (a sill on the plinth with a pot, or a seat).
  // Every wall window is one wide and two tall (y 2..3), under a white band below the wall plate.
  windows: [
    ...[6, 10].map(x => ({ name: `front ${x}`, cells: [[x, 2, 2], [x, 3, 2]], out: 'north', dress: 'sill' })),
    ...[4, 8].map(z => ({ name: `west ${z}`, cells: [[4, 2, z], [4, 3, z]], out: 'west', dress: 'seat' })),
    ...[4, 6, 8].map(z => ({ name: `east ${z}`, cells: [[12, 2, z], [12, 3, z]], out: 'east', dress: 'sill' })),
    ...[6, 8, 10].map(x => ({ name: `back ${x}`, cells: [[x, 2, 10], [x, 3, 10]], out: 'south', dress: 'sill' })),
    { name: 'front gable', cells: [[8, 7, 2]], out: 'north', dress: null },
    { name: 'back gable', cells: [[8, 7, 10]], out: 'south', dress: null },
  ],
}

// Every piece of furniture, footprint first. kind: a furniture.py kind (recorded as a furniture spot) or a fixture
// (a vanilla block the game uses as itself). part: the furnitureParts part that draws it. facing: the way a person
// using it faces. Beds are drawn by wardBeds (heads checked against the wall); `heads` are the head cells.
export const JOB = { brewingStand: [5, 1, 4], stands: [6, 1, 4] } // the Nurse stands at (6, 1, 4), beside it
export const BEDS = {
  west: { facing: 'west', heads: [[5, 1, 5], [5, 1, 7], [5, 1, 9]], stands: [[5, 1, 8]] }, // (5, 1, 6): the veranda door's way
  east: { facing: 'east', heads: [[11, 1, 5], [11, 1, 7], [11, 1, 9]], stands: [[11, 1, 6], [11, 1, 8]] },
}
export const FURNITURE = [
  // Station, west: the remedy chest in the front corner, the brewing stand beside it under the west window
  { room: 'Station', kind: 'fixture', part: 'chest', pos: [5, 1, 3], facing: 'east', block: 'chest' },
  // Station, east: the medicine cupboard in the front corner, the Nurse's desk and chair under the east window
  { room: 'Station', kind: 'cupboard', part: 'cupboard', pos: [11, 1, 3], facing: 'west', wood: 'spruce' },
  { room: 'Station', kind: 'desk', part: 'desk', pos: [11, 1, 4], facing: 'east', wood: 'spruce' },
  { room: 'Station', kind: 'chair', part: 'chair', pos: [10, 1, 4], facing: 'east', wood: 'spruce' },
  // Ward: a green runner down the aisle (clear of the cell inside the front door), each bed's green cloth on the wall
  // over its head (the clinic's 10%: the herb-garden green)
  { room: 'Ward', kind: 'rug', part: 'rug', cellsAbs: [[8, 1, 5], [8, 1, 6], [8, 1, 7], [8, 1, 8], [8, 1, 9]], colour: 'green' },
  ...[5, 7, 9].flatMap(z => [
    { room: 'Ward', kind: 'picture', part: 'picture', pos: [5, 3, z], facing: 'east', wood: 'spruce', colour: 'green' },
    { room: 'Ward', kind: 'picture', part: 'picture', pos: [11, 3, z], facing: 'west', wood: 'spruce', colour: 'green' },
  ]),
]

// The walking line: the Nurse's wait (6, 0, -3) -> front step -> door -> the station, beside the brewing stand, and on
// down the aisle to the beds; the veranda door out to the sun walk.
export const WALK = [
  [8, 0, 0], [8, 0, 1], [8, 1, 2], [8, 1, 3], [7, 1, 3], [7, 1, 4], [6, 1, 4],
  [7, 1, 5], [8, 1, 6], [8, 1, 7], [8, 1, 8], [8, 1, 9],
  [7, 1, 6], [6, 1, 6], [5, 1, 6], [4, 1, 6], [3, 1, 6], [2, 1, 6],
]

// Outside the rooms (not furniture spots): herb beds in their own soil either side of the step.
export const OUTSIDE = {
  herbBeds: [{ x0: 4, x1: 6, z: 0, plants: ['allium', 'oxeye_daisy', 'lily_of_the_valley'] }, { x0: 10, x1: 12, z: 0, plants: ['lily_of_the_valley', 'oxeye_daisy', 'allium'] }],
  pots: { north: ['potted_allium'], east: ['potted_fern'], south: ['potted_azure_bluet'] },
}
