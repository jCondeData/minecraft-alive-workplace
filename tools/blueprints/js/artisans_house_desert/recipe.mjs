// Artisan's House, desert look (49.10g), the recipe: assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, roof, openings, depth and detail, interior, ground) for LEVEL = 1..3: each level draws the
// one below first and adds to it (II keeps I's cells, III is II above the ground with the clay cellar under it). Writes
// furniture.json and, for III, out/cellar.txt (rebuild.py --cellar). Build every level: build.sh. CUT=<y>: a cutaway.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  SIZE, BASEMENT, PALETTE, HOUSE, IWAN, ORIEL, KILN, ROOF_SHADE, DRYING, CATCHER, ROOF_LADDER, SHADE, WING, YARD, OPENINGS, FURNITURE,
  OUTSIDE, CELLAR,
} from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, stair, slab, plinth, floor, post, wallPanel, window, door, quoins, flight, stairwell,
  railingRun, furnitureParts, windcatcher, vigas, rowParapet, cellar, hatchLadder,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const ashlar = mix(P.ashlar, 5), mud = mix(P.mud, 6), drum = mix(P.kiln.drum, 3)
const pot = (c, x, y, z, id) => c.set(x, y, z, id, id === 'decorated_pot' ? { facing: 'north', cracked: 'false', waterlogged: 'false' } : {})

// round cells about (cx, cz): r 3 a 7-round, r 2 a 5-round, r 1 a 3x3
const inDisc = (dx, dz, r) => dx * dx + dz * dz <= r * r + r
// a dome's step: the cells of disc r not in disc r - 1, as stairs whose tall half faces the middle
function domeRing(c, cx, cz, r, y, stairs, full) {
  for (let dx = -r; dx <= r; dx++) for (let dz = -r; dz <= r; dz++) {
    if (!inDisc(dx, dz, r)) continue
    if (r > 0 && inDisc(dx, dz, r - 1) && (r - 1 > 0 || (dx === 0 && dz === 0))) { c.set(cx + dx, y, cz + dz, full); continue }
    if (r === 0) { c.set(cx, y, cz, full); continue }
    const facing = Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? 'east' : 'west') : (dz < 0 ? 'south' : 'north')
    stair(c, cx + dx, y, cz + dz, stairs, facing)
  }
}

