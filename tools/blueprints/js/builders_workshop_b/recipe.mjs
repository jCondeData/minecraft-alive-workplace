// Builder's Workshop (Version B, the Hoist House), the recipe: assembles plan.mjs from the shared parts library
// (../parts) in passes (block-out and structure, openings, depth and detail, interior, ground, light) and returns the
// structure for the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts
// recorded) for tools/blueprints/rebuild.py.
// Build: see package.json; CUT=<y> builds a cutaway with everything above y removed. STANDIN=1 draws the Blueprint
// Table as a cartography table for the headless renders only (the renderer has no mod textures); never save that one.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, SHIFT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, post, beamRing, wallPanel, window, door, gableWall, catslideRoof, chimney, flowerBed,
  woodpile, lampPost, quoins, furnitureParts, catslideTrim, slopeDormer, jettyBrackets, hoist, timberRack, ShiftedCanvas, stockShelf, mantel, railingRun,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new ShiftedCanvas(SIZE, SHIFT) // drawn in plan coordinates (the village contract's), saved one block east
  const P = PALETTE
  const stone = mix(P.stone, 11)
  const W = MASSES.workshop, G = W.ground, U = W.upper, S = MASSES.store, R = MASSES.roof

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // Workshop ground storey: rubble walls on a rubble plinth, dressed quoins, a spruce floor
  plinth(c, G.x0, G.z0, G.x1, G.z1, stone)
  floor(c, W.room.x0, W.room.z0, W.room.x1, W.room.z1, 0, P.floor)
  wallPanel(c, [G.x0, G.z0], [G.x1, G.z0], G.y0, G.y1, stone)
  wallPanel(c, [G.x0, G.z1], [G.x1, G.z1], G.y0, G.y1, stone)
  wallPanel(c, [G.x0, G.z0], [G.x0, G.z1], G.y0, G.y1, stone)
  wallPanel(c, [G.x1, G.z0], [G.x1, G.z1], G.y0, G.y1, stone)
  quoins(c, [[G.x0, G.z0], [G.x1, G.z0], [G.x0, G.z1], [G.x1, G.z1]], G.y0, G.y1, P.dressing, P.dressingAlt)

  // the floor beam ring (the jetty's sill at the front), the loft floor inside it
  beamRing(c, U.x0, U.z0, U.x1, U.z1, W.floorY, P.frame)
  floor(c, W.upRoom.x0, W.upRoom.z0, W.upRoom.x1, W.upRoom.z1, W.floorY, P.floor)

  // the Jetty: brick nogging set between dark oak posts, a plate on top
  wallPanel(c, [U.x0, U.z0], [U.x1, U.z0], U.y0, U.y1, P.infill)
  wallPanel(c, [U.x0, U.z1], [U.x1, U.z1], U.y0, U.y1, P.infill)
  wallPanel(c, [U.x0, U.z0], [U.x0, U.z1], U.y0, U.y1, P.infill)
  wallPanel(c, [U.x1, U.z0], [U.x1, U.z1], U.y0, U.y1, P.infill)
  for (const x of W.frontPosts) { post(c, x, U.z0, U.y0, U.y1, P.frame); post(c, x, U.z1, U.y0, U.y1, P.frame) }
  // the side walls run up to the eaves (y 9) in brick; only the gable ends get a tie beam at y 9 (the gable's sill)
  wallPanel(c, [U.x0, U.z0 + 1], [U.x0, U.z1 - 1], U.plate, U.plate, P.infill)
  wallPanel(c, [U.x1, U.z0 + 1], [U.x1, U.z1 - 1], U.plate, U.plate, P.infill)
  for (const z of W.sidePosts) post(c, U.x1, z, U.y0, U.plate, P.frame) // the east side's posts, in its wall
  for (const z of [U.z0, U.z1]) post(c, U.x0, z, U.y0, U.plate, P.frame) // the west corners
  // the west jetty: a beam a block out on brackets, posts standing on it proud of the brick (under the eave)
  const Wj = W.west
  for (let z = U.z0; z <= U.z1; z++) c.set(Wj.x, W.floorY, z, P.frame, { axis: 'z' })
  jettyBrackets(c, Wj.brackets.map(z => [Wj.x, W.floorY - 1, z]), `${P.trim}_stairs`, 'east')
  // (the posts go in after the roof, which decides how tall they stand)
  for (let x = U.x0 + 1; x < U.x1; x++) for (const z of [U.z0, U.z1]) c.set(x, U.plate, z, P.frame, { axis: 'x' })

  // the Store: plank walls on dark oak posts, a stone floor
  plinth(c, S.x0, S.z0, S.x1, S.z1, stone)
  floor(c, S.room.x0, S.room.z0, S.room.x1, S.room.z1, 0, P.storeFloor)

  // the Roof: one catslide, the short west slope over the Workshop, the long east one down over the Store
  const roof = catslideRoof(c, R, P.roof)
  const top = x => roof.under[x] - 1 // the highest wall cell under the roof in column x
  for (let x = S.x0; x <= S.x1; x++) for (const z of [S.z0, S.z1]) for (let y = 1; y <= top(x); y++) c.set(x, y, z, P.boards)
  wallPanel(c, [S.x1, S.z0], [S.x1, S.z1], 1, top(S.x1), P.boards)
  for (const z of [S.z0, S.z1]) post(c, S.x1, z, 1, top(S.x1), P.frame)
  // the east wall's frame: a mid-rail over the window, proud posts flanking its bay on stone footings
  for (let z = S.z0 + 1; z < S.z1; z++) c.set(S.x1, S.railY, z, P.frame, { axis: 'z' })
  for (const z of S.eastPosts) { c.set(S.x1 + 1, 0, z, stone); post(c, S.x1 + 1, z, 1, top(S.x1 + 1), P.frame) }
  // the proud posts of the west jetty and the back, each to just under the roof, the back's on brackets with a tie beam
  for (const z of Wj.posts) post(c, Wj.x, z, U.y0, top(Wj.x), P.frame)
  const Bk = W.back
  for (const x of Bk.posts) post(c, x, Bk.z, U.y0, top(x), P.frame)
  jettyBrackets(c, Bk.posts.map(x => [x, W.floorY, Bk.z]), `${P.trim}_stairs`, 'north')
  for (let x = Bk.posts[0] + 1; x < Bk.posts[1]; x++) if (x !== MASSES.chimney.x) c.set(x, U.plate, Bk.z, P.frame, { axis: 'x' })

  // Gables: the front one over the jetty (the hero: a king post and two little panes), the back one over the chimney
  const gable = line => gableWall(c, 'z', line, [U.x0 + 1, U.x1 - 1], U.plate + 1, roof.under, (u, y) => {
    if (u === R.ridge) return [P.frame, { axis: 'y' }] // the king post
    return P.infill
  })
  gable(U.z0)
  gable(U.z1)

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: 'stone_brick_stairs' })
  const sd = OPENINGS.storeDoorway
  for (let y = sd.at[1]; y < sd.at[1] + sd.height; y++) c.clear(sd.at[0], y, sd.at[2])
  c.set(sd.at[0], sd.at[1] + sd.height, sd.at[2], P.frame, { axis: 'z' }) // a timber lintel over it
  // the cart doors, a pair under a dark oak lintel
  const cd = OPENINGS.cartDoors
  cd.cells.forEach(([x, y, z], i) => door(c, [x, y, z], cd.facing, 'north', { wood: P.wood, hinge: i === 0 ? 'right' : 'left' }))
  for (let x = S.x0; x < S.x1; x++) c.set(x, S.lintelY, S.z0, P.frame, { axis: 'x' })
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress ?? null, { glass: P.glass, wood: P.wood })
  // stone lintels over the ground storey's windows and door (the mason's dressing)
  for (const [x, z] of [[2, 2], [4, 2], [6, 2], [2, 8]]) c.set(x, 4, z, P.dressing)
  for (const z of [4, 5]) c.set(1, 4, z, P.dressing)
  // the ladder up through the loft floor
  const L = OPENINGS.ladder
  for (let y = L.y0; y <= L.y1; y++) c.set(L.x, y, L.z, 'ladder', { facing: L.facing, waterlogged: 'false' })

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  jettyBrackets(c, W.brackets, `${P.trim}_stairs`, 'south')
  // barge boards under both rakes of the catslide, front and back
  catslideTrim(c, 'z', R.along[0], R.across, R.ridge, roof.under, `${P.trim}_stairs`, `${P.trim}_slab`)
  catslideTrim(c, 'z', R.along[1], R.across, R.ridge, roof.under, `${P.trim}_stairs`, `${P.trim}_slab`)
  // the Hoist: a beam out of the king post, past the barge boards, a chain and a lamp over the loading window
  hoist(c, [R.ridge, 11, U.z0 - 1], 'z', -1, 1, 2, P.frame)
  // and the Store's hoist over the cart doors, for the heavy stock
  const chh = OUTSIDE.cartHoist
  hoist(c, chh.at, 'z', -1, chh.reach, chh.drop, P.frame)
  // the Chimney: the hearth's stone round it in the back wall, the stack up past the ridge with a dressed cap
  const ch = MASSES.chimney
  wallPanel(c, [ch.x - 1, G.z1], [ch.x + 1, G.z1], 1, G.y1, stone)
  wallPanel(c, [ch.x, ch.flue], [ch.x, ch.flue], 1, R.ridgeY - 1, stone) // the flue, behind the back gable
  chimney(c, ch.x, ch.z, 0, ch.top, stone, { breast: { axis: 'x', top: G.y1, stair: 'cobblestone_stairs' } })
  c.set(ch.x, ch.top, ch.z, P.dressing)

  // a dormer on the long east slope over the Store: breaks the sweep of slate and lights the rack
  slopeDormer(c, MASSES.store.dormer, roof.under, P.roof, P.boards, P.glass)
  c.set(MASSES.store.dormer.face, MASSES.store.dormer.y0 - 1, MASSES.store.dormer.zc, P.frame, { axis: 'z' }) // its sill beam

  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  const parts = {
    ...furnitureParts,
    stockShelf,
    job(c, f) { // the Blueprint Table, the Builder's job block (one, and no other job block anywhere)
      const [x, y, z] = f.pos
      c.free(f.pos)
      if (process.env.STANDIN) c.set(x, y, z, 'cartography_table')
      else c.set(x, y, z, P.job, { facing: f.facing })
    },
  }
  for (const f of FURNITURE) parts[f.part](c, f)
  timberRack(c, OUTSIDE.rack, P.log, 'z')
  // the fireplace: a stone mantel and hood over the hearth fire
  mantel(c, MASSES.chimney.at ?? [MASSES.chimney.x, 1, G.z1], 'north', P.dressing, 'stone_brick_stairs', G.y1)

  // --- Pass 5: ground and light -------------------------------------------------------------------------------------
  const A = OUTSIDE.apron
  plinth(c, A.x0, A.z0, A.x1, A.z1, stone)
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  // the materials yard: a fenced pen east of the Store with a gate, stacks of stone inside
  railingRun(c, OUTSIDE.pen.fence, P.wood)
  const gt = OUTSIDE.pen.gate
  c.set(gt[0], gt[1], gt[2], `${P.wood}_fence_gate`, { facing: 'east', in_wall: 'false', open: 'false', powered: 'false' })
  for (const [x, y, z, id] of OUTSIDE.stacks) c.set(x, y, z, id)
  for (let x = 12; x <= 14; x++) for (let z = 2; z <= 8; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, 'coarse_dirt') // trodden ground
  lampPost(c, ...OUTSIDE.lampPost, `${P.wood}_fence`)
  c.set(OUTSIDE.lampPost[0], 0, OUTSIDE.lampPost[2], stone) // its footing
  woodpile(c, OUTSIDE.woodpile.x, OUTSIDE.woodpile.z0, OUTSIDE.woodpile.z1, P.log)
  c.set(fd.at[0], W.floorY - 1, U.z0, 'lantern', { hanging: 'true', waterlogged: 'false' }) // over the door, from the jetty
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const name = 'builders_workshop_b' + (cut !== undefined ? `_cut${cut}` : '') + (process.env.STANDIN ? '_render' : '')
  return { structure: canvas.toStructure(api), metadata: { name } }
}
