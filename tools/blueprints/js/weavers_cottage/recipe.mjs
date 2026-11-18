// Weaver's Cottage (weavers_cottage, weavers_cottage_2; ROADMAP 49), the recipe: assembles plan.mjs from the shared
// parts library (../parts) in passes (block-out and structure, openings, depth and detail, interior, ground, light) and
// returns the structure for the minecraft-builder skill's runner. LEVEL=2 draws Level II: all of Level I in its cells,
// then the dye garden, its wall, the back door and the tenter frame. Also writes furniture.json (every furniture spot
// the parts recorded) for tools/blueprints/rebuild.py. Build both levels: ./build.sh. CUT=<y> cuts everything above y.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, RANGE, PORCH, OPENINGS, FURNITURE, FLEECES, FOLD, FRONT, GARDEN } from './plan.mjs'
import {
  Canvas, mix, slab, stair, plinth, floor, wallPanel, window, door, quoins, gableRoof, gableWall, bargeTrim, flight,
  railing, railingRun, furnitureParts, flowerBed, woodpile, leanTo, lightBand, mouldedCourse, takingInDoor, tenterFrame, dyeRows,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level = 1) {
  const c = new Canvas(SIZE[level])
  const P = PALETTE
  const rubble = mix(P.stone, 11)
  const mossy = mix(P.mossy, 12)
  const W = RANGE.walls, R = RANGE.room
  const top = RANGE.wallTop

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // The range: a mossy rubble footing, the workroom's boards, rubble walls two storeys high to the eaves
  plinth(c, W.x0, W.z0, W.x1, W.z1, mossy)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], 1, top, rubble) // front
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, top, rubble) // back
  wallPanel(c, [W.x0, W.z0], [W.x0, W.z1], 1, top, rubble) // west gable wall
  wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, top, rubble) // east gable wall
  for (let x = W.x0; x <= W.x1; x++) for (const z of [W.z0, W.z1]) c.set(x, 1, z, mossy) // weathering low down
  for (let z = W.z0; z <= W.z1; z++) for (const x of [W.x0, W.x1]) c.set(x, 1, z, mossy)
  floor(c, R.x0, R.z0, R.x1, R.z1, RANGE.loftY, P.floor) // the loft floor

  // The roof: 45-degree slates, ridge along x, a block over every side; the gables in rubble up under it
  const main = gableRoof(c, RANGE.roof, P.roof)
  for (const x of [W.x0, W.x1])
    gableWall(c, 'x', x, [W.z0, W.z1], top + 1, main.under, () => rubble)
  // The porch: a little gabled stone porch over the door, its ridge meeting the loft band
  const pr = PORCH.roof
  plinth(c, PORCH.x0, PORCH.z0, PORCH.x1, PORCH.z1, P.dressed)
  for (const x of [PORCH.x0, PORCH.x1]) wallPanel(c, [x, PORCH.z0], [x, PORCH.z1], 1, 3, rubble)
  const porch = gableRoof(c, pr, P.roof)
  gableWall(c, 'z', PORCH.z0, [PORCH.x0, PORCH.x1], 1, porch.under, () => rubble)

  // --- Pass 2: openings ---------------------------------------------------------------------------------------
  const pa = OPENINGS.porchArch
  for (let y = pa.at[1]; y < pa.at[1] + pa.height; y++) c.clear(pa.at[0], y, pa.at[2])
  for (const y of [1, 2]) c.clear(9, y, 1)
  stair(c, pa.step[0], pa.step[1], pa.step[2], 'cobblestone_stairs', 'south')
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, fd.out, { wood: P.wood })
  const od = OPENINGS.foldDoor
  door(c, od.at, od.facing, od.out, { wood: P.wood })
  if (level >= 2) { const bd = OPENINGS.backDoor; door(c, bd.at, bd.facing, bd.out, { wood: P.wood }) }

  // The weaver's window (three lights) and the loft band (five), stone mullions, a dressed lintel over each
  const ww = OPENINGS.weaverWindow, lb = OPENINGS.loftBand
  lightBand(c, { axis: 'x', line: ww.z, u0: ww.x0, u1: ww.x1, y0: ww.y0, y1: ww.y1, lights: ww.lights }, { glass: P.glass, mullion: P.mullion, lintel: P.dressed })
  lightBand(c, { axis: 'x', line: lb.z, u0: lb.x0, u1: lb.x1, y0: lb.y0, y1: lb.y1, lights: lb.lights }, { glass: P.glass, mullion: P.mullion, lintel: P.dressed })
  for (const w of OPENINGS.windows) {
    window(c, w.cells, w.out, null, { glass: P.glass })
    const [x, y, z] = w.cells[w.cells.length - 1]
    c.set(x, y + 1, z, P.dressed) // a dressed lintel over every light
  }
  // the flight up to the loft, its well, the railing round it
  const st = OPENINGS.stairs
  for (const [x, y, z] of st.well) c.clear(x, y, z)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  railing(c, [[8, 5, 6], [9, 5, 6], [10, 5, 6]], P.wood)

  // --- Pass 3: depth and detail -------------------------------------------------------------------------------
  // dressed quoins up every corner, long and short
  quoins(c, [[W.x0, W.z0], [W.x1, W.z0], [W.x0, W.z1], [W.x1, W.z1]], 1, top, P.dressed, P.dressedAlt)
  // the sill course under the weaver's window, the moulded course at the loft floor (the ground band's drip and the
  // loft band's sill), round the front to the porch and on the back wall's whole length
  for (let x = ww.x0; x <= ww.x1; x++) slab(c, x, 1, 1, P.sill, 'top')
  mouldedCourse(c, { axis: 'x', line: W.z0, u0: W.x0, u1: 7, y: RANGE.loftY, out: 'north' }, P.course)
  mouldedCourse(c, { axis: 'x', line: W.z0, u0: W.x1, u1: W.x1, y: RANGE.loftY, out: 'north' }, P.course)
  mouldedCourse(c, { axis: 'x', line: W.z1, u0: W.x0, u1: W.x1, y: RANGE.loftY, out: 'south' }, P.course)
  for (let x = W.x0; x <= ww.x1; x++) c.set(x, 0, 1, mossy) // the footing the sill course stands over
  c.set(7, 0, 1, mossy)
  // the porch arch's dressed jambs and lintel
  for (const x of [8, 10]) for (let y = 1; y <= 3; y++) c.set(x, y, 0, P.dressed)
  c.set(9, 3, 0, P.dressed)
  c.set(9, 4, 1, 'lantern', { hanging: 'true', waterlogged: 'false' }) // the porch lamp under its ridge
  // barge boards under the overhanging rakes, the porch's too
  bargeTrim(c, 'x', RANGE.roof.along[0], RANGE.roof.across, main.under, `${P.wood}_stairs`)
  bargeTrim(c, 'x', RANGE.roof.along[1], RANGE.roof.across, main.under, `${P.wood}_stairs`)
  // the gable stack: three wide on the west gable's apex, dressed, a slab cap either side of the smoke
  for (let z = 4; z <= 6; z++) for (let y = main.under[z]; y <= main.ridgeY + 1; y++) c.set(W.x0, y, z, P.dressed)
  for (let y = 8; y < main.ridgeY; y++) c.set(W.x0, y, 5, P.dressed) // the flue's face down the gable
  c.set(W.x0, main.ridgeY + 2, 5, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  for (const z of [4, 6]) slab(c, W.x0, main.ridgeY + 2, z, `${P.dressed.replace(/s$/, '')}_slab`, 'bottom')
  // the taking-in hatch high in the east gable (glazed, its leaf folded back: a door there would let a villager walk
  // out of the loft onto a 4-block drop), the hoist beam over the fold, its hook a lantern
  const ti = OPENINGS.takingIn, h = FOLD.hoist
  takingInDoor(c, ti.at, ti.facing, ti.out, { wood: P.wood, hoist: h.at, log: P.log, bracket: `${P.wood}_stairs`, reach: h.reach, drop: h.drop, glass: P.glass, leaf: ti.leaf })
  c.set(ti.at[0], ti.at[1] + 2, ti.at[2], P.dressed) // its lintel

  // --- Pass 4: interior (the plan's furniture) ----------------------------------------------------------------
  // the hearth in the west wall: dressed jambs and lintel round the fire
  for (const z of [4, 6]) for (const y of [1, 2]) c.set(2, y, z, P.dressed)
  for (let z = 4; z <= 6; z++) c.set(2, 3, z, P.dressed)
  for (const f of FURNITURE) {
    if (f.part === 'loom') { c.free(f.pos); c.set(...f.pos, 'loom', { facing: f.facing }); continue }
    furnitureParts[f.part](c, f)
  }
  for (const [x, y, z, w] of FLEECES) c.set(x, y, z, typeof w === 'number' ? P.wool[w] : w)

  // --- Pass 5: ground -----------------------------------------------------------------------------------------
  // the fold: grass to graze, the fence and its gate, a drystone back wall, the lean-to and its hay
  const fg = FOLD.ground
  for (let x = fg.x0; x <= fg.x1; x++) for (let z = fg.z0; z <= fg.z1; z++) {
    const trodden = FOLD.trodden.some(([tx, tz]) => tx === x && tz === z)
    c.set(x, 0, z, trodden ? 'coarse_dirt' : 'grass_block', trodden ? {} : { snowy: 'false' })
  }
  const bw = FOLD.backWall
  for (let x = bw.x0; x <= bw.x1; x++) { c.set(x, 0, bw.z, mossy); for (let y = 1; y <= bw.top; y++) c.set(x, y, bw.z, mossy) }
  railingRun(c, FOLD.fence, P.wood)
  railingRun(c, FOLD.westFence, P.wood)
  for (const [x, , z] of [...FOLD.fence, ...FOLD.westFence]) c.set(x, 0, z, mossy)
  for (let x = 12; x <= 17; x++) c.set(x, 0, 2, mossy)
  for (let z = 2; z <= 10; z++) c.set(17, 0, z, mossy)
  for (const z of [9, 10]) c.set(11, 0, z, mossy)
  const g = FOLD.gate
  c.set(g.at[0], g.at[1], g.at[2], `${P.wood}_fence_gate`, { facing: g.facing, in_wall: 'false', open: 'false', powered: 'false' })
  for (const [x, y, z] of FOLD.hay) c.set(x, y, z, 'hay_block', { axis: 'z' })
  const sh = FOLD.shed
  leanTo(c, { axis: 'x', u0: sh.x0, u1: sh.x1, v0: sh.z0, v1: sh.z1, topY: sh.topY, toward: 'west', posts: sh.posts }, { roof: P.roof, post: `${P.wood}_fence` })
  woodpile(c, FOLD.woodpile.x, FOLD.woodpile.z0, FOLD.woodpile.z1, P.log)
  for (const x of [2, 6]) slab(c, x, 1, W.z1 + 1, P.sill, 'top') // sills under the back lights, on the footing
  for (const x of [2, 6]) c.set(x, 0, W.z1 + 1, mossy) // the sills' footing stones (the garden's grass meets them in Level II)
  c.set(...FOLD.cornerLamp, 'lantern', { hanging: 'false', waterlogged: 'false' }) // on the corner post, by the gate
  // in front: the step's path, a bed of flowers under the weaver's window
  flowerBed(c, FRONT.bed.x0, FRONT.bed.x1, FRONT.bed.z, FRONT.bed.plants)

  // --- Level II: the dye garden -------------------------------------------------------------------------------
  if (level >= 2) {
    const G = GARDEN
    for (let x = 1; x <= 10; x++) for (let z = 9; z <= 15; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, 'grass_block', { snowy: 'false' })
    for (let i = 0; i + 1 < G.wall.length; i++) {
      const [ax, , az] = G.wall[i], [bx, , bz] = G.wall[i + 1]
      for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) for (let z = Math.min(az, bz); z <= Math.max(az, bz); z++) c.set(x, 0, z, mossy)
    }
    railingRun(c, G.wall, null, 'mossy_cobblestone_wall')
    for (let z = G.walk.z0; z <= G.walk.z1; z++) c.set(G.walk.x, 0, z, P.gravel)
    for (let x = G.path.x0; x <= G.path.x1; x++) c.set(x, 0, G.path.z, P.gravel)
    const west = {}, east = {}
    for (const [z, [w, e]] of Object.entries(G.rows)) { west[z] = w; east[z] = e }
    dyeRows(c, G.beds.west[0], G.beds.west[1], west, G.soil)
    dyeRows(c, G.beds.east[0], G.beds.east[1], east, G.soil)
    const hb = G.herbs
    for (let x = hb.x0; x <= hb.x1; x++) c.set(x, 1, hb.z, hb.plants[(x - hb.x0) % hb.plants.length])
    const t = G.tenter
    tenterFrame(c, { axis: 'x', line: t.z, u0: t.x0, u1: t.x1, posts: t.posts, y: t.y, sides: ['north', 'south'] }, { post: `${P.wood}_fence`, rail: P.log, cloth: t.cloth })
  }
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const id = level === 1 ? 'weavers_cottage' : `weavers_cottage_${level}`
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
