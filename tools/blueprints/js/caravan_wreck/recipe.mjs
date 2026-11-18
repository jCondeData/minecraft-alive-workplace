// The Caravan Wreck (story/caravan_wreck), the recipe: assembles plan.mjs from the shared parts library (../parts;
// wrecks.mjs grown for it) in passes (block-out: the body on its side; structure: the gear and the bonnet; depth and
// detail: wheels, arrows, the slash; interior: the hollow's goods; ground: the whole y 0 layer as a map of what
// happened; light: the lantern) and returns the structure for the minecraft-builder skill's runner. Writes
// furniture.json (no spots: nobody uses the wreck) for rebuild.py.
// Build: see package.json ("npm run build" draws and saves the candidate); CUT=<y> cuts everything above y.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, BODY, GEAR, BONNET, WHEELS, GROUND, SPILL, PINS, LANTERN } from './plan.mjs'
import {
  Canvas, slab, trapdoor, trailGround, wheelRuts, arrowStuds,
  sideWagon, underGear, sideBonnet, spokeWheel, flatWheel, scatter,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE, W = P.wagon, G = P.gear, B = P.bonnet

// A repeatable cell hash (integer maths: the same cell always gets the same block) and a weighted pick by it.
const hash = (x, z, seed) => {
  let h = Math.imul(x + 31, 374761393) ^ Math.imul(z + 17, 668265263) ^ Math.imul(seed, 1442695041)
  h = Math.imul(h ^ (h >>> 13), 1274126177)
  return (h ^ (h >>> 16)) >>> 0
}
const pick = (weighted, seed) => {
  const total = weighted.reduce((a, [, n]) => a + n, 0)
  return (x, z) => {
    let k = hash(x, z, seed) % total
    for (const [id, n] of weighted) { if (k < n) return id; k -= n }
    return weighted[0][0]
  }
}

export function draw() {
  const c = new Canvas(SIZE)
  const [w, , d] = SIZE

  // --- Pass 1: block-out: the body on its side --------------------------------------------------------------------
  const hollow = sideWagon(c, { floorX: BODY.floorX, y0: BODY.y0, y1: BODY.y1, z0: BODY.z0, z1: BODY.z1, boards: BODY.boards, frontZ: BODY.frontZ },
    { deck: W.deck, board: W.board, rail: W.rail })

  // --- Pass 2: structure: the running gear on the underside, the bonnet on the lee ---------------------------------
  const gx = BODY.floorX + 1
  underGear(c, { x: gx, y0: 0, y1: BODY.y1, axles: GEAR.axles, reachY: GEAR.reachY }, { axle: G.axle })
  for (const p of GEAR.tongue.cells) c.set(...p, G.tongue, { axis: 'z' })
  for (const p of GEAR.tongue.bar) c.set(...p, G.stub)
  const stained = new Set(BONNET.stain.map(p => p.join(',')))
  const cloth = (x, y, z) => (stained.has(`${x},${y},${z}`) ? B.stain : B.canvas)
  const inside = sideBonnet(c, BONNET, { canvas: cloth, hoop: B.hoop })

  // --- Pass 3: depth and detail: the wheels, the stub, the arrows from the road ------------------------------------
  const wheel = { rim: G.rim, hubLog: G.hub, spoke: G.spoke }
  spokeWheel(c, WHEELS.top.hub, { hubLog: G.hub, spoke: G.bareSpoke, rim: G.rim }, WHEELS.top.felloes)
  c.set(...GEAR.stub, G.stub) // the rear axle's top end, splintered where its wheel tore off
  flatWheel(c, WHEELS.flung.hub, wheel, WHEELS.flung.broken, WHEELS.flung.radius)
  for (const p of WHEELS.flung.pieces) slab(c, ...p, G.rim) // the smashed felloes, lying in the grass
  for (const [x, y, z, f] of GEAR.arrows) arrowStuds(c, [x - 1, y, z], [f], P.arrow)

  // --- Pass 4: interior: what the raiders left in the hollow; the spill at the front and the tail -------------------
  const ids = { ...P.goods, scrap: B.scrap }
  scatter(c, SPILL.inside, ids)
  scatter(c, SPILL.front, ids)
  scatter(c, SPILL.tail, ids)

  // --- Pass 5: ground: every cell of y 0, a map of what happened -----------------------------------------------------
  const Gd = P.ground
  const zone = {
    verge: pick(Gd.verge, 3), trodden: pick(Gd.trodden, 5), churned: pick(Gd.churned, 7), road: pick(Gd.road, 11), shade: pick(Gd.shade, 13),
  }
  const rut = new Set()
  // the old road's ruts, grassed over toward both ends of the plot (it fades into the world's own grass there)
  const fade = (v, n) => Math.min(v, n - 1 - v)
  for (const [x, z] of wheelRuts(GROUND.roadRuts)) if (fade(z, d) >= 2 || hash(x, z, 17) % 3 === 0) rut.add(`${x},${z}`)
  for (const line of GROUND.cartRuts) for (const [x, z] of line) if (x >= 2 || hash(x, z, 23) % 2 === 0) rut.add(`${x},${z}`) // fresh: only the plot's edge fades
  for (const [x, z] of GROUND.swerve) rut.add(`${x},${z}`)
  const hoof = new Set(GROUND.hoofs.map(([x, z]) => `${x},${z}`))
  const inBox = (b, x, z) => x >= b.x0 && x <= b.x1 && z >= b.z0 && z <= b.z1
  const [lx, ly, lz, lf] = LANTERN.board
  trapdoor(c, lx, ly, lz, W.trapdoor, lf, 'top') // the tail-lamp's board, fallen flat: its top flush with the ground
  const snowy = id => (/grass_block|podzol/.test(id) ? [id, { snowy: 'false' }] : id) // their full state
  trailGround(c, w, d, (x, z, over) => snowy(groundAt(x, z, over)))
  function groundAt(x, z, over) {
    const k = `${x},${z}`
    const covered = over && !/carpet|tripwire|lantern|_trapdoor|short_grass|fern/.test(over.id)
    if (rut.has(k)) return covered ? Gd.under : Gd.rut
    if (hoof.has(k)) return Gd.hoof
    if (fade(x, w) === 0 || fade(z, d) === 0) return hash(x, z, 29) % 4 ? 'grass_block' : zone.road(x, z) // the plot's edge: the world's grass
    if (x >= 9) return zone.road(x, z) // the road and its verge
    if (inBox(GROUND.churned, x, z)) return zone.churned(x, z)
    if (z >= 10) return zone.trodden(x, z) // the cart's loading ground and its way out
    if (inBox(GROUND.shade, x, z)) return zone.shade(x, z)
    return zone.verge(x, z)
  }

  // --- Pass 6: light: the lantern fallen from the tail, still alight on its board ---------------------------------------
  c.set(...LANTERN.at, P.lantern, { hanging: 'false', waterlogged: 'false' })

  // plants at the verge's untouched edge only (never on a rut or a path)
  for (const [x, z, id] of [[0, 0, 'grass'], [0, 4, 'fern'], [5, 0, 'grass'], [12, 3, 'grass'], [0, 7, 'grass'], [12, 6, 'fern']]) {
    if (!c.get(x, 1, z) && /grass_block|moss_block/.test(c.name(x, 0, z))) c.set(x, 1, z, P.plants[id])
  }

  // the pins: the test reads these cells; fail the build here, not in a GameTest
  for (const [[x, y, z], id] of PINS) if (c.name(x, y, z) !== `minecraft:${id}`) throw new Error(`pin ${id} at ${x},${y},${z} is ${c.name(x, y, z)}`)
  void hollow; void inside
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": []}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `caravan-wreck-cut${cut}` : 'caravan-wreck' } }
}
