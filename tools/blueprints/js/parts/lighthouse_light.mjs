// Lighthouses and round towers by the sea (the Lighthouse Wonder, 49): a round drum of masonry with a watertight shell,
// a corbelled course that carries a gallery out over the shaft, a weathering ledge where a wider foot steps in, a glazed
// lantern room on square copper posts, a crown of lamps lit at night by inverted daylight detectors over each one, a
// stone causeway (a mole) with parapets, and mooring bollards. Every block comes from the palette passed in.
import { stair } from './canvas.mjs'

/** roundCells: the cells of a filled circle of radius r round (cx, cz) (r*r + r, so the edge reads round, not starry). */
export function roundCells(cx, cz, r) {
  const out = []
  for (let dx = -r; dx <= r; dx++) for (let dz = -r; dz <= r; dz++) if (dx * dx + dz * dz <= r * r + r) out.push([cx + dx, cz + dz])
  return out
}

/** shellCells: the rim of roundCells(r): every cell with any of its 8 neighbours outside, so the ring is joined face to
 *  face all round (no see-through corner) and can hold panes, bars or fences end to end. */
export function shellCells(cx, cz, r) {
  const inside = new Set(roundCells(cx, cz, r).map(([x, z]) => `${x},${z}`))
  const rim = roundCells(cx, cz, r).filter(([x, z]) => {
    for (let dx = -1; dx <= 1; dx++) for (let dz = -1; dz <= 1; dz++) if (!inside.has(`${x + dx},${z + dz}`)) return true
    return false
  })
  // where two rim cells touch only at a corner, the inner of the two cells between them joins them face to face
  const has = new Set(rim.map(([x, z]) => `${x},${z}`))
  for (const [x, z] of [...rim]) for (const [dx, dz] of [[1, 1], [1, -1], [-1, 1], [-1, -1]]) {
    if (!has.has(`${x + dx},${z + dz}`) || has.has(`${x + dx},${z}`) || has.has(`${x},${z + dz}`)) continue
    const a = [x + dx, z], b = [x, z + dz]
    const d = ([px, pz]) => (px - cx) ** 2 + (pz - cz) ** 2
    const pick = d(a) <= d(b) ? a : b
    has.add(`${pick[0]},${pick[1]}`); rim.push(pick)
  }
  return rim
}

/** railCells: a thin rim of roundCells(r) for a rail of fence, bars or panes: every cell with a side outside, and
 *  where the rim steps diagonally the inner cell between, so each step is a corner with its post (owner rule 1). */
export function railCells(cx, cz, r) {
  const inside = new Set(roundCells(cx, cz, r).map(([x, z]) => `${x},${z}`))
  const rim = roundCells(cx, cz, r).filter(([x, z]) => [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dz]) => !inside.has(`${x + dx},${z + dz}`)))
  const has = new Set(rim.map(([x, z]) => `${x},${z}`))
  for (const [x, z] of [...rim]) for (const [dx, dz] of [[1, 1], [1, -1], [-1, 1], [-1, -1]]) {
    if (!has.has(`${x + dx},${z + dz}`) || has.has(`${x + dx},${z}`) || has.has(`${x},${z + dz}`)) continue
    const a = [x + dx, z], b = [x, z + dz]
    const d = ([px, pz]) => (px - cx) ** 2 + (pz - cz) ** 2
    const pick = d(a) >= d(b) ? a : b // the outer one: a corner post where the rail turns
    has.add(`${pick[0]},${pick[1]}`); rim.push(pick)
  }
  return rim
}

/** towardCentre: the facing (north/south/east/west) from (x, z) toward the centre (cx, cz), by the larger offset. */
export function towardCentre(x, z, cx, cz) {
  const dx = cx - x, dz = cz - z
  if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? 'east' : 'west'
  return dz > 0 ? 'south' : 'north'
}

/** lightDrum: a round wall of radius r from y0 to y1 (`id` may be a mix()), `skip(x, y, z)` leaving cells for openings. */
export function lightDrum(c, cx, cz, r, y0, y1, id, skip = () => false) {
  for (const [x, z] of shellCells(cx, cz, r)) for (let y = y0; y <= y1; y++) if (!skip(x, y, z)) c.set(x, y, z, id)
}

