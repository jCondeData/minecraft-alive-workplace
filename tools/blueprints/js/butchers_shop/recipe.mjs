// The Butcher's Shop, the recipe: assembles plan.mjs from the shared parts library (../parts) in passes (block-out and
// structure, roofs and gables, openings, depth and detail, interior, the freezer, ground and light) and returns the
// structure for the minecraft-builder skill's runner. Writes furniture.json (every furniture spot) and out/cellar.txt
// (the freezer's dug box for rebuild.py --cellar). Build: build.sh. CUT=<y> cuts everything above y (the drawing's y);
// ABOVE=1 drops the freezer (renders: it would hang under the render's ground).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, FITTINGS, OUTSIDE } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, plinth, floor, post, wallPanel, window, door, doorway, gableRoof, gableWall, bargeTrim,
  pentice, hatchLadder, lampPost, woodpile, furnitureParts, smokeTower, freezer, meatRail, choppingBlock,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new LiftedCanvas(SIZE, BASEMENT)
  const P = PALETTE
  const stone = mix(P.plinth, 11), brick = mix(P.tower, 5), infill = mix(P.infill, 13)
  const S = MASSES.shop, T = MASSES.tower
  const { x0, x1, z0, z1 } = S.posts
  const pn = S.panels, rm = S.room
  const log = (axis) => [P.frame, { axis }]

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  plinth(c, x0, z0, x1, z1, stone)
  plinth(c, T.x0, T.z0, T.x1, T.z1, stone)
  floor(c, rm.x0, rm.z0, rm.x1, rm.z1, 0, P.floor)
  floor(c, T.room.x0, T.room.z0, T.room.x1, T.room.z1, 0, P.floor)
  // white panels a block in from the posts, the side walls up through the wall plate
  wallPanel(c, [pn.x0, pn.z0], [pn.x1, pn.z0], 1, 3, infill)
  wallPanel(c, [pn.x0, pn.z1], [pn.x1, pn.z1], 1, 3, infill)
  wallPanel(c, [pn.x0, pn.z0], [pn.x0, pn.z1], 1, 3, infill)
  wallPanel(c, [pn.x1, pn.z0], [pn.x1, pn.z1], 1, 3, infill)
  for (const x of [pn.x0, pn.x1]) for (let z = pn.z0; z <= pn.z1; z++) c.set(x, S.beamY, z, ...log('z')) // wall plates
  // dark oak posts proud on the plinth line, beams across the front and back, tie beams across the shop
  for (const x of S.frontPosts) post(c, x, z0, 1, 3, P.frame)
  for (const x of S.backPosts) post(c, x, z1, 1, 3, P.frame)
  for (const z of S.westPosts) post(c, x0, z, 1, 3, P.frame)
  for (const z of S.eastPosts) post(c, x1, z, 1, 3, P.frame)
  for (const z of [z0, z1]) for (let x = x0; x <= x1; x++) c.set(x, S.beamY, z, ...log('x'))
  for (const z of S.ties) for (let x = rm.x0; x <= rm.x1; x++) c.set(x, S.beamY, z, ...log('x'))

  // --- Pass 2: roofs and gables ----------------------------------------------------------------------------------
  const main = gableRoof(c, S.roof, P.roof)
  // the gables jettied out to the post line over the beams: studs, a collar, the front one to carry the banner
  const gable = (front) => (u, y) => {
    if (u === 8 || u === 12) return log('y')
    if (y === 7 && u >= 9 && u <= 11) return log('x')
    if (front && u === 10 && y === 8) return log('y') // the king post's foot under the ridge
    return infill
  }
  gableWall(c, 'z', z0, [x0, x1], S.beamY + 1, main.under, gable(true))
  gableWall(c, 'z', z1, [x0, x1], S.beamY + 1, main.under, gable(false))
  bargeTrim(c, 'z', S.roof.along[0], S.roof.across, main.under, `${P.trim}_stairs`)
  bargeTrim(c, 'z', S.roof.along[1], S.roof.across, main.under, `${P.trim}_stairs`)
  for (const z of S.roof.along) for (const x of S.roof.across) c.clear(x, main.under[x] - 1, z) // no trim hanging under the eaves' corners
  // the Smoke Tower: drawn over the shop's east eave, its cap jettied out on brick brackets
  smokeTower(c, T, { body: brick, soot: P.soot, bracket: P.bracket, roof: P.roof, steep: false })
  for (let x = T.x0; x <= T.x1; x++) for (let z = T.z0; z <= T.z1; z++) // the string course at the shop's eave line
    if ((x === T.x0 || x === T.x1 || z === T.z0 || z === T.z1) && !(x === T.x0 && z > T.z0 && z < T.z1)) c.set(x, 4, z, P.band)
  for (const z of [T.z0 - 1, T.z1 + 1]) for (let y = 6; y < T.top + 1; y++) c.set(T.x0 - 1, y, z, brick) // corbel piers where the cap's corner meets the shop's roof

  // --- Pass 3: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: 'cobblestone_stairs' })
  for (const [x, y, z] of OPENINGS.towerDoorway) doorway(c, [x, y, z], 2)
  const pots = { 'west front': ['potted_red_tulip'], 'west back': ['potted_cornflower'], 'east front': ['potted_oxeye_daisy'], 'east back': ['potted_red_tulip'], back: ['potted_fern'] }
  for (const w of OPENINGS.windows)
    window(c, w.cells, w.out, w.bare ? null : 'sill', { glass: P.glass, wood: P.wood, sill: P.sill, pots: pots[w.name] ?? [] })
  for (const [x, y, z] of OPENINGS.vents) c.set(x, y, z, 'iron_bars')

  // --- Pass 4: depth and detail ---------------------------------------------------------------------------------
  // the shambles front: a slate pentice over the whole front between the corner posts, a lamp under it at the door,
  // lanterns standing on the display boards, the shop's banner on the jettied gable over the door
  pentice(c, Array.from({ length: x1 - x0 - 1 }, (_, i) => [x0 + 1 + i, z0 - 1]), S.beamY, P.roof, 'south')
  c.set(fd.at[0], S.beamY - 1, z0 - 1, 'lantern', { hanging: 'true', waterlogged: 'false' })
  for (const x of [7, 13]) c.set(x, 2, z0, 'lantern', { hanging: 'false', waterlogged: 'false' })
  c.set(fd.at[0], 6, z0 - 1, P.banner, { facing: 'north' })

  // --- Pass 5: interior (the plan's furniture and fittings) -------------------------------------------------------
  for (const f of FURNITURE) furnitureParts[f.part](c, f)
  choppingBlock(c, FITTINGS.choppingBlock, `stripped_${P.wood}_log`, `${P.wood}_pressure_plate`)
  meatRail(c, FITTINGS.hooks, 1)
  meatRail(c, FITTINGS.towerHooks, 2)

  // --- Pass 6: the freezer and its hatch ----------------------------------------------------------------------------
  const dug = freezer(c, MASSES.freezer, { wall: P.freezerWall, pier: P.freezerPier, floor: P.freezerFloor, ice: P.freezerIce })
  const h = OPENINGS.hatch
  hatchLadder(c, h.x, h.z, -MASSES.freezer.depth + 1, 0, h.facing, P.wood)
  for (const [x, y, z, id] of FITTINGS.iceStack) c.set(x, y, z, id)
  meatRail(c, FITTINGS.freezerHooks, 1)

  // --- Pass 7: ground and light ------------------------------------------------------------------------------------
  // (50.4, the ship step: no stock pen, lean-to or hay. With them the build overran the one working day that
  // BasementGameTests.butchersShopGoesUpWithItsFreezer and QaBasementSeals1107bGameTests give it (24,000 ticks from
  // the morning); without them it finishes the same day. The Butcher's herd grazes wherever it stands within 16 of the
  // smoker. The pen's parts, stockPen and shelter, stay in the parts library.)
  const [lx, ly, lz] = OUTSIDE.lamp
  c.set(lx, 0, lz, stone)
  lampPost(c, lx, ly, lz, `${P.wood}_fence`, 2)
  woodpile(c, OUTSIDE.woodpile.x, OUTSIDE.woodpile.z0, OUTSIDE.woodpile.z1, `${P.wood}_log`)
  return { c, dug }
}

export default function build(api) {
  const { c, dug } = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  fs.mkdirSync(path.join(here, 'out'), { recursive: true })
  fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(','))
  const cut = process.env.CUT
  let canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  if (process.env.ABOVE) {
    const a = new Canvas(SIZE)
    for (const [k, v] of canvas.cells) { const [x, y, z] = k.split(',').map(Number); if (y >= BASEMENT) a.cells.set(`${x},${y - BASEMENT},${z}`, v) }
    canvas = a
  }
  if (process.env.BELOW) { // renders only: the freezer alone (and the ground floor over it, cut away by CUT)
    const a = new Canvas([SIZE[0], BASEMENT, SIZE[2]])
    for (const [k, v] of canvas.cells) { const [x, y, z] = k.split(',').map(Number); if (y < BASEMENT) a.cells.set(k, v) }
    canvas = a
  }
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `butchers_shop_cut${cut}` : 'butchers_shop' } }
}
