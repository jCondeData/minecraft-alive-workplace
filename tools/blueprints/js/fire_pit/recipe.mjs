// The Fire Pit (`fire_pit`), the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP 49) in
// passes (block-out and structure, the fire, depth and detail, interior, ground, light) and returns the structure for
// the minecraft-builder skill's runner. Writes furniture.json beside it for rebuild.py. CUT=<y> builds a cutaway with
// everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, FURNITURE, LIGHTS } from './plan.mjs'
import {
  Canvas, mix, roundDais, firePit, standingStone, trilithon, spit, logStack, furnitureParts,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, M = MASSES
  const floor = mix(P.floor, 3), ash = mix(P.ash, 5), step = mix(P.step, 7), stone = mix(P.stone, 11)
  const D = M.dais
  const feet = [...M.frontStones, ...M.sideStones.map(([x, z]) => [x, z]), [M.trilithon.xa, M.trilithon.z], [M.trilithon.xb, M.trilithon.z]]

  // --- Pass 1: block-out and structure: the dais, the stones and the trilithon ------------------------------------
  // the floor: hearthstones next to the fire, a trodden ash ring round them, cobble beyond
  const ring = (x, y, z) => {
    const d = Math.max(Math.abs(x - D.cx), Math.abs(z - D.cz))
    return d <= 1 ? P.hearth : d === 2 ? ash(x, y, z) : floor(x, y, z)
  }
  roundDais(c, D.cx, D.cz, D.r2, D.y, ring, step, [...feet, [D.cx, D.cz]])
  for (const [x, z] of M.frontStones) standingStone(c, x, z, D.y, M.frontHeight, stone)
  for (const [x, z, out] of M.sideStones) standingStone(c, x, z, D.y, M.sideHeight, stone, { stair: P.stoneCap, facing: out === 'west' ? 'east' : 'west' })
  const T = M.trilithon
  trilithon(c, T.xa, T.xb, T.z, T.y0, T.topY, stone, P.lintel, T.over, T.tips)

  // --- Pass 2: the fire: sunk in the hearth, the brick curb round it ----------------------------------------------
  firePit(c, ...M.pit.pos, { curbStair: P.curb.stairs, curbSlab: P.curb.slab, open: M.pit.open })

  // --- Pass 3: depth and detail: the spit over the fire -------------------------------------------------------------
  const S = M.spit
  spit(c, S.xa, S.xb, S.z, S.y0, P.spit)

  // --- Pass 4: interior: the benches turned to the fire ---------------------------------------------------------------
  for (const f of FURNITURE) furnitureParts[f.part](c, f)

  // --- Pass 5: ground: the woodpile and the chopping stump outside the ring --------------------------------------
  logStack(c, M.woodpile, P.fuel, 'x')
  c.set(...M.stump, P.stump, { axis: 'y' })

  // --- Pass 6: light: lanterns on the front stones' heads, hung under the lintel's ends ---------------------------
  for (const p of LIGHTS.standing) c.set(...p, 'lantern', { hanging: 'false', waterlogged: 'false' })
  for (const p of LIGHTS.hanging) c.set(...p, 'lantern', { hanging: 'true', waterlogged: 'false' })
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `fire_pit_cut${cut}` : 'fire_pit' } }
}
