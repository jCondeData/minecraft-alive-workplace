// The Traveler's Camp (the Wanderer's Tipi), the recipe: assembles plan.mjs from the shared parts library (../parts,
// ROADMAP 49; tents.mjs grown for it) in passes (ground, block-out of the tipi, its openings, depth and detail, the
// camp round it, the interior, light) and returns the structure for the minecraft-builder skill's runner. Writes
// furniture.json (the tipi's furniture spots) for rebuild.py and frames.json (the map's frame on the barrel) for
// frames.py, which adds that one entity to the saved .nbt (rebuild.py carries blocks only).
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, TRAVELER, PALETTE, MASSES, OPENINGS, FURNITURE, JUG } from './plan.mjs'
import { Canvas, mix, trapdoor, firePit, spit, logStack, lampPost, furnitureParts, coneTent, waymark } from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, M = MASSES, T = M.tipi
  const [w, , d] = SIZE
  const ground = mix(P.ground, 3), rim = mix(P.rim, 5), floor = mix(P.floor, 11), hearth = mix(P.hearthGround, 2)
  const inTipi = (x, z) => (x - T.cx) ** 2 + (z - T.cz) ** 2 <= T.tiers[0].r2
  const fx = M.fire.at[0], fz = M.fire.at[2]

  // --- Pass 1: ground: the round trodden pad, its grassy rim, grass in the corners (no cell of y = 0 is air) --------
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) {
    const r = (x - M.pad.cx) ** 2 + (z - M.pad.cz) ** 2
    if (r > M.pad.r2) { c.set(x, 0, z, P.corner); continue }
    if (Math.abs(x - fx) <= 1 && Math.abs(z - fz) <= 1) c.set(x, 0, z, hearth)
    else if (inTipi(x, z)) c.set(x, 0, z, floor)
    else c.set(x, 0, z, r >= M.pad.rimFrom ? rim : ground)
  }
  for (const [x, y, z] of M.paths) c.set(x, y, z, P.path)
  for (let x = 0; x < w; x++) for (let z = 0; z < d; z++) { const n = c.name(x, 0, z); if (n === 'minecraft:grass_block' || n === 'minecraft:podzol') c.set(x, 0, z, n, P.grassProps) } // snowy: false, as the game saves them
  c.set(TRAVELER[0], 0, TRAVELER[2], P.standing)

  // --- Pass 2: block-out: the tipi, three tiers of painted hide stepping in to the peak -----------------------------
  const H = P.hide
  const marks = new Set(T.marks.map(m => m.join(',')))
  coneTent(c, T, {
    hide: (tier, x, y, z) => {
      if (tier === 2) return H.crown // the peak, smoke-dark red
      if (tier === 1) return H.body
      if (y === T.y0) return H.band // the red ochre band round the foot
      return marks.has(`${x},${y},${z}`) ? H.mark : H.body
    },
    hem: tier => (tier === 0 ? P.hem.low : P.hem.high),
    pole: P.pole,
  })

  // the door flap, folded back: two open trapdoors against the doorway's jamb, so the way in stays clear
  const od = OPENINGS.tipiDoor
  for (let y = od.at[1]; y < od.at[1] + od.height; y++) trapdoor(c, od.at[0], y, od.at[2], P.flap, od.flap, y === od.at[1] ? 'bottom' : 'top', true)

  // --- Pass 3: the camp: fire and spit, the map barrel, the seat, the woodpile and block, the cairn, the staff -------
  firePit(c, fx, 0, fz, { curbStair: P.curbStair, curbSlab: P.curbSlab, open: M.fire.open })
  const s = M.fire.spit
  spit(c, s.xa, s.xb, s.z, s.y0, P.spit)
  const [bx, by, bz] = M.barrel
  c.set(bx, by, bz, 'barrel', { facing: 'up', open: 'false' }) // the camp's only barrel: the map lies on its lid
  logStack(c, M.seat, P.seat, 'z')
  logStack(c, M.woodpile, P.firewood, 'z')
  c.set(...M.chopping, P.chopping, { axis: 'y' })
  waymark(c, M.cairn.at, { stones: P.cairn.stones, cap: P.cairn.cap, skirt: M.cairn.skirt.map(k => ({ at: k.at, slab: P.cairn[k.slab] })) })

  // --- Pass 4: interior: the bedroll, the pack, the rug, the jug --------------------------------------------------
  for (const f of FURNITURE) furnitureParts[f.part](c, f)
  c.set(...JUG.at, JUG.block, { facing: 'east', cracked: 'false', waterlogged: 'false' })

  // --- Pass 5: light: the lantern down the smoke shaft (a furniture lamp, above) and one on the traveler's staff -----
  lampPost(c, M.staff.at[0], M.staff.at[1], M.staff.at[2], P.staff, M.staff.height)

  // the traveler's cells stay clear
  c.free(TRAVELER, [TRAVELER[0], TRAVELER[1] + 1, TRAVELER[2]])
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  fs.writeFileSync(path.join(here, 'frames.json'), JSON.stringify({ frames: [{ at: MASSES.frame, facing: 'up', item: 'minecraft:map' }] }, null, 1) + '\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `traveller_camp_cut${cut}` : 'traveller_camp' } }
}
