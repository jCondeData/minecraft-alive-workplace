// The snowy Ranch's pitching dormer (ROADMAP 49.10e): a wall dormer in a STEEP (2:1) slope whose ridge runs along z,
// with its own steep little gable roof. Every block comes from the caller's palette.
import { stair } from './canvas.mjs'

const ns = id => (id.includes(':') ? id : `minecraft:${id}`)

/** steepWallDormer: a five-wide steep gabled dormer centred on z in the slope rising from x = face toward the ridge
 *  (`run` 1: it rises east; -1: west). Its face in the wall plane x = face: `infill` cheeks either side of two `glass`
 *  panes (y0, y0 + 1) and two infill blocks over them to the apex; its roof (eaves at z +- 2 on y0 + 1, a full course
 *  and a stair two up at z +- 1, the ridge on y0 + 4 over z) runs from one block outside the face (that row's full
 *  blocks upside-down stairs, one clean verge) two blocks back into the main slope. Main-roof full blocks under its
 *  eave stairs are kept; the two cells behind the panes are opened into the room, the one under its ridge closed. */
export function steepWallDormer(c, { z, face, run = 1, y0 }, roof, { infill, glass }) {
  const full = ns(roof.full)
  const out = run > 0 ? 'west' : 'east'
  for (let i = 0; i < 3; i++) {
    const x = face - run + i * run
    const verge = i === 0
    const put = (y, zz, id, props) => {
      if (props && c.name(x, y, zz) === full) return // a full main-roof block under the eave stays
      c.set(x, y, zz, id, props ?? {})
    }
    const st = (f, half = 'bottom') => ({ facing: f, half, shape: 'straight', waterlogged: 'false' })
    put(y0 + 1, z - 2, roof.stairs, st('south')); put(y0 + 1, z + 2, roof.stairs, st('north'))
    if (verge) { stair(c, x, y0 + 2, z - 1, roof.stairs, 'north', 'top'); stair(c, x, y0 + 2, z + 1, roof.stairs, 'south', 'top') }
    else { c.set(x, y0 + 2, z - 1, roof.full); c.set(x, y0 + 2, z + 1, roof.full) }
    c.set(x, y0 + 3, z - 1, roof.stairs, st('south')); c.set(x, y0 + 3, z + 1, roof.stairs, st('north'))
    c.set(x, y0 + 4, z, roof.full)
  }
  for (const dz of [-1, 1]) for (let y = y0; y <= y0 + 1; y++) c.set(face, y, z + dz, infill)
  c.set(face, y0, z, glass); c.set(face, y0 + 1, z, glass)
  c.set(face, y0 + 2, z, infill); c.set(face, y0 + 3, z, infill)
  const back = face + run
  c.clear(back, y0 + 1, z); c.clear(back, y0 + 2, z)
  c.set(back, y0 + 3, z, infill)
  return out
}
