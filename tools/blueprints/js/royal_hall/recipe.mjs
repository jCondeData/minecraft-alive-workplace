// The Royal Hall ("the Brick Palace"), the recipe: draws plan.mjs in passes (block-out and structure, openings, depth
// and detail, interior, ground) from the shared parts library, LEVEL=1 (realm/royal_hall) or 2 (realm/royal_hall_2:
// Level I and the east range with its belfry). Writes furniture.json beside it for tools/blueprints/rebuild.py.
// Build: sh build.sh (MC_BUILDER = the minecraft-builder skill's folder); CUT=<y> cuts everything above y.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { LEVEL, SIZE, PALETTE as P, MASSES as M, OPENINGS as O, FURNITURE, FURNITURE_2 } from './plan.mjs'
import {
  Canvas, mix, stair, slab, door, louvre, quoins, hipRoof, steepHip, buttress, dais, standard, hatchLadder, archHead,
  furnitureParts,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const IDS = { 1: 'royal_hall', 2: 'royal_hall_2' }

/** A rectangle's wall ring (x0..x1, z0..z1) from y0 to y1, `skip(x, y, z)` leaving a cell. */
function ring(c, { x0, x1, z0, z1 }, y0, y1, id, skip = () => false) {
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) {
    if (x !== x0 && x !== x1 && z !== z0 && z !== z1) continue
    if (!skip(x, y, z)) c.set(x, y, z, id)
  }
}

/** A window in a wall plane: panes over the cells, a smooth stone head over the top row and an upside-down stone
 *  sill under the bottom row one block out (`out`), so every window on the palace is dressed the same way. */
function dressed(c, cells, out) {
  const d = { north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0] }[out]
  const back = { north: 'south', south: 'north', west: 'east', east: 'west' }[out]
  const low = Math.min(...cells.map(p => p[1])), high = Math.max(...cells.map(p => p[1]))
  for (const [x, y, z] of cells) c.set(x, y, z, P.glass)
  for (const [x, y, z] of cells) {
    if (y === high) c.set(x, y + 1, z, P.dress)
    if (y === low && c.inside(x + d[0], y - 1, z + d[1])) stair(c, x + d[0], y - 1, z + d[1], P.stoneStairs, back, 'top')
  }
}

const col = (x, z, y0, y1) => Array.from({ length: y1 - y0 + 1 }, (_, i) => [x, y0 + i, z])

