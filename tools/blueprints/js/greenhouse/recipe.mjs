// The Greenhouse (the Leat House), the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP 49)
// in passes (block-out and structure, the roof, the machine and its water, openings, depth and detail, the farm and
// interior, ground) and returns the structure for the minecraft-builder skill's runner. Also writes furniture.json (the
// potting bench's counters; every other fixture is a block the game reads as itself) for tools/blueprints/rebuild.py.
// Build: sh build.sh (LIVE=1 for the live drawing). CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, MACHINE, FARM, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, fairMix, stair, slab, door, shaftRun, beltRun, composter,
  glassRidges, axisOf, glassGable, undershotLeat, deliveryHatch, downpipe, flowerBed, furnitureParts,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const brick = fairMix(P.brick, 3)
  const plinth = fairMix(P.plinth, 17)
  const kerb = fairMix(P.kerb, 29)
  const G = MASSES.glasshouse, E = MASSES.engineBay, B = MASSES.boiler, M = MACHINE
  const { x0, x1, z0, z1 } = G
  const onRing = (x, z) => x === x0 || x === x1 || z === z0 || z === z1

  // --- Pass 1: block-out and structure ------------------------------------------------------------------------------
  // The plinth, one course of brick (the dwarf wall), two courses of glass, a white beam ring
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (!onRing(x, z)) continue
    c.set(x, 0, z, plinth)
    c.set(x, G.dwarfTop, z, brick)
    for (let y = G.dwarfTop + 1; y <= G.glassTop; y++) c.set(x, y, z, P.glass)
    if (!G.gutters.includes(x)) c.set(x, G.beamY, z, P.glass) // the end walls' glass runs on up into the gables
  }
  // Frame posts (on the dwarf wall): the corners, every third cell down the long walls, and the nave's two tall posts
  // front and back, up to its eaves (the door's frame at the front)
  for (const z of [z0, z1]) for (const x of G.cornerPosts) post(c, x, z, G.dwarfTop + 1, G.glassTop, P.log)
  for (const z of G.sidePosts) { post(c, x0, z, G.dwarfTop + 1, G.glassTop, P.log); post(c, x1, z, G.dwarfTop + 1, G.glassTop, P.log) }
  for (const z of [z0, z1]) for (const x of G.navePosts) post(c, x, z, z === z0 ? 1 : G.dwarfTop + 1, G.beamY, P.log)
  // The engine bay: the west wall's brick a course higher where the drive comes through and the tools hang
  for (let z = E.z0; z <= E.z1; z++) if (!G.sidePosts.includes(z)) c.set(E.x, E.y, z, brick)

  // --- Pass 2: the roof (ridge and furrow, the raised nave) ---------------------------------------------------------
  const spans = glassRidges(c, { along: [z0, z1], gutterY: G.beamY, gutters: G.gutters, tops: G.tops },
    { pane: P.glass, frame: P.frame, gutter: P.log, finial: P.finial, edge: P.edge })
  // The gables: glass under the outline
  for (const z of [z0, z1]) glassGable(c, z, { gutters: G.gutters, lo: spans.lo }, G.beamY + 1, () => P.glass)

  // --- Pass 3: the machine and its water ----------------------------------------------------------------------------
  const L = M.leat
  undershotLeat(c, { x: L.x, z0: L.z0, z1: L.z1, sources: L.sources, wheelZ: L.wheelZ, facing: M.wheel.facing, gaps: L.gaps, raisedZ: L.raisedZ },
    { kerb, raised: P.coping })
  // the wheel hood: posts on the kerbs, a white gable over the wheel
  const Hd = M.hood
  for (const [px, pz] of Hd.posts) for (let y = 1; y < Hd.eaveY; y++) c.set(px, y, pz, `${P.wood}_fence`) // slender posts
  for (let x = Hd.x0; x <= Hd.x1; x++) {
    stair(c, x, Hd.eaveY, Hd.z - 1, P.edge, 'south')
    stair(c, x, Hd.eaveY, Hd.z + 1, P.edge, 'north')
    c.set(x, Hd.eaveY + 1, Hd.z, P.frame)
  }
  for (const p of M.pads) c.set(...p, P.coping)
  shaftRun(c, M.shafts.cells, M.shafts.axis) // the last one through the engine bay's dwarf course
  floorRun(c, FARM.machineBed, P.bed)
  beltRun(c, M.belt.cells, M.belt.facing)
  c.set(...M.harvester.at, 'create:mechanical_harvester', { facing: M.harvester.facing, waterlogged: 'false' })
  c.set(...M.plough.at, 'create:mechanical_plough', { facing: M.plough.facing, waterlogged: 'false' })

  // --- Pass 4: openings ----------------------------------------------------------------------------------------------
  const D = OPENINGS.door
  door(c, D.at, D.facing, D.out, { wood: P.wood, step: D.step, stepStair: P.step })
  c.set(...OPENINGS.transom, P.glass)
  deliveryHatch(c, OPENINGS.hatch.at, OPENINGS.hatch.out, { wood: P.wood, slab: P.hoodSlab, shutter: false })

  // --- Pass 5: depth and detail --------------------------------------------------------------------------------------
  // A lantern over the door on a bracket from the nave's post line
  c.set(...OPENINGS.doorLamp.bracket, `${P.wood}_fence`)
  c.set(...OPENINGS.doorLamp.lamp, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // Copper downpipes from the valley gutters' ends, beds of flowers under the front glass
  for (const d of OUTSIDE.downpipes) downpipe(c, d.x, d.z, 0, G.beamY - 1, P.finial, [d.x, G.beamY, d.z], 'north')
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  // The boiler behind the back wall: a brick stove house, its stoke-hole facing back, the stack and its smoke
  for (let x = B.x0; x <= B.x1; x++) for (let z = B.z0; z <= B.z1; z++) {
    for (let y = 0; y <= B.top; y++) c.set(x, y, z, brick)
    slab(c, x, B.top + 1, z, P.boilerTop)
  }
  const [sx, sz] = B.stack
  c.set(sx, 1, sz + 0, 'furnace', { facing: 'south', lit: 'false' })
  for (let y = B.top + 1; y <= B.stackTop; y++) c.set(sx, y, sz, brick)
  c.set(sx, B.stackTop + 1, sz, 'campfire', { facing: 'south', lit: 'true', signal_fire: 'false', waterlogged: 'false' })

  // --- Pass 6: the farm and the Farmer's place --------------------------------------------------------------------------
  const water = new Set(FARM.water.map(([x, z]) => `${x},${z}`))
  const footing = new Set(FARM.footings.map(([x, z]) => `${x},${z}`))
  const W = FARM.walk, F = FARM.frontWalk
  for (let x = x0 + 1; x < x1; x++) for (let z = z0 + 1; z < z1; z++) {
    if (x === FARM.machineBed.x) continue
    if (z === F.z || (x === W.x && z >= W.z0 && z <= W.z1)) { c.set(x, FARM.y, z, P.path); continue }
    if (footing.has(`${x},${z}`)) { c.set(x, FARM.y, z, P.bed); continue }
    if (water.has(`${x},${z}`)) { c.set(x, FARM.y, z, 'water', { level: '0' }); continue }
    c.set(x, FARM.y, z, 'farmland', { moisture: '7' })
    const crop = FARM.crops[x]
    if (crop) c.set(x, FARM.y + 1, z, crop, { age: '0' }) // planted fresh (MaterialRules.plantedFresh)
  }
  checkFarm(c)
  for (const f of FURNITURE) {
    if (f.part === 'composter') composter(c, f)
    else if (f.part === 'chest') c.set(...f.pos, 'chest', { facing: f.facing, type: 'single', waterlogged: 'false' })
    else if (f.part === 'counter') furnitureParts.counter(c, f)
    else if (f.part === 'pot') c.set(...f.pos, f.block)
    else if (f.part === 'hangingLamp') {
      const [x, y, z] = f.pos
      c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })
      for (let i = 1; i <= (f.chain ?? 0); i++) c.set(x, y + i, z, 'chain', { axis: 'y', waterlogged: 'false' })
    }
  }
  return c
}

