// The Stone Wall (`stone_wall`, Level II `stone_wall_2`), the recipe: assembles plan.mjs from the shared parts library
// (../parts) in passes (block-out and structure, the way up, depth and detail, ground, light) and returns the
// structure for the minecraft-builder skill's runner. LEVEL=1 (default) draws `stone_wall`; LEVEL=2 draws
// `stone_wall_2`: Level I drawn first and grown (every Level I block kept). Writes furniture.json beside it for
// rebuild.py (empty: the wall has no furniture). CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, KIT, MASSES, FURNITURE, LIGHTS } from './plan.mjs'
import { Canvas, mix, crenels, wallFlight, newel, hoarding, furnitureParts } from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level = 1) {
  const c = new Canvas(SIZE[level])
  const P = KIT, M = MASSES, W = M.curtain
  const plinth = mix(P.plinth, 11), ashlar = mix(P.ashlar, 12)

  // --- Pass 1: block-out and structure: the curtain (rows z 1..2), its walk and its parapet -----------------------
  for (let x = W.x0; x <= W.x1; x++) {
    for (const z of [W.inner, W.outer]) for (let y = 0; y < W.walkY; y++) c.set(x, y, z, y <= W.plinthTop ? plinth : ashlar)
    c.set(x, W.walkY, W.inner, P.course) // the walk's paving: seven stations
    c.set(x, W.walkY, W.outer, P.course) // the string course in the outer face, level with the walk
    c.set(x, W.parapetY, W.outer, ashlar) // the parapet, waist-high to a man on the walk
  }
  // the merlons (x 0, 2, 4, 6); the crenels between them, x 3 the ladder column's top
  crenels(c, [...Array(W.x1 + 1).keys()].map(x => [x, W.outer]), M.merlons.y, M.merlons.y, P.merlon, (i, x) => M.merlons.at(x))

  // --- Pass 2: the way up from the village side: the Flight and its Newel -----------------------------------------
  const F = M.flight
  wallFlight(c, F.x0, F.y0, F.z, F.steps, F.rise, P.steps, ashlar)
  const N = M.newel
  newel(c, N.x, N.z, N.y0, N.y1, plinth, P.newelPost)

  if (level >= 2) {
    // --- Level II, the Hoarded Wall: the timber gallery over the outer foot, its murder holes, the pent roof --------
    hoarding(c, M.hoarding, P.wood)
  }
  // --- Pass 5: light ----------------------------------------------------------------------------------------------
  for (const p of LIGHTS[level]) c.set(...p, 'lantern', { hanging: 'true', waterlogged: 'false' })
  for (const f of FURNITURE[level]) furnitureParts[f.part](c, f)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = level === 1 ? 'stone_wall' : `stone_wall_${level}`
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
