// Barracks ward (the Barracks, ROADMAP 49): a mirror canvas, so a second wing is the first one drawn again turned
// round a centre line, a gate arch through a tower, a stone buttress, a footlocker at a bed's foot and a weapon rack.
// Every block comes from the palette the caller passes.
import { stair } from './canvas.mjs'

const FLIP_X = { east: 'west', west: 'east', north: 'north', south: 'south', up: 'up', down: 'down' }

/** mirrorX: a view of canvas `c` mirrored across the line x = sum / 2 (a cell drawn at x lands at sum - x). Every
 *  facing turns east <-> west, door hinges and chest halves swap, stair shapes swap left and right, standing banners
 *  turn, and furniture spots are recorded mirrored too. Hand it to any part in place of the canvas: a symmetric wing
 *  (a second barrack range, the far side of a court) is then the first one's lines, run again. */
export function mirrorX(c, sum) {
  const props = p => {
    const q = { ...p }
    if (q.facing) q.facing = FLIP_X[q.facing] ?? q.facing
    if (q.hinge) q.hinge = q.hinge === 'left' ? 'right' : 'left'
    if (q.type === 'left' || q.type === 'right') q.type = q.type === 'left' ? 'right' : 'left'
    if (q.shape) q.shape = q.shape.replace(/left|right/, s => (s === 'left' ? 'right' : 'left'))
    if (q.rotation !== undefined) q.rotation = String((16 - Number(q.rotation)) % 16)
    if ('east' in q && 'west' in q) [q.east, q.west] = [q.west, q.east] // fence and wall joins
    return q
  }
  const m = {
    size: c.size,
    get spots() { return c.spots },
    inside: (x, y, z) => c.inside(sum - x, y, z),
    set: (x, y, z, id, p = {}) => c.set(sum - x, y, z, typeof id === 'function' ? id(x, y, z) : id, props(p)),
    get: (x, y, z) => c.get(sum - x, y, z),
    name: (x, y, z) => c.name(sum - x, y, z),
    clear: (x, y, z) => c.clear(sum - x, y, z),
    fill(x0, y0, z0, x1, y1, z1, id, p = {}) {
      for (let x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
        for (let y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
          for (let z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) m.set(x, y, z, id, p)
    },
    clearBox(x0, y0, z0, x1, y1, z1) { for (let x = x0; x <= x1; x++) for (let y = y0; y <= y1; y++) for (let z = z0; z <= z1; z++) m.clear(x, y, z) },
    free: (...cells) => c.free(...cells.map(([x, y, z]) => [sum - x, y, z])),
    spot: (kind, pos, facing, rel = [[0, 0, 0]], wood = null, colour = null) =>
      c.spot(kind, [sum - pos[0], pos[1], pos[2]], FLIP_X[facing] ?? facing, rel.map(([dx, dy, dz]) => [-dx, dy, dz]), wood, colour),
  }
  return m
}

/** gateArch: a round-headed opening `2 * half + 1` wide through a wall row z (centre cx, sill y on the floor) up to
 *  `top`: the cells cleared, and the two upper corners turned into haunches (upside-down `stairs` whose full side sits
 *  against the jamb), so the opening reads as an arch, not a slot. */
export function gateArch(c, cx, half, y, top, z, stairs) {
  for (let x = cx - half; x <= cx + half; x++) for (let yy = y; yy <= top; yy++) c.clear(x, yy, z)
  if (half >= 1) {
    stair(c, cx - half, top, z, stairs, 'west', 'top')
    stair(c, cx + half, top, z, stairs, 'east', 'top')
  }
}

/** wallButtress: a stone pier against a wall at [x, z] (`toward`: the way the wall lies from it), solid from y0 to y1 and
 *  weathered at y1 + 1 by a stair rising toward the wall. */
export function wallButtress(c, x, z, y0, y1, stone, stairs, toward) {
  for (let y = y0; y <= y1; y++) c.set(x, y, z, stone)
  stair(c, x, y1 + 1, z, stairs, toward)
}

/** footlocker: a soldier's chest at a bed's foot (single, its lid toward `facing`): a fixture, no spot. It stands
 *  against the bed, which the chest rule accepts. */
export function footlocker(c, [x, y, z], facing) {
  c.free([x, y, z])
  c.set(x, y, z, 'chest', { facing, type: 'single', waterlogged: 'false' })
}

/** weaponRack: a rack against a wall: `post` uprights (fences) in `cells`, each holding a spear (`spear`, e.g. a
 *  lightning rod standing up) on its top. */
export function weaponRack(c, cells, post, spear) {
  for (const [x, y, z] of cells) {
    c.set(x, y, z, post)
    c.set(x, y + 1, z, spear, { facing: 'up', powered: 'false', waterlogged: 'false' })
  }
}
