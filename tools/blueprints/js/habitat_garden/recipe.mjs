// The Habitat Garden (the Stone Ring), the recipe: assembles plan.mjs from the shared parts library (../parts) in
// passes (block-out and structure, ground, hedges and paths, depth and detail, planting, light) and returns the
// structure for the minecraft-builder skill's runner. LEVEL=1 (habitat_garden) or 2 (habitat_garden_2): Level II is
// Level I drawn again by the same passes and grown (the back hedge opened onto a back garden with a second pond and
// the keeper's hide). Writes furniture.json (no furniture spots: an open-air workplace whose job needs none).
// Build both levels and save them as candidates: build.sh. CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  LEVELS, PALETTE, CENTRE, CORNERS, HEDGE, GATE, PATHS, RING, PONDS, BOWER, TREE, REST, HIDE,
} from './plan.mjs'
import {
  Canvas, mix, slab, standingStone, trilithon, tree, lilyPads, boulders, lampPost, hangingLantern, plantFlower,
  railingRun, clip, door, bankedPond, openShed, stiltCabin, potsOn,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const hash = (x, z, s = 0) => {
  let h = (x * 73856093) ^ (z * 83492791) ^ (s * 19349663)
  h = Math.abs((h ^ (h >>> 13)) * 1274126177)
  return h % 100
}

export function draw(level) {
  const P = PALETTE
  const [W, , D] = LEVELS[level].size
  const c = new Canvas(LEVELS[level].size)
  const stone = mix(P.stone, 3), boulder = mix(P.boulder, 5), bed = mix(P.bed, 2)
  const pathMix = mix(P.path, 4), steps = mix(P.steps, 6), crown = mix(P.crown, 8), flower = mix(P.flowers, 9)
  const cut = new Set(CORNERS[level].map(([x, z]) => `${x},${z}`))
  const inPlate = (x, z) => x >= 0 && x < W && z >= 0 && z < D && !cut.has(`${x},${z}`)

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // The Ring: six standing stones on stone footings, the trilithon at its back with lanterns under its proud lintel
  for (const [x, z, h, cap] of RING.stones) {
    c.set(x, 0, z, 'andesite')
    standingStone(c, x, z, 1, h, stone, !cap ? null : cap.stair ? { stair: P.stoneCap.stairs, facing: cap.stair } : { slab: P.stoneCap.slab })
  }
  const T = RING.trilithon
  for (const x of [T.xa, T.xb]) c.set(x, 0, T.z, 'andesite')
  trilithon(c, T.xa, T.xb, T.z, 1, T.topY, stone, P.lintel)

  // The Tree's trunk and branches (the crown comes with the planting)
  const [tx, tz] = TREE.trunk
  c.set(tx, 0, tz, P.trunkSoil)
  for (let y = 1; y <= TREE.top; y++) c.set(tx, y, tz, P.sacc, { axis: 'y' })
  for (const [x, y, z, axis] of [...TREE.roots, ...TREE.branches]) c.set(x, y, z, P.sacc, { axis })

  // The Bower: a stone plinth under its walls, a worn floor, the lean-to frame, walls and roof
  const B = BOWER
  for (let x = B.x0; x <= B.x1; x++) for (let z = B.z0; z <= B.z1; z++) {
    const edge = x === B.x1 || z === B.z0 || z === B.z1
    c.set(x, 0, z, edge ? stone : 'coarse_dirt')
  }
  openShed(c, B, { frame: P.frame, wall: P.wall, roof: P.roof, glass: P.glass })

  // The Hide (Level II) on its stilts, and its balcony out to the pond's west bank
  if (level >= 2) {
    stiltCabin(c, HIDE, { frame: P.frame, wall: P.wall, floor: P.floor, pad: P.pad, glass: P.glass, roof: P.roof, wood: P.wood, apex: ['lightning_rod', { facing: 'up', powered: 'false', waterlogged: 'false' }] })
    door(c, HIDE.doorway, HIDE.door.facing, 'east', { wood: P.wood })
    const bal = HIDE.balcony
    for (const [x, z] of bal.legs) { c.set(x, 0, z, P.pad); for (let y = 1; y < HIDE.floorY; y++) c.set(x, y, z, P.frame, { axis: 'y' }) }
    for (let x = bal.x0; x <= bal.x1; x++) for (let z = bal.z0; z <= bal.z1; z++) c.set(x, HIDE.floorY, z, P.floor)
    railingRun(c, [[bal.x0, HIDE.floorY + 1, bal.z0], [bal.x1, HIDE.floorY + 1, bal.z0], [bal.x1, HIDE.floorY + 1, bal.z1], [bal.x0, HIDE.floorY + 1, bal.z1]], P.wood)
  }

  // The Ponds: mud beds, grass banks with boulders, still water
  const ponds = [PONDS[1], ...(level >= 2 ? [PONDS[2]] : [])]
  for (const p of ponds) bankedPond(c, { water: p.water, y: 1, boulders: p.boulders }, { bed, soil: P.soil, bank: P.lawn, boulder })

  // --- Pass 2: the ground ---------------------------------------------------------------------------------------
  const paths = [PATHS.both, ...(level >= 2 ? [PATHS[2]] : [])]
  const pathAt = new Map()
  for (const p of paths) {
    for (const [x, z] of p.path) pathAt.set(`${x},${z}`, 'path')
    for (const [x, z] of p.steps) pathAt.set(`${x},${z}`, 'steps')
  }
  const moss = new Set(RING.moss.map(([x, z]) => `${x},${z}`))
  for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
    if (!inPlate(x, z) || c.get(x, 0, z)) continue
    const k = `${x},${z}`
    if (x === CENTRE[0] && z === CENTRE[2]) c.set(x, 0, z, P.centre)
    else if (moss.has(k)) c.set(x, 0, z, P.moss)
    else if (pathAt.get(k) === 'path') c.set(x, 0, z, pathMix)
    else if (pathAt.get(k) === 'steps') c.set(x, 0, z, steps)
    else c.set(x, 0, z, P.lawn, { snowy: 'false' })
  }
  // (Level II) the hide's ladder foot and the ground under it: worn, not lawn
  if (level >= 2) for (const [x, z] of [[2, 20], [3, 20], [4, 20], [2, 19], [3, 18]]) c.set(x, 0, z, pathMix)

  // --- Pass 3: hedges and the gate ------------------------------------------------------------------------------
  const tall = new Set(HEDGE.tall.map(([x, z]) => `${x},${z}`))
  for (const [x, z] of [...HEDGE.both, ...HEDGE[level]]) {
    if (!inPlate(x, z) || c.get(x, 1, z)) continue
    clip(c, [[x, 1, z], ...(tall.has(`${x},${z}`) ? [[x, 2, z]] : [])], P.hedge)
  }
  for (const [x, z] of GATE.lamps) lampPost(c, x, 1, z, `${P.wood}_fence`, 2)

  // --- Pass 4: depth and detail ---------------------------------------------------------------------------------
  // The Bower's work: the pasture under the eave, the chest, the honeycomb crate, the potting bench and shelf, a lamp
  const pa = B.pasture.at
  c.set(pa[0], 0, pa[2], 'coarse_dirt')
  for (const part of ['bottom', 'top'])
    c.set(pa[0], pa[1] + (part === 'top' ? 1 : 0), pa[2], 'cobblemon:pasture', { facing: B.pasture.facing, on: 'false', part, waterlogged: 'false' })
  c.set(...B.chest.at, 'chest', { facing: B.chest.facing, type: 'single', waterlogged: 'false' })
  c.set(...B.crate, P.crate)
  for (const [x, y, z] of B.bench) slab(c, x, y, z, `${P.wood}_slab`, 'top')
  potsOn(c, B.bench, B.benchPots)
  potsOn(c, [B.crate], [B.cratePot])
  hangingLantern(c, ...B.lantern)

  // The Ring's lanterns under the lintel; the tree's lantern under its west branch; a lamp post by the tree
  for (const [x, y, z] of RING.lanterns) hangingLantern(c, x, y, z)
  hangingLantern(c, ...TREE.lantern)
  lampPost(c, REST.lampPost[0], REST.lampPost[1], REST.lampPost[2], `${P.wood}_fence`, 2)

  // The Rest: a log bench under the tree, a fallen mossy log and a boulder heap back-west
  for (let z = REST.bench.z0; z <= REST.bench.z1; z++) c.set(REST.bench.x, 1, z, `stripped_${P.wood}_log`, { axis: 'z' })
  for (let x = REST.fallenLog.x0; x <= REST.fallenLog.x1; x++) c.set(x, 1, REST.fallenLog.z, 'oak_log', { axis: 'x' })
  for (const x of [REST.fallenLog.x0, REST.fallenLog.x0 + 1]) c.set(x, 2, REST.fallenLog.z, 'moss_carpet')
  boulders(c, REST.heap.cells, boulder, REST.heap.caps, P.stoneCap.slab)

  // The Hide's inside: a window bench, a chest by the hatch, a lantern on a chain from the peak
  if (level >= 2) {
    for (const [x, y, z] of HIDE.bench) slab(c, x, y, z, `${P.wood}_slab`, 'bottom')
    c.set(...HIDE.chest.at, 'chest', { facing: HIDE.chest.facing, type: 'single', waterlogged: 'false' })
    hangingLantern(c, ...HIDE.lantern, 'lantern', 2)
    for (const [x, y, z] of HIDE.balcony.lamps) c.set(x, y, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
  }

  // --- Pass 5: planting -------------------------------------------------------------------------------------------
  // The honey tree's crown: layered discs round the trunk, ragged at the rim, kept inside the plot
  const leaves = []
  for (const [y, r] of TREE.layers) for (let dx = -4; dx <= 4; dx++) for (let dz = -4; dz <= 4; dz++) {
    const d = Math.hypot(dx, dz), x = tx + dx, z = tz + dz // (a ragged rim, never a flat face on the plot's edge)
    if (d > r || !c.inside(x, y, z) || z > 16 || x > 18) continue
    if (y === TREE.layers[0][0] ? hash(x, z, y) < 45 : d > r - 0.9 && hash(x, z, y) < 30) continue // the lowest layer hangs in tufts
    leaves.push([x, y, z])
  }
  // hollow: a leaf with leaves on all six sides is never seen (and costs the builder a trip): left out
  const inCrown = new Set(leaves.map(q => q.join(',')))
  const shell = leaves.filter(([x, y, z]) => [[1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1]]
    .some(([dx, dy, dz]) => !inCrown.has(`${x + dx},${y + dy},${z + dz}`)))
  tree(c, [], shell, P.sacc, crown)
  for (const p of ponds) lilyPads(c, p.lily, 2, P.lily)
  // tall flowers by the hedge; then the lawn's flowers and grasses (the ring's middle kept short: open ground)
  const topGrass = (x, z) => {
    for (const y of [1, 0]) if (c.name(x, y, z) === 'minecraft:grass_block' && !c.get(x, y + 1, z) && !c.get(x, y + 2, z)) return y
    return null
  }
  for (const [x, z] of REST.tall) {
    const y = topGrass(x, z)
    if (y !== null) plantFlower(c, x, y + 1, z, P.tallFlowers[hash(x, z, 3) % P.tallFlowers.length])
  }
  for (let x = 0; x < W; x++) for (let z = 0; z < D; z++) {
    const y = topGrass(x, z)
    if (y === null) continue
    const ring = (x - CENTRE[0]) ** 2 + (z - CENTRE[2]) ** 2 <= 13
    const h = hash(x, z, 1)
    if (ring) { if (h < 8) c.set(x, y + 1, z, 'short_grass'); continue }
    if (h < 16) c.set(x, y + 1, z, flower(x, y + 1, z))
    else if (h < 30) c.set(x, y + 1, z, 'short_grass')
    else if (h < 34) c.set(x, y + 1, z, 'fern')
  }
  return c
}

// The brief's pins, checked on every build so a redraw can't lose one.
function pins(c, level) {
  const want = [[...BOWER.pasture.at, 'cobblemon:pasture'], [...CENTRE, 'minecraft:mossy_cobblestone'],
    [...TREE.pin, 'cobblemon:saccharine_log'], [...PONDS[1].pin, 'minecraft:water']]
  if (level >= 2) want.push([...PONDS[2].pin, 'minecraft:water'], [...HIDE.floorPin, 'minecraft:spruce_planks'], [...HIDE.logPin, 'minecraft:spruce_log'])
  for (const [x, y, z, id] of want) if (c.name(x, y, z) !== id) throw new Error(`pin ${x},${y},${z} is ${c.name(x, y, z)}, not ${id}`)
  if (c.get(CENTRE[0], 1, CENTRE[2])) throw new Error('something stands on the centre stone')
  const [kx, ky, kz] = BOWER.keeperSpot
  if (c.get(kx, ky, kz) || c.get(kx, ky + 1, kz)) throw new Error('the keeper spot is taken')
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = LEVELS[level].id
  const c = draw(level)
  pins(c, level)
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": []}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
