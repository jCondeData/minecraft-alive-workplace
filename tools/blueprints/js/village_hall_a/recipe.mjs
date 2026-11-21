// Village Hall (Version A, "the Bell Porch"), the recipe: assembles plan.mjs from the shared parts library (../parts)
// in passes (block-out and structure, openings, depth and detail, interior, ground) for LEVEL = 1..3, each level drawn
// as the one before it plus what it adds, in the same template frame. Writes furniture.json (the level's furniture
// spots) beside it for tools/blueprints/rebuild.py.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --name village_hall_2 --data-version 3955
//   (build.sh: all three as candidates; CUT=<y> cuts everything above y for a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, PALETTE, HALL, hallRoofY, PORCH, COUNCIL, TOWER, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, stair, slab, plinth, floor, post, beamRing, wallPanel, window, quoins, gableRoof, gableWall, bargeTrim,
  louvre, masonButtress, masonLampPier, kneeBrace, flowerBed, railingRun, furnitureParts,
  meetingBell, civicParts, wallHearth, belfry, needleSpire, civicDormer,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const parts = { ...furnitureParts, ...civicParts }
const onLevel = (thing, level) => !thing.levels || thing.levels.includes(level)

export function drawLevel(level) {
  const c = new Canvas(SIZES[level])
  const P = PALETTE
  const stone = mix(P.stone, 3)
  const paving = mix(P.paving, 5)
  const H = HALL, W = H.walls
  const log = (x, y, z, axis) => c.set(x, y, z, P.frame, { axis })

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // Ground: the hall's plinth and a skirt a block wider (the sills and buttresses stand on it), the paved forecourt
  plinth(c, 0, 3, 12, 12, stone)
  floor(c, H.room.x0, H.room.z0, H.room.x1, H.room.z1, 0, P.floor)
  plinth(c, 0, 0, 12, 2, paving)
  for (let x = 1; x <= 11; x++) stair(c, x, 0, 0, P.dressedStairs, 'south') // a step up from the street, the whole front
  floor(c, 4, 0, 8, 2, 0, P.dressed) // the porch's floor (its front edge keeps the step)
  for (let x = 4; x <= 8; x++) stair(c, x, 0, 0, P.dressedStairs, 'south')

  // Hall: brick walls on the plinth, dressed quoins, the beam ring, buttresses at the middle of the long sides
  wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], 1, H.wallTop, P.wall)
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, H.wallTop, P.wall)
  wallPanel(c, [W.x0, W.z0], [W.x0, W.z1], 1, H.wallTop, P.wall)
  wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, H.wallTop, P.wall)
  quoins(c, [[W.x0, W.z0], [W.x1, W.z0], [W.x0, W.z1], [W.x1, W.z1]], 1, H.wallTop, P.dressed, 'polished_andesite')
  beamRing(c, W.x0, W.z0, W.x1, W.z1, H.beamY, P.frame)
  for (const [x, z, toward] of H.buttresses) masonButtress(c, x, z, 0, 3, P.dressed, P.dressedStairs, toward)
  // the tie beam across the hall and its king post up to the ridge
  for (let x = H.room.x0; x <= H.room.x1; x++) log(x, H.beamY, H.tieBeamZ, 'x')
  for (let y = H.beamY + 1; y < hallRoofY(6); y++) log(6, y, H.tieBeamZ, 'y')

  // Hall roof: slate, ridge along z, the gables of brick with a dark king post
  const main = gableRoof(c, H.roof, P.roof)
  const gable = (u, y) => (u === 6 ? [P.frame, { axis: 'y' }] : P.wall)
  gableWall(c, 'z', W.z0, H.roof.across, H.beamY + 1, main.under, gable)
  gableWall(c, 'z', W.z1, H.roof.across, H.beamY + 1, main.under, gable)
  bargeTrim(c, 'z', H.roof.along[0], H.roof.across, main.under, `${P.trim}_stairs`)
  bargeTrim(c, 'z', H.roof.along[1], H.roof.across, main.under, `${P.trim}_stairs`)
  if (level < 3) louvre(c, H.louvre.cx, H.louvre.cz, H.louvre.y, { base: P.roof.full, post: `${P.trim}_fence`, roof: P.roof, finial: 'lightning_rod' })
  // dormers at the middle bay of each long slope, lighting the open roof; the east one is where Level II's council
  // wing roof later runs into the hall, so from Level II the wing takes its place
  for (const d of H.dormers) if (onLevel(d, level))
    civicDormer(c, { ...d, roofY: hallRoofY }, { roof: P.roof, wall: P.wall, post: P.frame, glass: P.glass })

  // Porch: stone pillars, the tie beam the bell hangs from, side beams back to the hall's wall, its own lower gable
  for (const [x, z] of PORCH.pillars) for (let y = 0; y <= PORCH.beamY; y++) c.set(x, y, z, P.dressed)
  for (let x = 4; x <= 8; x++) log(x, PORCH.beamY, 0, 'x')
  for (const x of [3, 9]) for (let z = 1; z <= 2; z++) log(x, PORCH.beamY, z, 'z')
  const porch = gableRoof(c, PORCH.roof, P.roof, (x, y, z) => !c.get(x, y, z))
  // where the porch roof runs in under the hall's front overhang (z 2), close the step between them with tiles
  for (let x = PORCH.roof.across[0]; x <= PORCH.roof.across[1]; x++)
    for (let y = porch.under[x] + 1; y < main.under[x]; y++) c.set(x, y, 2, P.roof.full)
  gableWall(c, 'z', 0, PORCH.roof.across, PORCH.beamY + 1, porch.under, (u, y) => (u === 6 ? [P.frame, { axis: 'y' }] : P.wall))
  // the porch mouth's arch: braces under the tie beam against each pillar, and along the sides
  floor(c, 4, 1, 8, 2, PORCH.beamY, `${P.trim}_planks`) // a flat boarded ceiling: the porch stays a shallow porch
  kneeBrace(c, 4, 4, 0, `${P.trim}_stairs`, 'west')
  kneeBrace(c, 8, 4, 0, `${P.trim}_stairs`, 'east')
  kneeBrace(c, 3, 4, 1, `${P.trim}_stairs`, 'north')
  kneeBrace(c, 9, 4, 1, `${P.trim}_stairs`, 'north')

  // Council wing (II, III): lower brick wing on the hall's east side, its roof running into the hall's slope
  if (level >= 2) {
    const K = COUNCIL, KW = K.walls
    plinth(c, 12, 3, 19, 11, stone)
    floor(c, K.room.x0, K.room.z0, K.room.x1, K.room.z1, 0, P.councilFloor)
    c.clearBox(12, 1, 5, 12, 4, 9) // the hall's east buttress gives way to the room
    wallPanel(c, [12, KW.z0], [KW.x1, KW.z0], 1, H.wallTop, P.wall)
    wallPanel(c, [12, KW.z1], [KW.x1, KW.z1], 1, H.wallTop, P.wall)
    wallPanel(c, [KW.x1, KW.z0], [KW.x1, KW.z1], 1, H.wallTop, P.wall)
    quoins(c, [[KW.x1, KW.z0], [KW.x1, KW.z1]], 1, H.wallTop, P.dressed, 'polished_andesite')
    for (let z = K.room.z0; z <= K.room.z1; z++) c.clear(12, H.beamY, z) // the hall's eave, now inside the wing
    beamRing(c, KW.x0, KW.z0, KW.x1, KW.z1, K.beamY, P.frame)
    for (let x = K.tieBeam.x0; x <= K.tieBeam.x1; x++) log(x, K.beamY, K.tieBeam.z, 'x')
    // its roof: only above the hall's slope (a valley where they meet), never inside the hall
    const wing = gableRoof(c, K.roof, P.roof, (x, y, z) => !c.get(x, y, z) && (x >= 12 || y > hallRoofY(x)))
    gableWall(c, 'x', KW.x1, K.roof.across, K.beamY + 1, wing.under, (u, y) => (u === 7 && y !== 7 ? [P.frame, { axis: 'y' }] : P.wall))
    // its west gable closes the wing's roof space against the hall's slope; the hall's eave there turns to brick
    for (let z = K.room.z0; z <= K.room.z1; z++) {
      c.set(11, 6, z, P.wall)
      for (let y = 7; y < wing.under[z]; y++) if (!c.get(11, y, z)) c.set(11, y, z, P.wall)
    }
    // the valley: the wing's tiles carried down solid onto the hall's slope (no pocket between the two roofs)
    for (let x = K.roof.along[0]; x < 12; x++) for (let z = K.roof.across[0]; z <= K.roof.across[1]; z++)
      for (let y = hallRoofY(x) + 1; y < wing.under[z]; y++) if (!c.get(x, y, z)) c.set(x, y, z, P.roof.full)
    bargeTrim(c, 'x', K.roof.along[1], K.roof.across, wing.under, `${P.trim}_stairs`)
  }

  // Tower (III): the porch raised into the village's landmark. The pillars carry on up as its quoins; its back third
  // rises out of the hall's roof on a beam across the hall's front bay.
  if (level >= 3) {
    const T = TOWER, S = T.shaft
    c.clearBox(S.x0 + 1, S.y0, S.z0 + 1, S.x1 - 1, S.y1, S.z1 - 1) // the porch roof and gables inside it go
    for (let x = S.x0; x <= S.x1; x++) log(x, PORCH.beamY, S.z1, 'x') // the beam the back wall stands on
    for (const x of [S.x0, S.x1]) log(x, PORCH.beamY, S.z1, 'z')
    floor(c, S.x0 + 1, S.z0 + 1, S.x1 - 1, S.z1 - 1, PORCH.beamY, P.floor) // the porch's ceiling, the tower's floor
    for (let x = S.x0; x <= S.x1; x++) for (let z = S.z0; z <= S.z1; z++) for (let y = S.y0; y <= S.y1; y++) {
      if (x !== S.x0 && x !== S.x1 && z !== S.z0 && z !== S.z1) continue
      c.set(x, y, z, P.wall)
    }
    quoins(c, [[S.x0, S.z0], [S.x1, S.z0], [S.x0, S.z1], [S.x1, S.z1]], S.y0, S.y1, P.dressed, 'polished_andesite')
    // the cornice: a dressed floor over the shaft, a parapet round the ledge left by the narrower belfry, lamps on its corners
    floor(c, S.x0, S.z0, S.x1, S.z1, T.corniceY, P.dressed)
    for (const x of [S.x0, S.x1]) railingRun(c, [[x, T.corniceY + 1, S.z0], [x, T.corniceY + 1, S.z1]], null, P.dressedWall)
    for (const x of [S.x0, S.x1]) for (const z of [S.z0, S.z1]) c.set(x, T.corniceY + 2, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
    // the belfry and its great lantern, the spire
    const B = T.belfry
    belfry(c, B, { post: P.frame, rail: `${P.trim}_fence`, brace: `${P.trim}_stairs`, ceiling: `${P.trim}_planks` })
    meetingBell(c, T.towerBell) // the tower's own bell (farther from the hall block than the porch's: never the meeting bell)
    for (const [x, z] of [[5, 1], [7, 1], [5, 3], [7, 3]]) c.set(x, B.top - 1, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
    needleSpire(c, T.spire, P.roof, 'lightning_rod')
    // the street face: a dressed band over the porch beam, a lancet, a second band and the village clock (a white
    // face with dark hands), right under the cornice
    for (let x = S.x0 + 1; x < S.x1; x++) { c.set(x, S.y0, S.z0, P.dressed); c.set(x, 9, S.z0, P.dressed) }
    for (const x of [S.x0 + 1, S.x1 - 1]) for (let y = 10; y <= 12; y++) c.set(x, y, S.z0, P.dressed) // the clock's stone frame
    // the dial: stone corners round it off, a pale ring, a dark hub
    for (let x = 5; x <= 7; x++) for (let y = 10; y <= 12; y++) {
      const ring = (x === 6) !== (y === 11)
      c.set(x, y, S.z0, x === 6 && y === 11 ? T.clock.hub : ring ? T.clock.ring : P.dressed)
    }
    // red banners on the shaft's sides
    for (const z of [1, 3]) {
      c.set(S.x0 - 1, 11, z, `${P.cloth}_wall_banner`, { facing: 'west' })
      c.set(S.x1 + 1, 11, z, `${P.cloth}_wall_banner`, { facing: 'east' })
    }
  }

  // --- Pass 2: openings ----------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  for (const half of ['lower', 'upper'])
    c.set(fd.at[0], fd.at[1] + (half === 'upper' ? 1 : 0), fd.at[2], `${P.trim}_door`, { facing: fd.facing, half, hinge: 'left', open: 'false', powered: 'false' })
  c.set(...fd.lintel, P.dressed)
  if (level >= 2) {
    const d = OPENINGS.councilDoorway
    for (let y = d.at[1]; y < d.at[1] + d.height; y++) c.clear(d.at[0], y, d.at[2])
    c.set(d.at[0], d.at[1] + d.height, d.at[2], P.dressed)
  }
  for (const w of OPENINGS.windows) {
    if (!onLevel(w, level)) continue
    window(c, w.cells, w.out, w.sill ? 'sill' : null, { glass: P.glass, sill: P.dressedStairs })
    if (!w.sill) continue
    const top = Math.max(...w.cells.map(p => p[1]))
    const [x, , z] = w.cells[0]
    if (top + 1 < H.beamY) c.set(x, top + 1, z, P.dressed) // a dressed head over every ground-floor window
  }
  if (level >= 2) c.clear(12, 1, 9) // the hall's east-back window looks into the council room now: no sill inside

  // --- Pass 3: depth and detail --------------------------------------------------------------------------------------
  meetingBell(c, OPENINGS.bell.at)
  for (const x of [3, 9]) c.set(x, 4, 2, 'lantern', { hanging: 'true', waterlogged: 'false' }) // the porch's lamps
  if (level >= 2) wallHearth(c, [COUNCIL.hearth.x, 1, COUNCIL.hearth.z], 'north', COUNCIL.hearth.stackTop, { stone, dressed: P.dressed })

  // --- Pass 4: interior (the plan's furniture) -------------------------------------------------------------------------
  c.set(6, 1, 10, 'aliveworkplace:village_hall', { facing: 'north' })
  for (const f of FURNITURE) if (onLevel(f, level)) parts[f.part](c, f)

  // --- Pass 5: ground ------------------------------------------------------------------------------------------------
  for (const p of OUTSIDE.lampPiers) masonLampPier(c, p, { pier: P.dressed, post: P.dressedWall })
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = drawLevel(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
