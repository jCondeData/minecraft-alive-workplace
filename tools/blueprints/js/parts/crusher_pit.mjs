// Crushers (parts library, tools/blueprints/REBUILD.md; grown from the Crushing Mill, ROADMAP 49/50.10): Create's pair
// of crushing wheels over an open catch pit, and the "twin drop" that drives such a pair from one cross line so the two
// wheels turn into each other at one speed. Create's ids and states are checked in
// libs/create-fabric-6.0.10.0+mc1.21.1.jar: a crushing wheel's `axis` is its own; the cell between two wheels is
// left out of the drawing (a structure void) because the wheels place create:crushing_wheel_controller there
// themselves; the controller's `facing` (where it throws what it crushes) is DOWN only when each wheel's top turns
// toward the gap (CrushingWheelBlock.updateControllers: the wheel's axis crossed with the side, times the sign of its
// speed). Every other block comes from the caller's palette.
import { gearbox } from './foundry.mjs'

const CREATE = id => `create:${id}`

/** crusherPair: two crushing wheels on `axis` ('x' or 'z') either side of the `gap` cell [x, y, z] along the other
 *  horizontal axis, the gap a structure void, the cell under the gap left open (nothing drawn: air) as the catch pit
 *  with `floorId` under it, `guard` (a low block a villager can't walk past, e.g. a wall) on the pit's two open sides
 *  across the wheels' line (a villager standing in the pit would have his head in the crusher), and `bed` under the
 *  wheels' axles behind the pit (`bedCells` [x, z] at y - 1). Returns the wheels' cells [west/north, east/south]. */
export function crusherPair(c, [x, y, z], axis, { floorId, guard, bed, bedCells = [] }) {
  const [dx, dz] = axis === 'z' ? [1, 0] : [0, 1]
  const a = [x - dx, y, z - dz], b = [x + dx, y, z + dz]
  for (const w of [a, b]) c.set(...w, CREATE('crushing_wheel'), { axis })
  c.set(x, y, z, 'structure_void')
  c.clear(x, y - 1, z) // the catch pit: drawn air
  c.set(x, y - 2, z, floorId)
  for (const s of [-1, 1]) c.set(x + dz * s, y - 1, z + dx * s, guard) // the pit's open sides, across the wheel line
  for (const [bx, bz] of bedCells) c.set(bx, y - 1, bz, bed)
  return [a, b]
}

/** twinDrop: gearboxes that take a horizontal cross line (on `crossAxis`) at y + 1 down and round a corner into each
 *  wheel's axle line at y: at each of `drops` ([x, z] on the cross line), a gearbox on the line (axis: the third axis,
 *  so it connects along the line and down) over a gearbox at y (axis: the cross axis, so it connects up and along the
 *  axle). Two drops on one cross line, the drive coming in from one end, turn their two axles opposite ways at one
 *  speed (Create's gearbox: a perpendicular face turns the other way when it shares its axis direction with the
 *  source face), which is what a crushing pair needs. Returns the top gearboxes' cells. */
export function twinDrop(c, drops, y, crossAxis) {
  const topAxis = crossAxis === 'x' ? 'z' : 'x'
  return drops.map(([x, z]) => {
    gearbox(c, [x, y + 1, z], topAxis)
    gearbox(c, [x, y, z], crossAxis)
    return [x, y + 1, z]
  })
}
