// Terrace, the taiga look (ROADMAP 49.10c): assembles plan.mjs from the parts library (../parts) in passes (block-out
// and structure, the roof, openings, depth and detail, interior, ground) for LEVEL = 1 or 2, II the range of I run on
// by a third home on the east end. Writes furniture.json (the furniture spots). Build both: build.sh.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, PALETTE, HOUSE, HOMES, at, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, floor, window, door, furnitureParts, pentPorch, woodpile, flowerBed, evenRoof45, ridgeStack, logEnds,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function drawLevel(level) {
  const c = new Canvas(SIZES[level])
  const P = PALETTE
  const footing = mix(P.footing, 5), stack = mix(P.stack, 11), firewood = mix(P.firewood, 3)
  const homes = HOMES.filter(h => h.level <= level)
  const { z0, z1 } = HOUSE.walls
  const lines = new Map()
  for (const h of homes) for (const u of [0, 5]) lines.set(at(h, u), (lines.get(at(h, u)) ?? 0) + 1)
  const isEnd = x => lines.get(x) === 1
  const xs = [...lines.keys()].sort((a, b) => a - b)
  const west = xs[0], east = xs[xs.length - 1]
  const R = HOUSE.roof

  // --- Pass 1: block-out and structure: the log pens on their fieldstone course -------------------------------------
  for (const h of homes) {
    const xa = at(h, 0), xb = at(h, 5)
    for (let x = xa; x <= xb; x++) for (let z = z0; z <= z1; z++) {
      const wallLine = x === xa || x === xb, endWall = z === z0 || z === z1
      if (!wallLine && !endWall) { c.set(x, 0, z, P.floor); continue }
      c.set(x, 0, z, footing)
      c.set(x, HOUSE.footing, z, footing)
      for (let y = HOUSE.footing + 1; y <= HOUSE.wallTop; y++) {
        // logs lie along their wall; a corner alternates course by course, the way a notched pen's logs cross
        // (a party wall meets the front and back as a butt joint: the long logs run on past it, no grid of ends)
        const corner = wallLine && endWall && isEnd(x)
        const axis = corner ? (y % 2 ? 'z' : 'x') : endWall ? 'x' : 'z'
        const band = y === HOUSE.band && endWall && !corner
        c.set(x, y, z, band ? P.band : P.log, { axis })
      }
    }
    floor(c, xa + 1, z0 + 1, xb - 1, z1 - 1, HOUSE.upperFloor, P.floor)
    for (const z of [3, 6]) for (let x = xa + 1; x <= xb - 1; x++) c.set(x, HOUSE.upperFloor, z, P.joist, { axis: 'x' })
  }

  // --- Pass 2: the roof: one 45-degree range, the gables and party walls in logs up to it, a stack on every wall line --
  const { under } = evenRoof45(c, { axis: 'x', across: R.across, along: [west - 1, east + 1], eaveY: R.eaveY }, P.roof)
  for (const x of xs) {
    for (let z = z0; z <= z1; z++) for (let y = HOUSE.wallTop + 1; y < under[z]; y++) c.set(x, y, z, P.log, { axis: 'z' })
    const S = HOUSE.stack
    ridgeStack(c, S.zs.map(z => [x, z]), S.y0, S.y1, stack, { cap: P.capSlab })
  }

  // --- Pass 3: openings --------------------------------------------------------------------------------------------
  const d = OPENINGS.door
  for (const h of homes) {
    const dx = at(h, d.u)
    door(c, [dx, d.y, d.z], 'south', 'north', { wood: P.door, hinge: 'right' })
    c.set(dx, d.transom, d.z, P.glass)
    for (const w of OPENINGS.windows) {
      const cells = w.us.flatMap(u => w.ys.map(y => [at(h, u), y, w.z]))
      window(c, cells, w.out, w.dress ?? null, { glass: P.glass, sill: P.sill, pots: w.dress ? [P.pot, P.pot] : [] })
    }
    for (const u of [0, 5]) {
      const x = at(h, u)
      if (!isEnd(x)) continue
      for (const ew of OPENINGS.endWindows) window(c, ew.ys.map(y => [x, y, ew.z]), x === west ? 'west' : 'east', null, { glass: P.glass })
    }
    const l = OPENINGS.ladder
    for (let y = l.y0; y <= l.y1; y++) c.set(at(h, l.u), y, l.z, 'ladder', { facing: l.facing, waterlogged: 'false' })
  }

  // --- Pass 4: depth and detail: the veranda, the log ends ----------------------------------------------------------
  const V = HOUSE.veranda
  for (let x = west; x <= east; x++) c.set(x, 0, V.z, P.deck)
  pentPorch(c, { x0: west - 1, x1: east + 1, z: V.z, y: V.y, toward: 'south', posts: xs, lamps: homes.map(h => at(h, 2)) },
    { roof: P.roof, post: P.post, bracket: P.bracket })
  const E = HOUSE.logEnds
  logEnds(c, [[west - 1, z0, 'x'], [west - 1, z1, 'x'], [east + 1, z0, 'x'], [east + 1, z1, 'x']], E.side[0], E.side[1], P.log)
  logEnds(c, [[west, z1 + 1, 'z'], [east, z1 + 1, 'z']], E.back[0], E.back[1], P.log) // the back corners only (on every party wall they read as a grid)
  for (const x of xs) c.set(x, E.front, V.z, P.log, { axis: 'z' })

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

  // --- Pass 6: ground ----------------------------------------------------------------------------------------------
  const [rx, ry, rz] = OUTSIDE.rainBarrel
  c.set(rx, ry, rz, 'barrel', { facing: 'up', open: 'false' })
  for (const x of [west - 1, east + 1]) woodpile(c, x, HOUSE.firewood[0], HOUSE.firewood[1], firewood)
  for (const h of homes) flowerBed(c, at(h, OUTSIDE.ferns[0]), at(h, OUTSIDE.ferns[1]), OUTSIDE.fernZ, [P.plant], P.soil)
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
