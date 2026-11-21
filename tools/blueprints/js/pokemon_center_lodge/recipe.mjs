// The Pokémon Center's Mountain Lodge look, the recipe: assembles plan.mjs from the shared parts library (../parts) in
// passes (block-out and structure, openings, depth and detail, interior, ground). LEVEL=1|2 (default 1): II draws I
// first and grows out of it (the Trader's wing, the paddock), keeping I's cells but for the few it opens (the arch, the
// back door). Writes furniture.json (the level's spots). Build: sh build.sh; CUT=<y> builds a cutaway above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE as P, HALL, WING, FIXED, OPENINGS, FURNITURE } from './plan.mjs'
import {
  Canvas, mix, stair, slab, trapdoor, wallPanel, window, door, gableRoof, gableWall, bargeTrim, chimney, hearthFire,
  furnitureParts, railingRun, horseTrough, brackets, healingMachine, pcTower, pastureBlock, shopCounter, deskRun,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

function furnish(c, list) {
  for (const f of list) {
    if (f.part === 'desk') deskRun(c, f.cells, f.facing, { base: `${f.wood}_planks`, top: 'smooth_stone_slab', wood: f.wood })
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

/** a two-high window with open red shutters either side (the lodge's one window, on every face) */
const shuttered = (c, [x, y0, z, out]) => window(c, [[x, y0, z], [x, y0 + 1, z]], out, 'shutters', { glass: P.glass, wood: P.shutter })

// --- Level I: the Mountain Lodge ------------------------------------------------------------------------------------
function levelOne(c) {
  const stone = mix(P.stone, 21)
  const H = HALL
  // Pass 1: block-out and structure. The floor, the socle and log walls, the wall plate, tie beams, the roof, gables
  c.fill(H.x0 + 1, 0, H.z0 + 1, H.x1 - 1, 0, H.z1 - 1, P.floor)
  logWalls(c, H, 1, 4, H.plate, stone)
  for (const z of H.ties) for (let x = H.x0 + 1; x < H.x1; x++) c.set(x, H.plate, z, P.log, { axis: 'x' })
  const rf = gableRoof(c, H.roof, P.roof)
  // the hall's ceiling: boards under the slope (the stair's lower block), so the hall reads warm under a red roof
  for (let x = H.x0 + 1; x < H.x1; x++) for (let z = 0; z <= 12; z++) {
    const y = rf.under[x] - 1
    if (y > H.plate && !c.get(x, y, z)) c.set(x, y, z, P.lining)
  }
  const gable = (line, window) => gableWall(c, 'z', line, [H.x0, H.x1], H.plate + 1, rf.under, (u, y) => {
    if (y === H.plate + 1) return [P.frame, { axis: 'x' }] // the collar
    if (window(u, y)) return P.glass
    if (u === 6 && y >= 9) return [P.frame, { axis: 'y' }] // king post
    if ((u === 3 || u === 9) && y <= 8) return [P.frame, { axis: 'y' }]
    return P.infill
  })
  gable(0, (u, y) => u >= 5 && u <= 7 && (y === 7 || y === 8))
  gable(H.z1, (u, y) => u === 6 && (y === 7 || y === 8))
  for (const line of [0, 12]) bargeTrim(c, 'z', line, H.roof.across, rf.under, 'spruce_stairs')

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

  // Pass 2: openings. The front door off the porch deck, the shuttered windows
  door(c, OPENINGS.frontDoor, 'south', 'north', { wood: P.wood })
  for (const w of [...OPENINGS.windows, OPENINGS.eastWindowI, OPENINGS.backWindow]) shuttered(c, w)

  // Pass 3: depth. The hearth: a stone breast in the west wall round the fire, the stack outside at x 0 past the ridge
  const [hx, , hz] = OPENINGS.hearth
  for (let z = hz - 1; z <= hz + 1; z++) for (let y = 1; y <= 4; y++) c.set(hx, y, z, stone)
  hearthFire(c, OPENINGS.hearth, 'east')
  chimney(c, 0, hz, 0, rf.ridgeY + 1, stone, { breast: { axis: 'z', top: 3, stair: 'cobblestone_stairs' } })

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
  const empty = (x, y, z) => !c.get(x, y, z)
  // Pass 1: the wing's floor, socle, log walls (the hall's east wall is its west wall), a tie beam, its lower gable roof
  c.fill(W.x0 + 1, 0, W.z0 + 1, W.x1 - 1, 0, W.z1 - 1, P.floor)
  logWalls(c, W, 1, 3, W.plate, stone, (x) => x === W.x0)
  for (let x = W.x0 + 1; x < W.x1; x++) c.set(x, W.plate, 5, P.log, { axis: 'x' })
  const rf = gableRoof(c, W.roof, P.roof, empty)
  for (let z = W.z0 + 1; z < W.z1; z++) for (let x = W.x0 + 1; x <= W.x1; x++) {
    const y = rf.under[z] - 1
    if (y > W.plate && empty(x, y, z)) c.set(x, y, z, P.lining)
  }
  // where the wing's roof runs into the hall's: its west end closed up to the roof (a hidden wall over the hall's eave)
  for (let z = W.z0; z <= W.z1; z++) for (let y = W.plate + 1; y < rf.under[z]; y++) if (empty(W.x0 + 1, y, z)) c.set(W.x0 + 1, y, z, P.lining)
  gableWall(c, 'x', W.x1, [W.z0, W.z1], W.plate + 1, rf.under, (u, y) => {
    if (y === W.plate + 1) return [P.frame, { axis: 'z' }]
    if (u === 6 && y === 7) return P.glass
    if (u === 6) return [P.frame, { axis: 'y' }]
    return P.infill
  })
  bargeTrim(c, 'x', W.roof.along[1], W.roof.across, rf.under, 'spruce_stairs')

  // Pass 2: openings: the arch from the hall, the wing's door and windows, the hall's back door to the paddock
  const [ax, az] = OPENINGS.arch
  c.clear(ax, 1, az); c.clear(ax, 2, az); c.set(ax, 0, az, P.floor)
  door(c, OPENINGS.wingDoor.at, 'south', 'north', { wood: P.wood, step: OPENINGS.wingDoor.step, stepStair: 'cobblestone_stairs' })
  for (const w of OPENINGS.wingWindows) shuttered(c, w)
  // the east window of I is now inside the wing: its shutters go, its panes stay (a window between the rooms)
  const [ex, ey, ez] = OPENINGS.eastWindowI
  for (const dz of [-1, 1]) for (const y of [ey, ey + 1]) if (c.name(ex + 1, y, ez + dz).includes('trapdoor')) c.clear(ex + 1, y, ez + dz)
  const [bx, , bz] = OPENINGS.backWindow
  for (const dx of [-1, 1]) for (const y of [2, 3]) c.clear(bx + dx, y, bz + 1)
  door(c, [bx, 1, bz], 'north', 'south', { wood: P.wood, step: [bx, 0, bz + 1], stepStair: 'cobblestone_stairs' })
  c.set(bx, 3, bz, P.glass)

  // Pass 4: interior
  furnish(c, FURNITURE.filter(f => f.level === 2))
  shopCounter(c, FIXED.shopCounter, 'north')

  // Pass 5: the paddock: a fence round it with a gap on the path from the back door, the Pasture Block on a stone pad,
  // a log trough of water, a stack of hay, a lamp on a post at the gate
  const [px, , pz] = FIXED.pasture
  railingRun(c, [[1, 0, 13], [1, 0, 24], [19, 0, 24], [19, 0, 13], [1, 0, 13]], P.wood)
  c.clear(6, 0, 13); c.clear(7, 0, 13) // the way in: an open gap (villagers can't work a gate), the lamp post beside it
  for (let z = 12; z <= 12; z++) slab(c, 6, 0, z, 'cobblestone_slab')
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) c.set(px + dx, 0, pz + dz, stone)
  pastureBlock(c, FIXED.pasture, 'north')
  horseTrough(c, [[14, 21], [15, 21], [16, 21]], 0, P.log)
  for (const [x, y, z] of [[3, 0, 22], [4, 0, 22], [3, 0, 21], [3, 1, 22]]) c.set(x, y, z, 'hay_block', { axis: 'y' })
  c.set(5, 1, 13, P.fence); c.set(5, 2, 13, 'lantern', { hanging: 'false', waterlogged: 'false' })
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
