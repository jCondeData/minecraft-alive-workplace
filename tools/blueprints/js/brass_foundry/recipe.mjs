// The Brass Foundry, the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP 49) in passes
// (block-out and structure, the machine and its water, openings, depth and detail, interior, ground) and returns the
// structure for the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts
// recorded) for tools/blueprints/rebuild.py. CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, MACHINE, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, stair, slab, trapdoor, plinth, floor, wallPanel, window, door, gableRoof, gableWall, hood,
  stringCourse, quoins, chimney, slopeDormer, masonButtress, kneeBrace, lampPost, pallet, furnitureParts, kitchenParts,
  beltRun, shaftRun, gearbox, cog, waterWheel, mixerStack, settledWater, roofLantern, bandedTank,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const k = ([x, y, z]) => `${x},${y},${z}`

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const brick = mix(P.brick, 11)
  const stone = mix(P.stone, 23)
  const H = MASSES.hall, L = MASSES.lantern, C = MASSES.cookhouse, R = MASSES.raceHouse, T = MASSES.tankTower, M = MACHINE
  const brass = P.brass

  // Cells no roof or wall may take: the wheel, its water, the cells kept clear round them
  const clear = new Set([M.wheel.at, ...M.water.map(w => w.at), ...M.keepClear].map(k))
  const inLantern = (x, z) => x >= L.x0 && x <= L.x1 && z >= L.z0 && z <= L.z1 + 1 // the hall's roof stops at it (and has no back eave behind it)

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // The back first: the race house (the wheel's seat and the sump, dressed stone where the water is) and the tank tower
  // beside it: brick, a stone cornice, a two-tone copper tank, a lightning rod
  plinth(c, R.x0, R.z0, T.x1, T.z1, stone)
  c.fill(R.x0, 1, R.z0, R.x1, R.top - 1, R.z1, brick)
  c.fill(R.x0, R.top, R.z0, R.x1, R.top, R.z1, P.dressed) // the race's stone curb round the sump
  c.fill(T.x0, 1, T.z0, T.x1, T.brickTop, T.z1, brick)
  for (const y of [4, 8]) stringCourse(c, T.x0, T.z0, T.x1, T.z1, y, P.dressed)
  c.fill(T.x0, T.cornice, T.z0, T.x1, T.cornice, T.z1, P.dressed)
  for (const key of clear) { const [x, y, z] = key.split(',').map(Number); c.clear(x, y, z) }
  bandedTank(c, T.x0, T.z0, T.x1, T.z1, T.tank[0], T.tank[1], { shell: P.tank, hoop: P.roof.full, hoops: T.hoops, cap: P.roof, parapet: P.roof.slab, finial: P.finial })
  // the tower's back and east faces: a lit slit window in a niche, a buttress
  for (const y of [6, 7]) { c.clear(11, y, T.z1 - 1); c.set(11, y, T.z1, P.glass) }
  masonButtress(c, T.x1 + 1, 14, 0, 6, stone, P.dressedStairs, 'west')
  // the smoker's flue: a brick stack outside the cookhouse's east wall, through the catslide's eave, smoking
  chimney(c, MASSES.flue.x, MASSES.flue.z, 0, MASSES.flue.top, brick)

  // The hall: a stone plinth, an andesite floor, brick walls to the eaves
  plinth(c, H.x0, H.z0, H.x1, H.z1, stone)
  floor(c, H.x0 + 1, H.z0 + 1, H.x1 - 1, H.z1 - 1, 0, P.hallFloor)
  wallPanel(c, [H.x0, H.z0], [H.x1, H.z0], 1, H.wallTop, brick)
  wallPanel(c, [H.x0, H.z1], [H.x1, H.z1], 1, H.wallTop, brick)
  wallPanel(c, [H.x0, H.z0], [H.x0, H.z1], 1, H.wallTop, brick)
  wallPanel(c, [H.x1, H.z0], [H.x1, H.z1], 1, H.wallTop, brick)

  // The cookhouse: a stone plinth, boards, brick walls under the catslide
  plinth(c, C.x0 + 1, C.z0, C.x1, C.z1, stone)
  floor(c, C.x0 + 1, C.z0 + 1, C.x1 - 1, C.z1 - 1, 0, P.cookFloor)
  const cookUnder = { 14: 7, 15: 6, 16: 5, 17: 4 }
  for (const z of [C.z0, C.z1]) for (let x = C.x0 + 1; x <= C.x1; x++) wallPanel(c, [x, z], [x, z], 1, cookUnder[x] - 1, brick)
  wallPanel(c, [C.x1, C.z0], [C.x1, C.z1], 1, C.wallTop, brick)

  // Roofs: the hall's copper gable (none inside the lantern's box, round the wheel or where the back is drawn), the
  // catslide sweeping its east slope down over the cookhouse, the lantern's own gable
  const main = gableRoof(c, H.roof, P.roof, (x, y, z) => !c.get(x, y, z) && !inLantern(x, z) && !clear.has(k([x, y, z])))
  for (let z = C.along[0]; z <= C.along[1]; z++)
    for (const [x, y] of C.slope) if (!c.get(x, y, z)) stair(c, x, y, z, P.roof.stairs, 'west')
  const lan = gableRoof(c, L.roof, P.roof, (x, y, z) => !c.get(x, y, z) && !clear.has(k([x, y, z])))

  // Gables: the hall's front and back in brick
  gableWall(c, 'z', H.z0, [H.x0, H.x1], H.wallTop + 1, main.under, () => brick)
  gableWall(c, 'z', H.z1, [H.x0, H.x1], H.wallTop + 1, main.under, () => brick)

  // The lantern: brass posts glazed between in full glass (real glazing, not pane rails), their feet in the roof's
  // copper so each slope keeps one tone, copper bands, brass ends with a window in the front one
  roofLantern(c, {
    x0: L.x0, x1: L.x1, z0: L.z0, z1: L.z1, base: L.base, top: L.top, posts: L.posts, glass: L.glass,
    endBase: x => main.under[x], under: lan.under,
    endGlass: [[7, 12], [7, 13], [7, 14], [7, 15], [8, 13], [8, 14], [8, 15], [9, 12], [9, 13], [9, 14], [9, 15]],
  }, { post: brass.block, postFoot: P.roof.full, band: P.roof.full, glassId: 'glass', wall: (x, y, z) => (z === L.z1 ? brick : P.roof.full) })

  c.set(8, main.under[8] - 1, L.z0, brass.block) // the glazed end's middle mullion, down to the hall roof's ridge

  // --- Pass 2: the machine and its water ---------------------------------------------------------------------------
  mixerStack(c, M.mixer)
  const ms = M.mixerShaft
  shaftRun(c, Array.from({ length: ms.y1 - ms.y0 + 1 }, (_, i) => [ms.x, ms.y0 + i, ms.z]), 'y')
  for (const at of M.smallCogs) cog(c, at, 'y')
  for (const at of M.largeCogs) cog(c, at, 'y', true)
  for (const g of M.gearboxes) gearbox(c, g.at, g.axis)
  for (const s of M.shafts) shaftRun(c, s.cells, s.axis)
  waterWheel(c, M.wheel.at, M.wheel.facing)
  beltRun(c, M.belt.cells, M.belt.facing)
  c.set(...M.depot, 'create:depot')
  settledWater(c, M.water)
  // the spout's lip: the poured source spreads sideways as well as down, so the cells south of it and of its fall are
  // closed, the wheel's top too (the compat build saw it run off the tower to the ground both ways)
  for (const [x, y, z] of [[9, 14, 14], [9, 13, 14], [8, 14, 13]]) c.set(x, y, z, P.tank) // (8, 14, 13): over the wheel

  // --- Pass 3: openings --------------------------------------------------------------------------------------------
  const hd = OPENINGS.hallDoor
  door(c, hd.at, hd.facing, hd.out, { wood: P.wood, step: hd.step, stepStair: P.dressedStairs })
  const cd = OPENINGS.cookDoor
  door(c, cd.at, cd.facing, cd.out, { wood: P.wood, step: cd.step, stepStair: P.dressedStairs })
  const id = OPENINGS.innerDoor
  for (let y = id.at[1]; y < id.at[1] + id.height; y++) c.clear(id.at[0], y, id.at[2])
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, sill: P.dressedStairs })

  // --- Pass 4: depth and detail -------------------------------------------------------------------------------------
  // Stone pilasters a block proud of the front wall, framing three bays; the outer two run up under the eaves
  for (const x of [3, 6, 10, 13]) {
    for (let y = 0; y <= 6; y++) c.set(x, y, 0, stone)
    if (x === 3 || x === 13) c.set(x, 7, 0, P.dressed)
    else stair(c, x, 7, 0, P.dressedStairs, 'south')
  }
  // Buttresses down the long west wall, between its windows
  for (const z of [4, 8]) masonButtress(c, 2, z, 0, 4, stone, P.dressedStairs, 'east')
  // Dressed corners and a stone band at the wall top, round the hall
  quoins(c, [[H.x0, H.z1], [H.x1, H.z1]], 1, H.wallTop - 1, P.dressed, 'polished_andesite')
  stringCourse(c, H.x0, H.z0, H.x1, H.z1, H.wallTop, P.dressed)
  // The door: a stone lintel with a keystone, a brass hood with a lantern under it
  for (const x of [7, 9]) c.set(x, 3, 1, P.dressed)
  c.set(8, 3, 1, P.key)
  hood(c, 8, 4, 0, brass)
  // The gear window's brass hub
  c.set(8, 10, 1, brass.block)
  // (no barge boards: under these 45-degree rakes they doubled the steps into a saw-tooth edge)
  // A dormer in the catslide over the cookhouse's back half, lighting the Chef's chest and bench
  const D = MASSES.dormer
  slopeDormer(c, { face: D.face, zc: D.zc, y0: D.y0, dir: 1 }, { 13: 8, 14: 7, 15: 6, 16: 5, 17: 4, 18: 3 }, P.roof, brick, P.glass)
  // A lightning rod on the lantern's front ridge
  c.set(8, lan.ridgeY + 1, L.roof.along[0], P.finial)
  // Tie beams across the hall at the wall top, braced at the walls (the lamps hang from them)
  for (const z of [4, 7]) {
    for (let x = H.x0 + 1; x < H.x1; x++) c.set(x, H.wallTop, z, P.beam, { axis: 'x' })
    kneeBrace(c, H.x0 + 1, H.wallTop - 1, z, `${P.wood}_stairs`, 'west')
    kneeBrace(c, H.x1 - 1, H.wallTop - 1, z, `${P.wood}_stairs`, 'east')
  }

  // --- Pass 5: interior (the plan's furniture) ------------------------------------------------------------------------
  const parts = {
    ...furnitureParts,
    ...kitchenParts,
    jobBlock(c, f) { c.free(f.pos); c.set(...f.pos, f.block, { facing: f.facing, lit: 'false' }) },
    chest(c, f) { kitchenParts.pantry(c, f) },
  }
  for (const f of FURNITURE) parts[f.part](c, f)
  // a rail along the mixer's west side (the Chef works it from the east and the front)
  for (const z of [7, 8, 9]) c.set(7, 1, z, `${P.wood}_fence`)
  // the smiths' corner: an anvil by the bench; flour sacks behind the belt's drive; the copper stock by the east window
  c.set(5, 1, 3, 'anvil', { facing: 'east' })
  for (const x of [5, 6, 7]) c.set(x, 1, 11, 'hay_block', { axis: 'y' })
  pallet(c, [[12, 1, 2], [12, 1, 3]], brass.block, [[12, 2, 2, brass.slab]])
  // the west half's brass stock, stacked on the floor behind the smiths' table (on the floor, not on the belt)
  pallet(c, [[5, 1, 8], [6, 1, 8]], brass.block, [[6, 2, 8, brass.slab]])

  // --- Pass 6: ground ------------------------------------------------------------------------------------------------
  for (const [x, z] of OUTSIDE.lamps) lampPost(c, x, 0, z, `${P.wood}_fence`, 2)
  pallet(c, OUTSIDE.stock.cells, brass.block, OUTSIDE.stock.top.map(([x, y, z]) => [x, y, z, brass.slab]))
  for (const [x, y, z] of OUTSIDE.sacks) c.set(x, y, z, 'hay_block', { axis: 'y' })
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `brass_foundry_cut${cut}` : 'brass_foundry' } }
}
