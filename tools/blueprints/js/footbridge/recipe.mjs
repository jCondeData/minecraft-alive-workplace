// The Footbridge (`footbridge`), the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP 49)
// in passes (block-out and structure, the water, depth and detail, ground, light) and returns the structure for the
// minecraft-builder skill's runner. Writes furniture.json beside it for rebuild.py (no spots: a decoration).
// CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, LIGHTS } from './plan.mjs'
import { Canvas, mix, humpback, lampGate, stream, banks, lilyPads, boulders } from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, M = MASSES
  const rubble = mix(P.rubble, 11), lip = mix(P.lip, 13), boulder = mix(P.boulder, 17)

  // --- Pass 1: block-out and structure: the humpback over the stream's line, the lamp gate at its crown ------------
  const B = M.bridge
  humpback(c, B, {
    body: rubble, ring: P.ring, key: P.key, walk: P.walk, walkFull: P.walkFull,
    coping: P.coping, copingFull: P.copingFull, newel: P.newel, lamp: P.lamp,
  })
  lampGate(c, M.gate, { post: P.post, beam: P.beam, roof: P.hat, lamp: P.lamp })

  // --- Pass 2: the banks and the water: 15 sources in an S-bend, held in by a mossy lip ---------------------------
  const water = M.stream.rows.flatMap(([z, x0, x1]) => Array.from({ length: x1 - x0 + 1 }, (_, i) => [x0 + i, z]))
  if (water.length !== 15) throw new Error(`the stream holds ${water.length} sources, the brief asks for 15`)
  for (const [x, z] of water) c.clear(x, M.stream.y, z) // nothing of the bridge's stands in its water
  banks(c, { ...M.plot, y: M.stream.y }, water, { lip, soil: P.soil, soilProps: { snowy: 'false' } }, M.corners)
  stream(c, water, M.stream.y)
  for (const [x, z] of [[3, 2], [3, 3], [3, 4]]) // the brief's three pinned cells
    if (c.name(x, 0, z) !== 'minecraft:water') throw new Error(`(${x}, 0, ${z}) must be water`)

  // --- Pass 3: depth and detail: lily pads on the open water, a mossy boulder heap at the front east corner --------
  lilyPads(c, M.pads, M.stream.y + 1, P.pad)
  boulders(c, M.boulders.cells, boulder, M.boulders.caps, P.boulderCap)

  // --- Pass 4: ground: plants in clusters on the grass, a flowering azalea in each back corner -----------------------
  for (const [x, z, plant] of [...M.plants, ...M.shrubs]) {
    if (c.name(x, 0, z) !== `minecraft:${P.soil}`) throw new Error(`${plant} at ${x},${z} isn't on ${P.soil}`)
    c.set(x, 1, z, plant)
  }

  // --- Pass 5: light: the newels' lanterns came with the humpback, the gate's hangs over the crown ----------------
  if (c.name(...LIGHTS.gate) !== 'minecraft:lantern') throw new Error('the gate has no lantern')
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `footbridge_cut${cut}` : 'footbridge' } }
}
