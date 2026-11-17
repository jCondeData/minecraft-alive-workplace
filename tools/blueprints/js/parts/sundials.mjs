// Sundials (parts library, tools/blueprints/REBUILD.md): a cylinder dial ("cradle", the scaphe of the old world, drawn
// for Minecraft's sky) and the pylons that hold its rod. Every block comes from the family's palette; none is picked
// here.
//
// Why a cylinder: Minecraft's sun crosses the sky straight overhead, east to west, at an even pace. The shadow of a rod
// laid north-south on the axis of a half-cylinder therefore sweeps the cylinder's inside at an even 15 degrees an hour,
// and every hour line is a straight line along the rod: the one dial that tells true time under this sky.

/** cradleProfile: the cells of one cross-section (x, y) of a cradle, mirrored about its middle column `cx`, from a
 *  half-profile `half`: for each step out from the middle (dx = 0, 1, ...) a list of [y, role, shape], where role is a
 *  palette key of the caller's (e.g. 'hull', 'face', 'mark', 'noon') and shape is 'full', 'slab' (bottom slab) or
 *  'under' (an upside-down stair, its low step toward the middle: the hull's rounded underside). Returns
 *  [{x, y, role, shape, inward}] where inward is the facing toward the middle ('east' left of it, 'west' right of it). */
export function cradleProfile(cx, half) {
  const out = []
  half.forEach((cells, dx) => {
    for (const [y, role, shape] of cells) {
      out.push({ x: cx - dx, y, role, shape, inward: 'east' })
      if (dx > 0) out.push({ x: cx + dx, y, role, shape, inward: 'west' })
    }
  })
  return out
}

/** cradle: the profile swept along z from z0 to z1 (the rod's direction, north-south). `pick(role, z)` returns the
 *  palette entry for a role in that slice, { full, slab, stairs, props? } (props: a glazed or directional block's
 *  state); it also gets the cell {x, y, ...}, so a slice or a single row can trade its face for the frame's stone. */
export function cradle(c, { profile, z0, z1 }, pick) {
  for (let z = z0; z <= z1; z++) {
    for (const p of profile) {
      const m = pick(p.role, z, p)
      if (p.shape === 'full') c.set(p.x, p.y, z, m.full, m.props ?? {})
      else if (p.shape === 'slab') c.set(p.x, p.y, z, m.slab, { type: 'bottom', waterlogged: 'false' })
      else if (p.shape === 'under') c.set(p.x, p.y, z, m.stairs, { facing: p.inward, half: 'top', shape: 'straight', waterlogged: 'false' })
      else throw new Error(`cradle: unknown shape ${p.shape}`)
    }
  }
}

/** rod: the gnomon, a chain laid along z at (x, y) from z0 to z1 (a thin dark line, the shadow it casts is one too). */
export function rod(c, x, y, z0, z1, chain) {
  for (let z = z0; z <= z1; z++) c.set(x, y, z, chain, { axis: 'z', waterlogged: 'false' })
}

/** pylon: an upright that holds a rod's end: a full `base` block at y0, a slim `shaft` (a wall id) up to y1 - 1, a full
 *  `head` at y1 (the rod is fixed to its side), and, when `lamp` is given, a lantern standing on the head. */
export function pylon(c, x, z, y0, y1, { base, shaft, head, lamp = null }) {
  c.set(x, y0, z, base)
  for (let y = y0 + 1; y < y1; y++) c.set(x, y, z, shaft)
  c.set(x, y1, z, head)
  if (lamp) c.set(x, y1 + 1, z, lamp, { hanging: 'false', waterlogged: 'false' })
}
