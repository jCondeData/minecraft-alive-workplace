// The Print Shop (print_shop, print_shop_2), the recipe: assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, roof and lantern, openings, depth and detail, interior, ground) for LEVEL 1 or 2, and
// writes furniture.json (every furniture spot the parts recorded) for rebuild.py. Build: sh build.sh. CUT=<y> builds
// a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZES, PALETTE, MASSES, PRESS, OPENINGS, FURNITURE, BOOKS } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, wallPanel, window, door, gableWall, bargeTrim, beamRing, quoins, stair, slab,
  furnitureParts, psRoofUnion, psLightLantern, psArch, psParts, psBracketLamp,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const parts = { ...furnitureParts, ...psParts }

export function draw(level) {
  const c = new Canvas(SIZES[level])
  const P = PALETTE
  const two = level >= 2
  const plinthStone = mix(P.plinth, 3)
  const H = MASSES.hall, L = MASSES.lantern, R = MASSES.reading
  const { x0, x1, z0, z1 } = H.walls

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // the Hall: a stone-brick plinth ring, a spruce floor, brick walls y 1..4 with stone-brick quoins
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const edge = x === x0 || x === x1 || z === z0 || z === z1
    c.set(x, 0, z, edge ? plinthStone : P.floor)
  }
  wallPanel(c, [x0, z0], [x1, z0], 1, H.wallTop, P.wall)
  wallPanel(c, [x0, z1], [x1, z1], 1, H.wallTop, P.wall)
  wallPanel(c, [x0, z0], [x0, z1], 1, H.wallTop, P.wall)
  wallPanel(c, [x1, z0], [x1, z1], 1, H.wallTop, P.wall)
  quoins(c, [[x0, z0], [x1, z0], [x0, z1], [x1, z1]], 1, H.wallTop, P.stone, P.wall)

  if (two) { // the Reading Room: its plinth and floor, three new brick walls (the Hall's east wall is its west wall)
    for (let x = R.walls.x0 + 1; x <= R.walls.x1; x++) for (let z = R.walls.z0; z <= R.walls.z1; z++) {
      const edge = x === R.walls.x1 || z === R.walls.z0 || z === R.walls.z1
      c.set(x, 0, z, edge ? plinthStone : P.floor)
    }
    wallPanel(c, [R.walls.x0 + 1, R.walls.z0], [R.walls.x1, R.walls.z0], 1, R.wallTop, P.wall)
    wallPanel(c, [R.walls.x0 + 1, R.walls.z1], [R.walls.x1, R.walls.z1], 1, R.wallTop, P.wall)
    wallPanel(c, [R.walls.x1, R.walls.z0], [R.walls.x1, R.walls.z1], 1, R.wallTop, P.wall)
    quoins(c, [[R.walls.x1, R.walls.z0], [R.walls.x1, R.walls.z1]], 1, R.wallTop, P.stone, P.wall)
  }

  // --- Pass 2: roofs, gables and the lantern ----------------------------------------------------------------------
  const inLantern = (x, z) => x >= L.x0 && x <= L.x1 && z >= L.z0 && z <= L.z1
  const gables = [H.roof, ...(two ? [R.roof] : [])]
  const roof = psRoofUnion(c, gables, P.roof, (x, z) => !inLantern(x, z))
  const under = {}
  for (let x = H.roof.across[0]; x <= H.roof.across[1]; x++) under[x] = H.roof.eaveY + Math.min(x, H.roof.across[1] - x)
  const gablePick = (u, y) => P.wall
  gableWall(c, 'z', z0, [x0, x1], H.wallTop + 1, under, gablePick)
  gableWall(c, 'z', z1, [x0, x1], H.wallTop + 1, under, gablePick)
  // the lantern: a brick box astride the ridge, panes in its faces, a glazed light at its top over the open shaft
  psLightLantern(c, { ...L, base: (x, z) => roof.at(x, z) }, {
    wall: P.wall, corner: P.stone, glass: P.skylight, pane: P.glass, cap: P.roof, hang: [[4, 4], [6, 6]],
  })

  if (two) {
    const ru = {}
    for (let x = R.roof.across[0]; x <= R.roof.across[1]; x++) ru[x] = R.roof.eaveY + Math.min(x - R.roof.across[0], R.roof.across[1] - x)
    gableWall(c, 'z', R.walls.z0, [R.walls.x0 + 1, R.walls.x1], R.wallTop + 1, ru, gablePick)
    gableWall(c, 'z', R.walls.z1, [R.walls.x0 + 1, R.walls.x1], R.wallTop + 1, ru, gablePick)
    // barge boards under its rakes, east of the valley only (the Hall's room lies under the rest of the span)
    const mid = (R.roof.across[0] + R.roof.across[1]) / 2
    for (const z of R.roof.along) for (let x = R.walls.x0 + 1; x <= R.roof.across[1]; x++)
      if (!c.get(x, ru[x] - 1, z)) stair(c, x, ru[x] - 1, z, `${P.wood}_stairs`, x <= mid ? 'east' : 'west', 'top')
  }
  bargeTrim(c, 'z', H.roof.along[0], H.roof.across, under, `${P.wood}_stairs`)
  bargeTrim(c, 'z', H.roof.along[1], H.roof.across, under, `${P.wood}_stairs`)

  // --- Pass 3: openings -------------------------------------------------------------------------------------------
  const O = OPENINGS
  door(c, O.frontDoor.at, O.frontDoor.facing, 'north', { wood: P.wood, step: O.frontDoor.step, stepStair: 'stone_brick_stairs' })
  for (const w of O.shopWindows) window(c, w, 'north', 'sill', { glass: P.glass, wood: P.wood, sill: `${P.wood}_slab` })
  for (const w of O.sideWindows) {
    if (w.level === 1 && two) continue // under the Reading Room's roof, the Hall's east windows give way to the arch
    window(c, w.cells, w.out, w.dress === undefined ? 'shutters' : w.dress, { glass: P.glass, wood: P.wood })
  }
  window(c, O.frontGable, 'north', 'shutters', { glass: P.glass, wood: P.wood })
  window(c, O.backGable, 'south', null, { glass: P.glass })
  if (two) {
    const a = O.arch
    psArch(c, a.axis, a.line, a.span, a.y0, a.y1, 'brick_stairs')
    for (const [, y, z] of O.sideWindows.filter(w => w.level === 1).flatMap(w => w.cells)) if (!(z >= a.span[0] && z <= a.span[1] && y <= a.y1)) c.set(9, y, z, P.wall)
    for (const w of O.readingWindows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, sill: `${P.wood}_slab` })
    for (const g of O.readingGables) window(c, g, 'north', null, { glass: P.glass })
    // a stone sill course under the east wall's high windows, so the long wall over the bookcases isn't one flat field
    for (let z = R.walls.z0; z <= R.walls.z1; z++) c.set(R.walls.x1, 2, z, P.stone)
  }

  // --- Pass 4: depth and detail -----------------------------------------------------------------------------------
  // the shopfront: a stone apron the sills stand on, a dark-oak fascia over the windows, bracket lamps by the door
  // (between the quoins only: (1, 0, 0) stays open ground, where the B156 test stands a Printer by the front wall)
  for (let x = x0 + 1; x < x1; x++) if (x !== O.frontDoor.step[0]) c.set(x, 0, z0 - 1, plinthStone)
  for (let x = x0 + 1; x < x1; x++) c.set(x, H.wallTop, z0, P.fascia, { axis: 'x' })
  psBracketLamp(c, [4, 4, 1], 'north', `${P.wood}_fence`)
  psBracketLamp(c, [6, 4, 1], 'north', `${P.wood}_fence`)
  // tie beams across the hall at the wall top, either side of the lantern's shaft (the shop's lamps hang from them)
  for (const z of H.tieBeams) for (let x = x0 + 1; x < x1; x++) c.set(x, H.wallTop, z, P.beam, { axis: 'x' })

  // --- Pass 5: interior -------------------------------------------------------------------------------------------
  c.set(...PRESS, 'cartography_table')
  for (const f of FURNITURE) if (f.level <= level) parts[f.part](c, f)
  if (two) {
    for (const [x, z, y0, y1] of BOOKS) for (let y = y0; y <= y1; y++) c.set(x, y, z, 'bookshelf')
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
  const id = level === 1 ? 'print_shop' : `print_shop_${level}`
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
