// Watermills: an overshot wheel fed by a launder off a raised mill pond, the millstone on its hurst, and the lucam (the
// sack-hoist hood that juts from a mill's gable) (parts library, tools/blueprints/REBUILD.md). Grown from the Watermill
// (49, Create build). Create's ids and states are checked in libs/create-fabric-6.0.10.0+mc1.21.1.jar: a water wheel's
// `facing` is its axle's direction; a millstone takes its drive from below only. Every other block comes from the
// family's palette (ids or mix() functions), never from here.
import { DIRS, OPP, slab, stair, trapdoor } from './canvas.mjs'

const CREATE = id => `create:${id}`
const add = ([x, y, z], [dx, dy, dz], k = 1) => [x + dx * k, y + dy * k, z + dz * k]
const vec = dir => [DIRS[dir][0], 0, DIRS[dir][1]]
const UP = [0, 1, 0]
const DOWN = [0, -1, 0]

/** raisedPond: a mill pond held up on a stone terrace: still water (sources, every one poured from a bucket) over the
 *  box x0..x1, z0..z1 at `waterY`, its bed solid under it down to y = 0 (`fill`), a retaining wall (`wall`) round it from
 *  y = 0 to waterY, and a curb course (`curb`) one above the water. Cells of the ring listed in `open` ([x, z]) are left
 *  to the drawing (the outlet into a launder, a wall another mass already owns). Returns the water cells. */
export function raisedPond(c, { x0, x1, z0, z1, waterY, open = [] }, { wall, curb, fill }) {
  const skip = new Set(open.map(([x, z]) => `${x},${z}`))
  c.fill(x0, 0, z0, x1, waterY - 1, z1, fill)
  for (let x = x0 - 1; x <= x1 + 1; x++) for (let z = z0 - 1; z <= z1 + 1; z++) {
    const ring = x < x0 || x > x1 || z < z0 || z > z1
    if (!ring || skip.has(`${x},${z}`)) continue
    for (let y = 0; y <= waterY; y++) c.set(x, y, z, wall)
    c.set(x, waterY + 1, z, curb)
  }
  const cells = []
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) { c.set(x, waterY, z, 'water', { level: '0' }); cells.push([x, waterY, z]) }
  return cells
}

/** overshotWheel: a small Create water wheel at `wheel` (its axle along `axle`, 'east' or 'south': the side its drive
 *  shaft leaves from) fed over the top by a launder. The launder's water runs `run` cells toward `flow` (a horizontal
 *  direction in the wheel's plane) at the wheel's top, from its head (the cell beside the pond's source, at
 *  `headLevel`) over the wheel, to a lip one past it, then falls past the wheel's face into a closed two-cell sump
 *  under it. Over the top, down its face and back under it: all three wet cells turn the wheel the same way. Only the
 *  pond's sources are poured; every cell here is water as it settles and comes by itself. With Create gone the wheel's
 *  cell is air, the top water falls straight through it into the same sump, and nothing spreads (falling water never
 *  spreads sideways over water). Walls are set only where the drawing has nothing yet (draw the house first):
 *  `pit(x, y, z)` (an id or [id, props]) for the stone round the fall and the sump, `side` the launder's side walls, `bed` its bottom. The
 *  wheel's outer axle face (the cell past it, away from the shaft) is left open to show the wheel. Returns the cells. */
export function overshotWheel(c, { wheel, axle, flow, run, headLevel = 1 }, { pit, side, bed }) {
  const d = vec(flow), a = vec(axle), na = vec(OPP[axle])
  const put = (p, v) => { if (c.get(...p)) return; const [id, props] = Array.isArray(v) ? v : [v, {}]; c.set(...p, id, props) }
  const top = add(wheel, UP)
  const lip = add(top, d)
  const fall = add(wheel, d)
  const sumps = [add(fall, DOWN), add(wheel, DOWN)]
  const launder = Array.from({ length: run }, (_, i) => add(top, d, -(run - i))) // head first
  // water as it settles: the launder weakening a level a cell, the top, the lip, the fall, the sump
  launder.forEach((p, i) => c.set(...p, 'water', { level: String(headLevel + i) }))
  c.set(...top, 'water', { level: String(headLevel + run) })
  c.set(...lip, 'water', { level: String(Math.min(7, headLevel + run + 1)) })
  c.set(...fall, 'water', { level: '8' })
  c.set(...sumps[0], 'water', { level: '8' })
  c.set(...sumps[1], 'water', { level: '1' })
  c.set(...wheel, CREATE('water_wheel'), { facing: axle })
  // the launder: sides at the water's height, a bed under each cell (the wheel is the bed under the top)
  for (const p of [...launder, top, lip]) { put(add(p, a), side(...add(p, a))); put(add(p, na), side(...add(p, na))) }
  for (const p of launder) put(add(p, DOWN), bed(...add(p, DOWN)))
  put(add(lip, d), pit(...add(lip, d)))
  // the fall: solid on its three open faces (Create reads a fall only beside a solid face)
  for (const p of [add(fall, a), add(fall, na), add(fall, d)]) put(p, pit(...p))
  // the sump: shut in on every side and below
  for (const s of sumps) { for (const p of [add(s, a), add(s, na), add(s, DOWN)]) put(p, pit(...p)) }
  put(add(sumps[0], d), pit(...add(sumps[0], d)))
  put(add(sumps[1], d, -1), pit(...add(sumps[1], d, -1)))
  return { top, lip, fall, sumps, launder }
}

