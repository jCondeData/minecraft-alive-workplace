// Village Hall (Version B, "the Moot House"), the recipe: assembles plan.mjs from the shared parts library (../parts) in
// passes (block-out and structure, openings, depth and detail, interior, ground and light) and returns the structure for
// the minecraft-builder skill's runner. LEVEL=1..3 picks the level (default 1): each level draws the one below first and
// then its own passes, so every level grows out of the last cell for cell. Also writes furniture.json (every furniture
// spot the parts recorded) for rebuild.py. CUT=<y> builds a cutaway; STANDIN=1 draws a vanilla block in the hall
// block's place for the renderer.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, IDS, PALETTE, OCT, BUTTRESSES, PILASTERS, PORCH, WING, CROWN, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, DIRS, OPP, mix, stair, slab, door, window, raisedBed, plantFlower, lampPost, gableRoof, bargeTrim, louvre, furnitureParts,
  stretchedLevel, stretchedInward, octRing, ringFace, stretchedTent, wallBench, hangingPot, bellPorch,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const plinthStone = mix(P.plinth, 3)
const wallStone = (x, y, z) => (y <= 1 ? mix(P.wallLow, 5) : mix(P.wall, 9))(x, y, z)
const { cx, cz, e, cut } = OCT
const level = stretchedLevel(cx, cz, e, cut)
const axisOf = face => (face === 'north' || face === 'south' ? 'x' : face === 'corner' ? 'y' : 'z')

const parts = {
  ...furnitureParts,
  hangingPot,
  bench(c, f) { wallBench(c, f.cells, 1, f.facing, `${f.wood}_stairs`, f.wood) },
  job(c, f) { // the Village Hall block: one, facing north, no block data
    const [x, y, z] = f.pos
    c.free(f.pos)
    if (process.env.STANDIN) c.set(x, y, z, P.standin)
    else c.set(x, y, z, P.job, { facing: f.facing })
  },
  standard(c, f) { // a standing banner in the village's colour, facing the door (rotation 8: north)
    const [x, y, z] = f.pos
    c.free(f.pos)
    c.set(x, y, z, `${P.cloth}_banner`, { rotation: String(f.rotation) })
  },
}
const furnish = (c, lv) => {
  for (const f of FURNITURE[lv]) {
    if (f.gone) { for (const [x, y, z] of f.gone) { c.clear(x, y, z); c.spots = c.spots.filter(s => !s.cells.some(q => q[0] === x && q[1] === y && q[2] === z)) } continue }
    parts[f.part](c, f)
  }
}
const windows = (c, lv) => {
  for (const w of OPENINGS.windows[lv]) window(c, w.cells, w.out, w.bare ? null : 'sill', { glass: P.glass, sill: P.sill })
}

// a window's dressing in the wall: a dressed lintel over it and, a block out, a hood mould (an upside-down stair) that
// throws a shadow over the glass as the sill under it does, so each window reads set into the stone
const dressWindow = (c, w, wallTop) => {
  const top = Math.max(...w.cells.map(p => p[1]))
  const [ox, oz] = DIRS[w.out]
  for (const [x, y, z] of w.cells) {
    if (y !== top || top + 1 > wallTop) continue
    c.set(x, top + 1, z, P.dressed)
    if (!c.get(x + ox, top + 1, z + oz)) stair(c, x + ox, top + 1, z + oz, P.sill, OPP[w.out], 'top')
  }
}

