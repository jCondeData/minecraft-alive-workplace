// The Market Stall (market_stall, _2, _3; ROADMAP 49), the recipe: assembles plan.mjs from the shared parts library
// (../parts) in passes (block-out and structure, openings, depth and detail, interior and goods, ground) for LEVEL = 1,
// 2 or 3. Each level is the one before drawn by the same lines with its addition grown beside or behind it: II the
// lock-up shop east of the booth (the two roofs meeting in a valley), III the lock-up run back into a storeroom. Writes
// furniture.json (every furniture spot the parts recorded) for rebuild.py. Build: build.sh; CUT=<y> builds a cutaway.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZES, PALETTE, BOOTH, SHOP_BOARD, LOCKUP, OPENINGS, FURNITURE } from './plan.mjs'
import {
  Canvas, post, gableRoof, gableWall, door, window, furnitureParts, shopBoard, slab, stair,
} from '../parts/index.mjs'
import { doubleChest } from '../parts/tinker.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level) {
  const c = new Canvas(SIZES[level])
  const P = PALETTE
  const B = BOOTH, [bx0, bx1] = B.posts.x, [bz0, bz1] = B.posts.z
  const wall = y => (y === 0 ? P.boards : P.infill) // a plank sill course, limewashed panels over it

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // the booth: four log posts on the ground, plates round the top, boarded sides and back
  for (const x of B.posts.x) for (const z of B.posts.z) post(c, x, z, 0, B.top, P.log)
  for (let x = bx0 + 1; x < bx1; x++) for (const z of B.posts.z) c.set(x, B.top, z, P.log, { axis: 'x' })
  for (let z = bz0 + 1; z < bz1; z++) for (const x of B.posts.x) c.set(x, B.top, z, P.log, { axis: 'z' })
  for (let y = 0; y < B.top; y++) {
    for (let x = bx0 + 1; x < bx1; x++) c.set(x, y, bz1, wall(y))
    for (let z = bz0 + 1; z < bz1; z++) for (const x of B.posts.x) c.set(x, y, z, wall(y))
  }
  for (let x = bx0 + 1; x < bx1; x++) for (let z = bz0 + 1; z < bz1; z++) c.set(x, B.top, z, P.boards) // the ceiling

  // Level II: the lock-up's frame and walls (drawn before the roofs, so the booth's east eave gives way to it)
  const L = LOCKUP, [lx0, lx1] = L.posts.x, lz0 = L.posts.z[0], lz1 = level >= 2 ? L.back[Math.min(level, 3)] : 0
  if (level >= 2) {
    const postsZ = level >= 3 ? [lz0, L.midPost, lz1] : [lz0, lz1]
    for (const x of L.posts.x) for (const z of postsZ) post(c, x, z, 0, L.top, P.log)
    for (let x = lx0 + 1; x < lx1; x++) for (const z of [lz0, lz1]) c.set(x, L.top, z, P.log, { axis: 'x' })
    for (let z = lz0 + 1; z < lz1; z++) for (const x of L.posts.x) if (!postsZ.includes(z)) c.set(x, L.top, z, P.log, { axis: 'z' })
    for (let y = 0; y < L.top; y++) {
      for (let x = lx0 + 1; x < lx1; x++) for (const z of [lz0, lz1]) c.set(x, y, z, wall(y))
      for (let z = lz0 + 1; z < lz1; z++) for (const x of L.posts.x) if (!postsZ.includes(z)) c.set(x, y, z, wall(y))
      if (level >= 3) for (let x = lx0 + 1; x < lx1; x++) c.set(x, y, L.midPost, wall(y)) // the old back wall: a partition
    }
    if (level >= 3) for (let x = lx0 + 1; x < lx1; x++) c.set(x, L.top, L.midPost, P.log, { axis: 'x' }) // its tie beam
  }

  // the roofs: the booth's 45-degree gable (ridge y 7, a verge past each gable); the lock-up's lower one (ridge y 6)
  // beside it, its west eave meeting the booth's east eave in a valley (x 7 the booth's, x 8 the lock-up's)
  const booth = gableRoof(c, B.roof, P.roof)
  let lock = null
  if (level >= 2) {
    lock = gableRoof(c, { ...L.roof, along: [1, lz1] }, P.roof, (x, y, z) => !(x === lx0 - 1 && z <= B.roof.along[1]))
  }

  // gables: the booth's front one over the shop board (a king post under the ridge) and its closed back one; the
  // lock-up's front and back ones
  const boothPick = (u) => (u === B.kingPost ? [P.log, { axis: 'y' }] : P.infill)
  gableWall(c, 'z', B.frontGable, [bx0, bx1], B.top + 1, booth.under, boothPick) // jettied over the canopy board
  gableWall(c, 'z', bz1, [bx0, bx1], B.top + 1, booth.under, () => P.infill)
  if (level >= 2) {
    gableWall(c, 'z', 1, [lx0, lx1], L.top + 1, lock.under, () => P.infill)
    gableWall(c, 'z', lz1, [lx0, lx1], L.top + 1, lock.under, () => P.infill)
  }

  // --- Pass 2: openings ---------------------------------------------------------------------------------------------
  // the shop board: the counter sill, the folded-down ledge, the propped canopy and the striped valance on two poles
  const sb = SHOP_BOARD
  const board = shopBoard(c, { axis: 'x', line: sb.line, a0: sb.a0, a1: sb.a1, y: sb.y, canopyY: sb.canopyY, gap: sb.gap, boardTo: sb.boardTo, out: 'north' },
    { board: P.board, sill: P.boards, cloth: P.cloth, prop: P.prop })
  c.set(sb.gap[0], sb.y, sb.line, P.gate, { facing: 'north', open: 'false', in_wall: 'false', powered: 'false' }) // the way in

  // the booth's back: a two-high window with shutters behind the shelf (the back face is a shopfront too)
  window(c, OPENINGS.boothBack, 'south', 'shutters', { glass: P.glass, wood: 'spruce' })
  for (const [x, y, z] of OPENINGS.sideHatch) c.clear(x, y, z) // the side hatch
  // king posts break both booth gables; the trader's sign hangs on the back one too
  for (let y = B.top + 1; y < booth.under[B.kingPost]; y++) c.set(B.kingPost, y, bz1, P.log, { axis: 'y' })
  c.set(B.kingPost, 5, bz1 + 1, P.sign, { facing: 'south' })

  if (level >= 2) {
    // a door from the aisle behind the counter, through a little vestibule between the booth and the lock-up
    const ad = OPENINGS.aisleDoor, vx = ad.vestibule
    for (let y = 0; y <= L.top; y++) for (const z of [bz0, bz1]) c.set(vx, y, z, wall(y))
    for (let y = 2; y <= L.top; y++) c.set(vx, y, ad.at[2], wall(y))
    door(c, ad.at, ad.facing, 'east', { wood: P.door })
    c.clear(...ad.through); c.clear(ad.through[0], ad.through[1] + 1, ad.through[2])
    window(c, OPENINGS.vestibuleWindow, 'north', null, { glass: P.glass })
    // the lock-up's gables: a king post and a little window in each
    for (const gz of [1, lz1]) { c.set(10, L.top + 2, gz, P.log, { axis: 'y' }); c.set(10, L.top + 1, gz, P.glass) }
    for (const x of [9, 11]) { stair(c, x, 0, 1, `${P.door}_stairs`, 'south', 'top'); c.set(x, 1, 1, 'potted_red_tulip') } // flower sills
    window(c, OPENINGS.lockupWindows, 'north', null, { glass: P.glass })
    door(c, OPENINGS.lockupDoor.at, OPENINGS.lockupDoor.facing, 'north', { wood: P.door })
    window(c, OPENINGS.sideWindow, 'east', 'shutters', { glass: P.glass, wood: 'spruce' })
    if (level === 2) window(c, OPENINGS.backWindow[2], 'south', null, { glass: P.glass })
    if (level >= 3) {
      const [px, py, pz] = OPENINGS.partitionDoorway
      c.clear(px, py, pz); c.clear(px, py + 1, pz)
      door(c, OPENINGS.backDoor.at, OPENINGS.backDoor.facing, 'south', { wood: P.door })
      for (const [x, y, z] of OPENINGS.storeWindows) c.set(x, y, z, P.glass)
      // the street sees the growth: the shop's sign hung on the lock-up's front gable (the stock display is east)
      c.set(10, L.top + 2, 0, P.sign, { facing: 'north' })
    }
  }

  // --- Pass 3: depth and detail -------------------------------------------------------------------------------------
  // the trader's sign on the jettied gable
  c.set(B.kingPost, 5, B.frontGable - 1, P.sign, { facing: 'north' })
  if (level >= 2) for (let x = lx0 + 1; x < lx1; x++) c.set(x, L.top, B.frontGable, P.log, { axis: 'x' }) // the lock-up's jetty beam

  // --- Pass 4: interior and goods -------------------------------------------------------------------------------------
  // cloth laid out on the ledge, alternating
  board.ledge.forEach(([x, y, z], i) => c.set(x, y, z, P.carpets[i % 2]))
  if (level >= 2) { const [x, y, z] = FURNITURE.find(f => f.part === 'counter').pos; c.clear(x, y + 1, z - 1); c.clear(x, y, z - 1) } // the buyer's place
  for (const f of FURNITURE) {
    if ((f.level ?? 1) > level || (f.until ?? 9) < level) continue
    if (f.part === 'pot' || f.part === 'block') { c.free(f.pos); c.set(...f.pos, f.block) }
    else if (f.part === 'ledge') { c.free(f.pos); c.set(...f.pos, P.board, { facing: 'east', half: 'top', open: 'false', powered: 'false', waterlogged: 'false' }) }
    else if (f.part === 'shelf') for (const [x, y, z] of f.cells) slab(c, x, y, z, P.slab, 'top')
    else if (f.part === 'lamp') { c.free(f.pos); c.set(...f.pos, 'lantern', { hanging: 'false', waterlogged: 'false' }); c.spot('lamp', f.pos, 'north', [[0, 0, 0]], 'spruce') }
    else if (f.part === 'counter') { c.set(...f.pos, P.counter, { facing: f.facing }) }
    else if (f.part === 'doubleChest') doubleChest(c, f.pos, f.facing, f.along)
    else if (f.part === 'chest') { c.free(f.pos); c.set(...f.pos, 'chest', { facing: f.facing, type: 'single', waterlogged: 'false' }) }
    else { const n = c.spots.length; furnitureParts[f.part](c, f); if (f.plain) c.spots.length = n }
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
  const id = level === 1 ? 'market_stall' : `market_stall_${level}`
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
