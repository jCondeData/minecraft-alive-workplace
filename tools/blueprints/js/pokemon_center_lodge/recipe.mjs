// The Pokémon Center's Mountain Lodge look, the recipe: assembles plan.mjs from the shared parts library (../parts) in
// passes (block-out and structure, openings, depth and detail, interior, ground). LEVEL=1|2 (default 1): II draws I
// first and grows out of it (the Trader's wing carrying on I's cross gable, the paddock), keeping I's cells but for the
// few it opens (the arch) or walls in (the shutters on the wing's side). Writes furniture.json (the level's spots).
// Build: sh build.sh; CUT=<y> builds a cutaway above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE as P, HALL, CROSS, WING, DORMER, FIXED, OPENINGS, FURNITURE, mainEast, mainWest } from './plan.mjs'
import {
  Canvas, mix, stair, slab, window, door, gableRoof, gableWall, bargeTrim, chimney, hearthFire, furnitureParts,
  railingRun, horseTrough, brackets, healingMachine, pcTower, pastureBlock, shopCounter, deskRun, pitchDormer, woodpile,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const ST = (facing, half = 'bottom') => ({ facing, half, shape: 'straight', waterlogged: 'false' })

function furnish(c, list) {
  for (const f of list) {
    if (f.part === 'desk' && f.cells) deskRun(c, f.cells, f.facing, { base: `${f.wood}_planks`, top: 'smooth_stone_slab', wood: f.wood })
    else furnitureParts[f.part](c, f)
  }
}

/** a log house's walls round x0..x1, z0..z1: a stone socle to `socle`, laid logs above it to `top`, upright corner
 *  logs, a wall plate ring at `plate`; `skip(x, z)` leaves a stretch to a neighbour (a shared wall) */
function logWalls(c, { x0, x1, z0, z1 }, socle, top, plate, stone, skip = () => false) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const edge = x === x0 || x === x1 || z === z0 || z === z1
    if (!edge || skip(x, z)) continue
    const corner = (x === x0 || x === x1) && (z === z0 || z === z1)
    for (let y = 0; y <= socle; y++) c.set(x, y, z, stone)
    for (let y = socle + 1; y <= top; y++) c.set(x, y, z, P.log, { axis: corner ? 'y' : z === z0 || z === z1 ? 'x' : 'z' })
    c.set(x, plate, z, P.log, { axis: corner ? 'y' : z === z0 || z === z1 ? 'x' : 'z' })
  }
}

/** the window kinds (note 5): red shutters, a window box (a sill with potted plants), or bare glass */
const dressed = (c, [cells, out, dress, pots = []]) =>
  window(c, cells, out, dress, dress === 'shutters' ? { glass: P.glass, wood: P.shutter } : { glass: P.glass, wood: P.wood, pots })

/** the cross roof's height over row z (ridge along x at CROSS.cz) */
const crossY = z => CROSS.eaveY + CROSS.half - Math.abs(z - CROSS.cz)

/** the cross gable's roof over columns x0..x1: a stair (or the ridge's full block) on each cell where it stands above
 *  the main slope, taking the main roof's cell under it away so the hall rises into it; where the two meet at one
 *  height the main roof's stair stays and the valley runs down them */
function crossRoof(c, x0, x1, main) {
  for (let x = x0; x <= x1; x++) for (let z = CROSS.cz - CROSS.half; z <= CROSS.cz + CROSS.half; z++) {
    const y = crossY(z), m = main(x)
    if (m !== null && y <= m) continue
    if (m !== null) c.clear(x, m, z)
    if (z === CROSS.cz) c.set(x, y, z, P.roof.full)
    else stair(c, x, y, z, P.roof.stairs, z < CROSS.cz ? 'south' : 'north')
  }
}

/** a jettied, timbered gable face on line x (the cross gable's in I, the wing's end in II), standing one block out
 *  from the wall below on a sill beam (y 5) and three brackets, flush under its roof like the front gable: white
 *  infill, a two-high window at the ridge's row with a frame post either side of its foot, the ridge beam's end on top */
