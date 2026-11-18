// Post offices (parts library, grown from the Signal Tower post office, ROADMAP 51): a wall of sorting pigeonholes,
// exposed tie beams across a hall, a street pillar box, a spire's slender tip. Every block comes from the caller's palette.

/** pigeonholes: a sorting wall of `block` (a chiseled bookshelf reads as rows of pigeonholes) at each [x, z] of
 *  `cells` from y0 to y1, its face toward `facing` (into the room). A fixture: no furniture spot. */
export function pigeonholes(c, cells, y0, y1, facing, block) {
  const props = { facing }
  if (block.endsWith('chiseled_bookshelf')) for (let i = 0; i < 6; i++) props[`slot_${i}_occupied`] = 'false'
  for (const [x, z] of cells) for (let y = y0; y <= y1; y++) { c.free([x, y, z]); c.set(x, y, z, block, props) }
}

/** tieBeams: exposed beams (`log`) across a hall at y, one along z (z0..z1) at each x of `xs`, their ends in the
 *  walls or roof: the lamps hang from them. */
export function tieBeams(c, xs, z0, z1, y, log) {
  for (const x of xs) for (let z = z0; z <= z1; z++) c.set(x, y, z, log, { axis: 'z' })
}

/** pillarBox: a street post box (decoration, not a job block): a two-high `body` on a `pad` at y - 1, a `cap` slab
 *  on top. */
export function pillarBox(c, [x, y, z], { body, cap, pad }) {
  c.set(x, y - 1, z, pad)
  c.set(x, y, z, body)
  c.set(x, y + 1, z, body)
  c.set(x, y + 2, z, cap, { type: 'bottom', waterlogged: 'false' })
}

/** spireTip: a spire's point made slender: the full block at (x, y, z) (a pyramid's top) becomes a thin `post` (a
 *  wall block), the cell over it a `vane` (a lightning rod reads as a copper weathervane), anything above cleared. */
export function spireTip(c, x, y, z, post, vane) {
  for (let yy = y; yy < c.size[1]; yy++) c.clear(x, yy, z)
  c.set(x, y, z, post, { up: 'true', north: 'none', south: 'none', east: 'none', west: 'none', waterlogged: 'false' })
  if (y + 1 < c.size[1]) c.set(x, y + 1, z, vane, { facing: 'up', powered: 'false', waterlogged: 'false' })
}
