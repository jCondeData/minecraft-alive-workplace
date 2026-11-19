// Gem Grotto, the recipe: assembles plan.mjs ("the Split Geode") from the parts library in passes (block-out and shell,
// openings, depth and detail, interior, ground, light). LEVEL=2 draws Gem Grotto II: the same geode with the Cluster
// fused to its back. CUT=<y> cuts everything above y (cutaways).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, JOB } from './plan.mjs'
import { Canvas, stair, slab, door, eggCells, geodeLayers, crystalSpire, glazedBasin, crustVein } from '../parts/index.mjs'
import { fairMix } from '../parts/apiary_skep.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level = 1) {
  const P = PALETTE
  const c = new Canvas(SIZE[level])
  const crust = fairMix(P.crust, 3), crustLow = fairMix(P.crustLow, 5), band = fairMix(P.band, 7)
  const lining = fairMix(P.lining, 11), floorMix = fairMix(P.floor, 13), coreFloor = fairMix(P.coreFloor, 17)
  const G = MASSES.geode, K = MASSES.cluster
  const eggs = level === 2 ? [G, K] : [G]
  const inCluster = (x, z) => level === 2 && ((x - K.c[0]) / K.r[0]) ** 2 + ((z - K.c[2]) / K.r[2]) ** 2 <= 1 && z >= 12

  // --- Pass 1: block-out and shell ------------------------------------------------------------------------------
  // the body, layered from the outside in; the front slice is taken after the layering so the cut face shows the bands
  const body = eggCells(c, eggs)
  const layers = geodeLayers(body, (x, y, z) => z <= MASSES.cutZ)
  const room = []
  for (const [x, y, z] of body) {
    const n = layers.get(`${x},${y},${z}`)
    if (y === 0) { c.set(x, y, z, n === 0 && inCluster(x, z) ? coreFloor : floorMix); continue }
    if (z <= MASSES.cutZ) continue
    const face = z <= MASSES.faceBand
    if (n === 1) c.set(x, y, z, y <= 2 ? crustLow : crust)
    else if (n === 2) c.set(x, y, z, face ? band : lining)
    else if (n === 3 && face) c.set(x, y, z, P.crystal)
    else room.push([x, y, z])
  }
  // the floor's rim, where the crust meets the ground: the darker crust stone
  for (const [x, y, z] of body) if (y === 0 && z > MASSES.cutZ && layers.get(`${x},1,${z}`) === 1) c.set(x, 0, z, crustLow)

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  // The cut face (z = 1): every room cell in the slice is glazed, a calcite case round the door in
  // the middle; the step before it.
  const faceZ = MASSES.cutZ + 1
  for (const [x, y, z] of room) if (z === faceZ) c.set(x, y, z, P.glass)
  // a calcite door case in the glazing: jambs either side of the door and a lintel over it, so the face reads as a
  // crystal window round a door, not a wall of glass
  const D = OPENINGS.door
  for (const dx of [-1, 1]) for (let y = 1; y <= 3; y++) c.set(D.at[0] + dx, y, faceZ, P.mullion)
  c.set(D.at[0], 3, faceZ, P.mullion)
  door(c, D.at, D.facing, 'north', { wood: P.wood })
  for (const [x, y, z] of D.step) stair(c, x, y, z, P.step, 'south')
  // the niche: an alcove in the west wall's lining, the amethyst block at its back and its cluster on it
  const N = OPENINGS.niche
  for (const [x, y, z] of N.alcove) c.clear(x, y, z)
  c.set(...N.block, P.crystal)
  c.set(...N.lintel, P.mullion) // a calcite lintel over the cluster
  c.set(...N.cluster, P.bud, { facing: N.facing, waterlogged: 'false' })
  // Level II: where the eggs meet, the walls between the two rooms are gone already (one body); open the arch the
  // full height of the cluster's room so it reads as one geode grown on another
  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  // crystals breaking out of the crust, each with a calcite collar where it broke through
  const isCrust = (x, y, z) => layers.get(`${x},${y},${z}`) === 1 && z > MASSES.cutZ + 1 && c.get(x, y, z)?.id !== `minecraft:${P.mullion}`
  const top = (x, z) => { for (let y = c.size[1] - 1; y >= 0; y--) if (c.get(x, y, z)) return y; return -1 }
  const spires = [
    { at: [2, 3], h: 3, lean: 'west' }, { at: [12, 4], h: 2, lean: 'east' }, { at: [8, 6], h: 2, lean: null },
    { at: [11, 11], h: 2, lean: 'east' }, { at: [5, 9], h: 1, lean: null },
  ]
  // Level I's back: a crystal low on the back shoulder (Level II's Cluster grows over that spot)
  if (level === 1) spires.push({ at: [3, 12], h: 2, lean: 'south', vein: 4 })
  if (level === 2) spires.push({ at: [2, 17], h: 2, lean: 'west' }, { at: [9, 19], h: 2, lean: 'south' }, { at: [6, 15], h: 1, lean: null })
  for (const s of spires) {
    const [x, z] = s.at
    const y = top(x, z)
    if (y + 1 + s.h >= c.size[1]) continue
    crystalSpire(c, [x, y + 1, z], s.h, { crystal: P.crystal, bud: P.bud, collar: P.mullion, lean: s.lean })
    // the crust cracked where the crystal broke out: a calcite vein runs down from it
    if (s.vein !== 0) crustVein(c, [x, y + 1, z], s.vein ?? 3, P.mullion, isCrust)
  }

  // --- Pass 4: interior -----------------------------------------------------------------------------------------
  // the crucible: six lava sources in a deepslate kerb, plain glass to the front
  glazedBasin(c, JOB.lava, JOB.lavaY, { liquid: 'lava', kerb: P.kerb, corner: P.kerbCorner, glass: P.lavaGlass, glassSide: JOB.glass })
  // the stonecutter by the door
  c.set(...JOB.stonecutter, 'stonecutter', { facing: 'north' })
  // her chests against the east wall, a sorting bench beside them
  for (const z of [7, 8]) c.set(12, 1, z, 'chest', { facing: 'west', type: 'single', waterlogged: 'false' })
  for (const z of [5, 6]) { c.set(12, 1, z, P.benchBase); slab(c, 12, 2, z, P.bench, 'bottom') }
  // crystals growing on the lining: clusters hanging from the ceiling, out of reach of the floor
  let buds = 0
  for (const [x, y, z] of room) {
    if (y < 5 || c.get(x, y, z)) continue
    const above = c.name(x, y + 1, z)
    if (above !== `minecraft:${P.crystal}`) continue
    if ((x * 7 + z * 13 + y * 3) % 5 !== 0) continue
    if (Math.abs(x - 6) <= 2 && z >= 3 && z <= 8 && y < 7) continue // keep the crucible's air clear
    c.set(x, y, z, P.bud, { facing: 'down', waterlogged: 'false' }); buds++
  }
  if (level === 2) {
    // the four cores, plain deepslate standing up, each with its air above for a gem block and its cluster
    for (const [x, y, z] of JOB.cores) { c.set(x, y, z, P.core, { axis: 'y' }); c.clear(x, y + 1, z); c.clear(x, y + 2, z) }
  }

  // --- Pass 5: light --------------------------------------------------------------------------------------------
  const hang = (x, z, yLamp) => {
    let y = yLamp + 1
    while (!c.get(x, y, z)) { c.set(x, y, z, P.chain, { axis: 'y', waterlogged: 'false' }); y++ }
    c.set(x, yLamp, z, P.lamp, { hanging: 'true', waterlogged: 'false' })
  }
  hang(10, 7, 4)
  hang(3, 4, 4)
  hang(10, 11, 4)
  if (level === 2) { hang(6, 15, 4); hang(6, 11, 5) }

  // --- Pass 6: ground -------------------------------------------------------------------------------------------
  // boulders of the same stone in the corners the egg leaves, a crystal on the biggest
  const boulder = fairMix(P.boulder, 19)
  for (const [x, z, h] of [[0, 0, 2], [1, 0, 1], [0, 1, 1], [14, 0, 1], [13, 0, 1], [14, 1, 2], [14, 14, 1], [0, 14, 1]]) {
    if (!c.inside(x, 0, z)) continue
    for (let y = 0; y < h; y++) if (!c.get(x, y, z)) c.set(x, y, z, boulder)
  }
  c.set(0, 2, 0, P.bud, { facing: 'up', waterlogged: 'false' })
  c.set(14, 2, 1, P.bud, { facing: 'up', waterlogged: 'false' })
  return { c, buds }
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c } = draw(level)
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": []}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const name = level === 2 ? 'gem_grotto_2' : 'gem_grotto'
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${name}_cut${cut}` : name } }
}