// --- Level I: the potter's house, the domed kiln, the drying roof ------------------------------------------------------
function levelOne(c) {
  const H = HOUSE, { x0, x1, z0, z1 } = H.walls, U = H.upper

  // Pass 1: block-out and structure. A smooth plinth under the house and its iwan; smooth flags in the workroom, packed
  // mud in the hall; coursed sandstone to the upper floor, the cut course at it, mud brick up to the deck
  plinth(c, x0, U.z0, x1, z1, P.plinth)
  floor(c, x0 + 1, z0 + 1, x1 - 1, 5, 0, P.floor)
  floor(c, x0 + 1, 6, x1 - 1, z1 - 1, 0, P.hallFloor)
  for (const [a, b] of [[[x0, z0], [x1, z0]], [[x0, z1], [x1, z1]], [[x0, z0], [x0, z1]], [[x1, z0], [x1, z1]]])
    wallPanel(c, a, b, 1, H.groundTop, ashlar)
  quoins(c, [[x0, z1], [x1, z1]], 1, H.groundTop, P.quoin)
  for (const [a, b] of [[[U.x0, U.z0], [U.x1, U.z0]], [[U.x0, U.z1], [U.x1, U.z1]], [[U.x0, U.z0], [U.x0, U.z1]], [[U.x1, U.z0], [U.x1, U.z1]]]) {
    wallPanel(c, a, b, H.floorY, H.floorY, P.course)
    wallPanel(c, a, b, H.floorY + 1, H.upperTop, mud)
  }
  floor(c, U.x0 + 1, U.z0 + 1, U.x1 - 1, U.z1 - 1, H.floorY, P.upperFloor)

  // the iwan: the upper storey carried over the street front on two piers and a broad round arch under a cut lintel
  // (round 2: two deep, z 0..1, a barrel vault whose round head is red sandstone against the pale wall, a ledge over its
  // front bay that the oriel stands on)
  const I = IWAN
  for (const z of I.zs) {
    for (const x of I.piers) for (let y = 1; y <= I.lintelY; y++) c.set(x, y, z, P.pier)
    for (let x = I.a0; x <= I.a1; x++) c.set(x, I.lintelY, z, P.lintel)
    stair(c, I.a0, I.headY, z, P.archRed, 'west', 'top') // the haunches
    stair(c, I.a1, I.headY, z, P.archRed, 'east', 'top')
    stair(c, I.a0 + 1, I.lintelY, z, P.archRed, 'west', 'top') // the shoulders
    stair(c, I.a1 - 1, I.lintelY, z, P.archRed, 'east', 'top')
    c.set((I.a0 + I.a1) / 2, I.lintelY, z, P.keystone)
  }
  for (let x = I.piers[0]; x <= I.piers[1]; x++) c.set(x, I.lintelY + 1, I.zs[0], P.ledge) // the front bay's roof
  for (const x of I.piers) slab(c, x, I.lintelY + 2, I.zs[0], P.coping, 'bottom') // capping the piers' heads
  c.set(...I.lamp, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // Pass 2: the roof: the deck (its rim mud brick), the coped parapet, the windcatcher flush with the back corner
  c.fill(U.x0, H.deckY, U.z0, U.x1, H.deckY, U.z1, P.deck)
  for (const [a, b] of [[[U.x0, U.z0], [U.x1, U.z0]], [[U.x0, U.z1], [U.x1, U.z1]], [[U.x0, U.z0], [U.x0, U.z1]], [[U.x1, U.z0], [U.x1, U.z1]]])
    wallPanel(c, a, b, H.deckY, H.deckY, mud)
  const K = CATCHER, inCatcher = (x, z) => x >= K.x0 && x <= K.x0 + 2 && z >= K.z0 && z <= K.z0 + 2
  rowParapet(c, { x0: U.x0, z0: U.z0, x1: U.x1, z1: U.z1, y: H.parapetY }, { wall: P.parapet, coping: P.coping }, inCatcher)
  windcatcher(c, K.x0, K.z0, K.y0, K.y1, { body: mix(P.catcher.body, 9), corner: P.catcher.corner, cap: P.catcher.cap, capTop: P.catcher.capTop })
  // the drying floor's shade: four palm posts, two palm beams, palm-rib matting between them; the greenware under it
  const R = ROOF_SHADE
  for (const [x, z] of R.posts) post(c, x, z, H.parapetY, R.y - 1, P.palm.post)
  for (let z = R.z0; z <= R.z1; z++) for (let x = R.x0; x <= R.x1; x++) slab(c, x, R.y, z, P.palm.mat, 'bottom') // thin matting on the posts' heads
  for (const [x, id, z] of DRYING) pot(c, x, H.parapetY, z, id)
  const RL = ROOF_LADDER
  hatchLadder(c, RL.x, RL.z, RL.y0, H.deckY, RL.facing, P.wood)

  // the kiln: a round updraft kiln, its terracotta drum on a smooth platform, a band, a stepped mud dome, the fire at
  // its smoke hole; hollow inside (the firing chamber)
  const Kn = KILN, { cx, cz } = Kn
  for (let dx = -Kn.r; dx <= Kn.r; dx++) for (let dz = -Kn.r; dz <= Kn.r; dz++) {
    if (!inDisc(dx, dz, Kn.r)) continue
    c.set(cx + dx, 0, cz + dz, P.kiln.platform)
    const shell = !inDisc(dx, dz, Kn.r - 1)
    for (let y = 1; y <= Kn.drumTop; y++) if (shell) c.set(cx + dx, y, cz + dz, drum)
    c.set(cx + dx, Kn.bandY, cz + dz, P.kiln.band)
  }
  // the drum bonded to the house wall in its two re-entrant corners (no pocket left between the curve and the wall)
  for (const z of [cz - 2, cz + 2]) { c.set(8, 0, z, P.kiln.platform); for (let y = 1; y <= Kn.drumTop; y++) c.set(8, y, z, drum); c.set(8, Kn.bandY, z, P.kiln.band) }
  // the dome (round 2): a smooth slab shoulder ring over the band, one stair course, a ring of slabs round the crown,
  // so the outline curves instead of stepping
  const [d0, d1, d2] = Kn.dome
  for (let dx = -Kn.r; dx <= Kn.r; dx++) for (let dz = -Kn.r; dz <= Kn.r; dz++) {
    if (!inDisc(dx, dz, Kn.r)) continue
    if (inDisc(dx, dz, Kn.r - 1)) c.set(cx + dx, d0, cz + dz, P.kiln.dome)
    else slab(c, cx + dx, d0, cz + dz, P.kiln.shoulder, 'bottom')
  }
  domeRing(c, cx, cz, Kn.r - 1, d1, P.kiln.domeStairs, P.kiln.dome)
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++)
    if (dx || dz) slab(c, cx + dx, d2, cz + dz, P.kiln.crown, 'bottom'); else c.set(cx, d2, cz, P.kiln.dome)
  c.set(cx, Kn.fireY, cz, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  // the loading wicket on its back, bricked up with mud for the firing, a carved lintel over it
  const Wk = Kn.wicket
  for (const y of Wk.cells) c.set(Wk.x, y, Wk.z, P.kiln.wicket)
  c.set(Wk.x, Wk.lintelY, Wk.z, P.carved)
  // the fire mouth into the workroom, the stoke hole on the yard side under a mud hood, its flagged stoking place
  const km = OPENINGS.kilnMouth, sh = OPENINGS.stokeHole
  c.set(...km.at, 'furnace', { facing: km.facing, lit: 'false' })
  c.set(...sh.at, 'furnace', { facing: sh.facing, lit: 'false' })
  stair(c, sh.at[0] + 1, sh.at[1] + 1, sh.at[2], P.kiln.hood, 'west', 'top')
  for (let z = 3; z <= 5; z++) c.set(15, 0, z, P.paving)

  // the fuel shade behind the kiln: palm posts and a beam, palm-rib matting off the house wall
  const S = SHADE
  for (let x = S.x0; x <= S.x1; x++) for (let z = S.z0; z <= S.z1; z++) c.set(x, 0, z, 'packed_mud')
  for (const [x, z] of S.posts) post(c, x, z, 1, S.y - 1, P.palm.post)
  for (let z = S.z0; z <= S.z1; z++) {
    c.set(S.x1, S.y, z, P.palm.beam, { axis: 'z' })
    for (let x = S.x0; x < S.x1; x++) slab(c, x, S.y, z, P.palm.mat, 'bottom')
  }
  for (let x = S.x0; x < S.x1; x++) slab(c, x, S.y, S.z0 - 1, P.palm.mat, 'bottom') // on to the kiln's back

  // beam ends a block out under the deck: the west side, the back, the east side's back half (the kiln has the front)
  const V = H.vigas
  vigas(c, 'x', x0 - 1, V.west, V.y, P.viga)
  vigas(c, 'z', z1 + 1, V.back, V.y, P.viga)
  vigas(c, 'x', x1 + 1, V.east, V.y, P.viga)

  // Pass 3: openings
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood })
  stair(c, ...fd.step, P.step, 'south')
  for (const w of OPENINGS.windows) {
    window(c, w.cells, w.out, w.dress ?? null, { glass: P.glass, sill: P.sill, pots: w.pots ?? [] })
    const top = Math.max(...w.cells.map(p => p[1]))
    if (w.brow !== false && top + 1 === H.groundTop) for (const [x, , z] of w.cells.filter(p => p[1] === top)) c.set(x, top + 1, z, P.brow)
  }
  const st = OPENINGS.stairs
  stairwell(c, st.well)
  flight(c, st.flight, st.rises, P.wood, P.under)
  railingRun(c, OPENINGS.rail, P.wood)

  // Pass 4: depth and detail: the lattice oriel over the arch, before the bedroom's middle window
  // (round 2) a lattice bay, not a dark door: slim fence corner posts, the front a screen of open trapdoors, the two side
  // faces lattice too (trapdoors flat on the corner cells' outer faces), a dark-oak cornice a block out each side at
  // y 9 on corbel stairs, standing on the iwan's ledge
  const O = ORIEL
  for (let y = O.y0; y < O.y1; y++) {
    for (const x of [O.x0, O.x1]) c.set(x, y, O.z, `${P.latticeWood}_fence`)
    for (let x = O.x0 + 1; x < O.x1; x++) c.set(x, y, O.z, `${P.latticeWood}_fence`) // the front screen: bars, light between
  }
  for (let x = O.x0; x <= O.x1; x++) slab(c, x, O.y1, O.z, P.latticeRoof, 'bottom')
  for (const x of [O.x0 - 1, O.x1 + 1]) stair(c, x, O.y1, O.z, P.latticeCorbel, x < O.x0 ? 'east' : 'west', 'top') // corbelled ends

  // Pass 5: interior: the shipped house's furniture, cell for cell
  for (const f of FURNITURE[1]) furnitureParts[f.part](c, f)
  pot(c, 6, 2, 3, 'decorated_pot') // wares on the kept barrel

  // Pass 6: ground: the street's flags with the potter's wares set out either side of the step, the kept wares barrel
  // in the iwan (a jar on it), fuel by the stoke hole (palm fronds one side, brushwood the other), the fuel shade's stock
  for (let x = 0; x <= 8; x++) if (!c.get(x, 0, 0)) c.set(x, 0, 0, P.paving)
  for (const [x, id] of [[0, 'decorated_pot'], [2, 'flower_pot'], [6, 'flower_pot']]) pot(c, x, 1, 0, id)
  c.set(...OUTSIDE.wares, 'barrel', { facing: 'up', open: 'false' })
  pot(c, OUTSIDE.wares[0], 2, OUTSIDE.wares[2], 'decorated_pot')
  // (round 2) no fuel left out by the stoke hole: the brushwood and fronds are stacked under the fuel shade
  const sd = OUTSIDE.shed
  for (const [x, y, z] of [[9, 1, 7], [9, 2, 7], [9, 3, 7]]) c.set(x, y, z, y % 2 ? 'hay_block' : P.palm.beam, y % 2 ? { axis: 'y' } : { axis: 'x' })
  for (let z = S.z0 - 1; z <= S.z1; z++) for (let y = 1; y <= sd.logs.high; y++)
    c.set(sd.logs.x, y, z, (y + z) % 2 ? 'hay_block' : P.palm.beam, (y + z) % 2 ? { axis: 'y' } : { axis: 'x' })
  for (const p of sd.clay) c.set(...p, 'clay')
  c.set(...sd.cauldron, 'cauldron')
}

