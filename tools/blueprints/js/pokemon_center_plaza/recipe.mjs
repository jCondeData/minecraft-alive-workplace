// The Pokémon Center's Sunny Plaza look, the recipe: assembles plan.mjs from the shared parts library (../parts) in
// passes (block-out and structure, openings, depth and detail, interior, ground). LEVEL=1|2 (default 1): II draws I
// first and grows out of it (the court, the arcade), keeping I's cells but for the back window it opens into a door.
// Writes furniture.json (the level's spots). Build: sh build.sh; CUT=<y> builds a cutaway above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE as P, HALL, LOGGIA, FIXED, OPENINGS, FURNITURE } from './plan.mjs'
import {
  Canvas, mix, stair, slab, wallPanel, door, hipRoof, gableRoof, gableWall, archHead, furnitureParts, railingRun, roundCurb,
  pool, lampPost, healingMachine, pcTower, pastureBlock, shopCounter, deskRun, DIRS, OPP,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

function furnish(c, list) {
  for (const f of list) {
    if (f.part === 'desk') deskRun(c, f.cells, f.facing, { base: `${f.wood}_planks`, top: 'smooth_stone_slab', wood: f.wood })
    else furnitureParts[f.part](c, f)
  }
}

/** the plaza's window: two panes (y 2..3) in a sandstone surround, a sill stair under it one out (on the socle), a
 *  head of sandstone over it in the wall */
function plazaWindow(c, x, z, out) {
  const [ox, oz] = DIRS[out]
  c.set(x, 2, z, P.glass); c.set(x, 3, z, P.glass)
  c.set(x, 4, z, P.dress)
  stair(c, x + ox, 1, z + oz, P.dressStairs, OPP[out], 'top')
}

// --- Level I: the Sunny Plaza's hall and loggia ------------------------------------------------------------------------
function levelOne(c) {
  const socle = mix(P.socle, 31), floorMix = mix(P.floor, 32)
  const H = HALL, L = LOGGIA
  // Pass 1: block-out and structure. Floor, socle (y 0..1), white walls, the red band, the ceiling, the low tile roof
  c.fill(H.x0, 0, L.z0, H.x1, 0, H.z1, floorMix)
  for (let x = H.x0; x <= H.x1; x++) for (let z = H.z0; z <= H.z1; z++) {
    if (x !== H.x0 && x !== H.x1 && z !== H.z0 && z !== H.z1) continue
    c.set(x, 0, z, socle); c.set(x, 1, z, socle)
    for (let y = 2; y <= H.top; y++) c.set(x, y, z, P.wall)
    c.set(x, H.band, z, P.band)
  }
  c.fill(H.x0 + 1, H.ceiling, H.z0 + 1, H.x1 - 1, H.ceiling, H.z1 - 1, P.ceiling)
  // the roof's first ring sits on the walls as whole tiles (a top slab there would leave a half-block slot over the band)
  for (let x = H.x0; x <= H.x1; x++) for (const z of [H.z0, H.z1]) c.set(x, H.ceiling, z, P.roof.full)
  for (let z = H.z0; z <= H.z1; z++) for (const x of [H.x0, H.x1]) c.set(x, H.ceiling, z, P.roof.full)
  hipRoof(c, H.roof, P.roof, (x, y, z) => !c.get(x, y, z) || y > H.ceiling)
  // the corner piers of the hall in sandstone (quoins that frame each face)
  for (const [x, z] of [[H.x0, H.z0], [H.x1, H.z0], [H.x0, H.z1], [H.x1, H.z1]]) for (let y = 2; y <= H.top; y++) c.set(x, y, z, P.dress)

  // the loggia: piers on the front line, side walls, a ceiling at y 4 with the red band's fascia on its front (two
  // deep and three high inside: shade, not a room), steps into each bay
  const T = L.ceiling + 1 // the terrace's walking level
  for (const x of L.piers) for (let y = 1; y < L.ceiling; y++) c.set(x, y, L.z0, P.dress)
  for (const x of [H.x0, H.x1]) for (let z = L.z0 + 1; z < H.z0; z++) { c.set(x, 1, z, socle); for (let y = 2; y < L.ceiling; y++) c.set(x, y, z, P.wall) }
  for (let x = H.x0; x <= H.x1; x++) {
    for (let z = L.z0; z < H.z0; z++) c.set(x, L.ceiling, z, P.ceiling)
    c.set(x, L.ceiling, L.z0, P.band)
  }
  for (const [a, b] of L.bays) {
    for (let x = a; x <= b; x++) stair(c, x, 0, L.z0, P.dressStairs, 'south')
    // the middle bay a round arch (its keystone breaks the band over the door), the side bays square under the band:
    // open to the band's underside, so the shade behind them stays a porch, not a room
    if (b - a === 2) archHead(c, a, b, L.z0, 3, P.dressStairs, P.key)
  }
  // the terrace over the loggia: a sandstone balustrade round its open edges (a post at each turn)
  railingRun(c, [[H.x0, T, H.z0 - 1], [H.x0, T, L.z0], [H.x1, T, L.z0], [H.x1, T, H.z0 - 1]], null, P.baluster)
  // the bell-cote over the middle arch: two piers, an arched opening with a lantern hung in it, a pediment
  for (const x of [5, 7]) for (let y = T; y <= T + 2; y++) c.set(x, y, L.z0, P.dress)
  c.set(6, T, L.z0, P.dress)
  for (const x of [5, 6, 7]) c.set(x, T + 3, L.z0, P.dress)
  stair(c, 5, T + 4, L.z0, P.roof.stairs, 'east'); stair(c, 7, T + 4, L.z0, P.roof.stairs, 'west'); c.set(6, T + 4, L.z0, P.roof.full)
  c.set(6, T + 2, L.z0, 'lantern', { hanging: 'true', waterlogged: 'false' })
  c.set(6, T + 5, L.z0, 'lightning_rod', { facing: 'up', powered: 'false', waterlogged: 'false' })

  // Pass 2: openings. The front door under the middle arch, the windows, the loggia's side windows
  door(c, OPENINGS.frontDoor, 'south', 'north', { wood: P.wood })
  for (const [x, z, out] of [...OPENINGS.windows, OPENINGS.backWindow]) plazaWindow(c, x, z, out)
  for (const [x, z] of OPENINGS.loggiaSides) { c.set(x, 2, z, P.glass); c.set(x, 3, z, P.glass) }

  // Pass 3: depth: lanterns hung under the loggia ceiling either side of the door; stone benches in the side bays
  for (const x of [3, 9]) c.set(x, L.ceiling - 1, 2, 'lantern', { hanging: 'true', waterlogged: 'false' })
  for (const [x, f] of [[2, 'east'], [10, 'west']]) { stair(c, x, 1, 2, P.dressStairs, OPP[f]); c.spot('bench', [x, 1, 2], f, [[0, 0, 0]], P.wood) }

  // Pass 4: interior
  furnish(c, FURNITURE.filter(f => f.level === 1))
  healingMachine(c, FIXED.machine, 'north')
  pcTower(c, FIXED.pc, 'west')
}

// --- Level II: the court and the market arcade -----------------------------------------------------------------------
function levelTwo(c) {
  const socle = mix(P.socle, 33), pave = mix(P.paving, 34)
  const empty = (x, y, z) => !c.get(x, y, z)
  // Pass 1: the court's paving (at the hall's floor level), its low wall with posts at the turns
  for (let x = 0; x <= 18; x++) for (let z = 12; z <= 25; z++) if (empty(x, 0, z)) c.set(x, 0, z, pave)
  railingRun(c, [[0, 1, 13], [0, 1, 25], [18, 1, 25]], null, P.baluster)
  for (const x of [7, 8, 9, 10]) c.clear(x, 1, 25) // the gate to the fields
  // the market hall: a back wall at x 18 (z 12..24), an arcaded front at x 14 (piers, glazed arches, the door in the
  // wide middle bay), end walls, a ceiling at y 5 with the red band on the front
  const A = { x0: 14, x1: 18, z0: 12, z1: 24 }
  for (let z = A.z0; z <= A.z1; z++) {
    c.set(A.x1, 0, z, socle); c.set(A.x1, 1, z, socle)
    for (let y = 2; y <= 4; y++) c.set(A.x1, y, z, P.wall)
    for (let x = A.x0; x <= A.x1; x++) c.set(x, 5, z, x === A.x0 ? P.band : P.ceiling)
  }
  for (const z of [A.z0, A.z1]) for (let x = A.x0 + 1; x < A.x1; x++) { c.set(x, 1, z, socle); for (let y = 2; y <= 4; y++) c.set(x, y, z, P.wall) }
  for (const z of [A.z0, 15, 21, A.z1]) { c.set(A.x0, 0, z, socle); for (let y = 1; y <= 4; y++) c.set(A.x0, y, z, P.dress) }
  // the bays: two-wide arched windows either side (panes under shoulder stairs), the wide middle bay with the door,
  // glass either side of it, a lintel over its rounded shoulders and the keystone above
  for (const [a, b] of [[13, 14], [16, 20], [22, 23]]) {
    for (let z = a; z <= b; z++) { c.set(A.x0, 0, z, socle); c.set(A.x0, 4, z, P.wall) }
    if (b - a === 4) {
      for (const z of [16, 17, 19, 20]) for (const y of [1, 2]) c.set(A.x0, y, z, P.glass)
      for (const z of [16, 20]) stair(c, A.x0, 3, z, P.dressStairs, z === 16 ? 'north' : 'south', 'top')
      for (const z of [17, 18, 19]) c.set(A.x0, 3, z, P.dress)
      c.set(A.x0, 4, 18, P.key)
    } else {
      for (let z = a; z <= b; z++) { c.set(A.x0, 1, z, P.dress); c.set(A.x0, 2, z, P.glass) }
      stair(c, A.x0, 3, a, P.dressStairs, 'north', 'top'); stair(c, A.x0, 3, b, P.dressStairs, 'south', 'top')
    }
  }
  door(c, [A.x0, 1, 18], 'east', 'west', { wood: P.wood })
  // a low gable of tiles over it, ridge along z (y 9), eaves a block out over the court and over the back lane, the
  // gable ends plastered with a little window each; arched windows in the back wall, their sills on a socle step
  const rf = gableRoof(c, { axis: 'z', across: [A.x0 - 1, A.x1 + 1], along: [A.z0 - 1, A.z1 + 1], eaveY: 6, steep: false }, P.roof)
  for (const line of [A.z0, A.z1]) gableWall(c, 'z', line, [A.x0, A.x1], 6, rf.under, (u, y) => (u === 16 && y === 7 ? P.glass : P.wall))
  for (let z = A.z0; z <= A.z1; z++) c.set(A.x1 + 1, 0, z, socle)
  for (const z of [14, 18, 22]) plazaWindow(c, A.x1, z, 'east')
  // red cloths hung on the two piers either side of the door (the market's sign), a lantern over the door
  for (const z of [15, 21]) c.set(A.x0 - 1, 4, z, 'red_wall_banner', { facing: 'west' })

  // Pass 2: the back door from the hall into the court
  const [bx, bz] = OPENINGS.backWindow
  c.clear(bx, 1, bz + 1)
  door(c, [bx, 1, bz], 'north', 'south', { wood: P.wood })
  c.set(bx, 3, bz, P.glass); c.set(bx, 0, bz + 1, pave)

  // Pass 4: the arcade's stall and the court's furnishings
  furnish(c, FURNITURE.filter(f => f.level === 2))
  shopCounter(c, FIXED.shopCounter, 'west')
  // the fountain: a sandstone curb round a 3 x 3 pool on the paving, a column with a potted flower in the middle
  const fx = 9, fz = 18
  const water = []
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) if (dx || dz) water.push([fx + dx, fz + dz])
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) c.set(fx + dx, 0, fz + dz, P.dress)
  roundCurb(c, fx, fz, 2, 1, P.dress, [...water, [fx, fz]])
  c.set(fx, 1, fz, P.dress); c.set(fx, 2, fz, P.dress); c.set(fx, 3, fz, P.dressSlab, { type: 'bottom', waterlogged: 'false' })
  pool(c, water, 1)
  // the lawn bed for the Pasture Block: grass in a sandstone kerb, the block in the middle
  const [px, , pz] = FIXED.pasture
  for (let dx = -2; dx <= 2; dx++) for (let dz = -2; dz <= 2; dz++) {
    const edge = Math.abs(dx) === 2 || Math.abs(dz) === 2
    c.set(px + dx, 0, pz + dz, edge ? P.dress : 'grass_block', edge ? {} : { snowy: 'false' })
  }
  pastureBlock(c, FIXED.pasture, 'north')
  for (const [x, z] of [[3, 20], [5, 22]]) c.set(x, 1, z, 'poppy')
  // lamps at the court's corners, orange trees in pots by the hall's back door, benches facing the fountain
  for (const [x, z] of [[1, 14], [1, 24], [13, 24]]) lampPost(c, x, 1, z, `${P.wood}_fence`, 2)
  for (const [x, z] of [[4, 13], [8, 13]]) c.set(x, 1, z, 'potted_flowering_azalea_bush')
  for (const [x, z, f] of [[9, 15, 'south'], [9, 21, 'north']]) for (const dx of [-1, 0, 1]) {
    stair(c, x + dx, 1, z, `${P.wood}_stairs`, OPP[f])
    c.spot('bench', [x + dx, 1, z], f, [[0, 0, 0]], P.wood)
  }
}

export function draw(level = 1) {
  const c = new Canvas(SIZE[level])
  levelOne(c)
  if (level >= 2) levelTwo(c)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = level === 1 ? 'pokemon_center' : `pokemon_center_${level}`
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `plaza_${id}_cut${cut}` : id } }
}