export function draw(level = LEVEL) {
  const c = new Canvas(level === 2 ? [27, 25, 25] : SIZE)
  const brick = mix(P.brick, 3)
  const stone = mix(P.stoneMix, 5)
  const H = M.hall
  const inTower = (x, z) => M.towers.some(t => x >= t.x0 && x <= t.x1 && z >= t.z0 && z <= t.z1)

  // --- Pass 1: block-out and structure ---------------------------------------------------------------------------
  // The hall: a stone plinth course, brick walls, a stone band under the eaves
  c.fill(H.x0, 0, H.z0, H.x1, 0, H.z1, stone)
  for (let x = H.x0 + 1; x < H.x1; x++) for (let z = H.z0 + 1; z < H.z1; z++)
    c.set(x, 0, z, (x === H.x0 + 1 || x === H.x1 - 1 || z === H.z0 + 1) ? P.floorAlt : P.floor)
  ring(c, H, 1, 9, brick)
  ring(c, H, H.wallTop, H.wallTop, P.dress)
  quoins(c, [[H.x0, H.z1], [H.x1, H.z1]], 1, 9, P.stone, P.dress)

  // The two gate towers: stone footing, brick shaft with stone bands at each floor, a corbelled ring under a copper cap
  for (const t of M.towers) {
    c.fill(t.x0, 0, t.z0, t.x1, 0, t.z1, stone)
    ring(c, t, 1, 15, brick)
    for (const y of [5, 11]) ring(c, t, y, y, P.stone)
    quoins(c, [[t.x0, t.z0], [t.x1, t.z0], [t.x0, t.z1], [t.x1, t.z1]], 1, 15, P.stone, P.dress)
    const o = { x0: t.x0 - 1, x1: t.x1 + 1, z0: t.z0 - 1, z1: t.z1 + 1 }
    // corbels under the jettied ring: upside-down stairs, their backs to the shaft
    for (let x = t.x0; x <= t.x1; x++) { stair(c, x, 15, o.z0, P.stoneStairs, 'south', 'top'); stair(c, x, 15, o.z1, P.stoneStairs, 'north', 'top') }
    for (let z = t.z0; z <= t.z1; z++) { stair(c, o.x0, 15, z, P.stoneStairs, 'east', 'top'); stair(c, o.x1, 15, z, P.stoneStairs, 'west', 'top') }
    ring(c, o, 16, 16, P.stone)
    for (let x = t.x0; x <= t.x1; x++) for (let z = t.z0; z <= t.z1; z++) if (!(x === t.x0 || x === t.x1 || z === t.z0 || z === t.z1)) c.set(x, 16, z, P.planks)
    const top = steepHip(c, { x0: o.x0, z0: o.z0, x1: o.x1, z1: o.z1, y: 17 }, P.cap)
    const cx = (o.x0 + o.x1) / 2
    c.set(cx, top + 1, t.z0 + 2, 'lightning_rod', { facing: 'up', powered: 'false', waterlogged: 'false' })
    // floors at the bands, a ladder up the back corner through hatches
    for (const y of [5, 11]) for (let x = t.x0 + 1; x < t.x1; x++) for (let z = t.z0 + 1; z < t.z1; z++) c.set(x, y, z, P.planks)
    const lx = t.x0 < 9 ? t.x0 + 1 : t.x1 - 1
    const facing = t.x0 < 9 ? 'east' : 'west'
    hatchLadder(c, lx, t.z1 - 1, 1, 5, facing, P.wood)
    hatchLadder(c, lx, t.z1 - 1, 6, 11, facing, P.wood)
    hatchLadder(c, lx, t.z1 - 1, 12, 16, facing, P.wood)
  }

  // The porch between the towers: an arcade front, a stone deck on top (the proclamation balcony)
  const Po = M.porch
  for (let x = Po.x0; x <= Po.x1; x++) for (let z = Po.z0; z <= Po.z1; z++) {
    c.set(x, 0, z, (x === 9) ? P.floorAlt : P.floor)
    c.set(x, Po.deckY, z, P.stone)
  }
  for (const x of [Po.x0, Po.x1]) for (let y = 1; y < Po.deckY; y++) c.set(x, y, Po.z0, P.stone)
  for (let x = Po.x0 + 1; x < Po.x1; x++) c.set(x, 4, Po.z0, P.dress)
  archHead(c, Po.x0 + 1, Po.x1 - 1, Po.z0, 3, P.stoneStairs, null)
  c.set(9, 4, Po.z0, 'chiseled_stone_bricks')
  // the balcony's balustrade and its corner piers with lamps
  for (let x = Po.x0 + 1; x < Po.x1; x++) c.set(x, 6, Po.z0, P.stoneWall)
  for (const x of [Po.x0, Po.x1]) { c.set(x, 6, Po.z0, P.stone); c.set(x, 7, Po.z0, P.stoneWall); c.set(x, 8, Po.z0, 'lantern', { hanging: 'false', waterlogged: 'false' }) }
  standard(c, 9, 6, 4, { pole: `${P.wood}_fence`, colour: P.royal, face: 'north', height: 4 })

  // The vault (west) and the steward's room (east): low battlemented strongrooms on the hall's back flanks
  for (const [W, outX] of [[M.vault, M.vault.x0], [M.steward, M.steward.x1]]) {
    c.fill(W.x0, 0, W.z0, W.x1, 0, W.z1, stone)
    for (let x = W.x0 + 1; x < W.x1; x++) for (let z = W.z0 + 1; z < W.z1; z++) c.set(x, 1, z, P.floorAlt)
    const east = W === M.steward
    const shared = east && level === 2 // II: the steward's outer wall is the east range's wall
    ring(c, W, 1, W.top, (x, y, z) => (y === 1 ? P.stone : brick(x, y, z)),
      (x, y, z) => x === (east ? H.x1 : H.x0) || (shared && x === W.x1))
    for (let x = W.x0 + 1; x < W.x1; x++) for (let z = W.z0 + 1; z < W.z1; z++) c.set(x, W.top + 1, z, P.stone)
    // the parapet: a stone ring at top + 1 on the outer three sides, merlons on every other cell
    for (let x = W.x0; x <= W.x1; x++) for (let z = W.z0; z <= W.z1; z++) {
      const rim = x === outX || z === W.z0 || z === W.z1
      if (!rim || x === (east ? H.x1 : H.x0) || (shared && x === W.x1)) continue
      c.set(x, W.top + 1, z, P.stone)
      if ((x + z) % 2 === 0) { c.set(x, W.top + 2, z, P.stone); slab(c, x, W.top + 3, z, P.stoneSlab) } else slab(c, x, W.top + 2, z, P.stoneSlab)
    }
    quoins(c, [[outX, W.z0], [outX, W.z1]].filter(() => !shared), 1, W.top, P.stone, P.dress)
  }

  // The throne bay, pushed out of the back wall under its own hipped roof
  const B = M.throneBay
  c.fill(B.x0, 0, B.z0, B.x1, 0, B.z1, stone)
  ring(c, B, 1, B.top, brick, (x, y, z) => z === B.z0)
  for (let x = B.x0 + 1; x < B.x1; x++) for (let z = B.z0; z < B.z1; z++) c.set(x, 1, z, P.dais)
  quoins(c, [[B.x0, B.z1], [B.x1, B.z1]], 1, B.top, P.stone, P.dress)

  // Roofs: the hall's hip (not over the towers), the bay's lower hip
  hipRoof(c, { x0: H.x0 - 1, z0: H.z0 - 1, x1: H.x1 + 1, z1: H.z1 + 1, eaveY: H.eaveY }, P.roof,
    (x, y, z) => !inTower(x, z) && !(z === H.z1 + 1 && x >= B.x0 - 1 && x <= B.x1 + 1) && !(level === 2 && x === H.x1 + 1 && z >= 7))
  hipRoof(c, { x0: B.x0 - 1, z0: B.z0 - 1, x1: B.x1 + 1, z1: B.z1 + 1, eaveY: B.top }, P.roof,
    (x, y, z) => z > H.z1)
  // the frontispiece: a crow-stepped brick gable over the balcony carrying the realm's arms, the roof running in behind
  const steps = { 6: 12, 7: 13, 8: 14, 9: 16, 10: 14, 11: 13, 12: 12 }
  for (const [xs, top] of Object.entries(steps)) {
    const x = Number(xs)
    for (let y = H.wallTop + 1; y <= top; y++) c.set(x, y, H.z0, brick)
    if (x === 9) c.set(x, top + 1, H.z0, P.stoneWall); else slab(c, x, top + 1, H.z0, P.dressSlab)
  }
  c.set(9, 13, H.z0 - 1, `${P.royal}_wall_banner`, { facing: 'north' })
  c.set(9, 12, H.z0, P.dress)
  // a louvre on the ridge over the hall's middle, a lantern hung inside it
  louvre(c, 9, 13, 17, { base: P.roof.full, post: `${P.wood}_fence`, roof: P.roof, finial: 'lightning_rod' })
  // tie beams across the hall, the chandeliers hang from them
  for (const z of [9, 12, 15]) for (let x = H.x0 + 1; x < H.x1; x++) c.set(x, 8, z, P.log, { axis: 'x' })

  // --- Pass 2: openings --------------------------------------------------------------------------------------------
  door(c, O.frontDoor.at, 'north', 'north', { wood: P.wood })
  for (const x of [8, 10]) for (let y = 1; y <= 3; y++) c.set(x, y, H.z0, P.stone)
  c.set(9, 3, H.z0, 'chiseled_stone_bricks')
  door(c, O.vaultDoor.at, 'west', 'west', { wood: P.wood })
  door(c, O.stewardDoor.at, 'east', 'east', { wood: P.wood })
  // the towers' doors from the porch, and onto the balcony
  door(c, [5, 1, 3], 'east', 'east', { wood: P.wood, hinge: 'right' }); door(c, [13, 1, 3], 'west', 'west', { wood: P.wood })
  door(c, [5, 6, 3], 'east', 'east', { wood: P.wood, hinge: 'right' }); door(c, [13, 6, 3], 'west', 'west', { wood: P.wood })
  // hall windows: two tall pairs a side, clerestory pairs over the wings, the back pairs, the great window over the balcony
  for (const [x, out] of [[H.x0, 'west'], [H.x1, 'east']]) {
    for (const zs of [[8, 9], [11, 12]]) dressed(c, zs.flatMap(z => col(x, z, 3, 7)), out)
    for (const zs of [[15, 16], [18, 19]]) dressed(c, zs.flatMap(z => col(x, z, 8, 9)), out)
  }
  for (const xs of [[5, 6], [12, 13]]) dressed(c, xs.flatMap(x => col(x, H.z1, 3, 7)), 'south')
  dressed(c, [8, 9, 10].flatMap(x => col(x, H.z0, 7, 9)), 'north')
  c.set(9, 8, H.z0, P.dress) // a stone mullion splits the great window into a pair of lights round a cross
  // tower windows: slits at the foot, lights on each floor, front and outer side
  for (const t of M.towers) {
    const ox = t.x0 < 9 ? t.x0 : t.x1, out = t.x0 < 9 ? 'west' : 'east'
    dressed(c, col(3 + (t.x0 < 9 ? 0 : 12), t.z0, 7, 9), 'north')
    dressed(c, col(3 + (t.x0 < 9 ? 0 : 12), t.z0, 12, 14), 'north')
    c.set(3 + (t.x0 < 9 ? 0 : 12), 2, t.z0, P.glass); c.set(3 + (t.x0 < 9 ? 0 : 12), 3, t.z0, P.glass)
    dressed(c, col(ox, 3, 7, 9), out)
    dressed(c, col(ox, 3, 12, 14), out)
  }
  // the throne bay: an arch from the dais, a tall light behind the seat, side lights
  for (let x = B.x0 + 1; x < B.x1; x++) for (let y = 2; y <= 6; y++) c.clear(x, y, B.z0)
  archHead(c, B.x0 + 1, B.x1 - 1, B.z0, 6, P.stoneStairs, 'chiseled_stone_bricks')
  dressed(c, col(9, B.z1, 4, 7), 'south')
  for (const x of [B.x0, B.x1]) dressed(c, col(x, 21, 3, 5), x === B.x0 ? 'west' : 'east')
  // the strongrooms: barred slits on the vault, glazed lights on the steward's room
  for (const z of [15, 18]) { c.set(M.vault.x0, 3, z, P.bars); c.set(M.vault.x0, 4, z, P.bars) }
  if (level === 1) for (const z of [15, 18]) dressed(c, col(M.steward.x1, z, 3, 4), 'east')
  for (const x of [1, 2]) c.set(x, 3, M.vault.z1, P.bars)

  // --- Pass 3: depth and detail ------------------------------------------------------------------------------------
  // buttresses between the hall's window pairs, stepped at the top
  for (const [x, toward] of [[H.x0 - 1, 'east'], [H.x1 + 1, 'west']]) buttress(c, x, 10, 0, 6, P.stone, P.stoneStairs, toward)
  for (const [x, toward] of [[H.x0 - 1, 'east'], [H.x1 + 1, 'west']]) buttress(c, x, 7, 0, 6, P.stone, P.stoneStairs, toward)
  // the throne bay's corner piers
  for (const [x, toward] of [[B.x0 - 1, 'east'], [B.x1 + 1, 'west']]) buttress(c, x, B.z1, 0, 4, P.stone, P.stoneStairs, toward)
  // wall banners of the realm either side of the arcade, facing the street
  for (const x of [Po.x0, Po.x1]) c.set(x, 3, 0, `${P.royal}_wall_banner`, { facing: 'north' })
  // the porch lamp hangs from the deck; the stair up to the arcade
  c.set(9, 4, 3, 'lantern', { hanging: 'true', waterlogged: 'false' })
  for (let x = Po.x0 + 1; x < Po.x1; x++) stair(c, x, 0, 0, P.stoneStairs, 'south')
  // a flower bed in its own soil either side of the steps, under the towers
  for (const x of [1, 2, 4, 5, 13, 14, 16, 17]) { c.set(x, 0, 0, 'coarse_dirt') }
  for (const [x, f] of [[1, 'rose_bush'], [2, 'peony'], [4, 'peony'], [5, 'rose_bush'], [13, 'rose_bush'], [14, 'peony'], [16, 'peony'], [17, 'rose_bush']]) {
    c.set(x, 1, 0, f, { half: 'lower' }); c.set(x, 2, 0, f, { half: 'upper' })
  }
  for (const x of [0, 3, 15, 18]) c.set(x, 0, 0, P.stone) // kerbs

  // --- Pass 4: interior --------------------------------------------------------------------------------------------
  dais(c, { x0: H.x0 + 1, x1: H.x1 - 1, z0: 16, z1: H.z1 - 1, y: 1, stepZ: 15 }, { top: P.dais, step: P.daisStep })
  for (const x of [8, 10]) standard(c, x, 2, 22, { pole: `${P.wood}_fence`, colour: P.royal, face: 'north', height: 3 })
  for (const f of FURNITURE) furnitureParts[f.part](c, f)

  if (level === 2) drawRange(c, brick, stone)
  if (level === 2) {
    // the east range stands two blocks off the hall: the strip between is built up solid to the eaves (the hall's east
    // windows walled up behind it) and leaded over, so the hall and the range read as one block
    for (let x = H.x1 + 1; x <= M.range.x0 - 1; x++) for (let z = M.range.z0 - 1; z <= H.z1; z++) for (let y = 0; y <= 10; y++) {
      const inSteward = z > M.steward.z0 && z < M.steward.z1 && y >= 1 && y <= M.steward.top
      if (!inSteward) c.set(x, y, z, brick)
    }
    for (let x = H.x1 + 1; x <= M.range.x0 - 1; x++) for (let z = M.range.z0 - 1; z <= H.z1 + 1; z++) c.set(x, 11, z, P.roof.full)
    for (let x = H.x1 + 1; x <= M.range.x0 - 1; x++) slab(c, x, 11, M.range.z0 - 1, P.roof.slab)
    for (const z of [8, 9, 11, 12, 15, 16, 18, 19]) for (let y = 2; y <= 10; y++) if (c.name(H.x1, y, z) === 'minecraft:glass_pane') c.set(H.x1, y, z, brick)
    for (const z of [8, 9, 11, 12]) for (const x of [H.x1 + 1]) if (c.name(x, 2, z).endsWith('stairs')) c.set(x, 2, z, brick)
  }
  return c
}