/** corbelCourse: upside-down stairs under the rim of radius `out` that stands past radius `inner` (a gallery's or a
 *  jetty's overhang), each with its full back against the shaft. */
export function corbelCourse(c, cx, cz, out, inner, y, stairs) {
  const shaft = new Set(roundCells(cx, cz, inner).map(([x, z]) => `${x},${z}`))
  for (const [x, z] of roundCells(cx, cz, out)) if (!shaft.has(`${x},${z}`)) stair(c, x, y, z, stairs, towardCentre(x, z, cx, cz), 'top')
}

/** weathering: where a wide foot of radius `out` steps in to a shaft of radius `inner`, a ring of stairs on the foot's
 *  top sloping the rain off (backs against the shaft). */
export function weathering(c, cx, cz, out, inner, y, stairs) {
  const shaft = new Set(roundCells(cx, cz, inner).map(([x, z]) => `${x},${z}`))
  for (const [x, z] of roundCells(cx, cz, out)) if (!shaft.has(`${x},${z}`)) stair(c, x, y, z, stairs, towardCentre(x, z, cx, cz), 'bottom')
}

/** lanternRoom: a square glazed room of half-width h on the floor at y (sill course at y, panes y+1..y+glassH, a head
 *  course over them), a square post at each corner from sill to head. pal: {sill, post, glass, head}. Returns the head's y. */
export function lanternRoom(c, cx, cz, h, y, glassH, pal) {
  const top = y + glassH + 1
  for (let dx = -h; dx <= h; dx++) for (let dz = -h; dz <= h; dz++) {
    const edge = Math.abs(dx) === h || Math.abs(dz) === h
    if (!edge) continue
    const corner = Math.abs(dx) === h && Math.abs(dz) === h
    c.set(cx + dx, y, cz + dz, corner ? pal.post : pal.sill)
    for (let yy = y + 1; yy < top; yy++) c.set(cx + dx, yy, cz + dz, corner ? pal.post : pal.glass)
  }
  return top
}

/** lampCrown: the night light. Round a square of half-width h at y, a redstone lamp in every edge cell but the corners
 *  (pal.corner there), each with an inverted daylight detector on top: open to the sky, it lights the lamp under it at
 *  dusk and nothing else. Inside the ring, pal.cap. Returns the lamps' cells. */
export function lampCrown(c, cx, cz, h, y, pal) {
  const lamps = []
  for (let dx = -h; dx <= h; dx++) for (let dz = -h; dz <= h; dz++) {
    const x = cx + dx, z = cz + dz
    const edge = Math.abs(dx) === h || Math.abs(dz) === h
    const corner = Math.abs(dx) === h && Math.abs(dz) === h
    if (!edge) c.set(x, y, z, pal.cap)
    else if (corner) c.set(x, y, z, pal.corner)
    else {
      c.set(x, y, z, 'redstone_lamp', { lit: 'false' })
      c.set(x, y + 1, z, 'daylight_detector', { inverted: 'true', power: '0' })
      lamps.push([x, y, z])
    }
  }
  return lamps
}

/** causeway: a stone mole from z0 to z1 across x0..x1: solid to its deck (top at deckY), a parapet of `wall` on each
 *  side one above the deck, gaps in the parapets where `gap(x, z)` says (steps down to a landing). */
export function causeway(c, { x0, x1, z0, z1, deckY }, { body, deck, wall }, gap = () => false) {
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    for (let y = 0; y < deckY; y++) c.set(x, y, z, body)
    const side = x === x0 || x === x1
    c.set(x, deckY, z, side ? body : deck)
    if (side && !gap(x, z)) c.set(x, deckY + 1, z, wall)
  }
}

/** bollard: a mooring post (a stump of `post` under a cap of `cap`, a lantern-free iron-dark head) at x, y, z. */
export function bollard(c, x, y, z, { post, cap }) {
  c.set(x, y, z, post)
  c.set(x, y + 1, z, cap)
}
