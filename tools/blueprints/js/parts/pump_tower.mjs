// Water towers and pump rooms (parts library, tools/blueprints/REBUILD.md; grown from the Pump House, 49/50.13): a run
// of Create fluid pipes whose six sides follow the run, a two-high Create fluid tank, a gallery deck carried round a
// tower on corbels with its railing, a hollow four-sided roof, and a tanner's drying frame of hides on a beam.
// Create's ids and states are checked in libs/create-fabric-6.0.10.0+mc1.21.1.jar: a fluid pipe has the six booleans
// north/south/east/west/up/down (the sides it opens on); a fluid tank has top, bottom, shape and light_level. Every
// other block comes from the caller's palette, never from here.
import { stair } from './canvas.mjs'

const STEP = { east: [1, 0, 0], west: [-1, 0, 0], south: [0, 0, 1], north: [0, 0, -1], up: [0, 1, 0], down: [0, -1, 0] }
const dirTo = ([ax, ay, az], [bx, by, bz]) => {
  const d = [bx - ax, by - ay, bz - az]
  const hit = Object.entries(STEP).find(([, s]) => s[0] === d[0] && s[1] === d[1] && s[2] === d[2])
  if (!hit) throw new Error(`pipeRun: ${ax},${ay},${az} -> ${bx},${by},${bz} is not one step`)
  return hit[0]
}

/** pipeRun: Create fluid pipes along `cells` (each one step from the last), every pipe open toward its neighbours in
 *  the run, the first also toward `before` (the pump it leaves, or the water an open end dips into) and the last
 *  toward `after` (the pump or tank it enters). Returns the cells. */
export function pipeRun(c, cells, { before = null, after = null } = {}) {
  cells.forEach((p, i) => {
    const open = new Set()
    if (i > 0) open.add(dirTo(p, cells[i - 1]))
    if (i < cells.length - 1) open.add(dirTo(p, cells[i + 1]))
    if (i === 0 && before) open.add(before)
    if (i === cells.length - 1 && after) open.add(after)
    const props = {}
    for (const d of Object.keys(STEP)) props[d] = String(open.has(d))
    c.set(...p, 'create:fluid_pipe', props)
  })
  return cells
}

/** tallTank: a one-wide Create fluid tank two blocks tall, its foot at [x, y, z] (Create joins the two into one). */
export function tallTank(c, [x, y, z]) {
  c.set(x, y, z, 'create:fluid_tank', { bottom: 'true', top: 'false', shape: 'window', light_level: '0' })
  c.set(x, y + 1, z, 'create:fluid_tank', { bottom: 'false', top: 'true', shape: 'window', light_level: '0' })
}

/** corbelGallery: a deck at `y` over x0..x1, z0..z1 (a tower's top and one block round it), carried on a ring of
 *  upside-down `corbel` stairs at y - 1 under its outer edge (each with its back to the tower), a `rail` (a fence id)
 *  round the edge at y + 1. Cells `skip` ([x, y, z]) are left to the drawing (a pipe through the deck, a spout).
 *  Corbels go only where nothing is drawn yet. */
export function corbelGallery(c, { x0, x1, z0, z1, y, skip = [] }, { deck, corbel, rail }) {
  const no = new Set(skip.map(p => p.join(',')))
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (!no.has([x, y, z].join(','))) c.set(x, y, z, deck)
    const edge = x === x0 || x === x1 || z === z0 || z === z1
    if (!edge) continue
    const corner = (x === x0 || x === x1) && (z === z0 || z === z1)
    if (!corner && !c.get(x, y - 1, z) && !no.has([x, y - 1, z].join(','))) {
      const back = x === x0 ? 'east' : x === x1 ? 'west' : z === z0 ? 'south' : 'north'
      stair(c, x, y - 1, z, corbel, back, 'top')
    }
    c.set(x, y + 1, z, rail)
  }
}

/** steepHollowCap: a steep four-sided roof centred on (cx, cz), `r` out at its eave (y0): each ring a stair course, one
 *  in and two up from the last, with a full course under every stair course but the eave's, hollow inside; `peak`
 *  (an id, default roof.full) on top at y0 + 2r. Lighter than pyramidRoof (no solid core) for a roof seen from under
 *  its eaves. Returns the peak's y. */
export function steepHollowCap(c, cx, cz, r, y0, roof, peak = roof.full) {
  const face = (dx, dz) => (Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? 'east' : 'west') : (dz < 0 ? 'south' : 'north'))
  for (let k = 0; k < r; k++) {
    const rr = r - k, y = y0 + 2 * k
    for (let dx = -rr; dx <= rr; dx++) for (let dz = -rr; dz <= rr; dz++) {
      if (Math.max(Math.abs(dx), Math.abs(dz)) !== rr) continue
      stair(c, cx + dx, y, cz + dz, roof.stairs, face(dx, dz))
      if (rr < r) c.set(cx + dx, y - 1, cz + dz, roof.full)
    }
  }
  c.set(cx, y0 + 2 * r - 1, cz, roof.full)
  c.set(cx, y0 + 2 * r, cz, peak)
  return y0 + 2 * r
}

/** hideFrame: a tanner's drying frame along x at z: `post` logs at x0 and x1 from y0 to top, a `beam` log between them
 *  at top, and hides hung on both faces of the beam (wall banners, `hides` colours left to right; north face, then
 *  south face). */
export function hideFrame(c, { x0, x1, z, y0 = 0, top }, { post, beam, hides }) {
  for (const x of [x0, x1]) for (let y = y0; y <= top; y++) c.set(x, y, z, post, { axis: 'y' })
  for (let x = x0 + 1; x < x1; x++) {
    c.set(x, top, z, beam, { axis: 'x' })
    const i = x - x0 - 1
    c.set(x, top, z - 1, `${hides[i % hides.length]}_wall_banner`, { facing: 'north' })
    c.set(x, top, z + 1, `${hides[(i + 1) % hides.length]}_wall_banner`, { facing: 'south' })
  }
}
