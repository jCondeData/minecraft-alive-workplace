// The Palisade (`palisade`, Level II `palisade_2`), the recipe: assembles plan.mjs from the shared parts library
// (../parts, ROADMAP 52.3) in passes (block-out and structure, depth and detail, ground) and returns the structure for
// the minecraft-builder skill's runner. LEVEL=1 (default) draws `palisade`; LEVEL=2 draws `palisade_2`, Level I drawn
// first and grown. Writes furniture.json beside it for rebuild.py. CUT=<y> cuts everything above y.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, FURNITURE } from './plan.mjs'
import { Canvas, extrude, stakeBed, slab, furnitureParts } from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level = 1) {
  const c = new Canvas(SIZE[level])
  const P = PALETTE, M = MASSES, { x0, x1 } = M.run
  const stair = (facing) => ({ facing, half: 'bottom', shape: 'straight', waterlogged: 'false' })

  // --- Pass 1: block-out and structure: the one section, drawn on every column (copies overlap at any shift) --------
  const section = [
    [M.revetment.z, M.revetment.footY, P.foot],
    [M.bank.z, M.bank.footY, P.foot],
    [M.bank.z, M.bank.y, P.bank],
    [M.walk.z, M.walk.y, P.walk, { axis: 'x' }], // split logs laid along the wall: the walk, seven stations
  ]
  for (let y = M.stakes.y0; y <= M.stakes.y1; y++) section.push([M.stakes.z, y, P.stake, { axis: 'y' }])
  // --- Pass 2: depth and detail: the flight up the inside face, the wale through the stakes --------
  section.push([M.toe.z, M.toe.y, P.toe, stair(M.toe.facing)])
  section.push([M.revetment.z, M.revetment.y, P.revetment, stair(M.revetment.facing)])
  section.push([M.wale.z, M.wale.y, P.wale, { facing: M.wale.facing, half: 'top', shape: 'straight', waterlogged: 'false' }])
  extrude(c, x0, x1, section)

  if (level >= 2) {
    // --- Level II: the bank built out over the flight (a walk three wide) on every column but x 3, which keeps the
    // flight as the stair up through it; the stake ditch outside (turned earth, a dripstone stake standing in it)
    const F = M.fightingStep
    for (const x of F.xs) for (const z of F.zs) {
      c.set(x, 0, z, P.foot)
      c.set(x, 1, z, P.bank)
      slab(c, x, F.y, z, P.fightingStep, 'top')
    }
    stakeBed(c, M.ditch.xs.map(x => [x, M.ditch.z]), M.ditch.y, { bed: P.ditch, stake: P.spike })
  }
  for (const f of FURNITURE[level]) furnitureParts[f.part](c, f)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = level === 1 ? 'palisade' : `palisade_${level}`
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
