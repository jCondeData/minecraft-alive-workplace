// Waystone Plaza, the recipe: assembles plan.mjs (the Wayshrine) from the shared parts library (../parts) in passes
// (paving, collar and posts, roof, the stone, benches, beds, light) for one culture look and returns the
// structure for the minecraft-builder skill's runner. Also writes furniture.json (empty: a civic piece keeps no spots).
// LOOK=<plains|desert|savanna|snowy|taiga> (default plains); CUT=<y> builds a cutaway with everything above y removed.
// Build every look: see package.json ("npm run build").
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, CENTER, STONE, MASSES, LOOKS } from './plan.mjs'
import { Canvas, mix, slab, plantFlower, hangingLantern, squareRing, inwardFacing, hipCourse, armedBench } from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(look = 'plains') {
  const P = LOOKS[look]
  if (!P) throw new Error(`no look ${look}`)
  const c = new Canvas(SIZE)
  const M = MASSES
  const [cx, cz] = CENTER
  const field = mix(P.field, 7), walk = mix(P.walk, 3), edgeSlab = mix(P.edgeSlab, 5)
  const key = ([x, z]) => `${x},${z}`
  const benchCells = new Set(M.benches.flatMap(b => [...b.cells, ...b.arms]).map(key))
  const beds = new Set(M.beds.map(key))
  const path = new Set(M.path.map(key))

  // --- Pass 1: paving (y 0): the edge a slab step, full under the benches; the walk ring; the front path; bed soil --
  for (const [x, z] of squareRing(cx, cz, 3)) {
    if (beds.has(key([x, z]))) c.set(x, 0, z, P.soil, P.soil === 'grass_block' ? { snowy: 'false' } : {})
    else if (path.has(key([x, z]))) slab(c, x, 0, z, P.path[0])
    else if (benchCells.has(key([x, z]))) c.set(x, 0, z, field)
    else slab(c, x, 0, z, edgeSlab)
  }
  for (const [x, z] of squareRing(cx, cz, 2)) c.set(x, 0, z, path.has(key([x, z])) ? P.path[1] : walk)
  for (let r = 0; r <= 1; r++) for (const [x, z] of squareRing(cx, cz, r)) c.set(x, 0, z, field)

  // --- Pass 2: the collar (y 1): stairs climbing to the stone's footing; the posts on their stone footings ---------
  c.set(cx, 1, cz, P.footing)
  for (const [x, z] of squareRing(cx, cz, 1))
    c.set(x, 1, z, P.collar, { facing: inwardFacing(x, z, cx, cz), half: 'bottom', shape: 'straight', waterlogged: 'false' })
  for (const [x, z] of M.posts) {
    c.set(x, M.footingY, z, P.postFooting)
    for (let y = M.postY; y < M.upperY; y++) c.set(x, y, z, P.post, { axis: 'y' })
  }

  // --- Pass 3: the roof: two courses of a square hipped roof climbing inward, the eaves (y 3) over the edge and the
  //     upper course (y 4) over the walk, carried on the posts' heads, the 3 x 3 over the collar left open to the sky.
  //     Nothing stands at y 3 over the walk, so it keeps three blocks of headroom for anyone stepping on or off the
  //     collar. Desert: a flat sunshade, a top-slab fascia and a slab lid, its corners full blocks (a lantern can't hang
  //     from a top slab). -------------------------------------------------------------------------------------------
  if (P.roof.flat) {
    const corners = new Set(M.lanterns.map(([x, , z]) => `${x},${z}`))
    hipCourse(c, cx, cz, 3, M.eaveY, { flat: true, slab: P.roof.eave, type: 'top' }, (x, z) => corners.has(`${x},${z}`))
    for (const [x, , z] of M.lanterns) c.set(x, M.eaveY, z, P.roof.corner)
    hipCourse(c, cx, cz, 2, M.upperY, { flat: true, slab: P.roof.lid, type: 'bottom' })
  } else {
    // the hips (the four diagonals) in a darker stair, so the roof's shape reads from across the village
    const hips = (x, y, z) => (Math.abs(x - cx) === Math.abs(z - cz) && P.roof.hip ? P.roof.hip : P.roof.stairs)
    hipCourse(c, cx, cz, 3, M.eaveY, { stairs: hips })
    hipCourse(c, cx, cz, 2, M.upperY, { stairs: hips })
  }

  // --- Pass 4: the stone: the culture's waystone, both halves, facing the road ---------------------------------------
  const [sx, sy, sz] = STONE
  const ws = { facing: 'north', origin: 'village', waterlogged: 'false' }
  c.set(sx, sy, sz, P.stone, { ...ws, half: 'lower' })
  c.set(sx, sy + 1, sz, P.stone, { ...ws, half: 'upper' })

  // --- Pass 5: benches under the eaves, facing the stone ------------------------------------------------------------
  for (const b of M.benches) armedBench(c, b.cells, 1, b.facing, { seat: P.seat, arm: P.arm, cap: P.cap, arms: b.arms })

  // --- Pass 6: flowers in the corner beds; a lantern hanging under each roof corner ---------------------------------
  M.beds.forEach(([x, z], i) => plantFlower(c, x, 1, z, P.flowers[i % P.flowers.length]))
  for (const [x, y, z] of M.lanterns) hangingLantern(c, x, y, z)
  return c
}

export default function build(api) {
  const look = process.env.LOOK ?? 'plains'
  const c = draw(look)
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + c.spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `waystone_plaza_${look}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
