// Wrecks (parts library, tools/blueprints/REBUILD.md; grown from the Caravan Wreck, 49): a wagon lying on its side
// (its floor standing as a wall, the side boards as a sunk sill and a ledge, its axles upright on the underside), a
// covered wagon's bonnet on its side (a half-tunnel of canvas on hoops, sagging between them, slashed), a wheel lying
// flat (a hub with a ring of slabs, some broken out), and a scatter of props from a list. Every block comes from the
// caller's palette; none is picked here.
import { slab, trapdoor } from './canvas.mjs'

const AXIS_PROPS = id => (/_log$|_wood$|hay_block|_stem$/.test(id) ? true : false)

/** sideWagon: a wagon body lying on its side, running along z from z0 to z1. Its floor stands as a wall of `deck` at
 *  x = floorX from y0 to y1 (y0 may be the ground layer: the bottom edge sunk); its open top faces away from `under`
 *  ('east': the underside looks to +x, the load side to -x). `boards` [a, b]: the x cells of the side boards (the
 *  wagon's depth), drawn as the bottom board lying at y0 and the top board as a ledge at y1, the top one's outer cell
 *  a `rail` log along z. The front board shuts z = frontZ (or none) from y0 + 1 to y1 - 1. Returns the hollow's cells
 *  (the space between the boards, inside the floor). */
export function sideWagon(c, { floorX, y0, y1, z0, z1, boards: [ba, bb], frontZ = null }, { deck, board, rail }) {
  const outer = Math.abs(ba - floorX) > Math.abs(bb - floorX) ? ba : bb // the board's edge farthest from the floor
  for (let z = z0; z <= z1; z++) {
    for (let y = y0; y <= y1; y++) c.set(floorX, y, z, deck)
    for (const x of [ba, bb]) {
      c.set(x, y0, z, board)
      c.set(x, y1, z, x === outer ? rail : board, x === outer ? { axis: 'z' } : {})
    }
  }
  const hollow = []
  for (let z = z0; z <= z1; z++) for (const x of [ba, bb]) for (let y = y0 + 1; y < y1; y++) {
    if (frontZ !== null && z === frontZ) c.set(x, y, z, board)
    else hollow.push([x, y, z])
  }
  return hollow
}

/** underGear: a side-lying wagon's running gear on its underside at x: an `axle` log upright at each z of `axles`
 *  from y0 to y1, a `reach` log along z at reachY between the first and last axle. */
export function underGear(c, { x, y0, y1, axles, reachY }, { axle }) {
  for (const z of axles) for (let y = y0; y <= y1; y++) c.set(x, y, z, axle, { axis: 'y' })
  const lo = Math.min(...axles), hi = Math.max(...axles)
  for (let z = lo + 1; z < hi; z++) c.set(x, reachY, z, axle, { axis: 'z' })
}

/** sideBonnet: a covered wagon's canvas cover lying on its side: a half-tunnel along z (z0..z1) from the top rail at
 *  x = attach (y = top) out to x = outer and down to the ground fold at y = foot. Its section is rounded in one step:
 *  the top row at y = top from attach to outer + 2, the shoulder at (outer + 1, top - 1), the side at x = outer from
 *  top - 2 down to foot + 1, and the ground fold at y = foot from outer to attach - 1 (cells meeting only at an edge
 *  still shut the cells inside). At each hoop z the hoop holds the cloth out square: the two cells outside the
 *  shoulder are drawn too, a rib standing proud of the canvas sagging between. `front` 'shut' closes the section one
 *  in from z0 (a drawn pucker, set back inside the flared hoop at z0). `slash` lists cells cut out; `bare` lists cells
 *  drawn in `hoop` (a pale log along x) instead: a hoop's rib left spanning a slash. `canvas` may be a function of
 *  (x, y, z) (a stained hem). Returns the cells inside the tunnel at the foot's level + 1 (the hollow's floor). */