// an upright frame member (a post) from y0 to y1, with an axis only if the block has one
function post(c, x, z, y0, y1, id) { for (let y = y0; y <= y1; y++) c.set(x, y, z, id, axisOf(id, 'y')) }

function floorRun(c, { x, z0, z1 }, id) { for (let z = z0; z <= z1; z++) c.set(x, 0, z, id) }

// The brief's farm rules, checked on the drawing: every farmland within 4 of water at its level, the pinned cells.
function checkFarm(c) {
  const farm = [], wet = []
  for (const [k, v] of c.cells) {
    const [x, y, z] = k.split(',').map(Number)
    if (v.id === 'minecraft:farmland') farm.push([x, y, z])
    if (v.id === 'minecraft:water' && y === FARM.y) wet.push([x, y, z])
  }
  for (const [x, y, z] of farm)
    if (!wet.some(([wx, wy, wz]) => wy >= y && wy <= y + 1 && Math.abs(wx - x) <= 4 && Math.abs(wz - z) <= 4)) throw new Error(`dry farmland ${x},${y},${z}`)
  if (c.name(...FARM.pinned.farmland) !== 'minecraft:farmland' || c.name(...FARM.pinned.wheat) !== 'minecraft:wheat') throw new Error('pinned farm cells')
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `greenhouse_cut${cut}` : 'greenhouse' } }
}
