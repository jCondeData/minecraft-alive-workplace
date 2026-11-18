// The School (the Move Tutor's), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes
// (block-out and structure, roofs, openings, depth and detail, interior, ground, light) and returns the structure for
// the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts recorded) for
// tools/blueprints/rebuild.py. Build: see package.json; CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE, JOB } from './plan.mjs'
import {
  Canvas, mix, plinth, floor, post, beamRing, wallPanel, window, door, doorway, gableRoof, gableWall, catslideRoof,
  bargeTrim, flowerBed, lampPost, furnitureParts, stair, sparringRing, moonWindow, strawTarget,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = mix(P.stone, 11)
  const paving = mix(P.paving, 5)
  const G = MASSES.gate, CT = MASSES.court, H = MASSES.hall
  const hw = H.walls, hr = H.room
  const empty = (x, y, z) => !c.get(x, y, z)

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // Hall: a stone plinth, white panels between dark posts, a beam ring, a knee wall under the eaves
  plinth(c, hw.x0, hw.z0, hw.x1, hw.z1, stone)
  floor(c, hr.x0, hr.z0, hr.x1, hr.z1, 0, P.floor)
  wallPanel(c, [hw.x0, hw.z0], [hw.x1, hw.z0], 1, H.wallTop, P.infill)
  wallPanel(c, [hw.x0, hw.z1], [hw.x1, hw.z1], 1, H.wallTop, P.infill)
  wallPanel(c, [hw.x0, hw.z0], [hw.x0, hw.z1], 1, H.wallTop, P.infill)
  wallPanel(c, [hw.x1, hw.z0], [hw.x1, hw.z1], 1, H.wallTop, P.infill)
  for (const x of H.frontPosts) post(c, x, hw.z0, 1, H.wallTop, P.frame)
  beamRing(c, hw.x0, hw.z0, hw.x1, hw.z1, H.beamY, P.frame)
  wallPanel(c, [hw.x0 + 1, hw.z0], [hw.x1 - 1, hw.z0], H.knee, H.knee, P.infill)
  wallPanel(c, [hw.x0 + 1, hw.z1], [hw.x1 - 1, hw.z1], H.knee, H.knee, P.infill)

  // Gate: two stone piers, a dark lintel and a ceiling over the passage, a timber storey under the steep roof
  for (const [a, b] of G.piers) c.fill(a, 0, G.z0, b, G.pierTop, G.z1, stone)
  floor(c, G.passage[0], G.z0, G.passage[1], G.z1, 0, paving)
  for (const z of [G.z0, G.z1]) for (let x = G.passage[0]; x <= G.passage[1]; x++) c.set(x, G.pierTop, z, P.frame, { axis: 'x' })
  floor(c, G.passage[0], G.z0 + 1, G.passage[1], G.z1 - 1, G.pierTop, P.ceiling)
  beamRing(c, G.piers[0][0], G.z0, G.piers[1][1], G.z1, G.pierTop + 1, P.frame)

  // Court: paving with the sparring ring inlaid round the post
  sparringRing(c, { ...CT.ring, y: 0, x0: CT.x0, x1: CT.x1, z0: CT.z0, z1: CT.z1 }, { inner: P.ring.inner, border: P.ring.border, paving })

  // Cloisters: a white outer wall on a plinth, framed; a colonnade of posts on the court side; boards underfoot
  for (const k of MASSES.cloisters) {
    const r = k.roof
    const under = {}; for (let u = r.across[0]; u <= r.across[1]; u++) under[u] = r.ridgeY - Math.abs(u - r.ridge)
    plinth(c, k.wall, k.z0, k.wall, k.z1, stone)
    floor(c, Math.min(...k.walk), k.z0 + 1, Math.max(...k.walk), k.z1, 0, P.floor)
    floor(c, k.colonnade, k.z0, k.colonnade, k.z1, 0, stone)
    wallPanel(c, [k.wall, k.z0], [k.wall, k.z1], 1, under[k.wall] - 1, P.infill)
    for (const z of [4, 6, 8, 10]) post(c, k.colonnade, z, 1, 3, P.frame)
    // a board ceiling over the walk at y 4 (the attic above it shut): the loggia stays three deep, a room open on one side
    floor(c, Math.min(...k.walk), k.z0 + 1, Math.max(...k.walk), k.z1, 4, P.ceiling)
    // the front end wall under the lean-to, its corner on the colonnade's first post
    const ends = k.side === 'west' ? [k.wall + 1, k.colonnade - 1] : [k.colonnade + 1, k.wall - 1]
    plinth(c, ends[0], k.z0, ends[1], k.z0, stone)
    for (let x = ends[0]; x <= ends[1]; x++) for (let y = 1; y < under[x]; y++) c.set(x, y, k.z0, P.infill)
  }

  // --- Pass 2: roofs ------------------------------------------------------------------------------------------------
  // the gate's steep hero roof first, then the hall's broad one, the cloisters' lean-tos only where nothing is drawn
  const gate = gableRoof(c, G.roof, P.roof)
  const hall = gableRoof(c, H.roof, P.roof, empty)
  for (const k of MASSES.cloisters) catslideRoof(c, k.roof, P.roof, empty)

  // the gate's gables: a tie beam, studs either side of the moon window, white panels
  const gx0 = G.piers[0][0], gx1 = G.piers[1][1]
  for (const z of [G.z0, G.z1]) {
    gableWall(c, 'z', z, [gx0, gx1], G.pierTop + 2, gate.under, (u, y) => {
      if (u === 6 || u === 10) return [P.frame, { axis: 'y' }]
      return P.infill
    })
  }
  for (const x of [gx0, gx1]) for (let z = G.z0 + 1; z < G.z1; z++) c.set(x, G.pierTop + 1, z, P.frame, { axis: 'z' })
  // gate's attic floor over the piers (closes the timber storey)
  floor(c, gx0 + 1, G.z0 + 1, gx1 - 1, G.z1 - 1, G.pierTop + 1, P.ceiling)
  moonWindow(c, 'z', G.z0, G.moon[0], G.moon[1], { glass: P.glass, frame: P.frame })
  c.set(8, 10, G.z1, P.glass)
  for (const x of [gx0, gx1]) for (let z = G.z0 + 1; z < G.z1; z++) for (let y = G.pierTop + 2; y < G.roof.eaveY + 1; y++) c.set(x, y, z, P.infill)

  // the hall's gables: a stud each side of the gable window
  for (const x of [hw.x0, hw.x1]) {
    gableWall(c, 'x', x, [hw.z0, hw.z1], H.knee, hall.under, (u, y) => {
      if (y === H.knee) return [P.frame, { axis: 'z' }]
      if (u === 14 || u === 16) return [P.frame, { axis: 'y' }]
      return P.infill
    })
  }
  // where the cloister roofs meet the hall's front slope, tiles under the eave so no quarter gap shows
  for (const k of MASSES.cloisters) for (let u = k.roof.across[0]; u <= k.roof.across[1]; u++) {
    const y = k.roof.ridgeY - Math.abs(u - k.roof.ridge)
    for (let yy = H.roof.eaveY; yy <= y; yy++) if (empty(u, yy, 11)) c.set(u, yy, 11, P.roof.full)
  }

  // --- Pass 3: openings ---------------------------------------------------------------------------------------------
  const gw = OPENINGS.gateway
  for (const [x, y, z] of gw.steps) stair(c, x, y, z, 'stone_brick_stairs', 'south')
  const hd = OPENINGS.hallDoor
  door(c, hd.at, hd.facing, hd.out, { wood: P.wood })
  c.set(hd.at[0], 0, hd.at[2], 'polished_andesite') // a threshold
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: ['potted_red_tulip', 'potted_oxeye_daisy'] })

  // --- Pass 4: depth and detail -------------------------------------------------------------------------------------
  // the shelf screen between the lecture room and the quarters: books low, boards above, a tie beam on top
  const sc = H.screen
  for (let z = sc.z0; z <= sc.z1; z++) {
    c.set(sc.x, 1, z, 'bookshelf'); c.set(sc.x, 2, z, 'bookshelf'); c.set(sc.x, 3, z, `${P.wood}_planks`)
  }
  for (let z = hw.z0 + 1; z < hw.z1; z++) c.set(sc.x, H.beamY, z, P.frame, { axis: 'z' })
  doorway(c, OPENINGS.screenDoorway.at, OPENINGS.screenDoorway.height)
  // barge boards under the overhanging rakes: the gate front and back (steep: slabs), the hall's gables (stairs)
  bargeTrim(c, 'z', G.roof.along[0], G.roof.across, gate.under, `${P.wood}_stairs`)
  bargeTrim(c, 'z', G.roof.along[1], G.roof.across, gate.under, `${P.wood}_stairs`)
  bargeTrim(c, 'x', H.roof.along[0], H.roof.across, hall.under, `${P.wood}_stairs`)
  bargeTrim(c, 'x', H.roof.along[1], H.roof.across, hall.under, `${P.wood}_stairs`)
  // the outer frame, a block proud of the white panels on a stone kerb: posts and a beam on the west and east (the
  // cloisters' posts up to their eaves, the hall's to its beam) and along the back; a chimney up the back's middle
  for (const [x] of [[0], [16]]) {
    for (let z = 4; z <= 19; z++) c.set(x, 0, z, stone)
    for (const z of [4, 8]) post(c, x, z, 1, 5, P.frame)
    for (const z of [12, 15, 18]) post(c, x, z, 1, H.wallTop, P.frame)
    for (let z = 12; z <= 18; z++) if (empty(x, H.beamY, z)) c.set(x, H.beamY, z, P.frame, { axis: 'z' })
  }
  for (let x = 1; x <= 15; x++) c.set(x, 0, 19, stone)
  for (const x of [1, 4, 12, 15]) post(c, x, 19, 1, H.wallTop, P.frame)
  for (let x = 1; x <= 15; x++) if (x < 7 || x > 9) c.set(x, H.beamY, 19, P.frame, { axis: 'x' })
  c.fill(7, 1, 19, 9, 3, 19, stone)
  stair(c, 7, 4, 19, 'stone_brick_stairs', 'east'); stair(c, 9, 4, 19, 'stone_brick_stairs', 'west')
  for (let y = 1; y <= hall.ridgeY + 1; y++) c.set(8, y, 19, stone)
  c.set(8, hall.ridgeY + 2, 19, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  // red cloth hung on the gate piers either side of the passage, facing the road
  c.set(6, 3, 0, `${P.accent}_wall_banner`, { facing: 'north' })
  c.set(10, 3, 0, `${P.accent}_wall_banner`, { facing: 'north' })

  // --- Pass 5: interior (the plan's furniture and the post) -----------------------------------------------------------
  const extra = {
    target: (cv, f) => strawTarget(cv, f.pos, { bale: 'hay_block', face: 'target' }),
  }
  for (const f of FURNITURE) (extra[f.part] ?? furnitureParts[f.part])(c, f)
  c.set(...JOB.at, JOB.block, { facing: JOB.facing })

  // --- Pass 6: ground and light ---------------------------------------------------------------------------------------
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  for (const [x, y, z] of OUTSIDE.lampPosts) lampPost(c, x, y, z, `${P.wood}_fence`, 2)
  c.set(8, 4, 2, 'lantern', { hanging: 'true', waterlogged: 'false' }) // in the gate passage, under its ceiling
  c.set(8, 4, 11, 'lantern', { hanging: 'true', waterlogged: 'false' }) // over the hall door, under the eave
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `school_cut${cut}` : 'school' } }
}
