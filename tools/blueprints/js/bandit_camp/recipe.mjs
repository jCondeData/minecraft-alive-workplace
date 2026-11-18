// The Bandit Camp (the Lookout Camp), the recipe: assembles plan.mjs from the shared parts library (../parts,
// bandit_camp_lair.mjs grown for it) in passes (ground, block-out of the tents, marquee, nest and cart, depth and
// detail, interior, the camp ground's props, light) and returns the structure for the minecraft-builder skill's runner.
// Writes furniture.json (the camp's furniture spots) for rebuild.py. Before returning it checks the brief's hard rules
// (the fire, the spots, the counts, the ground layer) and throws on any miss.
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE, FIRE, CHIEF_SPOT, BAND_SPOTS, POLE } from './plan.mjs'
import {
  Canvas, mix, slab, stair, furnitureParts, cartWheel, cartBed, logStack,
  patchwork, pitchedTent, ridgePole, marquee, crowsNest, fireRing, trophyStake, hitchRail,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, M = MASSES, T = P.timber
  const [w, , d] = SIZE
  const [fx, fy, fz] = FIRE
  const trampled = mix(P.trampled, 3)

  // --- Pass 1: the ground layer (y = 0). Drawn: the trampled round about the fire, the paths out of the gaps, the
  // tents' floors, the nest's and the cart's ground. Everything else is left to the world (never air). ------------------
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++)
    if ((x - fx) ** 2 + (z - fz) ** 2 <= M.plaza.r2Drawn) c.set(x, 0, z, trampled)
  const box = (x0, x1, z0, z1, id) => { for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) c.set(x, 0, z, id) }
  for (const g of [OPENINGS.wayIn, OPENINGS.west, OPENINGS.east]) box(g.x[0], g.x[1], g.z[0], g.z[1], P.path)
  box(...M.tentA.across, ...M.tentA.along, P.tentFloor)
  box(...M.tentB.across, ...M.tentB.along, P.tentFloor)
  box(M.marquee.x0, M.marquee.x1, M.marquee.zFront, M.marquee.zBack, P.chiefFloor)
  box(M.nest.x0, M.nest.x1, M.nest.z0, M.nest.z1, P.nestFloor)
  for (const x of [M.nest.x0, M.nest.x1]) for (const z of [M.nest.z0, M.nest.z1]) c.set(x, 0, z, P.pad)
  box(10, 14, 0, 3, P.nestFloor)
  c.set(fx, 0, fz, P.hearth)

  // --- Pass 2: block-out ------------------------------------------------------------------------------------------
  // Bunk tents A and B: 5-wide pitched tents of mended brown cloth, hemmed, a mouth in the front gable
  const tentA = pitchedTent(c, M.tentA, { cloth: patchwork(P.clothA.base, P.clothA.patches), hem: (x, y) => (y <= M.tentA.y0 + 1 ? P.hemA : null) })
  const tentB = pitchedTent(c, M.tentB, { cloth: patchwork(P.clothB.base, P.clothB.patches), hem: (x, y) => (y <= M.tentB.y0 + 1 ? P.hemB : null) })
  // The chief's marquee: black cloth striped red down the roof, open along its front on two posts
  const mq = M.marquee
  const tent = marquee(c, mq, { cloth: P.chief, post: T.log, valance: P.valance })
  // the roof is striped red down its slope (every other column), the walls stay black
  for (let z = mq.zFront; z <= mq.zBack; z++) for (let x = mq.x0; x <= mq.x1; x++) c.set(x, tent.roofY(z), z, (x - mq.x0) % 2 === 1 ? P.stripe : P.chief)
  // and the stripes run on down the back wall, so the back reads as sewn panels of cloth, not a black slab
  for (let x = mq.x0 + 1; x < mq.x1; x += 2) for (let y = mq.y0; y < tent.roofY(mq.zBack); y++) c.set(x, y, mq.zBack, P.stripe)
  // The crow's nest on four legs, the flag leg running on up
  const N = M.nest
  crowsNest(c, { ...N, tall: [N.flagLeg] }, { leg: T.log, deck: T.planks, rail: T.fence, ladder: 'ladder', bracket: T.stairs, railPost: T.fence, brace: T.stripped })
  // The stolen cart: two disc wheels on an axle along z, a bed of two beams, its shafts down on the ground
  for (const hub of M.cart.hubs) cartWheel(c, hub, 'z', { rim: T.wheel, hubLog: T.stripped })
  cartBed(c, { ...M.cart.bed, along: 'x' }, { beam: T.stripped, deck: T.planks })
  for (const [x, y, z] of M.cart.shafts) slab(c, x, y, z, T.slab)

  // --- Pass 3: depth and detail -----------------------------------------------------------------------------------
  // ridge poles lying on the bunk tents' peaks, a cell out over each mouth
  for (const [t, m] of [[tentA, M.tentA], [tentB, M.tentB]]) ridgePole(c, { axis: 'z', u: t.mid, y: t.peakY + 1, ...m.pole }, T.stripped)
  // the chief's standard on his roof's back and the nest's red flag, both turned to the fire's side (north)
  const [sx, sy, sz] = mq.standard
  c.set(sx, sy, sz, T.fence)
  c.set(sx, sy + 1, sz, P.flag, { rotation: '8' })
  c.set(N.flagLeg.x, N.flagLeg.top + 1, N.flagLeg.z, T.fence)
  c.set(N.flagLeg.x, N.flagLeg.top + 2, N.flagLeg.z, P.flag, { rotation: '8' })
  // the stolen village bell hung under the deck: the camp's alarm
  c.set(...N.bell, 'bell', { attachment: 'ceiling', facing: 'north', powered: 'false' })
  // the way in: two trophy stakes, a skull on one, a lantern on the other
  const [s1, s2] = OPENINGS.wayIn.stakes
  trophyStake(c, s1, { log: T.log, fence: T.fence, top: 'skeleton_skull', topProps: { rotation: '8' } })
  trophyStake(c, s2, { log: T.log, fence: T.fence, top: 'lantern', topProps: { hanging: 'false', waterlogged: 'false' } })

  // --- Pass 4: interior (the plan's furniture) ------------------------------------------------------------------------
  for (const f of FURNITURE) furnitureParts[f.part](c, f)

  // --- Pass 5: the camp ground: the fire in its stones, seats, fuel, the target, the horses' rail, the plunder ------------
  c.set(fx, fy, fz, 'campfire', { facing: 'south', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  const ringStairs = mix(P.stone.stairs, 5), ringSlabs = mix(P.stone.slabs, 6)
  fireRing(c, fx, fy, fz, { stairs: ringStairs, slabs: ringSlabs })
  logStack(c, OUTSIDE.bench.cells, T.stripped, 'z')
  for (const [x, y, z] of OUTSIDE.stumps) c.set(x, y, z, T.stripped, { axis: 'y' })
  logStack(c, OUTSIDE.woodpile, T.log, 'z')
  c.set(...OUTSIDE.target.hay, 'hay_block', { axis: 'y' })
  c.set(...OUTSIDE.target.target, 'target', { power: '0' })
  hitchRail(c, OUTSIDE.hitch, T.fence)
  for (const h of OUTSIDE.hay) c.set(...h, 'hay_block', { axis: 'x' })
  c.set(...OUTSIDE.grindstone, 'grindstone', { face: 'floor', facing: 'east' })
  const pl = OUTSIDE.plunder
  for (const b of [...pl.barrels, ...pl.heap]) c.set(...b, 'barrel', { facing: 'up', open: 'false' })
  for (const h of pl.hay) c.set(...h, 'hay_block', { axis: 'x' })
  for (const b of pl.bale) c.set(...b, 'white_wool')
  for (const t of pl.tarp) c.set(...t, 'red_carpet')

  // --- Pass 6: light: a lantern under each bunk tent's pole end, one on the nest's corner leg -------------------------
  for (const m of [M.tentA, M.tentB]) c.set(...m.lamp, 'lantern', { hanging: 'true', waterlogged: 'false' })
  c.set(...N.lamp, 'lantern', { hanging: 'false', waterlogged: 'false' })

  check(c)
  return c
}

// The brief's hard rules, checked on the drawing itself.
function check(c) {
  const fail = m => { throw new Error(`bandit_camp: ${m}`) }
  const [w, h, d] = c.size
  const [fx, fy, fz] = FIRE
  const fire = c.get(fx, fy, fz)
  if (fire?.id !== 'minecraft:campfire' || fire.props.lit !== 'true') fail('no lit campfire at the fire')
  for (let y = fy + 1; y < h; y++) if (c.get(fx, y, fz)) fail(`a block above the fire at y ${y}`)
  const free = (x, y, z) => !c.get(x, y, z)
  const floorOk = (x, z) => (c.get(x, 0, z) ? !/carpet|slab|stairs|path/.test(c.name(x, 0, z)) || c.name(x, 0, z).endsWith('dirt_path') : true)
  for (const [x, y, z] of [CHIEF_SPOT, ...BAND_SPOTS]) if (!free(x, y, z) || !free(x, y + 1, z) || !floorOk(x, z)) fail(`spot ${x},${y},${z} not open`)
  for (const [dx, dz] of [[3, 0], [-2, 2], [0, -3], [2, 2], [-3, -1], [3, -2]]) for (let r = 0; r < 4; r++) {
    const [ax, az] = [[dx, dz], [-dz, dx], [-dx, -dz], [dz, -dx]][r]
    if (!free(fx + ax, 1, fz + az) || !free(fx + ax, 2, fz + az)) fail(`bounty ground ${fx + ax},${fz + az} (turn ${r}) taken`)
  }
  if (!free(...POLE) || !free(POLE[0], 2, POLE[2])) fail('the pole spot is taken')
  let chests = 0, beds = 0, wool = 0, campfires = 0
  for (const [k, v] of c.cells) {
    const [x, y, z] = k.split(',').map(Number)
    if (v.id === 'minecraft:chest') { chests++; if (Math.abs(x - fx) > 9 || Math.abs(z - fz) > 9 || y < 1 || y > 6) fail('chest out of reach') }
    if (v.id.endsWith('_bed')) beds++
    if (v.id === 'minecraft:campfire') campfires++
    if (v.id.endsWith('_wool') && y >= 1 && y <= 6) wool++
    if (y === 0 && /chest|barrel|campfire|_bed|banner|sign|skull|bell|lantern|carpet|slab|stairs/.test(v.id)) fail(`${v.id} in the ground layer at ${k}`)
    if (/sand|gravel|concrete_powder|anvil|scaffolding|leaves|sapling|water|lava|snow|ice/.test(v.id) && !v.id.includes('sandstone')) fail(`${v.id} would not stay at ${k}`)
  }
  if (chests !== 2) fail(`${chests} chests, want 2`)
  if (beds !== 10) fail(`${beds} bed blocks, want 10`)
  if (campfires !== 1) fail(`${campfires} campfires`)
  if (wool <= 20) fail(`only ${wool} wool`)
  // ground-type full blocks above y = 0 only in a column that holds something else
  const GROUND = /^minecraft:(dirt|grass_block|smooth_stone|coarse_dirt|dirt_path|gravel|cobblestone|fire)$/
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) {
    const col = []
    for (let y = 1; y < h; y++) if (c.get(x, y, z)) col.push(c.name(x, y, z))
    if (col.length && col.every(n => GROUND.test(n))) fail(`column ${x},${z} holds only ground blocks`)
  }
  console.log(`bandit_camp: fire ok, ${chests} chests, ${beds / 2} beds, ${wool} wool, spots open`)
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `bandit_camp_cut${cut}` : 'bandit_camp' } }
}
