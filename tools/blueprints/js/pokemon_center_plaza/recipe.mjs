// The Pokémon Center's Sunny Plaza look, the recipe: assembles plan.mjs from the shared parts library (../parts) in
// passes (block-out and structure, openings, depth and detail, interior, ground). LEVEL=1|2 (default 1): II draws I
// first and grows out of it (the court, the market hall), keeping I's cells but for the back window it opens into a
// door. Writes furniture.json (the level's spots). Build: sh build.sh; CUT=<y> builds a cutaway above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE as P, HALL, LOGGIA, ARCADES, COTE, APRON, FIXED, OPENINGS, FURNITURE, COURT } from './plan.mjs'
import {
  Canvas, mix, stair, door, gableRoof, gableWall, archHead, furnitureParts, railingRun, roundCurb, pool, lampPost,
  plantFlower, healingMachine, pcTower, pastureBlock, shopCounter, deskRun, tileHip, tileArcade, DIRS, OPP,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

function furnish(c, list) {
  for (const f of list) {
    if (f.part === 'desk') deskRun(c, f.cells, f.facing, { base: `${f.wood}_planks`, top: 'smooth_stone_slab', wood: f.wood })
    else furnitureParts[f.part](c, f)
  }
}

/** the plaza's window: two panes (y 2..3) under a sandstone head in the wall, a sill stair under it one out */
function plazaWindow(c, x, z, out) {
  const [ox, oz] = DIRS[out]
  c.set(x, 2, z, P.glass); c.set(x, 3, z, P.glass)
  c.set(x, 4, z, P.dress)
  stair(c, x + ox, 1, z + oz, P.dressStairs, OPP[out], 'top')
}

/** bayWindow: the window in an arcade bay, so the bay reads as a window and not a hole. In the hall wall at (x, z): an
 *  arched light set a block back (three panes at y 2, the middle one at y 3, under the bay's round head; a corner
 *  stone in the run stays), a sandstone head over it; one out, in the arcade's plane: a cut-sandstone breast across
 *  the bay at y 1 (the sill course, with the piers) and a pot of flowers on its middle. */
function bayWindow(c, x, z, out, pot) {
  const [ox, oz] = DIRS[out]
  const [ax, az] = ox ? [0, 1] : [1, 0]
  for (const d of [-1, 0, 1]) {
    const wx = x + ax * d, wz = z + az * d
    if (c.get(wx, 2, wz) !== P.dress) c.set(wx, 2, wz, P.glass)
    c.set(wx + ox, 1, wz + oz, P.dress)
  }
  c.set(x, 3, z, P.glass); c.set(x, 4, z, P.dress)
  if (pot) c.set(x + ox, 2, z + oz, pot)
}

const ARCADE = { plinth: P.socle, pier: P.dress, head: P.dress, stairs: P.dressStairs, key: P.key, frieze: P.band }

// --- Level I: the Sunny Plaza's hall, its arcade and loggia -------------------------------------------------------------
function levelOne(c) {
  const floorMix = mix(P.floor, 32)
  const H = HALL, L = LOGGIA
  // Pass 1: block-out and structure. Floor; sandstone walls on a smooth socle course, up to y 5; no ceiling: the hall
  // is open to the underside of its tile hip (its lamps hang on chains from it). The walls' own footing course (y 0)
  // is left out where the arcade's plinth and the floor hide it on both sides (all but under the door): nobody sees it
  c.fill(H.x0, 0, L.z0, H.x1, 0, H.z0 - 1, floorMix)
  c.fill(H.x0 + 1, 0, H.z0 + 1, H.x1 - 1, 0, H.z1 - 1, floorMix)
  c.set(OPENINGS.frontDoor[0], 0, H.z0, P.socle)
  for (let x = H.x0; x <= H.x1; x++) for (let z = H.z0; z <= H.z1; z++) {
    if (x !== H.x0 && x !== H.x1 && z !== H.z0 && z !== H.z1) continue
    c.set(x, 1, z, P.socle)
    // the top course only where it shows: on the front over the terrace (the sides and back have the arcade's frieze
    // standing against that course, so the hall shows its red back from inside, a band under the tiles)
    for (let y = 2; y <= H.top; y++) if (y < H.top || z === H.z0) c.set(x, y, z, P.wall)
  }
  // the hall's corners in cut sandstone up to the eave
  for (const [x, z] of [[H.x0, H.z0], [H.x1, H.z0], [H.x0, H.z1], [H.x1, H.z1]]) for (let y = 2; y <= H.top; y++) c.set(x, y, z, P.dress)
  // the arcade one block proud on the sides and back: piers, round heads over the window bays, the red frieze at y 5
  for (const [axis, at, a0, a1, piers] of ARCADES) tileArcade(c, { axis, at, a0, a1, piers, y0: 0, headY: 4 }, ARCADE)
  // the cornice: a ring of upside-down sandstone stairs on the roof's outer line over the frieze, its undercut outward
  const R = H.roof
  for (let x = R.x0; x <= R.x1; x++) for (let z = R.z0; z <= R.z1; z++) {
    if (x !== R.x0 && x !== R.x1 && z !== R.z0 && z !== R.z1) continue
    const inward = x === R.x0 ? 'east' : x === R.x1 ? 'west' : z === R.z0 ? 'south' : 'north'
    stair(c, x, H.cornice, z, P.dressStairs, inward, 'top')
  }
  // the roof: one low hip of tiles over the arcades' outer line, its eave a continuous slab course on the cornice
  const roofAt = tileHip(c, R, P.roof)

  // the loggia: piers on its front line, side walls, a ceiling at y 4 with the red frieze on its front
  const T = L.ceiling + 1 // the terrace's walking level
  for (const x of L.piers) { c.set(x, 0, L.z0, P.socle); for (let y = 1; y < L.ceiling; y++) c.set(x, y, L.z0, P.dress) }
  for (const x of [H.x0, H.x1]) for (let z = L.z0 + 1; z < H.z0; z++) { c.set(x, 1, z, P.socle); for (let y = 2; y < L.ceiling; y++) c.set(x, y, z, P.wall) }
  for (let x = H.x0; x <= H.x1; x++) {
    for (let z = L.z0; z < H.z0; z++) c.set(x, L.ceiling, z, P.ceiling)
    c.set(x, L.ceiling, L.z0, P.band)
  }
  for (const x of [H.x0, H.x1]) for (let z = L.z0 + 1; z < H.z0; z++) c.set(x, L.ceiling, z, P.band) // the frieze round the loggia's sides
  for (const [a, b] of L.bays) if (b - a === 2) archHead(c, a, b, L.z0, 3, P.dressStairs, P.key)
  // the apron, the street edge in front: steps up to the middle arch; a planter in soil either side of them (azaleas at
  // its ends, flowers between); a lamp on a post at each corner; a striped awning over each planter, sloping down
  // from the frieze (quartz and tile stairs in turn), shading it
  const A = APRON
  for (let x = A.steps[0]; x <= A.steps[1]; x++) stair(c, x, 0, A.z, P.dressStairs, 'south')
  for (const [a, b, plants] of A.planters) for (let x = a; x <= b; x++) {
    c.set(x, 0, A.z, P.soil, { snowy: 'false' })
    const plant = plants[x - a]
    if (plant === 'flowering_azalea') c.set(x, 1, A.z, plant); else plantFlower(c, x, 1, A.z, plant)
  }
  for (const x of A.lamps) lampPost(c, x, 0, A.z, `${P.wood}_fence`, 3)
  for (const [a, b] of A.awnings) for (let x = a; x <= b; x++) stair(c, x, L.ceiling, A.z, (x - a) % 2 ? P.roof.stairs : 'quartz_stairs', 'south')
  // the terrace over the loggia: a sandstone balustrade round its open edges, up to the cornice at the back
  railingRun(c, [[H.x0, T, H.z0 - 1], [H.x0, T, L.z0], [H.x1, T, L.z0], [H.x1, T, H.z0 - 1]], null, P.baluster)
  // the bell-cote over the middle arch, two deep: piers, a tall niche with a lantern hung in it, the red frieze round
  // its top, a cap, a tile gable
  for (let x = COTE.x0; x <= COTE.x1; x++) for (let z = COTE.z0; z <= COTE.z1; z++) {
    const side = x === COTE.x0 || x === COTE.x1 || z === COTE.z1 // the back closed: the roof's slope is behind it
    for (let y = COTE.y0; y <= COTE.top; y++) if (side || y === COTE.y0 || y === COTE.top) c.set(x, y, z, y === COTE.top ? P.band : P.dress)
    else c.clear(x, y, z)
    c.set(x, COTE.top + 1, z, P.dress)
    if (x === COTE.x0) stair(c, x, COTE.top + 2, z, P.roof.stairs, 'east')
    else if (x === COTE.x1) stair(c, x, COTE.top + 2, z, P.roof.stairs, 'west')
    else c.set(x, COTE.top + 2, z, P.roof.full)
  }
  c.set(6, COTE.top - 1, COTE.z0, 'lantern', { hanging: 'true', waterlogged: 'false' })
  c.set(6, COTE.top + 3, COTE.z0, 'lightning_rod', { facing: 'up', powered: 'false', waterlogged: 'false' })

  // Pass 2: openings. The front door under the middle arch, the windows (the front pair onto the loggia, an arched
  // light in every arcade bay), the loggia's sides
  door(c, OPENINGS.frontDoor, 'south', 'north', { wood: P.wood })
  for (const [x, z, out] of OPENINGS.windows) plazaWindow(c, x, z, out)
  for (const [x, z, out, pot] of [...OPENINGS.bays, OPENINGS.backWindow]) bayWindow(c, x, z, out, pot)
  for (const [x, z] of OPENINGS.loggiaSides) { c.set(x, 2, z, P.glass); c.set(x, 3, z, P.glass) }

  // Pass 3: depth: lanterns hung under the loggia ceiling either side of the door; stone benches in the side bays
  for (const x of [3, 9]) c.set(x, L.ceiling - 1, 2, 'lantern', { hanging: 'true', waterlogged: 'false' })
  for (const [x, f] of [[2, 'east'], [10, 'west']]) { stair(c, x, 1, 2, P.dressStairs, OPP[f]); c.spot('bench', [x, 1, 2], f, [[0, 0, 0]], P.wood) }

  // Pass 4: interior
  // the hall's lamps hang on chains from the roof cell over them
  furnish(c, FURNITURE.filter(f => f.level === 1).map(f => (f.part === 'hangingLamp' ? { ...f, chain: roofAt(f.pos[0], f.pos[2]) - f.pos[1] - 1 } : f)))
  healingMachine(c, FIXED.machine, 'north')
  pcTower(c, FIXED.pc, 'west')
}

// --- Level II: the court and the market hall ------------------------------------------------------------------------
function levelTwo(c) {
  const pave = mix(P.paving, 34)
  const empty = (x, y, z) => !c.get(x, y, z)
  // Pass 1: the court's paving (at the hall's floor level), its low wall with posts at the turns
  for (let x = 0; x <= 19; x++) for (let z = 12; z <= 25; z++) if (empty(x, 0, z)) c.set(x, 0, z, pave)
  railingRun(c, [[0, 1, 13], [0, 1, 25], [18, 1, 25]], null, P.baluster)
  for (const x of [7, 8, 9, 10]) c.clear(x, 1, 25) // the gate to the fields
  // the market hall: a back wall at x 18 (z 12..24), a glazed arcade at x 14 on the court, end walls, a ceiling at y 5
  // with the red frieze on the court front; an open arcade at x 19 on the back lane, like the hall's
  const A = { x0: 14, x1: 18, z0: 12, z1: 24 }
  for (let z = A.z0; z <= A.z1; z++) {
    c.set(A.x1, 0, z, P.socle); c.set(A.x1, 1, z, P.socle)
    for (let y = 2; y <= 5; y++) c.set(A.x1, y, z, P.wall)
    for (let x = A.x0; x < A.x1; x++) c.set(x, 5, z, x === A.x0 ? P.band : P.ceiling)
  }
  for (const z of [A.z0, A.z1]) for (let x = A.x0 + 1; x < A.x1; x++) { c.set(x, 0, z, P.socle); c.set(x, 1, z, P.socle); for (let y = 2; y <= 4; y++) c.set(x, y, z, P.wall) }
  for (const z of [A.z0, A.z1]) for (let y = 2; y <= 5; y++) c.set(A.x1, y, z, P.dress) // the back corners
  for (const z of [A.z0, 15, 21, A.z1]) { c.set(A.x0, 0, z, P.socle); for (let y = 1; y <= 4; y++) c.set(A.x0, y, z, P.dress) }
  // the court front's bays: two-wide arched windows either side, the wide middle bay with the door, glass either side
  // of it, a lintel over its rounded shoulders and the keystone above
  for (const [a, b] of [[13, 14], [16, 20], [22, 23]]) {
    for (let z = a; z <= b; z++) { c.set(A.x0, 0, z, P.socle); c.set(A.x0, 4, z, P.dress) }
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
  // the back lane's arcade at x 19: bays in line with the back wall's windows
  tileArcade(c, { axis: 'z', at: A.x1 + 1, a0: A.z0, a1: A.z1, piers: [12, 16, 20, 24], y0: 0, headY: 4 }, ARCADE)
  for (const [z, pot] of [[14, 'potted_red_tulip'], [18, 'potted_flowering_azalea_bush'], [22, 'potted_poppy']]) bayWindow(c, A.x1, z, 'east', pot)
  // a 45-degree gable of tiles over it all, ridge along z (y 9), eaves on the court front and on the lane arcade, the
  // gable ends in sandstone with a little window each
  const rf = gableRoof(c, { axis: 'z', across: [A.x0 - 1, A.x1 + 1], along: [A.z0 - 1, A.z1 + 1], eaveY: 6, steep: false }, P.roof)
  for (const line of [A.z0, A.z1]) gableWall(c, 'z', line, [A.x0, A.x1], 5, rf.under, (u, y) => (u === 16 && y === 7 ? P.glass : y === 5 ? (u === A.x0 ? P.band : P.dress) : P.wall))
  // red cloths hung on the two piers either side of the door (the market's sign)
  for (const z of [15, 21]) c.set(A.x0 - 1, 4, z, 'red_wall_banner', { facing: 'west' })

  // Pass 2: the back door from the hall into the court, through the middle bay of the hall's back arcade
  const [bx, bz] = OPENINGS.backWindow
  c.clear(bx, 1, bz + 1); c.clear(bx, 2, bz + 1) // the breast's middle and its pot
  door(c, [bx, 1, bz], 'north', 'south', { wood: P.wood })
  c.set(bx, 3, bz, P.glass)

  // Pass 4: the market's stall and the court's furnishings
  furnish(c, FURNITURE.filter(f => f.level === 2))
  shopCounter(c, FIXED.shopCounter, 'west')
  // the fountain: a sandstone curb round a 3 x 3 pool on the paving, a column with a basin slab in the middle
  const [fx, fz] = COURT.fountain
  const water = []
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) if (dx || dz) water.push([fx + dx, fz + dz])
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) c.set(fx + dx, 0, fz + dz, P.dress)
  roundCurb(c, fx, fz, 2, 1, P.dress, [...water, [fx, fz]])
  c.set(fx, 1, fz, P.dress); c.set(fx, 2, fz, P.dress); c.set(fx, 3, fz, P.dressSlab, { type: 'bottom', waterlogged: 'false' })
  pool(c, water, 1)
  // round it: a bench of three seats on each side, facing the water; a soil bed at each diagonal (an azalea at the
  // outer corner, flowers in the rest), sunk in the paving
  for (const [a0, a1, line, f] of COURT.benches) for (let u = a0; u <= a1; u++) {
    const [x, z] = f === 'south' || f === 'north' ? [u, line] : [line, u]
    stair(c, x, 1, z, `${P.wood}_stairs`, OPP[f])
    c.spot('bench', [x, 1, z], f, [[0, 0, 0]], P.wood)
  }
  const flowers = ['poppy', 'allium', 'cornflower', 'oxeye_daisy']
  COURT.beds.forEach(([x0, z0], i) => {
    const outer = [x0 + (x0 < fx ? 0 : 1), z0 + (z0 < fz ? 0 : 1)]
    for (const [dx, dz] of [[0, 0], [1, 0], [0, 1], [1, 1]]) {
      const x = x0 + dx, z = z0 + dz
      c.set(x, 0, z, P.soil, { snowy: 'false' })
      if (x === outer[0] && z === outer[1]) c.set(x, 1, z, 'flowering_azalea')
      else if (!c.get(x, 1, z)) plantFlower(c, x, 1, z, flowers[(i + dx + 2 * dz) % flowers.length])
    }
  })
  // the lawn bed for the Pasture Block: grass in a sandstone kerb, the block in the middle, flowers in its corners
  const Lw = COURT.lawn
  for (let x = Lw.x0; x <= Lw.x1; x++) for (let z = Lw.z0; z <= Lw.z1; z++) {
    const edge = x === Lw.x0 || x === Lw.x1 || z === Lw.z0 || z === Lw.z1
    c.set(x, 0, z, edge ? P.dress : P.soil, edge ? {} : { snowy: 'false' })
  }
  pastureBlock(c, FIXED.pasture, 'north')
  for (const [x, z, f] of [[2, 21, 'poppy'], [4, 23, 'dandelion'], [4, 21, 'cornflower']]) plantFlower(c, x, 1, z, f)
  // lamps at the court's corners, azaleas in pots by the hall's back door
  for (const [x, z] of [[1, 14], [1, 24], [13, 24]]) lampPost(c, x, 1, z, `${P.wood}_fence`, 2)
  for (const [x, z] of [[4, 13], [8, 13]]) c.set(x, 1, z, 'potted_flowering_azalea_bush')
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
