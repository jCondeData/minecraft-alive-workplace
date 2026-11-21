// Shrines (ROADMAP 52, the Mega Shrine's Keystone Arch): a round-headed arch standing in one z plane, and twin flights
// that climb from both ends of a row to a landing in its middle (an altar stair). Palettes are passed in; nothing here
// picks a block itself.
import { stair } from './canvas.mjs'

// A round-headed arch in the plane z, centred on x = cx: piers at cx +- r from y0 up to spring (inclusive), then the
// head rounded with stairs: a shoulder stair on each pier, a block and a stair one step in, and the crown of full
// blocks over the middle three cells, the middle one the key. m = { full, stairs, key } (full may be a mix()).
// Returns the cells the arch's head leaves open under it, for lights hung from the crown.
export function roundArch(c, { cx, z, y0, spring, r = 3 }, m) {
  for (const s of [-1, 1]) {
    const x = cx + s * r, inward = s < 0 ? 'east' : 'west'
    c.fill(x, y0, z, x, spring, z, m.full)
    stair(c, x, spring + 1, z, m.stairs, inward) // the shoulder: its high side toward the middle
    c.set(cx + s * (r - 1), spring + 1, z, m.full)
    stair(c, cx + s * (r - 1), spring + 2, z, m.stairs, inward)
    for (let d = 1; d < r - 1; d++) c.set(cx + s * d, spring + 2, z, m.full)
  }
  c.set(cx, spring + 2, z, m.key ?? m.full)
  const open = []
  for (let d = -(r - 2); d <= r - 2; d++) open.push([cx + d, spring + 1, z])
  return open
}

// Twin flights along the row z: from x = cx - n and x = cx + n at y0, each climbing one block a step toward the middle,
// to a landing at x = cx on top of a solid column (its top level with the last step's high half). Everything under
// the steps is filled with `fill`, so the row's face is a stepped gable. m = { stairs, fill, landing }.
export function twinFlight(c, { cx, z, y0, n }, m) {
  for (const s of [-1, 1]) {
    for (let i = 0; i < n; i++) {
      const x = cx + s * (n - i), y = y0 + i
      for (let yy = y0; yy < y; yy++) c.set(x, yy, z, m.fill)
      stair(c, x, y, z, m.stairs, s < 0 ? 'east' : 'west') // climbed going toward the middle
    }
  }
  for (let y = y0; y < y0 + n - 1; y++) c.set(cx, y, z, m.fill)
  c.set(cx, y0 + n - 1, z, m.landing ?? m.fill)
}
