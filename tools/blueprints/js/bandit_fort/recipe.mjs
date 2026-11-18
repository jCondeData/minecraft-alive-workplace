// The Bandit Fort, the recipe: draws plan.mjs's three levels from one function (LEVEL=1, 2 or 3; each level is the
// whole fort, drawn over the last in the same box) from the shared parts library (../parts, ROADMAP 49), in passes:
// ground, block-out and structure, openings, depth and detail, interior, light. Then it checks every cell the Java and
// the tests read (plan.mjs PINS, the brief's tables) and refuses to build if one is wrong.
// Build: LEVEL=<n> node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955
//        (CUT=<y> builds a cutaway with everything above y removed).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, PINS, MASSES, OPENINGS } from './plan.mjs'
import {
  Canvas, mix, stair, slab, door, quoins, gableRoof, gableWall, bargeTrim, fenceGates, gateGable, spit, logStack,
  arrowLoop, warStandard, stakeWall, poleTent, legTower, stepLeanTo, jettyBand,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const hash = (a, b) => { let h = Math.imul(a, 73856093) ^ Math.imul(b, 19349663); h = Math.imul(h ^ (h >>> 13), 1274126177); return (h ^ (h >>> 16)) >>> 0 }

export function draw(level) {
  const c = new Canvas(SIZE)
  const ground = mix(P.ground, 11)
  const bank = mix(P.bank, 5)
  const stone = mix(P.keepStone, 23)
  const W = MASSES.wall, G = MASSES.gate, K = MASSES.keep, S = MASSES.stable
  const towers = level >= 2, stable = level >= 2, keep = level >= 3
  const inTower = (x, z) => towers && z <= 4 && (x <= 4 || x >= 24)
  const lantern = (x, y, z, hanging) => c.set(x, y, z, 'lantern', { hanging: String(hanging), waterlogged: 'false' })

  // --- Pass 1: ground (y = 0 is the ground itself: every cell solid) ------------------------------------------------
  c.fill(0, 0, 0, 28, 0, 28, ground)
  for (let i = 0; i <= 28; i++) for (const [x, z] of [[i, 0], [i, 28], [0, i], [28, i]]) c.set(x, 0, z, bank)
  for (let z = 0; z <= 10; z++) for (let x = 13; x <= 15; x++) if (!(z >= 9 && x !== 14)) c.set(x, 0, z, P.path) // the gate road to the fire
  for (let z = 12; z <= 16; z++) c.set(14, 0, z, P.path)

  // --- Pass 2: block-out and structure ------------------------------------------------------------------------------
  // The stake wall: ragged tops (4 or 5, the corners 6), a dripstone point on each; the fire-step one in, all round
  const underGate = (x, z) => z === 0 && x >= G.x0 && x <= G.x1
  const height = (i, x, z) => {
    if (underGate(x, z)) return 3
    if (inTower(x, z)) return 4
    if (stable && x === 28 && z >= S.z0 - 1 && z <= S.z1 + 1) return 7
    if ((x === 0 || x === 28) && (z === 0 || z === 28)) return 6
    const r = hash(x, z) % 10 // ragged: stakes cut by eye, in runs, a tall one now and then
    return r < 4 ? 4 : r < 9 ? 5 : 6
  }
  // most stakes bark-on; one in four fresh-cut (a repair), so the wall reads as poles, not a plank fence
  const stakeAt = (i, x, z) => (x === 0 && z === 7) || hash(z, x) % 4 ? P.stake : `stripped_${P.stake}`
  stakeWall(c, W, { stake: stakeAt, tip: P.tip, bank, step: P.step }, {
    height,
    tipped: (i, x, z) => !underGate(x, z) && !inTower(x, z),
    skip: (x, z) => (z === 0 && x >= G.way[0] && x <= G.way[1]) || (keep && z === 28 && x >= K.body.x0 && x <= K.body.x1),
    stepAt: (x, z) => !(z <= 4 && (x <= 4 || x >= 24)) && !(z <= 2 && x >= G.x0 && x <= G.x1)
      && !(stable && x === 27 && z >= S.z0 - 1 && z <= S.z1 + 1) && !(keep && z === 27 && x >= 8 && x <= 20),
  })

  // The gatehouse: log cheeks, the gates two high, a lintel, a 45-degree gable over the gateway
  for (const x of [G.x0 + 1, G.x1 - 1]) for (let z = G.z0; z <= G.z1; z++) for (let y = 1; y <= 3; y++) c.set(x, y, z, P.stake, { axis: 'y' })
  for (const y of OPENINGS.gate.ys) fenceGates(c, OPENINGS.gate.gates, y, OPENINGS.gate.z, `${P.wood}_fence_gate`, 'south')
  for (let x = G.way[0]; x <= G.way[1]; x++) for (const z of [G.z0, G.z1]) c.set(x, 3, z, `stripped_${P.wood}_log`, { axis: 'x' })
  gateGable(c, { x0: G.x0, x1: G.x1, z0: G.z0, z1: G.z1, eaveY: G.eaveY }, P.timberRoof,
    { infill: `${P.wood}_planks`, king: P.pole, cap: P.timberRoof.slab })
  for (let x = G.x0 + 1; x <= G.x1 - 1; x++) c.set(x, G.eaveY, 1, P.pole, { axis: 'x' }) // the tie beam the lanterns hang from
  lantern(13, 3, 1, true); lantern(15, 3, 1, true)

  // Watchtowers (II): on legs over the front corners, the woodpiles under them
  if (towers) for (const t of MASSES.towers) {
    legTower(c, t, {
      leg: P.stake, deck: `${P.wood}_planks`, joist: `stripped_${P.wood}_log`, board: `${P.wood}_planks`,
      rail: `${P.wood}_fence`, brace: `${P.wood}_stairs`, roof: P.timberRoof, cap: P.timberRoof.slab, hatch: `${P.wood}_trapdoor`,
    })
    // the base boarded on its yard sides into a woodshed, a door in its back wall to the ladder
    const inX = t.outward.includes('west') ? t.x1 : t.x0
    for (let y = 1; y <= t.deckY - 2; y++) {
      for (let x = t.x0 + 1; x < t.x1; x++) c.set(x, y, t.z1, `${P.wood}_planks`)
      for (let z = t.z0 + 1; z < t.z1; z++) c.set(inX, y, z, `${P.wood}_planks`)
    }
    for (let z = t.z0 + 1; z < t.z1; z++) c.set(inX, t.deckY - 1, z, `stripped_${P.wood}_log`, { axis: 'z' }) // the side joist
    door(c, [(t.x0 + t.x1) / 2, 1, t.z1], 'north', 'south', { wood: P.wood })
  }

  // The stable (II): a stepped lean-to on the east wall, posts and a boarded header on the yard side, two stalls
  if (stable) {
    const ys = stepLeanTo(c, { low: S.low, high: S.high, z0: S.z0, z1: S.z1, eaveY: S.eaveY, over: 0 },
      { full: `${P.wood}_planks`, slab: P.timberRoof.slab, board: `${P.wood}_planks` })
    for (const z of S.posts) for (let y = 1; y < ys[S.low]; y++) c.set(S.low, y, z, P.stake, { axis: 'y' })
    for (const [za, zb] of S.stalls) {
      for (let z = za; z <= zb; z++) {
        for (let y = 3; y < ys[S.low]; y++) c.set(S.low, y, z, `${P.wood}_planks`)
        c.set(S.low, 1, z, `${P.wood}_fence_gate`, { facing: 'east', open: 'false', in_wall: 'false', powered: 'false' })
        c.set(S.low, 2, z, `${P.wood}_trapdoor`, { facing: 'west', half: 'top', open: 'true', powered: 'false', waterlogged: 'false' }) // the half-door's top leaf, swung open
      }
    }
    for (let x = S.low + 1; x <= S.high - 1; x++) c.set(x, 1, 15, `${P.wood}_fence`) // the stall divider
    c.set(27, 1, 15, 'hay_block', { axis: 'y' }); c.set(27, 2, 15, 'hay_block', { axis: 'x' }) // the manger's hay
    for (const z of [13, 17]) c.set(27, 1, z, 'water_cauldron', { level: '3' })
    for (let x = S.low + 1; x <= S.high; x++) for (let z = S.z0 + 1; z <= S.z1 - 1; z++) { const hay = x === S.high || hash(x, z) % 5 === 0; c.set(x, 0, z, hay ? 'hay_block' : 'packed_mud', hay ? { axis: 'y' } : {}) }
    lantern(25, 5, 13, true); lantern(25, 5, 17, true)
  }

  // The keep (III): robbed stone, the fort's back wall its own
  if (keep) {
    const B = K.body, R = K.room
    for (let x = B.x0 - 1; x <= B.x1 + 1; x++) for (let z = B.z0 - 1; z <= B.z1; z++) c.set(x, 0, z, stone)
    for (let x = R.x0; x <= R.x1; x++) for (let z = R.z0; z <= R.z1; z++) c.set(x, 0, z, P.keepFloor)
    for (let y = 1; y <= K.wallTop; y++) for (let x = B.x0; x <= B.x1; x++) for (let z = B.z0; z <= B.z1; z++)
      if (x === B.x0 || x === B.x1 || z === B.z0 || z === B.z1) c.set(x, y, z, stone)
    quoins(c, [[B.x0, B.z0], [B.x1, B.z0], [B.x0, B.z1], [B.x1, B.z1]], 1, K.wallTop, P.quoin[0], P.quoin[1])
    for (let x = R.x0; x <= R.x1; x++) for (let z = R.z0; z <= R.z1; z++) c.set(x, K.floorY, z, P.keepFloor)
    // a battered foot (stairs leaning on the wall) on the sides and the front, the door's 3 x 3 left open
    for (let z = B.z0; z <= B.z1 - 1; z++) { stair(c, B.x0 - 1, 1, z, P.keepStairs, 'east'); stair(c, B.x1 + 1, 1, z, P.keepStairs, 'west') }
    for (let x = B.x0 - 1; x <= B.x1 + 1; x++) {
      if (x >= 12 && x <= 16) continue
      const corner = x === B.x0 - 1 || x === B.x1 + 1
      c.set(x, 1, B.z0 - 1, P.keepStairs, { facing: 'south', half: 'bottom', shape: corner ? (x < 14 ? 'outer_left' : 'outer_right') : 'straight', waterlogged: 'false' })
    }
    // the jetty: corbels under it, a dark oak sill ring and a band of boards between posts, one block out
    for (let z = B.z0; z <= B.z1 - 1; z++) { stair(c, B.x0 - 1, K.wallTop, z, P.keepStairs, 'east', 'top'); stair(c, B.x1 + 1, K.wallTop, z, P.keepStairs, 'west', 'top') }
    for (let x = B.x0 - 1; x <= B.x1 + 1; x++) stair(c, x, K.wallTop, B.z0 - 1, P.keepStairs, 'south', 'top')
    jettyBand(c, { x0: B.x0, z0: B.z0, x1: B.x1, z1: B.z1, y: K.jettyY, bay: 3, back: 0 },
      { sill: `${P.trim}_log`, post: `${P.trim}_log`, board: `${P.wood}_planks`, floorId: `${P.wood}_planks` })
    // the roof: a 45-degree dark oak gable across the jetty, its front gable over the yard on a one-block overhang
    const roof = gableRoof(c, { ...K.roof, along: [K.roof.along[0] - 1, K.roof.along[1]] }, P.keepRoof)
    for (const line of [K.roof.along[0], K.roof.along[1]]) {
      gableWall(c, 'z', line, K.roof.across, K.roof.eaveY, roof.under, (u, y) => {
        if (u === 14 && (y === 12 || y === 13) && line === K.roof.along[0]) return 'glass_pane'
        if (u === 14 || u === 11 || u === 17) return [`${P.trim}_log`, { axis: 'y' }]
        return `${P.wood}_planks`
      })
    }
    bargeTrim(c, 'z', K.roof.along[0] - 1, K.roof.across, roof.under, `${P.trim}_stairs`)
  }

  // --- Pass 3: openings ---------------------------------------------------------------------------------------------
  if (keep) {
    const kd = OPENINGS.keepDoor
    for (const x of [13, 15]) for (let y = 1; y <= 3; y++) c.set(x, y, 17, 'stone_bricks')
    c.set(14, 3, 17, 'chiseled_stone_bricks')
    door(c, kd.at, kd.facing, 'north', { wood: P.trim })
    const pd = OPENINGS.postern
    for (const x of [13, 15]) for (let y = 1; y <= 3; y++) c.set(x, y, 28, 'stone_bricks')
    c.set(14, 3, 28, 'stone_bricks')
    door(c, pd.at, pd.facing, 'south', { wood: P.wood })
    for (const [x, y, z, inward] of OPENINGS.loops) { arrowLoop(c, x, y, z, inward, P.keepStairs); c.set(x, y, z, 'iron_bars'); c.set(x, y + 1, z, 'iron_bars') }
    for (const [x, y, z] of OPENINGS.chamberWindows) for (const yy of [y, y + 1]) c.set(x, yy, z, 'iron_bars')
    const st = OPENINGS.stair
    for (const cell of st.well) c.clear(...cell)
    st.cells.forEach(([x, y, z]) => { for (let yy = 1; yy < y; yy++) c.set(x, yy, z, stone); stair(c, x, y, z, `${P.wood}_stairs`, st.rises) })
    for (let z = 19; z <= 22; z++) c.set(11, 6, z, `${P.wood}_fence`)
  }

  // --- Pass 4: depth and detail -------------------------------------------------------------------------------------
  // The fire: a stone ring, a spit across it, log benches either side, a lantern post at each side of the yard
  const [fx, fy, fz] = MASSES.fire.at
  c.set(fx, fy, fz, 'campfire', { facing: 'south', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  stair(c, fx, fy, fz - 1, 'cobblestone_stairs', 'south'); stair(c, fx, fy, fz + 1, 'cobblestone_stairs', 'north')
  for (const [dx, dz] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) slab(c, fx + dx, fy, fz + dz, 'cobblestone_slab')
  spit(c, fx - 1, fx + 1, fz, fy, `${P.wood}_fence`)
  logStack(c, [[11, 1, 10], [11, 1, 11], [11, 1, 12], [17, 1, 10], [17, 1, 11], [17, 1, 12]], P.stake, 'z')
  for (const x of [9, 19]) { c.set(x, 1, 12, `${P.wood}_fence`); c.set(x, 2, 12, `${P.wood}_fence`); lantern(x, 3, 12, false) }

  // Tents: red and black wool on ridge poles, carpet hems, bedrolls inside
  for (const t of MASSES.tents) {
    const { inside } = poleTent(c, { a0: t.a0, a1: t.a1, l0: t.l0, l1: t.l1, y0: 1, back: t.back },
      { canvas: P.canvas[t.colour], pole: P.pole, fence: `${P.wood}_fence`, hem: P.hem[t.colour], flap: `${P.wood}_trapdoor` })
    const mid = (t.a0 + t.a1) / 2
    c.set(mid - 1, 1, t.back === 'l0' ? t.l0 + 1 : t.l1 - 1, 'barrel', { facing: 'up', open: 'false' })
  }
  // The King's pavilion (I, II): where his keep will stand; his standard before it
  if (!keep) {
    const pv = MASSES.pavilion
    const canvas = (x, y) => (y === 1 ? P.canvas.red : P.canvas.black)
    const { ridgeY } = poleTent(c, { a0: pv.a0, a1: pv.a1, l0: pv.l0, l1: pv.l1, y0: 1, back: pv.back },
      { canvas, pole: P.pole, fence: `${P.trim}_fence`, hem: P.hem.black })
    for (let i = 0; pv.a0 + i < 14; i++) for (let x = pv.a0 + i + 1; x < pv.a1 - i; x++) c.set(x, 1 + i, pv.l0, canvas(x, 1 + i))
    for (const x of [13, 15]) for (const y of [1, 2])
      c.set(x, y, pv.l0, `${P.trim}_trapdoor`, { facing: x === 13 ? 'east' : 'west', half: 'bottom', open: 'true', powered: 'false', waterlogged: 'false' })
    for (let x = 13; x <= 15; x++) for (let z = 22; z <= 25; z++) if (!c.get(x, 1, z)) c.set(x, 1, z, 'red_carpet')
    for (const x of [11, 17]) c.set(x, 1, 25, 'barrel', { facing: 'up', open: 'false' })
    void ridgeY
    warStandard(c, [11, 1, 18], { logHeight: 2, fenceHeight: 2, arm: [1, 0], rotation: 8 },
      { log: P.pole, fence: `${P.trim}_fence`, banner: 'black_banner', lantern: 'lantern' })
  }
  // Woodpiles in the front corners (the towers' legs stand over them from II)
  logStack(c, [[2, 1, 1], [3, 1, 1], [3, 2, 1], [3, 1, 2]], P.stake, 'x')
  logStack(c, [[25, 1, 1], [26, 1, 1], [25, 2, 1], [25, 1, 2]], P.stake, 'x')
  // The back corners: archery butts on the west, a woodpile and barrels on the east
  for (const x of [3, 6]) { c.set(x, 1, 24, `${P.wood}_fence`); c.set(x, 2, 24, 'target') }
  logStack(c, [[23, 1, 26], [24, 1, 26], [25, 1, 26], [23, 2, 26], [24, 2, 26]], P.stake, 'x')
  for (const [x, y, z] of [[26, 1, 23], [26, 1, 24], [26, 2, 24], [25, 1, 23]]) c.set(x, y, z, 'barrel', { facing: 'up', open: 'false' })

  // --- Pass 5: interior (III, the keep) -----------------------------------------------------------------------------
  if (keep) {
    // the throne on its dais, the treasure beside it, the postern passage behind
    for (let x = 12; x <= 16; x++) { for (const z of [23, 24]) c.set(x, 1, z, P.dais); stair(c, x, 1, 22, P.daisStairs, 'south') }
    stair(c, 14, 2, 24, `${P.trim}_stairs`, 'north')
    for (const x of [13, 15]) c.set(x, 2, 24, `${P.trim}_fence`)
    for (let y = 1; y <= 4; y++) c.set(14, y, 25, `${P.trim}_log`, { axis: 'y' })
    c.set(14, 4, 24, 'black_wall_banner', { facing: 'north' })
    for (let z = 19; z <= 21; z++) c.set(14, 1, z, 'red_carpet')
    const [tx, ty, tz] = PINS.treasure
    c.set(tx, ty, tz, 'chest', { facing: 'west', type: 'single', waterlogged: 'false' })
    c.set(18, 1, 23, 'barrel', { facing: 'up', open: 'false' })
    c.set(18, 1, 25, 'raw_gold_block'); c.set(17, 1, 25, 'barrel', { facing: 'up', open: 'false' }); c.set(18, 2, 25, 'gold_block')
    for (const z of [19, 23]) c.set(18, 3, z, 'red_wall_banner', { facing: 'west' })
    for (const [x, z] of [[12, 20], [16, 20], [12, 26], [16, 26]]) lantern(x, 4, z, true)
    // the King's chamber: his map table, loot, a bedroll and a rug
    c.set(16, 6, 25, 'cartography_table')
    for (const [x, y, z] of [[18, 6, 27], [18, 7, 27], [17, 6, 27], [18, 6, 26]]) c.set(x, y, z, 'barrel', { facing: 'up', open: 'false' })
    for (const z of [25, 26]) { c.set(11, 6, z, 'brown_carpet'); c.set(12, 6, z, 'brown_carpet') }
    c.set(11, 6, 27, 'hay_block', { axis: 'x' }); c.set(12, 6, 27, 'hay_block', { axis: 'x' })
    for (let x = 13; x <= 16; x++) for (let z = 20; z <= 22; z++) c.set(x, 6, z, 'red_carpet')
    lantern(14, 8, 21, true); lantern(14, 8, 25, true)
    lantern(12, 3, 16, true); lantern(16, 3, 16, true)
    c.set(12, 4, 16, `${P.trim}_fence`); c.set(16, 4, 16, `${P.trim}_fence`)
    c.set(14, 4, 16, 'black_wall_banner', { facing: OPENINGS.banner.facing })
  }

  check(c, level)
  return c
}

// The cells the Java and the tests read (the brief's tables): refuse to build a level that breaks one.
function check(c, level) {
  const bad = []
  const is = (x, y, z, id) => c.name(x, y, z) === `minecraft:${id}` || bad.push(`(${x},${y},${z}) is ${c.name(x, y, z)}, wants ${id}`)
  const air = (x, y, z) => !c.get(x, y, z) || bad.push(`(${x},${y},${z}) is ${c.name(x, y, z)}, wants air`)
  const walk = (x, y, z) => !c.get(x, y, z) || c.name(x, y, z).endsWith('_carpet') || bad.push(`(${x},${y},${z}) is ${c.name(x, y, z)}, wants walkable`)
  for (let x = 0; x < 29; x++) for (let z = 0; z < 29; z++) if (!c.get(x, 0, z)) bad.push(`ground (${x},0,${z}) open`)
  air(14, 1, 14); air(14, 2, 14)
  for (const [x, y, z] of [...PINS.yardMen, ...PINS.hillGone]) { air(x, y, z); air(x, y + 1, z) }
  is(0, 1, 7, 'spruce_log'); is(14, 1, 0, 'spruce_fence_gate'); is(14, 1, 11, 'campfire'); is(5, 3, 7, 'stripped_dark_oak_log')
  if (level >= 2) {
    is(0, 7, 0, 'spruce_log'); is(1, 3, 2, 'ladder'); is(27, 3, 2, 'ladder'); is(27, 1, 15, 'hay_block')
    for (const [x, y, z] of PINS.towerMen) { air(x, y, z); air(x, y + 1, z); if (!c.get(x, y - 1, z)) bad.push(`no deck under ${x},${y},${z}`) }
  }
  if (level >= 3) {
    is(14, 1, 17, 'dark_oak_door'); is(14, 4, 16, 'black_wall_banner'); is(14, 2, 24, 'dark_oak_stairs'); is(18, 1, 24, 'chest')
    for (let x = 13; x <= 15; x++) for (let z = 14; z <= 16; z++) { air(x, 1, z); air(x, 2, z) }
    walk(14, 1, 21); air(14, 2, 21)
    if (!c.name(14, 1, 28).endsWith('_door') || !c.name(14, 2, 28).endsWith('_door')) bad.push('postern is not a door')
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) if (c.name(18 + dx, 1, 24 + dz) === 'minecraft:chest') bad.push('treasure chest has a neighbour chest')
  }
  for (const [k] of c.cells) if (Number(k.split(',')[1]) > 17) bad.push(`above y 17: ${k}`)
  if (bad.length) throw new Error(`Level ${level} breaks the brief:\n  ` + bad.join('\n  '))
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": []}\n')
  const name = level === 1 ? 'bandit_fort' : `bandit_fort_${level}`
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${name}_cut${cut}` : name } }
}
