// Terrace, savanna look (49.10c), the recipe: assembles plan.mjs from the parts library (../parts) in passes (block-out
// and structure, the hip, openings, depth and detail, interior, ground) for LEVEL = 1 or 2, II the row of I with its
// third house on the east end under the same hip made longer. Writes furniture.json. Build both: build.sh. CUT=<y>.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZES, PALETTE, HOUSE, HOMES, at, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, floor, window, door, furnitureParts, stair, post, hipRoof, chimney, paintedBand, stoop,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function drawLevel(level) {
  const c = new Canvas(SIZES[level])
  const P = PALETTE, H = HOUSE
  const granite = mix(P.granite, 5), plinth = mix(P.plinth, 3)
  const homes = HOMES.filter(h => h.level <= level)
  const { z0, z1 } = H.walls
  const lines = new Map()
  for (const h of homes) for (const u of [0, 5]) lines.set(at(h, u), (lines.get(at(h, u)) ?? 0) + 1)
  const isEnd = x => lines.get(x) === 1
  const xs = [...lines.keys()].sort((a, b) => a - b)
  const west = xs[0], east = xs[xs.length - 1]
  const uOf = (h, u) => (typeof u === 'string' ? h[u] : u)

  // --- Pass 1: block-out and structure: the plinth, the hip, the walls up to it, the frame -------------------------
  const under = hipRoof(c, { x0: west - 1, z0: H.hip.z0, x1: east + 1, z1: H.hip.z1, eaveY: H.hip.eaveY }, P.roof)
  for (const h of homes) {
    const xa = at(h, 0), xb = at(h, 5)
    for (let x = xa; x <= xb; x++) for (let z = z0; z <= z1; z++) {
      const wall = x === xa || x === xb || z === z0 || z === z1
      c.set(x, 0, z, wall ? plinth : P.floor)
      if (!wall) continue
      for (let y = 1; y < under(x, z); y++) {
        const id = y <= 4 ? granite : y === H.beamY ? P.frame : P.mud
        c.set(x, y, z, id, id === P.frame ? { axis: x === xa || x === xb ? 'z' : 'x' } : {})
      }
    }
    floor(c, xa + 1, z0 + 1, xb - 1, z1 - 1, H.upperFloor, P.upperFloor)
  }
  // grey acacia posts on every wall line's front and back corner, eave to plinth
  for (const x of xs) for (const z of [z0, z1]) post(c, x, z, 1, under(x, z) - 1, P.frame)

  // --- Pass 2: openings ----------------------------------------------------------------------------------------------
  for (const h of homes) {
    const d = OPENINGS.door, dx = at(h, d.u)
    door(c, [dx, d.y, d.z], 'south', 'north', { wood: P.door, step: [dx, 0, 0], stepStair: P.step, hinge: 'right' })
    c.set(dx, 3, d.z, P.frame, { axis: 'x' }) // an acacia lintel over the door
    for (const w of OPENINGS.windows) {
      const cells = w.us.flatMap(u => w.ys.map(y => [at(h, uOf(h, u)), y, w.z]))
      window(c, cells, w.out, w.dress, { glass: P.glass, wood: P.shutter })
    }
    for (const u of [0, 5]) {
      const x = at(h, u)
      if (!isEnd(x)) continue
      for (const w of OPENINGS.endWindows)
        window(c, w.ys.map(y => [x, y, w.z]), x === west ? 'west' : 'east', 'shutters', { glass: P.glass, wood: P.shutter })
    }
    const l = OPENINGS.ladder
    for (let y = l.y0; y <= l.y1; y++) c.set(at(h, l.u), y, l.z, 'ladder', { facing: l.facing, waterlogged: 'false' })
  }

  // --- Pass 3: depth and detail: the veranda, the painted fronts, the stacks ----------------------------------------
  const V = H.veranda
  for (const h of homes) for (const u of V.posts) post(c, at(h, u), V.z, 1, V.y1, P.frame)
  post(c, east, V.z, 1, V.y1, P.frame)
  for (let x = west - 1; x <= east + 1; x++) {
    const side = x < west ? 'east' : x > east ? 'west' : 'south' // the ends turned round the corner
    stair(c, x, V.eaveY, V.z, P.roof.stairs, side)
  }
  stoop(c, west, east, V.z, 0, P.deck)
  for (const h of homes) {
    const cells = H.paint.us.flatMap(u => H.paint.ys.map(y => [at(h, u), y, z0]))
    paintedBand(c, cells, [P.paint, `${h.beds}_terracotta`, `${h.beds}_terracotta`, P.paint])
    const S = H.stack, sx = at(h, uOf(h, S.u))
    chimney(c, sx, S.z, 0, S.top, P.mudBrick)
  }

  // --- Pass 4: interior: the shipped Terrace's furniture, cell for cell ----------------------------------------------
  for (const h of homes) {
    for (const f of FURNITURE) {
      const g = { ...f }
      if (f.pos) g.pos = [at(h, uOf(h, f.pos[0])), f.pos[1], f.pos[2]]
      if (f.colour === 'home') g.colour = f.kind === 'bed' ? h.beds : h.rug
      if (f.cells) g.cellsAbs = f.cells.map(([u, y, z]) => [at(h, u), y, z])
      if (f.part === 'hangingLamp' && f.pos[1] > H.upperFloor) g.chain = under(g.pos[0], g.pos[2]) - f.pos[1] - 1 // hung from the thatch
      furnitureParts[f.part](c, g)
    }
  }

  // --- Pass 5: ground: the veranda's life, the kept rain barrel ------------------------------------------------------
  for (const h of homes) {
    const L = OUTSIDE.doorLamp
    c.set(at(h, L.u), L.y, L.z, 'lantern', { hanging: 'true', waterlogged: 'false' })
    c.set(at(h, OUTSIDE.hay.u), 1, V.z, 'hay_block', { axis: 'x' })
    c.set(at(h, OUTSIDE.pot.u), 1, V.z, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
  }
  const [rx, ry, rz] = OUTSIDE.rainBarrel
  c.set(rx, ry, rz, 'barrel', { facing: 'up', open: 'false' })
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = drawLevel(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const name = level === 1 ? 'terrace' : `terrace_${level}`
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${name}_cut${cut}` : name } }
}
