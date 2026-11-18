// The Clock Tower, the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out and
// structure, the machine and its water, openings, depth and detail, interior, ground) and returns the structure for
// the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts recorded).
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, MACHINE, OPENINGS, FURNITURE, JOB } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, wallPanel, window, door, quoins, stringCourse, corbelRing, compassRose, ladderShaft,
  batter, furnitureParts, fallRace, dialFace, bellcastSpire, kineticTrain, stair,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const S = MASSES.shaft, Sp = MASSES.spout, C = MASSES.chamber, R = MASSES.spire, M = MACHINE
  const shaft = mix(P.shaft, 51)
  const isFront = (x, z) => z === S.z0
  const keepOut = new Set([M.wheel.at, ...M.shafts.flatMap(s => s.cells), ...M.gearboxes.map(g => g.at)].map(p => p.join(',')))

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // the shaft: a deepslate plinth, stone-brick walls, deepslate quoins and a string course at the loft floor
  plinth(c, S.x0, S.z0, S.x1, S.z1, P.dressing)
  floor(c, S.room.x0, S.room.z0, S.room.x1, S.room.z1, 0, P.floor)
  for (const [ax, az, bx, bz] of [[S.x0, S.z0, S.x1, S.z0], [S.x0, S.z1, S.x1, S.z1], [S.x0, S.z0, S.x0, S.z1], [S.x1, S.z0, S.x1, S.z1]])
    wallPanel(c, [ax, az], [bx, bz], S.y0, S.y1, shaft)
  quoins(c, [[S.x0, S.z0], [S.x1, S.z0], [S.x0, S.z1], [S.x1, S.z1]], S.y0, S.y1, P.dressing, P.dressingAlt)
  stringCourse(c, S.x0, S.z0, S.x1, S.z1, S.course, P.dressing)
  for (const y of S.floors) floor(c, S.room.x0, S.room.z0, S.room.x1, S.room.z1, y, P.floor)

  // the chamber: corbels under the jetty, a 9 x 9 sill floor (a dark oak beam round its edge), a frame of corner
  // posts and a top plate, calcite between, a ceiling of planks under the spire
  corbelRing(c, C.x0, C.z0, C.x1, C.z1, C.corbelY, P.frameStairs)
  floor(c, C.x0 + 1, C.z0 + 1, C.x1 - 1, C.z1 - 1, C.sill, P.planks)
  for (let x = C.x0; x <= C.x1; x++) for (const z of [C.z0, C.z1]) c.set(x, C.sill, z, P.frame, { axis: 'x' })
  for (let z = C.z0 + 1; z < C.z1; z++) for (const x of [C.x0, C.x1]) c.set(x, C.sill, z, P.frame, { axis: 'z' })
  for (const [ax, az, bx, bz] of [[C.x0, C.z0, C.x1, C.z0], [C.x0, C.z1, C.x1, C.z1], [C.x0, C.z0, C.x0, C.z1], [C.x1, C.z0, C.x1, C.z1]])
    wallPanel(c, [ax, az], [bx, bz], C.y0, C.y1 - 1, P.infill)
  for (const [x, z] of [[C.x0, C.z0], [C.x1, C.z0], [C.x0, C.z1], [C.x1, C.z1]])
    for (let y = C.y0; y <= C.y1; y++) c.set(x, y, z, P.frame, { axis: 'y' })
  for (let x = C.x0 + 1; x < C.x1; x++) for (const z of [C.z0, C.z1]) c.set(x, C.y1, z, P.frame, { axis: 'x' })
  for (let z = C.z0 + 1; z < C.z1; z++) for (const x of [C.x0, C.x1]) c.set(x, C.y1, z, P.frame, { axis: 'z' })
  // tie beams across the top plate, crossing under the spire's king post (the chamber open to the spire above)
  for (let x = C.x0 + 1; x < C.x1; x++) c.set(x, C.y1, R.cz, P.frame, { axis: 'x' })
  for (let z = C.z0 + 1; z < C.z1; z++) c.set(R.cx, C.y1, z, P.frame, { axis: 'z' })

  // the spire: three 45-degree courses from the eave, then the steep needle; a king post from the ceiling to the tip
  for (let y = R.kingPost[0]; y <= R.kingPost[1]; y++) c.set(R.cx, y, R.cz, P.frame, { axis: 'y' })
  bellcastSpire(c, R.cx, R.cz, R.half, R.y0, R.flare, P.roof, { apex: [P.apex, { facing: 'up', powered: 'false', waterlogged: 'false' }] })

  // --- Pass 2: the machine and its water ----------------------------------------------------------------------------
  // the spout pier and its race first (the stone the water falls against and lands in), then the wheel and the drive
  for (let y = Sp.pier.y0; y <= Sp.pier.y1; y++) for (let z = Sp.pier.z0; z <= Sp.pier.z1; z++)
    c.set(Sp.pier.x, y, z, y === Sp.pier.y1 ? P.dressingAlt : P.dressing)
  for (const [x, y, z] of Sp.footing) c.set(x, y, z, P.dressing)
  fallRace(c, Sp.race, { wall: P.dressing, floorId: P.dressing, stairs: P.dressingStairs })
  c.set(...M.bearing, P.bearing)
  kineticTrain(c, M)
  for (const k of M.clocks) dialFace(c, k.at, k.facing, k.along, { surround: P.dial, surroundProps: { axis: 'y' } })

  // --- Pass 3: openings -------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: P.dressingStairs })
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
  const L = OPENINGS.ladder
  ladderShaft(c, L.x, L.z, L.y0, L.y1, L.facing)

  // --- Pass 4: depth and detail -------------------------------------------------------------------------------------
  // a battered foot round the shaft (not across the door's step or the race), a coping slab on the pier's top
  batter(c, S.x0 - 1, S.z0 - 1, S.x1 + 1, S.z1 + 1, 0, P.dressingStairs,
    (x, z) => (z === S.z0 - 1 && x <= 5) || (x === S.x0 - 1 && z <= S.z0) || (x === fd.at[0] && z === S.z0 - 1))
  c.set(Sp.pier.x, Sp.pier.y1 + 1, Sp.pier.z0, `${P.dressingStairs.replace('_stairs', '_slab')}`, { type: 'bottom', waterlogged: 'false' })
  c.set(Sp.pier.x, Sp.pier.y1 + 1, Sp.pier.z1, `${P.dressingStairs.replace('_stairs', '_slab')}`, { type: 'bottom', waterlogged: 'false' })
  // the floor of the map room inlaid with a compass rose round its middle
  compassRose(c, 7, 0, 7, { heart: P.rose.heart, arm: P.rose.arm, diag: P.rose.diag, reach: 2 })

  // --- Pass 5: interior (the plan's furniture) and the job block ------------------------------------------------------
  c.set(...JOB, 'cartography_table')
  for (const f of FURNITURE) furnitureParts[f.part](c, f)

  // nothing structural may stand in the drive's cells (a check, not a fix)
  for (const k of keepOut) { const [x, y, z] = k.split(',').map(Number); if (!c.name(x, y, z).startsWith('create:')) throw new Error(`drive cell ${k} is ${c.name(x, y, z)}`) }
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `clock_tower_cut${cut}` : 'clock_tower' } }
}
