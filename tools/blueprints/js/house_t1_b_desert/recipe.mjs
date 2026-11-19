// Daub House, desert look (49.10d), the recipe: assembles plan.mjs in passes (block-out and structure, roofs and
// towers, openings, depth and detail, interior, ground) on the parts library (../parts), the kept insides from
// ../parts/daub_looks.mjs. Writes furniture.json. Build: build.sh. CUT=<y>: a cutaway.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, WINDOWS, OUTSIDE } from './plan.mjs'
import { Canvas, mix, stair, slab, screenOriel } from '../parts/index.mjs'
import { daubInterior, daubSpots } from '../parts/daub_looks.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const OUT = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, H = MASSES.hall, T = MASSES.towers, K = MASSES.kitchen
  const mud = mix(P.mud, 4), foot = mix(P.foot, 9)
  const ring = (x, z) => x === H.x0 || x === H.x1 || z === H.z0 || z === H.z1
  const tower = (x, z) => T.at.some(([tx, tz]) => x >= tx && x <= tx + 1 && z >= tz && z <= tz + 1)

  // --- Pass 1: block-out and structure: the hall's walls, the beamed ceiling and the deck ---------------------------
  for (let x = H.x0; x <= H.x1; x++) for (let z = H.z0; z <= H.z1; z++) {
    const thick = z <= H.z0 + 1 || z >= H.z1 - 1 // the ground storey's front and back are two thick
    if (ring(x, z) || thick) c.set(x, 0, z, P.plinth)
    for (let y = 1; y <= H.groundTop; y++) if (ring(x, z) || thick) c.set(x, y, z, mud)
    for (let y = H.floorY; y <= H.ceilingY; y++) if (ring(x, z)) c.set(x, y, z, mud)
    if (!ring(x, z)) c.set(x, H.ceilingY, z, z % 2 === 0 ? P.ceiling : P.upperFloor, z % 2 === 0 ? { axis: 'x' } : {})
    c.set(x, H.deckY, z, P.deck)
  }
  // the deck's course round every face in cut sandstone: one dressed line carried from tower to tower
  for (let x = H.x0; x <= H.x1; x++) for (let z = H.z0; z <= H.z1; z++) if (ring(x, z)) c.set(x, H.deckY, z, P.course)
  // the parapet, level all round, and its coping
  for (let x = H.x0; x <= H.x1; x++) for (let z = H.z0; z <= H.z1; z++) {
    if (!ring(x, z)) continue
    c.set(x, H.parapetY, z, mud)
    slab(c, x, H.copingY, z, P.coping, 'bottom')
  }

  // --- Pass 2: the towers and the kitchen's vault ----------------------------------------------------------------------
  for (const [tx, tz] of T.at) {
    for (let x = tx; x <= tx + 1; x++) for (let z = tz; z <= tz + 1; z++) {
      for (let y = 0; y <= T.top; y++) {
        const id = y === 0 ? P.plinth : y <= T.foot ? foot : T.courses.includes(y) ? P.course : mud
        c.set(x, y, z, id)
      }
      // the crown: four stairs rising to the tower's middle (finish() turns them into a little stepped cap)
      const facing = x === tx ? (z === tz ? 'south' : 'east') : (z === tz ? 'west' : 'north')
      stair(c, x, T.crownY, z, P.crown, facing)
    }
  }
  // the kitchen: mud walls, then a barrel vault along x (its ridge east-west), its east end a lunette
  for (let x = K.x0; x <= K.x1; x++) for (let z = K.z0; z <= K.z1; z++) {
    const wall = x === K.x1 || z === K.z0 || z === K.z1
    if (!wall) continue
    c.set(x, 0, z, P.plinth)
    for (let y = 1; y <= K.wallTop; y++) c.set(x, y, z, mud)
  }
  // the vault's section across z (z 3..9): [z, y, kind, facing]: outer stairs, inner (upside-down) stairs, the crown
  const section = [
    [3, 5, 'out', 'south'], [4, 5, 'in', 'north'], [4, 6, 'out', 'south'], [5, 6, 'in', 'north'], [5, 7, 'out', 'south'],
    [6, 7, 'crown'], [7, 6, 'in', 'south'], [7, 7, 'out', 'north'], [8, 5, 'in', 'south'], [8, 6, 'out', 'north'], [9, 5, 'out', 'north'],
  ]
  for (let x = K.x0; x <= K.x1; x++) for (const [z, y, kind, facing] of section) {
    const rim = x === K.x1 // the east end's rim: the arch drawn in pale stone
    if (kind === 'crown') c.set(x, y, z, rim ? P.dressed : P.vault)
    else stair(c, x, y, z, rim ? P.arch : P.vaultStairs, facing, kind === 'in' ? 'top' : 'bottom')
  }
  for (let x = K.x0; x <= K.x1; x++) for (const z of [K.z0, K.z1]) c.set(x, K.wallTop, z, P.course) // the springing course
  for (let z = K.z0; z <= K.z1; z++) c.set(K.x1, K.wallTop, z, P.course)
  for (const [z, y] of [[5, 5], [6, 5], [7, 5], [6, 6]]) c.set(K.x1, y, z, P.vault) // the lunette closing the east end
  for (const [z, y] of [[4, 5], [5, 6], [7, 6], [8, 5]]) c.set(K.x1, y, z, P.vault)
  c.set(10, 8, 6, P.vault) // the oven's flue on the crown, smoking
  c.set(10, 9, 6, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })

  // --- Pass 3: openings: the door niche, the windows ----------------------------------------------------------------
  const N = MASSES.niche
  for (let x = N.x0; x <= N.x1; x++) { c.set(x, 0, N.z, P.plinth); for (let y = 1; y <= 2; y++) c.clear(x, y, N.z) }
  c.clear(4, N.archY, N.z)
  stair(c, N.x0, N.archY, N.z, P.arch, 'west', 'top')
  stair(c, N.x1, N.archY, N.z, P.arch, 'east', 'top')
  c.set(4, N.archY, 2, P.carved) // a carved lintel over the door
  for (const x of [3, 5]) for (let y = 1; y <= N.archY; y++) c.set(x, y, 2, P.dressed) // the door's dressed jambs
  for (const w of WINDOWS) {
    for (const [x, y, z] of w.cells) c.set(x, y, z, P.glass)
    if (w.dress === null || w.dress === 'oriel') continue
    const [ox, oz] = OUT[w.out]
    const low = Math.min(...w.cells.map(p => p[1])), high = Math.max(...w.cells.map(p => p[1]))
    for (const [x, y, z] of w.cells) if (y === low) slab(c, x + ox, y - 1, z + oz, P.sill, 'top')
  }

  // --- Pass 4: depth and detail: the oriel, the toron, the batter, the stack, the hearth -----------------------------
  const O = MASSES.oriel
  screenOriel(c, { x0: O.x0, x1: O.x1, z: O.z, y0: O.y0, y1: O.y1 },
    { post: P.latticePost, lattice: P.lattice, roof: P.latticeRoof, sill: P.dressed, corbel: P.corbel })
  c.set(4, N.archY, N.z, 'lantern', { hanging: 'true', waterlogged: 'false' }) // light in the arch, over the door
  for (const z of MASSES.toron.z) c.set(H.x0 - 1, MASSES.toron.y, z, P.toron) // the hall's floor beams' ends (toron)
  // a batter skirt along the side walls' foot, between the towers
  for (let z = H.z0 + 1; z < H.z1; z++) { c.set(0, 0, z, P.plinth); stair(c, 0, 1, z, P.batter, 'east') }
  for (const x of [2, 6]) { c.set(x, 0, 12, P.plinth); stair(c, x, 1, 12, P.batter, 'north') }
  const S = MASSES.stack
  for (const x of S.breast) {
    for (let y = 0; y <= S.breastTop; y++) c.set(x, y, S.z, y <= T.foot ? foot : mud)
    stair(c, x, S.breastTop + 1, S.z, P.crown, x < S.x ? 'east' : 'west')
  }
  for (let y = 0; y <= S.top; y++) c.set(S.x, y, S.z, y <= T.foot ? foot : T.courses.includes(y) ? P.course : mud)
  c.set(S.x, S.top + 1, S.z, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  for (const x of [3, 5]) for (let y = 1; y <= 3; y++) c.set(x, y, 10, P.course) // the hearth's sandstone cheeks
  c.set(4, 2, 10, P.dressed); c.set(4, 3, 10, P.carved)

  // --- Pass 5: interior: the shipped Daub House's insides, cell for cell ----------------------------------------------
  daubInterior(c, P, { step: OUTSIDE.step })

  // --- Pass 6: ground: cacti in a sand strip behind the kitchen, water jars by the door ----------------------------------------
  for (const [x, z, plant] of OUTSIDE.garden) { // a strip of sand behind the kitchen: cacti and a dead bush
    c.set(x, 0, z, 'sand')
    if (plant === 'cactus') { c.set(x, 1, z, 'cactus', { age: '0' }); c.set(x, 2, z, 'cactus', { age: '0' }) } else c.set(x, 1, z, plant)
  }
  for (const [x, y, z] of OUTSIDE.jars) c.set(x, y, z, 'decorated_pot', { facing: 'north', cracked: 'false', waterlogged: 'false' })
  return c
}

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + daubSpots(c).map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `house_t1_b_cut${cut}` : 'house_t1_b' } }
}
