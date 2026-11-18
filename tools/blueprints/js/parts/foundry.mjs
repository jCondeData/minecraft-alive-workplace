// Foundries: Create's gearing drawn cell by cell (shafts, gearboxes, small and large cogwheels, a water wheel), a mixer
// standing over its basin and blaze burner, water laid as it settles, a glazed roof lantern (a monitor) and a banded
// tank (parts library, tools/blueprints/REBUILD.md). Grown from the Brass Foundry (49). Create's ids and states are
// checked in libs/create-fabric-6.0.10.0+mc1.21.1.jar: a shaft's, gearbox's and cogwheel's `axis` is their own (a
// gearbox connects on the four faces NOT on its axis); a large and a small cogwheel mesh only diagonally adjacent on
// parallel axes, the small one turning twice as fast; a water wheel's `facing` is its axle's direction. Every other
// block comes from the family's palette.

const CREATE = id => `create:${id}`

/** shaftRun: Create shafts on one `axis` in the given cells. */
export function shaftRun(c, cells, axis) {
  for (const [x, y, z] of cells) c.set(x, y, z, CREATE('shaft'), { axis, waterlogged: 'false' })
}

/** gearbox: a Create gearbox at [x, y, z] (it turns the drive round a corner on the four faces off its `axis`). */
export function gearbox(c, [x, y, z], axis) { c.set(x, y, z, CREATE('gearbox'), { axis }) }

/** cog: a small (or, `large`, a large) cogwheel on `axis`. Pair a large one diagonally with a small one to gear up 2x. */
export function cog(c, [x, y, z], axis, large = false) {
  c.set(x, y, z, CREATE(large ? 'large_cogwheel' : 'cogwheel'), { axis, waterlogged: 'false' })
}

/** waterWheel: a small Create water wheel, its axle along `facing`'s axis. Water must run or fall past one of the
 *  four cells round it in its plane (falling water only counts beside a solid face). */
export function waterWheel(c, [x, y, z], facing) { c.set(x, y, z, CREATE('water_wheel'), { facing }) }

/** mixerStack: a mechanical mixer at [x, y, z] (driven from the shaft above it), the cell under it left empty for its
 *  head, a basin two below it (facing down: it keeps what it makes) and a filled blaze burner under the basin. */
export function mixerStack(c, [x, y, z]) {
  c.set(x, y, z, CREATE('mechanical_mixer'))
  c.clear(x, y - 1, z)
  c.set(x, y - 2, z, CREATE('basin'), { facing: 'down' })
  c.set(x, y - 3, z, CREATE('blaze_burner'))
}

/** settledWater: water as it lies once it has settled ([{at, level}]: level 0 the poured source, 1-7 running water,
 *  8 falling). Only the source is placed by a builder; the rest comes by itself. */
export function settledWater(c, cells) {
  for (const { at: [x, y, z], level } of cells) c.set(x, y, z, 'water', { level: String(level) })
}

/** roofLantern: a glazed monitor along z standing on a roof (walls x0 / x1 from base(x) up to `top`, ends z0 / z1):
 *  `post` blocks at the corners and at each z of `posts` (`postFoot` below the glass, default `post`), `glassId`
 *  between them from glass[0] to glass[1], a
 *  `band` course over and under the glass, ends filled with `wall` from `endBase(x)` up to `under[x] - 1` (the
 *  lantern roof's underside, from gableRoof; `wall` may be a function (x, y, z) => id). Glass cells in the front end
 *  (z0) are `endGlass` ([x, y] pairs). */
export function roofLantern(c, { x0, x1, z0, z1, base, top, posts, glass: [g0, g1], endBase, under, endGlass = [] },
  { post, postFoot = post, band, glassId, wall }) {
  for (const x of [x0, x1]) for (let z = z0; z <= z1; z++) for (let y = base; y <= top; y++) {
    const corner = z === z0 || z === z1 || posts.includes(z)
    c.set(x, y, z, corner ? (y < g0 ? postFoot : post) : y >= g0 && y <= g1 ? glassId : band)
  }
  const glassAt = new Set(endGlass.map(([x, y]) => `${x},${y}`))
  for (const z of [z0, z1]) for (let x = x0 + 1; x < x1; x++)
    for (let y = endBase(x); y < under[x]; y++)
      c.set(x, y, z, glassAt.has(`${x},${y}`) && z === z0 ? glassId : typeof wall === 'function' ? wall(x, y, z) : wall)
}

/** bandedTank: a square tank of `shell` on a tower top from y0 to y1 (x0..x1, z0..z1), a `hoop` ring at each y of
 *  `hoops`, a pyramid cap of `cap` stairs round a full block (or, given `parapet` (a slab id), a flat lid: a ring of
 *  those slabs round a full block), and a `finial` on top. */
export function bandedTank(c, x0, z0, x1, z1, y0, y1, { shell, hoop, hoops = [], cap, parapet = null, finial = null }) {
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const ring = x === x0 || x === x1 || z === z0 || z === z1
    c.set(x, y, z, ring && hoops.includes(y) ? hoop : shell)
  }
  const mx = (x0 + x1) / 2, mz = (z0 + z1) / 2
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const dx = x - mx, dz = z - mz
    if (dx === 0 && dz === 0) { c.set(x, y1 + 1, z, cap.full); continue }
    if (parapet) { c.set(x, y1 + 1, z, parapet, { type: 'bottom', waterlogged: 'false' }); continue }
    const facing = Math.abs(dz) >= Math.abs(dx) ? (dz < 0 ? 'south' : 'north') : (dx < 0 ? 'east' : 'west')
    c.set(x, y1 + 1, z, cap.stairs, { facing, half: 'bottom', shape: 'straight', waterlogged: 'false' })
  }
  if (finial) c.set(Math.round(mx), y1 + 2, Math.round(mz), finial) // its default state (a lightning rod stands up)
}
