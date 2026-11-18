// Strongholds (ROADMAP 49, the Bandit Fort): a ragged stake wall with a fire-step behind it, an A-frame tent on a
// ridge pole, a watchtower standing on four legs, a stepped lean-to roof against a wall and a jettied timber band round
// a stone keep. Every block comes from the palette passed in; nothing here picks one.
//   stakeWall    a ring of upright stakes on the edge of a square, each stake's height from `height(i, x, z)`,
//                a pointed tip on each (`tip`), and an earth-and-stone fire-step one in from it (`bank` under `step`)
//   poleTent     an A-frame tent along z: canvas stepping up from both eaves to a ridge pole (a log along z, one
//                proud at each end on a fence upright), the back end closed, the mouth left with a flap
//   legTower     a watchtower on four corner legs: a deck on joists, a ladder up one leg's wall through the deck,
//                a parapet (boards on the outward sides, a rail on the inward), knee braces, a tented hat
//   stepLeanTo   a lean-to rising one block per two across (bottom slab, full block, bottom slab...) against a wall,
//                its two gable ends boarded up to the roof
//   jettyBand    a timber band jettied one block out round a keep's stone body: a log sill ring, posts and boards
import { slab, stair } from './canvas.mjs'
import { watchHat } from './palisade_kit.mjs'

const edgeCells = (x0, z0, x1, z1) => {
  const out = []
  for (let x = x0; x <= x1; x++) out.push([x, z0])
  for (let z = z0 + 1; z <= z1; z++) out.push([x1, z])
  for (let x = x1 - 1; x >= x0; x--) out.push([x, z1])
  for (let z = z1 - 1; z > z0; z--) out.push([x0, z])
  return out
}

/** stakeWall: stakes (`stake`, a log, or `stake(i, x, z)` choosing one per stake) from y 1 up to `height(i, x, z)` on the ring x0..x1, z0..z1 (`i` counts round
 *  the ring), a `tip` (pointed dripstone, tip up) on each when `tipped(i, x, z)`; `skip(x, z)` leaves a cell to others.
 *  The fire-step: one in from the ring, `bank` at y 1 and a bottom slab of `step` at y 2 where `stepAt(x, z)`. */
export function stakeWall(c, { x0, z0, x1, z1 }, { stake, tip, bank, step }, { height, tipped = () => true, skip = () => false, stepAt = () => true }) {
  const pick = typeof stake === 'function' ? stake : () => stake
  edgeCells(x0, z0, x1, z1).forEach(([x, z], i) => {
    if (skip(x, z)) return
    const h = height(i, x, z)
    for (let y = 1; y <= h; y++) c.set(x, y, z, pick(i, x, z), { axis: 'y' })
    if (tipped(i, x, z)) c.set(x, h + 1, z, tip, { tip_direction: 'up', thickness: 'tip', waterlogged: 'false' })
  })
  for (const [x, z] of edgeCells(x0 + 1, z0 + 1, x1 - 1, z1 - 1)) {
    if (!stepAt(x, z)) continue
    c.set(x, 1, z, bank)
    slab(c, x, 2, z, step)
  }
}

/** poleTent: an A-frame tent across x a0..a1 (odd), along z l0..l1, from y0: canvas (`canvas(x, y, z)` or an id)
 *  steps in a block per layer to a ridge `pole` (a log along z) on the middle column, the pole running one proud of
 *  each end onto a `fence` upright; carpet `hem` softens each step; the end at `back` ('l0' or 'l1') is closed, the
 *  other end gets canvas on its outer cells and a `flap` (an open trapdoor id) on one side of the mouth. Returns the
 *  floor cells inside. */
export function poleTent(c, { a0, a1, l0, l1, y0, back = 'l1' }, { canvas, pole, fence, hem = null, flap = null }) {
  const mid = (a0 + a1) / 2
  const at = typeof canvas === 'function' ? canvas : () => canvas
  const ridgeY = y0 + (mid - a0)
  for (let z = l0; z <= l1; z++) {
    for (let i = 0; a0 + i < mid; i++) {
      for (const x of [a0 + i, a1 - i]) {
        c.set(x, y0 + i, z, at(x, y0 + i, z))
        if (hem && a0 + i + 1 < mid) c.set(x, y0 + i + 1, z, hem)
      }
    }
  }
  for (let z = l0 - 1; z <= l1 + 1; z++) c.set(mid, ridgeY, z, pole, { axis: 'z' })
  for (const z of [l0 - 1, l1 + 1]) for (let y = y0; y < ridgeY; y++) c.set(mid, y, z, fence)
  const shut = back === 'l0' ? l0 : l1
  const mouth = back === 'l0' ? l1 : l0
  for (let i = 0; a0 + i < mid; i++) for (let x = a0 + i + 1; x < a1 - i; x++) c.set(x, y0 + i, shut, at(x, y0 + i, shut))
  // the mouth: canvas on the outer cells of the lowest layer, a flap hung back on one side
  c.set(a0 + 1, y0, mouth, at(a0 + 1, y0, mouth))
  if (flap) {
    const open = { half: 'bottom', open: 'true', powered: 'false', waterlogged: 'false' }
    c.set(a1 - 1, y0, mouth, flap, { facing: 'west', ...open })
    for (let y = y0; y < ridgeY; y++) c.set(mid, y, mouth, flap, { facing: 'west', ...open }) // tied back, the way in
  }
  const inside = []
  for (let x = a0 + 1; x < a1; x++) for (let z = l0; z <= l1; z++) if (z !== shut) inside.push([x, y0, z])
  return { ridgeY, inside }
}