// --- Level II: the journeyman's domed cell and the drying yard -------------------------------------------------------
function levelTwo(c) {
  const hb = OPENINGS.windows.find(w => w.level1Only)
  for (const [x, y, z] of hb.cells) c.set(x, y, z, ashlar) // the hall's back window walled up for the doorway
  const Wg = WING, { x0, x1, z0, z1 } = Wg
  // Pass 1: the cell on its plinth, mud-brick walls, a flat ceiling at y 5 whose rim steps in as a dome to a cap
  plinth(c, x0, z0, x1, z1, P.plinth)
  floor(c, x0 + 1, z0, x1 - 1, z1 - 1, 0, P.hallFloor)
  for (const [a, b] of [[[x0, z0], [x0, z1]], [[x1, z0], [x1, z1]], [[x0, z1], [x1, z1]]]) wallPanel(c, a, b, 1, 4, mud)
  quoins(c, [[x0, z1], [x1, z1]], 1, 4, P.quoin)
  const y = Wg.deckY
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const rim = x === x0 || x === x1 || z === z1
    if (!rim) { c.set(x, y, z, P.kiln.dome); continue }
    const facing = x === x0 ? 'east' : x === x1 ? 'west' : 'north'
    if ((x === x0 || x === x1) && z === z1) c.set(x, y, z, P.kiln.dome) // the back corners full (no stair shows its end)
    else stair(c, x, y, z, P.kiln.domeStairs, facing)
  }
  domeRing(c, 4, 15, 1, y + 1, P.kiln.domeStairs, P.kiln.dome)
  slab(c, 4, y + 2, 15, P.coping, 'bottom')
  // Pass 2: openings
  const wd = OPENINGS.wingDoorway
  for (let yy = wd.at[1]; yy < wd.at[1] + wd.height; yy++) c.clear(wd.at[0], yy, wd.at[2])
  c.set(wd.at[0], 0, wd.at[2], P.hallFloor)
  for (const w of OPENINGS.wingWindows) window(c, w.cells, w.out, w.dress ?? null, { glass: P.glass, sill: P.sill, pots: w.pots ?? [] })
  // Pass 5: interior
  for (const f of FURNITURE[2]) furnitureParts[f.part](c, f)
  // Pass 6: the drying yard: sand, a palm-rib shade on four posts, the fired pots on a mud bench and the kept barrel,
  // the quench jar (the kept cauldron), a date palm in the corner
  const Y = YARD, g = Y.ground
  for (let x = g.x0; x <= g.x1; x++) for (let z = g.z0; z <= g.z1; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, 'sand')
  const S = Y.shade
  for (const [x, z] of S.posts) post(c, x, z, 1, S.y - 1, P.palm.post)
  for (const z of [S.z0, S.z1]) for (let x = S.x0; x <= S.x1; x++) c.set(x, S.y, z, P.palm.beam, { axis: 'x' })
  for (let z = S.z0 + 1; z < S.z1; z++) for (let x = S.x0; x <= S.x1; x++) slab(c, x, S.y, z, P.palm.mat, 'bottom')
  const r = OUTSIDE.rack
  c.set(r.x0 + 1, 1, r.z, 'barrel', { facing: 'up', open: 'false' }) // the kept barrel (11, 1, 15)
  for (const p of Y.bench) c.set(...p, P.kiln.dome)
  c.set(12, 1, 15, 'cauldron') // the quench jar
  for (const [x, yy, z, id] of Y.pots) pot(c, x, yy, z, id)
  // the date palm (round 2), drawn by hand: a tall bare trunk, a small tuft, four long fronds four out drooping two courses to
  // their tips and four short ones, one leaf thick
  const [px, py, pz] = Y.palm, top = py + 7, leaf = { persistent: 'true', distance: '1', waterlogged: 'false' }
  const put = (x, yy, z) => { if (c.inside(x, yy, z) && !c.get(x, yy, z)) c.set(x, yy, z, 'jungle_leaves', leaf) }
  c.set(px, py, pz, 'sand')
  for (let yy = py + 1; yy <= top; yy++) c.set(px, yy, pz, 'jungle_log', { axis: 'y' })
  put(px, top + 1, pz)
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]])
    for (const [k, dy] of [[1, 0], [2, 0], [3, 0], [3, -1], [4, -1], [4, -2]]) put(px + k * dx, top + dy, pz + k * dz)
  for (const [dx, dz] of [[1, 1], [1, -1], [-1, 1], [-1, -1]]) put(px + dx, top, pz + dz)
}

