// Farmstead, savanna look (49.10e), the recipe: assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, roof, openings, depth and detail, interior, cellar, ground and field) for LEVEL = 1..3, each
// level the one before plus what it adds (II the passage, the grain house, the veranda run on along it and the second
// field; III the granite root cellar and its hatch, nothing else above ground). Writes furniture.json and, for III,
// out/cellar.txt. Build all three: build.sh. CUT=<y> cuts everything above y.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  SIZE, BASEMENT, IDS, PALETTE, HALL, HIP, STACK, VERANDA, STOEP, FIELD, FIELD2, PASSAGE, BARN, BARN_HIP, WOODSTORE,
  CELLAR, WAGON_HOOD, OPENINGS, FURNITURE, CROPS, OUTSIDE,
} from './plan.mjs'
import {
  LiftedCanvas, mix, stair, slab, plinth, floor, wallPanel, window, door, post, beamRing, chimney, hearthFire, railingRun,
  furnitureParts, cellar, hatchLadder, hipRoof, fieldPlot, kerbFence, scarecrow, composter, eaveBrackets,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const POT = { facing: 'north', cracked: 'false', waterlogged: 'false' }

export function drawLevel(level) {
  const c = new LiftedCanvas(SIZE[level], BASEMENT[level] ?? 0)
  const P = PALETTE, H = HALL, B = BARN
  const mud = mix(P.mud, 6), terra = mix(P.plinth, 7), kerb = mix(P.kerb, 9)
  const two = level >= 2
  const has = o => (o.level ?? 1) <= level && level <= (o.until ?? 9)
  const log = axis => [P.frame, { axis }]
  const sidesOf = (x0, z0, x1, z1) => [[[x0, z0], [x1, z0]], [[x0, z1], [x1, z1]], [[x0, z0], [x0, z1]], [[x1, z0], [x1, z1]]]
  const corner = (x, z, x0, z0, x1, z1) => (x === x0 || x === x1) && (z === z0 || z === z1)
  const band = (x0, z0, x1, z1, y, colours) => { // a painted course round a wall ring, the corners left to the posts
    for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++)
      if ((x === x0 || x === x1 || z === z0 || z === z1) && !corner(x, z, x0, z0, x1, z1)) c.set(x, y, z, colours[(x + z) % 2])
  }

  // --- Pass 1: block-out and structure -----------------------------------------------------------------------------
  // The hall: a terracotta plinth, a mud-brick floor, mud walls to y 4 with the painted band, the grey acacia top
  // plate at y 5 (the eave's height), posts at the corners, tie beams across the room for the hung lamps.
  plinth(c, H.x0, H.z0, H.x1, H.z1, terra)
  floor(c, H.room.x0, H.room.z0, H.room.x1, H.room.z1, 0, P.floor)
  for (const [a, b] of sidesOf(H.x0, H.z0, H.x1, H.z1)) wallPanel(c, a, b, 1, H.paintY - 1, mud)
  band(H.x0, H.z0, H.x1, H.z1, H.paintY, P.paint)
  beamRing(c, H.x0, H.z0, H.x1, H.z1, H.beamY, P.frame)
  for (const [x, z] of [[H.x0, H.z0], [H.x1, H.z0], [H.x0, H.z1], [H.x1, H.z1]]) post(c, x, z, 1, H.beamY, P.frame)
  for (const z of H.tieBeams) for (let x = H.room.x0; x <= H.room.x1; x++) c.set(x, H.beamY, z, ...log('x'))
  // the front face limed white over a red dado (the homestead's painted wall), the band above it
  for (let x = H.x0 + 1; x < H.x1; x++) {
    c.set(x, 1, H.z0, P.red)
    for (let y = 2; y < H.paintY; y++) c.set(x, y, H.z0, P.lime)
  }
  // the hearth: a mud-brick surround in the west wall, the fire in it, the stack outside
  for (let z = STACK.z - 1; z <= STACK.z + 1; z++) for (let y = 1; y < H.paintY; y++) c.set(H.x0, y, z, P.stack)
  // the back stoep (I) / the passage floor (II) under the hall's back eave
  for (let x = H.x0; x <= H.x1; x++) c.set(x, 0, PASSAGE.z, two && (x === H.x0 || x === H.x1) ? terra(x, 0, PASSAGE.z) : P.deck)
  // the front stoep under the front eave
  for (let x = STOEP.x0; x <= STOEP.x1; x++) c.set(x, 0, STOEP.z, P.stoep)
  // the veranda's deck along the field, the step up at the front
  const vEnd = VERANDA.z1[two ? 2 : 1]
  for (let z = VERANDA.z0; z <= vEnd; z++) c.set(VERANDA.x, 0, z, P.deck)
  stair(c, ...OPENINGS.galleryStep, P.step, 'south')

  // The grain house (II): two storeys of mud on a terracotta plinth, a beam ring at the loft floor, a painted band, a
  // top plate under the eave, posts at the corners and mid-sides; the threshing floor; the loft on its ladder; the
  // second Farmer's chamber under the loft.
  if (two) {
    plinth(c, B.x0, B.z0, B.x1, B.z1, terra)
    floor(c, B.room.x0, B.room.z0, B.room.x1, B.room.z1, 0, P.barnFloor)
    for (const [a, b] of sidesOf(B.x0, B.z0, B.x1, B.z1)) {
      wallPanel(c, a, b, 1, B.ringY - 1, mud)
      wallPanel(c, a, b, B.ringY + 1, B.paintY - 1, mud)
    }
    band(B.x0, B.z0, B.x1, B.z1, B.paintY, P.paint)
    beamRing(c, B.x0, B.z0, B.x1, B.z1, B.ringY, P.frame)
    beamRing(c, B.x0, B.z0, B.x1, B.z1, B.wallTop, P.frame)
    for (const [x, z] of [[B.x0, B.z0], [B.x1, B.z0], [B.x0, B.z1], [B.x1, B.z1]]) post(c, x, z, 1, B.wallTop, P.frame)
    for (const z of B.midPosts) for (const x of [B.x0, B.x1]) { post(c, x, z, 1, B.ringY - 1, P.frame); post(c, x, z, B.ringY + 1, B.wallTop - 1, P.frame) }
    for (const z of B.tieBeams) for (let x = B.room.x0; x <= B.room.x1; x++) c.set(x, B.ringY, z, ...log('x'))
    const L = B.loft
    floor(c, B.room.x0, L.z0, B.room.x1, L.z1, L.y, P.loftFloor)
    const ld = OPENINGS.ladder
    for (let y = ld.y0; y <= ld.y1; y++) c.set(ld.x, y, ld.z, 'ladder', { facing: ld.facing, waterlogged: 'false' })
    railingRun(c, [[3, L.y + 1, L.z0], [6, L.y + 1, L.z0]], P.wood)
    const K = B.chamber
    for (let x = B.room.x0; x <= K.doorX; x++) for (let y = 1; y < L.y; y++) c.set(x, y, K.wallZ, P.partition, { axis: 'y' })
    for (let z = K.z0; z <= K.z1; z++) for (let y = 1; y < L.y; y++) c.set(K.wallX, y, z, P.partition, { axis: 'y' })
    // the passage's end walls (mud under the hall's back eave), a door in each
    for (const x of [H.x0, H.x1]) for (let y = 1; y < HIP.eaveY; y++) c.set(x, y, PASSAGE.z, mud)
  }

  // --- Pass 2: the roofs: the hall's golden hip, the grain house's higher one behind it ------------------------------
  hipRoof(c, { x0: HIP.x0, z0: HIP.z0, x1: HIP.x1, z1: HIP.z1, eaveY: HIP.eaveY }, P.roof)
  if (two) hipRoof(c, { x0: BARN_HIP.x0, z0: BARN_HIP.z0, x1: BARN_HIP.x1, z1: BARN_HIP.z1, eaveY: BARN_HIP.eaveY }, P.roof)
  // the veranda's thatch run on along the grain house at the hall's eave height (II)
  if (two) for (let z = B.z0; z <= vEnd; z++) stair(c, VERANDA.x, VERANDA.y, z, P.roof.stairs, 'west')
  // the clay pots on the points
  c.set(...HIP.finial, P.finial, POT)
  if (two) c.set(...BARN_HIP.finial, P.finial, POT)
  // the stack: mud brick past the ridge, a stepped breast, its fire on top
  chimney(c, STACK.x, STACK.z, 0, STACK.top, P.stack, { breast: { axis: 'z', top: STACK.breastTop, stair: P.stackStair } })

  // --- Pass 3: openings ----------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, fd.out, { wood: P.door, step: fd.step, stepStair: P.step })
  const bd = OPENINGS.backDoor
  door(c, bd.at, bd.facing, bd.out, { wood: P.door })
  // the front door's painted head: red over the door, the band above
  c.set(fd.at[0], 3, H.z0, P.red)
  if (two) {
    for (const o of [OPENINGS.barnDoor, OPENINGS.barnSideDoor, OPENINGS.chamberDoor, OPENINGS.hayDoor]) door(c, o.at, o.facing, o.out, { wood: P.door })
    for (const [x, y, z, f, o] of OPENINGS.passageDoors.at) door(c, [x, y, z], f, o, { wood: P.door })
    stair(c, ...OPENINGS.passageStep.at, P.step, 'east')
    const wd = OPENINGS.wagonDoors
    wd.at.forEach((at, i) => door(c, at, wd.facing, wd.out, { wood: P.door, hinge: i ? 'right' : 'left' }))
    for (let x = 4; x <= 6; x++) c.set(x, 3, B.z1, ...log('x')) // the wagon doors' lintel
    for (const [x, , z] of wd.at) stair(c, x, 0, z + 1, P.step, 'north')
    c.set(4, 3, B.z0, P.red) // the barn door's painted head
  }
  for (const w of OPENINGS.windows) if (has(w)) {
    window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
    if (w.out === 'north' && w.cells[0][2] === H.z0) c.set(w.cells[0][0], 1, H.z0, P.red) // the red sill under the front lights
  }

  // --- Pass 4: depth and detail --------------------------------------------------------------------------------------
  // the veranda: acacia posts off the walking line, brackets under the eave over the walk
  for (const z of VERANDA.posts[two ? 2 : 1]) post(c, VERANDA.x, z, 1, VERANDA.y - 1, P.frame)
  eaveBrackets(c, 'z', VERANDA.x, VERANDA.brackets[two ? 2 : 1], VERANDA.y - 1, `${P.wood}_stairs`, 'west')
  // the stoep: clay pots under the front lights, the door's lamp hung from the eave
  for (const p of STOEP.pots) c.set(...p, P.finial, POT)
  eaveBrackets(c, 'x', STOEP.z, STOEP.brackets, HIP.eaveY - 1, `${P.wood}_stairs`, 'south')
  c.set(...STOEP.lamp, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // the hearth's fire, in the west wall facing the room
  hearthFire(c, STACK.hearth, 'east')
  c.set(STACK.x, STACK.top, STACK.z, P.stackCap)
  if (!two) {
    for (const p of OUTSIDE.woodpile[1]) c.set(...p, P.frame, { axis: 'z' })
    for (const p of OUTSIDE.jars[1]) c.set(...p, P.finial, POT)
    c.set(...PASSAGE.lamp, 'lantern', { hanging: 'true', waterlogged: 'false' }) // over the back door
  }
  if (two) {
    c.set(...PASSAGE.lamp, 'lantern', { hanging: 'true', waterlogged: 'false' })
    // the hoist over the hay door: a beam end out of the eave, its chain hanging before the door
    const hd = OPENINGS.hayDoor.at
    c.set(hd[0], BARN_HIP.eaveY, BARN_HIP.z1, ...log('z'))
    c.set(hd[0], BARN_HIP.eaveY - 1, BARN_HIP.z1, 'chain', { axis: 'y', waterlogged: 'false' })
    // a thatch hood over the wagon doors, on brackets
    const WH = WAGON_HOOD
    for (let x = WH.x0; x <= WH.x1; x++) stair(c, x, WH.y, WH.z, P.roof.stairs, 'north')
    eaveBrackets(c, 'x', WH.z, [WH.x0, WH.x1], WH.y - 1, `${P.wood}_stairs`, 'north')
    // the woodstore along the grain house's west wall: a thatch lean-to over stacked logs
    const W = WOODSTORE
    for (let z = W.z0; z <= W.z1; z++) {
      stair(c, W.x, W.y, z, P.roof.stairs, 'east')
      if (z === W.z0 || z === W.z1) continue
      for (let y = 1; y < W.y; y++) c.set(W.x, y, z, P.frame, { axis: 'x' })
    }
    for (const z of [W.z0, W.z1]) post(c, W.x, z, 1, W.y - 1, P.frame)
    for (let z = W.z0; z <= W.z1; z++) c.set(W.x, 0, z, terra(W.x, 0, z))
  }

  // --- Pass 5: interior (the shipped spots, cell for cell) -------------------------------------------------------------
  for (const f of FURNITURE) {
    if (!has(f)) continue
    if (f.part === 'composter') composter(c, f)
    else furnitureParts[f.part](c, f)
  }
  if (two) {
    // the byre at the front: a stall down the east wall behind acacia rails, hay in its rack, a sunken trough
    railingRun(c, [[5, 1, 11], [5, 1, 13]], P.wood)
    c.set(5, 1, 12, `${P.wood}_fence_gate`, { facing: 'east', in_wall: 'false', open: 'false', powered: 'false' })
    c.set(6, 1, 11, 'hay_block', { axis: 'y' })
    c.set(6, 0, 13, 'water', { level: '0' })
    // grain jars down the west wall, a pumpkin by the ladder
    for (const z of [11, 12, 13]) c.set(2, 1, z, P.finial, { ...POT, facing: 'east' })
    c.set(3, 1, 11, P.finial, { ...POT, facing: 'east' })
    c.set(2, 1, 14, 'pumpkin')
    // hay up in the loft (clear of the hay door and the ladder's head)
    for (const [x, y, z] of [[5, 6, 17], [5, 6, 18], [5, 6, 19], [6, 6, 17], [6, 6, 18], [6, 6, 19],
      [3, 6, 18], [3, 6, 19], [2, 6, 19]]) c.set(x, y, z, 'hay_block', { axis: 'y' })
  }

  // --- Pass 6: the cellar (III) -------------------------------------------------------------------------------------
  let dug = null
  if (level >= 3) {
    const C = CELLAR
    dug = cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, mix(P.cellarWall, 3), P.cellarFloor)
    hatchLadder(c, C.ladder.x, C.ladder.z, -C.depth + 1, 0, C.ladder.facing, P.wood)
    // grain jars and produce down the east wall under a board shelf, hay at the back
    ;[['pumpkin', 3], ['melon', 4], ['pumpkin', 5], ['melon', 6]].forEach(([id, z]) => {
      c.set(6, -3, z, id); slab(c, 6, -2, z, `${P.wood}_slab`, 'top')
      c.set(6, -1, z, P.finial, { ...POT, facing: 'west' })
    })
    for (const [x, y, z] of [[5, -3, 7], [6, -3, 7], [6, -2, 7]]) c.set(x, y, z, 'hay_block', { axis: 'y' })
    c.set(3, -3, 7, P.finial, POT)
  }

  // --- Pass 7: ground and field -------------------------------------------------------------------------------------
  const cropAt = rows => (x, z) => { const r = rows[x]; return r ? [r[0], r[1][z % r[1].length]] : null }
  const F = FIELD
  fieldPlot(c, F, {
    water: (x, z) => x === F.channelX && z !== F.scarecrowZ,
    footing: (x, z) => (x === F.channelX && z === F.scarecrowZ ? 'terracotta' : null),
    crop: cropAt(CROPS[1]),
  })
  kerbFence(c, [[F.x0, F.z0 - 1], [14, F.z0 - 1], [14, F.z1 + 1], [F.x0, F.z1 + 1]], 0, kerb, `${P.wood}_fence`)
  scarecrow(c, [F.channelX, 1, F.scarecrowZ], 'west', { leg: `${P.wood}_fence`, body: 'hay_block', arms: `${P.wood}_fence`, hat: P.roof.slab })
  if (two) {
    const F2 = FIELD2
    fieldPlot(c, F2, {
      water: (x, z) => x === F2.channelX && z >= F2.water[0] && z <= F2.water[1],
      crop: (x, z) => (x === F2.channelX ? ['carrots', 4] : cropAt(CROPS[2])(x, z)),
    })
    kerbFence(c, [[14, F2.z0 - 1], [14, F2.z1 + 1], [F2.x0, F2.z1 + 1]], 0, kerb, `${P.wood}_fence`)
  }
  for (const [x, z] of [...OUTSIDE.fieldLamps[1], ...(two ? OUTSIDE.fieldLamps[2] : [])])
    c.set(x, 2, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
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
