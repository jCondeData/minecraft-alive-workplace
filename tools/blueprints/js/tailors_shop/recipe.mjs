// The Tailor's Shop (tailors_shop, tailors_shop_2; ROADMAP 49), the recipe: assembles plan.mjs from the shared parts
// library (../parts) in passes (block-out and structure, openings, depth and detail, interior, ground) for LEVEL = 1 or
// 2. Level II is Level I drawn by the same lines with the Dyer's Tower grown out of its back-east corner (the shop's
// roof gives way only where the tower stands). Writes furniture.json (every furniture spot the parts recorded) for
// rebuild.py. Build: build.sh; CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZES, PALETTE, SHOP, TOWER, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, slab, plinth, floor, post, beamRing, wallPanel, window, door, gableRoof, gableWall, bargeTrim, chimney,
  flowerBed, furnitureParts, jettyBeam, pentice, stair, clothBolts, boltRack, dressForm, postBanner, steepCap,
} from '../parts/index.mjs'
import { doubleChest } from '../parts/tinker.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level) {
  const c = new Canvas(SIZES[level])
  const P = PALETTE
  const stone = mix(P.stone, 11)
  const S = SHOP, W = S.walls, R = S.room
  const T = TOWER, TW = T.walls
  const inTower = (x, z) => level >= 2 && x >= TW.x0 && x <= TW.x1 && z >= TW.z0 && z <= TW.z1

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // Shop: a deepslate plinth, spruce boards, white plaster walls framed in dark oak, a beam ring and two tie beams
  plinth(c, W.x0, W.z0, W.x1, W.z1, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], 1, S.wallTop, P.infill)
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, S.wallTop, P.infill)
  wallPanel(c, [W.x0, W.z0], [W.x0, W.z1], 1, S.wallTop, P.infill)
  wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, S.wallTop, P.infill)
  for (const x of S.frontPosts) post(c, x, W.z0, 1, S.wallTop, P.frame)
  for (const x of S.backPosts) post(c, x, W.z1, 1, S.wallTop, P.frame)
  for (const z of S.sidePosts) { post(c, W.x0, z, 1, S.wallTop, P.frame); post(c, W.x1, z, 1, S.wallTop, P.frame) }
  post(c, W.x1, S.eastStud, 1, S.wallTop, P.frame)
  beamRing(c, W.x0, W.z0, W.x1, W.z1, S.beamY, P.frame)
  for (const z of S.tieBeams) for (let x = R.x0; x <= R.x1; x++) c.set(x, S.beamY, z, P.frame, { axis: 'x' })

  // the jetty: the front gable stands a block out on a beam and carved brackets over the recessed shopfront
  jettyBeam(c, 'x', S.jetty.z, [W.x0, W.x1], S.jetty.y, P.frame, `${P.wood}_stairs`, S.jetty.brackets, 'south')

  // Level II: the Dyer's Tower (drawn before the roofs and trims, which then leave its cells alone)
  if (level >= 2) tower(c, P, stone)

  // the shop's roof: 45 degrees, ridge along z, everywhere but where the tower stands
  const main = gableRoof(c, S.roof, P.roof, (x, y, z) => !inTower(x, z))

  // Level II: where the shop's east slope meets the tower, a cricket: a course of tiles rising to the tower wall, so
  // the two make a valley against it (a joint, not a slope cut off by a wall)
  if (level >= 2) for (let z = TW.z0; z <= TW.z1; z++) stair(c, TW.x0 - 1, main.under[TW.x0 - 1] + 1, z, P.roof.stairs, 'east')

  // gables: the jettied front one (studs either side of a two-high window), the back one flush over the back wall
  const pick = (u, y) => {
    if (u === 3 || u === 7) return [P.frame, { axis: 'y' }]
    if (u === 5 && y === 9) return [P.frame, { axis: 'y' }] // the king post's head under the ridge
    return P.infill
  }
  gableWall(c, 'z', S.jetty.z, [W.x0, W.x1], S.beamY + 1, main.under, pick)
  gableWall(c, 'z', W.z1, [W.x0, W.x1], S.beamY + 1, main.under, pick)

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  // the shopfront: dark stall boards under three-high display windows, the door with a fanlight and a lintel over it
  for (const sw of OPENINGS.shopWindows) for (let x = sw.x0; x <= sw.x1; x++) {
    c.set(x, 1, W.z0, `${P.wood}_planks`)
    for (let y = 2; y <= S.wallTop; y++) c.set(x, y, W.z0, P.glass)
  }
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood })
  c.set(...fd.fanlight, P.glass)
  c.set(fd.at[0], S.wallTop, fd.at[2], P.frame, { axis: 'x' })
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  // the pressing stove's stack up the back, its flue in the wall behind the stove
  chimney(c, 3, W.z1 + 1, 0, 11, stone)
  for (let y = 1; y <= S.wallTop; y++) c.set(3, y, W.z1, stone)
  // barge boards under the rakes (45 degrees: upside-down stairs)
  bargeTrim(c, 'z', S.roof.along[0], S.roof.across, main.under, `${P.wood}_stairs`)
  bargeTrim(c, 'z', S.roof.along[1], S.roof.across, main.under, `${P.wood}_stairs`)
  // the shop porch under the jetty: paved in half steps, a lantern under the beam over the door, livery banners
  for (let x = OUTSIDE.porch.x0; x <= OUTSIDE.porch.x1; x++) slab(c, x, 0, OUTSIDE.porch.z, P.paving, 'bottom')
  c.set(fd.at[0], S.jetty.y - 1, S.jetty.z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  postBanner(c, [W.x0, 3, W.z0], 'north', P.banner)
  postBanner(c, [W.x1, 3, W.z0], 'north', P.banner)

  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  for (const f of FURNITURE) {
    if ((f.level ?? 1) > level) continue
    if (f.part === 'bolts') clothBolts(c, f, `${P.wood}_planks`)
    else if (f.part === 'boltRack') boltRack(c, f)
    else if (f.part === 'dressForm') dressForm(c, f)
    else if (f.part === 'loom') { c.free(f.pos); c.set(...f.pos, 'loom', { facing: f.facing }) }
    else if (f.part === 'doubleChest') doubleChest(c, f.pos, f.facing, f.along)
    else furnitureParts[f.part](c, f)
  }

  // --- Pass 5: ground -----------------------------------------------------------------------------------------------
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  const rk = OUTSIDE.dryingRack
  if (level === rk.onlyLevel) {
    for (const z of rk.posts) for (let y = 0; y <= rk.top; y++) c.set(rk.x, y, z, `${P.wood}_fence`)
    for (const [z, colour] of rk.cloths) {
      c.set(rk.x, rk.top, z, `${P.wood}_fence`)
      for (let k = 1; k <= rk.drop; k++) c.set(rk.x, rk.top - k, z, `${colour}_wool`)
    }
  }
  return c
}

