// Stone House, the desert look (ROADMAP 49.10b): assembles plan.mjs from the parts library (../parts) in passes. LEVEL=1..4
// picks the level; each draws the one below first and adds to it. Writes furniture.json beside it (and, for IV,
// out/cellar.txt) for rebuild.py. CUT=<y> cuts above y (a cutaway). Build all four: build.sh.
//   Level I    the sandstone house: flat roof behind a coped parapet, the windcatcher, the portal and its oriel
//   Level II   + the parlour: a lower mud-brick block east, an arcaded loggia in front of it
//   Level III  + the back room: a lower mud-brick block behind, its roof a terrace under a pergola, a door from the bedroom
//   Level IV   + the cellar under the hall, as the shipped house's, in sandstone
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, DIRS, OPP, slab, stair, plinth, floor, wallPanel, window, door, doorway, chimney, flight,
  railingRun, furnitureParts, cellar, hatchLadder, flatDeck, vigas, windcatcher, screenOriel, archBay, hearthFire,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const ashlar = mix(P.ashlar, 23), mud = mix(P.mud, 17)
const house = (x, y, z) => (y > MASSES.house.upperY ? mud : ashlar)(x, y, z) // stone below the frieze, mud brick above
// beam ends: thin spruce poles poking a block out of the wall (round 2: whole logs read as crates)
const beamEnds = (c, axis, out, at, y) => { for (const a of at) c.set(...(axis === 'z' ? [a, y, out] : [out, y, a]), P.viga) }

/** block: the walls of a flat-roofed block round W (x0..x1, z0..z1) from y 1 up to the parapet (deckY + 1), skipping the
 *  side `open` (a wall another block already stands on), the frieze at friezeY, cut piers at its free corners, the
 *  deck inside at deckY and a smooth coping on the parapet. */
function block(c, W, deckY, fill, { friezeY = 5, open = null } = {}) {
  const onOpen = (x, z) => (open === 'north' && z === W.z0) || (open === 'south' && z === W.z1) || (open === 'west' && x === W.x0) || (open === 'east' && x === W.x1)
  for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++) {
    if ((x !== W.x0 && x !== W.x1 && z !== W.z0 && z !== W.z1) || onOpen(x, z)) continue
    const corner = (x === W.x0 || x === W.x1) && (z === W.z0 || z === W.z1)
    for (let y = 1; y <= deckY + 1; y++) c.set(x, y, z, y === friezeY ? P.frieze : corner ? P.pier : fill)
    c.set(x, deckY + 2, z, P.dressed) // the coping course on the parapet: smooth stone, a full course (a slab lip reads as a roof round a pit)
  }
  flatDeck(c, W.x0 + 1, W.z0 + 1, W.x1 - 1, W.z1 - 1, deckY, P.deck)
}

const has = (o, level) => (o.from ?? 1) <= level && level <= (o.until ?? 9)
function windows(c, level, only = null) {
  for (const w of OPENINGS.windows) {
    if (!has(w, level) || (only && !only(w))) continue
    window(c, w.cells, w.out, null, { glass: P.glass })
    const top = w.cells.reduce((a, b) => (b[1] > a[1] ? b : a))
    const [ox, oz] = DIRS[w.out]
    const [x, y, z] = [top[0] + ox, top[1] + 1, top[2] + oz]
    if (w.brow !== false && c.inside(x, y, z) && !c.get(x, y, z)) slab(c, x, y, z, P.brow, 'top') // a stone brow: shade over the glass
    // round 2: a proud sill under a ground-storey window (the front's are in the arcade)
    const low = w.cells.reduce((a, b) => (b[1] < a[1] ? b : a))
    const [sx, sy, sz] = [low[0] + ox, low[1] - 1, low[2] + oz]
    if (w.sill !== false && w.out !== 'north' && low[1] === 2 && c.inside(sx, sy, sz) && !c.get(sx, sy, sz)) stair(c, sx, sy, sz, P.sillStair, OPP[w.out], 'top')
  }
}
const furnish = (c, level) => { for (const f of FURNITURE) if ((f.from ?? 1) === level) furnitureParts[f.part](c, f) } // what this level adds
const pots = (c, list) => { for (const [x, y, z, id] of list ?? []) c.set(x, y, z, id) }
/** azalea: a little flowering azalea tree in its own soil at [x, y, z]: rooted dirt, the bush, then a crown of
 *  leaves `crown` high. */
