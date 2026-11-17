// Mills: Create's moving parts drawn as a builder lays them, and the water that drives a wheel (parts library,
// tools/blueprints/REBUILD.md). Grown from the Sawmill (49, Create build). Blocks other than Create's own come from the
// family's palette; Create's ids and states are checked in libs/create-fabric-6.0.10.0+mc1.21.1.jar (a belt's pulleys
// turn about the axis across its `facing`; a saw facing up turns about x with axis_along_first true, about z without).

const CREATE = id => `create:${id}`

/** beltRun: a straight horizontal Create belt over `cells` (in order from its start pulley to its end pulley), its
 *  items running toward `facing`. Create works out each segment's place on its belt from the blocks. */
export function beltRun(c, cells, facing) {
  cells.forEach(([x, y, z], i) => {
    const part = i === 0 ? 'start' : i === cells.length - 1 ? 'end' : 'middle'
    c.set(x, y, z, CREATE('belt'), { facing, part, slope: 'horizontal', casing: 'false', waterlogged: 'false' })
  })
}

/** cogTrain: small cogwheels on one `axis` in a row of cells (each meshes with the next; neighbours turn opposite). */
export function cogTrain(c, cells, axis) {
  for (const [x, y, z] of cells) c.set(x, y, z, CREATE('cogwheel'), { axis, waterlogged: 'false' })
}

/** upSaw: a mechanical saw facing up in a belt line, turning about `axis` ('x' or 'z'); `flipped` for a saw whose
 *  drive turns the other way (geared from a neighbouring cog), so both cut the same way. */
export function upSaw(c, [x, y, z], axis, flipped = false) {
  c.set(x, y, z, CREATE('mechanical_saw'), { facing: 'up', axis_along_first: String(axis === 'x'), flipped: String(flipped) })
}

/** wheelDrop: a drive shaft that drops from a wheel's axle to the floor inside a wall: a gearbox at the top and the
 *  bottom (both on `axis`, taking the drive round the corner), a vertical shaft between them. */
export function wheelDrop(c, x, z, yTop, yBottom, axis) {
  c.set(x, yTop, z, CREATE('gearbox'), { axis })
  for (let y = yBottom + 1; y < yTop; y++) c.set(x, y, z, CREATE('shaft'), { axis: 'y', waterlogged: 'false' })
  c.set(x, yBottom, z, CREATE('gearbox'), { axis })
}

/** flume: a wooden trough carrying water to a wheel. `run` lists the trough's water cells [x, z] at y from its head
 *  (the source the builder pours) to its lip (the last cell, open below, where the water falls); `side` the trough's
 *  wall cells either side ([x, z]), `floor` its bed, `end` the head's back wall. Water as it settles: the source, then
 *  running water one level weaker per cell (it comes by itself). `walls` and `bed` are the palette's blocks (`walls`
 *  may be a function (x, y, z) => id, for a trough whose one side is another wall). */
export function flume(c, { run, y, side, end }, { walls, bed }) {
  run.forEach(([x, z], i) => {
    if (i < run.length - 1) c.set(x, y - 1, z, bed)
    c.set(x, y, z, 'water', { level: String(i) })
  })
  for (const [x, z] of side) c.set(x, y, z, typeof walls === 'function' ? walls(x, y, z) : walls)
  c.set(end[0], y, end[1], typeof walls === 'function' ? walls(...end) : walls)
}

/** fallChute: the shaft water falls down from a flume's lip at (x, yTop, z) to a closed sump at y = yBottom (its floor
 *  under it, its four sides walled), drawn as falling water. `walls` lists the chute's wall cells [x, z] (at least one
 *  side solid all the way, so the water falls hard against the wheel); `sump` the sump's walls round the bottom cell. */
export function fallChute(c, [x, z], yTop, yBottom, { walls, sump }, stone) {
  for (let y = yBottom; y < yTop; y++) c.set(x, y, z, 'water', { level: '8' })
  c.set(x, yBottom - 1, z, stone)
  for (const [wx, wz] of walls) for (let y = yBottom; y < yTop; y++) c.set(wx, y, wz, stone)
  for (const [wx, wz] of sump) c.set(wx, yBottom, wz, stone)
}

/** cistern: a hooped water tank on log legs (a mill's header tank, read from the street): a `w` x `d` shell of
 *  `stave` (a log) from y0 to y1 on corner `leg` posts down to y = 0, a `hoop` ring at each y of `hoops`, and a low pyramid
 *  cap of `cap` stairs. The shell is solid (the mill's water is its flume). */
export function cistern(c, x0, z0, w, d, y0, y1, { stave, leg, hoop, hoops = [], cap }) {
  const x1 = x0 + w - 1, z1 = z0 + d - 1
  for (const [x, z] of [[x0, z0], [x1, z0], [x0, z1], [x1, z1]]) for (let y = 0; y < y0; y++) c.set(x, y, z, leg, { axis: 'y' })
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const ring = x === x0 || x === x1 || z === z0 || z === z1
    c.set(x, y, z, ring && hoops.includes(y) ? hoop : stave, { axis: 'y' }) // both logs (stave and hoop stand upright)
  }
  const mx = (x0 + x1) / 2, mz = (z0 + z1) / 2
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const dx = x - mx, dz = z - mz
    if (dx === 0 && dz === 0) { c.set(x, y1 + 1, z, cap.full); continue }
    const facing = Math.abs(dz) >= Math.abs(dx) ? (dz < 0 ? 'south' : 'north') : (dx < 0 ? 'east' : 'west')
    c.set(x, y1 + 1, z, cap.stairs, { facing, half: 'bottom', shape: 'straight', waterlogged: 'false' })
  }
}
