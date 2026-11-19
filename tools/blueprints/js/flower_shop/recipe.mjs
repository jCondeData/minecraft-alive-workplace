// The Flower Shop (ROADMAP 49), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes
// (block-out and structure, openings, depth and detail, interior, ground, light) for LEVEL = 1 (flower_shop) or 2
// (flower_shop_2, Level I with the glass range across the yard and the ridge lantern), and returns the structure for
// the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts recorded) for
// tools/blueprints/rebuild.py. Build: see build.sh; CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, BEDS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, stair, plinth, floor, post, beamRing, wallPanel, window, door, doorway, gableRoof, gableWall,
  chimney, lampPost, kerbFence, furnitureParts, composter, hangingPot, glassGableRoof, staging, growingBed, glazedLantern,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

// one glass house: dwarf brick wall, dark oak posts, panes between, a log ring at the beam
function glassWalls(c, { x0, x1, z0, z1 }, sidePosts, P, stone, { front = null, skip = () => false } = {}) {
  plinth(c, x0, z0, x1, z1, stone)
  const ring = []
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) if (x === x0 || x === x1 || z === z0 || z === z1) ring.push([x, z])
  for (const [x, z] of ring) {
    if (skip(x, z)) continue
    const corner = (x === x0 || x === x1) && (z === z0 || z === z1)
    const sidePost = (x === x0 || x === x1) && sidePosts.includes(z)
    const frontPost = front && z === z0 && front.includes(x)
    if (corner || sidePost || frontPost) { post(c, x, z, 1, 3, P.frame); continue }
    c.set(x, 1, z, P.brick)
    c.set(x, 2, z, P.glass); c.set(x, 3, z, P.glass)
  }
  beamRing(c, x0, z0, x1, z1, 4, P.frame)
}