// --- Level I: the Moot ring and its bell porch -----------------------------------------------------------------------
function levelOne(c) {
  // Pass 1: block-out and structure. The plinth ring and its apron (ring 5), the chequer floor
  for (let x = 0; x <= 12; x++) for (let z = 2; z <= 12; z++) {
    const L = level(x, z)
    if (L <= 3) c.set(x, 0, z, (x + z) % 2 ? P.floorA : P.floorB)
    else if (L === 4) c.set(x, 0, z, plinthStone)
    else if (L === 5) c.set(x, 0, z, plinthStone) // the apron: the rubble plinth a block wider all round
  }
  // the walls (ring 4) y 1..5, a dark oak plate at y 6 following each face
  for (const [x, z] of octRing(cx, cz, e, cut, OCT.wall)) {
    for (let y = 1; y <= OCT.wallTop; y++) c.set(x, y, z, wallStone)
    c.set(x, OCT.plateY, z, P.timber, { axis: axisOf(ringFace(x, z, cx, cz, e, cut, OCT.wall)) })
  }
  // crossed tie beams at plate height: the lamps and pots hang from them
  for (let x = 2; x <= 10; x++) c.set(x, OCT.plateY, cz, P.timber, { axis: 'x' })
  for (let z = 4; z <= 10; z++) if (z !== cz) c.set(cx, OCT.plateY, z, P.timber, { axis: 'z' })
  // the tent roof, brick in alternate steep and shallow courses, the dark oak kick at the eave
  stretchedTent(c, { cx, cz, e, cut, heights: OCT.heights }, P.roof, { keep: (x, y, z) => !c.get(x, y, z) })
  // the lantern louvre on the ridge
  louvre(c, cx, cz, OCT.heights[0], { base: P.roof.full, post: `${P.wood}_fence`, roof: P.roof })
  // its own little cap: a brick crown block on the stair cap and a dark oak finial post
  c.set(cx, OCT.heights[0] + 3, cz, P.roof.full)
  c.set(cx, OCT.heights[0] + 4, cz, `${P.wood}_fence`)

  // the bell porch: its own floor of dressed stone, a step up from the street at the front between the posts
  for (let x = PORCH.x0; x <= PORCH.x1; x++) for (let z = PORCH.z0; z <= PORCH.z1; z++) {
    if (z === PORCH.z0 && x > PORCH.x0 && x < PORCH.x1) stair(c, x, 0, z, P.skirt, 'south')
    else c.set(x, 0, z, P.dressed)
  }
  // the tent roof's eave kick under the porch roof gives way to the porch
  for (let x = PORCH.x0; x <= PORCH.x1; x++) c.clear(x, OCT.heights[5], PORCH.z1)
  const porch = bellPorch(c, PORCH, { post: P.timber, roof: P.roof, brace: `${P.wood}_stairs`, low: true, keep: (x, y, z) => !c.get(x, y, z) })
  // its front gable over the beam: a king post and boards, barge boards under the rake
  for (let x = PORCH.x0 + 1; x <= PORCH.x1 - 1; x++) for (let y = PORCH.beamY + 1; y < porch.under[x]; y++)
    c.set(x, y, PORCH.z0, x === cx ? P.timber : `${P.wood}_planks`, x === cx ? { axis: 'y' } : {})
  // a boarded ceiling over the porch at beam height (y 5) (the lantern hangs from it), the little roof space above it boarded too
  for (let z = PORCH.z0 + 1; z <= PORCH.z1; z++) for (let x = PORCH.x0 + 1; x <= PORCH.x1 - 1; x++)
    for (let y = PORCH.beamY; y < porch.under[x]; y++) c.set(x, y, z, `${P.wood}_planks`)

  // Pass 2: openings
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, fd.out, { wood: P.wood })
  windows(c, 1)
  // dressed lintels over the windows and the door
  for (const w of OPENINGS.windows[1]) dressWindow(c, w, OCT.wallTop)
  c.set(fd.at[0], 3, fd.at[2], P.dressed)

  // Pass 3: depth and detail. Dressed stone up the octagon's eight angles (the vertices of ring 4)
  for (const [x, z] of octRing(cx, cz, e, cut, OCT.wall)) {
    if (ringFace(x, z, cx, cz, e, cut, OCT.wall) !== 'corner') continue
    for (let y = 1; y <= OCT.wallTop; y++) c.set(x, y, z, y % 2 ? P.dressed : wallStone(x, y, z))
  }

  // an angle buttress in the notch of each cut corner, its top sloped back to the wall
  for (const [x, z] of BUTTRESSES) {
    for (let y = 1; y <= 3; y++) c.set(x, y, z, wallStone)
    stair(c, x, 4, z, P.skirt, stretchedInward(x, z, cx, cz, 0))
  }

  // a pilaster a block proud of each angle, dressed stone up to the eave
  for (const [x, z] of PILASTERS) for (let y = 0; y <= OCT.wallTop; y++) {
    const got = c.name(x, y, z)
    if (y === 0 || got === 'minecraft:air' || got === 'minecraft:polished_andesite') c.set(x, y, z, P.pilaster)
  }

  // Pass 4: interior
  furnish(c, 1)

  // Pass 5: ground and light
  const posts = new Set(OUTSIDE.lampPosts.map(([x, z]) => `${x},${z}`))
  for (const b of OUTSIDE.flowerBeds) {
    const inside = raisedBed(c, b, { rim: plinthStone, cap: 'stone_brick_slab', soil: 'grass_block', skipCap: (x, z) => posts.has(`${x},${z}`) })
    inside.forEach(([x, z], i) => plantFlower(c, x, 1, z, b.plants[i % b.plants.length]))
  }
  for (const [x, z] of OUTSIDE.lampPosts) { c.set(x, 0, z, P.dressed); lampPost(c, x, 1, z, `${P.wood}_fence`, 1) }
}

