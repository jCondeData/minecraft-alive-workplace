// Terrace, the snowy look (ROADMAP 49.10c): assembles plan.mjs from the parts library (../parts) in passes (block-out
// and structure, roofs and gables, openings, depth and detail, interior, snow and ground) for LEVEL = 1 or 2, II the
// row of I with its third home on the east end. Writes furniture.json (the furniture spots). Build both: build.sh.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, PALETTE, HOUSE, HOMES, at, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, floor, wallPanel, window, door, evenGableRoof, gableWall, furnitureParts, quoins, stringCourse,
  snowLoad, snowSkirt, rakeVerge, ridgeStack, skinWall, stair,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function drawLevel(level) {
  const c = new Canvas(SIZES[level])
  const P = PALETTE
  const wall = mix(P.wall, 7), stack = mix(P.stack, 13)
  const homes = HOMES.filter(h => h.level <= level)
  const { z0, z1 } = HOUSE.walls
  const lines = new Map()
  for (const h of homes) for (const u of [0, 5]) lines.set(at(h, u), (lines.get(at(h, u)) ?? 0) + 1)
  const isEnd = x => lines.get(x) === 1
  const xs = [...lines.keys()]
  const west = Math.min(...xs), east = Math.max(...xs)

  // --- Pass 1: block-out and structure -----------------------------------------------------------------------------
  for (const h of homes) {
    const xa = at(h, 0), xb = at(h, 5)
    for (let x = xa; x <= xb; x++) for (let z = z0; z <= z1; z++) {
      const edge = x === xa || x === xb || z === z0 || z === z1
      c.set(x, 0, z, edge ? P.plinth : P.floor)
    }
    wallPanel(c, [xa, z0], [xa, z1], 1, HOUSE.wallTop, wall)
    wallPanel(c, [xb, z0], [xb, z1], 1, HOUSE.wallTop, wall)
    wallPanel(c, [xa, z0], [xb, z0], 1, HOUSE.wallTop, wall)
    wallPanel(c, [xa, z1], [xb, z1], 1, HOUSE.wallTop, wall)
    floor(c, xa + 1, z0 + 1, xb - 1, z1 - 1, HOUSE.upperFloor, P.floor)
    for (const z of [3, 6]) for (let x = xa + 1; x <= xb - 1; x++) c.set(x, HOUSE.upperFloor, z, P.joist, { axis: 'x' })
  }
  // the thick ground storey: the outer skin across the row (its reveals in pass 3), on the plinth
  for (let x = west; x <= east; x++) c.set(x, 0, HOUSE.skin.z, P.plinth)

  // --- Pass 2: roofs and gables ------------------------------------------------------------------------------------
  for (const h of homes) {
    const xa = at(h, 0), xb = at(h, 5)
    const ends = { a0: isEnd(xa) ? 'eave' : 'gutter', a1: isEnd(xb) ? 'eave' : 'gutter' }
    const { under } = evenGableRoof(c, { axis: 'z', across: [xa, xb], along: HOUSE.roofAlong, eaveY: HOUSE.eaveY, ends }, P.roof)
    for (const z of [z0, z1]) gableWall(c, 'z', z, [xa + 1, xb - 1], HOUSE.wallTop + 1, under, () => wall)
  }

  // --- Pass 3: openings --------------------------------------------------------------------------------------------
  const d = OPENINGS.door
  skinWall(c, { x0: west, x1: east, z: HOUSE.skin.z, top: HOUSE.skin.top },
    { stone: wall, cap: P.weathering, lintel: P.lintel, snow: P.snow },
    { doors: homes.map(h => at(h, d.u)),
      windows: homes.map(h => [at(h, OPENINGS.frontRoom.us[0]), at(h, OPENINGS.frontRoom.us[1]), ...OPENINGS.frontRoom.ys]) })
  for (const h of homes) {
    const dx = at(h, d.u)
    door(c, [dx, d.y, d.z], 'south', 'north', { wood: P.door, step: [dx, 0, 0], stepStair: P.step, hinge: 'right' })
    c.set(dx, 3, HOUSE.skin.z, 'lantern', { hanging: 'true', waterlogged: 'false' }) // hung in the reveal, over the head
    for (const u of OPENINGS.frontRoom.us) for (const y of OPENINGS.frontRoom.ys) c.set(at(h, u), y, z0, P.glass)
    for (const w of OPENINGS.windows) {
      window(c, w.us.flatMap(u => w.ys.map(y => [at(h, u), y, w.z])), w.z === z0 ? 'north' : 'south', null, { glass: P.glass })
      if (w.lintel) for (const u of w.us) c.set(at(h, u), w.lintel, w.z, P.lintel)
    }
    for (const u of [0, 5]) {
      const x = at(h, u)
      if (!isEnd(x)) continue
      for (const ew of OPENINGS.endWindows)
        window(c, ew.ys.map(y => [x, y, ew.z]), x === west ? 'west' : 'east', 'shutters', { glass: P.glass, wood: P.wood })
    }
    const l = OPENINGS.ladder
    for (let y = l.y0; y <= l.y1; y++) c.set(at(h, l.u), y, l.z, 'ladder', { facing: l.facing, waterlogged: 'false' })
  }

  // --- Pass 4: depth and detail --------------------------------------------------------------------------------------
  stringCourse(c, west, z0, east, z1, HOUSE.band, P.band)
  quoins(c, [[west, z1], [east, z1]], 1, HOUSE.wallTop, P.quoin, P.quoinAlt)
  quoins(c, [[west, HOUSE.skin.z], [east, HOUSE.skin.z]], 1, HOUSE.skin.top, P.quoin, P.quoinAlt)
  // a pale pier on every party wall, front (over the skin) and back, under the gutter's end
  for (const x of xs) if (!isEnd(x)) {
    for (let y = HOUSE.piers.front[0]; y <= HOUSE.piers.front[1]; y++) c.set(x, y, 0, wall)
    c.set(x, 0, 9, P.plinth)
    for (let y = HOUSE.piers.back[0]; y <= HOUSE.piers.back[1]; y++) c.set(x, y, 9, wall)
  }

  // --- Pass 5: interior (the shipped furniture, cell for cell) ------------------------------------------------------
  for (const h of homes) {
    for (const f of FURNITURE) {
      const g = { ...f }
      if (f.pos) g.pos = [at(h, typeof f.pos[0] === 'string' ? h[f.pos[0]] : f.pos[0]), f.pos[1], f.pos[2]]
      if (f.colour === 'home') g.colour = f.kind === 'bed' ? h.beds : h.rug
      if (f.cells) g.cellsAbs = f.cells.map(([u, y, z]) => [at(h, u), y, z])
      furnitureParts[f.part](c, g)
    }
  }

  // --- Pass 6: snow, the verges, the stacks and the ground --------------------------------------------------------
  // round 3 (critic notes 1 and 3): every gable comes to a point. The ridge is one course at ridgeY (eaveY + 3, the old
  // ridge's lower course; nothing above it): on the gable rows (the rakes z 0 / 9 and the gable walls z 1 / 8) two stairs
  // back to back, so each gable ends in a peak closed by them over the calcite (which stops at ridgeY - 1); behind, z 2..7,
  // full blocks carrying the ridge's snow (a snow layer can't rest on a stair's sloped top in the game), set two rows back
  // so it reads as the ridge running away from the peak, not as a cap on it.
  const ridgeY = HOUSE.eaveY + 3
  const gableRow = z => z <= HOUSE.walls.z0 || z >= HOUSE.walls.z1
  for (const h of homes) for (const [u, rise] of [[2, 'east'], [3, 'west']]) {
    const x = at(h, u)
    for (let z = HOUSE.roofAlong[0]; z <= HOUSE.roofAlong[1]; z++) {
      c.clear(x, ridgeY + 1, z)
      if (z <= HOUSE.walls.z0 + 1 || z >= HOUSE.walls.z1 - 1) stair(c, x, ridgeY, z, P.roof.stairs, rise)
      else c.set(x, ridgeY, z, P.roof.full)
    }
  }
  const innerEave = (x, y) => (x === west || x === east) && y === HOUSE.eaveY // the stair over the wall: one snow line, on the lip
  // round 3 (critic note 2): at each row end the eave's overhang stays a stair on the rake rows and the gable-wall rows
  // (z 0..1, 8..9), so each verge runs down onto a stair foot and the lip's snow starts a block in from the gable (z 2..7)
  snowLoad(c, P.roof, P.snow, { eaves: true, ridge: '2', tread: () => false,
    eaveSkip: (x, y, z) => innerEave(x, y) || y === ridgeY || ((x === west - 1 || x === east + 1) && gableRow(z)) })
  for (const z of HOUSE.roofAlong) rakeVerge(c, P.roof, 'z', z, [0, c.size[0] - 1])
  for (const h of homes) {
    const S = HOUSE.stack
    for (const z of S.zs) ridgeStack(c, S.us.map(u => [at(h, u), z]), S.y0, S.y1, stack, { cap: P.capSlab })
  }
  const [rx, ry, rz] = OUTSIDE.rainBarrel
  c.set(rx, ry, rz, 'barrel', { facing: 'up', open: 'false' })
  snowSkirt(c, P.snow, [])
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = drawLevel(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
