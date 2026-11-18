// Tinker's Workshop, the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out and
// structure, roofs, openings, depth and detail, interior, ground and light). One drawing for both levels: Level II is
// Level I with the tower grown a storey through its old cap (the cap's eave kept as a skirt), a stores loft with two
// double chests up a ladder, and a new cap with a lightning rod, so Level I's cells stay put.
// LEVEL=1|2 picks the level (default 1); CUT=<y> builds a cutaway with everything above y removed. Writes
// furniture.json (the level's furniture spots) beside it for rebuild.py.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { LEVELS, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, post, beamRing, wallPanel, window, door, quoins, gableRoof, gableWall, bargeTrim,
  chimney, hood, flowerBed, woodpile, lampPost, furnitureParts, stockShelf, mantel, beamRun, hipCap, skirtRing,
  doubleChest, lampOn, railSpur, stringCourse,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const has = (lv, f) => (f.level ?? 1) <= lv

export function draw(level = 1) {
  const c = new Canvas(LEVELS[level].size)
  const P = PALETTE
  const stone = mix(P.stone, 13)
  const T = MASSES.tower, H = MASSES.hall, S = MASSES.stack
  const tw = T.walls, hw = H.walls, hp = H.plinth, hr = H.room
  const inTower = (x, z) => x >= tw.x0 && x <= tw.x1 && z >= tw.z0 && z <= tw.z1
  const topY = T.topY[level]

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // the ground: a deepslate plinth under the hall (a block proud of its walls, where the posts stand) and the tower
  plinth(c, hp.x0, hp.z0, hp.x1, hp.z1, stone)
  plinth(c, tw.x0, tw.z0, tw.x1, tw.z1, stone)
  floor(c, hr.x0, hr.z0, hr.x1, hr.z1, 0, P.hallFloor)
  floor(c, T.room.x0, T.room.z0, T.room.x1, T.room.z1, 0, P.towerFloor)

  // Hall: brick walls on the plinth, a beam ring on them, dark oak posts a block proud carrying the eaves and the
  // jettied gables, tie beams across the room for the lamps
  wallPanel(c, [hw.x0, hw.z0], [tw.x0 - 1, hw.z0], 1, H.wallTop, P.walls)
  wallPanel(c, [tw.x1 + 1, hw.z0], [hw.x1, hw.z0], 1, H.wallTop, P.walls)
  wallPanel(c, [hw.x0, hw.z1], [hw.x1, hw.z1], 1, H.wallTop, P.walls)
  wallPanel(c, [hw.x0, hw.z0], [hw.x0, hw.z1], 1, H.wallTop, P.walls)
  wallPanel(c, [hw.x1, hw.z0], [hw.x1, hw.z1], 1, H.wallTop, P.walls)
  beamRing(c, hw.x0, hw.z0, hw.x1, hw.z1, H.beamY, P.frame)
  for (const x of H.frontPosts) post(c, x, hp.z0, 1, H.wallTop, P.frame)
  for (const x of H.backPosts) post(c, x, hp.z1, 1, H.wallTop, P.frame)
  for (const z of H.sidePosts) { post(c, hp.x0, z, 1, H.wallTop, P.frame); post(c, hp.x1, z, 1, H.wallTop, P.frame) }
  for (const x of [hp.x0, hp.x1]) for (let z = hw.z0; z <= hw.z1; z++) c.set(x, H.beamY, z, P.frame, { axis: 'z' }) // jetty beams
  for (const x of H.ties) for (let z = hr.z0; z <= hr.z1; z++) c.set(x, H.beamY, z, P.frame, { axis: 'z' })

  // Stack: the forge chimney up the back wall's middle, its breast either side, smoke over the ridge
  chimney(c, S.x, S.z, 0, S.top, P.walls, { breast: { axis: 'x', top: S.breastTop, stair: P.wallStairs } })

  // Tower: brick on its plinth, dark quoins, a polished band at the floor line, a dark oak band under the cap (and,
  // at Level II, under the new storey's cap)
  for (let y = 1; y <= topY; y++) stringCourse(c, tw.x0, tw.z0, tw.x1, tw.z1, y, P.walls)
  stringCourse(c, tw.x0, tw.z0, tw.x1, tw.z1, T.bandY, P.band)
  quoins(c, [[tw.x0, tw.z0], [tw.x1, tw.z0], [tw.x0, tw.z1], [tw.x1, tw.z1]], 1, T.bandY - 1, P.quoin, P.quoinAlt)
  quoins(c, [[tw.x0, tw.z0], [tw.x1, tw.z0]], T.bandY + 1, topY, P.quoin, P.quoinAlt)
  beamRing(c, tw.x0, tw.z0, tw.x1, tw.z1, T.beamY, P.frame)
  if (level >= 2) beamRing(c, tw.x0, tw.z0, tw.x1, tw.z1, topY + 1, P.frame)
  beamRun(c, 'x', T.cap.cz, T.room.x0, T.room.x1, T.beamY, P.frame, `${P.trim}_stairs`, false) // the lamp's beam

  // --- Pass 2: roofs --------------------------------------------------------------------------------------------
  // the hall's long copper roof, everywhere the tower and the stack aren't
  const keepHall = (x, y, z) => !c.get(x, y, z) && !inTower(x, z)
  const hall = gableRoof(c, H.roof, P.roof, keepHall)
  // the hall's gables, jettied out over the side posts: brick round a dark oak king stud and a little window
  for (const line of [hp.x0, hp.x1])
    gableWall(c, 'x', line, H.roof.across, H.beamY + 1, hall.under, (u, y) => (u === 8 && y === H.beamY + 1 ? [P.frame, { axis: 'y' }] : P.walls))
  for (const line of [hp.x0, hp.x1]) bargeTrim(c, 'x', line, H.roof.across, hall.under, `${P.trim}_stairs`)
  // the tower's copper hip cap; at Level II its eave stays as a skirt at the floor line and the cap goes up a storey
  const cap = T.cap
  if (level >= 2) {
    skirtRing(c, cap.cx, cap.cz, cap.half, T.skirtY, P.roof)
    hipCap(c, cap.cx, cap.cz, cap.half, cap.y[2], P.roof, { apex: ['lightning_rod', { facing: 'up', powered: 'false', waterlogged: 'false' }] })
  } else hipCap(c, cap.cx, cap.cz, cap.half, cap.y[1], P.roof)
  // where the cap's back eave overhangs the hall's front slope: copper under it, so the two roofs meet closed
  for (let x = cap.cx - cap.half; x <= cap.cx + cap.half; x++) {
    const z = cap.cz + cap.half, y = T.skirtY - 1
    if (!c.get(x, y, z)) c.set(x, y, z, P.roof.full)
  }

  // --- Pass 3: openings -----------------------------------------------------------------------------------------
  const O = OPENINGS
  door(c, O.door.at, O.door.facing, 'north', { wood: P.door, step: O.door.step, stepStair: 'cobbled_deepslate_stairs' })
  const a = O.arch
  for (let x = a.x0; x <= a.x1; x++) {
    for (let y = a.y0; y <= a.y1; y++) c.clear(x, y, a.z)
    c.set(x, 0, a.z, P.hallFloor)
  }
  const pots = {
    'hall front west': ['potted_red_tulip'], 'hall front east': ['potted_cornflower'],
    'hall west front bay': ['potted_fern', null], 'hall east back bay': [null, 'potted_fern'],
  }
  const wins = level >= 2 ? [...O.windows, ...O.windows2] : O.windows
  for (const w of wins) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: pots[w.name] ?? [] })
  if (level >= 2) { // the stores loft's floor and the ladder up through it
    const ld = O.ladder
    floor(c, T.room.x0, T.room.z0, T.room.x1, T.room.z1, O.loftFloorY, P.towerFloor)
    for (let x = T.room.x0; x <= T.room.x1; x++) for (let z = T.room.z0; z <= T.room.z1; z++)
      if (!c.get(x, O.topFloorY, z)) c.set(x, O.topFloorY, z, P.towerFloor) // round the lamp's beam
    for (let y = ld.y0; y <= ld.y1; y++) c.set(ld.x, y, ld.z, 'ladder', { facing: ld.facing, waterlogged: 'false' })
  }

  // --- Pass 4: depth and detail -----------------------------------------------------------------------------------
  // a copper hood over the door on the band, its lantern over the step
  hood(c, O.hood.x, O.hood.y, O.hood.z, P.roof)
  // the forge: a brick hood over the hearth's niche, a breast up to the rafters
  mantel(c, S.hearth, 'north', P.walls, P.wallStairs, 5)

  // --- Pass 5: interior (the plan's furniture) --------------------------------------------------------------------
  for (const f of FURNITURE) {
    if (!has(level, f)) continue
    const [x, y, z] = f.pos
    switch (f.part) {
      case 'furnace': c.set(x, y, z, 'furnace', { facing: f.facing, lit: 'false' }); break
      case 'smithing': c.set(x, y, z, 'smithing_table'); break
      case 'anvil': c.set(x, y, z, 'anvil', { facing: f.facing }); break
      case 'doubleChest': doubleChest(c, f.pos, f.facing, 'south'); break
      case 'lampOn': lampOn(c, f); break
      case 'candleOn': c.free(f.pos); c.set(x, y, z, 'candle', { candles: '3', lit: 'true', waterlogged: 'false' }); c.spot('lamp', f.pos, f.facing, [[0, 0, 0]], f.wood); break
      case 'stockShelf': stockShelf(c, f); break
      default: furnitureParts[f.part](c, f)
    }
  }

  // --- Pass 6: ground and light -----------------------------------------------------------------------------------
  const wp = OUTSIDE.woodpile
  woodpile(c, wp.x, wp.z0, wp.z1, P.fuel)
  const sd = OUTSIDE.siding
  floor(c, sd.paving.x0, sd.paving.z0, sd.paving.x1, sd.paving.z1, 0, P.paving)
  railSpur(c, 'z', sd.rail.x, sd.rail.z0, sd.rail.z1, 1, P.paving)
  const fb = OUTSIDE.flowerBed
  flowerBed(c, fb.x0, fb.x1, fb.z, fb.plants)
  for (const [x, y, z] of OUTSIDE.lamps) {
    if (!c.get(x, y - 1, z)) c.set(x, y - 1, z, stone)
    lampPost(c, x, y, z, P.post)
  }
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  console.error(`blocks: ${canvas.cells.size}`)
  const name = level === 2 ? 'tinkers_workshop_2' : 'tinkers_workshop'
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${name}_cut${cut}` : name } }
}
