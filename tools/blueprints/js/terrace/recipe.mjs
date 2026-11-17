// Terrace (ROADMAP 49), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out
// and structure, roofs and gables, openings, depth and detail, interior, ground) for LEVEL = 1 or 2, II the row of I
// with its third house on the east end. Writes furniture.json (the furniture spots). Build both: build.sh.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZES, PALETTE, HOUSE, HOMES, at, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, floor, wallPanel, window, door, evenGableRoof, crowGable, doorCanopy, furnitureParts, slab, quoins,
  brackets,
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
  const xs = [...lines.keys()]
  const west = Math.min(...xs), east = Math.max(...xs)
  const out = x => (x === west ? -1 : 1) // the side an end wall looks out to (x step)

  // --- Pass 1: block-out and structure -----------------------------------------------------------------------------
  for (const h of homes) {
    const xa = at(h, 0), xb = at(h, 5)
    for (let x = xa; x <= xb; x++) for (let z = z0; z <= z1; z++) {
      const wall = x === xa || x === xb || z === z0 || z === z1
      c.set(x, 0, z, wall ? plinthStone : P.floor)
    }
    wallPanel(c, [xa, z0], [xa, z1], 1, HOUSE.wallTop, P.brick)
    wallPanel(c, [xb, z0], [xb, z1], 1, HOUSE.wallTop, P.brick)
    wallPanel(c, [xa, z0], [xb, z0], 1, HOUSE.wallTop, P.brick)
    wallPanel(c, [xa, z1], [xb, z1], 1, HOUSE.wallTop, P.brick)
    // the belt course at the bedroom floor, front and back
    for (let x = xa + 1; x <= xb - 1; x++) for (const z of [z0, z1]) c.set(x, HOUSE.belt, z, P.dressed)
    // the bedroom floor, on two dark joists across the house
    floor(c, xa + 1, z0 + 1, xb - 1, z1 - 1, HOUSE.upperFloor, P.floor)
    for (const z of [3, 6]) for (let x = xa + 1; x <= xb - 1; x++) c.set(x, HOUSE.upperFloor, z, P.joist, { axis: 'x' })
  }
  // the end walls: the belt course carried round
  for (const x of xs) if (isEnd(x)) for (let z = z0; z <= z1; z++) c.set(x, HOUSE.belt, z, P.dressed)
  // piers: every wall line stands a block proud of the front and the back, plinth to wall top, a white cap; the row's
  // corners in long-and-short white quoins
  for (const x of xs) for (const z of HOUSE.piers) {
    c.set(x, 0, z, plinthStone)
    for (let y = 1; y <= HOUSE.wallTop; y++) c.set(x, y, z, P.brick)
    slab(c, x, HOUSE.wallTop + 1, z, P.coping, 'bottom')
  }
  quoins(c, [[west, 0], [west, 9], [east, 0], [east, 9]], 1, HOUSE.wallTop, P.dressed, P.brick)
  for (const x of [west, east]) for (const z of HOUSE.piers) c.set(x, HOUSE.belt, z, P.dressed)

  // --- Pass 2: roofs and gables --------------------------------------------------------------------------------------
  for (const h of homes) {
    const xa = at(h, 0), xb = at(h, 5)
    const ends = { a0: isEnd(xa) ? 'eave' : 'gutter', a1: isEnd(xb) ? 'eave' : 'gutter' }
    evenGableRoof(c, { axis: 'z', across: [xa, xb], along: HOUSE.roofAlong, eaveY: HOUSE.eaveY, ends }, P.roof)
    const tops = {}
    for (let u = 0; u <= 5; u++) tops[at(h, u)] = HOUSE.steps[u]
    // both gables crow-stepped parapets standing over the roof, a cornice where each begins, a window in each
    for (const [z, name] of [[z0, 'front gable'], [z1, 'back gable']]) {
      const gw = OPENINGS.windows.find(w => w.name === name)
      const gxs = gw.us.map(u => at(h, u))
      crowGable(c, 'z', z, [xa, xb], HOUSE.wallTop + 1, tops, P.brick, P.coping, (x, y) => {
        if (gxs.includes(x) && gw.ys.includes(y)) return P.glass
        if (gxs.includes(x) && y === gw.lintel) return P.dressed
        if (y === HOUSE.cornice && x !== xa && x !== xb) return P.dressed
        return undefined
      })
    }
    // the chimney: the back gable's apex capped in stone, the household's smoke on its flue
    const ch = HOUSE.chimney
    for (const u of ch.us) {
      c.set(at(h, u), ch.cap, z1, P.cap)
      if (u === ch.flue) c.set(at(h, u), ch.cap + 1, z1, 'campfire', { facing: 'south', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
      else slab(c, at(h, u), ch.cap + 1, z1, P.capSlab, 'bottom')
    }
  }
  // the end eaves: corbels under the overhang, and a kneeler step closing it behind each parapet
  for (const x of [west, east]) {
    const ox = x + out(x)
    for (const z of HOUSE.corbels) brackets(c, [[ox, HOUSE.eaveY - 2, z]], P.roof.stairs, out(x) < 0 ? 'east' : 'west')
    for (const z of [z0, z1]) {
      // two courses deep, so the eave's last stair and its corbel butt flush against it (critic, round 2, note 3)
      c.set(ox, HOUSE.eaveY - 1, z, P.brick)
      c.set(ox, HOUSE.eaveY - 2, z, P.brick)
      slab(c, ox, HOUSE.eaveY, z, P.coping, 'bottom')
      brackets(c, [[ox, HOUSE.eaveY - 3, z]], P.brickStairs, out(x) < 0 ? 'east' : 'west')
    }
  }

  // --- Pass 3: openings ----------------------------------------------------------------------------------------------
  for (const h of homes) {
    const d = OPENINGS.door
    const dx = at(h, d.u)
    door(c, [dx, d.y, d.z], 'south', 'north', { wood: P.door, step: [dx, 0, 0], stepStair: P.step, hinge: 'right' })
    c.set(dx, d.transom, d.z, P.glass)
    for (const w of OPENINGS.windows) {
      if (w.name.endsWith('gable')) continue
      const cells = w.us.flatMap(u => w.ys.map(y => [at(h, u), y, w.z]))
      window(c, cells, w.out, w.dress, { glass: P.glass, pots: OUTSIDE.pots[w.name] ?? [], sill: P.sill })
    }
    for (const u of [0, 5]) {
      const x = at(h, u)
      if (!isEnd(x)) continue
      for (const w of OPENINGS.endWindows)
        window(c, w.ys.map(y => [x, y, w.z]), x === west ? 'west' : 'east', 'shutters', { glass: P.glass, wood: P.wood })
    }
    const l = OPENINGS.ladder
    for (let y = l.y0; y <= l.y1; y++) c.set(at(h, l.u), y, l.z, 'ladder', { facing: l.facing, waterlogged: 'false' })
  }

  // --- Pass 4: depth and detail: the stoop, the door canopies --------------------------------------------------------
  for (const h of homes) {
    for (let u = 1; u <= 4; u++) if (!c.get(at(h, u), 0, 0)) c.set(at(h, u), 0, 0, P.stoop)
    doorCanopy(c, [at(h, OPENINGS.door.u), OPENINGS.door.y, OPENINGS.door.z], 'north', 'west', P.roof)
  }

  // --- Pass 5: interior ----------------------------------------------------------------------------------------------
  for (const h of homes) {
    for (const f of FURNITURE) {
      const g = { ...f }
      if (f.pos) g.pos = [at(h, typeof f.pos[0] === 'string' ? h[f.pos[0]] : f.pos[0]), f.pos[1], f.pos[2]]
      if (f.colour === 'home') g.colour = f.kind === 'bed' ? h.beds : h.rug
      if (f.cells) g.cellsAbs = f.cells.map(([u, y, z]) => [at(h, u), y, z])
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
