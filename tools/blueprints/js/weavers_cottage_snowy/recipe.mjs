// Weaver's Cottage, the snowy look (ROADMAP 49.10d): assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, roof, openings, depth and detail, interior, ground, snow) for LEVEL = 1 or 2, II all of I
// in its cells and the walled dye garden behind. Writes furniture.json (the furniture spots). Build both: build.sh.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, IDS, PALETTE, HOUSE, DORMER, PORCH, STACK, OPENINGS, FURNITURE, FLEECES, FOLD, WOODPILE, GARDEN } from './plan.mjs'
import {
  Canvas, mix, stair, slab, floor, wallPanel, window, door, quoins, gableRoof, gableWall, flight, railing, railingRun,
  furnitureParts, lightBand, frontDormer, snowLoad, snowSkirt, rakeVerge, plantFlower, lampPost,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level = 1) {
  const c = new Canvas(SIZE[level])
  const P = PALETTE
  const W = HOUSE.walls, R = HOUSE.room
  const stack = mix(P.stack, 13), field = mix(P.field, 7)
  const porchCell = (x, z) => x >= PORCH.x0 && x <= PORCH.x1 && z >= PORCH.z0 && z <= PORCH.z1

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++) {
    const edge = x === W.x0 || x === W.x1 || z === W.z0 || z === W.z1
    c.set(x, 0, z, edge ? P.plinth : P.floor)
  }
  wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], 1, HOUSE.wallTop, P.wall)
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, HOUSE.wallTop, P.wall)
  wallPanel(c, [W.x0, W.z0], [W.x0, W.z1], 1, HOUSE.wallTop, P.wall)
  wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, HOUSE.wallTop, P.wall)
  floor(c, R.x0, R.z0, R.x1, R.z1, HOUSE.loftY, P.floor)

  // --- Pass 2: the steep roof, the gables, the porch's gable -------------------------------------------------------
  const main = gableRoof(c, HOUSE.roof, P.roof, (x, y, z) => !(porchCell(x, z) && y === HOUSE.roof.eaveY))
  for (const x of [W.x0, W.x1]) gableWall(c, 'x', x, [W.z0, W.z1], HOUSE.wallTop + 1, main.under, () => P.wall)
  // the storm porch: dressed cheeks, its own steep little gable (full under a stair, the ridge at y 6) into the slope
  for (const x of [PORCH.x0, PORCH.x1]) for (let z = PORCH.z0; z <= PORCH.z1; z++) {
    c.set(x, 0, z, P.plinth)
    for (let y = 1; y <= PORCH.top; y++) c.set(x, y, z, z === PORCH.z0 ? P.quoin : P.wall)
  }
  c.set(9, 0, 1, P.plinth)
  stair(c, 9, 0, 0, P.step, 'south')
  // round 2 (critic note 1): the porch comes to a point: one rake stair a side on the cheeks (y 4), the ridge one course
  // over x 9 (y 5, snow on it), calcite only under it on the front (9, 4, 0)
  for (let z = PORCH.z0; z <= PORCH.z1; z++) {
    stair(c, PORCH.x0, 4, z, P.roof.stairs, 'east'); stair(c, PORCH.x1, 4, z, P.roof.stairs, 'west')
    c.set(9, 5, z, P.roof.full)
  }
  c.set(9, 3, 0, P.lintel)
  c.set(9, 4, 0, P.wall)

  // --- Pass 3: openings ----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor, od = OPENINGS.foldDoor
  door(c, fd.at, fd.facing, fd.out, { wood: P.wood })
  door(c, od.at, od.facing, od.out, { wood: P.wood })
  if (level >= 2) { const bd = OPENINGS.backDoor; door(c, bd.at, bd.facing, bd.out, { wood: P.wood }) }
  c.set(9, 4, 1, 'lantern', { hanging: 'true', waterlogged: 'false' }) // hung under the porch's ridge, over the doorway's two clear blocks
  const ww = OPENINGS.weaverWindow
  lightBand(c, { axis: 'x', line: ww.z, u0: ww.x0, u1: ww.x1, y0: ww.y0, y1: ww.y1, lights: ww.lights }, { glass: P.glass, mullion: P.mullion, lintel: P.lintel })
  for (let x = ww.x0; x <= ww.x1; x++) { c.set(x, 0, 1, P.plinth); slab(c, x, 1, 1, P.capSlab, 'top') } // the stone sill on its footing
  for (const w of OPENINGS.windows) {
    window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
    const [x, y, z] = w.cells[w.cells.length - 1]
    if (!c.get(x, y + 1, z)?.id.includes('deepslate')) c.set(x, y + 1, z, P.lintel)
  }
  frontDormer(c, DORMER, P.roof, { cheek: P.post, infill: P.dormerFace, glass: P.glass, verge: P.verge, vergeTop: P.vergeTop, snow: P.snow })
  // round 3 (critic note 2): close the verge's apex notch under its ridge (4, 8, 1), so no birch shows above the face
  if (!c.get(DORMER.cx, DORMER.y0 + 3, DORMER.face - 1)) c.set(DORMER.cx, DORMER.y0 + 3, DORMER.face - 1, P.vergeTop)
  const st = OPENINGS.stairs
  for (const [x, y, z] of st.well) c.clear(x, y, z)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  railing(c, [[8, 5, 6], [9, 5, 6], [10, 5, 6]], P.wood)

  // --- Pass 4: depth and detail ----------------------------------------------------------------------------------
  quoins(c, [[W.x0, W.z0], [W.x1, W.z0], [W.x0, W.z1], [W.x1, W.z1]], 1, HOUSE.wallTop, P.quoin, P.quoinAlt)
  for (const x of [W.x0, W.x1]) for (let z = W.z0 + 1; z < W.z1; z++)
    if (c.name(x, HOUSE.loftY, z) === 'minecraft:calcite') c.set(x, HOUSE.loftY, z, P.course) // the loft floor's course on the gables
  // the stack astride the ridge: its breast up through the loft from the hearth, out through the roof
  for (const x of STACK.xs) for (const z of STACK.zs) for (let y = HOUSE.loftY; y <= STACK.y1; y++) c.set(x, y, z, stack)
  STACK.zs.forEach(z => STACK.xs.forEach(x => {
    if (x === STACK.fire[0] && z === STACK.fire[1]) c.set(x, STACK.y1 + 1, z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
    else if (z === STACK.fire[1]) slab(c, x, STACK.y1 + 1, z, P.capSlab)
  }))
  // the rakes: one clean steep verge each
  for (const x of HOUSE.rakes) rakeVerge(c, P.roof, 'x', x, [0, c.size[2] - 1])

  // --- Pass 5: interior (the shipped furniture, cell for cell) -----------------------------------------------------
  // the hearth in the west wall: dressed jambs and a lintel round the fire, the breast over it
  for (const z of [4, 6]) for (const y of [1, 2]) c.set(2, y, z, P.quoin)
  for (let z = 4; z <= 6; z++) c.set(2, 3, z, P.lintel)
  for (const f of FURNITURE) {
    if (f.part === 'loom') { c.free(f.pos); c.set(...f.pos, 'loom', { facing: f.facing }); continue }
    furnitureParts[f.part](c, f)
  }
  for (const [x, y, z, w] of FLEECES) c.set(x, y, z, typeof w === 'number' ? P.wool[w] : w)

  // --- Pass 6: ground: the fold, the woodpile ------------------------------------------------------------------
  const fg = FOLD.ground
  for (let x = fg.x0; x <= fg.x1; x++) for (let z = fg.z0; z <= fg.z1; z++) {
    const trodden = FOLD.trodden.some(([tx, tz]) => tx === x && tz === z)
    c.set(x, 0, z, trodden ? 'coarse_dirt' : 'grass_block', trodden ? {} : { snowy: 'false' })
  }
  const L = FOLD.lee
  const leeWall = (x, z) => { c.set(x, 0, z, field); for (let y = 1; y < L.top; y++) c.set(x, y, z, field); c.set(x, L.top, z, P.coping) }
  for (let x = L.back.x0; x <= L.back.x1; x++) leeWall(x, L.back.z)
  for (const [x, z] of L.returns) leeWall(x, z)
  const h = FOLD.hood
  for (let x = h.x0; x <= h.x1; x++) stair(c, x, h.y, h.z, P.roof.stairs, 'south')
  for (const [x, y, z] of FOLD.hay) c.set(x, y, z, 'hay_block', { axis: 'x' })
  railingRun(c, FOLD.fence, P.wood)
  for (let i = 0; i + 1 < FOLD.fence.length; i++) {
    const [ax, , az] = FOLD.fence[i], [bx, , bz] = FOLD.fence[i + 1]
    for (let x = Math.min(ax, bx); x <= Math.max(ax, bx); x++) for (let z = Math.min(az, bz); z <= Math.max(az, bz); z++) c.set(x, 0, z, P.plinth)
  }
  const g = FOLD.gate
  c.set(...g.at, `${P.wood}_fence_gate`, { facing: g.facing, in_wall: 'false', open: 'false', powered: 'false' })
  c.set(...FOLD.cornerLamp, 'lantern', { hanging: 'false', waterlogged: 'false' })
  const wp = WOODPILE
  for (let z = wp.z0; z <= wp.z1; z++) for (let y = 0; y <= wp.top - 1; y++) c.set(wp.x, y, z, P.firewood, { axis: 'z' }) // round 2: one wood, laid along the wall

  // --- Level II: the walled dye garden --------------------------------------------------------------------------
  if (level >= 2) {
    const G = GARDEN, gw = G.walls
    for (let x = gw.x0 + 1; x < gw.x1; x++) for (let z = gw.z0; z < gw.z1; z++) c.set(x, 0, z, 'grass_block', { snowy: 'false' })
    const ring = (x, z) => x === gw.x0 || x === gw.x1 || z === gw.z1
    for (let x = gw.x0; x <= gw.x1; x++) for (let z = gw.z0; z <= gw.z1; z++) {
      if (!ring(x, z)) continue
      const tall = G.piers.some(([px, pz]) => px === x && pz === z) || (z === G.back.z && x >= G.back.x0 && x <= G.back.x1)
      const top = tall ? G.back.top : gw.top
      const pier = G.piers.some(([px, pz]) => px === x && pz === z) && z === G.back.z
      c.set(x, 0, z, P.plinth)
      const slit = z === G.back.z && G.slits.includes(x) // round 2 (note 4): slits under the hood, the cloth and the house seen through
      for (let y = 1; y < top; y++) if (!(slit && y <= 2)) c.set(x, y, z, pier ? stack : slit && y === 3 ? P.quoin : P.wall)
      c.set(x, top, z, P.coping)
    }
    for (let z = G.walk.z0; z <= G.walk.z1; z++) c.set(G.walk.x, 0, z, P.gravel)
    for (let x = G.cross.x0; x <= G.cross.x1; x++) { c.set(x, 0, G.cross.z, P.gravel); c.set(x, 0, G.hood.z, P.gravel) }
    for (let x = G.court.x0; x <= G.court.x1; x++) for (let z = G.court.z0; z <= G.court.z1; z++) c.set(x, 0, z, P.gravel)
    for (const b of G.borders) for (let x = b.x0; x <= b.x1; x++) for (let z = b.z0; z <= b.z1; z++) {
      c.set(x, 0, z, 'grass_block', { snowy: 'false' })
      const row = b.plants[b.along === 'z' ? x : z], i = b.along === 'z' ? z - b.z0 : x - b.x0
      plantFlower(c, x, 1, z, row[i % row.length])
    }
    lampPost(c, G.lampPost[0], 1, G.lampPost[1], `${P.wood}_fence`, 2)
    // the drying hood on the tall back wall: a slate pent over a spruce rail, the dyed cloth hung from its face
    for (let x = G.hood.x0; x <= G.hood.x1; x++) {
      stair(c, x, G.hood.y, G.hood.z, P.roof.stairs, 'south')
      c.set(x, G.rail.y, G.rail.z, 'stripped_spruce_log', { axis: 'x' })
      c.set(x, G.rail.y, G.rail.z - 1, `${G.cloth[(x - G.hood.x0) % G.cloth.length]}_wall_banner`, { facing: 'north' })
      // round 3 (critic note 3): behind each slit a bolt of the same dye hangs from the rail to the ground, so the back
      // road sees colour through the slit (a stone-brick head over it, the plinth its sill)
      if (G.slits.includes(x)) for (let y = 1; y < G.rail.y; y++) c.set(x, y, G.rail.z, `${G.cloth[(x - G.hood.x0) % G.cloth.length]}_wool`)
    }
    c.set(8, 3, 9, 'lantern', { hanging: 'true', waterlogged: 'false' }) // under the back eave, by the garden door
  }

  // --- Pass 7: snow ------------------------------------------------------------------------------------------------
  const rake = x => HOUSE.rakes.includes(x)
  const dormer = (x, y, z) => x >= DORMER.cx - 2 && x <= DORMER.cx + 2 && z <= DORMER.face + 1 && y >= DORMER.y0 + 2
  snowLoad(c, P.roof, P.snow, { eaves: true, ridge: '2', tread: () => false,
    eaveSkip: (x, y, z) => rake(x) || porchCell(x, z) || dormer(x, y, z) || y !== HOUSE.roof.eaveY || z > 9 })
  const coped = (x, y, z) => c.name(x, y, z) === `minecraft:${P.coping}` && c.name(x, y + 1, z) === 'minecraft:air'
  for (let x = 0; x < c.size[0]; x++) for (let z = 0; z < c.size[2]; z++) for (let y = 2; y < c.size[1] - 1; y++)
    if (coped(x, y, z) && !(STACK.xs.includes(x) && STACK.zs.includes(z))) c.set(x, y + 1, z, P.snow, { layers: '2' })
  for (let x = OPENINGS.weaverWindow.x0; x <= OPENINGS.weaverWindow.x1; x++) c.set(x, 2, 1, P.snow, { layers: '1' })
  for (let z = WOODPILE.z0; z <= WOODPILE.z1; z++) c.set(WOODPILE.x, WOODPILE.top, z, P.snow, { layers: '1' })
  const bare = [[9, 0], [8, 0], [10, 0], [14, 1], [13, 1], [15, 1], [11, 0], [12, 0], [11, 1], [12, 1], [16, 1], [17, 1]] // rounds 2 and 3: no loose layers by the step or before the fold's kerb
  snowSkirt(c, P.snow, bare)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
