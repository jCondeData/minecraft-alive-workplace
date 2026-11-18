// Map Room, the recipe: assembles plan.mjs (the Lantern Tower) from the shared parts library (../parts) in passes
// (block-out and structure, openings, depth and detail, interior, ground, light) and returns the structure for the
// minecraft-builder skill's runner. Also writes furniture.json. CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, FURNITURE } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, wallPanel, window, door, quoins, boxPyramidRoof, shelfWall, furnitureParts,
  corbelRing, compassRose, lookoutRail, hood, ladderShaft, batter,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, M = MASSES
  const stone = mix(P.stone, 11)
  const S = M.shaft, R = M.room

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  plinth(c, 0, 0, 8, 8, stone) // the ring under the skirt and the shaft's footing
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  for (const [a, b] of [[[S.x0, S.z0], [S.x1, S.z0]], [[S.x0, S.z1], [S.x1, S.z1]], [[S.x0, S.z0], [S.x0, S.z1]], [[S.x1, S.z0], [S.x1, S.z1]]])
    wallPanel(c, a, b, S.y0, S.y1, stone)
  for (const y of M.floors.slice(1)) floor(c, R.x0, R.z0, R.x1, R.z1, y, P.floor)
  // the crown: corbels round the ring, the deck one block proud of the shaft all round
  corbelRing(c, 0, 0, 8, 8, M.corbelY, P.corbel)
  floor(c, 0, 0, 8, 8, M.deckY, P.deck)
  lookoutRail(c, 0, 0, 8, 8, M.lookout.y0, M.lookout.y1, { post: P.post, fence: `${P.wood}_fence` })
  // the lantern: posts at its corners, glass all round between them
  const N = M.lantern
  for (const [x, z] of [[N.x0, N.z0], [N.x1, N.z0], [N.x0, N.z1], [N.x1, N.z1]]) for (let y = M.lookout.y0; y <= M.lookout.y1; y++) c.set(x, y, z, P.post, { axis: 'y' })
  for (let y = M.lookout.y0; y <= M.lookout.y1; y++) {
    for (let x = N.x0 + 1; x < N.x1; x++) for (const z of [N.z0, N.z1]) c.set(x, y, z, P.glass)
    for (let z = N.z0 + 1; z < N.z1; z++) for (const x of [N.x0, N.x1]) c.set(x, y, z, P.glass)
  }
  // log mullions proud of the glass: a post in the middle of each lantern face but the door's
  for (let y = M.lookout.y0; y <= M.lookout.y1; y++) { c.set(4, y, N.z1, P.post, { axis: 'y' }); c.set(N.x0, y, 4, P.post, { axis: 'y' }); c.set(N.x1, y, 4, P.post, { axis: 'y' }) }
  // the balcony floor's lantern-side posts at the rail's front corners carry lanterns
  for (const x of [0, 8]) c.set(x, M.lookout.y1 + 0, 0, P.post, { axis: 'y' })
  const apex = boxPyramidRoof(c, M.roof, P.roof)
  // a plate over the balcony, from the lantern's set-back north wall to the eave: closes the roof space
  for (let x = N.x0; x <= N.x1; x++) if (!c.get(x, M.roof.y, 1)) c.set(x, M.roof.y, 1, P.deck)

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: P.skirt })
  const gd = OPENINGS.galleryDoor
  door(c, gd.at, gd.facing, 'north', { wood: P.wood })
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
  const L = OPENINGS.ladder
  ladderShaft(c, L.x, L.z, L.y0, L.y1, L.facing)

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  quoins(c, [[S.x0, S.z0], [S.x1, S.z0], [S.x0, S.z1], [S.x1, S.z1]], 1, S.y1, P.quoin, P.quoinAlt)
  // the splayed foot: stairs leaning on the shaft, open at the door's step
  batter(c, 0, 0, 8, 8, 1, P.skirt, (x, z) => z === 0 && x === fd.at[0])
  c.clear(fd.step[0], 1, fd.step[2])
  // a dark band at the store's floor: the shaft read in two stages
  for (let x = S.x0 + 1; x < S.x1; x++) for (const z of [S.z0, S.z1]) if (!c.name(x, 5, z).includes('pane')) c.set(x, 5, z, P.quoinAlt)
  for (let z = S.z0 + 1; z < S.z1; z++) for (const x of [S.x0, S.x1]) if (!c.name(x, 5, z).includes('pane')) c.set(x, 5, z, P.quoinAlt)
  hood(c, fd.at[0], 4, 0, P.roof) // a tiled hood and a lantern over the door
  c.set(apex[0], apex[1] + 1, apex[2], P.vane, { facing: 'up', powered: 'false', waterlogged: 'false' })

  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  compassRose(c, 4, 0, 4, P.rose)
  const parts = {
    ...furnitureParts,
    jobBlock: (cv, f) => { cv.free(f.pos); cv.set(...f.pos, f.block, f.block === 'cartography_table' ? {} : { facing: f.facing }) },
    shelves: (cv, f) => shelfWall(cv, f.cells, f.y0, f.y1, 'bookshelf'),
  }
  for (const f of FURNITURE) parts[f.part](c, f)
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `map_room_cut${cut}` : 'map_room' } }
}
