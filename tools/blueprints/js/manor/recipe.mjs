// Noble's Manor (Milestone 49), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes
// (block-out and structure, openings, depth and detail, interior, ground) and returns the structure for the
// minecraft-builder skill's runner. LEVEL=1..4 picks the level (default 1): each level draws the one below first and
// then its own passes, so every level grows out of the last cell for cell.
//   Level I    the hall range, the west (service) cross-wing and the porch tower
//   Level II   + the walled parterre behind the hall
//   Level III  + the east cross-wing: the ballroom with its musicians' gallery, and the garden's east half
//   Level IV   + the vaulted cellar under the kitchen (a LiftedCanvas: the file holds it at the bottom)
// Writes furniture.json beside it for rebuild.py. CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, BRACKET_LAMPS, OUTSIDE } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, stair, slab, plinth, floor, wallPanel, door, hood, gableRoof, crowGable, pyramidRoof,
  chimney, hearthFire, flight, stairwell, railingRun, lampPost, cellar, hatchLadder, plantFlower, clip, leafColumn,
  benchRun, masonButtress, quoins, plinthCourse, moulding, faceCells, mullionWindow, kneeler, bayWindow,
  furnitureParts,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const stone = mix(P.stone, 49)
const paving = mix([['stone_bricks', 5], ['mossy_stone_bricks', 1], ['cracked_stone_bricks', 1]], 50)
const chequer = (x, y, z) => P.hallFloor[(x + z) % 2]
const parquet = (x, y, z) => P.ballFloor[(x + z) % 2]
const WW = MASSES.westWing, H = MASSES.hall, T = MASSES.tower, B = MASSES.ballroom

// the hall roof's height over row z (its ridge along x at z 9), the wing's and the ballroom's over column x
const hallH = z => H.roof.eaveY + 5 - Math.abs(z - 9)
const ballH = x => B.roof.eaveY + 5 - Math.abs(x - 23)

const windows = (c, level) => {
  for (const w of OPENINGS.windows[level] ?? []) {
    const attic = w.y[0] >= 10
    mullionWindow(c, w.axis, w.line, w.lights, w.y[0], w.y[1], w.out, {
      glass: P.glass, mullion: P.dress, sill: attic ? null : P.dressStairs, transom: w.transom ?? null,
    })
  }
}
const furnish = (c, level) => {
  for (const f of FURNITURE[level]) {
    if (f.part === 'jukebox') { c.free(f.pos); c.set(...f.pos, 'jukebox', { has_record: 'false' }); continue }
    furnitureParts[f.part](c, f)
  }
}
const ring = (x0, z0, x1, z1) => {
  const cells = []
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) if (x === x0 || x === x1 || z === z0 || z === z1) cells.push([x, z])
  return cells
}
const plantBed = (c, cells, plants) => cells.forEach(([x, z], i) => {
  c.set(x, 0, z, P.garden.soil, { snowy: 'false' })
  plantFlower(c, x, 1, z, plants[i % plants.length])
})
// a knot: a box-edged bed of soil, flowers in its middle row (or rows)
const knot = (c, { x0, x1, z0, z1, flowers }) => {
  const inner = []
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    c.set(x, 0, z, P.garden.soil, { snowy: 'false' })
    const edge = x === x0 || x === x1 || z === z0 || z === z1
    if (edge) clip(c, [[x, 1, z]], (xx, yy, zz) => ((xx + zz) % 3 ? P.garden.box : P.garden.boxFlower))
    else inner.push([x, z])
  }
  inner.forEach(([x, z], i) => plantFlower(c, x, 1, z, flowers[i % flowers.length]))
}
// a garden wall: brick on a stone foot, stone-coped, with stone piers (a lantern on the corner piers)
const gardenWall = (c, cells, piers, lamps) => {
  for (const [x, z] of cells) {
    c.set(x, 0, z, stone)
    for (let y = 1; y <= 2; y++) c.set(x, y, z, P.brick)
    slab(c, x, 3, z, P.dressSlab)
  }
  for (const [x, z] of piers) {
    for (let y = 1; y <= 3; y++) c.set(x, y, z, P.dress)
    if (lamps.some(([lx, lz]) => lx === x && lz === z)) lampPost(c, x, 4, z, `${P.dress.replace(/s$/, '')}_wall`) // a lantern on a stone post
    else slab(c, x, 4, z, P.dressSlab)
  }
}