// --- Level II: the Council wing on the east face -----------------------------------------------------------------------
function levelTwo(c) {
  const W = WING
  // Pass 1: block-out. The plinth and floor, the skirt round the three new sides
  for (let x = W.x0; x <= W.x1 + 1; x++) for (let z = W.z0 - 1; z <= W.z1 + 1; z++) {
    if (level(x, z) <= OCT.wall) continue // the octagon's own
    const inside = x < W.x1 && z > W.z0 && z < W.z1
    c.set(x, 0, z, inside ? P.wingFloor : plinthStone)
    if (inside) for (const y of [1, 2, 3, 4, 5, OCT.heights[5]]) if (level(x, z) === 5) c.clear(x, y, z) // the octagon's pilasters and eave kick, now inside the wing
  }
  // walls y 1..4 on north, south and east, dark oak posts in them, a beam ring at y 5
  for (let x = W.x0; x <= W.x1; x++) for (const z of [W.z0, W.z1]) if (level(x, z) > OCT.wall) for (let y = 1; y <= W.wallTop; y++) c.set(x, y, z, wallStone)
  for (let z = W.z0; z <= W.z1; z++) for (let y = 1; y <= W.wallTop; y++) c.set(W.x1, y, z, wallStone)
  for (const [x, z] of W.posts) for (let y = 1; y <= W.wallTop; y++) c.set(x, y, z, P.timber, { axis: 'y' })
  for (let x = W.x0; x <= W.x1; x++) for (const z of [W.z0, W.z1]) if (level(x, z) > OCT.wall) c.set(x, W.beamY, z, P.timber, { axis: x === W.x1 ? 'y' : 'x' })
  for (let z = W.z0 + 1; z < W.z1; z++) c.set(W.x1, W.beamY, z, P.timber, { axis: 'z' })
  for (const x of W.ties) for (let z = W.z0 + 1; z < W.z1; z++) c.set(x, W.beamY, z, P.timber, { axis: 'z' })
  // the octagon's eave kick on its east corners gives way to the wing's roof
  for (let x = W.x0; x <= W.x0 + 1; x++) for (let z = W.z0; z <= W.z1; z++) if (level(x, z) === 5) c.clear(x, OCT.heights[5], z)
  // the roof: a 45-degree brick gable, ridge along x, where the octagon's roof doesn't already stand
  const roof = gableRoof(c, W.roof, P.roof, (x, y, z) => !c.get(x, y, z))
  // the valley: where the wing's slope and the cone's stand level, the wing's stair runs through, so the two roofs
  // meet along one clean line instead of a stepped joint (finish() turns the corners)
  for (let x = W.x0; x <= W.x0 + 2; x++) for (let z = W.roof.across[0]; z <= W.roof.across[1]; z++) {
    const L = level(x, z)
    if (L > 5) continue
    const hw = W.roof.eaveY + Math.min(z - W.roof.across[0], W.roof.across[1] - z), ho = OCT.heights[L]
    if (hw === ho && z !== 7) stair(c, x, hw, z, P.roof.stairs, z < 7 ? 'south' : 'north')
  }
  // its east gable: stone with a window (the west end runs into the octagon's roof)
  for (let z = W.z0; z <= W.z1; z++) for (let y = W.beamY + 1; y < roof.under[z]; y++) c.set(W.x1, y, z, wallStone)
  // where the wing's ridge passes over the octagon's east face, a brick fills the wedge between the two roofs
  for (let z = W.z0; z <= W.z1; z++) for (let y = OCT.plateY + 1; y < roof.under[z]; y++)
    if (!c.get(W.x0 + 1, y, z) && level(W.x0 + 1, z) === OCT.wall) c.set(W.x0 + 1, y, z, P.roof.full)
  bargeTrim(c, 'x', W.roof.along[1], W.roof.across, roof.under, `${P.wood}_stairs`)

  // Pass 2: openings. The east window of the ring becomes the council doorway, a light kept over it
  const cd = OPENINGS.councilDoorway
  c.clear(cd.at[0] + 1, 1, cd.at[2]) // the old window's sill, now indoors
  for (let y = 1; y < 1 + cd.height; y++) c.clear(cd.at[0], y, cd.at[2])
  c.set(cd.at[0], 3, cd.at[2], P.dressed)
  windows(c, 2)
  for (const w of OPENINGS.windows[2]) if (!w.bare) dressWindow(c, w, WING.wallTop)

  // Pass 4: interior
  furnish(c, 2)
}

