// Clock towers (parts library, tools/blueprints/REBUILD.md; grown from the Clock Tower, ROADMAP 51.6): a spout race
// (a stone pier with a cup on top where ONE water source is poured, its fall down a tower's corner past a water wheel
// into a closed sump), a clock face set in a wall inside a dial surround, a bell-cast spire (a 45-degree flare that
// turns into a steep needle) and a kinetic train drawn from a list. Every block comes from the caller's palette; the
// Create parts use foundry.mjs's helpers (ids and states checked in libs/create-fabric-6.0.10.0+mc1.21.1.jar).
import { stair } from './canvas.mjs'
import { shaftRun, gearbox, waterWheel } from './foundry.mjs'

/** fallRace: water that falls down a wall past a wheel. `col` [x, z] is the fall's column against a solid wall behind
 *  it (Create reads a downward flow only beside a solid face); `sumpY` its closed sump (a floor under it, `walls` on
 *  the sides the caller lists in `sumpSides` ([x, z] cells), `topY` the poured source, closed on the four sides by
 *  `cup` cells ([x, z]) at topY and open above (a source that cannot fall any more spreads sideways). `corbels`:
 *  [{at: [x, z], facing}] upside-down `stairs` under the cup's free-hanging blocks. Draws the water as it settles
 *  (source level 0, the fall and the sump level 8): only the source is placed by a builder. */
export function fallRace(c, { col: [cx, cz], sumpY, topY, cup, sumpSides, corbels = [] }, { wall, floorId, stairs }) {
  c.set(cx, sumpY - 1, cz, floorId)
  for (const [x, z] of sumpSides) c.set(x, sumpY, z, wall)
  for (const [x, z] of cup) c.set(x, topY, z, wall)
  for (const { at: [x, z], facing } of corbels) stair(c, x, topY - 1, z, stairs, facing, 'top')
  c.set(cx, topY, cz, 'water', { level: '0' })
  for (let y = sumpY; y < topY; y++) c.set(cx, y, cz, 'water', { level: '8' })
}

/** dialFace: a clock face in a wall: `clock` (create:cuckoo_clock, facing out) at [x, y, z] in a 3 x 3 `surround`
 *  along the wall (`along` 'x' or 'z'; `surroundProps` its state, e.g. a log's axis), and a top-half `hood` slab over
 *  it one out (null: none). */
export function dialFace(c, [x, y, z], facing, along, { surround, surroundProps = {}, hood = null }) {
  for (let u = -1; u <= 1; u++) for (let v = -1; v <= 1; v++) {
    if (u === 0 && v === 0) continue
    c.set(along === 'x' ? x + u : x, y + v, along === 'z' ? z + u : z, surround, surroundProps)
  }
  c.set(x, y, z, 'create:cuckoo_clock', { facing })
  if (hood) {
    const out = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[facing]
    c.set(x + out[0], y + 2, z + out[1], hood, { type: 'top', waterlogged: 'false' })
  }
}

/** bellcastSpire: a square roof centred on (cx, cz): `flare` rings of stairs at 45 degrees from `half` out at y0
 *  (the eave), each a block in and a block up; then the steep needle, each ring inward a full block under a stair (two
 *  up for one in), and a full block on the centre with `apex` ([id, props]) on it. `keep(x, y, z)` as in gableRoof.
 *  Returns the centre block's y. */
export function bellcastSpire(c, cx, cz, half, y0, flare, roof, { apex = null, keep = () => true } = {}) {
  const facing = (dx, dz) => (Math.abs(dz) >= Math.abs(dx) ? (dz < 0 ? 'south' : 'north') : (dx < 0 ? 'east' : 'west'))
  const ring = (s, y, f) => {
    for (let dx = -s; dx <= s; dx++) for (let dz = -s; dz <= s; dz++)
      if (Math.max(Math.abs(dx), Math.abs(dz)) === s && keep(cx + dx, y, cz + dz)) f(cx + dx, y, cz + dz, dx, dz)
  }
  let y = y0, s = half
  for (let i = 0; i < flare; i++, s--, y++) ring(s, y, (x, yy, z, dx, dz) => stair(c, x, yy, z, roof.stairs, facing(dx, dz)))
  for (; s >= 1; s--, y += 2) {
    ring(s, y, (x, yy, z) => c.set(x, yy, z, roof.full))
    ring(s, y + 1, (x, yy, z, dx, dz) => stair(c, x, yy, z, roof.stairs, facing(dx, dz)))
  }
  c.set(cx, y, cz, roof.full)
  if (apex) c.set(cx, y + 1, cz, apex[0], apex[1] ?? {})
  return y
}

/** kineticTrain: a Create drive from a list: {wheel: {at, facing}, shafts: [{cells, axis}], gearboxes: [{at, axis}]}. */
export function kineticTrain(c, { wheel, shafts, gearboxes }) {
  if (wheel) waterWheel(c, wheel.at, wheel.facing)
  for (const s of shafts) shaftRun(c, s.cells, s.axis)
  for (const g of gearboxes) gearbox(c, g.at, g.axis)
}