// --- Level I: hall, west wing, porch tower ---------------------------------------------------------------------------
function levelOne(c) {
  const ww = WW.walls, hw = H.walls, hr = H.room, tw = T.walls, tr = T.room

  // Pass 1, block-out and structure. Plinths and floors: the wing (parlour boards, kitchen flags), the hall's
  // chequer, the porch's
  plinth(c, ww.x0, ww.z0, ww.x1, ww.z1, stone)
  floor(c, 2, 2, 6, 7, 0, P.floor)
  floor(c, 2, 9, 6, 14, 0, 'polished_andesite')
  plinth(c, hw.x0, hw.z0, hw.x1, hw.z1, stone)
  floor(c, hr.x0, hr.z0, hr.x1, hr.z1, 0, chequer)
  plinth(c, tw.x0, tw.z0, tw.x1, tw.z1 - 1, stone)
  floor(c, tr.x0, tr.z0, tr.x1, tw.z1, 0, chequer)

  // West wing: brick side walls to the plate (y 9), crow-stepped parapet gables front and back, long-and-short quoins
  wallPanel(c, [ww.x0, ww.z0 + 1], [ww.x0, ww.z1 - 1], 1, 9, P.brick)
  wallPanel(c, [ww.x1, ww.z0 + 1], [ww.x1, ww.z1 - 1], 1, 9, P.brick)
  for (const z of [ww.z0, ww.z1]) crowGable(c, 'z', z, [ww.x0, ww.x1], 1, WW.crow, P.brick, P.dressSlab)
  quoins(c, [[1, 1]], 1, 9, P.dress)
  quoins(c, [[7, 1]], 1, 9, P.dress)
  quoins(c, [[1, 15]], 1, 9, P.dress)
  quoins(c, [[7, 15]], 1, 9, P.dress)
  wallPanel(c, [2, WW.partitionGround], [6, WW.partitionGround], 1, 4, P.plaster) // parlour | kitchen
  floor(c, 2, 2, 6, 14, 5, P.floor) // the chambers' floor
  wallPanel(c, [2, WW.partitionUpper], [6, WW.partitionUpper], 6, 9, P.plaster) // front chamber | great chamber
  floor(c, 2, 2, 6, 14, 10, P.lining) // the chambers' boarded ceiling (the attic over it)

  // Hall range: front and back walls to the eave (y 6), the crow-stepped east gable, quoins
  wallPanel(c, [hr.x0, hw.z0], [hr.x1, hw.z0], 1, 6, P.brick)
  wallPanel(c, [hr.x0, hw.z1], [hr.x1, hw.z1], 1, 6, P.brick)
  crowGable(c, 'x', hw.x1, [hw.z0, hw.z1], 1, H.crow, P.brick, P.dressSlab)
  quoins(c, [[19, 5]], 1, 6, P.dress)
  quoins(c, [[19, 13]], 1, 6, P.dress)
  floor(c, hr.x0, hr.z0, hr.x1, hr.z1, 5, P.floor) // the Long Gallery's floor

  // Roofs: the wing first (the taller), then the hall's running into the wing's east slope (its eave column over the
  // hall cleared), the hall's ridge carried one further onto the wing's slope
  gableRoof(c, WW.roof, P.roof)
  for (let z = hw.z0; z <= hw.z1; z++) c.clear(8, 9, z)
  gableRoof(c, H.roof, P.roof, (x, y, z) => !c.get(x, y, z))
  c.set(7, 11, 9, P.roof.full)

  // Porch tower: brick to y 10 round the porch, its study floor and ceiling, quoins, the steep tiled pyramid
  for (const [x, z] of ring(tw.x0, tw.z0, tw.x1, tw.z1)) for (let y = 1; y <= 10; y++) {
    if (z === tw.z1 && y <= H.roof.eaveY && x > tw.x0 && x < tw.x1) continue // the hall's front wall there
    c.set(x, y, z, P.brick)
  }
  c.clearBox(tr.x0, 6, tr.z0, tr.x1, 9, tr.z1) // the hall roof's cells inside the tower
  floor(c, tr.x0, tr.z0, tr.x1, tr.z1, 5, P.floor)
  floor(c, tr.x0, tr.z0, tr.x1, tr.z1, 10, P.lining)
  quoins(c, [[11, 1]], 1, 10, P.dress)
  quoins(c, [[15, 1]], 1, 10, P.dress)
  const apex = pyramidRoof(c, T.cx, T.cz, T.r, 11, P.roof)
  c.clear(T.cx, apex, T.cz) // the apex: a slab over the last course, not a little stack
  slab(c, T.cx, apex - 1, T.cz, P.roof.slab)

  // Pass 2, openings: the front door under its hood, the arch into the hall with a beam over it, the doors off the
  // hall and the gallery, the windows, the parlour's bay
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: P.dressStairs })
  const a = OPENINGS.arch
  c.clearBox(a.x0, a.y0, a.z, a.x1, a.y1, a.z)
  for (let x = a.x0; x <= a.x1; x++) c.set(x, a.y1 + 1, a.z, P.beam, { axis: 'x' })
  for (const d of [OPENINGS.parlourDoor, OPENINGS.kitchenDoor, OPENINGS.frontChamberDoor, OPENINGS.greatChamberDoor, OPENINGS.studyDoor, OPENINGS.gardenDoor])
    door(c, d.at, d.facing, d.facing, { wood: P.wood })
  windows(c, 1)
  c.set(13, 9, 1, P.datestone) // over the study window: the house's datestone
  bayWindow(c, 'x', 1, [2, 6], 'north', { y0: 0, g0: 2, g1: 3, top: 4 }, { pier: P.dress, apron: stone, glass: P.glass, lintel: P.dress, cap: P.dressSlab })

  // Pass 3, depth and detail: a projecting plinth course and a moulded string course at the floor line round every
  // face, the tower's cornice, kneelers at the parapets' feet, the two stacks with their hearths, the family's banners
  const bay = (x, z) => z === 0 && x >= 2 && x <= 6
  const faces = [
    ['z', 1, 0, 16, 'west', 'east'], ['x', 1, 0, 8, 'north', 'south'], ['z', 7, 0, 4, 'east', 'west'], ['x', 15, 0, 8, 'south', 'north'],
    ['z', 7, 14, 15, 'east', 'west'], ['x', 5, 8, 10, 'north', 'south'], ['x', 5, 16, 18, 'north', 'south'],
    ['z', 19, 4, 14, 'east', 'west'], ['x', 13, 8, 18, 'south', 'north'], ['x', 1, 10, 16, 'north', 'south'], ['z', 11, 0, 4, 'west', 'east'], ['z', 15, 0, 4, 'east', 'west'],
  ]
  const stackCells = new Set(['0,10', '0,11', '0,12', '9,14', '10,14', '11,14'])
  for (const [axis, line, u0, u1, out, toward] of faces) {
    const cells = faceCells(axis, line, u0, u1, out)
    // no moulding in the narrow slots between the porch tower and the wings (x 8..10, 16..18 in front of the hall)
    const slot = (x, z) => z <= 4 && ((x >= 8 && x <= 10) || (x >= 16 && x <= 18))
    plinthCourse(c, cells, 0, stone, (x, z) => bay(x, z) || (x === 13 && z === 0) || stackCells.has(`${x},${z}`))
    moulding(c, cells, 5, P.dressStairs, toward, (x, z) => bay(x, z) || stackCells.has(`${x},${z}`) || (slot(x, z) && z < 4))
  }
  // the tower's cornice under the pyramid's eave
  moulding(c, faceCells('x', 1, 11, 15, 'north'), 10, P.dressStairs, 'south')
  hood(c, 13, 4, 0, P.roof)
  for (const z of [1, 15]) {
    kneeler(c, [0, 9, z], P.dress, P.dressStairs, 'east')
    kneeler(c, [8, 9, z], P.dress, P.dressStairs, 'west')
  }
  kneeler(c, [19, 6, 4], P.dress, P.dressStairs, 'south')
  kneeler(c, [19, 6, 14], P.dress, P.dressStairs, 'north')

  // the kitchen stack up the wing's west side, its cooking hearth in the wall; the hall stack on the back wall, its
  // fire in a stone surround under a dark oak mantel (the fires stand on slabs: only the two stacks smoke)
  const ks = MASSES.stacks.kitchen, hs = MASSES.stacks.hall
  chimney(c, ks.x, ks.z, 0, ks.top, P.brick, { breast: { axis: 'z', top: ks.breastTop, stair: P.dressStairs } })
  c.set(ks.x, ks.top, ks.z, P.dress) // the stack's stone cap under the smoke
  slab(c, 1, 0, 11, P.hearthSlab, 'top')
  c.clear(1, 1, 11)
  hearthFire(c, [1, 1, 11], 'east')
  c.set(1, 2, 11, P.dress)
  chimney(c, hs.x, hs.z, 0, hs.top, P.brick, { breast: { axis: 'x', top: hs.breastTop, stair: P.dressStairs } })
  c.set(hs.x, hs.top, hs.z, P.dress)
  for (const [x, y] of [[9, 1], [9, 2], [9, 3], [11, 1], [11, 2], [11, 3], [10, 2], [10, 3]]) c.set(x, y, 13, P.dress)
  slab(c, 10, 0, 13, P.hearthSlab, 'top')
  hearthFire(c, [10, 1, 13], 'north')
  for (const x of [9, 10, 11]) slab(c, x, 4, 12, `${P.wood}_slab`, 'top')
  for (const [x, y, z, colour] of OUTSIDE.banners) c.set(x, y, z, `${colour}_wall_banner`, { facing: 'north' })

  // Pass 4, interior: the grand stair right inside the arch, rising along the hall's front wall to the Long Gallery,
  // its well and railing; then the plan's furniture
  const gs = OPENINGS.grandStair
  stairwell(c, gs.well)
  flight(c, gs.flight, gs.rises, P.wood, `${P.wood}_planks`)
  railingRun(c, OPENINGS.railing, P.wood)
  furnish(c, 1)

  // Pass 5, ground: beds of flowers in their own soil before the hall's front, the paved terrace behind it with the
  // rain butt by the garden door and a lamp post at its corner
  for (const b of OUTSIDE.frontBeds) {
    const cells = []
    for (let x = b.x0; x <= b.x1; x++) cells.push([x, b.z])
    plantBed(c, cells, b.plants)
  }
  const t = OUTSIDE.terrace
  for (let x = t.x0; x <= t.x1; x++) for (let z = t.z0; z <= t.z1; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, paving)
  c.set(...OUTSIDE.rainButt, 'barrel', { facing: 'up', open: 'false' })
  lampPost(c, 18, 1, 15, `${P.wood}_fence`)
}

