// Raiders' stockades and lock-ups (parts library, tools/blueprints/REBUILD.md; grown from the Raiders' Stockade, 49):
// a serrated ring of stakes, a lever-worked iron door in a jamb (wired so the lever really opens it), a lookout
// standing on four posts over whatever is under it, and a lashing rail along a wall's inner face. Every block comes
// from the caller's palette; none is picked here.
//   serratedStakes  upright stakes along a list of [x, z] cells, a repeating height rhythm with spikes on the tall ones
//   leverDoor       an iron door in a wall, a post jamb beside it and a lever on the jamb's outer face at head height
//   stiltLookout    four corner posts, a top plate, a deck at deckY and a rail round it (with a gap for a ladder)
//   innerWale       a lashing rail (a log lying along the wall) on the inner face of a stake wall, only in empty cells

/** serratedStakes: along `cells` ([x, z], in wall order), a stake of `log` (axis y) from y0 up to y0 + h - 1, h taken
 *  from `rhythm` (an array of {h, tip}) by the cell's index; a stake with tip gets `tip` (a fence, a skull...) on top,
 *  with `tipProps`. `override(i, x, z)` may return {h, tip} for a cell (a corner post, a stake by the gate). */
export function serratedStakes(c, cells, y0, rhythm, { log, tip, tipProps = {} }, override = () => null) {
  cells.forEach(([x, z], i) => {
    const r = override(i, x, z) ?? rhythm[i % rhythm.length]
    for (let y = y0; y < y0 + r.h; y++) c.set(x, y, z, log, { axis: 'y' })
    if (r.tip) {
      const [id, props] = typeof r.tip === 'string' ? [r.tip, r.props ?? {}] : [tip, tipProps]
      c.set(x, y0 + r.h, z, id, props)
    }
  })
}

/** leverDoor: an iron door (`door`) with its lower half at [x, y, z] in a wall running along `wall` ('x' or 'z'),
 *  shut; a jamb post of `jamb` (a log, axis y) on the side `jambSide` (+1 or -1 along the wall) from y to y + 2; and a
 *  lever on the jamb's `out` face (north, south, west or east) at y + 1. The lever powers the jamb it is fixed to, and
 *  the jamb touches the door's upper half, so flipping it opens the door (a lever only powers the block it hangs on
 *  and the blocks beside itself). Returns the lever's cell. */
export function leverDoor(c, [x, y, z], { wall, jambSide = 1, out, hinge = 'left' }, { door, jamb }) {
  for (const half of ['lower', 'upper'])
    c.set(x, y + (half === 'lower' ? 0 : 1), z, door, { facing: out, half, hinge, open: 'false', powered: 'false' })
  const [jx, jz] = wall === 'x' ? [x + jambSide, z] : [x, z + jambSide]
  for (let yy = y; yy <= y + 2; yy++) c.set(jx, yy, jz, jamb, { axis: 'y' })
  const step = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[out]
  const lever = [jx + step[0], y + 1, jz + step[1]]
  c.set(...lever, 'lever', { face: 'wall', facing: out, powered: 'false' })
  return lever
}

/** stiltLookout: a lookout over (x0, z0)..(x1, z1): `post` logs at the four corners from y0 to topY, a top plate of
 *  `post` logs lying round the corners at topY, a `deck` floor at deckY inside the posts' ring (the posts stay), and
 *  a `rail` (fence) round the deck at deckY + 1 between the posts, leaving the cells in `gaps` ([x, z]) open. */
export function stiltLookout(c, { x0, z0, x1, z1 }, { y0, deckY, topY }, { post, deck, rail }, gaps = []) {
  const corner = (x, z) => (x === x0 || x === x1) && (z === z0 || z === z1)
  for (const x of [x0, x1]) for (const z of [z0, z1]) for (let y = y0; y <= topY; y++) c.set(x, y, z, post, { axis: 'y' })
  for (let x = x0 + 1; x < x1; x++) for (const z of [z0, z1]) c.set(x, topY, z, post, { axis: 'x' })
  for (let z = z0 + 1; z < z1; z++) for (const x of [x0, x1]) c.set(x, topY, z, post, { axis: 'z' })
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) if (!corner(x, z)) c.set(x, deckY, z, deck)
  const open = new Set(gaps.map(([x, z]) => `${x},${z}`))
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    const edge = x === x0 || x === x1 || z === z0 || z === z1
    if (edge && !corner(x, z) && !open.has(`${x},${z}`)) c.set(x, deckY + 1, z, rail)
  }
}

/** innerWale: a lashing rail of `log` lying along the wall at y over `cells` ([x, z]); `axis` the wall's direction.
 *  Only empty cells get one (it stops at anything already drawn: a ladder, a tower, a stack of spoils). */
export function innerWale(c, cells, y, log, axis) {
  for (const [x, z] of cells) if (!c.get(x, y, z)) c.set(x, y, z, log, { axis })
}