const azalea = (c, [x, y, z], crown) => {
  c.set(x, y, z, 'rooted_dirt')
  c.set(x, y + 1, z, 'flowering_azalea')
  for (let i = 0; i < crown; i++) c.set(x, y + 2 + i, z, i % 2 ? 'azalea_leaves' : 'flowering_azalea_leaves', { persistent: 'true', distance: '1', waterlogged: 'false' })
}
const jars = (c, list) => { for (const p of list ?? []) c.set(...p, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' }) }

// --- Level I: the sandstone house ------------------------------------------------------------------------------------
function levelOne(c) {
  const H = MASSES.house
  // Pass 1, block-out and structure: the plinth, the floors, the walls up to the coped parapet, the deck
  plinth(c, H.x0, H.z0, H.x1, H.z1, P.plinth)
  floor(c, H.x0 + 1, H.z0 + 1, H.x1 - 1, H.z1 - 1, 0, P.floor)
  block(c, H, H.deckY, house)
  wallPanel(c, [H.x0, H.z0], [H.x1, H.z0], 1, 1, P.dressed) // a smooth base course on the front
  floor(c, H.x0 + 1, H.z0 + 1, H.x1 - 1, H.z1 - 1, H.upperY, P.upperFloor)

  // the hearth's stack on the west wall, its breast stepped, smoking over the coping
  const F = MASSES.flue
  chimney(c, F.x, F.z, 0, F.top, P.pier, { breast: { axis: 'z', top: F.breastTop, stair: P.step } })
  // the windcatcher in the middle of the roof deck, over the bedroom's lamp
  const T = MASSES.tower
  windcatcher(c, T.x0, T.z0, T.y0, T.y1, { body: ashlar, corner: P.pier, cap: P.dressed, capTop: P.coping, slot: T.slot })

  // Pass 2, openings: the door in its portal, the windows, the partition and the stair
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: P.step })
  windows(c, 1)
  const pt = OPENINGS.partition
  for (const [x, z] of pt.cells) wallPanel(c, [x, z], [x, z], 1, H.upperY - 1, P.partition)
  wallPanel(c, [pt.door[0], pt.door[2]], [pt.door[0], pt.door[2]], 3, H.upperY - 1, P.partition)
  door(c, pt.door, 'south', 'south', { wood: P.wood })
  const st = OPENINGS.stairs
  for (const [x, y, z] of st.well) c.clear(x, y, z)
  flight(c, st.flight, st.rises, P.wood, P.dressed)
  railingRun(c, st.rail, P.wood)

  // Pass 3, depth and detail: the carved portal (piers, a lintel course, a carved panel over the door, a lantern under
  // the lintel) and the dark lattice oriel on it, the bedroom's window looking out through the screen
  const Po = MASSES.portal
  for (const x of Po.piers) {
    c.set(x, 0, Po.z, P.plinth)
    for (let y = 1; y <= Po.top; y++) c.set(x, y, Po.z, P.carved) // round 2: the door's surround carved all the way up
  }
  c.set(fd.at[0], 3, fd.at[2], P.carved)
  const O = Po.oriel
  screenOriel(c, O, { post: P.latticePost, lattice: P.lattice, roof: P.latticeRoof, sill: P.dressed })
  c.set(fd.at[0], Po.top, Po.z, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // round 2: a shallow arcade along the front (two round arches either side of the door's carved bay), its roof the
  // frieze's course; proud corner pilasters; the frieze carried round the house as a proud moulding
  const A = MASSES.arcade
  for (let x = H.x0; x <= H.x1; x++) if (!c.get(x, 0, A.z)) c.set(x, 0, A.z, P.plinth)
  for (const x of A.piers) for (let y = 1; y < A.roofY; y++) c.set(x, y, A.z, y === 1 ? P.carved : P.pier)
  for (const [a0, a1] of A.arches) archBay(c, { axis: 'x', line: A.z, a0, a1, y: A.roofY - 1 }, P.arch)
  for (let x = H.x0; x <= H.x1; x++) if (!c.get(x, A.roofY, A.z)) c.set(x, A.roofY, A.z, P.dressed)
  for (const [x, z] of MASSES.pilasters) {
    c.set(x, 0, z, P.plinth)
    // the ground storey's corners only, capped by the frieze's course (full-height piers read as a keep's towers)
    for (let y = 1; y <= H.upperY; y++) if (!(z === A.z && y <= A.roofY)) c.set(x, y, z, y === H.upperY ? P.dressed : P.pier)
  }
  const ring = []
  for (let z = H.z0; z <= H.z1; z++) ring.push([H.x0 - 1, z, 'east'], [H.x1 + 1, z, 'west'])
  for (let x = H.x0; x <= H.x1; x++) ring.push([x, H.z1 + 1, 'north'])
  for (const [x, z, f] of ring) {
    if (x === MASSES.flue.x && Math.abs(z - MASSES.flue.z) <= 1) continue // the flue's breast stands clear of it
    if (!c.get(x, H.upperY, z)) stair(c, x, H.upperY, z, P.moulding, f, 'top')
  }
  // beam ends: one even row at the deck's level, out of every wall
  const V = OUTSIDE.vigas
  beamEnds(c, 'z', H.z0 - 1, V.front, H.deckY)
  beamEnds(c, 'z', H.z1 + 1, V.back, H.deckY)
  beamEnds(c, 'x', H.x0 - 1, V.west, H.deckY)
  beamEnds(c, 'x', H.x1 + 1, V.east, H.deckY)

  // Pass 4, interior: the shipped house's furniture, cell for cell
  furnish(c, 1)

  // Pass 5, ground: potted cactus and dead bush at the corners, water jars by the portal
  pots(c, OUTSIDE.pots[1])
  jars(c, OUTSIDE.jars[1])
  azalea(c, OUTSIDE.tree, 4)
}

// --- Level II: the parlour and its loggia ----------------------------------------------------------------------------
function levelTwo(c) {
  const W = MASSES.parlour, L = W.loggia
  for (let z = 0; z <= 1; z++) c.clear(10, 0, z) // Level I's corner pot, where the loggia's floor goes
  for (let y = 0; y <= MASSES.house.upperY; y++) { c.clear(10, y, 1); c.clear(10, y, 9) } // Level I's east pilasters: in the loggia, behind the parlour
  for (let z = W.z0 - 1; z <= W.z1; z++) c.clear(10, MASSES.house.upperY, z) // the moulding, where the parlour stands
  // Pass 1: plinth and floor, mud-brick walls against the house's east wall, the deck and its coping
  plinth(c, W.x0 + 1, W.z0, W.x1, W.z1, P.plinth)
  floor(c, W.x0 + 1, W.z0 + 1, W.x1 - 1, W.z1 - 1, 0, P.floor)
  block(c, W, W.deckY, mud, { open: 'west' })
  // Pass 2, openings: the hall's east window knocked through, the parlour's windows
  doorway(c, OPENINGS.wingDoorway.at)
  windows(c, 2, w => (w.from ?? 1) === 2)
  // Pass 3, depth: the loggia, two round arches on smooth piers in front of the parlour, a third to the east
  for (let x = L.x0; x <= L.x1; x++) for (let z = L.z0; z <= L.z1; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, P.plinth)
  for (const x of L.piers) for (let y = 1; y < L.deckY; y++) c.set(x, y, L.z0, y === 1 ? P.carved : P.pier)
  archBay(c, { axis: 'x', line: L.z0, a0: L.x0 + 1, a1: L.x1 - 1, y: L.deckY - 1 }, P.arch)
  stair(c, L.x1, L.deckY - 1, L.z1, P.arch, 'south', 'top') // the east bay's head, its back to the parlour's corner
  for (let x = L.x0; x <= L.x1; x++) for (let z = L.z0; z <= L.z1; z++) if (!c.get(x, L.deckY, z)) c.set(x, L.deckY, z, P.deck)
  for (let x = L.x0; x <= L.x1; x++) slab(c, x, L.deckY + 1, L.z0, P.coping, 'bottom')
  slab(c, L.x1, L.deckY + 1, L.z1, P.coping, 'bottom')
  c.set(10, L.deckY - 1, 1, 'lantern', { hanging: 'true', waterlogged: 'false' }) // light at the parlour's window
  // Pass 4, interior
  furnish(c, 2)
  pots(c, OUTSIDE.pots[2])
  jars(c, OUTSIDE.jars[2])
}

// --- Level III: the back room and its terrace ------------------------------------------------------------------------
function levelThree(c) {
  const B = MASSES.backRoom, H = MASSES.house
  // everything a block out of the house's back wall (moulding, sills, brows, pilasters, beam ends, pots) where the
  // back room and its terrace go
  c.clearBox(0, 0, H.z1 + 1, 10, H.deckY + 2, H.z1 + 1)
  for (let y = 0; y <= H.upperY; y++) c.clear(H.x0 - 1, y, H.z1) // the west pilaster, now a pier between two volumes
  for (const [x, y, z] of [[3, 2, 9], [3, 3, 9]]) c.set(x, y, z, ashlar) // the box room's back window, now indoors
  for (const [x, y, z] of [[3, 4, 10], [7, 4, 10], [5, 8, 10], [3, 8, 10]]) c.clear(x, y, z) // their brows
  // Pass 1: plinth and floor, mud-brick walls against the house's back wall, the deck (the terrace) and its coping
  plinth(c, B.x0, B.z0 + 1, B.x1, B.z1, P.plinth)
  floor(c, B.x0 + 1, B.z0 + 1, B.x1 - 1, B.z1 - 1, 0, P.floor)
  block(c, B, B.deckY, mud, { open: 'north' })
  // Pass 2, openings: the kitchen's back window knocked through, the back room's windows, the terrace door up a step
  doorway(c, OPENINGS.backDoorway.at)
  windows(c, 3, w => (w.from ?? 1) === 3)
  const [dx, dy, dz] = B.door
  c.set(dx, dy - 1, dz, ashlar)
  door(c, B.door, 'south', 'south', { wood: P.wood })
  flight(c, [B.step], 'south', P.wood, P.upperFloor, B.step[1])
  // Pass 3, depth: the pergola, posts at the back corners, a lantern hung over the door
  const G = B.pergola
  for (const [x, z] of G.posts) for (let y = B.deckY + 1; y < G.y; y++) c.set(x, y, z, `${P.wood}_fence`)
  // the top a light frame of fence rails (a lattice to hang vines and cloth from, not a roof): along the back over the
  // posts, and out from the house's back wall over each post and the door
  for (let x = G.posts[0][0]; x <= G.posts[1][0]; x++) c.set(x, G.y, G.posts[0][1], `${P.wood}_fence`)
  for (const x of G.rafters) for (let z = B.z0 + 1; z < G.posts[0][1]; z++) c.set(x, G.y, z, `${P.wood}_fence`)
  c.set(5, G.y - 1, 11, 'lantern', { hanging: 'true', waterlogged: 'false' })
  pots(c, OUTSIDE.terrace.pots)
  azalea(c, OUTSIDE.terrace.tree, 1)
  jars(c, OUTSIDE.terrace.jars)
  // Pass 4, interior
  furnish(c, 3)
  pots(c, OUTSIDE.pots[3])
}

// --- Level IV: the cellar (the shipped house's, cell for cell, in sandstone) ------------------------------------------
function levelFour(c) {
  const C = MASSES.cellar
  const dug = cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, mix(P.cellarWall, 3), P.cellarFloor)
  hatchLadder(c, C.ladder.x, C.ladder.z, -C.depth + 1, 0, C.ladder.facing, P.wood)
  furnish(c, 4) // first: the beam goes round what the shelves already hold
  const Bm = C.beam // the summer beam across the store carrying the hall's floor, on one post in the middle
  for (let x = C.x0 + 1; x <= C.x1 - 1; x++) if (!c.get(x, -1, Bm.z)) c.set(x, -1, Bm.z, P.beam, { axis: 'x' })
  for (let y = -C.depth + 1; y <= -2; y++) c.set(Bm.post, y, Bm.z, P.beam, { axis: 'y' })
  return dug
}

export function draw(level = 1) {
  const c = level >= 4 ? new LiftedCanvas(SIZES[4], BASEMENT[4]) : new Canvas(SIZES[level])
  levelOne(c)
  if (level >= 2) levelTwo(c)
  if (level >= 3) levelThree(c)
  const dug = level >= 4 ? levelFour(c) : null
  if (level >= 4) {
    // With a cellar the drawing reaches below ground, so a pot on the ground must stand on a drawn block: a footing
    // under each one that has nothing under it (FloatingFurnishingsGameTests; as the desert cottage's Level IV, 49.10a).
    for (let x = 0; x < SIZES[4][0]; x++) for (let z = 0; z < SIZES[4][2]; z++) {
      const at = c.get(x, 0, z)
      if (at && String(at.id).includes('potted_') && !c.get(x, -1, z)) c.set(x, -1, z, 'smooth_sandstone')
    }
  }
  return { c, dug }
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  if (dug) { fs.mkdirSync(path.join(here, 'out'), { recursive: true }); fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(',')) }
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