// --- Level II: the walled garden ---------------------------------------------------------------------------------------
function levelTwo(c) {
  const g = MASSES.garden
  // Pass 1: the walls (x 7 and x 19 from the house back, z 24 across), stone piers, lanterns on the far corners
  const cells = []
  for (let z = 16; z <= g.z1; z++) cells.push([g.x0, z])
  for (let z = g.z0; z <= g.z1; z++) cells.push([g.x1, z])
  for (let x = g.x0 + 1; x < g.x1; x++) cells.push([x, g.z1])
  const piers = [[7, 16], [7, 20], [7, 24], [11, 24], [15, 24], [19, 24], [19, 20], [19, 16]]
  gardenWall(c, cells, piers, [[7, 24], [19, 24], [7, 16], [19, 16]])
  // Pass 5, ground: the walks (the axis from the terrace to the bench, the cross walk, the back walk), four knots,
  // clipped yews at the cross, a lamp on a pedestal at its middle, a stone-ended bench at the end of the axis
  for (let z = 16; z <= 23; z++) c.set(13, 0, z, paving)
  for (let x = 8; x <= 18; x++) { c.set(x, 0, 19, paving); c.set(x, 0, 23, paving) }
  for (const k of OUTSIDE.knots) knot(c, k)
  for (const [x, z] of OUTSIDE.yews) leafColumn(c, x, z, 1, 2, P.garden.yew)
  lampPost(c, OUTSIDE.crossLamp[0], 1, OUTSIDE.crossLamp[1], `${P.dress.replace(/s$/, '')}_wall`)
  const bn = OUTSIDE.bench
  benchRun(c, { x0: bn.x0, x1: bn.x1, y: 1, z: bn.z, facing: 'north' }, { seat: `${P.wood}_stairs`, ends: P.dress, endCap: P.dressSlab })
}

