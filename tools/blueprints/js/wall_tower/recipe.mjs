// The Wall Tower (`wall_tower`, Level II `wall_tower_2`), the recipe: assembles plan.mjs from the shared parts library
// (../parts) in passes (block-out and structure, openings, depth and detail, interior, light) and returns the structure
// for the minecraft-builder skill's runner. LEVEL=1 (default) draws `wall_tower`; LEVEL=2 draws `wall_tower_2`: Level I
// drawn first and grown. Writes furniture.json beside it for rebuild.py (empty). CUT=<y> builds a cutaway.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, KIT, TOWER, MASSES, FIXTURES, FURNITURE, LIGHTS } from './plan.mjs'
import { Canvas, mix, slab, trapdoor, quoins, stringCourse, crenels, arrowLoop, corbelLine, crossGableCrown, batteredFoot, stair, furnitureParts } from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level = 1) {
  const c = new Canvas(SIZE[level])
  const P = KIT, T = TOWER, M = MASSES, S = M.shaft
  const plinth = mix(P.plinth, 21), ashlar = mix(P.ashlar, 22)
  const ring = (x, z) => x === S.x0 || x === S.x1 || z === S.z0 || z === S.z1

  // --- Pass 1: block-out and structure: the shaft, its floors, the deck ---------------------------------------------
  for (let x = S.x0; x <= S.x1; x++) for (let z = S.z0; z <= S.z1; z++) {
    if (ring(x, z)) for (let y = S.y0; y <= S.top; y++) c.set(x, y, z, y <= S.plinthTop ? plinth : ashlar)
    else {
      c.set(x, M.groundFloor.y, z, P.groundFloor) // the lock-up's floor
      slab(c, x, M.walkFloor.y, z, P.wood.floor) // the walk room's floor: slabs, so no station below the top
    }
    c.set(x, M.deck.y, z, ring(x, z) ? P.course : P.merlon) // the deck, its rim the pale course (the crenels' sills)
  }
  // the fighting top: merlons on the corners and side middles, coped (Level I) so no station stands on a merlon
  const rim = []
  for (let x = S.x0; x <= S.x1; x++) for (let z = S.z0; z <= S.z1; z++) if (ring(x, z)) rim.push([x, z])
  crenels(c, rim, M.merlons.y, M.merlons.y, P.merlon, (i, x, z) => M.merlons.at(x, z), level === 1 ? P.cap : null)

  // --- Pass 2: openings: the door, the walk arches, the loops, the ladder ------------------------------------------
  const [dx, dy, dz] = M.door // on the plinth; its lintel is the string course
  for (const half of ['lower', 'upper'])
    c.set(dx, dy + (half === 'upper' ? 1 : 0), dz, P.wood.door, { facing: 'south', half, hinge: 'left', open: 'false', powered: 'false' })
  for (const [x, z] of M.arches) {
    c.clear(x, M.walkFloor.y + 1, z); c.clear(x, M.walkFloor.y + 2, z)
    slab(c, x, M.walkFloor.y, z, P.courseSlab) // the sill: level with the walk room's slab floor
  }
  for (const [x, y, z, inward] of M.loops) arrowLoop(c, x, y, z, inward, P.courseStairs) // (its head: a machicolation)
  const L = M.ladder
  for (let y = L.y0; y <= L.y1; y++) c.set(L.x, y, L.z, 'ladder', { facing: L.facing, waterlogged: 'false' })
  // a hatch where the ladder passes the walk room's floor: shut, it keeps the lock-up a room (and the floor whole)
  trapdoor(c, L.x, M.walkFloor.y, L.z, P.wood.hatch, L.facing)

  // --- Pass 3: depth and detail: the string course (the wall walk's line, round the tower), the quoins -------------
  stringCourse(c, S.x0, S.z0, S.x1, S.z1, S.courseY, P.course,
    (x, z) => M.arches.some(([ax, az]) => ax === x && az === z) || M.loops.some(([lx, ly, lz]) => lx === x && lz === z && ly - 1 === S.courseY))
  const corners = [[S.x0, S.z0], [S.x1, S.z0], [S.x0, S.z1], [S.x1, S.z1]]
  quoins(c, corners, S.plinthTop + 1, S.courseY - 1, P.quoin, P.quoinAlt)
  quoins(c, corners, S.courseY + 1, S.top, P.quoinAlt, P.quoin)
  corbelLine(c, S.x0, S.z0, S.x1, S.z1, M.corbels.y, T.machicolation) // the machicolations, corners full as piers
  // the batter: the foot splayed on every face but under the door
  batteredFoot(c, S.x0, S.z0, S.x1, S.z1, S.y0, T.batter, (x, z) => x === M.door[0] && z === M.door[2])
  // the wall walk's arches framed in pale stone: jambs either side (the head is the machicolation over it)
  for (const [x, z] of M.arches) for (const jz of [z - 1, z + 1]) for (let y = M.walkFloor.y + 1; y <= M.walkFloor.y + 2; y++) c.set(x, y, jz, T.jamb)

  // --- Pass 4: interior: the guardroom's table and benches, the walk room's arrow store and bench ----------------
  for (const f of FIXTURES) {
    if (f.on) c.set(...f.on, T.patch) // a plank patch in the slab floor, so the piece stands level
    if (f.kind === 'chair') stair(c, ...f.pos, T.bench, f.facing)
    else if (f.kind === 'table') {
      c.set(...f.pos, T.table.leg, { north: 'false', south: 'false', east: 'false', west: 'false', waterlogged: 'false' })
      c.set(f.pos[0], f.pos[1] + 1, f.pos[2], T.table.top)
    } else if (f.kind === 'storage') c.set(...f.pos, T.store, { facing: f.facing, type: 'single', waterlogged: 'false' })
  }

  if (level >= 2) {
    // --- Level II, the Gabled Tower: the corner merlons carried up whole, the crown of four gables -----------------
    for (const [x, z] of rim) if (M.merlons.at(x, z)) {
      const corner = (x === S.x0 || x === S.x1) && (z === S.z0 || z === S.z1)
      if (corner) c.set(x, M.merlons.y + 1, z, P.merlon); else c.clear(x, M.merlons.y + 1, z) // a lantern hangs there
    }
    for (let x = S.x0 + 1; x < S.x1; x++) for (let z = S.z0 + 1; z < S.z1; z++) c.set(x, M.ceiling.y, z, P.wood.board) // the loft's ceiling
    crossGableCrown(c, S.x0, S.z0, S.x1, S.z1, M.hat.y, { stairs: T.crownStairs, gable: T.gable, slab: T.crownSlab })
  }
  // --- Pass 5: light ----------------------------------------------------------------------------------------------
  for (const p of LIGHTS[level]) c.set(...p, 'lantern', { hanging: 'true', waterlogged: 'false' })
  for (const f of FURNITURE[level]) furnitureParts[f.part](c, f)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = level === 1 ? 'wall_tower' : `wall_tower_${level}`
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
