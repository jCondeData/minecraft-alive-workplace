// The Great Cathedral (a Wonder, version B: the Miners' Minster), the recipe: assembles plan.mjs stage by stage on a
// StagedCanvas (a later stage may only add: it throws on any block an earlier stage drew), each stage in passes
// (block-out, openings, depth and detail, interior, light), and returns the Wonder as it stands after STAGE (default 4)
// for the minecraft-builder skill's runner. verify() checks every stage against the brief and refuses a stage that
// changes an earlier one. CUT=<y> cuts everything above y (a floor's cutaway). stages.py saves the candidates.
import {
  SIZE, STAGES, MID, PALETTE as P, TOWERS, TOWER as T, FRONT as F, NAVE as N, AISLE as A, FLYERS, TRANSEPT as X,
  CHOIR as C, CHAPEL as CH, ALTAR, GUESTS, CAMERA, BELLS, LECTERNS, SACRISTY as SAC, PEWS,
} from './plan.mjs'
import { mix, stair, slab } from '../parts/canvas.mjs'
import { StagedCanvas } from '../parts/observatory_instruments.mjs'
import { stagesOnlyAdd } from '../parts/rotunda_helix.mjs'
import { oct, put, roofField, drawRoof, drawVault, flyingButtress, needleSpire, roseWindow, lancet } from '../parts/minster_b.mjs'

const W = SIZE[0] - 1 // 38: x mirrors to W - x
const wallMix = mix(P.wall, 7)
const plinthMix = mix(P.plinth, 11)
const stone = (x, y, z) => (y <= 3 ? plinthMix(x, y, z) : wallMix(x, y, z))
const glass = (x, y, z) => P.glass[(2 * y + x + z) % P.glass.length] // the lancets' glass, banded
const FLIP = { east: 'west', west: 'east', north: 'north', south: 'south' }
const both = f => { f(x => x, d => d); f(x => W - x, d => FLIP[d]) } // draw the west half, then its mirror
const d2a = (x, z) => Math.hypot(x - C.apse.cx, z - C.apse.cz) // distance from the apse's centre
const inApse = (x, z, r) => z > C.apse.cz && d2a(x, z) <= r
const lantern = (c, x, y, z, hanging) => c.set(x, y, z, 'lantern', { hanging: String(hanging), waterlogged: 'false' })
const candle = (c, x, y, z, n = 3) => c.set(x, y, z, 'white_candle', { candles: String(n), lit: 'false', waterlogged: 'false' })
const door = (c, x, y, z, facing, hinge) => {
  for (const half of ['lower', 'upper']) c.set(x, y + (half === 'upper' ? 1 : 0), z, `${P.wood}_door`, { facing, half, hinge, open: 'false', powered: 'false' })
}
const chandelier = (c, x, z, crown, drop) => {
  for (let y = crown - 1; y > crown - 1 - drop; y--) c.set(x, y, z, 'chain', { axis: 'y', waterlogged: 'false' })
  lantern(c, x, crown - 1 - drop, z, true)
}

// --- the roofs: one height field (the higher roof wins at every column) --------------------------------------------
const naveH = x => 23 + 2 * Math.min(x - 12, 26 - x)
const ROOFS = [
  // the nave and choir, x 12-26 (x 13-25 between the towers), ridge y 37, to the apse
  { cells: (x, z) => (z >= 12 && z <= C.z1 && x >= 12 && x <= 26) || (z >= 4 && z < 12 && x >= 13 && x <= 25), h: naveH },
  // the apse: a half cone round the altar's end
  { cells: (x, z) => z > C.z1 && d2a(x, z) <= 7.4, h: (x, z) => 23 + 2 * Math.max(0, 7 - d2a(x, z)) },
  // the transepts, x 1-37, ridge y 33 along x, running into the nave's slopes
  { cells: (x, z) => x >= 1 && x <= W - 1 && z >= X.z0 && z <= X.z1, h: (x, z) => 23 + 2 * Math.min(z - X.z0, X.z1 - z) },
  // the lean-to aisles (nave and choir), 1:1 from the eave y 9 up to the clerestory
  { cells: (x, z) => ((z >= 12 && z <= 28) || (z >= C.z0 && z <= C.z1 + 1)) && ((x >= 6 && x <= 12) || (x >= 26 && x <= 32)), h: x => 9 + (x <= 12 ? x - 6 : 32 - x) },
  // the transept chapels, small gables ridged along x
  { cells: (x, z) => ((x >= 0 && x <= CH.x1) || (x >= W - CH.x1 && x <= W)) && z >= CH.z0 && z <= CH.z1 + 1, h: (x, z) => CH.eave + 2 * Math.min(z - CH.z0, CH.z1 + 1 - z) },
]
const field = roofField(ROOFS)
const ROOF_BOX = [0, W, 0, SIZE[2] - 1]

