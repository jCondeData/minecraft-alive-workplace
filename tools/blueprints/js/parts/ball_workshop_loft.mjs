// Drying-loft parts (parts library, tools/blueprints/REBUILD.md; grown from the Ball Workshop, 50.12): a jettied
// storey's sill beam on its brackets, a boss with a button set in a band, and a two-tier tray rack of produce
// (a gable's hoist arm is storehouse_b_warehouse's hoistArm). Every block comes from the family's palette; none is picked here.
import { stair, slab } from './canvas.mjs'

/** jettySill: the beam a jettied storey stands on, along x at (x0..x1, y, z) (logs along x), with stair brackets
 *  under it at each x of `at` (upside down, their backs to the wall behind: `back` is the way the wall lies). */
export function jettySill(c, { x0, x1, y, z, at = [], back = 'south' }, { log, bracket }) {
  for (let x = x0; x <= x1; x++) c.set(x, y, z, log, { axis: 'x' })
  for (const x of at) stair(c, x, y - 1, z, bracket, back, 'top')
}

/** boss: a round boss set into a band at `at` (a full `face` block in the beam's place), a `button` on its front
 *  (`out`: the way it faces), and a lantern hung under it when `lamp` is given. */
export function boss(c, { at, button, lamp = null, out = 'north' }, { face, buttonId }) {
  c.set(at[0], at[1], at[2], face)
  c.set(button[0], button[1], button[2], buttonId, { face: 'wall', facing: out, powered: 'false' })
  if (lamp) c.set(lamp[0], lamp[1], lamp[2], 'lantern', { hanging: 'true', waterlogged: 'false' })
}

/** trayRack: produce drying on trays along a wall: on the `line` (x when `along` is 'z', else z), from `from` to `to`,
 *  each tier a tray (a top `traySlab`) at y with the produce (a carpet of the next of `colours`) on it at y + 1;
 *  fence legs (`fence`) at the `posts` positions from the floor (`base`) up to under the lowest tray. The colours step
 *  along the rack and tier by tier from `start`, so no two neighbours match (racks of one loft pass on the count). */
export function trayRack(c, { along, line, from, to, base, posts = [], tiers, start = 0 }, { traySlab, fence, colours }) {
  const at = (u, y) => (along === 'z' ? [line, y, u] : [u, y, line])
  let k = start
  for (const y of tiers) for (let u = from; u <= to; u++) {
    const [x, , z] = at(u, y)
    c.free([x, y, z], [x, y + 1, z])
    slab(c, x, y, z, traySlab, 'top')
    c.set(x, y + 1, z, `${colours[k++ % colours.length]}_carpet`)
  }
  for (const u of posts) for (let y = base; y < Math.min(...tiers); y++) { const [x, , z] = at(u, y); c.free([x, y, z]); c.set(x, y, z, fence) }
}
