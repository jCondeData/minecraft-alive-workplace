// The snowy Ranch's pitching dormer (ROADMAP 49.10e): a wall dormer in a STEEP (2:1) slope whose ridge runs along z,
// with its own closed little gable roof. Every block comes from the caller's palette.

const ns = id => (id.includes(':') ? id : `minecraft:${id}`)

/** steepWallDormer: a five-wide wall dormer centred on z, its face in the wall plane x = face, in the slope rising
 *  from there toward the ridge (`run` 1: it rises east; -1: west). Round 3: a closed little gable, flush with the wall
 *  like the barn's own gables: on the face two `glass` panes (y0, y0 + 1) between `infill` cheeks; its roof slate
 *  stairs meeting at a ridge in a point over the panes: eaves at z +- 2 on y0 + 1, stairs at z +- 1 on y0 + 2, the
 *  ridge a full block on y0 + 2 over z, flush with the tops of those stairs, so snow lies on it as layers and never
 *  stands a block proud. Two rows deep (face and face + run), it meets the main slope at face + 2 run. Main-roof full
 *  blocks under its eaves are kept; the cell behind the panes is opened into the room, closed above by the ridge. */
export function steepWallDormer(c, { z, face, run = 1, y0 }, roof, { infill, glass }) {
  const full = ns(roof.full)
  const st = f => ({ facing: f, half: 'bottom', shape: 'straight', waterlogged: 'false' })
  for (const x of [face, face + run]) {
    for (const [dz, f] of [[-2, 'south'], [2, 'north']]) if (c.name(x, y0 + 1, z + dz) !== full) c.set(x, y0 + 1, z + dz, roof.stairs, st(f))
    c.set(x, y0 + 2, z - 1, roof.stairs, st('south')); c.set(x, y0 + 2, z + 1, roof.stairs, st('north'))
    c.set(x, y0 + 2, z, roof.full)
  }
  for (const dz of [-1, 1]) for (let y = y0; y <= y0 + 1; y++) c.set(face, y, z + dz, infill)
  c.set(face, y0, z, glass); c.set(face, y0 + 1, z, glass)
  c.clear(face + run, y0 + 1, z)
}