/** driveToMillstone: the wheel's axle carried straight along `axle` through `shafts` cells (Create shafts) to a gearbox
 *  turning it up into a millstone over it (the gearbox's own axis is the horizontal one across the drive). */
export function driveToMillstone(c, shafts, gearboxAt, axle) {
  const axis = axle === 'east' || axle === 'west' ? 'x' : 'z'
  for (const [x, y, z] of shafts) c.set(x, y, z, CREATE('shaft'), { axis, waterlogged: 'false' })
  c.set(...gearboxAt, CREATE('gearbox'), { axis: axis === 'x' ? 'z' : 'x' })
  c.set(gearboxAt[0], gearboxAt[1] + 1, gearboxAt[2], CREATE('millstone'))
}

/** hurst: the timber frame a millstone stands on, round its gearbox at `at`: `post` (a log) at the four corners,
 *  `board` on the edges, the cell `shaftFrom` (the side the drive comes in, a direction) left to the shaft; and a deck
 *  of `deck` slabs round the stone one up, none over the shaft (nothing rests on a Create block). */
export function hurst(c, at, shaftFrom, { post, board, deck }) {
  const [x, y, z] = at
  const [sx, sz] = DIRS[shaftFrom]
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) {
    if (dx === 0 && dz === 0) continue
    if (dx === sx && dz === sz) continue
    const corner = dx !== 0 && dz !== 0
    c.set(x + dx, y, z + dz, corner ? post : board, corner ? { axis: 'y' } : {})
    slab(c, x + dx, y + 1, z + dz, deck, 'bottom')
  }
}

/** lucam: a mill's sack-hoist hood jutting from a gable: a `depth`-deep box `2r+1` wide centred on x, its front at z0
 *  (the gable at z0 + depth), its floor at y0 carried on upside-down stair brackets against the gable, `post` logs at its
 *  front corners, `infill` on its sides, `face` (default `infill`) round its door and in its little gable, a door in its face (the loft's hoist door), the opening through to the loft, a small
 *  45-degree gable roof of `roof` on top, and a lantern hung under its floor (it hoods the door below). Returns the
 *  roof's ridge y. */
export function lucam(c, { x, z0, depth = 2, y0, top, r = 1 }, { post, infill, face = infill, floor, roof, bracket, wood }) {
  const gable = z0 + depth
  for (let z = z0; z < gable; z++) for (let dx = -r; dx <= r; dx++) c.set(x + dx, y0, z, floor)
  for (const dx of [-r, r]) stair(c, x + dx, y0 - 1, gable - 1, bracket, 'south', 'top')
  for (let y = y0 + 1; y <= top; y++) for (let z = z0; z < gable; z++) for (let dx = -r; dx <= r; dx++) {
    const edge = Math.abs(dx) === r
    if (!edge && z !== z0) { c.clear(x + dx, y, z); continue } // the hood's inside
    const corner = edge && z === z0 // the front corners in `post`, the sides back to the gable in `infill`
    c.set(x + dx, y, z, corner ? post : z === z0 ? face : infill, corner ? { axis: 'y' } : {})
  }
  for (const half of ['lower', 'upper'])
    c.set(x, y0 + 1 + (half === 'upper' ? 1 : 0), z0, `${wood}_door`, { facing: 'south', half, hinge: 'left', open: 'false', powered: 'false' })
  for (const y of [y0 + 1, y0 + 2]) c.clear(x, y, gable) // through the gable to the loft
  // its roof: stairs from one past each side up to a ridge block over the middle, along the box (the gable behind closes it)
  const ridgeY = top + 2 + r
  for (let z = z0; z < gable; z++) {
    for (let i = 0; i <= r; i++) {
      const y = top + 1 + i
      stair(c, x - r - 1 + i, y, z, roof.stairs, 'east')
      stair(c, x + r + 1 - i, y, z, roof.stairs, 'west')
      for (let yy = top + 1; yy < y; yy++) for (const xx of [x - r - 1 + i, x + r + 1 - i]) if (Math.abs(xx - x) <= r && z === z0) c.set(xx, yy, z, face)
    }
    c.set(x, ridgeY, z, roof.full)
    if (z === z0) for (let yy = top + 1; yy < ridgeY; yy++) c.set(x, yy, z, face)
  }
  c.set(x, y0 - 1, gable - 1, 'lantern', { hanging: 'true', waterlogged: 'false' })
  return ridgeY
}

/** sluiceGate: a raised gate over a launder's mouth: a top-half trapdoor opened up against the side it lifts toward
 *  (`facing`), hung in the cell over the water between the walls either side. */
export function sluiceGate(c, [x, y, z], wood, facing) { trapdoor(c, x, y, z, `${wood}_trapdoor`, facing, 'top', true) }
