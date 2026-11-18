// The Kitchen (the Lantern Kitchen, ROADMAP 49), the recipe: assembles plan.mjs from the shared parts library
// (../parts) in passes (block-out and structure, roofs, openings, depth and detail, interior, cold larder, ground and
// light) and returns the structure for the minecraft-builder skill's runner. Also writes furniture.json (every
// furniture spot the parts recorded) and out/cellar.txt (the larder's dug-out inside) for rebuild.py.
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed; ABOVE=1 drops
// the cellar (renders only).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, PRODUCE, OUTSIDE } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, plinth, floor, post, beamRing, wallPanel, window, door, doorway, gableRoof, gableWall,
  bargeTrim, chimney, furnaceMouth, furnitureParts, cellar, hatchLadder, cornerPiers, railing, flowerBed, doubleChest,
  ledgeRing, louvredHip, hipRolls, tieBeams, pilasters, produce, skirtRing,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new LiftedCanvas(SIZE, BASEMENT)
  const P = PALETTE
  const walls = P.walls, base = P.base, larderWall = P.larderWall
  const K = MASSES.kitchen, Po = MASSES.porch, W = MASSES.wing, H = MASSES.hearth, L = MASSES.larder
  const kw = K.walls, kr = K.room, ring = K.ring
  const [cx, cz] = K.centre

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // The great kitchen: a dark apron a block out all round, a brick floor inside, stone walls on a dark first course
  plinth(c, ring.x0, ring.z0, ring.x1, ring.z1, base)
  floor(c, kr.x0, kr.z0, kr.x1, kr.z1, 0, P.floor)
  for (let y = 1; y <= K.wallTop; y++) {
    const id = y === 1 ? base : walls
    wallPanel(c, [kw.x0, kw.z0], [kw.x1, kw.z0], y, y, id)
    wallPanel(c, [kw.x0, kw.z1], [kw.x1, kw.z1], y, y, id)
    wallPanel(c, [kw.x0, kw.z0], [kw.x0, kw.z1], y, y, id)
    wallPanel(c, [kw.x1, kw.z0], [kw.x1, kw.z1], y, y, id)
  }
  // Corner piers carry the eave's corners: 2 x 2, proud of both faces, the dark stone at their feet, the walls'
  // stone above, up to the corbel line
  cornerPiers(c, ring.x0, ring.z0, ring.x1, ring.z1, 0, 1, base, base, 2)
  cornerPiers(c, ring.x0, ring.z0, ring.x1, ring.z1, 2, K.pierTop, walls, walls, 2)

  // The Chef's Room: spruce posts and beams on the plinth against the kitchen's east wall, stone between the posts
  const ww = W.walls, wr = W.room
  plinth(c, ww.x0 + 1, ww.z0, ww.x1, ww.z1, base)
  floor(c, wr.x0, wr.z0, wr.x1, wr.z1, 0, P.wingFloor)
  wallPanel(c, [ww.x0 + 1, ww.z0], [ww.x1, ww.z0], 1, W.beamY - 1, P.infill)
  wallPanel(c, [ww.x0 + 1, ww.z1], [ww.x1, ww.z1], 1, W.beamY - 1, P.infill)
  wallPanel(c, [ww.x1, ww.z0], [ww.x1, ww.z1], 1, W.beamY - 1, P.infill)
  for (const [x, z] of W.posts) post(c, x, z, 1, W.beamY - 1, P.frame)
  beamRing(c, ww.x0 + 1, ww.z0, ww.x1, ww.z1, W.beamY, P.frame)

  // The porch: a deck before the door, two log posts on stone feet at its mouth, a beam across them, rails down its
  // sides back to the wall, and a boarded ceiling behind the beam
  const pd = Po.deck
  floor(c, pd.x0, pd.z0, pd.x1, pd.z1, 0, `${P.wood}_planks`)
  for (const [x, z] of Po.posts) { c.set(x, 0, z, base); post(c, x, z, 1, Po.beamY - 1, P.frame) }
  for (let x = pd.x0; x <= pd.x1; x++) c.set(x, Po.beamY, pd.z0, P.frame, { axis: 'x' })
  floor(c, pd.x0, pd.z0 + 1, pd.x1, pd.z1, Po.beamY, `${P.wood}_planks`)
  for (const x of [pd.x0, pd.x1]) {
    railing(c, Array.from({ length: pd.z1 - pd.z0 - 1 }, (_, i) => [x, 1, pd.z0 + 1 + i]), P.wood)
    for (let y = 1; y < Po.beamY; y++) c.set(x, y, pd.z1, walls) // stone cheeks where the porch meets the wall
  }

  // --- Pass 2: roofs (the small ones first, the great roof last, keeping theirs) ---------------------------------
  const wing = gableRoof(c, W.roof, P.roof)
  gableWall(c, 'x', ww.x1, [W.roof.across[0] + 1, W.roof.across[1] - 1], W.beamY + 1, wing.under, () => P.infill)
  bargeTrim(c, 'x', W.roof.along[1], W.roof.across, wing.under, `${P.wood}_stairs`)
  const porch = gableRoof(c, Po.roof, P.roof)
  gableWall(c, 'z', pd.z0, [pd.x0, pd.x1], Po.beamY + 1, porch.under, () => `${P.wood}_planks`)
  for (let z = pd.z0 + 1; z <= pd.z1; z++) c.set(Po.roof.across[0] + 2, Po.beamY + 1, z, `${P.wood}_planks`) // the porch roof's core
  // the great roof: a hip in brick tiles from the eave ring to the louvre that lets the smoke out
  // the back gablet first, so the great roof keeps out of it: a little stone gable breaking the back eave
  const G = MASSES.gablet
  const gab = gableRoof(c, G.roof, P.roof)
  gableWall(c, 'z', G.face, [G.roof.across[0] + 1, G.roof.across[1] - 1], G.roof.eaveY + 1, gab.under, () => walls)
  window(c, [G.window], 'south', null, { glass: P.glass })
  const underGablet = (x, z) => x > G.roof.across[0] && x < G.roof.across[1] && z > G.face // the eave breaks round it
  const great = louvredHip(c, cx, cz, K.half, K.eaveY, P.roof,
    { base: P.roof.full, post: `${P.wood}_fence`, cap: { full: P.trim, stairs: P.trimStairs }, finial: null, lamp: true },
    (x, y, z) => !c.get(x, y, z) && !underGablet(x, z))
  // the kicked eave: one more ring of tiles a block further out and a block lower, past the piers and pilasters, so
  // every side's eave throws a shadow (left off where the Chef's Room's roof runs in under it)
  skirtRing(c, cx, cz, K.half + 1, K.kickY, P.roof,
    (x, y, z) => !c.get(x, y, z) && !underGablet(x, z) && !(x === ring.x1 + 1 && z >= W.roof.across[0] && z <= W.roof.across[1]))
  // the four hips as clean rolls of dark stone blocks from the kicked eave's corners up to the louvre
  hipRolls(c, cx, cz, K.half, K.eaveY, 2, P.trim, [[K.half + 1, K.kickY]])
  // inside, the hip's thrust tied by two crossing beams that the lamps hang from
  tieBeams(c, cx, cz, [kr.x0, kr.x1], [kr.z0, kr.z1], K.tieY, P.frame)

  // --- Pass 3: openings ---------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, fd.out, { wood: P.wood, step: fd.step, stepStair: `${P.wood}_stairs` })
  doorway(c, OPENINGS.wingDoorway.at, OPENINGS.wingDoorway.height)
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress ?? null, { glass: P.glass, wood: P.wood })

  // --- Pass 4: depth and detail ---------------------------------------------------------------------------------------
  // the battered skirt at the walls' foot, a block out, wherever the porch, the wing and the piers leave the wall bare;
  // pilasters break the long west wall into bays
  pilasters(c, K.pilasters, 2, K.pierTop - 1, walls)
  const offWing = (x, z) => !(x === ring.x1 && z >= ww.z0 && z <= ww.z1)
  ledgeRing(c, ring.x0, ring.z0, ring.x1, ring.z1, 1, P.skirt, 'bottom', (x, z) => offWing(x, z) && !(z === ring.z0 && x >= pd.x0 && x <= pd.x1))
  // the hearth: the smoker in a brick breast in the back wall under a hood, the chimney up the back past the roof
  furnaceMouth(c, H.smoker, 'north', 'smoker', { brick: P.hearth, hoodStairs: P.hearthStairs, top: H.breastTop })
  chimney(c, H.stack[0], H.stack[1], 0, H.stackTop, walls, { breast: { axis: 'x', top: 4, stair: P.trimStairs } })
  c.set(H.stack[0], H.stackTop, H.stack[1], P.trim) // the stack's dark cap under the smoke
  for (const x of [H.stack[0] - 1, H.stack[0] + 1]) c.set(x, 0, H.stack[1], base) // the breast stands on the apron

  // --- Pass 5: interior (the plan's furniture) ----------------------------------------------------------------------
  for (const f of FURNITURE) {
    if (f.part === 'doubleChest') doubleChest(c, f.pos, f.facing, f.along)
    else if (f.part === 'plainBed') { // a vanilla bed, drawn without a furniture spot
      const n = c.spots.length
      furnitureParts.bed(c, f)
      c.spots.length = n
    }
    else furnitureParts[f.part](c, f)
  }

  // --- Pass 6: the cold larder (the cellar) and its way down ---------------------------------------------------------
  const lw = L.walls
  const dug = cellar(c, lw.x0, lw.z0, lw.x1, lw.z1, L.depth, larderWall, P.larderFloor)
  post(c, L.post[0], L.post[1], -L.depth + 1, -1, P.frame) // a post under the kitchen floor's middle
  const hatch = OPENINGS.hatch
  hatchLadder(c, hatch.at[0], hatch.at[1], hatch.ladderFrom, 0, hatch.facing, P.wood)
  produce(c, PRODUCE)

  // --- Pass 7: ground and light -------------------------------------------------------------------------------------
  for (const b of OUTSIDE.herbs) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  for (const [x, y, z] of Po.lamps) c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  return { c, dug, great }
}

export default function build(api) {
  const { c, dug } = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  fs.mkdirSync(path.join(here, 'out'), { recursive: true })
  fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(','))
  const cut = process.env.CUT
  let canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  if (process.env.ABOVE) { // renders only: the build above the ground (the cellar hangs under a render's grass)
    const a = new Canvas(SIZE)
    for (const [k, v] of canvas.cells) { const [x, y, z] = k.split(',').map(Number); if (y >= BASEMENT) a.cells.set(`${x},${y - BASEMENT},${z}`, v) }
    canvas = a
  }
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `kitchen_cut${cut}` : 'kitchen' } }
}
