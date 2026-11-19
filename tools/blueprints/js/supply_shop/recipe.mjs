// The Supply Shop (supply_shop, _2, _3, _4; ROADMAP 49), the recipe: assembles plan.mjs from the shared parts library
// (../parts) in passes (block-out and structure, openings, depth and detail, interior, ground and light) for LEVEL = 1..4.
// Each level is the one below drawn by the same lines with its new mass grown on: II the Range behind, III the Wing
// east, IV the Cellar dug under the Wing (a LiftedCanvas). Writes furniture.json and, for IV, out/cellar.txt.
// Build: build.sh; CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZES, BASEMENT, PALETTE, SHOP, RANGE, WING, CELLAR, OPENINGS, FURNITURE, WING_SACKS, CELLAR_STOCK, CELLAR_LOGS, CELLAR_RACK, CELLAR_TABLE, CELLAR_SACKS } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, plinth, floor, post, beamRing, wallPanel, window, door, doorway, gableRoof, gableWall,
  bargeTrim, pitchDormer, flowerBed, furnitureParts, hangingPot, wallTrellis, gableHoist, jettyBay, caskStack, doubleChest,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level) {
  const c = level >= 4 ? new LiftedCanvas(SIZES[level], BASEMENT) : new Canvas(SIZES[level])
  const P = PALETTE
  const stone = mix(P.stone, 7)
  const S = SHOP, W = S.walls, R = S.room
  const log = axis => [P.frame, { axis }]

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // Shop: a dark deepslate plinth out under the bays, spruce boards, white calcite walls between spruce posts
  plinth(c, W.x0, S.jetty.z, W.x1, W.z1, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], 1, S.wallTop, P.infill)
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, S.wallTop, P.infill)
  wallPanel(c, [W.x0, W.z0], [W.x0, W.z1], 1, S.wallTop + 2, P.infill) // side walls up through the knee under the eave
  wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, S.wallTop + 2, P.infill)
  for (const x of S.frontPosts) post(c, x, W.z0, 1, S.wallTop, P.frame)
  for (const z of S.sidePosts) { post(c, W.x0, z, 1, S.wallTop, P.frame); post(c, W.x1, z, 1, S.wallTop, P.frame) }
  beamRing(c, W.x0, W.z0, W.x1, W.z1, S.beamY, P.frame)
  floor(c, R.x0, R.z0, R.x1, R.z1, S.beamY, P.ceiling) // the shop's ceiling, the attic floor

  // the proud frame: on a plinth course a block out from the calcite, posts and a wall plate along both sides and the
  // back (the Wing takes the east side's middle from III, the Range the back post at the door from II)
  const F = S.frame
  const inWing = (x, z) => level >= 3 && x === F.sides[1] && z >= WING.walls.z0 && z <= WING.walls.z1
  for (const x of F.sides) for (let z = W.z0; z <= F.back; z++) {
    if (inWing(x, z)) continue
    c.set(x, 0, z, stone)
    c.set(x, S.beamY, z, ...log('z'))
    if (F.sidePosts.includes(z)) post(c, x, z, 1, S.wallTop, P.frame)
  }
  for (let x = F.sides[0]; x <= F.sides[1]; x++) {
    if (inWing(x, F.back)) continue
    c.set(x, 0, F.back, stone)
    c.set(x, S.beamY, F.back, ...log('x'))
  }
  for (const x of F.backPosts) if (!inWing(x, F.back) && !(level >= 2 && x === 5)) post(c, x, F.back, 1, S.wallTop, P.frame)

  // the jetty: bay posts on the plinth's front edge, a beam across them, the street gable standing on it
  for (const x of S.jetty.posts) post(c, x, S.jetty.z, 1, S.wallTop, P.frame)
  for (let x = W.x0; x <= W.x1; x++) c.set(x, S.jetty.y, S.jetty.z, ...log('x'))

  // Level II: the Range behind (drawn before the roofs)
  if (level >= 2) range(c, P, stone)
  // Level III: the Wing east
  if (level >= 3) wing(c, P, stone)

  // roofs: the Shop's tall 45-degree roof, ridge to the street; the Range's and Wing's lower ones where nothing stands
  const main = gableRoof(c, S.roof, P.roof)
  const back = level >= 2 ? gableRoof(c, RANGE.roof, P.roof, (x, y, z) => z > S.frame.back && !c.get(x, y, z)) : null // from behind the shop's overhang
  const side = level >= 3 ? gableRoof(c, WING.roof, P.roof, (x, y, z) => !c.get(x, y, z)) : null

  // the street gable: the copper sign band on the jetty, studs either side of the taking-in door, a king post
  const [lx, ly] = S.loftWindow
  gableWall(c, 'z', S.jetty.z, [W.x0, W.x1], S.beamY + 1, main.under, (u, y) => {
    if (y === S.beamY + 1) return P.copper
    if (u === lx && (y === ly || y === ly + 1)) return P.glass // the loft window
    if (u === lx && y === ly + 2) return log('x') // its lintel
    if (u === lx && y > ly + 2) return log('y')
    return P.infill
  })
  // the back gable over the back wall (its lower courses hidden by the Range's roof from II)
  gableWall(c, 'z', W.z1, [W.x0, W.x1], S.beamY + 1, main.under, (u, y) => {
    if (u === 5 && (y === S.backDoor[1] || y === S.backDoor[1] + 1)) return null // the loading door
    if (u === 5 && y === S.backDoor[1] + 2) return log('x')
    if (u === 3 || u === 7 || (u === 5 && y >= 8)) return log('y')
    return P.infill
  })

  // from II the Range's attic runs under the shop's back overhang: close it there with a plain gable on the frame line
  if (level >= 2) gableWall(c, 'z', S.frame.back, [W.x0, W.x1], S.beamY + 1, main.under, (u, y) =>
    (u === 5 && (y === S.backDoor[1] || y === S.backDoor[1] + 1) ? null : P.infill)) // the loading door now opens into the Range's loft

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  // display windows out at z 0 (a stall board under glass, a copper hood), the jetty bay and front wall behind open
  for (const b of S.bays) {
    jettyBay(c, b.x0, b.x1, 1, S.wallTop, 0, 1, { board: P.board, glass: P.glass })
    for (let x = b.x0; x <= b.x1; x++) {
      c.set(x, 0, 0, stone)
      c.set(x, S.jetty.y, 0, P.copperStairs, { facing: 'south', half: 'bottom', shape: 'straight', waterlogged: 'false' })
      if (S.jetty.posts.includes(x)) { post(c, x, S.jetty.z, 1, S.wallTop, P.frame); continue }
      for (let y = 1; y <= S.wallTop; y++) c.clear(x, y, W.z0)
      c.set(x, 1, S.jetty.z, `${P.wood}_slab`, { type: 'top', waterlogged: 'false' }) // the display shelf
      c.set(x, 2, S.jetty.z, 'candle', { candles: String(2 + (x % 3)), lit: 'true', waterlogged: 'false' })
    }
  }
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: 'cobbled_deepslate_stairs' })
  c.set(fd.at[0], S.wallTop, fd.at[2], ...log('x')) // the lintel over the door (QaDoorwayClearanceGameTests)
  for (const w of OPENINGS.windows) {
    if ((w.from ?? 1) > level || (w.until ?? 9) < level) continue
    for (const d of w.dress === 'both' ? ['sill', 'shutters'] : [w.dress]) window(c, w.cells, w.out, d, { glass: P.glass, wood: P.wood })
  }
  if (level >= 2) {
    const bd = OPENINGS.backDoor
    door(c, bd.at, bd.facing, 'south', { wood: P.wood })
  }
  if (level >= 3) {
    const [dx, dy, dz] = WING.doorway
    doorway(c, WING.doorway, 2)
    c.set(dx, dy + 2, dz, P.infill)
    const ls = WING.ledgerShelf
    c.set(...ls.at, 'chiseled_bookshelf', { facing: ls.facing })
  }

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  // the hoist out of the apex with the shop's lantern on its rope; a lantern under the jetty over the door
  gableHoist(c, S.hoist.at, 'north', S.hoist.drop, { beam: P.frame })
  // the back gable's loading door: a spruce door over a slab ledge on the back plate, a landing inside, a hoist over it
  const [bx, by, bz] = S.backDoor
  door(c, S.backDoor, 'north', 'south', { wood: P.wood })
  c.set(bx, by - 1, bz - 1, P.floor)
  if (level === 1) {
    c.set(bx, by - 1, S.frame.back, `${P.wood}_slab`, { type: 'bottom', waterlogged: 'false' })
    gableHoist(c, S.backHoist.at, 'south', S.backHoist.drop, { beam: P.frame })
  }
  // a gabled dormer in each long slope, over the side windows: the sides' outline breaks above the eave
  const roofY = x => main.under[x] ?? 0
  for (const d of S.dormers) pitchDormer(c, { face: d.face, zc: d.zc, dir: d.dir, main: roofY }, { roof: P.roof, frame: P.frame, infill: P.infill, glass: P.glass })
  // a shadow line under the side eaves: the wall plates at x 0 and x 10 turned to upside-down stairs, stepped in
  for (const [x, inward] of [[S.frame.sides[0], 'east'], [S.frame.sides[1], 'west']])
    for (let z = W.z0; z <= S.frame.back; z++)
      if (c.name(x, S.beamY, z) === `minecraft:${P.frame}`) c.set(x, S.beamY, z, `${P.wood}_stairs`, { facing: inward, half: 'top', shape: 'straight', waterlogged: 'false' })
  c.set(fd.at[0], S.jetty.y - 1, S.jetty.z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // barge boards under the front rake (45 degrees: upside-down stairs)
  bargeTrim(c, 'z', S.roof.along[0], S.roof.across, main.under, `${P.wood}_stairs`)
  if (side) bargeTrim(c, 'z', WING.roof.along[0], WING.roof.across, side.under, `${P.wood}_stairs`)

  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  for (const f of FURNITURE) {
    if ((f.level ?? 1) > level) continue
    if (f.part === 'shopCounter') { c.free(f.pos); c.set(...f.pos, 'aliveworkplace:shop_counter', { facing: f.facing }) }
    else if (f.part === 'casks') caskStack(c, f.pos, f.facing, f.height, P.cask)
    else if (f.part === 'doubleChest') doubleChest(c, f.pos, f.facing, f.along)
    else if (f.part === 'hangingPot') hangingPot(c, f)
    else if (f.part === 'trellis') { if (!c.get(f.pos[0], f.pos[1] - 1, f.pos[2])) c.set(f.pos[0], f.pos[1] - 1, f.pos[2], 'grass_block', { snowy: 'false' }); wallTrellis(c, f) }
    else furnitureParts[f.part](c, f)
  }
  if (level >= 3) for (const [x, y, z, h] of WING_SACKS) for (let i = 0; i < h; i++) c.set(x, y + i, z, 'hay_block', { axis: 'y' })

  // --- Pass 5: ground and light -------------------------------------------------------------------------------------
  let dug = null
  if (level >= 4) dug = cellar(c, P, stone)
  return { c, dug }
}