// --- the vaults: a pointed barrel over the nave and choir, another over the transepts, groined at the crossing -----
const barrel = k => Math.min(19 + k, 23)
const vaultH = (x, z) => {
  let v = null
  if (x >= N.in[0] && x <= N.in[1] && z >= 7 && z <= C.z1) v = barrel(Math.min(x - N.in[0], N.in[1] - x))
  if (x >= 1 && x <= W - 1 && z >= X.z0 + 1 && z <= X.z1 - 1) v = Math.max(v ?? 0, barrel(Math.min(z - X.z0 - 1, X.z1 - 1 - z)))
  if (z > C.z1 && d2a(x, z) <= 5.5) v = barrel(Math.max(0, Math.round(5 - d2a(x, z))))
  return v
}
const rib = (x, z) => N.piers.includes(z) || z === X.z0 || z === X.z1 || z === 44 || (Math.abs(x - MID) === Math.abs(z - 34) && z > X.z0 && z < X.z1 && x >= 14 && x <= 24)

export function draw() {
  const c = new StagedCanvas(SIZE)

  // ==== Stage I: The Foundations and the West Front ================================================================
  c.begin(1)
  // Pass 1, the floor: one stone floor under the whole cross, a checker of andesite and deepslate down the vessel
  // (only where the church stands: the towers and the forecourt before the portal, the body, the arms, the buttresses' feet)
  const RECTS = [[1, 37, 1, 11], [2, 12, 0, 0], [26, 36, 0, 0], [13, 25, 0, 2], [7, 31, 12, 28], [0, W, 29, 39], [0, W, 40, 45], [7, 31, 46, 49],
    [1, 1, 27, 28], [5, 5, 27, 28], [W - 1, W - 1, 27, 28], [W - 5, W - 5, 27, 28], [7, 12, 12, 12]]
  for (const z of [...FLYERS, 48]) RECTS.push([5, 6, z, z], [W - 6, W - 5, z, z])
  const inFloor = (x, z) => RECTS.some(([a, b, p, q]) => x >= a && x <= b && z >= p && z <= q) || inApse(x, z, 6.5)
  for (let x = 0; x <= W; x++) for (let z = 0; z < SIZE[2]; z++) {
    if (!inFloor(x, z)) continue
    const vessel = (x >= 13 && x <= 25 && z >= 6) || (z >= X.z0 && z <= X.z1)
    c.set(x, 0, z, vessel ? P.floor[(x + z) & 1] : plinthMix(x, 0, z))
  }
  // the choir's floor, a step up (y 1), the altar's footing; the step across at z 40
  for (let x = 14; x <= 24; x++) for (let z = C.z0 + 1; z < SIZE[2]; z++) if (z <= C.z1 || inApse(x, z, 5.5)) c.set(x, 1, z, P.floor[(x + z) & 1])
  for (let x = 14; x <= 24; x++) stair(c, x, 1, C.z0, P.dressStairs, 'south')

  // Pass 2, the towers' lower storeys (y 1-22): shells with angle buttresses, string courses, lancets, a ladder
  for (const t of TOWERS) {
    for (let y = 1; y <= T.stage1Top; y++) for (let x = t.x0; x <= t.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
      if (x !== t.x0 && x !== t.x1 && z !== T.z0 && z !== T.z1) continue
      if (x === t.door[0] && z === t.door[1] && y <= 3) continue // the doorway from the narthex
      const front = z === T.z0 && (x === t.cx - 1 || x === t.cx + 1) && y >= 10 && y <= 14
      const outer = (x === (t.cx < MID ? t.x0 : t.x1)) && (z === 5 || z === 7) && y >= 10 && y <= 14
      if (front || outer) c.set(x, y, z, glass(x, y, z), { waterlogged: 'false' })
      else c.set(x, y, z, (x === t.x0 || x === t.x1) && (z === T.z0 || z === T.z1) ? P.dress : stone(x, y, z))
    }
    for (let y = 1; y <= T.stage1Top; y++) c.set(t.ladder[0], y, t.ladder[1], 'ladder', { facing: t.ladder[2], waterlogged: 'false' })
  }
  both((m, f) => {
    // angle buttresses at the towers' outer corners, stepping back at y 9 and capped at y 17
    for (const [x, z, face] of [[2, 0, 'south'], [1, 1, 'east'], [1, 11, 'east'], [12, 0, 'south']]) {
      for (let y = 1; y <= 16; y++) c.set(m(x), y, z, y <= 8 ? plinthMix(m(x), y, z) : P.dress)
      stair(c, m(x), 17, z, P.dressStairs, f(face))
    }
  })
  // string courses round each tower at y 8, 15 and 22 (upside-down stairs, the cornice under the belfry stage)
  for (const t of TOWERS) for (const y of [8, 15, 22]) {
    for (let x = t.x0 - 1; x <= t.x1 + 1; x++) for (let z = T.z0 - 1; z <= T.z1 + 1; z++) {
      const ex = x === t.x0 - 1 || x === t.x1 + 1, ez = z === T.z0 - 1 || z === T.z1 + 1
      if (!(ex || ez) || (ex && ez) || c.get(x, y, z) || !c.inside(x, y, z)) continue
      if (x >= 13 && x <= 25) continue // the front screen and the nave stand there
      if (z === T.z1 + 1 && y < 22) continue // the aisles' roofs meet the backs
      if (field.covered(x, z)) continue // never over a roof (it would hang over the aisles' lean-tos)
      stair(c, x, y, z, P.dressStairs, ez ? (z < T.z0 ? 'south' : 'north') : (x < t.x0 ? 'east' : 'west'), 'top')
    }
  }

  // Pass 3, the west front's screen (x 13-25, z 3-5, y 1-14): the deep portal of three orders, the doors and trumeau
  const portal = (x, y, z) => {
    const dx = Math.abs(x - MID)
    if (z === 3) return (y <= 7 && dx <= 3) || (y === 8 && dx <= 2) || (y === 9 && dx <= 1) || (y === 10 && dx === 0)
    if (z === 4) return (y <= 6 && dx <= 2) || (y === 7 && dx <= 1) || (y === 8 && dx === 0)
    return false
  }
  const arch = (x, y, z) => { // the upside-down stairs that point each order's arch
    const dx = x - MID
    if (z === 3) return (y === 8 && Math.abs(dx) === 3) || (y === 9 && Math.abs(dx) === 2) || (y === 10 && Math.abs(dx) === 1)
    if (z === 4) return (y === 7 && Math.abs(dx) === 2) || (y === 8 && Math.abs(dx) === 1)
    return false
  }
  for (let x = F.x0; x <= F.x1; x++) for (let y = 1; y <= F.top; y++) for (let z = F.thick[0]; z <= F.thick[1]; z++) {
    if (portal(x, y, z)) continue
    if ((z === 5 && Math.abs(x - MID) > 2) || (z === 4 && Math.abs(x - MID) > 4 && y <= 10)) continue // two thick, three at the doors
    if (z === F.doorZ && F.doors.includes(x) && y <= 2) continue
    if (arch(x, y, z)) { stair(c, x, y, z, P.dressStairs, x < MID ? 'west' : 'east', 'top'); continue }
    const niche = z === 3 && y >= 11 && y <= 12 && x % 2 === 0 && x >= 14 && x <= 24
    if (niche) { c.set(x, y, z, P.post); continue } // a figure in each niche
    const jamb = z === 3 && Math.abs(x - MID) === 4 && y <= 8
    c.set(x, y, z, jamb || (z === F.doorZ && x === MID && y <= 2) ? P.dress : z === F.doorZ && Math.abs(x - MID) <= 1 && y <= 5 ? 'chiseled_stone_bricks' : stone(x, y, z))
  }
  for (const [i, x] of F.doors.entries()) door(c, x, 1, F.doorZ, 'north', i ? 'right' : 'left')
  for (let x = F.x0; x <= F.x1; x++) stair(c, x, 13, 2, P.dressStairs, 'south', 'top') // the gallery's cornice
  for (let x = F.x0; x <= F.x1; x++) if (!c.get(x, 10, 2) && Math.abs(x - MID) > 4) stair(c, x, 10, 2, P.dressStairs, 'south', 'top') // the sill under the kings
  lantern(c, MID, 8, 4, true) // a lamp in the portal's crown

  // ==== Stage II: The Nave =========================================================================================
  c.begin(2)
  // Pass 1, block-out: the aisles' outer walls (y 1-9) with paired lancets, open to their lean-to roofs
  both((m, f) => {
    for (let z = 12; z <= 28; z++) for (let y = 1; y <= A.ceiling; y++) {
      const win = N.bays.some(([a, b]) => z === a || z === b) && y >= 3 && y <= 8
      if (win) c.set(m(A.wall[0]), y, z, glass(m(A.wall[0]), y, z), { waterlogged: 'false' })
      else c.add(m(A.wall[0]), y, z, N.piers.includes(z) ? P.dress : stone(m(A.wall[0]), y, z))
    }
    // the arcade (x 13): piers of andesite, pointed arches y 1-8, the wall over them to y 23 with the clerestory
    for (let z = N.z0; z <= N.z1; z++) for (let y = 1; y <= N.wallTop; y++) {
      const x = m(13)
      if (z < 12) { if (y > N.tribune) c.add(x, y, z, stone(x, y, z)); continue }
      if (N.piers.includes(z)) { c.add(x, y, z, P.dress); continue }
      const p = N.piers.filter(q => q < z).pop(), o = z - p // 1..3 within the bay
      if (y <= 7) continue
      if (y === 8) { if (o !== 2) stair(c, x, y, z, P.dressStairs, o === 1 ? 'north' : 'south', 'top'); continue }
      const clere = (o === 1 || o === 3) && y >= 16 && y <= 18
      if (clere) c.set(x, y, z, glass(x, y, z), { waterlogged: 'false' })
      else c.add(x, y, z, y === 9 || y === 15 ? P.dress : wallMix(x, y, z))
    }
  })
  // the tribune over the narthex (y 14), its rail, the flat ceiling under the rose (y 25) and the diaphragm at z 7
  for (let x = 13; x <= 25; x++) for (let z = 6; z <= 11; z++) c.add(x, N.tribune, z, x === 13 || x === 25 || z === 11 ? P.dress : 'stone_bricks')
  for (let x = 14; x <= 24; x++) c.set(x, N.tribune + 1, 11, P.post)
  for (let x = 14; x <= 24; x++) for (let z = 4; z <= 6; z++) c.add(x, 25, z, 'stone_bricks')
  for (let x = 14; x <= 24; x++) for (let y = vaultH(x, 7) + 1; y <= 24; y++) c.add(x, y, 7, wallMix(x, y, 7))
  // Pass 2, the roof and the vault, z <= 28
  drawRoof(c, field, ROOF_BOX, (x, z) => z <= 28, P.roof)
  drawVault(c, vaultH, [13, 25, 7, 28], (x, z) => z <= 28, P.vault, rib)
  for (let z = 4; z < 12; z++) for (const x of [13, 25]) put(c, x, 24, z, wallMix(x, 24, z)) // under the eave between the towers
  // Pass 3, depth: flying buttresses at z 16, 20, 24 on both sides, pinnacled
  both((m, f) => {
    for (const z of FLYERS) {
      const dir = f('east') === 'east' ? 1 : -1
      flyingButtress(c, { x: m(5), z, dir, step: 8, top: 16, wallX: m(13) }, { pier: P.dress, stairs: P.dressStairs, pin: P.post, cap: P.finial })
    }
  })
  // Pass 4, interior: the pews, the carpet up the middle, chandeliers, aisle lamps, banners on the piers
  for (const z of PEWS.zs) for (const x of PEWS.xs) stair(c, x, 1, z, `${P.wood}_stairs`, 'north')
  for (let z = 6; z <= 28; z++) for (let x = 18; x <= 20; x++) c.set(x, 1, z, P.carpet)
  for (const z of [15, 19, 23]) chandelier(c, MID, z, 23, 3)
  both(m => { for (const z of [14, 18, 22, 26]) lantern(c, m(10), field.top(m(10), z) - 1, z, true) })
  lantern(c, MID, 13, 8, true) // under the tribune, over the way in
  for (const [x, z, facing, colour] of [[14, 16, 'east', 'red'], [24, 16, 'west', 'white'], [14, 24, 'east', 'blue'], [24, 24, 'west', 'red']])
    c.set(x, 7, z, `${colour}_wall_banner`, { facing })

  // ==== Stage III: The Transepts and the Choir ======================================================================
  c.begin(3)
  // Pass 1, block-out: the transept arms' walls (z 29 and 39, y 1-22), the end walls and their gables
  both((m, f) => {
    for (const z of [X.z0, X.z1]) for (let x = 0; x <= 12; x++) for (let y = 1; y <= X.wallTop; y++) {
      if (z === X.z0 && x >= 9 && x <= 11 && y <= 7) continue // from the aisle
      if (z === X.z1 && x >= 2 && x <= 5 && y <= 5) continue // into the chapel
      if (z === X.z1 && x >= 9 && x <= 11 && y <= 6 && m(x) === x) continue // into the Lady chapel (west only)
      if (z === X.z1 && m(x) === SAC.door[0] && y <= 2) continue
      const lower = (x === 2 || x === 4) && z === X.z0 && y >= 3 && y <= 9
      const clere = (x === 3 || x === 5 || x === 9 || x === 11) && y >= 16 && y <= 18
      if (lower || clere) c.set(m(x), y, z, glass(m(x), y, z), { waterlogged: 'false' })
      else c.add(m(x), y, z, x === 0 || x === 6 ? P.dress : stone(m(x), y, z))
    }
    // the end wall and gable (x 0): a lancet triplet, the gable to the roof with a coping
    for (let z = X.z0 + 1; z < X.z1; z++) {
      const top = field.top(1, z)
      for (let y = 1; y <= top; y++) {
        const lan = ((z === 32 || z === 36) && y >= 4 && y <= 17) || (z === 34 && y >= 4 && y <= 20)
        if (lan) c.set(m(0), y, z, glass(m(0), y, z), { waterlogged: 'false' })
        else c.add(m(0), y, z, stone(m(0), y, z))
      }
      slab(c, m(0), top + 1, z, P.dressSlab)
    }
    for (const z of [X.z0, X.z1]) c.add(m(0), 23, z, P.dress)
    // the choir's walls (x 13, z 40-49): piers, the arcade to the Lady chapel (west), clerestory lancets
    for (let z = C.z0; z <= C.z1; z++) for (let y = 1; y <= 23; y++) {
      const x = m(13)
      if (C.piers.includes(z)) { c.add(x, y, z, P.dress); continue }
      const west = m(13) === 13
      if (west && y >= 2 && y <= 7 && z < 48) continue
      if (west && y === 8 && z < 48) { const o = (z - C.z0) % 4; if (o !== 2) stair(c, x, y, z, P.dressStairs, o === 1 ? 'north' : 'south', 'top'); continue }
      if ((z === 42 || z === 46) && y >= 16 && y <= 18) c.set(x, y, z, glass(x, y, z), { waterlogged: 'false' })
      else c.add(x, y, z, stone(x, y, z))
    }
    // the choir aisles (x 7-12, z 40-49): walls to y 9, open to the lean-to
    for (let z = C.z0; z <= C.z1; z++) for (let y = 1; y <= A.ceiling; y++) {
      const win = z === 47 && y >= 3 && y <= 7
      c.add(m(7), y, z, win ? glass(m(7), y, z) : stone(m(7), y, z), win ? { waterlogged: 'false' } : {})
    }
    for (let x = 8; x <= 12; x++) for (let y = 1; y < field.top(m(x), C.z1); y++) { // the end wall, up under the lean-to
      const win = x === 10 && y >= 3 && y <= 7
      c.add(m(x), y, C.z1, win ? glass(m(x), y, C.z1) : stone(m(x), y, C.z1), win ? { waterlogged: 'false' } : {})
    }
    // the transept chapels (x 0-7, z 40-45): walls to y 8, gables at x 0 and 7 up under their roof, a lancet
    for (let z = CH.z0; z <= CH.z1; z++) for (const x of [0, 7]) {
      const top = field.top(x, z) - 1
      for (let y = 1; y <= top; y++) {
        const lan = x === 0 && z === 43 && y >= 3 && y <= 10
        if (lan) c.set(m(x), y, z, glass(m(x), y, z), { waterlogged: 'false' })
        else c.add(m(x), y, z, stone(m(x), y, z))
      }
    }
    for (let x = 1; x <= 6; x++) for (let y = 1; y <= field.top(x, CH.z1) - 1; y++) c.add(m(x), y, CH.z1, stone(m(x), y, CH.z1))
  })
  // the apse: a round wall (radius 6 round the altar's end) to y 23, tall lancets where the wall runs straight
  const apseWall = (x, z) => z > C.z1 && d2a(x, z) > 5.5 && d2a(x, z) <= 6.5
  const lancets = []
  for (let x = 13; x <= 25; x++) for (let z = C.z1 + 1; z < SIZE[2]; z++) {
    if (!apseWall(x, z)) continue
    const alongX = apseWall(x - 1, z) && apseWall(x + 1, z) && !apseWall(x, z - 1) && !apseWall(x, z + 1)
    const alongZ = apseWall(x, z - 1) && apseWall(x, z + 1) && !apseWall(x - 1, z) && !apseWall(x + 1, z)
    if ((alongX || alongZ) && lancets.every(([a, b]) => Math.abs(a - x) + Math.abs(b - z) >= 4)) lancets.push([x, z])
  }
  for (let x = 12; x <= 26; x++) for (let z = C.z1 + 1; z < SIZE[2]; z++) {
    if (!apseWall(x, z)) continue
    const lan = lancets.some(([a, b]) => a === x && b === z)
    for (let y = 1; y <= 23; y++) {
      if (lan && y >= 3 && y <= 13) c.set(x, y, z, glass(x, y, z), { waterlogged: 'false' })
      else c.add(x, y, z, y === 1 || y === 14 ? P.dress : stone(x, y, z))
    }
  }
  // the crossing's four piers
  for (const x of [13, 25]) for (const z of [X.z0, X.z1]) for (let y = 1; y <= 23; y++) c.add(x, y, z, P.dress)
  // cornices along the transepts' long walls, under the eaves
  both(m => { for (let x = 0; x <= 12; x++) { if (field.covered(m(x), X.z0 - 1) || field.covered(m(x), X.z1 + 1)) continue; if (!c.get(m(x), 22, X.z0 - 1)) stair(c, m(x), 22, X.z0 - 1, P.dressStairs, 'south', 'top'); if (!c.get(m(x), 22, X.z1 + 1)) stair(c, m(x), 22, X.z1 + 1, P.dressStairs, 'north', 'top') } })
  // Pass 2, the roofs and vaults beyond the nave
  drawRoof(c, field, ROOF_BOX, (x, z) => z > 28, P.roof)
  both(m => put(c, m(8), 10, CH.z0, P.roof.full)) // where the Lady chapel's lean-to tucks under the chapel's gable
  drawVault(c, vaultH, [1, W - 1, 29, SIZE[2] - 1], (x, z) => z > 28, P.vault, rib)
  // Pass 3, depth: the choir's flyers (z 48), the transepts' buttresses, the fleche over the crossing
  both((m, f) => {
    const dir = f('east') === 'east' ? 1 : -1
    flyingButtress(c, { x: m(5), z: 48, dir, step: 8, top: 16, wallX: m(13) }, { pier: P.dress, stairs: P.dressStairs, pin: P.post, cap: P.finial })
    for (const x of [1, 5]) {
      for (let y = 1; y <= 16; y++) c.add(m(x), y, X.z0 - 1, y <= 3 ? plinthMix(m(x), y, 28) : P.dress)
      for (let y = 1; y <= 8; y++) c.add(m(x), y, X.z0 - 2, plinthMix(m(x), y, 27))
      stair(c, m(x), 9, X.z0 - 2, P.dressStairs, 'south')
      stair(c, m(x), 17, X.z0 - 1, P.dressStairs, 'south')
    }
  })
  // the fleche: a little copper lantern on the crossing's ridge, then a needle
  for (let y = 38; y <= 42; y++) for (let x = MID - 1; x <= MID + 1; x++) for (let z = 33; z <= 35; z++) {
    const corner = x !== MID && z !== 34
    const mid = x === MID && z === 34
    if (y >= 40 && y <= 41 && !corner) continue // open on all four faces, hollow
    if (mid && y < 42) continue
    if (c.get(x, y, z)) continue
    c.set(x, y, z, y === 42 ? P.copper : corner ? P.copper : P.copper)
  }
  for (let y = 43; y <= 48; y++) c.set(MID, y, 34, y === 43 ? P.copper : 'waxed_weathered_copper')
  c.set(MID, 49, 34, P.finial, { facing: 'up', powered: 'false', waterlogged: 'false' })
  // Pass 4, interior: the altar, the choir stalls, the chapels' lecterns and candles, the sacristy, the lamps
  c.set(...ALTAR.gold, 'gold_block')
  candle(c, ALTAR.gold[0], 3, ALTAR.gold[2])
  stair(c, 18, 2, 50, P.dressStairs, 'east', 'top'); stair(c, 20, 2, 50, P.dressStairs, 'west', 'top')
  for (const x of [17, 21]) { c.set(x, 2, 50, P.dress); candle(c, x, 3, 50, 2) }
  for (let x = 16; x <= 22; x++) for (let y = 2; y <= 6; y++) c.set(x, y, 52, (x === 16 || x === 22 || y === 6) ? P.dress : y === 5 && x === MID ? 'gold_block' : 'chiseled_stone_bricks')
  for (const x of [16, 22]) candle(c, x, 7, 52, 4)
  both((m, f) => { for (let z = 42; z <= 46; z++) stair(c, m(15), 2, z, `${P.wood}_stairs`, f('west')) })
  for (const [x, y, z] of LECTERNS) c.set(x, y, z, 'lectern', { facing: 'north', has_book: 'false', powered: 'false' })
  for (const [x, z] of [[3, 44], [W - 3, 44], [10, 48]]) { c.set(x, 1, z, P.dress); candle(c, x, 2, z, 3) }
  door(c, ...SAC.door, 'north', 'left')
  c.set(...SAC.brew, 'brewing_stand', { has_bottle_0: 'false', has_bottle_1: 'false', has_bottle_2: 'false' })
  c.set(...SAC.cauldron, 'cauldron')
  for (let z = 29; z <= 39; z++) for (let x = 18; x <= 20; x++) c.set(x, 1, z, P.carpet)
  chandelier(c, MID, 34, 23, 4)
  chandelier(c, MID, 45, 23, 4)
  for (const [x, z] of [[10, 43], [W - 10, 43], [W - 10, 46]]) lantern(c, x, field.top(x, z) - 1, z, true)
  both(m => lantern(c, m(3), field.top(3, 43) - 2, 43, true))

  // ==== Stage IV: The Towers and the Rose Window ====================================================================
  c.begin(4)
  for (const t of TOWERS) {
    const L = t.cx < MID ? t.x0 : t.x1 // the outer face
    // Pass 1, the upper storey (y 23-29) with lancets, the belfry floor (y 30)
    for (let y = 23; y <= 29; y++) for (let x = t.x0; x <= t.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
      if (x !== t.x0 && x !== t.x1 && z !== T.z0 && z !== T.z1) continue
      const lan = ((z === T.z0 && (x === t.cx - 1 || x === t.cx + 1)) || (x === L && (z === 5 || z === 7))) && y >= 24 && y <= 28
      if (lan) c.set(x, y, z, glass(x, y, z), { waterlogged: 'false' })
      else c.set(x, y, z, (x === t.x0 || x === t.x1) && (z === T.z0 || z === T.z1) ? P.dress : wallMix(x, y, z))
    }
    for (let x = t.x0; x <= t.x1; x++) for (let z = T.z0; z <= T.z1; z++) if (!(x === t.ladder[0] && z === t.ladder[1])) c.set(x, T.floor, z, x === t.x0 || x === t.x1 || z === T.z0 || z === T.z1 ? P.dress : 'stone_bricks')
    for (let y = 23; y <= T.floor; y++) c.set(t.ladder[0], y, t.ladder[1], 'ladder', { facing: t.ladder[2], waterlogged: 'false' })
    // Pass 2, the belfry (y 31-35): two louvred lights a face between corner piers and a mullion, the bells hung
    // from its ceiling (y 36)
    for (let y = T.belfry[0]; y <= T.belfry[1]; y++) for (let x = t.x0; x <= t.x1; x++) for (let z = T.z0; z <= T.z1; z++) {
      const ex = x === t.x0 || x === t.x1, ez = z === T.z0 || z === T.z1
      if (!ex && !ez) continue
      const o = ez && !ex ? x - t.x0 : z - T.z0 // offset along the face
      const light = (o >= 2 && o <= 4) || (o >= 6 && o <= 8)
      if (ex && ez) { c.set(x, y, z, P.dress); continue }
      if (!light) { c.set(x, y, z, P.dress); continue }
      const face = ez ? (z === T.z0 ? 'north' : 'south') : (x === t.x0 ? 'west' : 'east')
      if (y === T.belfry[1] && (o === 2 || o === 4 || o === 6 || o === 8)) {
        const away = ez ? (o === 2 || o === 6 ? 'west' : 'east') : (o === 2 || o === 6 ? 'north' : 'south')
        stair(c, x, y, z, P.dressStairs, away, 'top'); continue
      }
      c.set(x, y, z, `${P.louvre}`, { facing: face, half: 'bottom', open: 'true', powered: 'false', waterlogged: 'false' })
    }
    for (let x = t.x0; x <= t.x1; x++) for (let z = T.z0; z <= T.z1; z++) if (x === t.x0 || x === t.x1 || z === T.z0 || z === T.z1 || z === T.cz) c.set(x, T.ceiling, z, P.dress) // a ring and the bells' beam
    // Pass 3, the parapet: a corbelled ring (y 37), merlons (y 38), pinnacles at the corners, the needle spire
    for (let x = t.x0 - 1; x <= t.x1 + 1; x++) for (let z = T.z0 - 1; z <= T.z1 + 1; z++) {
      const ex = x === t.x0 - 1 || x === t.x1 + 1, ez = z === T.z0 - 1 || z === T.z1 + 1
      if (ex || ez) {
        if (field.covered(x, z) || (x >= 13 && x <= 25)) continue // the nave's roof and the aisles' run in there
        if (ex && ez) c.set(x, 37, z, P.dress)
        else stair(c, x, 37, z, P.dressStairs, ez ? (z < T.z0 ? 'south' : 'north') : (x < t.x0 ? 'east' : 'west'), 'top')
        if ((x + z) % 2 === 0 || (ex && ez)) c.set(x, 38, z, P.dress)
      } else if (oct(x - t.cx, z - T.cz) > 3.9) c.set(x, 37, z, 'stone_bricks') // the spire's footing, open under its hollow
    }
    for (const [x, z] of [[t.x0, T.z0], [t.x1, T.z0], [t.x0, T.z1], [t.x1, T.z1]]) {
      c.set(x, 38, z, P.dress)
      for (let y = 39; y <= 41; y++) c.set(x, y, z, P.post)
      c.set(x, 42, z, P.finial, { facing: 'up', powered: 'false', waterlogged: 'false' })
    }
    needleSpire(c, { cx: t.cx, cz: T.cz, y0: T.spire[0], y1: T.spire[1], r0: 5, lucarneY: 41 }, { tile: P.roof.full, finial: P.finial, slit: P.dress })
  }
  for (const [x, y, z] of BELLS) c.set(x, y, z, 'bell', { attachment: 'ceiling', facing: 'north', powered: 'false' })
  // Pass 4, the west gable with the rose: white eye, yellow spokes, light blue field, red rim, an andesite frame
  const R = F.rose
  roseWindow(c, { cx: R.cx, cy: R.cy, z: F.z, r: R.r }, (dx, dy, d) => {
    if (d <= 1.2) return P.rose.eye
    if (d > R.r - 0.8) return P.rose.rim
    if (dx === 0 || dy === 0 || Math.abs(dx) === Math.abs(dy)) return P.rose.spoke
    return P.rose.field
  }, P.dress)
  for (let x = F.x0; x <= F.x1; x++) {
    const top = naveH(x)
    for (let y = F.top + 1; y <= top; y++) put(c, x, y, F.z, y === F.top + 1 ? P.dress : stone(x, y, F.z))
    if (x === MID) { c.set(x, top + 1, F.z, P.dress); c.set(x, top + 2, F.z, P.post); c.set(x, top + 3, F.z, P.post) }
    else stair(c, x, top + 1, F.z, P.dressStairs, x < MID ? 'east' : 'west')
  }
  return c
}

