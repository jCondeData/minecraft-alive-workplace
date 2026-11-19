// Railway stations (parts library, tools/blueprints/REBUILD.md): a straight run of track on its ballast, the timber
// trusses of a train shed (a tie beam high over the line, a king post to the ridge, brackets at the walls), a glazed
// lantern along a ridge, a clock face in a tower's wall and a waiting bench that records its seats. Grown from the
// Train Station (49/50.13). Every block comes from the family's palette; none is picked here.
import { OPP, stair } from './canvas.mjs'

/** trackRun: straight, level track along `axis` ('x' or 'z') on line `line`, from a0 to a1 at y, each piece on a cell
 *  of `ballast` (a block or a mix()) one below. `track`: the track's id; its shape is 'xo' or 'zo' (Create's straight
 *  pieces), never turned. Returns the cells of track. */
export function trackRun(c, { axis, line, a0, a1, y }, { track, ballast }) {
  const cells = []
  for (let u = a0; u <= a1; u++) {
    const [x, z] = axis === 'x' ? [u, line] : [line, u]
    if (ballast) c.set(x, y - 1, z, ballast)
    c.set(x, y, z, track, { shape: axis === 'x' ? 'xo' : 'zo', turn: 'false', waterlogged: 'false' })
    cells.push([x, y, z])
  }
  return cells
}

/** shedTruss: one timber truss across a shed whose ridge runs along x, at column x: a tie beam along z from z0 to z1
 *  at `tieY`, a king post from tieY + 1 up under the ridge (`kingTop`) at `ridgeZ`, struts (upside-down `brace`
 *  stairs) either side of the king post one over the tie, and brackets (upside-down `brace` stairs) under the tie's
 *  ends against the walls (at z0 and z1, one under the tie) when `brackets`. */
export function shedTruss(c, { x, z0, z1, tieY, ridgeZ, kingTop, brackets = true }, { beam, brace }) {
  for (let z = z0; z <= z1; z++) c.set(x, tieY, z, beam, { axis: 'z' })
  for (let y = tieY + 1; y <= kingTop; y++) c.set(x, y, ridgeZ, beam, { axis: 'y' })
  stair(c, x, tieY + 1, ridgeZ - 1, brace, 'south', 'top')
  stair(c, x, tieY + 1, ridgeZ + 1, brace, 'north', 'top')
  if (brackets) {
    stair(c, x, tieY - 1, z0, brace, 'north', 'top')
    stair(c, x, tieY - 1, z1, brace, 'south', 'top')
  }
}

/** ridgeLantern: a glazed lantern along a ridge that runs along x, from x0 to x1, its ridge column at z = zc: the ridge
 *  under it opened (from x0 + 1 to x1 - 1), `glass` either side one over the ridge, `end` walls at x0 and x1, and a
 *  cap of `cap` ({full, stairs}) one higher: stairs either side rising to a full block over the middle. */
export function ridgeLantern(c, { x0, x1, zc, y }, { glass, end, cap }) {
  for (let x = x0; x <= x1; x++) {
    const inner = x > x0 && x < x1
    if (inner) c.clear(x, y, zc)
    for (const z of [zc - 1, zc + 1]) c.set(x, y + 1, z, inner ? glass : end)
    if (!inner) c.set(x, y + 1, zc, end)
    stair(c, x, y + 2, zc - 1, cap.stairs, 'south')
    stair(c, x, y + 2, zc + 1, cap.stairs, 'north')
    c.set(x, y + 2, zc, cap.full)
  }
}

/** clockFace: a three-by-three clock set into a wall plane, centred on (x, y, z), the wall along `along` ('x' or 'z'):
 *  a `face` round a `hand` boss at the centre, so it reads as a dial from the road; `rim`, when given, takes the four corners. */
export function clockFace(c, { x, y, z, along }, { face, hand, rim = null }) {
  for (let a = -1; a <= 1; a++) for (let b = -1; b <= 1; b++) {
    const [cx, cz] = along === 'x' ? [x + a, z] : [x, z + a]
    const corner = a !== 0 && b !== 0
    const isHand = a === 0 && b === 0
    c.set(cx, y + b, cz, isHand ? hand : corner && rim ? rim : face)
  }
}

/** waitingBench: stair seats in `cells` whose sitters face `facing` (the backrest on the other side), each recorded as
 *  a 'bench' furniture spot; with `back` (a wood), an open trapdoor stands one up behind each seat as a slatted back. */
export function waitingBench(c, cells, facing, { seat, back = null, wood = null }) {
  const behind = { north: [0, 1], south: [0, -1], west: [1, 0], east: [-1, 0] }[facing]
  for (const [x, y, z] of cells) {
    c.free([x, y, z])
    stair(c, x, y, z, seat, OPP[facing])
    if (back) c.set(x + behind[0], y + 1, z + behind[1], `${back}_trapdoor`, { facing, half: 'bottom', open: 'true', powered: 'false', waterlogged: 'false' })
    c.spot('bench', [x, y, z], facing, [[0, 0, 0]], wood)
  }
}

/** pediment: a little gabled hood over a door in a wall along x: stairs at x - 1 and x + 1 rising to a full block over
 *  the door at y, one out (z) from the wall. */
export function stationPediment(c, x, y, z, { full, stairs }) {
  stair(c, x - 1, y, z, stairs, 'east')
  stair(c, x + 1, y, z, stairs, 'west')
  c.set(x, y, z, full)
}