// The Range (II): the shopkeeper's house behind the shop, a block narrower each side, under a lower roof.
function range(c, P, stone) {
  const { walls: W, room: R } = RANGE
  plinth(c, W.x0, W.z0 + 1, W.x1, W.z1, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  wallPanel(c, [W.x0, W.z0 + 1], [W.x0, W.z1], 1, RANGE.wallTop, P.infill)
  wallPanel(c, [W.x1, W.z0 + 1], [W.x1, W.z1], 1, RANGE.wallTop, P.infill)
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, RANGE.wallTop, P.infill)
  for (const x of [W.x0, W.x1]) post(c, x, W.z1, 1, RANGE.wallTop, P.frame)
  for (let z = W.z0 + 1; z <= W.z1; z++) for (const x of [W.x0, W.x1]) c.set(x, RANGE.beamY, z, P.frame, { axis: 'z' })
  for (let x = W.x0; x <= W.x1; x++) c.set(x, RANGE.beamY, W.z1, P.frame, { axis: 'x' })
  floor(c, R.x0, R.z0, R.x1, R.z1, RANGE.beamY, P.ceiling)
  // the back gable jettied a block out over the yard on a beam and two brackets
  const J = RANGE.jetty
  for (let x = W.x0; x <= W.x1; x++) c.set(x, RANGE.beamY, J.z, P.frame, { axis: 'x' })
  for (const x of J.brackets) c.set(x, RANGE.beamY - 1, J.z, `${P.wood}_stairs`, { facing: 'north', half: 'top', shape: 'straight', waterlogged: 'false' })
  const under = { 2: 5, 3: 6, 4: 7, 5: 8, 6: 7, 7: 6, 8: 5 }
  gableWall(c, 'z', J.z, [W.x0, W.x1], RANGE.beamY + 1, under, (u, y) => (u === 5 && y >= 6 ? [P.frame, { axis: 'y' }] : P.infill))
  for (let x = R.x0; x <= R.x1; x++) c.set(x, RANGE.beamY, W.z1, P.ceiling) // the loft floor out to the jettied gable
}

