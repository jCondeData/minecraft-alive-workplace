// The snowy Ranch's pitching dormer (ROADMAP 49.10e): a wall dormer in a STEEP (2:1) slope whose ridge runs along z,
// with its own closed little gable roof. Every block comes from the caller's palette.
//
// Round 3 (critic: "a notch cut in the slate with a white board floating over it"): no more steep stepped roof of
// full blocks and top stairs, no 3-layer cap standing a block over its stairs. The dormer is now a closed 45-degree
// gable of slate stairs meeting at one full ridge block, hooding the face by one block, its cheeks the main slope's own
// full blocks, its ridge running straight back into the slope where the slope reaches the ridge's height; the snow
// lies along that ridge (the caller lays it, two layers), on the roof, never a block over it. (Tried in renders and
// dropped: the face flush with no hood read as a birch hatch under a chimney; snow on every course read as steps.)
const ns = id => (id.includes(':') ? id : `minecraft:${id}`)

/** steepWallDormer: a five-wide gabled dormer centred on z in the slope rising from x = face toward the ridge (`run` 1:
 *  it rises east; -1: west), for a 2:1 main slope whose eave course sits at y0 on the face (one full block and a stair
 *  per column further in). Its face in the wall plane x = face: `infill` cheeks either side of two `glass` panes
 *  (y0, y0 + 1) and one infill block over the panes under the ridge. Its roof, three columns deep (the hood one block
 *  out from the face, the face, the one behind it): eave stairs at z +- 2 on y0 + 1, stairs at z +- 1 on y0 + 2, the
 *  ridge a full block on y0 + 3 over z;
 *  main-roof full blocks under its stairs are kept (they are the cheeks, flush to the slope). The two cells behind the
 *  panes open into the room. Returns the ridge's cells [[x, y, z]] (for the snow). */
export function steepWallDormer(c, { z, face, run = 1, y0 }, roof, { infill, glass }) {
  const full = ns(roof.full)
  const st = f => ({ facing: f, half: 'bottom', shape: 'straight', waterlogged: 'false' })
  const ridge = []
  for (let i = -1; i < 2; i++) {
    const x = face + i * run
    const put = (y, zz, f) => { if (c.name(x, y, zz) !== full) c.set(x, y, zz, roof.stairs, st(f)) }
    put(y0 + 1, z - 2, 'south'); put(y0 + 1, z + 2, 'north')
    c.set(x, y0 + 2, z - 1, roof.stairs, st('south')); c.set(x, y0 + 2, z + 1, roof.stairs, st('north'))
    c.set(x, y0 + 3, z, roof.full)
    ridge.push([x, y0 + 4, z])
  }
  for (const dz of [-1, 1]) for (let y = y0; y <= y0 + 1; y++) c.set(face, y, z + dz, infill)
  c.set(face, y0, z, glass); c.set(face, y0 + 1, z, glass)
  c.set(face, y0 + 2, z, infill)
  const back = face + run
  c.clear(back, y0 + 1, z); c.clear(back, y0 + 2, z)
  return ridge
}
