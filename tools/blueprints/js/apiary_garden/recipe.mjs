// The Apiary Garden ("the Skep Wall"), the recipe: assembles plan.mjs from the shared parts library (../parts) in
// passes (ground, block-out and structure, openings and hives, depth and detail, the stores inside, planting, light),
// then proves the Beekeeper's rules (hiveCheck) and returns the structure for the minecraft-builder skill's runner.
// LEVEL=1 (apiary_garden) or 2 (apiary_garden_2); Level II is Level I drawn again and grown (the same passes, with the
// Level II rows of the plan). Writes furniture.json (no spots: this family has no furnished room). CUT=<y> cuts above y.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  LEVELS, PALETTE, GROUND, FLOWERS, WALLS, WALL, BOLES, FRONT_BUTTRESSES, BACK_BUTTRESSES, SKEPS, GATE_PIERS, FONT,
  CHERRY,
} from './plan.mjs'
import {
  Canvas, fairMix, stair, slab, plantFlower, clip, tree, canopy, hangingLantern, lampPost, doubleChest, door,
  skepDome, smokedHive, beeBole, beeFont, hiveCheck,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE

export function draw(level = 1) {
  const c = new Canvas(LEVELS[level].size)
  const wall = fairMix(P.wall, 3), wallHigh = fairMix(P.wallHigh, 5), plinth = fairMix(P.plinth, 7), hedge = fairMix(P.hedge, 11)
  const rows = GROUND[level]
  const grass = (x, z) => c.set(x, 0, z, P.soil, { snowy: 'false' })

  // --- Pass 1: the ground (the y 0 layer: soil, paths, stone under every stone thing) ------------------------------
  rows.forEach((row, z) => [...row].forEach((ch, x) => {
    if (ch === 'p') c.set(x, 0, z, P.path)
    else if ('WBG'.includes(ch)) c.set(x, 0, z, plinth)
    else if ('SF'.includes(ch)) c.set(x, 0, z, P.stand)
    else grass(x, z)
  }))

  // --- Pass 2: block-out and structure: the bole walls, their coping and buttresses, the skeps --------------------
  for (const w of WALLS[level]) {
    for (let x = w.x0; x <= w.x1; x++) {
      for (let z = WALL.z0; z <= WALL.z1; z++) for (let y = 1; y <= WALL.top; y++) c.set(x, y, z, y === 1 ? wall : wallHigh)
      // saddleback coping: a stair each side rising to a ridge course, a slab along the ridge
      stair(c, x, WALL.coping, WALL.z0, P.coping.stairs, 'south')
      c.set(x, WALL.coping, WALL.z0 + 1, P.coping.full)
      stair(c, x, WALL.coping, WALL.z1, P.coping.stairs, 'north')
      slab(c, x, WALL.coping + 1, WALL.z0 + 1, P.coping.slab)
    }
    if (w.end !== null) { // the end pier: the coping squared off, a wall post and its lantern on the ridge
      for (let z = WALL.z0; z <= WALL.z1; z++) c.set(w.end, WALL.coping, z, P.coping.full)
      lampPost(c, w.end, WALL.coping + 1, WALL.z0 + 1, P.coping.wall)
    }
  }
  for (const x of FRONT_BUTTRESSES[level]) {
    c.set(x, 1, WALL.z0 - 1, wall); c.set(x, 2, WALL.z0 - 1, wallHigh)
    stair(c, x, 3, WALL.z0 - 1, P.coping.stairs, 'south')
  }
  for (const x of BACK_BUTTRESSES) {
    c.set(x, 1, WALL.z1 + 1, wall); c.set(x, 2, WALL.z1 + 1, wallHigh)
    stair(c, x, 3, WALL.z1 + 1, P.coping.stairs, 'north')
  }
  const skeps = level >= 2 ? [SKEPS.mother, SKEPS.daughter] : [SKEPS.mother]
  for (const s of skeps) skepDome(c, s.centre[0], s.centre[1], 1, s.courses, P.straw)

  // --- Pass 3: openings and hives: the boles, the skeps' doors and their smoked hives -----------------------------
  const ids = { hive: P.hive, fire: P.fire, head: P.coping.stairs }
  for (const [x, y, z] of BOLES) if (c.get(x, y, z)) beeBole(c, [x, y, z], 'north', ids)
  for (const s of skeps) {
    for (const [x, y, z] of s.lintel) c.set(x, y, z, P.lintel, { axis: 'x' })
    door(c, s.door, 'south', 'north', { wood: P.wood })
    for (const h of s.hives[level] ?? []) smokedHive(c, h, 'north', ids)
  }

  // --- Pass 4: depth and detail: the gate piers, the bee font ----------------------------------------------------
  for (const [x, z] of GATE_PIERS) { c.set(x, 1, z, wall); lampPost(c, x, 2, z, P.coping.wall) }
  beeFont(c, FONT.centre[0], FONT.y, FONT.centre[1], { stairs: P.coping.stairs, corner: P.coping.full, pad: P.pad })

  // --- Pass 5: the stores inside the skeps (no furnished rooms: chests, comb and a hung lantern) -------------------
  for (const s of skeps) {
    doubleChest(c, s.chest.pos, s.chest.facing, s.chest.along)
    for (const [x, y, z] of [...s.comb, ...(s.backing[level] ?? [])]) c.set(x, y, z, P.store)
    hangingLantern(c, ...s.lamp)
  }

  // --- Pass 6: planting: the beds, the hedge, the cherry (II) ------------------------------------------------------
  rows.forEach((row, z) => [...row].forEach((ch, x) => {
    if (ch === 'H') clip(c, [[x, 1, z]], hedge)
    else if (FLOWERS[ch]) plantFlower(c, x, 1, z, FLOWERS[ch])
  }))
  if (level >= 2) tree(c, CHERRY.logs, canopy(CHERRY.crown), P.tree.log, P.tree.leaf)

  // --- Check: every hive keeps the Beekeeper's rules (face on air, 4 flowers, a chest in reach, soil to plant) -----
  const flowers = new Set([...Object.values(FLOWERS).filter(f => f !== 'fern'), 'flowering_azalea_leaves', 'cherry_leaves'])
  const lines = hiveCheck(c, { hive: P.hive, chest: P.chest, flowers, soil: new Set([P.soil]) })
  if (process.env.VERBOSE) console.error(lines.join('\n'))
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = level > 1 ? `apiary_garden_${level}` : 'apiary_garden'
  const c = draw(level)
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": []}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