// --- The brief's checks, asserted on every stage ----------------------------------------------------------------------
const BANNED = ['calcite', 'bedrock', 'barrier', 'command_block', 'structure_block', 'structure_void', 'jigsaw', 'spawner',
  'light', 'fire', 'sand', 'gravel', 'oak_leaves', 'snow']
const COUNTS = [
  ['white glass', 1, ['white_stained_glass', 'white_stained_glass_pane']], ['light blue glass', 1, ['light_blue_stained_glass', 'light_blue_stained_glass_pane']],
  ['yellow glass', 1, ['yellow_stained_glass', 'yellow_stained_glass_pane']], ['red glass', 1, ['red_stained_glass', 'red_stained_glass_pane']],
  ['pews', 10, ['dark_oak_stairs']], ['gold', 1, ['gold_block']], ['candles', 1, ['white_candle', 'candle']],
  ['banners', 2, ['red_wall_banner', 'white_wall_banner', 'blue_wall_banner']], ['lecterns', 3, ['lectern']], ['brewing stand', 1, ['brewing_stand']],
  ['polished andesite', 50, ['polished_andesite']], ['deepslate tiles', 200, ['deepslate_tiles', 'deepslate_tile_stairs']],
  ['cracked and mossy', 50, ['cracked_stone_bricks', 'mossy_stone_bricks']], ['bells', 3, ['bell']],
]