// --- Level III: the Crown --------------------------------------------------------------------------------------------
function levelThree(c) {
  const K = CROWN
  const ring = stretchedLevel(K.cx, K.cz, 0, K.cut)
  // Pass 1: the louvre and the roof's top inside the drum come away, opening the ring to the lantern; the drum's wall
  // stands on the roof course under it and rises to its top
  for (let x = K.cx - 2; x <= K.cx + 2; x++) for (let z = K.cz - 2; z <= K.cz + 2; z++) {
    const L = ring(x, z)
    if (L > K.ring) continue
    let y0 = OCT.plateY + 1
    while (y0 < K.top && !c.get(x, y0, z)) y0++
    for (let y = OCT.plateY + 1; y < c.size[1]; y++) {
      if (L === K.ring && y >= y0 && y <= K.top) c.set(x, y, z, wallStone)
      else c.clear(x, y, z)
    }
  }
  // the copper spire
  stretchedTent(c, { cx: K.cx, cz: K.cz, e: 0, cut: K.cut, heights: K.heights }, P.spire, { keep: (x, y, z) => !c.get(x, y, z) })
  c.set(K.cx, K.heights[0] + 1, K.cz, 'lightning_rod', { facing: 'up', powered: 'false', waterlogged: 'false' })

  // the belfry: a dark oak floor over the drum at y 11 (the corona hangs from it), an open arch in each face with a
  // dark oak sill, the head of the arch a lantern's hook
  for (let x = K.cx - 1; x <= K.cx + 1; x++) for (let z = K.cz - 1; z <= K.cz + 1; z++) {
    c.set(x, K.floorY, z, `${P.wood}_planks`)
    c.set(x, K.top, z, `${P.wood}_planks`) // its boarded ceiling under the spire
  }
  for (const [x, z, out] of K.arches) {
    for (let y = K.archY[0]; y <= K.archY[1] + 1; y++) c.clear(x, y, z) // the head (archY + 1) holds the lantern
    c.set(x, K.archY[0] - 1, z, `${P.wood}_planks`)
    const [ox, oz] = DIRS[out]
    stair(c, x + ox, K.archY[0] - 1, z + oz, `${P.wood}_stairs`, OPP[out], 'top') // the arch's sill, a block out
  }

  // Pass 2: openings
  windows(c, 3)
  // Pass 4: interior
  furnish(c, 3)
}

export function draw(lv = 1) {
  const c = new Canvas(SIZE[lv])
  levelOne(c)
  if (lv >= 2) levelTwo(c)
  if (lv >= 3) levelThree(c)
  return c
}

export default function build(api) {
  const lv = Number(process.env.LEVEL ?? 1)
  const id = IDS[lv]
  const c = draw(lv)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const name = id + (cut !== undefined ? `_cut${cut}` : '') + (process.env.STANDIN ? '_render' : '')
  return { structure: canvas.toStructure(api), metadata: { name } }
}