// --- Level III: the clay cellar ----------------------------------------------------------------------------------------
function levelThree(c) {
  const Cl = CELLAR
  const inside = cellar(c, Cl.x0, Cl.z0, Cl.x1, Cl.z1, Cl.depth, mix(P.cellarWall, 4), P.cellarFloor)
  const L = OPENINGS.hatch
  hatchLadder(c, L.x, L.z, -3, 0, L.facing, P.wood)
  for (let x = 2; x <= 6; x++) {
    c.set(x, -3, 3, 'chest', { facing: 'south', type: 'single', waterlogged: 'false' })
    c.set(x, -2, 3, 'barrel', { facing: 'south', open: 'false' })
  }
  for (let z = 6; z <= 10; z++) c.set(6, -3, z, 'barrel', { facing: 'west', open: 'false' })
  for (const z of [7, 9]) c.set(6, -2, z, 'barrel', { facing: 'west', open: 'false' })
  for (const [x, y, z] of [[2, -3, 11], [3, -3, 11], [2, -2, 11], [2, -3, 10]]) c.set(x, y, z, 'clay')
  c.set(4, -3, 8, `${P.wood}_fence`); c.set(4, -2, 8, 'brown_carpet')
  for (const z of [5, 9]) c.set(4, -1, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  return inside
}

export function draw(level = 1) {
  const c = level === 3 ? new LiftedCanvas(SIZE[3], BASEMENT) : new Canvas(SIZE[level])
  levelOne(c)
  if (level >= 2) levelTwo(c)
  if (level >= 3) c.cellarInside = levelThree(c)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = level === 1 ? 'artisans_house' : `artisans_house_${level}`
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  fs.mkdirSync(path.join(here, 'out'), { recursive: true })
  if (c.cellarInside) fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), c.cellarInside.join(','))
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