// --- Level III: the ballroom wing ----------------------------------------------------------------------------------------
function levelThree(c) {
  const bw = B.walls, br = B.room
  // Pass 1: take down what the wing replaces (the hall gable's plinth and string course, its parapet over the new
  // roof line, the old garden's east wall where the wing stands); then the wing's plinth and parquet, its brick walls,
  // gables, quoins and roof
  for (let z = 4; z <= 16; z++) for (const y of [0, 1, 5]) c.clear(20, y, z)
  for (let z = 4; z <= 14; z++) for (let y = 10; y <= 13; y++) c.clear(19, y, z)
  for (const z of [4, 14]) c.clear(19, 5, z)
  for (const z of [7, 11]) for (let y = 2; y <= 4; y++) c.set(19, y, z, P.brick) // the gable's ground windows now inside
  for (const z of [8, 10]) c.clear(20, 6, z) // and the gallery window's sills
  plinth(c, bw.x0, bw.z0, bw.x1, bw.z1, stone)
  floor(c, br.x0, br.z0, br.x1, br.z1, 0, parquet)
  wallPanel(c, [bw.x1, bw.z0 + 1], [bw.x1, bw.z1 - 1], 1, 9, P.brick)
  for (const [z0, z1] of [[2, 4], [14, 14]]) wallPanel(c, [bw.x0, z0], [bw.x0, z1], 1, 9, P.brick)
  for (const z of [bw.z0, bw.z1]) crowGable(c, 'z', z, [bw.x0, bw.x1], 1, B.crow, P.brick, P.dressSlab)
  quoins(c, [[19, 1]], 1, 9, P.dress)
  quoins(c, [[27, 1]], 1, 9, P.dress)
  quoins(c, [[27, 15]], 1, 9, P.dress)
  quoins(c, [[19, 15]], 1, 9, P.dress)
  gableRoof(c, B.roof, P.roof, (x, y, z) => !c.get(x, y, z) && !(x === 18 && z >= 5 && z <= 13 && y < hallH(z)))
  c.set(19, 11, 9, P.roof.full) // the hall's ridge runs on onto the ballroom's slope
  // the ballroom's ceiling: boards under the roof's slope (a coved ceiling), the musicians' gallery floor at the back
  for (let x = br.x0; x <= br.x1; x++) for (let z = br.z0; z <= br.z1; z++) c.set(x, ballH(x) - 1, z, P.lining)
  const gl = B.gallery
  floor(c, gl.x0, gl.z0, gl.x1, gl.z1, gl.y, P.floor)

  // Pass 2, openings: the double doors from the hall, the door from the Long Gallery onto the musicians' gallery, the
  // glass door to the garden under a hood, the great cross-window and the tall windows
  for (const d of OPENINGS.ballroomDoors) { c.clear(d.at[0], 1, d.at[2]); c.clear(d.at[0], 2, d.at[2]); door(c, d.at, d.facing, 'west', { wood: P.wood, hinge: d.hinge }) }
  const gd = OPENINGS.galleryDoor
  door(c, gd.at, gd.facing, 'west', { wood: P.wood })
  const bd = OPENINGS.ballroomGardenDoor
  door(c, bd.at, bd.facing, 'south', { wood: P.wood })
  windows(c, 3)
  hood(c, 23, 4, 16, P.roof)

  // Pass 3, depth and detail: plinth course and string course round the new faces, buttresses between the tall
  // windows, kneelers
  const faces = [
    ['z', 27, 0, 16, 'east', 'west'], ['x', 1, 18, 28, 'north', 'south'], ['x', 15, 18, 28, 'south', 'north'], 
  ]
  for (const [axis, line, u0, u1, out, toward] of faces) {
    const cells = faceCells(axis, line, u0, u1, out)
    plinthCourse(c, cells, 0, stone, (x, z) => x <= 19 && z >= 15)
    // no string course across the front and the east side: the tall windows run unbroken through the floor line
    if (line === 15) moulding(c, cells, 5, P.dressStairs, toward, (x, z) => x <= 19)
  }
  for (const z of B.buttresses) masonButtress(c, 28, z, 0, 6, P.dress, P.dressStairs, 'west')
  for (const z of [1, 15]) {
    kneeler(c, [18, 9, z], P.dress, P.dressStairs, 'east')
    kneeler(c, [28, 9, z], P.dress, P.dressStairs, 'west')
  }

  // Pass 4, interior: the gallery's railing, the lamps hung from arms on the walls, the plan's furniture, note blocks
  railingRun(c, [[gl.x0, gl.y + 1, gl.z0], [gl.x1, gl.y + 1, gl.z0]], P.wood)
  for (const [x, y, z, side] of BRACKET_LAMPS[3]) {
    c.set(x, y + 1, z, `${P.wood}_fence`) // a short iron-dark arm out of the wall (it joins the wall behind)
    c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
    c.spot('lamp', [x, y, z], side === 'west' ? 'east' : 'west', [[0, 0, 0]], P.wood)
  }
  furnish(c, 3)
  for (const x of [22, 24]) c.set(x, 6, 14, 'note_block', { instrument: 'harp', note: '0', powered: 'false' })

  // Pass 5, ground: the garden runs on behind the ballroom: a terrace at its door, the cross walk through a gate in the
  // old east wall, rose beds, walls and piers to match
  const cells = []
  for (let z = 16; z <= 24; z++) cells.push([27, z])
  for (let x = 20; x <= 26; x++) cells.push([x, 24])
  gardenWall(c, cells, [[27, 16], [27, 20], [27, 24], [23, 24]], [[27, 24], [27, 16]])
  const gg = OPENINGS.gardenGate
  for (const y of [1, 2]) c.clear(gg.x, y, gg.z) // a gateway between two piers under the coping
  c.set(gg.x, 0, gg.z, paving)
  gardenWall(c, [], [[gg.x, gg.z - 1]], []) // the gateway's second pier (the first stands at z 20)
  for (let x = 20; x <= 26; x++) for (let z = 16; z <= 23; z++) c.set(x, 0, z, P.garden.soil, { snowy: 'false' })
  for (let x = 20; x <= 26; x++) for (const z of [16, 17, 19]) c.set(x, 0, z, paving)
  for (let z = 18; z <= 23; z++) c.set(23, 0, z, paving)
  for (const k of OUTSIDE.eastKnots) knot(c, k)
}