// The Dyer's Tower (Level II): a stone foot framed at its corners under a pent skirt, a tall drying shaft (its cloths
// hung inside behind a glazed band), the cutting room on top, a steep cap; a ladder of nine rungs up the shaft's south
// wall to the cutting room's hatch; its west wall is the shop's east wall up to the beam.
function tower(c, P, stone) {
  const T = TOWER, { x0, x1, z0, z1 } = T.walls, r = T.room
  const ring = (x, z) => x === x0 || x === x1 || z === z0 || z === z1
  const corner = (x, z) => (x === x0 || x === x1) && (z === z0 || z === z1)
  const shopWall = (x, z) => x === x0 && z <= SHOP.walls.z1 // the shop's east wall, already drawn below the beam
  plinth(c, x0, z0, x1, z1, stone)
  // the stone foot, its corners dark oak posts like the shop's
  for (let y = 1; y <= T.baseTop; y++)
    for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
      if (!ring(x, z) || shopWall(x, z)) continue
      if (corner(x, z)) c.set(x, y, z, P.frame, { axis: 'y' })
      else c.set(x, y, z, stone)
    }
  // beam rings at the floor lines (the shop's own beam stays where it runs along the shared wall)
  for (const y of T.beams)
    for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
      if (!ring(x, z) || (y === SHOP.beamY && shopWall(x, z))) continue
      c.set(x, y, z, P.frame, { axis: corner(x, z) ? 'y' : (z === z0 || z === z1) ? 'x' : 'z' })
    }
  // a pent skirt of tiles round the foot at the beam line, falling away from the tower (not on the shop's side)
  pentice(c, T.skirt.north, SHOP.beamY, P.roof, 'south')
  pentice(c, T.skirt.east, SHOP.beamY, P.roof, 'west')
  pentice(c, T.skirt.south, SHOP.beamY, P.roof, 'north')
  // the timbered storeys: corner posts, plaster panels, studs up the plain bays (the ladder's backing; over the roof)
  for (const [ya, yb] of [[6, 8], [10, 11]])
    for (let y = ya; y <= yb; y++)
      for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
        if (!ring(x, z)) continue
        if (corner(x, z)) c.set(x, y, z, P.frame, { axis: 'y' })
        else c.set(x, y, z, P.infill)
      }
  for (let y = 6; y <= 8; y++) { c.set(T.cap.cx, y, z1, P.frame, { axis: 'y' }); c.set(x0, y, T.cap.cz, P.frame, { axis: 'y' }) }
  // the cutting room's floor (the ladder's hatch left open)
  const L = T.ladder
  for (const y of T.floors)
    for (let x = r.x0; x <= r.x1; x++) for (let z = r.z0; z <= r.z1; z++) {
      if (x === L.x && z === L.z) continue
      c.set(x, y, z, P.floor)
    }
  for (let y = L.y0; y <= L.y1; y++) c.set(L.x, y, L.z, 'ladder', { facing: L.facing, waterlogged: 'false' })
  for (const w of OPENINGS.towerWindows) for (const [x, y, z] of w.cells) c.set(x, y, z, P.glass)
  // the doorway from the shop
  const [dx, dy, dz] = T.doorway
  c.clear(dx, dy, dz); c.clear(dx, dy + 1, dz)
  // the steep cap on the wall line (no eave to hang over the shop's roof), a lightning rod for a finial
  steepCap(c, T.cap.cx, T.cap.cz, T.cap.half, T.cap.y0, P.roof, { apex: [P.finial, { facing: 'up', powered: 'false', waterlogged: 'false' }] })
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const id = level === 1 ? 'tailors_shop' : `tailors_shop_${level}`
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