// The Wing (III): the seed and hardware room east of the shop, set a block back from its face, its own small gable
// to the street with a copper band like the shop's, a shop window under it.
function wing(c, P, stone) {
  const { walls: W, room: R } = WING
  plinth(c, W.x0 + 1, W.z0, W.x1, W.z1, stone)
  for (let z = SHOP.walls.z1 + 1; z <= W.z1; z++) c.set(W.x0, 0, z, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  wallPanel(c, [W.x0 + 1, W.z0], [W.x1, W.z0], 1, WING.wallTop, P.infill)
  wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, WING.wallTop + 1, P.infill)
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, WING.wallTop, P.infill)
  wallPanel(c, [W.x0, SHOP.walls.z1 + 1], [W.x0, W.z1], 1, WING.wallTop, P.infill)
  for (const x of [W.x0 + 1, W.x1]) post(c, x, W.z0, 1, WING.wallTop, P.frame)
  for (const z of WING.sidePosts) post(c, W.x1, z, 1, WING.wallTop, P.frame)
  post(c, W.x0, W.z1, 1, WING.wallTop, P.frame)
  for (let x = W.x0 + 1; x <= W.x1; x++) c.set(x, WING.beamY, W.z0, P.copper) // the copper band
  for (let x = W.x0; x <= W.x1; x++) c.set(x, WING.beamY, W.z1, P.frame, { axis: 'x' })
  for (let z = W.z0 + 1; z < W.z1; z++) c.set(W.x1, WING.beamY, z, P.frame, { axis: 'z' })
  for (let z = SHOP.walls.z1 + 1; z < W.z1; z++) c.set(W.x0, WING.beamY, z, P.frame, { axis: 'z' })
  floor(c, R.x0 + 1, R.z0, R.x1, R.z1, WING.beamY, P.ceiling)
  // the street window: a stall board under glass
  for (let x = WING.window.x0; x <= WING.window.x1; x++) {
    c.set(x, 1, W.z0, P.board)
    for (let y = 2; y <= WING.wallTop; y++) c.set(x, y, W.z0, P.glass)
  }
  const under = { 11: 5, 12: 6, 13: 7, 14: 6, 15: 5 }
  gableWall(c, 'z', W.z0, [11, 15], WING.beamY + 1, under, () => P.infill)
  gableWall(c, 'z', W.z1, [11, 15], WING.beamY + 1, under, () => P.infill)
}

