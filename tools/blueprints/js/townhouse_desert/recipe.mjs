// Burgher's Townhouse, the desert look (ROADMAP 49.10b): assembles plan.mjs from the parts library (../parts) in passes
// (block-out and structure, openings, depth and detail, interior, cellar and ground) for LEVEL = 1..4, each level the
// one before plus what it adds. Writes furniture.json (the furniture spots) and, for IV, out/cellar.txt for rebuild.py.
// CUT=<y> cuts above y (a cutaway). Build all four: build.sh.
//   Level I    the merchant's house: the street arcade, the rawshan, the top floor's lantern, the stack
//   Level II   + the counting house and its loggia, a terrace with a palm on the loggia's roof
//   Level III  + the strongroom tower and its windcatcher, the yard walled into a court with a basin and palms
//   Level IV   + the vault under the shop
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  LiftedCanvas, mix, DIRS, OPP, slab, stair, trapdoor, plinth, floor, wallPanel, window, door, doorway, chimney, flight,
  railingRun, furnitureParts, cellar, hatchLadder, archBay, rawshan, qaaLantern, badgirHead, datePalm,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE

// a rectangle's wall ring (x0..x1, z0..z1) from y0 to y1
function ring(c, x0, z0, x1, z1, y0, y1, id) {
  for (const [a, b] of [[[x0, z0], [x1, z0]], [[x0, z1], [x1, z1]], [[x0, z0], [x0, z1]], [[x1, z0], [x1, z1]]])
    wallPanel(c, a, b, y0, y1, id)
}
const pier = (c, x, z, y1) => { for (let y = 1; y <= y1; y++) c.set(x, y, z, y === 1 ? P.carved : P.pier) }
const lantern = (c, x, y, z) => c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
const jar = (c, p) => c.set(...p, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })

