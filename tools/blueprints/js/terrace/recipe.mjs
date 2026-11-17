// Terrace (ROADMAP 49), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out
// and structure, roofs and gables, openings, depth and detail, interior, ground) for LEVEL = 1 or 2, II the row of I
// with its third house on the east end. Writes furniture.json (the furniture spots). Build both: build.sh.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZES, PALETTE, HOUSE, HOMES, at, face, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, floor, wallPanel, window, door, evenGableRoof, crowGable, doorCanopy, furnitureParts, slab,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function drawLevel(level) {
  const c = new Canvas(SIZES[level])
  const P = PALETTE
  const plinthStone = mix(P.plinth, 3)
  const homes = HOMES.filter(h => h.level <= level)
  const { z0, z1 } = HOUSE.walls
  // a wall line is a party wall when two homes stand on it, the row's end when one does
  const lines = new Map()
  for (const h of homes) for (const u of [0, 5]) lines.set(at(h, u), (lines.get(at(h, u)) ?? 0) + 1)
  const isEnd = x => lines.get(x) === 1
  const X = (h, u) => at(h, u)

  // --- Pass 1: block-out and structure -----------------------------------------------------------------------------
  for (const h of homes) {
    const xa = Math.min(X(h, 0), X(h, 5)), xb = Math.max(X(h, 0), X(h, 5))
    for (let x = xa; x <= xb; x++) for (let z = z0; z <= z1; z++) {
      const wall = x === xa || x === xb || z === z0 || z === z1
      c.set(x, 0, z, wall ? plinthStone : P.floor)
    }
    // walls: the side walls (party or end) and the front and back, brick from the plinth to the wall top
    wallPanel(c, [xa, z0], [xa, z1], 1, HOUSE.wallTop, P.brick)
    wallPanel(c, [xb, z0], [xb, z1], 1, HOUSE.wallTop, P.brick)
    wallPanel(c, [xa, z0], [xb, z0], 1, HOUSE.wallTop, P.brick)
    wallPanel(c, [xa, z1], [xb, z1], 1, HOUSE.wallTop, P.brick)
    // the bedroom floor, on two dark joists across the house
    floor(c, xa + 1, z0 + 1, xb - 1, z1 - 1, HOUSE.upperFloor, P.floor)
    for (const z of [3, 6]) for (let x = xa + 1; x <= xb - 1; x++) c.set(x, HOUSE.upperFloor, z, P.joist, { axis: 'x' })
  }
  // piers: every wall line stands a block proud of the front and the back, plinth to wall top; a white cap in front
  // (the back ones carry the roofs' overhang)
  for (const x of lines.keys()) for (const z of HOUSE.piers) {
    c.set(x, 0, z, plinthStone)
    for (let y = 1; y <= HOUSE.wallTop; y++) c.set(x, y, z, P.brick)
    if (z === 0) slab(c, x, HOUSE.wallTop + 1, z, P.coping, 'bottom')
  }

  // --- Pass 2: roofs, gables and stacks ------------------------------------------------------------------------------
  for (const h of homes) {
    const xa = Math.min(X(h, 0), X(h, 5)), xb = Math.max(X(h, 0), X(h, 5))
    const ends = { a0: isEnd(xa) ? 'eave' : 'gutter', a1: isEnd(xb) ? 'eave' : 'gutter' }
    const roof = evenGableRoof(c, { axis: 'z', across: [xa, xb], along: HOUSE.roofAlong, eaveY: HOUSE.eaveY, ends }, P.roof)
    // the front: a crow-stepped parapet gable standing over the roof, the gable window and its lintel in it
    const tops = {}
    for (let u = 0; u <= 5; u++) tops[X(h, u)] = HOUSE.steps[u]
    const gw = OPENINGS.windows.find(w => w.name === 'front gable')
    const gxs = gw.us.map(u => X(h, u))
    crowGable(c, 'z', z0, [xa, xb], HOUSE.wallTop + 1, tops, P.brick, P.coping, (x, y) => {
      if (gxs.includes(x) && gw.ys.includes(y)) return P.glass
      if (gxs.includes(x) && y === gw.lintel) return P.dressed
      if (y === HOUSE.cornice && x !== xa && x !== xb) return P.dressed
      return undefined
    })
    // the back gable, under the roof's overhang
    for (let x = xa + 1; x <= xb - 1; x++) for (let y = HOUSE.eaveY; y < roof.under[x]; y++) c.set(x, y, z1, P.brick)
  }
  // the stacks: one on the back of every wall line, a flue (and a smoke) per household it serves
  for (const [x, n] of lines) {
    for (const z of HOUSE.stack.z) for (let y = HOUSE.stack.y0; y <= HOUSE.stack.top; y++) c.set(x, y, z, P.brick)
    for (const z of HOUSE.stack.z) c.set(x, HOUSE.stack.top, z, P.stoop)
    const flues = n === 2 ? HOUSE.stack.z : [HOUSE.stack.z[1]]
    for (const z of flues) c.set(x, HOUSE.stack.top + 1, z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
    if (n === 1) slab(c, x, HOUSE.stack.top + 1, HOUSE.stack.z[0], P.coping, 'bottom')
  }

  // --- Pass 3: openings ----------------------------------------------------------------------------------------------
  for (const h of homes) {
    const d = OPENINGS.door
    const dx = X(h, d.u)
    door(c, [dx, d.y, d.z], 'south', 'north', { wood: h.door, step: [dx, 0, 0], stepStair: P.step, hinge: h.mirror ? 'left' : 'right' })
    c.set(dx, d.transom, d.z, P.glass)
    c.set(dx, d.transom + 1, d.z, P.dressed)
    for (const w of OPENINGS.windows) {
      if (w.name === 'front gable') continue
      const cells = w.us.flatMap(u => w.ys.map(y => [X(h, u), y, w.z]))
      window(c, cells, w.out, w.dress, { glass: P.glass, pots: OUTSIDE.pots[w.name] ?? [], sill: P.sill })
      if (w.lintel) for (const u of w.us) c.set(X(h, u), w.lintel, w.z, P.dressed)
    }
    // the end walls: a window on each floor
    for (const u of [0, 5]) {
      const x = X(h, u)
      if (!isEnd(x)) continue
      const out = x === Math.min(X(h, 0), X(h, 5)) ? 'west' : 'east'
      for (const w of OPENINGS.endWindows) {
        window(c, w.ys.map(y => [x, y, w.z]), out, 'shutters', { glass: P.glass, wood: P.wood })
        c.set(x, w.lintel, w.z, P.dressed)
      }
    }
    // the ladder up the back wall through the bedroom floor
    const l = OPENINGS.ladder
    for (let y = l.y0; y <= l.y1; y++) c.set(X(h, l.u), y, l.z, 'ladder', { facing: l.facing, waterlogged: 'false' })
  }

  // --- Pass 4: depth and detail: the stoop, the door canopies ------------------------------------------------------
  for (const h of homes) {
    for (let u = 1; u <= 4; u++) if (!c.get(X(h, u), 0, 0)) c.set(X(h, u), 0, 0, P.stoop)
    doorCanopy(c, [X(h, OPENINGS.door.u), OPENINGS.door.y, OPENINGS.door.z], 'north', face(h, 'west'), P.roof)
  }

  // --- Pass 5: interior ----------------------------------------------------------------------------------------------
  for (const h of homes) {
    for (const f of FURNITURE) {
      const u = typeof f.pos?.[0] === 'string' ? h[f.pos[0]] : f.pos?.[0]
      const g = { ...f }
      if (f.pos) g.pos = [X(h, u), f.pos[1], f.pos[2]]
      if (f.facing) g.facing = face(h, f.facing)
      if (f.colour === 'home') g.colour = f.kind === 'bed' ? h.beds : h.rug
      if (f.cells) g.cellsAbs = f.cells.map(([cu, y, z]) => [X(h, cu), y, z])
      furnitureParts[f.part](c, g)
    }
  }

  // --- Pass 6: ground ------------------------------------------------------------------------------------------------
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