export function draw(level = 1) {
  const c = new Canvas(SIZE[level])
  const P = PALETTE
  const stone = mix(P.stone, 11)
  const tiles = mix(P.floor, 5)
  const G = MASSES.glasshouse, B = MASSES.boiler, K = MASSES.parlour, Y = MASSES.yard
  const gw = G.walls, gi = G.inside, kw = K.walls, ki = K.inside
  const glassRoof = { glass: P.glassBlock, bar: P.roof.stairs, gutter: P.roof.stairs, ridge: P.roof.full }

  // --- Pass 1: block-out and structure ------------------------------------------------------------------------------
  // Glasshouse: dwarf wall and glass on the street gable and the west side, the parlour's brick on the east front half,
  // a solid brick back wall
  const inParlour = (x, z) => x === kw.x0 && z >= kw.z0 && z <= kw.z1
  glassWalls(c, gw, G.sidePosts, P, stone, { front: G.frontPosts, skip: (x, z) => z === gw.z1 || inParlour(x, z) })
  floor(c, gi.x0, gi.z0, gi.x1, gi.z1, 0, tiles)
  wallPanel(c, [gw.x0 + 1, gw.z1], [gw.x1 - 1, gw.z1], 1, 3, P.brick)
  post(c, gw.x0, gw.z1, 1, 3, P.frame); post(c, gw.x1, gw.z1, 1, 3, P.frame)
  // Parlour: brick on its plinth, up through the beam course on the eave walls
  plinth(c, kw.x0, kw.z0, kw.x1, kw.z1, stone)
  floor(c, ki.x0, ki.z0, ki.x1, ki.z1, 0, P.floorWood)
  wallPanel(c, [kw.x0, kw.z0], [kw.x1, kw.z0], 1, 4, P.brick)
  wallPanel(c, [kw.x0, kw.z1], [kw.x1, kw.z1], 1, 4, P.brick)
  wallPanel(c, [kw.x0, kw.z0], [kw.x0, kw.z1], 1, 4, P.brick)
  wallPanel(c, [kw.x1, kw.z0], [kw.x1, kw.z1], 1, 3, P.brick) // the eave wall
  for (let x = kw.x0 + 1; x < kw.x1; x++) c.set(x, 4, kw.z0, P.frame, { axis: 'x' }) // the shop's fascia beam

  // Roofs: the glass first (the hero), leaving its east gutter out of the parlour; the parlour's copper after it,
  // only where nothing is drawn
  const main = glassGableRoof(c, G.roof, glassRoof, G.ribs, (x, y, z) => !(x === 8 && z >= kw.z0 && z <= kw.z1))
  const wing = gableRoof(c, { ...K.roof, steep: false }, P.roof, (x, y, z) => !c.get(x, y, z))
  // Gables: the street gable glazed round a king post, the back gable solid brick, the parlour's east gable brick and
  // its west gable closing its attic above the glass
  gableWall(c, 'z', gw.z0, [gw.x0, gw.x1], 5, main.under, u => (u === 4 ? [P.frame, { axis: 'y' }] : P.glass))
  gableWall(c, 'z', gw.z1, [gw.x0, gw.x1], 4, main.under, () => P.brick)
  gableWall(c, 'z', kw.z0, [kw.x0 + 1, kw.x1], 5, wing.under, () => P.brick)
  gableWall(c, 'z', kw.z1, [kw.x0 + 1, kw.x1], 5, wing.under, () => P.brick)

  // Level II: the range across the yard's east side and the lantern on the big ridge
  let range = null
  if (level >= 2) {
    const R = MASSES.range, rw = R.walls
    // the yard's east fence and its gate give way to the range's west wall
    for (let z = Y.z0; z <= 12; z++) { c.clear(11, 1, z); c.clear(11, 0, z) }
    glassWalls(c, rw, R.sidePosts, P, stone, { front: [] })
    floor(c, R.inside.x0, R.inside.z0, R.inside.x1, R.inside.z1, 0, tiles)
    range = glassGableRoof(c, R.roof, glassRoof, R.ribs, (x, y, z) => x !== R.roof.across[0] && !c.get(x, y, z)) // no gutter over the yard: it stays open to the sky
    gableWall(c, 'z', rw.z0, [rw.x0, rw.x1], 5, range.under, u => (u === 13 ? [P.frame, { axis: 'y' }] : P.glass))
    gableWall(c, 'z', rw.z1, [rw.x0, rw.x1], 5, range.under, u => (u === 13 ? [P.frame, { axis: 'y' }] : P.glass))
  }

  // --- Pass 2: openings ----------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: P.stoneStairs })
  c.set(...OPENINGS.transom, P.glass)
  doorway(c, OPENINGS.parlourDoorway.at, OPENINGS.parlourDoorway.height)
  const yd = OPENINGS.yardDoor
  door(c, yd.at, yd.facing, 'east', { wood: P.wood })
  c.set(yd.at[0], 3, yd.at[2], P.glass)
  for (const w of OPENINGS.windows) {
    window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, pots: w.name === 'shop window' ? ['potted_red_tulip', 'potted_cornflower', 'potted_pink_tulip'] : [] })
  }
  if (level >= 2) {
    const rd = OPENINGS.rangeDoors
    door(c, rd.yard.at, rd.yard.facing, 'west', { wood: P.wood })
    c.set(rd.yard.at[0], 3, rd.yard.at[2], P.glass)
    door(c, rd.front.at, rd.front.facing, 'north', { wood: P.wood, step: [13, 0, 6], stepStair: P.stoneStairs })
    c.set(rd.front.at[0], 3, rd.front.at[2], P.glass)
  }

  // --- Pass 3: depth and detail --------------------------------------------------------------------------------------
  // the boiler: the stove's chimney up the back gable and out past the ridge, a stepped breast either side
  const [sx, sz] = B.stack
  chimney(c, sx, sz, 0, B.top, P.brick, { breast: { axis: 'x', top: 4, stair: 'brick_stairs' } })
  for (let y = 4; y <= 8; y++) c.set(sx, y, gw.z1, P.brick) // the flue through the gable
  // the shop window's dark oak frame: a stall riser under it
  for (let x = kw.x0 + 1; x < kw.x1; x++) c.set(x, 1, kw.z0, `${P.wood}_planks`)
  if (level >= 2) {
    const L = MASSES.lantern
    glazedLantern(c, L.cx, L.cz, L.y0, { post: P.roof.full, glass: P.glass, cap: P.roof }) // no finial: the critic's note 3
  }

  // --- Pass 4: interior (the plan's furniture) and her beds ----------------------------------------------------------
  growingBed(c, BEDS.west.cells, 0, { base: P.under, soil: P.soil, plants: BEDS.west.plants })
  growingBed(c, BEDS.east.cells, 0, { base: P.under, soil: P.soil, plants: BEDS.east.plants })
  if (level >= 2) growingBed(c, BEDS.range.cells, 0, { base: P.under, soil: P.soil, plants: BEDS.range.plants })
  const parts = {
    ...furnitureParts, hangingPot, composter,
    staging: (cv, f) => staging(cv, f.cellsAbs, f.wood, f.pots),
    decoratedPot: (cv, f) => { cv.free(f.pos); cv.set(...f.pos, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' }) },
  }
  c.clear(...B.stove) // the stove takes the brick's place in the back wall
  for (const f of FURNITURE) if ((f.level ?? 1) <= level) parts[f.part](c, f)
  for (const [x, z] of [[2, 9], [3, 9]]) c.set(x, 3, z, 'flower_pot') // the potting bench's empty pots: hers to fill

  // --- Pass 5: ground ------------------------------------------------------------------------------------------------
  const ap = OUTSIDE.apron
  for (let x = ap.x0; x <= ap.x1; x++) if (!c.get(x, 0, ap.z)) c.set(x, 0, ap.z, stone)
  for (const [x, y, z] of OUTSIDE.lampPosts) lampPost(c, x, y, z, `${P.wood}_fence`)
  for (const [x, y, z, id] of OUTSIDE.frontPots) {
    if (id === 'decorated') c.set(x, y, z, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
    else c.set(x, y, z, id)
  }
  // the yard: grass, a stone path from her side door to the gate, the kerb and fence, tall flowers, a dipping tank
  for (let x = Y.x0; x <= Y.x1; x++) for (let z = Y.z0; z <= Y.z1; z++) c.set(x, 0, z, z === 8 ? stone : P.soil, z === 8 ? {} : { snowy: 'false' })
  for (const [x, z, id] of BEDS.yardPlants) {
    c.set(x, 1, z, id, { half: 'lower' }); c.set(x, 2, z, id, { half: 'upper' })
  }
  const [tx, ty, tz] = OUTSIDE.dippingTank
  c.set(tx, ty, tz, 'water', { level: '0' })
  if (level === 1) {
    kerbFence(c, Y.fence, 0, stone, `${P.wood}_fence`)
    const [gx, gy, gz] = Y.gate
    c.set(gx, gy, gz, `${P.wood}_fence_gate`, { facing: 'east', in_wall: 'false', open: 'false', powered: 'false' })
  } else {
    kerbFence(c, [[10, 12], [7, 12], [7, 11]], 0, stone, `${P.wood}_fence`)
    c.set(11, 0, 8, stone)
  }
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const id = level === 1 ? 'flower_shop' : `flower_shop_${level}`
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
