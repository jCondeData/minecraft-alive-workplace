// Burgher's Townhouse (ROADMAP 49), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes
// (block-out, roofs and gables, openings, depth and detail, interior, cellar and ground) for LEVEL = 1..4, each level
// the one before plus what it adds (II the back house, III the tower, IV the store below). Writes furniture.json (the
// furniture spots) and, for IV, out/cellar.txt. Build all four: build.sh.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, GOODS, OUTSIDE } from './plan.mjs'
import {
  LiftedCanvas, mix, slab, stair, plinth, floor, post, wallPanel, window, door, doorway, gableRoof, gableWall,
  bargeTrim, steppedGable, pyramidRoof, flowerBed, flight, railingRun, furnitureParts, cellar, hatchLadder, lampPost,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function drawLevel(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT[level] ?? 0)
  const P = PALETTE, M = MASSES.main, Wg = MASSES.wing, T = MASSES.tower, ch = MASSES.chimney
  const base = mix(P.plinth, 7)
  const has = o => (o.from ?? 1) <= level && level <= (o.until ?? 9)
  const inChimney = (x, z) => x >= ch.x0 && x <= ch.x1 && z >= ch.z0 && z <= ch.z1
  const inTower = (x, z) => level >= 3 && x >= T.x0 && x <= T.x1 && z >= T.z0 && z <= T.z1
  const band = (x, y, z) => { if (!c.get(x, y, z)) slab(c, x, y, z, P.band, 'top') }

  // --- Pass 1: block-out and structure ---------------------------------------------------------------------------
  // the plinth one block proud all round (the ground-floor sills stand on it), its front row the terrace's deck
  plinth(c, M.x0 - 1, M.z0 - 1, M.x1 + 1, M.z1 + 1, base)
  floor(c, M.x0 + 1, M.z0 + 1, M.x1 - 1, M.z1 - 1, 0, P.floor)
  for (const [a, b] of [[[M.x0, M.z0], [M.x1, M.z0]], [[M.x0, M.z1], [M.x1, M.z1]], [[M.x0, M.z0], [M.x0, M.z1]], [[M.x1, M.z0], [M.x1, M.z1]]])
    wallPanel(c, a, b, 1, M.loftY, P.brick)
  floor(c, M.x0 + 1, M.z0 + 1, M.x1 - 1, M.z1 - 1, M.upperY, P.floor)
  floor(c, M.x0 + 1, M.z0 + 1, M.x1 - 1, M.z1 - 1, M.loftY, P.floor)
  if (level >= 2) { // the back house: brick between dark oak posts, two floors
    plinth(c, Wg.x0 - 1, Wg.z0 + 1, Wg.x1 + 1, Wg.z1 + 1, base) // proud all round, the yard door's step on it
    floor(c, Wg.x0 + 1, Wg.z0 + 1, Wg.x1 - 1, Wg.z1 - 1, 0, P.floor)
    for (const [a, b] of [[[Wg.x0, Wg.z0 + 1], [Wg.x0, Wg.z1]], [[Wg.x1, Wg.z0], [Wg.x1, Wg.z1]], [[Wg.x0, Wg.z1], [Wg.x1, Wg.z1]], [[M.x1 + 1, Wg.z0], [Wg.x1, Wg.z0]]])
      wallPanel(c, a, b, 1, 9, P.brick)
    floor(c, Wg.x0 + 1, Wg.z0 + 1, Wg.x1 - 1, Wg.z1 - 1, 5, P.floor)
    floor(c, Wg.x0 + 1, Wg.z0 + 1, Wg.x1 - 1, Wg.z1 - 1, 9, P.floor)
  }

  // --- Pass 2: roofs and gables ----------------------------------------------------------------------------------
  // the front house: steep tiles between the stepped street gable and (I) a plain back gable under an overhang, or
  // (II on) a stepped back gable carried across the back house's front
  const stepped = (x, z) => (z === M.z0 && x >= M.x0 && x <= M.x1) || (level >= 2 && z === M.z1 && x >= M.x0 && x <= Wg.x1)
  const main = gableRoof(c, { ...M.roof, along: [M.z0, level >= 2 ? M.z1 : M.z1 + 1] }, P.roof,
    (x, y, z) => !stepped(x, z) && !inChimney(x, z))
  steppedGable(c, 'z', M.z0, MASSES.frontSteps, 1, P.brick, P.dressed)
  if (level >= 2) {
    steppedGable(c, 'z', M.z1, MASSES.backSteps, 1, P.brick, P.dressed)
    const wing = gableRoof(c, Wg.roof, P.roof, (x, y, z) => !stepped(x, z) && !c.get(x, y, z))
    gableWall(c, 'z', Wg.z1, [Wg.x0, Wg.x1], 1, wing.under, () => P.brick)
    bargeTrim(c, 'z', Wg.roof.along[1], Wg.roof.across, wing.under, P.barge)
  } else {
    gableWall(c, 'z', M.z1, [M.x0, M.x1], 1, main.under, () => P.brick)
    bargeTrim(c, 'z', M.z1 + 1, M.roof.across, main.under, P.barge)
  }
  if (level >= 3) { // the tower in the yard's angle, through the back house's eave, up past the ridge
    plinth(c, T.x0 - 1, T.z0 + 1, T.x1, T.z1 + 1, base)
    for (const [a, b] of [[[T.x0, T.z0], [T.x0, T.z1]], [[T.x1, T.z0], [T.x1, T.z1]], [[T.x0, T.z1], [T.x1, T.z1]], [[T.x0, T.z0], [T.x1, T.z0]]])
      wallPanel(c, a, b, 1, T.top, P.brick)
    c.clearBox(T.x0 + 1, 1, T.z0 + 1, T.x1 - 1, T.top + 2, T.z1 - 1)
    floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, 0, P.floor)
    for (const y of T.floors) floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, y, P.floor)
    for (let x = T.x0; x <= T.x1; x++) for (let y = T.top + 1; y <= 21; y++) c.clear(x, y, T.z0) // the back gable's steps give way to the spire
    pyramidRoof(c, { x0: T.x0 - 1, z0: T.z0 - 1, x1: T.x1 + 1, z1: T.z1 + 1, eaveY: T.top, rise: T.rise }, P.roof,
      (x, y, z) => !c.get(x, y, z), ['lightning_rod', { facing: 'up', powered: 'false', waterlogged: 'false' }])
  }

  // --- Pass 3: openings ------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.timber })
  c.set(fd.at[0], fd.at[1] + 2, fd.at[2], P.dressed) // the lintel
  for (const w of OPENINGS.windows) if (has(w)) {
    window(c, w.cells, w.out, w.dress === 'sill' ? 'sill' : null, { glass: P.glass, wood: P.wood, pots: w.pots ?? [], sill: P.sill })
    const top = Math.max(...w.cells.map(p => p[1]))
    for (const [x, y, z] of w.cells) if (y === top) c.set(x, y + 1, z, P.dressed) // a white lintel over every window
  }
  if (level >= OPENINGS.wingDoorway.level) doorway(c, OPENINGS.wingDoorway.at)
  if (level >= OPENINGS.towerDoorway.level) doorway(c, OPENINGS.towerDoorway.at)
  // the stairs: hall to parlour, parlour to loft (II: the back house's)
  for (const st of [OPENINGS.stairs, OPENINGS.loftStairs, ...(level >= 2 ? [OPENINGS.wingStairs] : [])]) {
    for (const [x, y, z] of st.well) c.clear(x, y, z)
    flight(c, st.flight, st.rises, P.wood, P.floor, st.base ?? 1)
    railingRun(c, st.rail, P.wood)
  }
  const pt = OPENINGS.partition
  wallPanel(c, [pt.x0, pt.z], [pt.x1, pt.z], M.upperY + 1, M.loftY - 1, P.partition)
  door(c, pt.door, 'south', 'south', { wood: P.wood })
  if (level >= 2) door(c, OPENINGS.wingDoor.at, OPENINGS.wingDoor.facing, 'west', { wood: P.timber })

  // --- Pass 4: depth and detail ----------------------------------------------------------------------------------
  // the street front: piers a block proud up to the crow steps, each capped white; the door in a niche between the
  // middle two under a white keystone and a lantern; string courses at the floors; the terrace with its benches and
  // lamps; the hoist beam at the peak
  for (const p of MASSES.piers) {
    for (let y = p.from; y <= p.top; y++) c.set(p.x, y, M.z0 - 1, P.brick)
    if (p.from > 1) stair(c, p.x, p.from - 1, M.z0 - 1, P.corbel, 'south', 'top')
    c.set(p.x, p.top + 1, M.z0 - 1, P.dressed)
  }
  c.set(fd.at[0], 4, M.z0 - 1, P.dressed)
  c.set(fd.at[0], 3, M.z0 - 1, 'lantern', { hanging: 'true', waterlogged: 'false' })
  for (const y of [M.upperY, M.loftY]) for (let x = M.x0; x <= M.x1; x++) band(x, y, M.z0 - 1)
  // the course carried round the sides and back at the first floor (the upper sills' ledge)
  for (let z = M.z0; z <= M.z1 + 1; z++) { band(M.x0 - 1, M.upperY, z); band(M.x1 + 1, M.upperY, z) }
  for (let x = M.x0 - 1; x <= M.x1 + 1; x++) if (level < 2 || x < Wg.x0) band(x, M.upperY, M.z1 + 1)
  // pots on the course under the upper windows
  for (const w of OPENINGS.windows) if (has(w) && !w.dress && w.pots) {
    const [ox, oz] = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[w.out]
    const low = Math.min(...w.cells.map(p => p[1]))
    w.cells.filter(p => p[1] === low).forEach(([x, y, z], i) => { if (w.pots[i]) c.set(x + ox, y, z + oz, w.pots[i]) })
  }
  const tr = MASSES.terrace
  for (let x = tr.x0; x <= tr.x1; x++) if (!tr.lamps.includes(x)) stair(c, x, 0, M.z0 - 2, P.step, 'south')
  for (const x of tr.lamps) { c.set(x, 0, M.z0 - 2, base); lampPost(c, x, 1, M.z0 - 2, P.post, 1) }
  for (const [a, b] of tr.benches) for (let x = a; x <= b; x++) stair(c, x, 1, M.z0 - 1, P.bench, 'south')
  const h = MASSES.hoist
  for (const z of [M.z0 - 2, M.z0 - 1, M.z0]) c.set(h.x, h.beamY, z, `${P.timber}_log`, { axis: 'z' })
  c.set(h.x, h.beamY - 1, M.z0 - 2, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(h.x, h.beamY - 2, M.z0 - 2, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // the west stack: 2 x 2 up the side wall from the plinth, a white cap course, two flues smoking
  for (let x = ch.x0; x <= ch.x1; x++) for (let z = ch.z0; z <= ch.z1; z++) {
    for (let y = 0; y < ch.top; y++) c.set(x, y, z, P.brick)
    c.set(x, ch.top, z, P.dressed)
  }
  for (const [x, z] of [[ch.x0, ch.z0], [ch.x1, ch.z1]]) c.set(x, ch.top + 1, z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  if (level >= 2) { // the back house's frame: dark oak posts, a rail at the floor and a plate at the eaves
    for (const [x, z] of Wg.posts) post(c, x, z, 1, 9, `${P.timber}_log`)
    for (const y of [5, 9]) {
      for (let z = Wg.z0 + 1; z < Wg.z1; z++) {
        if (![...Wg.posts].some(([px, pz]) => px === Wg.x1 && pz === z) && c.name(Wg.x1, y, z) === `minecraft:${P.brick}`) c.set(Wg.x1, y, z, `${P.timber}_log`, { axis: 'z' })
        if (z > T.z1 && c.name(Wg.x0, y, z) === `minecraft:${P.brick}`) c.set(Wg.x0, y, z, `${P.timber}_log`, { axis: 'z' })
      }
      for (let x = Wg.x0 + 1; x < Wg.x1; x++) if (c.name(x, y, Wg.z1) === `minecraft:${P.brick}`) c.set(x, y, Wg.z1, `${P.timber}_log`, { axis: 'x' })
    }
  }
  if (level >= 3) { // the tower's floors read outside as white courses
    for (const y of T.floors) {
      for (let z = T.z0 + 1; z <= T.z1 + 1; z++) band(T.x0 - 1, y, z)
      for (let x = T.x0 - 1; x < T.x1; x++) band(x, y, T.z1 + 1)
    }
  }

  // --- Pass 5: interior (the plan's furniture) ---------------------------------------------------------------------
  for (const f of FURNITURE) if (has(f)) furnitureParts[f.part](c, f)
  for (const [x, y, z, id] of GOODS) c.set(x, y, z, id)
  if (level >= 3) { // the tower's ladder from the counting room up through each floor to the lookout's hatch
    const L = T.ladder
    for (const y of T.floors.slice(0, -1)) c.clear(L.x, y, L.z)
    hatchLadder(c, L.x, L.z, L.y0, L.top, L.facing, P.wood)
  }

  // --- Pass 6: the store (IV) and the ground -----------------------------------------------------------------------
  let dug = null
  if (level >= 4) {
    const C = MASSES.cellar
    dug = cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, mix(P.cellarWall, 3), P.cellarFloor)
    hatchLadder(c, C.ladder.x, C.ladder.z, -C.depth + 1, 0, C.ladder.facing, P.wood)
  }
  if (level >= 2) {
    const b = OUTSIDE.yardBed
    flowerBed(c, b.x0, b.x1, b.z, b.plants)
    const [lx, ly, lz] = OUTSIDE.yardLamp
    lampPost(c, lx, ly, lz, `${P.timber}_fence`, 2)
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
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[level]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