function crossFace(c, x) {
  const { cz, eaveY } = CROSS
  for (let z = cz - CROSS.half + 1; z <= cz + CROSS.half - 1; z++) for (let y = eaveY; y < crossY(z); y++) {
    if (y === eaveY) c.set(x, y, z, P.log, { axis: 'z' }) // the sill beam
    else if (z === cz && y <= eaveY + 2) c.set(x, y, z, P.glass)
    else if (z === cz) c.set(x, y, z, P.log, { axis: 'x' }) // the ridge beam's end
    else if (Math.abs(z - cz) === 1 && y === eaveY + 1) c.set(x, y, z, P.frame, { axis: 'y' })
    else c.set(x, y, z, P.infill)
  }
  brackets(c, [-3, 0, 3].map(d => [x, eaveY - 1, cz + d]), `${P.wood}_stairs`, 'west')
}

// --- Level I: the Mountain Lodge ------------------------------------------------------------------------------------
function levelOne(c) {
  const stone = mix(P.stone, 21)
  const H = HALL
  // Pass 1: block-out and structure. The floor, the socle and log walls, the wall plate, tie beams, the roof, gables
  c.fill(H.x0 + 1, 0, H.z0 + 1, H.x1 - 1, 0, H.z1 - 1, P.floor)
  logWalls(c, H, 1, 4, H.plate, stone)
  for (const z of H.ties) for (let x = H.x0 + 1; x < H.x1; x++) c.set(x, H.plate, z, P.log, { axis: 'x' })
  const rf = gableRoof(c, H.roof, P.roof)
  const gable = (line, window) => gableWall(c, 'z', line, [H.x0, H.x1], H.plate + 1, rf.under, (u, y) => {
    if (y === H.plate + 1) return [P.frame, { axis: 'x' }] // the collar
    if (window(u, y)) return P.glass
    if (u === 6 && y >= 9) return [P.frame, { axis: 'y' }] // king post
    if ((u === 3 || u === 9) && y <= 8) return [P.frame, { axis: 'y' }]
    return P.infill
  })
  gable(0, (u, y) => u >= 5 && u <= 7 && (y === 7 || y === 8))
  gable(H.z1, (u, y) => (u === 6 && (y === 7 || y === 8)) || ((u === 5 || u === 7) && y === 7))
  // a ridge beam the length of the roof (the hall's ceiling is its timbers, no lining: Level I stays lean), and the
  // purlins' ends through both gables (note 4's beam ends)
  for (let z = 0; z <= 12; z++) c.set(6, rf.ridgeY - 1, z, P.log, { axis: 'z' })
  for (const z of [0, H.z1, 12]) for (const x of H.purlins) c.set(x, rf.under[x] - 1, z, P.log, { axis: 'z' })
  bargeTrim(c, 'z', 12, H.roof.across, rf.under, 'spruce_stairs')

  // the cross gable (note 1): its roof from the eave line (x 12) back into the main slope, its face jettied out over
  // the east wall on the eave line (the main eave and the slope's cells under the cross roof give way; the hall rises
  // into it)
  crossRoof(c, CROSS.from, CROSS.to, mainEast)
  crossFace(c, CROSS.to)

  // the dormer (note 1) on the west slope over the desk
  pitchDormer(c, { face: DORMER.face, zc: DORMER.zc, dir: -1, main: mainWest }, { roof: P.roof, frame: P.frame, infill: P.infill, glass: P.glass })
  // its little roof stops flush with its face: an overhang there leaves slots over the main slope that the kit seals
  // with a block of frame either side, hiding the window
  for (let dz = -2; dz <= 2; dz++) c.clear(DORMER.face - 1, mainWest(DORMER.face) + 3 - Math.abs(dz), DORMER.zc + dz)

  // the porch: steps at z 0, a deck at z 1, posts at the corners carrying a beam and a boarded ceiling at y 5,
  // brackets under the beam, a rail from each post to the wall
  for (let x = H.x0 + 1; x < H.x1; x++) { stair(c, x, 0, 0, `${P.wood}_stairs`, 'south'); c.set(x, 0, 1, P.floor) }
  for (const x of [H.x0, H.x1]) { c.set(x, 0, 0, stone); c.set(x, 0, 1, stone); for (let y = 1; y <= 4; y++) c.set(x, y, 0, P.log, { axis: 'y' }) }
  for (let x = H.x0; x <= H.x1; x++) { c.set(x, H.plate, 0, P.log, { axis: 'x' }); c.set(x, H.plate, 1, P.lining) }
  brackets(c, [[H.x0 + 1, 4, 0]], `${P.wood}_stairs`, 'west')
  brackets(c, [[H.x1 - 1, 4, 0]], `${P.wood}_stairs`, 'east')
  railingRun(c, [[H.x0, 1, 1], [H.x0, 1, 1]], P.wood)
  railingRun(c, [[H.x1, 1, 1], [H.x1, 1, 1]], P.wood)
  for (const x of [4, 8]) c.set(x, 4, 1, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // Pass 2: openings. The front door off the porch deck, the Nurse's back door, the windows (three kinds)
  door(c, OPENINGS.frontDoor, 'south', 'north', { wood: P.wood })
  const bd = OPENINGS.backDoor
  door(c, bd.at, 'north', 'south', { wood: P.wood, step: bd.step, stepStair: `${P.wood}_stairs` })
  for (const w of [...OPENINGS.windows, OPENINGS.eastWindowI]) dressed(c, w)

  // Pass 3: depth. The hearth: a stone breast in the west wall round the fire, the stack outside at x 0 past the ridge,
  // the winter's wood under the west eave
  const [hx, , hz] = OPENINGS.hearth
  for (let z = hz - 1; z <= hz + 1; z++) for (let y = 1; y <= 4; y++) c.set(hx, y, z, stone)
  hearthFire(c, OPENINGS.hearth, 'east')
  chimney(c, 0, hz, 0, rf.ridgeY + 1, stone, { breast: { axis: 'z', top: 3, stair: 'cobblestone_stairs' } })
  woodpile(c, 0, 9, 11, P.log)
  // the back porch (note 4): a pent over the back door on two log posts, brackets, a slab deck either side of the step
  const bz = bd.at[2] + 1
  for (const x of [4, 8]) for (let y = 0; y <= 3; y++) c.set(x, y, bz, P.log, { axis: 'y' })
  for (let x = 3; x <= 9; x++) stair(c, x, 4, bz, P.roof.stairs, 'north')
  brackets(c, [[5, 3, bz]], `${P.wood}_stairs`, 'west')
  brackets(c, [[7, 3, bz]], `${P.wood}_stairs`, 'east')
  slab(c, 5, 0, bz, `${P.wood}_slab`, 'bottom'); slab(c, 7, 0, bz, `${P.wood}_slab`, 'bottom')

  // Pass 4: interior. The plan's furniture (lamps after the beams they hang from), the machine and the PC
  furnish(c, FURNITURE.filter(f => f.level === 1))
  healingMachine(c, FIXED.machine, 'north')
  pcTower(c, FIXED.pc, 'west')
  return rf
}

// --- Level II: the Trader's wing and the paddock ----------------------------------------------------------------------
function levelTwo(c) {
  const stone = mix(P.stone, 22)
  const W = WING
  // Pass 1: the wing's floor, socle, log walls (the hall's east wall is its west wall), a tie beam, and the cross
  // gable's roof carried on east to the wing's own gable end at x 19 (note 2)
  c.fill(W.x0, 0, W.z0 + 1, W.x1 - 1, 0, W.z1 - 1, P.floor)
  logWalls(c, { x0: W.x0 - 1, x1: W.x1, z0: W.z0, z1: W.z1 }, 1, 4, W.plate, stone, (x) => x === W.x0 - 1)
  for (const x of W.ties) for (let z = W.z0 + 1; z < W.z1; z++) c.set(x, W.plate, z, P.log, { axis: 'z' })
  crossRoof(c, CROSS.to + 1, W.overhang, () => null)
  crossFace(c, W.overhang) // jettied like I's: the same face, one block out over the wing's end wall
  // I's cross-gable face stays, the window between the hall and the wing; the ridge beam carries on to the wing's end
  for (let x = CROSS.to + 1; x < W.overhang; x++) c.set(x, crossY(CROSS.cz) - 1, CROSS.cz, P.log, { axis: 'x' })

  // Pass 2: openings: the arch from the hall, the wing's shop door and back door, its windows; I's east window by the
  // arch loses its shutters (one is the wing's wall now)
  const [ex, ez] = OPENINGS.arch
  for (const dz of [-1, 1]) for (const y of [2, 3]) if (c.name(ex + 1, y, ez + dz).includes('trapdoor')) c.clear(ex + 1, y, ez + dz)
  c.clear(ex, 1, ez); c.clear(ex, 2, ez); c.set(ex, 0, ez, P.floor)
  door(c, OPENINGS.wingDoor.at, 'south', 'north', { wood: P.wood, step: OPENINGS.wingDoor.step, stepStair: 'cobblestone_stairs' })
  door(c, OPENINGS.wingBackDoor.at, 'north', 'south', { wood: P.wood, step: OPENINGS.wingBackDoor.step, stepStair: 'cobblestone_stairs' })
  for (const w of OPENINGS.wingWindows) dressed(c, w)

  // Pass 4: interior
  furnish(c, FURNITURE.filter(f => f.level === 2))
  shopCounter(c, FIXED.shopCounter, 'north')

  // Pass 5: the paddock (note 3): a fence round it with an open gap on each path, a lamp post by each gap, stepping
  // stones from both back doors to the Pasture Block on its stone pad, a lean-to field shelter on the back fence with
  // hay in it, a log trough
  const [px, , pz] = FIXED.pasture
  railingRun(c, [[1, 0, 13], [1, 0, 25], [19, 0, 25], [19, 0, 13], [1, 0, 13]], P.wood)
  const gaps = [6, 15]
  for (const gx of gaps) c.clear(gx, 0, 13)
  for (const lx of [7, 16]) { c.set(lx, 1, 13, P.fence); c.set(lx, 2, 13, 'lantern', { hanging: 'false', waterlogged: 'false' }) }
  const stones = []
  for (let z = 12; z <= pz; z++) stones.push([6, z], [15, z]) // from both back steps (drawn already) through the gaps
  for (let x = 7; x <= px - 2; x++) stones.push([x, pz])
  for (let x = px + 2; x <= 14; x++) stones.push([x, pz])
  stones.forEach(([x, z], i) => { if (!c.get(x, 0, z)) slab(c, x, 0, z, P.path[(x * 7 + z * 3) % 2], 'bottom') })
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) c.set(px + dx, 0, pz + dz, stone)
  pastureBlock(c, FIXED.pasture, 'north')
  // the field shelter: x 2..8, open to the north, its back wall on the fence line, a lean-to roof falling to the front
  const S = { x0: 2, x1: 8, front: 23, back: 25 }
  for (let x = S.x0; x <= S.x1; x++) for (let y = 0; y <= 3; y++) c.set(x, y, S.back, P.lining)
  c.set(5, 0, S.back, P.log, { axis: 'y' }); c.set(5, 3, S.back, P.log, { axis: 'x' }) // a mid post, a rail over the hay window
  c.set(5, 1, S.back, P.fence); c.set(5, 2, S.back, P.fence)
  for (const x of [3, 4, 6, 7]) c.set(x, 3, S.back, P.log, { axis: 'x' })
  for (const x of [S.x0, S.x1]) {
    for (let y = 0; y <= 3; y++) c.set(x, y, S.back, P.log, { axis: 'y' })
    for (let y = 0; y <= 2; y++) c.set(x, y, S.back - 1, P.lining)
    for (let y = 0; y <= 1; y++) c.set(x, y, S.front, P.log, { axis: 'y' })
  }
  for (let x = S.x0 - 1; x <= S.x1 + 1; x++) {
    stair(c, x, 4, S.back, P.roof.stairs, 'south')
    stair(c, x, 3, S.back - 1, P.roof.stairs, 'south')
    stair(c, x, 2, S.front, P.roof.stairs, 'south')
  }
  for (const [x, y, z] of [[3, 0, 24], [4, 0, 24], [3, 1, 24], [7, 0, 24], [6, 0, 24]]) c.set(x, y, z, 'hay_block', { axis: 'y' })
  horseTrough(c, [[13, 22], [14, 22], [15, 22]], 0, P.log)
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
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `lodge_${id}_cut${cut}` : id } }
}
