// Smithy parts (parts library, tools/blueprints/REBUILD.md; grown from the Smithy, 49): a tapered furnace stack (a
// truncated stone pyramid in setback tiers with a smoking crown), a raised quench trough, and a furnace mouth set in a
// wall under its brick hood. Every block comes from the family's palette; none is picked here.
import { stair, slab } from './canvas.mjs'

const INWARD = { n: 'south', s: 'north', w: 'east', e: 'west' }

/** taperedStack: a furnace stack centred on (cx, cz): `tiers` [{w, top}, ...] (odd widths, narrowing upward), the
 *  first standing from y0. Each tier is solid `body` (a block or a mix(); `bodyOf(i)` overrides it per tier) up to its
 *  top; where a tier steps in, the ledge it leaves is a ring of `ledge` stairs rising inward (the batter: kit.finish
 *  turns the corners). On the last tier a crown: its centre a full `crown` block under a lit campfire (the smoke), the
 *  ring round it `crownSlab` slabs. The campfire stands on a stack of full blocks all the way down to the hollow, so
 *  a restyle keeps it whole (StyleGameTests.chimneysStayWholeInEveryStyle). `quoin(i)`: dressed corner stones per tier
 *  ([id, alt] alternating course by course, or null). Returns the campfire's y. Clear rooms out of it afterwards. */
export function taperedStack(c, cx, cz, y0, tiers, { body, bodyOf = () => null, ledge, crown, crownSlab, quoin = () => null }) {
  let from = y0
  tiers.forEach((t, i) => {
    const h = (t.w - 1) / 2
    const id = bodyOf(i) ?? body
    for (let y = from; y <= t.top; y++)
      for (let x = cx - h; x <= cx + h; x++) for (let z = cz - h; z <= cz + h; z++) c.set(x, y, z, id)
    const q = quoin(i)
    if (q) for (const [x, z] of [[cx - h, cz - h], [cx + h, cz - h], [cx - h, cz + h], [cx + h, cz + h]])
      for (let y = Math.max(from, y0 + 1); y <= t.top; y++) c.set(x, y, z, (y - from) % 2 ? q[1] : q[0])
    const next = tiers[i + 1]
    if (next) { // the ledge: this tier's rim not covered by the next tier
      const k = (next.w - 1) / 2
      for (let x = cx - h; x <= cx + h; x++) for (let z = cz - h; z <= cz + h; z++) {
        if (Math.abs(x - cx) <= k && Math.abs(z - cz) <= k) continue
        const side = z === cz - h ? 'n' : z === cz + h ? 's' : x === cx - h ? 'w' : x === cx + h ? 'e' : null
        if (side) stair(c, x, t.top + 1, z, ledge, INWARD[side])
      }
    }
    from = t.top + 1
  })
  const last = tiers[tiers.length - 1], top = last.top + 1, h = (last.w - 1) / 2
  for (let x = cx - h; x <= cx + h; x++) for (let z = cz - h; z <= cz + h; z++)
    if (x === cx && z === cz) c.set(x, top, z, crown)
    else slab(c, x, top, z, crownSlab, 'bottom')
  c.set(cx, top + 1, cz, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  return top + 1
}

/** quenchTrough: water a smith quenches in, standing above the ground: water sources in `cells` at y (each on a full
 *  block at y - 1), every open side at y closed by a full `rim` block (a block or a mix()), so nothing flows out. */
export function quenchTrough(c, cells, y, rim) {
  const water = new Set(cells.map(([x, z]) => `${x},${z}`))
  for (const [x, z] of cells) {
    if (!c.get(x, y - 1, z)) c.set(x, y - 1, z, rim)
    c.set(x, y, z, 'water', { level: '0' })
  }
  for (const [x, z] of cells) for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    const nx = x + dx, nz = z + dz
    if (!water.has(`${nx},${nz}`) && !c.get(nx, y, nz)) c.set(nx, y, nz, rim)
  }
}

/** furnaceMouth: a job block (`block`, e.g. a blast furnace) set into a wall at [x, y, z], facing into the room
 *  (`into`), its breast of `brick` up the wall to `top` either side and over it, and a hood in the room over its front:
 *  a full `brick` at y + 2 with `hoodStairs` upside down either side, their backs to the wall (the mantel). */
export function furnaceMouth(c, [x, y, z], into, block, { brick, hoodStairs, top = y + 2 }) {
  const [dx, dz] = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[into]
  const back = { north: 'south', south: 'north', west: 'east', east: 'west' }[into]
  const [ax, az] = dx ? [0, 1] : [1, 0]
  for (let yy = y; yy <= top; yy++) for (const s of [-1, 0, 1]) {
    if (s === 0 && yy === y) continue
    c.set(x + ax * s, yy, z + az * s, brick)
  }
  c.set(x, y, z, block, { facing: into, lit: 'false' })
  c.set(x + dx, y + 2, z + dz, brick)
  for (const s of [-1, 1]) stair(c, x + dx + ax * s, y + 2, z + dz + az * s, hoodStairs, back, 'top')
}

/** clumped: a mix() read in blocks of `n` (n x n x n cells share one pick), so rubble's colours lie in patches the
 *  way stones of one load lie together, not as single-block confetti (STYLE.md: weathering in clusters). */
export function clumped(m, n = 2) {
  return (x, y, z) => m(Math.floor(x / n), Math.floor(y / n), Math.floor(z / n))
}
