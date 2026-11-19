// Guard House parts (the Gate Watch guard house, ROADMAP 49): a timber storey whose frame stands on the outer line with
// its infill a block back (so shutters and sills stand on the floor's edge beam), the stone face of an arch over a
// passage, and an alarm bell hung under a roof's overhang (parts library, tools/blueprints/REBUILD.md). Every block
// comes from the caller's palette.
import { slab } from './canvas.mjs'

/** insetStorey: a timber storey over x0..x1, z0..z1 (the outer line, the floor's edge beam below it): `post` logs on
 *  the outer line at the corners and at each x of `xs` (front and back) and z of `zs` (the sides) from y0 to y1; the
 *  `infill` wall one block in (x0 + 1..x1 - 1, z0 + 1..z1 - 1) from y0 to y1; a top plate of `plate` logs round the
 *  outer line at y1 + 1. The cells between the posts stay open: a recess a block deep whose floor is the edge beam, so
 *  the shutters drawn in it stand on something. `flush` lists the sides ('x0', 'x1', 'z0', 'z1') whose infill stands
 *  on the outer line instead (a side with no jetty: its posts stand in the wall). `infill` may be a mix(). Returns the
 *  infill's box. */
export function insetStorey(c, { x0, z0, x1, z1, y0, y1, xs = [], zs = [], flush = [] }, { post, infill, plate }) {
  const f = s => (flush.includes(s) ? 0 : 1)
  const X0 = x0 + f('x0'), X1 = x1 - f('x1'), Z0 = z0 + f('z0'), Z1 = z1 - f('z1')
  for (let x = X0; x <= X1; x++) for (let z = Z0; z <= Z1; z++) {
    if (x !== X0 && x !== X1 && z !== Z0 && z !== Z1) continue
    for (let y = y0; y <= y1; y++) c.set(x, y, z, infill)
  }
  const up = [[x0, z0], [x1, z0], [x0, z1], [x1, z1]]
  for (const x of xs) up.push([x, z0], [x, z1])
  for (const z of zs) up.push([x0, z], [x1, z])
  for (const [x, z] of up) for (let y = y0; y <= y1; y++) c.set(x, y, z, post, { axis: 'y' })
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (x !== x0 && x !== x1 && z !== z0 && z !== z1) continue
    const corner = (x === x0 || x === x1) && (z === z0 || z === z1)
    c.set(x, y1 + 1, z, plate, { axis: corner ? 'y' : (z === z0 || z === z1) ? 'x' : 'z' })
  }
  return { x0: X0, z0: Z0, x1: X1, z1: Z1 }
}

/** archFace: the stone face of a passage's arch in the wall plane z = `z`, over the opening x0..x1 (3 or wider): at
 *  y (the arch's springing course, the top of the opening) an upside-down `stairs` against each jamb and `keystone`
 *  in the cells between, so the opening reads as a sprung arch with its keystone (a villager still walks under the
 *  middle, y - 1 below it). `stairs` faces the jamb it leans on. */
export function archFace(c, z, x0, x1, y, { stairs, keystone }) {
  c.set(x0, y, z, stairs, { facing: 'west', half: 'top', shape: 'straight', waterlogged: 'false' })
  c.set(x1, y, z, stairs, { facing: 'east', half: 'top', shape: 'straight', waterlogged: 'false' })
  for (let x = x0 + 1; x < x1; x++) c.set(x, y, z, keystone)
}

/** alarmBell: a bell hung from the block over it (a ridge, a beam), the watch's alarm; with `hood`, a bottom slab of
 *  that id over a lone hanger cell (when the cell above is empty) so the bell has its own little roof. */
export function alarmBell(c, [x, y, z], facing, { hood = null } = {}) {
  if (hood && !c.get(x, y + 1, z)) slab(c, x, y + 1, z, hood)
  c.set(x, y, z, 'bell', { attachment: 'ceiling', facing, powered: 'false' })
}
