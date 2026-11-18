// Glass cone parts (parts library, tools/blueprints/REBUILD.md), grown from the Glassblower's Workshop (50.7): a hollow
// brick glass cone that tapers in corbelled courses over the furnace (the whole cone is the flue), a round crown-glass
// "bullseye" window for a gable, and a brick siege round a furnace or kiln. Every block comes from the family's
// palette; none is picked here.
import { stair, slab } from './canvas.mjs'

const inward = (dx, dz) => (Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? 'east' : 'west') : (dz < 0 ? 'south' : 'north'))

/** coneStage: the stage of a glass cone a course at y belongs to (stages [{t, top}], widest first, the first from
 *  y0 + 1), or null outside them. A course's outline is the disc dx^2 + dz^2 <= t, so stepping t down a little at a
 *  time (19, 16, 13, 10, 8, 5, 2) narrows the cone a few cells at a time, not a whole ring: it reads as a smooth taper. */
export function coneStage(stages, y0, y) {
  if (y <= y0) return null
  for (const s of stages) if (y <= s.top) return s
  return null
}

const inT = (dx, dz, t) => dx * dx + dz * dz <= t

/** glassCone: a hollow round glass cone centred on (cx, cz) standing on a solid round `plinth` course at y0 (the
 *  first stage's outline): a one-block shell of `body` (a block or mix(); `base` for the courses up to `baseTop`, a
 *  dressed foot) round the outline of each course (stages [{t, top}]: the disc dx^2 + dz^2 <= t). Where a stage steps
 *  in, the cells it leaves are a ledge of `ledge` stairs rising inward (`ledgeOf(i)` overrides it for stage i: a
 *  dressed band), and the new outline corbels in over the hollow. The last stage is the neck (t 2: a 3 x 3 round a
 *  one-block flue, its corners `wall` so it reads round); over it a lip of upside-down `lipStairs` flaring out (t 5)
 *  round a solid cap, and a rim of `wall` round a lit campfire on the cap's middle (its smoke: the cone is the
 *  furnace's flue). `keep(x, y, z)` returns false for a cell the recipe keeps (a door). Returns {has(x, y, z): whether
 *  a cell lies inside the cone's outline (shell, ledge or hollow), inside(x, y, z): in the hollow, rim (the
 *  campfire's y)}. */
export function glassCone(c, cx, cz, { y0 = 0, stages, baseTop = y0 }, { plinth, base, body, ledge, lipStairs, wall, ledgeOf = () => null }, keep = () => true) {
  const put = (x, y, z, id, props) => { if (keep(x, y, z)) c.set(x, y, z, id, props) }
  const R = Math.floor(Math.sqrt(stages[0].t))
  const T0 = stages[0].t
  for (let dx = -R; dx <= R; dx++) for (let dz = -R; dz <= R; dz++) if (inT(dx, dz, T0)) put(cx + dx, y0, cz + dz, plinth)
  const last = stages[stages.length - 1].top
  const shell = (dx, dz, t) => inT(dx, dz, t) && [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([a, b]) => !inT(dx + a, dz + b, t))
  for (let y = y0 + 1; y <= last; y++) {
    const s = coneStage(stages, y0, y), sb = coneStage(stages, y0, y - 1)
    const i = stages.indexOf(s)
    const id = y <= baseTop ? base : body
    for (let dx = -R; dx <= R; dx++) for (let dz = -R; dz <= R; dz++) {
      const x = cx + dx, z = cz + dz
      if (sb && sb.t > s.t && inT(dx, dz, sb.t) && !inT(dx, dz, s.t)) { // the ledge round the step in
        if (shell(dx, dz, sb.t) && keep(x, y, z)) stair(c, x, y, z, ledgeOf(i) ?? ledge, inward(dx, dz))
        continue
      }
      if (!shell(dx, dz, s.t)) continue
      const corner = s.t <= 2 && Math.abs(dx) === 1 && Math.abs(dz) === 1
      put(x, y, z, corner ? wall : id)
    }
  }
  const lip = last + 1
  for (let dx = -2; dx <= 2; dx++) for (let dz = -2; dz <= 2; dz++) {
    if (inT(dx, dz, 2)) put(cx + dx, lip, cz + dz, body)
    else if (inT(dx, dz, 5) && keep(cx + dx, lip, cz + dz)) stair(c, cx + dx, lip, cz + dz, lipStairs, inward(dx, dz), 'top')
  }
  const rim = lip + 1
  for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) if (dx || dz) put(cx + dx, rim, cz + dz, wall)
  put(cx, rim, cz, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  const has = (x, y, z) => {
    const dx = x - cx, dz = z - cz
    if (y === lip) return inT(dx, dz, 5)
    if (y === rim) return inT(dx, dz, 2)
    const t = Math.max(coneStage(stages, y0, y)?.t ?? -1, coneStage(stages, y0, y - 1)?.t ?? -1)
    return t >= 0 && inT(dx, dz, t)
  }
  const inside = (x, y, z) => {
    const s = coneStage(stages, y0, y)
    return !!s && inT(x - cx, z - cz, s.t) && !shell(x - cx, z - cz, s.t)
  }
  return { has, inside, rim }
}

/** bullseye: a round crown-glass window in a wall plane (axis 'z': the wall runs along x at z = line; 'x': along z at
 *  x = line) centred on (u, y): a plus of five panes, the middle one `eye` (the bullseye's thick knot) and the four
 *  arms `ring`, its four corners `infill` so it reads round. Grown from the Glassblower's Workshop's front gable. */
export function bullseye(c, axis, line, u, y, { eye, ring, infill }) {
  const at = (du, dy, id) => { const [x, z] = axis === 'z' ? [u + du, line] : [line, u + du]; c.set(x, y + dy, z, id) }
  for (const du of [-1, 1]) for (const dy of [-1, 1]) at(du, dy, infill)
  for (const [du, dy] of [[-1, 0], [1, 0], [0, -1], [0, 1]]) at(du, dy, ring)
  at(0, 0, eye)
}

/** kilnSiege: the brick surround a furnace or kiln stands in (the job block at [x, y, z], facing `facing`, drawn by
 *  the recipe): `brick` cheeks either side under `slabId` slabs, and a breast behind it two high under a slab. The
 *  cells are fixtures, not furniture. */
export function kilnSiege(c, [x, y, z], facing, { brick, slabId }) {
  const back = { north: [0, 1], south: [0, -1], west: [1, 0], east: [-1, 0] }[facing]
  const side = back[0] ? [0, 1] : [1, 0]
  for (const s of [-1, 1]) {
    c.set(x + side[0] * s, y, z + side[1] * s, brick)
    slab(c, x + side[0] * s, y + 1, z + side[1] * s, slabId, 'bottom')
  }
  c.set(x + back[0], y, z + back[1], brick)
  c.set(x + back[0], y + 1, z + back[1], brick)
  slab(c, x + back[0], y + 2, z + back[1], slabId, 'bottom')
}

/** eyebrow: a drip mould over a window in a curved or flat wall: an upside-down stair one out from the wall at y
 *  (`out`: the side the wall faces), its back to the wall. */
export function eyebrow(c, [x, y, z], out, id) {
  const d = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[out]
  const back = { north: 'south', south: 'north', west: 'east', east: 'west' }[out]
  stair(c, x + d[0], y, z + d[1], id, back, 'top')
}
