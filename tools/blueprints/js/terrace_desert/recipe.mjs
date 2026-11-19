// Terrace, desert look (49.10c), the recipe: assembles plan.mjs from the parts library (../parts) in passes (block-out
// and structure, roof and windcatchers, openings, depth and detail, interior, ground) for LEVEL = 1 or 2, II the row of
// I with its third house on the east end. Writes furniture.json. Build both: build.sh. CUT=<y>: a cutaway.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZES, PALETTE, HOUSE, HOMES, at, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, floor, wallPanel, window, door, furnitureParts, slab, stair, rawshan, windcatcher, rowPilasters, rowParapet,
  stoop,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function drawLevel(level) {
  const c = new Canvas(SIZES[level])
  const P = PALETTE, H = HOUSE
  const ashlar = mix(P.ashlar, 5), mud = mix(P.mud, 6)
  const homes = HOMES.filter(h => h.level <= level)
  const { z0, z1 } = H.walls
  const lines = new Map()
  for (const h of homes) for (const u of [0, 5]) lines.set(at(h, u), (lines.get(at(h, u)) ?? 0) + 1)
  const isEnd = x => lines.get(x) === 1
  const xs = [...lines.keys()].sort((a, b) => a - b)
  const west = xs[0], east = xs[xs.length - 1]
  const uOf = (h, u) => (typeof u === 'string' ? h[u] : u)

  // --- Pass 1: block-out and structure -----------------------------------------------------------------------------
  for (const h of homes) {
    const xa = at(h, 0), xb = at(h, 5)
    for (let x = xa; x <= xb; x++) for (let z = z0; z <= z1; z++) {
      const wall = x === xa || x === xb || z === z0 || z === z1
      c.set(x, 0, z, wall ? P.plinth : P.floor)
    }
    for (const [a, b] of [[[xa, z0], [xa, z1]], [[xb, z0], [xb, z1]], [[xa, z0], [xb, z0]], [[xa, z1], [xb, z1]]]) {
      wallPanel(c, a, b, 1, 4, ashlar)
      wallPanel(c, a, b, H.course, H.course, P.course)
      wallPanel(c, a, b, 6, H.deckY - 1, mud)
    }
    floor(c, xa + 1, z0 + 1, xb - 1, z1 - 1, H.upperFloor, P.upperFloor)
    for (const z of [3, 6]) for (let x = xa + 1; x <= xb - 1; x++) c.set(x, H.upperFloor, z, P.joist, { axis: 'x' })
  }
  // the rowPilasters: every wall line a block proud of the front and the back, up to the parapet, the coping over them
  for (const z of H.pilasterZ) rowPilasters(c, xs, z, 0, H.parapetY, P.pilaster, P.coping)

  // --- Pass 2: the roof: the deck, the parapet over the rim and every party wall, each household's windcatcher ----------
  const K = H.catcher
  const catcherAt = (x, z) => homes.some(h => x >= at(h, K.u0) && x <= at(h, K.u0 + 2) && z >= K.z0 && z <= K.z0 + 2)
  c.fill(west, H.deckY, z0, east, H.deckY, z1, P.deck)
  rowParapet(c, { x0: west, z0, x1: east, z1, y: H.parapetY, cross: xs.slice(1, -1) }, { wall: P.parapet, coping: P.coping }, catcherAt)
  for (const h of homes)
    windcatcher(c, at(h, K.u0), K.z0, K.y0, K.y1, { body: mix(P.catcher.body, 9), corner: P.catcher.corner, cap: P.catcher.cap, capTop: P.catcher.capTop })
  // beam ends under the deck: the back of every home, the end walls
  for (const h of homes) for (const u of H.beams.back) c.set(at(h, u), H.beams.y, z1 + 1, P.beam)
  for (const z of H.beams.ends) for (const x of [west - 1, east + 1]) c.set(x, H.beams.y, z, P.beam)

  // --- Pass 3: openings ----------------------------------------------------------------------------------------------
  for (const h of homes) {
    const d = OPENINGS.door, dx = at(h, d.u)
    door(c, [dx, d.y, d.z], 'south', 'north', { wood: P.door, step: [dx, 0, 0], stepStair: P.step, hinge: 'right' })
    c.set(dx, 3, d.z, P.carved) // a carved lintel over the door
    for (const w of OPENINGS.windows) {
      const cells = w.us.flatMap(u => w.ys.map(y => [at(h, uOf(h, u)), y, w.z]))
      window(c, cells, w.out, w.dress, { glass: P.glass, pots: OUTSIDE.pots[w.name] ?? [], sill: P.sill })
    }
    for (const u of [0, 5]) {
      const x = at(h, u)
      if (!isEnd(x)) continue
      for (const w of OPENINGS.endWindows)
        window(c, w.ys.map(y => [x, y, w.z]), x === west ? 'west' : 'east', w.dress, { glass: P.glass, sill: P.sill })
    }
    const l = OPENINGS.ladder
    for (let y = l.y0; y <= l.y1; y++) c.set(at(h, l.u), y, l.z, 'ladder', { facing: l.facing, waterlogged: 'false' })
  }

  // --- Pass 4: depth and detail: the rawshans, the stoop, a lamp at every door ----------------------------------------
  for (const h of homes) {
    const R = H.rawshan
    rawshan(c, { x0: at(h, R.u0), x1: at(h, R.u1), z: 0, y0: R.y0, y1: R.y1, posts: R.posts.map(u => at(h, u)) },
      { post: P.latticePost, lattice: P.lattice, sill: P.latticeSill, roof: P.latticeRoof, corbel: P.corbel })
    const L = OUTSIDE.doorLamp
    c.set(at(h, L.u), L.y, L.z, 'lantern', { hanging: 'false', waterlogged: 'false' })
  }
  stoop(c, west + 1, east - 1, 0, 0, P.stoop)

  // --- Pass 5: interior: the shipped Terrace's furniture, cell for cell ----------------------------------------------
  for (const h of homes) {
    for (const f of FURNITURE) {
      const g = { ...f }
      if (f.pos) g.pos = [at(h, uOf(h, f.pos[0])), f.pos[1], f.pos[2]]
      if (f.colour === 'home') g.colour = f.kind === 'bed' ? h.beds : h.rug
      if (f.cells) g.cellsAbs = f.cells.map(([u, y, z]) => [at(h, u), y, z])
      if (f.part === 'hangingLamp' && f.pos[1] > H.upperFloor) g.chain = H.deckY - f.pos[1] - 1 // hung from the deck
      furnitureParts[f.part](c, g)
    }
  }

  // --- Pass 6: ground: the kept rain barrel at the west end, water jars at the east -------------------------------------
  const [rx, ry, rz] = OUTSIDE.rainBarrel
  c.set(rx, ry, rz, 'barrel', { facing: 'up', open: 'false' })
  c.set(east + 1, 0, 1, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
  c.set(east + 1, 0, 2, 'decorated_pot', { facing: 'west', cracked: 'false', waterlogged: 'false' })
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
