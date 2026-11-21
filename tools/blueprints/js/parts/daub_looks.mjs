// The Daub House's biome looks (ROADMAP 49.10d, as 49.10a): the shipped Daub House's kept insides, drawn into any
// look's shell (parts library, tools/blueprints/REBUILD.md). A look is the same building: these are the cells it may not
// move. Every look draws its own shell first (walls, floors' edges, roofs), leaving the rooms empty; then daubInterior
// puts in the hall and kitchen floors, the loft floor with its stairwell, the flight, the joined loft railing, the front
// door, the kitchen doorway and every furniture piece of ../house_t1_b/plan.mjs in its cell (the spots, kinds and
// facings as shipped; a hanging lamp's chain reaches whatever ceiling the look puts over it). Not exported from
// index.mjs (import it from here): only the Daub House's looks use it.
//
// The kept envelope (every look's shell must leave these clear and close them in):
//   Hall    ground room x 2..6, z 3..9, y 1..3, its walls on x 1 and 7, z 2 and 10 (the door at (4, 1, 2), the hearth's
//           campfire at (4, 1, 10) in the back wall, the west wall solid at (1, 3, 4) for the hall's banner)
//   Loft    floor y 4 over x 2..6, z 2..10, room y 5..7 at least where the plan's pieces stand (lamps at (4, 7, 4) and
//           (4, 7, 8)), its walls on x 1 and 7, z 1 and 11
//   Kitchen room x 8..10, z 4..8, y 1..4, its walls on x 11, z 3 and 9, the doorway (7, 1..2, 5)
//   Railing spruce fences at (5, 5, 5..8) and (6, 5, 5): the only fences a look may put at y 4 or above (the Daub
//           House's tests read every fence that high as the loft railing, one joined run)
import { FURNITURE, OPENINGS } from '../house_t1_b/plan.mjs'
import { flight } from './stairs.mjs'
import { door } from './walls.mjs'
import { furnitureParts } from './furniture.mjs'
import { hangTo } from './manor_cold.mjs'

export const DAUB_FURNITURE = FURNITURE
export const DAUB_RAILING = [[5, 5, 5], [5, 5, 6], [5, 5, 7], [5, 5, 8], [6, 5, 5]]
export const DAUB_HALL = { x0: 2, x1: 6, z0: 3, z1: 9 }
export const DAUB_LOFT = { x0: 2, x1: 6, z0: 2, z1: 10, y: 4 }
export const DAUB_KITCHEN = { x0: 8, x1: 10, z0: 4, z1: 8 }

/** daubInterior: the kept insides (see above). P: {floor, upperFloor, kitchenFloor, under (the solid under the
 *  flight's steps)}. `step`: the stair before the door ({at, id}) or null. */
export function daubInterior(c, P, { step = null } = {}) {
  const H = DAUB_HALL, L = DAUB_LOFT, K = DAUB_KITCHEN
  c.fill(H.x0, 0, H.z0, H.x1, 0, H.z1, P.floor)
  c.fill(K.x0, 0, K.z0, K.x1, 0, K.z1, P.kitchenFloor)
  c.fill(L.x0, L.y, L.z0, L.x1, L.y, L.z1, P.upperFloor)
  const st = OPENINGS.stairs
  for (const [x, y, z] of st.well) c.clear(x, y, z)
  flight(c, st.flight, st.rises, 'spruce', P.under)
  for (const [x, y, z] of DAUB_RAILING) c.set(x, y, z, 'spruce_fence')
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: 'spruce' })
  if (step) c.set(step.at[0], step.at[1], step.at[2], step.id, { facing: 'south', half: 'bottom', shape: 'straight', waterlogged: 'false' })
  const kd = OPENINGS.kitchenDoorway
  for (let y = kd.at[1]; y < kd.at[1] + kd.height; y++) c.clear(kd.at[0], y, kd.at[2])
  for (const f of FURNITURE) furnitureParts[f.part](c, f.part === 'hangingLamp' ? hangTo(c, f) : f)
}

/** daubSpots: the canvas's furniture spots with cells relative to each spot's pos (furniture.json's form). */
export function daubSpots(c) {
  return c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
}
