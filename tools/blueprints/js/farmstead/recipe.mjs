// Farmstead (farmstead, farmstead_2, farmstead_3; ROADMAP 49), the recipe: assembles plan.mjs from the shared parts
// library (../parts) in passes (block-out and structure, openings, depth and detail, interior, cellar, ground and field)
// for LEVEL = 1..3, each level the one before plus what it adds (II the cross passage, the barn and the second field under
// the same roof line; III the root cellar and its hatch, nothing else above ground). Writes furniture.json (the
// furniture spots) and, for III, out/cellar.txt. Build all three: build.sh. CUT=<y> cuts everything above y.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, BASEMENT, IDS, PALETTE, MASSES, OPENINGS, FURNITURE, CROPS, OUTSIDE } from './plan.mjs'
import {
  LiftedCanvas, mix, stair, slab, trapdoor, plinth, floor, wallPanel, window, door, catslideRoof, gableRoof, gableWall, bargeTrim, pentice,
  chimney, flowerBed, railingRun, furnitureParts, cellar, hatchLadder, louvre,
  fieldPlot, kerbFence, scarecrow, composter,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function drawLevel(level) {
  const c = new LiftedCanvas(SIZE[level], BASEMENT[level] ?? 0)
  const P = PALETTE
  const stone = mix(P.stone, 5)
  const kerb = mix(P.kerb, 9)
  const H = MASSES.house, R = MASSES.roof, G = MASSES.gallery, B = MASSES.barn, F = MASSES.field, F2 = MASSES.field2
  const FR = MASSES.frame
  const two = level >= 2
  const has = o => (o.level ?? 1) <= level && level <= (o.until ?? 9)
  const beam = axis => [P.frame, { axis }]
  const post = (x, z, y0, y1) => { for (let y = y0; y <= y1; y++) c.set(x, y, z, ...beam('y')) }
  const galleryEnd = G.z1[two ? 2 : 1]
  const bz = MASSES.breezeway.z

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // The house's catslide roof first (its undersides set every wall's height): ridge along z at x 3, the short west
  // slope to the eave at x 0, the long east sweep down to head height over the gallery at x 8. II: the barn's roof
  // on the same line two blocks higher, so house and barn read as two volumes stepping up along the range.
  const under = catslideRoof(c, { axis: R.axis, across: R.across, ridge: R.ridge, along: R.along, ridgeY: R.ridgeY }, P.roof).under
  const BR = MASSES.barnRoof
  const ub = two ? catslideRoof(c, { axis: R.axis, across: R.across, ridge: R.ridge, along: BR.along, ridgeY: BR.ridgeY }, P.roof).under : null

  // House: a cobble plinth, boards inside, whitewashed walls, a beam at y 5 under boarded gables, the west wall's
  // plate at y 5 and the east wall's at y 4 (the low side); the frame stands a block proud of all this (pass 3)
  plinth(c, H.x0, H.z0, H.x1, H.z1, stone)
  floor(c, H.room.x0, H.room.z0, H.room.x1, H.room.z1, 0, P.floor)
  for (let z = 4; z <= 6; z++) c.set(2, 0, z, 'stone_bricks') // the hearthstone
  const gable = (wall, upper) => (u, y) => {
    if (u === H.x0 || u === H.x1) return beam('y')
    if (y === H.beamY) return beam('x')
    if (y > H.beamY) return upper
    return wall
  }
  gableWall(c, 'z', H.z0, [H.x0, H.x1], 1, under, gable(P.wall, [P.boards, { axis: 'y' }]))
  gableWall(c, 'z', H.z1, [H.x0, H.x1], 1, under, gable(P.wall, [P.boards, { axis: 'y' }]))
  for (let z = H.z0 + 1; z < H.z1; z++) {
    for (let y = 1; y < under[H.x0]; y++) c.set(H.x0, y, z, ...(y === H.beamY ? beam('z') : [P.wall]))
    for (let y = 1; y < under[H.x1]; y++) c.set(H.x1, y, z, ...(y === under[H.x1] - 1 ? beam('z') : [P.wall]))
  }
  for (let z = 4; z <= 6; z++) for (let y = 1; y < H.beamY; y++) c.set(H.x0, y, z, stone) // the hearth's surround
  for (const z of H.tieBeams) for (let x = H.room.x0; x <= H.room.x1; x++) c.set(x, H.beamY, z, ...beam('x'))

  // The back porch (I) / cross passage (II): stone flags at z 9 between the house and the barn, a step from the west
  for (let x = H.x0; x <= H.x1; x++) c.set(x, 0, bz, kerb)
  stair(c, 0, 0, bz, 'cobblestone_stairs', 'east')
  // The gallery: a boarded deck along the east eave, a step up at the front
  for (let z = G.z0; z <= galleryEnd; z++) c.set(G.x, 0, z, P.deck)
  stair(c, ...OPENINGS.galleryStep, `${P.wood}_stairs`, 'south')

  // The barn (II): red boards on a cobble plinth under its own higher roof, a beaten-earth threshing floor
  if (two) {
    plinth(c, B.x0, B.z0, B.x1, B.z1, stone)
    floor(c, B.room.x0, B.room.z0, B.room.x1, B.room.z1, 0, P.barnFloor)
    gableWall(c, 'z', B.z0, [B.x0, B.x1], 1, ub, gable(P.barn, P.barn))
    gableWall(c, 'z', B.z1, [B.x0, B.x1], 1, ub, gable(P.barn, P.barn))
    for (let z = B.z0 + 1; z < B.z1; z++) {
      for (let y = 1; y < ub[B.x0]; y++) c.set(B.x0, y, z, ...(y === H.beamY ? beam('z') : [P.barn]))
      for (let y = 1; y < ub[B.x1]; y++) c.set(B.x1, y, z, ...(y === ub[B.x1] - 1 ? beam('z') : [P.barn]))
    }
    for (const z of B.tieBeams) for (let x = B.room.x0; x <= B.room.x1; x++) c.set(x, H.beamY, z, ...beam('x'))
    // the cross passage's tie beam (the lantern hangs from it)
    for (let x = H.x0; x <= H.x1 - 1; x++) c.set(x, H.beamY, bz, ...beam('x'))
    // the hay loft at the back, on a ladder up the west wall, a rail along its edge
    const L = B.loft
    floor(c, B.room.x0, L.z0, B.room.x1, L.z1, L.y, P.floor)
    for (let y = 1; y <= L.y; y++) c.set(2, y, L.z0 - 1, 'ladder', { facing: 'east', waterlogged: 'false' })
    railingRun(c, [[3, L.y + 1, L.z0], [6, L.y + 1, L.z0]], P.wood)
    // the second Farmer's chamber under the loft: board walls, a door from the barn floor
    const K = B.chamber
    for (let x = B.room.x0; x <= K.doorX; x++) for (let y = 1; y < L.y; y++) c.set(x, y, K.wallZ, P.boards, { axis: 'y' })
    for (let z = K.z0; z <= K.z1; z++) for (let y = 1; y < L.y; y++) c.set(K.wallX, y, z, P.boards, { axis: 'y' })
    door(c, [K.doorX, 1, K.wallZ], 'south', 'north', { wood: P.wood })
    // two stalls down the east wall: a fence front with a gate to each, dividers, a sunken trough and hay in each
    const S = B.stalls
    for (let z = S.z0; z <= S.z1; z++) {
      if (S.gates.includes(z)) c.set(S.front, 1, z, `${P.wood}_fence_gate`, { facing: 'east', in_wall: 'false', open: 'false', powered: 'false' })
      else c.set(S.front, 1, z, `${P.wood}_fence`)
    }
    for (const z of S.dividers) c.set(B.room.x1, 1, z, `${P.wood}_fence`)
    for (const z of S.troughs) c.set(B.room.x1, 0, z, 'water', { level: '0' })
    for (const z of S.hay) c.set(B.room.x1, 1, z, 'hay_block', { axis: 'y' })
    // the tool wall: hooks on the west wall over a board bench
    for (const z of B.tools.z) c.set(B.tools.x, 2, z, 'tripwire_hook', { facing: 'east', attached: 'false', powered: 'false' })
    for (const z of B.tools.z.slice(0, 2)) slab(c, B.tools.x, 1, z, `${P.wood}_slab`, 'top')
  }

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, fd.out, { wood: P.wood, step: fd.step })
  const bd = OPENINGS.backDoor
  door(c, bd.at, bd.facing, bd.out, { wood: P.wood })
  if (two) {
    for (const o of [OPENINGS.barnDoor, OPENINGS.barnSideDoor]) door(c, o.at, o.facing, o.out, { wood: P.wood })
    const wd = OPENINGS.wagonDoors
    wd.at.forEach((at, i) => door(c, at, wd.facing, wd.out, { wood: P.wood, hinge: i ? 'right' : 'left' }))
    for (let x = 4; x <= 7; x++) c.set(x, 3, B.z1, ...beam('x')) // the wagon doors' lintel
    for (const [x, , z] of wd.at) stair(c, x, 0, z + 1, 'cobblestone_stairs', 'north')
    // the cross passage: whitewashed ends with a door in each, west to the yard and east to the gallery
    for (let y = 1; y < under[H.x0]; y++) c.set(H.x0, y, bz, ...(y === H.beamY ? beam('z') : [P.wall]))
    for (let y = 1; y < under[H.x1]; y++) c.set(H.x1, y, bz, ...(y === under[H.x1] - 1 ? beam('z') : [P.wall]))
    door(c, [H.x0, 1, bz], 'east', 'west', { wood: P.wood })
    door(c, [H.x1, 1, bz], 'west', 'east', { wood: P.wood })
    c.set(4, H.beamY - 1, bz, 'lantern', { hanging: 'true', waterlogged: 'false' })
  }
  const pots = { 'hall west': ['potted_cornflower'], 'hall west back': ['potted_poppy'] }
  for (const w of OPENINGS.windows) if (has(w)) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: pots[w.name] ?? [] })
  if (!two) for (const y of [2, 3]) trapdoor(c, 3, y, bz, `${P.wood}_trapdoor`, 'south', 'bottom', true) // the back window's shutter

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  // The proud frame: posts and beams a block out from every face, the limewash and boards in the wall plane behind.
  for (const x of FR.front.posts) { c.set(x, 0, FR.front.z, stone); post(x, FR.front.z, 1, FR.front.top) }
  const W = FR.west
  for (let z = 1; z <= (two ? B.z1 : H.z1); z++) {
    if (W.skip.includes(z) || (z === bz && two)) continue
    if (z === bz) continue
    c.set(W.x, W.beamY, z, ...beam('z'))
  }
  for (const z of W.posts[two ? 2 : 1]) { c.set(W.x, 0, z, stone); post(W.x, z, 1, (z >= B.z0 ? ub[W.x] : under[W.x]) - 1) }
  if (!two) { // the back gable's frame over the porch (the passage takes z 9 in II)
    for (const x of FR.back.posts) post(x, FR.back.z, 1, under[x] - 1)
    for (let x = H.x0 + 1; x < H.x1; x++) c.set(x, H.beamY, FR.back.z, ...beam('x'))
  }
  // the field side's frame: posts between the windows, in the wall plane under the plate
  for (const z of FR.east.posts) post(FR.east.x, z, 1, under[FR.east.x] - 2)
  // the gallery's frame: a beam under each eave on posts set off the walking line
  for (let z = 0; z <= H.z1 + 1; z++) c.set(G.x, under[G.x] - 1, z, ...beam('z'))
  if (two) for (let z = B.z0; z <= B.z1; z++) c.set(G.x, ub[G.x] - 1, z, ...beam('z'))
  for (const z of G.posts[two ? 2 : 1]) post(G.x, z, 1, (z >= B.z0 ? ub[G.x] : under[G.x]) - 2)
  if (two) { // the barn's back: corner posts and a beam a block out, a pent roof over the wagon doors
    const BB = FR.barnBack
    for (const x of BB.posts) { c.set(x, 0, BB.z, stone); post(x, BB.z, 1, BB.beamY - 1) }
    for (let x = B.x0; x <= B.x1; x++) c.set(x, BB.beamY, BB.z, ...beam('x'))
    pentice(c, [2, 3, 4, 5, 6].map(x => [x, BB.z]), BB.beamY - 1, P.roof, 'north')
  }
  // the pent roof across the front gable over the door and windows, a lantern hung under it at the door
  pentice(c, [1, 2, 3, 4, 5, 6].map(x => [x, 0]), H.beamY, P.roof, 'south')
  c.set(fd.at[0], H.beamY - 1, 0, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // the chimney: a cobble stack on the west wall past the ridge, a stepped breast, the fire inside the wall
  const ch = MASSES.chimney
  chimney(c, ch.x, ch.z, 0, ch.top, stone, { breast: { axis: 'z', top: ch.breastTop } })
  c.set(ch.x, ch.top, ch.z, 'stone_bricks') // a dressed cap under the smoke
  // barge boards under the open rakes (45 degrees: upside-down stairs)
  bargeTrim(c, 'z', R.along[0], R.across, under, `${P.trim}_stairs`)
  if (!two) bargeTrim(c, 'z', R.along[1], R.across, under, `${P.trim}_stairs`)
  if (two) {
    bargeTrim(c, 'z', BR.along[1], R.across, ub, `${P.trim}_stairs`)
    // the barn's cross gable over the gallery: a steep little gable facing the field over the second composter,
    // drawn only where it rises out of the barn's slope; its face in red boards on the gallery's beam
    const X = MASSES.crossGable
    const xg = gableRoof(c, { axis: 'x', across: X.across, along: X.along, eaveY: ub[G.x], steep: true }, P.roof,
      (x, y) => y >= ub[x])
    gableWall(c, 'x', G.x, X.across, ub[G.x], xg.under, (u, y) => (u === X.window.z && X.window.y.includes(y) ? P.glass : P.barn))
    for (let x = X.along[0]; x < G.x; x++) for (let z = X.across[0] + 1; z < X.across[1]; z++)
      for (let y = ub[x] + 1; y < xg.under[z]; y++) if (!c.get(x, y, z)) c.set(x, y, z, P.barn)
    // the cupola on the barn's ridge (a louvred ventilator, a lightning rod for a weathervane)
    const cu = MASSES.cupola
    louvre(c, cu.x, cu.z, BR.ridgeY, { base: P.roof.full, post: `${P.trim}_fence`, roof: P.roof })
    c.set(cu.x, BR.ridgeY + 3, cu.z, 'lightning_rod', { facing: 'up', powered: 'false', waterlogged: 'false' })
    // the hay door high in the back gable, the hoist beam over it with its chain
    const hd = OPENINGS.hayDoor.at
    door(c, hd, 'north', 'south', { wood: P.wood })
    c.set(hd[0], hd[1] + 3, B.z1, ...beam('z')); c.set(hd[0], hd[1] + 3, B.z1 + 1, ...beam('z'))
    c.set(hd[0], hd[1] + 2, B.z1 + 1, 'chain', { axis: 'y', waterlogged: 'false' })
  }

  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  for (const f of FURNITURE) {
    if (!has(f)) continue
    if (f.part === 'composter') composter(c, f)
    else furnitureParts[f.part](c, f)
  }
  if (two) {
    // hay up on the loft (clear of the hay door), a stack by the wagon doors, a pumpkin by the ladder
    for (const [x, y, z] of [[6, 1, 18], [6, 2, 18], [3, 6, 18], [3, 6, 19], [5, 6, 17], [5, 6, 18], [5, 6, 19], [5, 7, 19],
      [6, 6, 18], [6, 6, 19], [3, 7, 19]]) c.set(x, y, z, 'hay_block', { axis: 'y' })
    c.set(2, 1, 14, 'pumpkin')
  }

  // --- Pass 5: the cellar (III) -----------------------------------------------------------------------------------
  let dug = null
  if (level >= 3) {
    const C = MASSES.cellar
    dug = cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, mix(P.cellarWall, 3), P.cellarFloor)
    hatchLadder(c, C.ladder.x, C.ladder.z, -C.depth + 1, 0, C.ladder.facing, P.wood)
    // a root cellar: produce racks down the east wall (pumpkins and melons under a board shelf), hay at the back
    ;[['pumpkin', 3], ['melon', 4], ['pumpkin', 5], ['melon', 6]].forEach(([id, z]) => {
      c.set(6, -3, z, id); slab(c, 6, -2, z, `${P.wood}_slab`, 'top')
    })
    for (const [x, y, z] of [[3, -3, 7], [4, -3, 7], [6, -3, 7], [3, -2, 7]]) c.set(x, y, z, 'hay_block', { axis: 'y' })
    c.set(5, -3, 7, 'pumpkin')
  }

  // --- Pass 6: ground and field ---------------------------------------------------------------------------------
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  // the field: farmland round a channel down x 11, the scarecrow's footing in the channel, crops in rows
  const cropAt = rows => (x, z) => { const r = rows[x]; return r ? [r[0], r[1][z % r[1].length]] : null }
  fieldPlot(c, F, {
    water: (x, z) => x === F.channelX && z !== F.scarecrowZ,
    footing: (x, z) => (x === F.channelX && z === F.scarecrowZ ? 'cobblestone' : null),
    crop: cropAt(CROPS[1]),
  })
  kerbFence(c, [[F.x0, F.z0 - 1], [14, F.z0 - 1], [14, F.z1 + 1], [F.x0, F.z1 + 1]], 0, kerb, `${P.wood}_fence`)
  scarecrow(c, [F.channelX, 1, F.scarecrowZ], 'west', { leg: `${P.wood}_fence`, body: 'hay_block', arms: `${P.wood}_fence`, hat: 'brown_carpet' })
  if (two) {
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