export function drawLevel(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT[level] ?? 0)
  const has = o => (o.from ?? 1) <= level && level <= (o.until ?? 9)
  const ashlar = mix(P.ashlar, 23), mud = mix(P.mud, 17)
  const H = MASSES.house, A = MASSES.arcade, Wg = MASSES.wing, L = Wg.loggia, T = MASSES.tower, C = MASSES.court

  // --- Pass 1: block-out and structure ---------------------------------------------------------------------------
  // House: the sandstone ground storey behind the arcade line, then the mud-brick upper storeys out to it
  plinth(c, H.x0, H.zUp, H.x1, H.z1, P.plinth)
  ring(c, H.x0, H.z0, H.x1, H.z1, 1, H.firstY - 1, ashlar)
  ring(c, H.x0, H.zUp, H.x1, H.z1, H.firstY, H.firstY, P.frieze) // the floor line
  floor(c, H.x0 + 1, H.z0, H.x1 - 1, H.z1 - 1, H.firstY, P.upperFloor)
  ring(c, H.x0, H.zUp, H.x1, H.z1, H.firstY + 1, H.courseY - 1, mud)
  ring(c, H.x0, H.zUp, H.x1, H.z1, H.courseY, H.courseY, P.course)
  floor(c, H.x0 + 1, H.z0, H.x1 - 1, H.z1 - 1, H.courseY, P.upperFloor)
  ring(c, H.x0, H.zUp, H.x1, H.z1, H.courseY + 1, H.deckY, mud)
  floor(c, H.x0 + 1, H.z0, H.x1 - 1, H.z1 - 1, H.deckY, P.deck)
  ring(c, H.x0, H.zUp, H.x1, H.z1, H.deckY + 1, H.deckY + 1, P.dressed) // the coping
  // the lantern over the top floor: the deck open under it, the room rising into it
  const Ln = MASSES.lantern
  c.clearBox(Ln.x0 + 1, H.deckY, Ln.z0 + 1, Ln.x1 - 1, H.deckY, Ln.z1 - 1)
  qaaLantern(c, Ln, { wall: ashlar, coping: P.dressed, ceiling: P.deck, glass: P.glass, windows: Ln.windows })
  // the hearth's stack up the back wall, its breast stepped, smoking over the coping
  const S = MASSES.stack
  chimney(c, S.x, S.z, 0, S.top, P.pier)
  for (const dx of [-1, 1]) for (let y = 0; y <= H.firstY; y++) c.set(S.x + dx, y, S.z, P.pier) // round 2: one breast, square-topped at the frieze, no stair steps

  if (level >= 2) { // the counting house: a sandstone storey, a mud bedroom storey, a flat deck
    plinth(c, Wg.x0, Wg.z0, Wg.x1, Wg.z1, P.plinth)
    floor(c, Wg.x0 + 1, Wg.z0 + 1, Wg.x1 - 1, Wg.z1 - 1, 0, P.floor)
    ring(c, Wg.x0, Wg.z0, Wg.x1, Wg.z1, 1, H.firstY - 1, ashlar)
    ring(c, Wg.x0, Wg.z0, Wg.x1, Wg.z1, H.firstY, H.firstY, P.frieze)
    floor(c, Wg.x0 + 1, Wg.z0 + 1, Wg.x1 - 1, Wg.z1 - 1, H.firstY, P.upperFloor)
    ring(c, Wg.x0, Wg.z0, Wg.x1, Wg.z1, H.firstY + 1, Wg.deckY, mud)
    floor(c, Wg.x0 + 1, Wg.z0 + 1, Wg.x1 - 1, Wg.z1 - 1, Wg.deckY, P.deck)
    ring(c, Wg.x0, Wg.z0, Wg.x1, Wg.z1, Wg.deckY + 1, Wg.deckY + 1, P.dressed)
    // the loggia in front of it: three narrow arches on z 2, closed on its east side, its roof a terrace
    plinth(c, L.x0, L.z0, L.x1, L.z1, P.plinth)
    for (const x of L.piers) pier(c, x, L.z0, L.deckY - 1)
    for (let x = L.x0; x <= L.x1; x++) if (!L.piers.includes(x)) slab(c, x, L.deckY - 1, L.z0, P.brow, 'top')
    pier(c, L.x1, L.z1, L.deckY - 1)
    for (let x = L.x0; x < L.x1; x++) c.set(x, L.deckY - 1, L.z1, P.dressed) // the soffit behind the arch heads
    floor(c, L.x0, L.z0, L.x1, L.z1, L.deckY, P.deck)
    for (let x = L.x0; x <= L.x1; x++) c.set(x, L.deckY + 1, L.z0, P.dressed) // the terrace's parapet
    for (let z = L.z0; z <= L.z1; z++) c.set(L.x1, L.deckY + 1, z, P.dressed)
  }

  if (level >= 3) { // the strongroom tower and its windcatcher
    plinth(c, T.x0, T.z0, T.x1, T.z1, P.plinth)
    floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, 0, P.floor)
    ring(c, T.x0, T.z0, T.x1, T.z1, 1, T.deckY - 1, ashlar)
    for (const y of T.floors) floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, y, P.upperFloor)
    ring(c, T.x0, T.z0, T.x1, T.z1, T.deckY, T.deckY, P.frieze)
    for (const y of T.floors) ring(c, T.x0, T.z0, T.x1, T.z1, y, y, P.band) // round 2: a mud-brick band at each floor
    floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, T.deckY, P.deck)
    badgirHead(c, { x0: T.x0, z0: T.z0, x1: T.x1, z1: T.z1, y0: T.head[0], y1: T.head[1] }, { body: ashlar, fin: P.pier, cap: P.dressed, cornice: P.cornice })
    // the court: the yard behind the house walled in, a gate in its west wall
    for (let z = MASSES.house.z1 + 1; z <= C.back; z++) { wallPanel(c, [C.west, z], [C.west, z], 1, C.wallY - 1, mud); c.set(C.west, C.wallY, z, P.dressed) }
    for (let x = C.west; x < T.x0; x++) { wallPanel(c, [x, C.back], [x, C.back], 1, C.wallY - 1, mud); c.set(x, C.wallY, C.back, P.dressed) }
    for (let z = MASSES.house.z1 + 1; z <= C.back; z++) c.set(C.west, 0, z, P.plinth)
    for (let x = C.west; x < T.x0; x++) c.set(x, 0, C.back, P.plinth)
    const [gx, gz] = C.gate
    doorway(c, [gx, 1, gz])
    for (const [x, z] of [[C.west, MASSES.house.z1 + 1], [C.west, C.back], [C.west, gz - 1], [C.west, gz + 1]]) { // round 2: capped piers, no stub
      for (let y = 1; y < C.wallY + 1; y++) c.set(x, y, z, P.pier)
      c.set(x, C.wallY + 1, z, P.dressed)
    }
    const pave = mix([['smooth_sandstone', 3], ['sandstone', 2], ['cut_sandstone', 1]], 41)
    for (let x = C.west + 1; x < T.x0; x++) for (let z = MASSES.house.z1 + 1; z < C.back; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, pave)
    // the basin: a smooth rim round still water
    const B = C.basin
    for (let x = B.x0; x <= B.x1; x++) for (let z = B.z0; z <= B.z1; z++) {
      const rim = x === B.x0 || x === B.x1 || z === B.z0 || z === B.z1
      c.set(x, 0, z, P.plinth)
      if (rim) c.set(x, 1, z, P.dressed)
      else c.set(x, 1, z, 'water', { level: '0' })
    }
  }

  // --- Pass 2: openings ------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: P.step })
  if (has(OPENINGS.wingDoor)) {
    const wd = OPENINGS.wingDoor
    door(c, wd.at, wd.facing, 'north', { wood: P.wood, step: wd.step, stepStair: P.step })
  }
  for (const w of OPENINGS.windows) if (has(w)) window(c, w.cells, w.out, null, { glass: P.glass })
  for (const d of OPENINGS.doorways) if (has(d)) for (const at of d.at) doorway(c, at)
  const pt = OPENINGS.partition
  wallPanel(c, [pt.x0, pt.z], [pt.x1, pt.z], H.firstY + 1, H.courseY - 1, P.partition)
  door(c, pt.door, 'south', 'south', { wood: P.wood })
  const st = OPENINGS.stairs, at = OPENINGS.attic
  for (const [x, y, z] of [...st.well, ...at.well]) c.clear(x, y, z)
  flight(c, st.flight, st.rises, P.wood, P.upperFloor)
  flight(c, at.flight, at.rises, P.wood, P.partition, H.firstY + 1)
  railingRun(c, st.rail, P.wood)
  railingRun(c, at.rail, P.wood)
  if (level >= 3) hatchLadder(c, T.ladder.x, T.ladder.z, T.ladder.y0, T.ladder.top, T.ladder.facing, P.wood)

  // --- Pass 3: depth and detail ----------------------------------------------------------------------------------
  // the street arcade: piers on the upper storeys' line, round arches over the shop windows, the carved door bay
  for (const x of A.piers) pier(c, x, A.z, H.firstY - 1)
  for (const [a0, a1] of A.arches) archBay(c, { axis: 'x', line: A.z, a0, a1, y: H.firstY - 1 }, P.arch)
  c.set(A.door, H.firstY - 1, A.z, P.carved) // the keystone over the door bay
  lantern(c, A.door, H.firstY - 2, A.z)
  for (const x of [fd.at[0] - 1, fd.at[0] + 1]) for (let y = 1; y <= 3; y++) c.set(x, y, fd.at[2], P.carved) // the door's carved jambs
  c.set(fd.at[0], 3, fd.at[2], P.carved)
  // the rawshan over the arcade
  const R = MASSES.rawshan
  rawshan(c, R, { post: P.latticePost, lattice: P.lattice, sill: P.latticeSill, roof: P.latticeRoof, corbel: P.latticeRoof })
  // the frieze carried round the sides and back as a proud moulding (clear of the stack's breast)
  const out = []
  for (let z = H.zUp; z <= H.z1; z++) out.push([H.x0 - 1, z, 'east'], [H.x1 + 1, z, 'west'])
  if (level < 3) for (let x = H.x0; x <= H.x1; x++) if (Math.abs(x - S.x) > 2) out.push([x, H.z1 + 1, 'north']) // III: in the court, no moulding (it reads as a roof over the yard)
  if (level >= 2) for (let z = Wg.z0; z <= Wg.z1 - (level >= 3 ? 1 : 0); z++) out.push([Wg.x1 + 1, z, 'west']) // (III: not under the windcatcher's cornice, or the kit carries a column up to it)
  if (level === 2) for (let x = Wg.x0; x <= Wg.x1; x++) out.push([x, Wg.z1 + 1, 'north'])
  for (const [x, z, f] of out) if (c.inside(x, H.firstY, z) && !c.get(x, H.firstY, z)) stair(c, x, H.firstY, z, P.moulding, f, 'top')
  // window dressing: a lattice screen over the hall storey's and the tower's windows; a sill under the ground
  // storey's; a stone brow over the rest (not under the arcade or the rawshan)
  for (const w of OPENINGS.windows) {
    if (!has(w) || w.brow === false) continue
    const [ox, oz] = DIRS[w.out]
    if (w.screen) { for (const [x, y, z] of w.cells) trapdoor(c, x + ox, y, z + oz, P.lattice, w.out, 'bottom', true); continue } // flat on the wall over the glass
    const top = w.cells.reduce((a, b) => (b[1] > a[1] ? b : a)), low = w.cells.reduce((a, b) => (b[1] < a[1] ? b : a))
    const [bx, by, bz] = [top[0] + ox, top[1] + 1, top[2] + oz], [sx, sy, sz] = [low[0] + ox, low[1] - 1, low[2] + oz]
    if (top[1] !== H.firstY - 2 && c.inside(bx, by, bz) && !c.get(bx, by, bz)) slab(c, bx, by, bz, P.brow, 'top') // (the ground storey's: the moulding over them is their brow)
    if (low[1] === 2 && c.inside(sx, sy, sz) && !c.get(sx, sy, sz)) stair(c, sx, sy, sz, P.sillStair, OPP[w.out], 'top')
  }
  // beam ends: thin spruce poles out of the walls at the deck line
  const V = OUTSIDE.vigas, beam = (x, y, z) => { if (c.inside(x, y, z) && !c.get(x, y, z)) c.set(x, y, z, P.viga) }
  for (const x of V.front) beam(x, H.deckY, H.zUp - 1)
  for (const z of V.west) beam(H.x0 - 1, H.deckY, z)
  if (level < 2) for (const z of V.east) beam(H.x1 + 1, H.deckY, z)
  for (const x of V.back) beam(x, H.deckY, H.z1 + 1)
  if (level >= 2) for (const z of V.wingEast) beam(Wg.x1 + 1, Wg.deckY, z)
  // lights: under the loggia, in the court
  if (level >= 2) lantern(c, 13, L.deckY - 1, 2)

  // --- Pass 4: interior (the plan's furniture) -------------------------------------------------------------------
  for (const f of FURNITURE) if (has(f)) furnitureParts[f.part](c, f)
  for (const f of FURNITURE) if (has(f) && f.throat) c.clear(...f.throat)

  // --- Pass 5: the vault (IV) and the ground ---------------------------------------------------------------------
  let dug = null
  if (level >= 4) {
    const Cv = MASSES.cellar
    dug = cellar(c, Cv.x0, Cv.z0, Cv.x1, Cv.z1, Cv.depth, mix(P.cellarWall, 3), P.cellarFloor)
    hatchLadder(c, Cv.ladder.x, Cv.ladder.z, -Cv.depth + 1, 0, Cv.ladder.facing, P.wood)
    for (const z of Cv.ribs) { // two stone ribs across the vault, springing from upside-down stairs at the walls
      for (let x = Cv.x0 + 2; x <= Cv.x1 - 2; x++) c.set(x, -1, z, P.pier)
      stair(c, Cv.x0 + 1, -1, z, P.sillStair, 'west', 'top')
      stair(c, Cv.x1 - 1, -1, z, P.sillStair, 'east', 'top')
    }
  }
  for (let l = 1; l <= level && l <= 3; l++) {
    for (const [x, y, z, id] of OUTSIDE.pots[l] ?? []) if (!c.get(x, y, z)) c.set(x, y, z, id)
    for (const p of OUTSIDE.jars[l] ?? []) if (!c.get(...p)) jar(c, p)
  }
  if (level >= 2) datePalm(c, OUTSIDE.terracePalm, { ...P.palm, height: 6, reach: 3, skip: (x, y, z) => z < L.z0 }) // no frond out over the street: it would roof the loggia
  if (level >= 3) for (const [x, z] of C.palms) datePalm(c, [x, 0, z], { ...P.palm, height: 8, reach: 3 })
  if (dug) {
    // With a cellar the drawing reaches below ground, so a pot (potted plant or decorated pot) on the ground must stand
    // on a drawn block: a sandstone footing under each one with nothing under it (FloatingFurnishingsGameTests).
    for (let x = 0; x < SIZES[level][0]; x++) for (let z = 0; z < SIZES[level][2]; z++) {
      const at = c.get(x, 0, z)
      if (at && /pot/.test(String(at.id)) && !c.get(x, -1, z)) c.set(x, -1, z, 'smooth_sandstone')
    }
  }
  return { c, dug }
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = drawLevel(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  if (dug) { fs.mkdirSync(path.join(here, 'out'), { recursive: true }); fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(',')) }
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