export function sideBonnet(c, { outer, top, foot, attach, z0, z1, hoops = [], front = null, slash = [], bare = [] }, { canvas, hoop }) {
  const cut = new Set(slash.map(p => p.join(',')))
  const rib = new Set(bare.map(p => p.join(',')))
  const section = []
  for (let x = attach; x >= outer + 2; x--) section.push([x, top, false])
  section.push([outer + 1, top - 1, false])
  for (let y = top - 2; y > foot; y--) section.push([outer, y, false])
  for (let x = outer; x < attach; x++) section.push([x, foot, false])
  section.push([outer + 1, top, true], [outer, top - 1, true]) // the hoop's square corner
  for (let z = z0; z <= z1; z++) {
    const isHoop = hoops.includes(z)
    for (const [x, y, hoopOnly] of section) {
      const k = `${x},${y},${z}`
      if (hoopOnly && !isHoop) continue
      if (rib.has(k)) { c.set(x, y, z, hoop, { axis: 'x' }); continue }
      if (cut.has(k)) continue
      c.set(x, y, z, canvas)
    }
    if (z === z0 + 1 && front === 'shut') for (let x = outer + 1; x < attach; x++) for (let y = foot + 1; y < top; y++) c.set(x, y, z, canvas)
  }
  const inside = []
  for (let z = z0; z <= z1; z++) for (let x = outer + 1; x < attach; x++) inside.push([x, foot + 1, z])
  return inside
}

/** spokeWheel: a wheel lying flat with its felloes smashed off: the `hubLog` upright at `hub` [x, y, z] and a `spoke`
 *  (a fence, which joins the hub and reads as a bare spoke) out on each of the four arms, a `rim` slab fragment left
 *  at each of the corners named in `felloes` ([dx, dz]). */
export function spokeWheel(c, [x, y, z], { hubLog, spoke, rim }, felloes = []) {
  c.set(x, y, z, hubLog, { axis: 'y' })
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) c.set(x + dx, y, z + dz, spoke)
  for (const [dx, dz] of felloes) slab(c, x + dx, y, z + dz, rim)
}

/** flatWheel: a cart wheel lying flat on its face at the hub's height, its hub `hubLog` upright at `hub` [x, y, z].
 *  radius 1: the rim is the eight cells round the hub, `rim` slabs. radius 2: a spoked wheel, its inner ring the
 *  eight cells round the hub as `spoke`s (a trapdoor lying shut: lower than the rim, so the wheel is dished, and a
 *  lattice trapdoor shows its spokes; no open gaps, which would be pits in a roof at height), and the rim the twelve
 *  cells of the ring two out (its corners cut, so it reads round). `broken` cells are left out (a smashed felloe). */
export function flatWheel(c, [x, y, z], { rim, hubLog, spoke = null }, broken = [], radius = 1) {
  const gone = new Set(broken.map(p => p.join(',')))
  c.set(x, y, z, hubLog, { axis: 'y' })
  for (let dx = -radius; dx <= radius; dx++) for (let dz = -radius; dz <= radius; dz++) {
    const ring = Math.max(Math.abs(dx), Math.abs(dz))
    if (!ring || gone.has(`${x + dx},${y},${z + dz}`)) continue
    if (radius === 2 && Math.abs(dx) === 2 && Math.abs(dz) === 2) continue // the cut corners
    if (radius === 2 && ring === 1) {
      const facing = dz > 0 ? 'south' : dz < 0 ? 'north' : dx > 0 ? 'east' : 'west'
      trapdoor(c, x + dx, y, z + dz, spoke, facing)
      continue
    }
    slab(c, x + dx, y, z + dz, rim)
  }
}

/** scatter: props from a list of [x, y, z, key, props]; `ids[key]` is the block id (a key not in `ids` is used as the
 *  id itself). Trapdoors get their unset state filled (bottom, shut, unpowered); logs and bales keep their axis. */
export function scatter(c, list, ids) {
  for (const [x, y, z, key, props = {}] of list) {
    const id = ids[key] ?? key
    const p = { ...props }
    if (id.endsWith('_trapdoor')) Object.assign(p, { half: p.half ?? 'bottom', open: p.open ?? 'false', powered: 'false', waterlogged: 'false' })
    if (AXIS_PROPS(id) && !p.axis) p.axis = 'y'
    c.set(x, y, z, id, p)
  }
}