// The Cellar (IV): dug 4 deep under the Wing, a ladder down its east wall, lanterns hung from the Wing's floor.
function cellar(c, P, stone) {
  const { walls: W, room: R, ladder: L } = CELLAR
  for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++) {
    c.set(x, -4, z, P.cellarFloor)
    const ring = x === W.x0 || x === W.x1 || z === W.z0 || z === W.z1
    for (let y = -3; y <= -1; y++) if (ring) c.set(x, y, z, stone)
  }
  for (let y = L.y0; y <= L.y1; y++) c.set(L.x, y, L.z, 'ladder', { facing: L.facing, waterlogged: 'false' })
  for (const [x, y, z] of CELLAR.lamps) c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  for (const s of CELLAR_STOCK) doubleChest(c, s.pos, s.facing, s.along)
  const K = CELLAR_LOGS
  for (let z = K.z0; z <= K.z1; z++) for (const y of [-3, -2]) c.set(K.x, y, z, `${P.wood}_log`, { axis: 'x' })
  const Q = CELLAR_RACK // a rack: fence legs, two slatted shelves of top slabs, sacks on the bottom one
  for (const z of Q.legs) c.set(Q.x, -3, z, `${P.wood}_fence`)
  for (let z = Q.z0; z <= Q.z1; z++) c.set(Q.x, -2, z, `${P.wood}_slab`, { type: 'top', waterlogged: 'false' })
  c.set(Q.x, -3, 6, 'hay_block', { axis: 'z' })
  for (const [x, y, z] of CELLAR_TABLE) { c.set(x, y, z, `${P.wood}_fence`); c.set(x, y + 1, z, 'brown_carpet') }
  for (const [x, y, z, h] of CELLAR_SACKS) for (let i = 0; i < h; i++) c.set(x, y + i, z, 'hay_block', { axis: 'y' })
  return [R.x0, R.z0, R.x1, R.z1]
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  fs.mkdirSync(path.join(here, 'out'), { recursive: true })
  if (dug) fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(','))
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const id = level === 1 ? 'supply_shop' : `supply_shop_${level}`
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
