// Wall kits (ROADMAP 52.3, the Palisade kit: palisade, palisade_gate, palisade_tower): the parts a wall piece is drawn
// from. Every block comes from the palette passed in; nothing here picks one.
//   extrude      one cross-section drawn on every column of a run: a wall segment that agrees with itself where two
//                copies overlap at any shift (Walls.run spreads whole segments and lets them overlap)
//   stakeBed     a row of upward pointed stakes, each on a sturdy bed block (a ditch a blueprint can't dig)
//   fenceGates   shut fence gates across a gateway, their panels along x (the way through runs along z)
//   gateGable    a 45-degree gable over a gateway (ridge along z, gables to the front and back), the gable filled with
//                boards round a king post, the ridge capped with a slab (no flat top an archer would be set on)
//   stockade     a ring of upright stakes round a square, corners left for the posts
//   watchHat     a tented hat over a square lookout, its apex a bottom slab (a cap no station can stand on); with
//                `top`, rings stop there and the middle left at the top gets the cap
//   setBackPanels  boards hung on the inner side of a square's wall cells (open trapdoors facing out): the panels sit
//                13/16 back from the posts and beams round them, and stop a mob like a wall
//   ringFacing   which way a cell on a square's edge looks out (north, south, west, east)
import { slab } from './canvas.mjs'
import { gableRoof } from './roofs.mjs'
import { boxPyramidRoof as tent } from './library_nave.mjs'

/** extrude: draw `section` ([z, y, id, props]) on every column x0..x1. */
export function extrude(c, x0, x1, section) {
  for (let x = x0; x <= x1; x++) for (const [z, y, id, props = {}] of section) c.set(x, y, z, id, props)
}

/** stakeBed: at each [x, z], `bed` at y and an upward pointed stake on it at y + 1. */
export function stakeBed(c, cells, y, { bed, stake }) {
  for (const [x, z] of cells) {
    c.set(x, y, z, bed)
    c.set(x, y + 1, z, stake, { tip_direction: 'up', thickness: 'tip', waterlogged: 'false' })
  }
}

/** fenceGates: shut gates at each x of `xs` on row z (facing south or north: the panel spans x). */
export function fenceGates(c, xs, y, z, gate, facing = 'south') {
  for (const x of xs) c.set(x, y, z, gate, { facing, open: 'false', in_wall: 'false', powered: 'false' })
}

/** gateGable: a 45-degree gable roof over x0..x1 (odd span), running z0..z1, eaves at eaveY; under it, on each of the
 *  gable lines `faces` (z values), boards or logs (`infill`, with `infillProps`) round a king post (`king`) on the middle column from eaveY up;
 *  the ridge gets `cap` as a bottom slab. Returns the ridge's y. */
export function gateGable(c, { x0, x1, z0, z1, eaveY, faces = [z0, z1] }, roof, { infill, infillProps = {}, king, cap }) {
  const { ridgeY, under } = gableRoof(c, { axis: 'z', across: [x0, x1], along: [z0, z1], eaveY, steep: false }, roof)
  const mid = (x0 + x1) / 2
  for (const z of faces) for (let x = x0 + 1; x < x1; x++) for (let y = eaveY; y < under[x]; y++)
    c.set(x, y, z, x === mid ? king : infill, x === mid ? { axis: 'y' } : infillProps)
  for (let z = z0; z <= z1; z++) slab(c, mid, ridgeY, z, cap)
  return ridgeY
}

/** stockade: upright stakes (`log`, axis y) y0..y1 along the four sides of x0..x1, z0..z1, corners left out (the
 *  posts go there); `skip(x, y, z)` leaves a cell (a way in); `props` {} for a block with no axis (a stone foot). */
export function stockade(c, { x0, z0, x1, z1 }, y0, y1, log, skip = () => false, props = { axis: 'y' }) {
  const ring = []
  for (let x = x0 + 1; x < x1; x++) ring.push([x, z0], [x, z1])
  for (let z = z0 + 1; z < z1; z++) ring.push([x0, z], [x1, z])
  for (const [x, z] of ring) for (let y = y0; y <= y1; y++) if (!skip(x, y, z)) c.set(x, y, z, log, props)
}

/** watchHat: a tented hat over a square (rings of `roof.stairs` rising inward from y); the apex becomes a bottom slab
 *  of `cap`. With `top`, no ring goes above it: the cells the rings leave at the top level get the cap. Returns the
 *  cap cells. */
export function watchHat(c, box, roof, cap, top = null) {
  if (top === null) { const apex = tent(c, box, roof); slab(c, ...apex, cap); return [apex] }
  const { x0, z0, x1, z1, y } = box
  let k = 0
  for (; x0 + k < x1 - k && z0 + k < z1 - k && y + k <= top; k++) {
    const a = x0 + k, b = x1 - k, f = z0 + k, g = z1 - k, yy = y + k
    const put = (x, z, facing, shape = 'straight') =>
      c.set(x, yy, z, roof.stairs, { facing, half: 'bottom', shape, waterlogged: 'false' })
    for (let x = a + 1; x < b; x++) { put(x, f, 'south'); put(x, g, 'north') }
    for (let z = f + 1; z < g; z++) { put(a, z, 'east'); put(b, z, 'west') }
    put(a, f, 'south', 'outer_right'); put(b, f, 'south', 'outer_left')
    put(a, g, 'north', 'outer_left'); put(b, g, 'north', 'outer_right')
  }
  const caps = []
  for (let x = x0 + k; x <= x1 - k; x++) for (let z = z0 + k; z <= z1 - k; z++) {
    slab(c, x, Math.min(y + k, top), z, cap); caps.push([x, Math.min(y + k, top), z])
  }
  return caps
}

/** ringFacing: the way a cell on the edge of x0..x1, z0..z1 looks out (corners: along z). */
export function ringFacing({ x0, z0, x1, z1 }, x, z) {
  return z === z0 ? 'north' : z === z1 ? 'south' : x === x0 ? 'west' : 'east'
}

/** setBackPanels: at each wall cell [x, y, z] of the square `box`, a board (`board`, a trapdoor id) hung open on the
 *  cell's inner side, facing out. */
export function setBackPanels(c, box, cells, board) {
  for (const [x, y, z] of cells)
    c.set(x, y, z, board, { facing: ringFacing(box, x, z), half: 'bottom', open: 'true', powered: 'false', waterlogged: 'false' })
}
