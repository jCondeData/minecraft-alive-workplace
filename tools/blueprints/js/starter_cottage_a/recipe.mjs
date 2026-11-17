// Starter Cottage (Version A), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes
// (block-out and structure, openings, depth and detail, interior, ground) for LEVEL = 1..4, each level drawn as the
// level before plus what it adds (the half-Cape; its second half; the kitchen ell; the root cellar). Writes
// furniture.json (the level's furniture spots) for tools/blueprints/rebuild.py. CUT=<y> cuts everything above y.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (build.sh: all four)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZES, BASEMENT, IDS, PALETTE, MASSES, OPENINGS, FURNITURE, CELLAR, OUTSIDE, DORMER, LADDER } from './plan.mjs'
import {
  LiftedCanvas, mix, plinth, floor, post, beamRing, wallPanel, window, door, doorway, gableRoof, gableWall, hood,
  woodpile, bargeTrim, furnitureParts, stair, slab, cellar, hatchLadder,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const stone = mix(P.stone, 11)
const H = MASSES.hall, C = MASSES.chimney, R = MASSES.parlour, K = MASSES.kitchen

/** A timber wall line from a to b at y 1..3 (birch infill), the knee wall above the beam to y `knee` (when given). */
function infill(c, a, b, top = 3) { wallPanel(c, a, b, 1, top, P.infill) }

/** The gable end on wall line x (a z-span gable under a roof whose ridge runs along x): infill with the wall's posts
 *  carried up as studs, a king post over the loft window at the middle (`window`: false for a blind gable). */
function gable(c, x, under, posts, { window: win = true } = {}) {
  gableWall(c, 'x', x, [H.posts.z0, H.posts.z1], H.beamY + 1, under, (z, y) => {
    if (posts.includes(z) && z !== H.posts.z0 && z !== H.posts.z1) return [P.frame, { axis: 'y' }]
    if (win && z === 4 && y === 8) return [P.frame, { axis: 'y' }]
    return P.infill
  })
}

function drawLevel(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT[level] ?? 0)
  const at = l => level >= l
  const { x0, x1, z0, z1 } = H.posts

  // --- Pass 1: block-out and structure -----------------------------------------------------------------------------
  // The Hall: a cobble plinth, spruce boards, posts and a beam ring, birch infill, a knee wall under the eaves
  plinth(c, x0, z0, x1, z1, stone)
  floor(c, H.room.x0, H.room.z0, H.room.x1, H.room.z1, 0, P.floor)
  infill(c, [x0, z0], [x1, z0]); infill(c, [x0, z1], [x1, z1]); infill(c, [x0, z0], [x0, z1]); infill(c, [x1, z0], [x1, z1])
  for (const x of H.frontPosts) post(c, x, z0, 1, 3, P.frame)
  for (const x of H.backPosts) post(c, x, z1, 1, 3, P.frame)
  for (const z of H.westPosts) post(c, x0, z, 1, 3, P.frame)
  for (const z of H.eastPosts) post(c, x1, z, 1, 3, P.frame)
  beamRing(c, x0, z0, x1, z1, H.beamY, P.frame)
  floor(c, H.room.x0, H.room.z0, H.room.x1, H.room.z1, H.beamY, P.floor) // the loft floor: the hall's ceiling
  const kneeTo = at(2) ? R.posts.x1 : x1
  for (let x = x0; x <= kneeTo; x++) for (const z of [z0, z1]) {
    const isPost = [1, 5, 7, 11].includes(x)
    c.set(x, H.beamY + 1, z, isPost ? P.frame : P.infill, isPost ? { axis: 'y' } : {})
  }

  // The Parlour (II): the other half, on the far side of the chimney; the hall's east gable becomes the partition
  if (at(2)) {
    const r = R.posts
    plinth(c, r.x0 + 1, r.z0, r.x1, r.z1, stone)
    floor(c, R.room.x0, R.room.z0, R.room.x1, R.room.z1, 0, P.floor)
    infill(c, [r.x0 + 1, r.z0], [r.x1, r.z0]); infill(c, [r.x0 + 1, r.z1], [r.x1, r.z1]); infill(c, [r.x1, r.z0], [r.x1, r.z1])
    for (const x of R.frontPosts) post(c, x, r.z0, 1, 3, P.frame)
    for (const x of R.backPosts) post(c, x, r.z1, 1, 3, P.frame)
    for (const z of R.eastPosts) post(c, r.x1, z, 1, 3, P.frame)
    beamRing(c, r.x0, r.z0, r.x1, r.z1, H.beamY, P.frame)
    c.set(r.x0, H.beamY, r.z0, P.frame, { axis: 'y' }); c.set(r.x0, H.beamY, r.z1, P.frame, { axis: 'y' })
    floor(c, R.room.x0, R.room.z0, R.room.x1, R.room.z1, H.beamY, P.floor)
  }

  // Roof: one 45-degree shingle roof, ridge along x, a block of overhang all round; II carries it on to x 12
  const roofSpec = at(2) ? R.roof : H.roof
  const main = gableRoof(c, roofSpec, P.roof)
  gable(c, x0, main.under, H.westPosts)
  if (!at(2)) gable(c, x1, main.under, [3, 5], { window: false })
  else {
    gable(c, x1, main.under, [3, 5], { window: false }) // the loft's partition
    gable(c, R.posts.x1, main.under, R.eastPosts)
  }

  // The Kitchen ell (III): rubble walls on the plinth, spruce corner posts, a lower roof with its ridge at y 6
  if (at(3)) {
    const w = K.walls
    plinth(c, w.x0 + 1, w.z0, w.x1, w.z1, stone)
    floor(c, K.room.x0, K.room.z0, K.room.x1, K.room.z1, 0, P.kitchenFloor)
    wallPanel(c, [w.x0 + 1, w.z0], [w.x1, w.z0], 1, 3, stone)
    wallPanel(c, [w.x0 + 1, w.z1], [w.x1, w.z1], 1, 3, stone)
    wallPanel(c, [w.x1, w.z0], [w.x1, w.z1], 1, 3, stone)
    for (const z of [w.z0, w.z1]) post(c, w.x1, z, 1, 3, P.frame)
    const wing = gableRoof(c, K.roof, P.roof, (x, y, z) => !c.get(x, y, z))
    gableWall(c, 'x', w.x1, [w.z0, w.z1], 4, wing.under, () => P.infill)
    bargeTrim(c, 'x', K.roof.along[1], K.roof.across, wing.under, P.trim)
  }

  // --- Pass 2: openings ----------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.door, step: fd.step, stepStair: P.step })
  c.set(fd.at[0], 3, fd.at[2], P.frame, { axis: 'x' }) // the lintel
  for (const w of OPENINGS.windows) {
    if (!at(w.level)) continue
    if (w.until && at(w.until)) { for (const [x, y, z] of w.cells) c.set(x, y, z, P.infill); continue } // walled in
    window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: w.pots ?? [] })
  }
  if (at(2)) doorway(c, OPENINGS.partition.at, OPENINGS.partition.height)
  if (at(3)) {
    doorway(c, OPENINGS.kitchenDoorway.at, OPENINGS.kitchenDoorway.height)
    const kd = OPENINGS.kitchenDoor
    door(c, kd.at, kd.facing, 'north', { wood: P.door, step: kd.step, stepStair: P.step })
  }

  // --- Pass 3: depth and detail --------------------------------------------------------------------------------------
  // The chimney: a stepped breast and a stack outside the hall's east gable, the hearth in a stone surround inside
  const top = C.top[at(2) ? 2 : 1]
  for (let y = 1; y <= 3; y++) for (let z = C.breast[0]; z <= C.breast[1]; z++) c.set(x1, y, z, stone) // the fireback
  for (const z of [C.breast[0], C.breast[1]]) for (let y = 1; y <= 3; y++) c.set(x1 - 1, y, z, stone) // the cheeks
  for (let y = 2; y <= 3; y++) c.set(x1 - 1, y, C.z, stone) // the hood over the fire
  for (let y = 0; y <= 3; y++) for (let z = C.breast[0]; z <= C.breast[1]; z++) c.set(C.x, y, z, stone)
  for (let y = 0; y <= top; y++) c.set(C.x, y, C.z, y === 10 || y === top ? P.cap : stone)
  c.set(C.x, top + 1, C.z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  if (!at(2)) {
    stair(c, C.x, 4, C.breast[0], P.step, 'south')
    stair(c, C.x, 4, C.breast[1], P.step, 'north')
    bargeTrim(c, 'x', H.roof.along[1], H.roof.across, main.under, P.trim)
  } else {
    for (const z of C.breast) c.set(C.x, 4, z, stone) // the stack's shoulders inside the parlour's loft floor
    bargeTrim(c, 'x', R.roof.along[1], R.roof.across, main.under, P.trim)
  }
  bargeTrim(c, 'x', H.roof.along[0], H.roof.across, main.under, P.trim)
  // the hood over the front door, its lantern under it
  hood(c, fd.at[0], H.beamY, 0, P.roof)
  // a drip course of upside-down spruce stairs round the beam ring, a block out: the frame reads in layers
  const east = at(2) ? R.posts.x1 + 1 : x1 + 1
  const drip = (x, z, f) => { if (!c.get(x, 4, z)) stair(c, x, 4, z, `${P.wood}_stairs`, f, 'top') }
  for (let x = x0; x <= east - 1; x++) { drip(x, 0, 'south'); drip(x, 8, 'north') }
  for (let z = z0; z <= z1; z++) { drip(0, z, 'east'); if (!(at(3) && z >= K.walls.z0 - 1) && !(!at(2) && z >= 3 && z <= 5)) drip(east, z, 'west') }
  // the front dormer over the hall window: a gabled face on the wall line, its own little roof into the main slope
  const d = DORMER, f = d.face
  for (const dx of [-1, 1]) for (let y = 6; y <= 7; y++) c.set(d.x + dx, y, f, P.frame, { axis: 'y' })
  c.set(d.x, 6, f, P.glass); c.set(d.x, 7, f, P.glass); c.set(d.x, 8, f, P.infill)
  c.clear(d.x, 7, f + 1)
  for (let z = 0; z <= f + 1; z++) {
    stair(c, d.x - 2, 7, z, P.roof.stairs, 'east'); stair(c, d.x + 2, 7, z, P.roof.stairs, 'west')
    stair(c, d.x - 1, 8, z, P.roof.stairs, 'east'); stair(c, d.x + 1, 8, z, P.roof.stairs, 'west')
  }
  for (let z = 0; z <= f + 2; z++) c.set(d.x, 9, z, P.roof.full)
  c.set(d.x - 1, 7, 0, P.trim, { type: 'top', waterlogged: 'false' }); c.set(d.x + 1, 7, 0, P.trim, { type: 'top', waterlogged: 'false' })
  // the woodstore lean-to at the back: two posts, a shingle roof falling away from the house, split logs under it
  const ws = OUTSIDE.woodstore
  for (const x of [ws.x0, ws.x1]) post(c, x, ws.z1, 0, 2, P.frame)
  for (let x = ws.x0; x <= ws.x1; x++) { stair(c, x, 4, ws.z0, P.roof.stairs, 'north'); stair(c, x, 3, ws.z1, P.roof.stairs, 'north') }
  for (let x = ws.x0 + 1; x < ws.x1; x++) { // stacked to the roof against the wall, two high in front
    for (let y = 0; y <= 3; y++) c.set(x, y, ws.z0, P.frame, { axis: 'z' })
    for (let y = 0; y <= 1; y++) c.set(x, y, ws.z1, P.frame, { axis: 'z' })
  }
  if (at(3)) c.set(16, 2, 2, 'lantern', { hanging: 'true', waterlogged: 'false' }) // by the kitchen door, under its eave

  // --- Pass 4: interior (the plan's furniture, level by level) ---------------------------------------------------------
  for (const f of FURNITURE) if (at(f.level)) furnitureParts[f.part](c, f)
  // the loft ladder against the hearth's cheek, through the loft floor, onto a bookcase in the loft
  for (let y = 1; y <= LADDER.top; y++) c.set(LADDER.x, y, LADDER.z, 'ladder', { facing: LADDER.facing, waterlogged: 'false' })
  c.set(LADDER.x + 1, 5, LADDER.z, 'bookshelf')
  c.set(3, 5, 5, 'hay_block', { axis: 'y' }); c.set(4, 5, 5, 'hay_block', { axis: 'y' }) // a straw pallet
  if (at(2)) c.set(8, 3, 6, 'bookshelf') // the parlour cupboard's top shelf, up to the ceiling beside the chimney

  // --- Pass 5: the root cellar (IV) and the ground ---------------------------------------------------------------------
  let dug = null
  if (at(4)) {
    const w = MASSES.cellar.walls, d = MASSES.cellar.depth
    dug = cellar(c, w.x0, w.z0, w.x1, w.z1, d, stone, P.cellarFloor)
    const L = CELLAR.ladder
    hatchLadder(c, L.x, L.z, -d + 1, 0, L.facing, P.wood)
    for (const [id, x, y, z] of CELLAR.stores) c.set(x, y, z, id, id === 'hay_block' ? { axis: 'y' } : {})
    for (const l of CELLAR.lamps) c.set(...l, 'lantern', { hanging: 'true', waterlogged: 'false' })
  }
  return { c, dug }
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = drawLevel(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  if (dug) fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(','))
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const name = `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}`
  return { structure: canvas.toStructure(api), metadata: { name } }
}
