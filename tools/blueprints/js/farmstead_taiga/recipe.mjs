// Farmstead, the taiga look (ROADMAP 49.10e, as 49.10a): assembles plan.mjs from the parts library (../parts) in passes
// (block-out and roofs, walls, openings, depth and detail, interior, cellar, ground and fields) for LEVEL = 1..3, each
// level the one before plus what it adds (II the passage, the cross barn and the second field; III the root cellar and
// its hatch, nothing else above ground). Writes furniture.json (the furniture spots) and, for III, out/cellar.txt.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  SIZE, BASEMENT, IDS, PALETTE, ROOFS, HOUSE, BARN, PENT, HEARTH, SVALGANG, OPENINGS, FURNITURE, FIELDS, FENCES, OUTSIDE,
} from './plan.mjs'
import {
  LiftedCanvas, mix, stair, slab, window, door, bargeTrim, railing, furnitureParts, cellar, hatchLadder,
  fieldPlot, kerbFence, scarecrow, composter,
} from '../parts/index.mjs'
import { unionRoof, fillUp } from '../parts/manor_cold.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const has = (o, level) => (o.level ?? 1) <= level
const k = (x, y, z) => `${x},${y},${z}`

export function drawLevel(level) {
  const c = new LiftedCanvas(SIZE[level], BASEMENT[level] ?? 0)
  const P = PALETTE
  const two = level >= 2
  const footing = mix(P.footing, 5), byre = mix(P.byre, 7), chimney = mix(P.chimney, 9), kerb = mix(P.kerb, 11)
  const byreFloor = mix(P.byreFloor, 13), cellarWall = mix(P.cellarWall, 3)
  const log = axis => [P.log, { axis }]
  const H = HOUSE, B = BARN, S = SVALGANG, G = B.gablet
  const svEnd = S.z1[two ? 2 : 1]

  // --- Pass 1: block-out: footings, floors, the deck, the roofs ---------------------------------------------------
  for (let x = H.x0; x <= H.x1; x++) for (let z = H.z0; z <= H.z1; z++) {
    const edge = x === H.x0 || x === H.x1 || z === H.z0 || z === H.z1
    c.set(x, 0, z, edge ? footing : P.floor)
  }
  // the back porch (I) / the passage's floor (II): fieldstone flags at z 9
  for (let x = H.x0; x <= H.x1; x++) c.set(x, 0, 9, footing)
  if (!two) stair(c, 4, 0, 10, P.weather, 'north') // the porch's step down to the back
  // the svalgang's deck and its step up at the front
  for (let z = S.z0; z <= svEnd; z++) c.set(S.x, 0, z, P.deck)
  stair(c, ...S.step, `${P.wood}_stairs`, 'south')
  if (two) {
    for (let x = B.x0; x <= B.x1; x++) for (let z = B.z0; z <= B.z1; z++) {
      const edge = x === B.x0 || x === B.x1 || z === B.z0 || z === B.z1
      c.set(x, 0, z, edge ? byre : (x >= 3 && x <= 4 && z <= 15 ? P.threshing : byreFloor))
    }
  }

  // the roofs as one surface; left out: the hearth breast's weathering, the stack, the gablet and the hay door
  const skip = new Set([k(0, 4, 4), k(0, 4, 5), k(0, 4, 6), k(HEARTH.stack.x, 5, HEARTH.stack.z), k(HEARTH.stack.x, 5, HEARTH.stack.z + 1)])
  if (two) for (const key of [k(3, 7, 20), k(4, 7, 20), k(5, 7, 20), k(3, 8, 19), k(5, 8, 19), k(4, 6, 21)]) skip.add(key)
  const masses = [ROOFS.lodge, ...(two ? [ROOFS.barn] : [])]
  unionRoof(c, masses, P.roof, { keep: (x, y, z) => !skip.has(k(x, y, z)) })
  const roofY = (m, u) => m.eaveY + Math.min(u - m.across[0], m.across[1] - u)
  const underL = {}, underB = {}
  for (let u = 0; u <= 8; u++) underL[u] = roofY(ROOFS.lodge, u)
  for (let u = 9; u <= 21; u++) underB[u] = roofY(ROOFS.barn, u)
  if (two) { c.set(4, 8, 10, P.roof.full); c.set(4, 7, 9, P.roof.full) } // the lodge's ridge run on into the barn's slope (the valley's crown), closed under
  if (two) {
    // the gablet over the hay door: the back wall raised round it, its own little roof into the main slope
    for (const x of [G.x0, G.x1]) c.set(x, 7, G.z, ...log('x'))
    c.set(4, G.eaveY, G.z, ...log('x'))
    for (let z = G.back; z <= G.z + 1; z++) {
      stair(c, G.x0, G.eaveY, z, P.roof.stairs, 'east')
      stair(c, G.x1, G.eaveY, z, P.roof.stairs, 'west')
      c.set(4, G.ridgeY, z, P.roof.full)
    }
  }

  // --- Pass 2: walls, carried up to the roof over them ---------------------------------------------------------------
  // the lodge: spruce logs lying along each wall on the footing, the corners alternating course by course, a pale
  // stripped band across both gables at y 5
  const lodgeWall = (x, y, z) => {
    const corner = (x === H.x0 || x === H.x1) && (z === H.z0 || z === H.z1)
    const along = corner ? (y % 2 ? 'x' : 'z') : (x === H.x0 || x === H.x1) ? 'z' : 'x'
    if (y === H.band && !corner && (z === H.z0 || z === H.z1)) return [P.band, { axis: 'x' }]
    return log(along)
  }
  const ring = (b) => {
    const cells = []
    for (let x = b.x0; x <= b.x1; x++) for (let z = b.z0; z <= b.z1; z++)
      if (x === b.x0 || x === b.x1 || z === b.z0 || z === b.z1) cells.push([x, z])
    return cells
  }
  // the hearth first (its stones are the wall there): the fire's surround in the west wall, the breast outside, the stack
  const Hh = HEARTH
  for (const z of Hh.zs) for (let y = 0; y <= Hh.breastTop; y++) c.set(Hh.x, y, z, chimney)
  stair(c, Hh.x, Hh.breastTop + 1, Hh.zs[0], P.weather, 'south')
  stair(c, Hh.x, Hh.breastTop + 1, Hh.zs[1], P.weather, 'east')
  stair(c, Hh.x, Hh.breastTop + 1, Hh.zs[2], P.weather, 'north')
  for (const z of [Hh.zs[0], Hh.zs[2]]) for (let y = 1; y <= 4; y++) c.set(H.x0, y, z, chimney)
  for (let y = 2; y <= Hh.stack.top; y++) c.set(Hh.stack.x, y, Hh.stack.z, chimney)
  for (let y = 5; y < Hh.stack.top; y++) c.set(Hh.stack.x, y, Hh.stack.z + 1, chimney) // the stack two wide through the slope
  slab(c, Hh.stack.x, Hh.stack.top, Hh.stack.z + 1, P.roof.slab) // its cap beside the smoke
  c.set(Hh.stack.x, Hh.stack.top + 1, Hh.stack.z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  fillUp(c, ring(H), 1, (x, y, z) => lodgeWall(x, y, z))
  // the passage's end walls (II) / the back corners' log ends (I): logs along z at x 1 and 7, z 9, up to the roof
  fillUp(c, [[H.x0, 9], [H.x1, 9]], 1, () => log('z'))
  // tie beams across the hall (the lamps hang from them) and over the passage
  for (const z of [...H.tieBeams, ...(two ? [9] : [])]) for (let x = H.room.x0; x <= H.room.x1; x++) c.set(x, 5, z, ...log('x'))

  if (two) {
    // the cross barn: a fieldstone byre storey, a stripped band at y 4, logs above (along each wall) up to the roof
    const barnWall = (x, y, z) => {
      if (y <= B.stoneTop) return byre
      const gable = x === B.x0 || x === B.x1
      const corner = gable && (z === B.z0 || z === B.z1)
      if (y === B.band) return [P.band, { axis: gable && !corner ? 'z' : 'x' }]
      return log(corner ? (y % 2 ? 'x' : 'z') : gable ? 'z' : 'x')
    }
    fillUp(c, ring(B), 1, barnWall)
    for (const z of B.tieBeams) for (let x = B.room.x0; x <= B.room.x1; x++) c.set(x, 5, z, ...log('x'))
  }

  // --- Pass 3: openings ----------------------------------------------------------------------------------------
  const O = OPENINGS
  for (const o of [O.frontDoor, O.backDoor, O.passageWest, O.passageEast, O.barnDoor, O.barnSideDoor, O.hayDoor, O.chamberDoor]) {
    if (!has(o, level)) continue
    door(c, o.at, o.facing, o.out, { wood: P.wood, step: o.step ?? null, stepStair: P.weather })
  }
  if (two) {
    const wd = O.wagonDoors
    wd.at.forEach((at, i) => door(c, at, wd.facing, wd.out, { wood: P.wood, hinge: i ? 'right' : 'left' }))
    for (let x = 4; x <= 7; x++) c.set(x, 3, B.z1, ...log('x')) // the wagon doors' lintel
    for (const [x, , z] of wd.at) stair(c, x, 0, z + 1, P.weather, 'north')
    for (const [cells, out] of B.windows) window(c, cells, out, null, { glass: P.glass })
    for (const x of [B.x0, B.x1]) for (const y of [7, 8]) c.set(x, y, 15, ...log('y')) // the loft lights' mullions
  }
  for (const w of O.windows) window(c, w.cells, w.out, w.dress ?? null, { glass: P.glass, wood: P.wood })

  // --- Pass 4: depth and detail ---------------------------------------------------------------------------------
  // saddle notches: the logs cross out past the lodge's corners (the front's as the pent's piers to z 0, the west's to
  // x 0); the back east corner's log end stands down the svalgang as its post
  fillUp(c, [[H.x0, 0], [H.x1, 0]], 1, () => log('z'))
  fillUp(c, [[0, H.z0], [0, H.z1]], 1, () => log('x'))
  for (const [x, z] of [[H.x0, 0], [H.x1, 0], [0, H.z0], [0, H.z1], [H.x0, 9], [H.x1, 9]]) c.set(x, 0, z, footing)
  // the pent across the front between the piers, a lantern hung under it at the door
  for (let x = PENT.x0; x <= PENT.x1; x++) stair(c, x, PENT.y, PENT.z, P.roof.stairs, 'south')
  c.set(...PENT.lamp, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // barge boards under the rakes (the lodge's front; I's back; the barn's two gables)
  bargeTrim(c, 'z', 0, [1, 7], underL, P.barge)
  if (!two) bargeTrim(c, 'z', 9, [1, 7], underL, P.barge)
  // a spruce finial on each ridge end
  c.set(4, underL[4] + 1, 0, `${P.wood}_fence`)
  // the svalgang: a log plate under the eave on posts (the back corner's log end at z 8 in I)
  for (let z = 0; z <= svEnd; z++) c.set(S.x, S.plateY, z, ...log('z'))
  for (const z of S.posts[two ? 2 : 1]) for (let y = 1; y < S.plateY; y++) c.set(S.x, y, z, ...log('y'))
  if (two) {
    // the svalgang's pent along the barn's east gable, the same eave line as the lodge's
    for (let z = S.pentZ[0]; z <= S.pentZ[1]; z++) stair(c, S.x, 4, z, P.roof.stairs, 'west')
    // log ends at the barn's corners (the log storey's courses crossing out)
    for (const y of [5, 6]) for (const z of [B.z0, B.z1]) for (const x of [0, 8]) c.set(x, y, z, ...log('x'))
    for (const y of [4, 5]) for (const x of [B.x0, B.x1]) c.set(x, y, 21, ...log('z'))
    bargeTrim(c, 'x', 0, [10, 20], underB, P.barge)
    bargeTrim(c, 'x', 8, [10, 20], underB, P.barge)
    for (const x of [0, 8]) c.set(x, underB[15] + 1, 15, `${P.wood}_fence`)
    c.set(4, G.eaveY, G.z + 1, 'lantern', { hanging: 'true', waterlogged: 'false' }) // a lamp hung under the gablet's peak over the hay door
  }

  // --- Pass 5: interior (the shipped furniture, cell for cell) ------------------------------------------------------
  c.clear(H.x0, 1, Hh.stack.z) // the fire's mouth in the west wall
  for (const f of FURNITURE) {
    if (!has(f, level)) continue
    if (f.part === 'composter') composter(c, f)
    else furnitureParts[f.part](c, f)
  }
  if (two) {
    // the second Farmer's chamber under the loft: board walls, its door from the threshing floor
    const K = B.chamber, L = B.loft
    for (let x = B.room.x0; x <= K.doorX; x++) for (let y = 1; y < L.y; y++) if (!c.get(x, y, K.wallZ)) c.set(x, y, K.wallZ, P.boards)
    for (let z = K.wallZ; z <= K.z1; z++) for (let y = 1; y < L.y; y++) if (!c.get(K.wallX, y, z)) c.set(K.wallX, y, z, P.boards)
    // the loft on the ladder, its rail, hay up there clear of the hay door
    for (let x = B.room.x0; x <= B.room.x1; x++) for (let z = L.z0; z <= L.z1; z++) c.set(x, L.y, z, P.floor)
    for (let y = 1; y <= B.ladder.y1; y++) c.set(B.ladder.x, y, B.ladder.z, 'ladder', { facing: 'east', waterlogged: 'false' })
    railing(c, [[3, 6, 16], [4, 6, 16], [5, 6, 16], [6, 6, 16]], P.wood)
    for (const [x, y, z] of [[2, 6, 18], [2, 6, 19], [3, 6, 19], [2, 7, 19], [5, 6, 19], [6, 6, 19], [6, 6, 18], [6, 7, 19]])
      c.set(x, y, z, 'hay_block', { axis: 'y' })
    // the sheep pen down the east wall: a fence front with a gate, hay in the crib, a sunken trough
    const Pn = B.pen
    for (let z = Pn.z0; z <= Pn.z1; z++) {
      if (z === Pn.gateZ) c.set(Pn.x0, 1, z, `${P.wood}_fence_gate`, { facing: 'west', in_wall: 'false', open: 'false', powered: 'false' })
      else c.set(Pn.x0, 1, z, `${P.wood}_fence`)
    }
    c.set(Pn.x1, 1, Pn.z1, `${P.wood}_fence`)
    c.set(Pn.x1, 1, Pn.z0, 'hay_block', { axis: 'y' })
    c.set(Pn.x1, 0, 13, 'water', { level: '0' })
    for (const z of [Pn.z0 + 1, 13]) c.set(Pn.x1, 0, z, 'coarse_dirt')
    // the tool wall and the stores on the west: a board bench under hooks, a pumpkin, hay by the ladder
    slab(c, 2, 1, 11, `${P.wood}_slab`, 'top')
    for (const z of [11, 13]) c.set(2, 2, z, 'tripwire_hook', { facing: 'east', attached: 'false', powered: 'false' })
    c.set(2, 1, 12, 'pumpkin')
    c.set(2, 1, 13, 'hay_block', { axis: 'y' })
    // hay by the side door, clear of the wagon doors and the chamber's door
    for (const [x, y, z] of [[6, 1, 15], [6, 1, 16], [6, 2, 16], [5, 1, 16]]) c.set(x, y, z, 'hay_block', { axis: 'y' })
    // the passage: a lantern hung from its tie beam
    c.set(4, 4, 9, 'lantern', { hanging: 'true', waterlogged: 'false' })
  }

  // --- Pass 6: the cellar (III) -------------------------------------------------------------------------------------
  let dug = null
  if (level >= 3) {
    dug = cellar(c, H.x0, H.z0, H.x1, H.z1, 4, cellarWall, P.cellarFloor)
    hatchLadder(c, 2, 2, -3, 0, 'south', P.wood)
    // a root cellar: produce bins under a board shelf down the east wall, log posts at its ends, hay at the back
    ;[['pumpkin', 3], ['melon', 4], ['pumpkin', 5]].forEach(([id, z]) => { c.set(6, -3, z, id); slab(c, 6, -2, z, `${P.wood}_slab`, 'top') })
    for (const z of [2, 6]) for (let y = -3; y <= -1; y++) c.set(6, y, z, ...log('y'))
    for (const [x, y, z] of [[3, -3, 7], [4, -3, 7], [5, -3, 7], [6, -3, 7], [5, -2, 7]]) c.set(x, y, z, 'hay_block', { axis: 'y' })
  }

  // --- Pass 7: ground and fields ------------------------------------------------------------------------------------
  for (const [x, z] of OUTSIDE.ferns) { c.set(x, 0, z, P.soil); c.set(x, 1, z, P.fern) }
  const woodpile = (w, slabTop) => {
    for (let z = w.z0; z <= w.z1; z++) {
      for (let y = 0; y <= w.top; y++) c.set(w.x, y, z, P.firewood, { axis: 'x' })
      if (slabTop) slab(c, w.x, w.top + 1, z, P.roof.slab)
    }
  }
  woodpile(OUTSIDE.firewood, false)
  if (two) woodpile(OUTSIDE.barnWood, true)
  const field = (F, footing) => {
    const crop = (x, z) => { const r = F.rows[z]; return r ? [r[0], r[1][(x - F.x0) % r[1].length]] : null }
    fieldPlot(c, F, {
      water: (x, z) => z === F.ditchZ && !(F.scarecrow && x === F.scarecrow[0] && z === F.scarecrow[1]),
      footing: (x, z) => (F.scarecrow && x === F.scarecrow[0] && z === F.scarecrow[1] ? footing : null),
      crop,
    })
  }
  field(FIELDS[1], 'mossy_cobblestone')
  kerbFence(c, FENCES[1], 0, kerb, `${P.wood}_fence`)
  c.set(...FENCES.gate1, `${P.wood}_fence_gate`, { facing: 'north', in_wall: 'false', open: 'false', powered: 'false' })
  const [sx, sz] = FIELDS[1].scarecrow
  scarecrow(c, [sx, 1, sz], 'north', { leg: `${P.wood}_fence`, body: 'hay_block', arms: `${P.wood}_fence`, hat: 'gray_carpet' })
  if (two) {
    field(FIELDS[2])
    kerbFence(c, FENCES[2], 0, kerb, `${P.wood}_fence`)
  }
  for (const [x, z] of [...FENCES.lamps[1], ...(two ? FENCES.lamps[2] : [])]) c.set(x, 2, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
  return { c, dug }
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = drawLevel(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  fs.mkdirSync(path.join(here, 'out'), { recursive: true })
  if (dug) fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(','))
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