// --- Level IV: the cellar ----------------------------------------------------------------------------------------------
function levelFour(c) {
  const Cl = MASSES.cellar
  const inside = cellar(c, Cl.x0, Cl.z0, Cl.x1, Cl.z1, Cl.depth, stone, P.dress)
  // the spine wall under the wing's east wall carries the floor over the cellar's middle
  for (let z = Cl.z0 + 1; z < Cl.z1; z++) for (let y = -3; y <= -1; y++) c.set(7, y, z, P.dress)
  for (const z of [10, 13]) { c.clear(7, -3, z); c.clear(7, -2, z) } // two doorways through it
  const L = OPENINGS.hatch
  hatchLadder(c, L.x, L.z, -3, 0, L.facing, P.wood)
  // wine casks along both end walls, two high in the middle; the family's chests between decorated pots at the back
  for (let z = 10; z <= 13; z++) { c.set(2, -3, z, 'barrel', { facing: 'east', open: 'false' }); c.set(12, -3, z, 'barrel', { facing: 'west', open: 'false' }) }
  for (const z of [11, 12]) { c.set(2, -2, z, 'barrel', { facing: 'east', open: 'false' }); c.set(12, -2, z, 'barrel', { facing: 'west', open: 'false' }) }
  for (const x of [4, 10]) c.set(x, -3, 14, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
  furnish(c, 4)
  return inside
}

export function draw(level = 1) {
  const c = level === 4 ? new LiftedCanvas(SIZE[4], BASEMENT) : new Canvas(SIZE[level])
  levelOne(c)
  if (level >= 2) levelTwo(c)
  if (level >= 3) levelThree(c)
  if (level >= 4) c.cellarInside = levelFour(c)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = level === 1 ? 'manor' : `manor_${level}`
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  if (c.cellarInside) fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), c.cellarInside.join(','))
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