export function verify(c = draw()) {
  const snaps = [1, 2, 3, 4].map(n => c.stageCanvas(n))
  const changed = stagesOnlyAdd(snaps)
  if (changed) throw new Error(changed)
  const out = []
  for (let n = 1; n <= STAGES; n++) {
    const s = snaps[n - 1]
    const cells = [...s.cells.keys()].map(k => k.split(',').map(Number))
    const seen = new Set(cells.filter(([, y]) => y === 0).map(p => p.join(',')))
    const todo = [...seen].map(k => k.split(',').map(Number))
    while (todo.length) {
      const [x, y, z] = todo.pop()
      for (let dx = -1; dx <= 1; dx++) for (let dy = -1; dy <= 1; dy++) for (let dz = -1; dz <= 1; dz++) {
        if (!dx && !dy && !dz) continue
        const k = `${x + dx},${y + dy},${z + dz}`
        if (s.cells.has(k) && !seen.has(k)) { seen.add(k); todo.push([x + dx, y + dy, z + dz]) }
      }
    }
    const floating = cells.filter(p => !seen.has(p.join(',')))
    if (floating.length) throw new Error(`stage ${n}: ${floating.length} blocks not grounded, e.g. ${floating.slice(0, 3).join(' ')}`)
    for (const [k, v] of s.cells) {
      const id = v.id.slice(10)
      if (BANNED.includes(id)) throw new Error(`stage ${n}: ${id} at ${k}`)
      const [x, y, z] = k.split(',').map(Number)
      if (id === 'lantern') {
        const hang = v.props.hanging === 'true'
        if (!(hang ? s.get(x, y + 1, z) : s.get(x, y - 1, z))) throw new Error(`stage ${n}: a lantern at ${k} with nothing ${hang ? 'above' : 'under'} it`)
      }
      if (id === 'bell' && !s.get(x, y + 1, z)) throw new Error(`stage ${n}: a bell at ${k} hangs from nothing`)
      if (id.endsWith('candle') && !s.get(x, y - 1, z)) throw new Error(`stage ${n}: a candle at ${k} on air`)
      if (id === 'lectern' && v.props.has_book !== 'false') throw new Error(`stage ${n}: a lectern with a book at ${k}`)
      if (id.endsWith('_door') && v.props.half === 'lower') {
        const up = s.get(x, y + 1, z)
        if (!up || up.id !== v.id || up.props.facing !== v.props.facing) throw new Error(`stage ${n}: a door at ${k} without its upper half`)
        const [fx, fz] = { north: [0, -1], south: [0, 1], east: [1, 0], west: [-1, 0] }[v.props.facing]
        for (const sgn of [1, -1]) for (const dy of [0, 1]) {
          const b = s.get(x + sgn * fx, y + dy, z + sgn * fz)
          if (b && !b.id.endsWith('_carpet')) throw new Error(`stage ${n}: the door at ${k} is blocked at ${x + sgn * fx},${y + dy},${z + sgn * fz}`)
        }
      }
    }
    out.push(cells.length)
  }
  const s = snaps[3]
  const cells = [...s.cells.keys()].map(k => k.split(',').map(Number))
  const ext = i => Math.max(...cells.map(p => p[i])) - Math.min(...cells.map(p => p[i])) + 1
  const top = Math.max(...cells.map(p => p[1]))
  if (ext(0) < 37 || ext(0) > 41 || ext(2) < 53 || ext(2) > 57 || top < 51 || top > 53 || Math.min(...cells.map(p => p[1])) !== 0) throw new Error(`size ${ext(0)} x ${ext(2)}, top ${top}`)
  const xs = cells.map(p => p[0]), mid = (Math.min(...xs) + Math.max(...xs)) / 2
  const front = side => Math.max(...cells.filter(p => p[2] <= 12 && (side ? p[0] > mid : p[0] < mid)).map(p => p[1]))
  if (front(0) < 51 || front(0) > 53 || front(1) < 51 || front(1) > 53) throw new Error(`front towers ${front(0)} / ${front(1)}`)
  const row = cells.filter(p => p[2] === 25 && p[1] === 2).map(p => p[0])
  const span = Math.max(...row) - Math.min(...row) + 1
  if (span < 25 || span > 29) throw new Error(`the body at z 25 spans ${span}`)
  const total = s.cells.size
  if (total < 12600 || total > 15400) throw new Error(`block budget: ${total}, wants 12,600-15,400`)
  const count = ids => [...s.cells.values()].filter(v => ids.includes(v.id.slice(10))).length
  const counts = {}
  for (const [what, min, ids] of COUNTS) { counts[what] = count(ids); if (counts[what] < min) throw new Error(`${what}: ${counts[what]}, wants ${min}`) }
  for (const [k, v] of s.cells) if (v.id === 'minecraft:bell' && Number(k.split(',')[1]) < 31) throw new Error(`a bell below y 31 at ${k}`)
  // the altar and the wedding
  if (!s.get(19, 1, 49)) throw new Error('nothing under the altar spot')
  for (const p of [[19, 2, 49], [19, 3, 49], [20, 2, 49], [20, 3, 49]]) if (s.get(...p)) throw new Error(`the couple's cell ${p} is drawn`)
  if (s.name(19, 2, 50) !== 'minecraft:gold_block') throw new Error('no gold block behind the altar spot')
  if (!s.get(20, 1, 49)) throw new Error('no floor under the groom')
  for (const [x, y, z] of GUESTS) {
    if (!s.get(x, y - 1, z)) throw new Error(`no floor under the guest at ${x},${y},${z}`)
    for (const dy of [0, 1]) if (s.get(x, y + dy, z)) throw new Error(`a guest's cell ${x},${y + dy},${z} is drawn`)
  }
  const [cx, cy, cz] = CAMERA
  for (let i = 0; i <= 40; i++) {
    const t = i / 40, x = Math.round(cx), y = Math.round(cy + (3 - cy) * t), z = Math.round(cz + (49 - cz) * t)
    const b = s.get(x, y, z)
    if (b && z < 49) throw new Error(`the camera's line is blocked at ${x},${y},${z} by ${b.id}`)
  }
  return { perStage: out, total, counts }
}

export default function build(api) {
  const c = draw()
  const v = verify(c)
  console.error(`great_cathedral_b: stage totals ${v.perStage.join(' / ')}; ${v.total} blocks; ${JSON.stringify(v.counts)}`)
  const n = Number(process.env.STAGE ?? STAGES)
  const s = c.stageCanvas(n)
  const cut = process.env.CUT
  const canvas = cut !== undefined ? s.cutAbove(Number(cut)) : s
  const name = `great_cathedral_b_s${n}${cut !== undefined ? `_cut${cut}` : ''}`
  return { structure: canvas.toStructure(api), metadata: { name } }
}