/** legTower: a watchtower over x0..x1, z0..z1: corner legs (`leg`, a log) from y 1 to `topY`, a deck of `deck` at
 *  `deckY` on `joist` logs (along x at deckY - 1 under the two z edges), a ladder at `ladder` [x, z, facing] from y 1
 *  up through the deck, a parapet at deckY + 1 (`board` on the sides in `outward`, a `rail` fence on the others),
 *  knee braces (upside-down `brace` stairs) under the deck against each leg on the inward sides, and a tented hat of
 *  `roof` (capped with `cap`) from topY + 1. */
export function legTower(c, { x0, z0, x1, z1, deckY, topY, ladder, outward = [] }, { leg, deck, joist, board, rail, brace, roof, cap, hatch = null }) {
  for (const [x, z] of [[x0, z0], [x1, z0], [x0, z1], [x1, z1]]) for (let y = 1; y <= topY; y++) c.set(x, y, z, leg, { axis: 'y' })
  for (let x = x0 + 1; x < x1; x++) for (const z of [z0, z1]) c.set(x, deckY - 1, z, joist, { axis: 'x' })
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) if (!c.get(x, deckY, z)) c.set(x, deckY, z, deck)
  const [lx, lz, lf] = ladder
  for (let y = 1; y <= deckY; y++) c.set(lx, y, lz, 'ladder', { facing: lf, waterlogged: 'false' })
  // with `hatch`, the deck's opening is a trapdoor standing open over the ladder (climbable, and it shuts the shed below)
  if (hatch) c.set(lx, deckY, lz, hatch, { facing: lf, half: 'bottom', open: 'true', powered: 'false', waterlogged: 'false' })
  const side = (x, z) => (z === z0 ? 'north' : z === z1 ? 'south' : x === x0 ? 'west' : 'east')
  for (const [x, z] of edgeCells(x0, z0, x1, z1)) {
    if ((x === x0 || x === x1) && (z === z0 || z === z1)) continue
    c.set(x, deckY + 1, z, outward.includes(side(x, z)) ? board : rail)
  }
  // knee braces on the inward faces, under the joists, against each inward leg (their full half up toward the leg)
  const kb = deckY - 2
  if (!outward.includes('south')) { stair(c, x0 + 1, kb, z1, brace, 'west', 'top'); stair(c, x1 - 1, kb, z1, brace, 'east', 'top') }
  if (!outward.includes('north')) { stair(c, x0 + 1, kb, z0, brace, 'west', 'top'); stair(c, x1 - 1, kb, z0, brace, 'east', 'top') }
  if (!outward.includes('east')) { stair(c, x1, kb, z0 + 1, brace, 'north', 'top'); stair(c, x1, kb, z1 - 1, brace, 'south', 'top') }
  if (!outward.includes('west')) { stair(c, x0, kb, z0 + 1, brace, 'north', 'top'); stair(c, x0, kb, z1 - 1, brace, 'south', 'top') }
  return watchHat(c, { x0, z0, x1, z1, y: topY + 1 }, roof, cap)
}

/** stepLeanTo: a lean-to over x from `low` to `high` (the wall side), z0..z1 (`over` blocks of overhang past each end), rising
 *  from `eaveY`: a bottom slab, then a full block at the same y, then up one. The gable ends (z0 and z1) are boarded
 *  with `board` from y 1 to under each column's roof cell. Returns each column's roof y. */
export function stepLeanTo(c, { low, high, z0, z1, eaveY, over = 1 }, { full, slab: slabId, board }) {
  const dir = high > low ? 1 : -1
  const ys = {}
  for (let k = 0, x = low; dir > 0 ? x <= high : x >= high; k++, x += dir) {
    const y = eaveY + Math.floor(k / 2)
    ys[x] = y
    for (let z = z0 - over; z <= z1 + over; z++) k % 2 ? c.set(x, y, z, full) : slab(c, x, y, z, slabId)
    if (x !== low) for (const z of [z0, z1]) for (let yy = 1; yy < y; yy++) c.set(x, yy, z, board)
  }
  return ys
}

/** jettyBand: round a keep whose stone body is x0..x1, z0..z1, a timber band one block out (x0 - 1..x1 + 1, z0 - 1 and
 *  z1 + `back`; back 0 keeps the back flush): a sill ring of `sill` logs at y, and at y + 1 `post` logs every `bay`
 *  with `board` between; the floor inside the sill at y gets `floorId`. */
export function jettyBand(c, { x0, z0, x1, z1, y, bay = 3, back = 1 }, { sill, post, board, floorId }) {
  const X0 = x0 - 1, X1 = x1 + 1, Z0 = z0 - 1, Z1 = z1 + back
  for (let x = X0; x <= X1; x++) for (let z = Z0; z <= Z1; z++) {
    const edge = x === X0 || x === X1 || z === Z0 || z === Z1
    if (edge) c.set(x, y, z, sill, { axis: x === X0 || x === X1 ? 'z' : 'x' })
    else c.set(x, y, z, floorId)
  }
  for (const [x, z] of edgeCells(X0, Z0, X1, Z1)) {
    const corner = (x === X0 || x === X1) && (z === Z0 || z === Z1)
    const onBay = (x === X0 || x === X1) ? (z - Z0) % bay === 0 : (x - X0) % bay === 0
    c.set(x, y + 1, z, corner || onBay ? post : board, corner || onBay ? { axis: 'y' } : {})
  }
}