/** Level II: the east range (the treasury, the council chamber over it) and the belfry at its back corner. */
function drawRange(c, brick, stone) {
  const R = M.range, F = M.belfry
  const inBelfry = (x, z) => x >= F.x0 && x <= F.x1 && z >= F.z0 && z <= F.z1
  c.fill(R.x0, 0, R.z0, R.x1, 0, R.z1, stone)
  for (let x = R.x0 + 1; x < R.x1; x++) for (let z = R.z0 + 1; z < R.z1; z++) c.set(x, 0, z, (x + z) % 2 ? P.floor : P.floorAlt)
  ring(c, R, 1, R.top - 1, brick)
  ring(c, R, R.upper, R.upper, P.stone) // the floor band outside
  ring(c, R, R.top, R.top, P.dress)
  quoins(c, [[R.x1, R.z0], [R.x0, R.z0]], 1, R.top - 1, P.stone, P.dress)
  for (let x = R.x0 + 1; x < R.x1; x++) for (let z = R.z0 + 1; z < R.z1; z++) c.set(x, R.upper, z, P.planks)
  // the belfry: a brick shaft, an open bell chamber, a copper cap
  c.fill(F.x0, 0, F.z0, F.x1, 0, F.z1, stone)
  ring(c, F, 1, F.top - 1, brick)
  for (const y of [6, 12]) ring(c, F, y, y, P.stone)
  quoins(c, [[F.x0, F.z1], [F.x1, F.z1], [F.x1, F.z0]], 1, F.top - 1, P.stone, P.dress)
  for (let x = F.x0 + 1; x < F.x1; x++) for (let z = F.z0 + 1; z < F.z1; z++) { c.set(x, 12, z, P.planks); c.set(x, 16, z, P.stone) }
  for (const [x, z] of [[F.x0, 22], [F.x1, 22], [22, F.z1], [23, F.z1], [22, F.z0], [23, F.z0]]) for (let y = 13; y <= 15; y++) c.clear(x, y, z)
  for (const [x, z] of [[F.x0, 22], [F.x1, 22], [22, F.z1], [23, F.z1], [22, F.z0], [23, F.z0]]) for (let y = 13; y <= 15; y++) c.set(x, y, z, P.bars)
  c.set(22, 15, 22, 'bell', { attachment: 'ceiling', facing: 'north', powered: 'false' })
  ring(c, { x0: F.x0, x1: F.x1, z0: F.z0, z1: F.z1 }, F.top, F.top, P.stone)
  const top = steepHip(c, { x0: F.x0, z0: F.z0, x1: F.x1, z1: F.z1, y: F.top + 1 }, P.cap)
  c.set(22, top + 1, 22, 'lightning_rod', { facing: 'up', powered: 'false', waterlogged: 'false' })
  hatchLadder(c, F.x1 - 1, F.z1 - 1, 1, 12, 'north', P.wood)
  // the range's hipped roof (not over the belfry), tie beams under the council's ceiling
  hipRoof(c, { x0: R.x0 - 1, z0: R.z0 - 1, x1: R.x1 + 1, z1: R.z1 + 1, eaveY: R.top }, P.roof, (x, y, z) => !inBelfry(x, z) && !(x === R.x0 - 1 && z <= 20))
  for (const z of [12, 16]) for (let x = R.x0 + 1; x < R.x1; x++) c.set(x, R.top - 1, z, P.log, { axis: 'x' })

  // openings: the east door with its lamps on posts, the way from the steward's room down a step, the belfry door
  door(c, O.eastDoor.at, 'east', 'east', { wood: P.wood, step: [26, 0, 14], stepStair: P.stoneStairs })
  for (const z of [12, 16]) { c.set(26, 0, z, P.stoneWall); c.set(26, 1, z, P.stoneWall); c.set(26, 2, z, 'lantern', { hanging: 'false', waterlogged: 'false' }) }
  for (const y of [2, 3]) c.clear(R.x0, y, 16)
  stair(c, R.x0 + 1, 1, 16, P.stoneStairs, 'west')
  door(c, [22, 1, F.z0], 'north', 'north', { wood: P.wood })
  // the stair up to the council chamber along the west wall, rising north from beside the steward's step
  for (let k = 0; k <= 5; k++) {
    for (let y = 1; y < 1 + k; y++) c.set(19, y, 14 - k, P.planks)
    stair(c, 19, 1 + k, 14 - k, `${P.wood}_stairs`, 'north')
  }
  for (const z of [10, 11]) c.clear(19, R.upper, z)
  for (const [x, z] of [[20, 10], [20, 11], [19, 12]]) c.set(x, R.upper + 1, z, `${P.wood}_fence`)
  // windows: the east face and the front, both floors
  for (const zs of [[10, 11], [17, 18]]) dressed(c, zs.flatMap(z => col(R.x1, z, 3, 4)), 'east')
  for (const zs of [[10, 11], [15, 16]]) dressed(c, zs.flatMap(z => col(R.x1, z, 8, 9)), 'east')
  dressed(c, [21, 22].flatMap(x => col(x, R.z0, 3, 4)), 'north')
  dressed(c, [21, 22, 23].flatMap(x => col(x, R.z0, 8, 9)), 'north')
  dressed(c, [10, 11].flatMap(z => col(R.x0, z, 8, 9)), 'west')
  for (const f of FURNITURE_2) furnitureParts[f.part](c, f)
}

export default function build(api) {
  const c = draw(LEVEL)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: `${IDS[LEVEL]}${cut !== undefined ? `_cut${cut}` : ''}` } }
}
