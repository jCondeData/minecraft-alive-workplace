// Compost Yard (Levels I and II, "the Bin Barn"), the recipe: assembles plan.mjs from the shared parts library
// (../parts) in passes (block-out and structure, roof, openings, depth and detail, the bins and the work spot, ground and
// light) and returns the structure for the minecraft-builder skill's runner. LEVEL=1 or 2 picks the level; Level II is
// Level I drawn again with the riddling shed added, so every cell of I it keeps stays the same block.
// Also writes furniture.json (no spots: the brief asks for none). Build: ./build.sh (CUT=<y> for a cutaway).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { LEVEL, SIZE, PALETTE, MASSES, BINS, FIXTURES, OPENINGS, OUTSIDE } from './plan.mjs'
import {
  Canvas, fairMix, post, beamRing, wallPanel, gableFill, stair, trapdoor, steepUnionRoof, overhangTrim,
  compostBin, eaveBrackets, fenceLamp, hangingLantern, lampPost, flowerBed, shedRoof, door,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level = LEVEL) {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = fairMix(P.stone, 11)
  const B = MASSES.barn, A = MASSES.aisle, Hg = MASSES.hoist
  const { x0, x1, z0, z1 } = B.frame
  const log = (x, y, z, axis = 'y') => c.set(x, y, z, P.frame, { axis })
  const empty = (x, y, z) => !c.get(x, y, z)

  // --- Pass 1: block-out and structure ------------------------------------------------------------------------------
  // the rubble plinth ring round nave and aisle; inside, the bins' heaps (Pass 4) and the aisle's trodden floor
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= A.back; z++)
    if (x === x0 || x === x1 || z === z0 || z === A.back) c.set(x, 0, z, stone)
  for (let x = x0 + 1; x < x1; x++) for (let z = z1; z < A.back; z++) c.set(x, 0, z, P.path)
  // the nave: posts on the front and the arcade line (z 9, open to the aisle), mud walls, a beam ring at y 6
  for (const x of B.frontPosts) post(c, x, z0, 1, B.wallTop, P.frame)
  for (const x of B.arcadePosts) post(c, x, z1, 1, B.wallTop, P.frame)
  for (const xx of [x0, x1]) wallPanel(c, [xx, z0 + 1], [xx, z1 - 1], 1, B.wallTop, P.infill)
  for (const xx of [x0, x1]) for (let z = z0 + 1; z < z1; z++) log(xx, 3, z, 'z') // a mid rail across each end wall
  for (let x = x0 + 1; x < x1; x++) if (!B.frontPosts.includes(x)) wallPanel(c, [x, z0], [x, z0], 4, B.wallTop, P.infill) // over the bin mouths
  beamRing(c, x0, z0, x1, z1, B.beamY, P.frame)
  // the aisle: a lower back wall (z 11) on posts, its end walls carried up under the nave's eave (z 10)
  for (const x of A.posts) post(c, x, A.back, 1, A.wallTop, P.frame)
  for (let x = x0 + 1; x < x1; x++) if (!A.posts.includes(x)) wallPanel(c, [x, A.back], [x, A.back], 1, A.wallTop, P.infill)
  for (const xx of [x0, x1]) wallPanel(c, [xx, A.back - 1], [xx, A.back - 1], 1, B.wallTop, P.infill)
  // the plank dividers between the bins, as high as the heaps' faces
  for (const x of [4, 8]) for (let z = 6; z <= 8; z++) for (let y = 0; y <= 3; y++) c.set(x, y, z, `${P.wood}_planks`)
  // the hoist gable's jetty: a beam out on the overhang line (z 4) over the bins, on brackets off the bin posts
  for (let x = Hg.face.x0; x <= Hg.face.x1; x++) log(x, B.beamY, Hg.face.z, 'x')
  eaveBrackets(c, 'x', Hg.face.z, Hg.brackets, B.beamY - 1, `${P.trim}_stairs`, 'south')

  // --- Pass 2: the roofs ----------------------------------------------------------------------------------------------
  // one steep thatch: the nave's ridge along x and the hoist gable's along z at the same pitch, eave and ridge, a T with
  // two clean valleys; a hay ridge on both. The jetty beam is drawn first and kept.
  const roof = steepUnionRoof(c, [B.roof, Hg.roof], P.roof, empty)
  // the aisle's own lean-to, lower and at 45 degrees, rising north to the nave's back eave: the back slope breaks on it
  for (const { z, y } of A.roof) for (let x = A.along[0]; x <= A.along[1]; x++) if (empty(x, y, z)) stair(c, x, y, z, P.roof.stairs, 'north')
  // the nave's end gables (x 1 and 11) and the hoist gable's face (z 4), mud round a king post
  const endGable = xx => gableFill(c, [5, 6, 7, 8, 9].map(z => [xx, z]), B.beamY + 1, roof.under, (x, y, z) =>
    z === 7 ? [P.frame, { axis: 'y' }] : P.infill)
  endGable(x0); endGable(x1)
  gableFill(c, [4, 5, 6, 7, 8].map(x => [x, Hg.face.z]), B.beamY + 1, roof.under, (x, y) =>
    x === 6 && y > 8 ? [P.frame, { axis: 'y' }] : P.infill)
  // the attic behind the hoist face: closed off at the nave's front wall line where the gable roof leaves a gap
  for (let x = 3; x <= 9; x++) for (let y = B.beamY + 1; y < (roof.under(x, z0) ?? 0); y++) if (empty(x, y, z0)) c.set(x, y, z0, P.infill)
  // barge boards under every overhang: the nave's rakes (x 0, 12) and the hoist gable's front (z 3)
  const rakes = []
  for (let z = 4; z <= 10; z++) rakes.push([0, z], [12, z])
  for (let x = 3; x <= 9; x++) rakes.push([x, 3])
  overhangTrim(c, rakes, roof.under, `${P.trim}_slab`)

  // --- Pass 3: openings ---------------------------------------------------------------------------------------------
  const ad = OPENINGS.aisleDoor
  door(c, ad.at, ad.facing, ad.out, { wood: P.wood, step: ad.step })
  for (const [x, y, z] of OPENINGS.loftDoors) trapdoor(c, x, y, z, `${P.wood}_trapdoor`, 'north', 'bottom', true)
  for (const [x, y, z] of OPENINGS.backVents) trapdoor(c, x, y, z, `${P.wood}_trapdoor`, 'south', 'bottom', true)

  // --- Pass 4: the bins, the aisle and the work spot ------------------------------------------------------------------
  for (const b of BINS) {
    const heap = fairMix(P[b.heap], 3 + b.x0)
    // each heap high at the mouth (its face over the board) and sloping down to the aisle, where it is turned
    for (const s of b.slope) compostBin(c, { x0: b.x0, x1: b.x1, z0: s.z, z1: s.z, y0: 0, y1: s.top }, heap, { wood: P.wood })
    for (let x = b.x0; x <= b.x1; x++) trapdoor(c, x, b.boardY, b.boardZ, `${P.wood}_trapdoor`, 'north', 'bottom', true)
  }
  for (const [x, y, z] of OUTSIDE.straw) c.set(x, y, z, 'hay_block', { axis: 'x' })
  hangingLantern(c, ...OUTSIDE.aisleLamp)
  const yd = OUTSIDE.yard
  const yardX1 = level === 1 ? yd.x1 : 13
  for (let x = yd.x0; x <= yardX1; x++) for (let z = yd.z0; z <= yd.z1; z++) c.set(x, 0, z, P.yard)
  for (const [x, z] of [...OUTSIDE.path, ...(level >= 2 ? OUTSIDE.shedPath : [])]) c.set(x, 0, z, P.path)
  for (const [x, y, z] of OPENINGS.steps) stair(c, x, y, z, 'cobblestone_stairs', 'south')
  for (const f of FIXTURES) {
    if (f.level > level) continue
    c.free(f.pos)
    if (f.block === 'composter') c.set(...f.pos, 'composter', { level: '0' })
    else c.set(...f.pos, 'chest', { facing: f.facing, type: 'single', waterlogged: 'false' })
  }
  for (const s of OUTSIDE.scraps) c.set(...s.at, s.block, s.block === 'hay_block' ? { axis: 'y' } : {})

  // --- Pass 5: Level II, the riddling shed under the east gable -------------------------------------------------------
  if (level >= 2) {
    const S = MASSES.shed
    for (let x = 12; x <= 13; x++) for (let z = S.z0 + 1; z <= S.z1 - 1; z++) c.set(x, 0, z, P.yard) // its floor
    for (const z of S.posts) post(c, S.postX, z, 0, 2, P.frame)
    // a ledger beam on the barn wall, the pent's rafters resting on it and on the posts' heads
    for (let z = S.z0 + 2; z <= S.z1 - 2; z++) if (empty(12, 4, z)) log(12, 4, z, 'z')
    shedRoof(c, { ...S.roof, z0: S.z0, z1: S.z1 }, P.roof.stairs, empty)
    // a hurdle between the back posts closes the shed's back corner
    for (let z = 8; z <= 11; z++) { c.set(S.postX, 0, z, stone); c.set(S.postX, 1, z, `${P.wood}_fence`) }
    // against the barn wall: hay (browns) by the first chest, the riddled fine pile, the potting bench
    c.set(12, 1, 6, 'hay_block', { axis: 'z' })
    c.set(12, 1, 7, 'podzol', { snowy: 'false' }) // the riddled fine compost (old Level II's crafting-table cell)
    c.set(12, 2, 7, 'brown_mushroom') // gone to mushrooms, as fine compost does in the shade
    // a bed of finished compost along the shed's open side, its first crop of ferns and lilies of the valley (no
    // saplings in the ground: they would grow up through the pent; the seedlings stand potted on the bench)
    for (let z = 5; z <= 7; z++) { c.set(S.postX, 0, z, 'podzol', { snowy: 'false' }); c.set(S.postX, 1, z, ['fern', 'lily_of_the_valley', 'fern'][z - 5]) }
    for (const z of [10, 11]) stair(c, 12, 1, z, `${P.wood}_stairs`, 'west', 'top')
    c.set(12, 2, 10, 'potted_oak_sapling'); c.set(12, 2, 11, 'potted_birch_sapling')
    hangingLantern(c, 13, 3, 8) // under the pent, mid-shed
  }

  // --- Pass 6: ground and light ---------------------------------------------------------------------------------------
  for (const [x, y, z] of OUTSIDE.lampPosts) lampPost(c, x, y, z, `${P.wood}_fence`, 2)
  fenceLamp(c, OUTSIDE.hoistLamp, `${P.trim}_fence`)
  for (const [x, y, z] of OUTSIDE.jettyLamps) hangingLantern(c, x, y, z, 'lantern', 1)
  for (const b of OUTSIDE.beds) flowerBed(c, b.x0, b.x1, b.z, b.plants, 'podzol')
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + c.spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const id = LEVEL === 1 ? 'compost_yard' : `compost_yard_${LEVEL}`
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
