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
  arrowLoop, warStandard, stakeWall, poleTent, legTower, stepLeanTo, jettyBand, buttress, horseTrough, cartWheel, cartBed,
  coverHoop, wallWalk, fightingStage, bretesche, pennant, bedroll,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const hash = (a, b) => { let h = Math.imul(a, 73856093) ^ Math.imul(b, 19349663); h = Math.imul(h ^ (h >>> 13), 1274126177); return (h ^ (h >>> 16)) >>> 0 }

export function draw(level) {
  const c = new Canvas(SIZE)
  const ground = mix(P.ground, 11)
  const bank = mix(P.bank, 5)
  const stone = mix(P.keepStone, 23)
  const W = MASSES.wall, G = MASSES.gate, K = MASSES.keep, S = MASSES.stable, WK = MASSES.walk
  const towers = level >= 2, stable = level >= 2, keep = level >= 3
  const inTower = (x, z) => towers && z <= 4 && (x <= 4 || x >= 24)
  const lantern = (x, y, z, hanging) => c.set(x, y, z, 'lantern', { hanging: String(hanging), waterlogged: 'false' })
  const barrel = (x, y, z, facing = 'up') => c.set(x, y, z, 'barrel', { facing, open: 'false' })
  const plank = `${P.wood}_planks`, fence = `${P.wood}_fence`, stripped = `stripped_${P.wood}_log`
  const underStable = (z) => stable && z >= S.z0 && z <= S.z1
  const underKeep = (x) => keep && x >= K.body.x0 - 1 && x <= K.body.x1 + 1

  // --- Pass 1: ground (y = 0 is the ground itself: every cell solid) ------------------------------------------------
  c.fill(0, 0, 0, 28, 0, 28, ground)
  for (let i = 0; i <= 28; i++) for (const [x, z] of [[i, 0], [i, 28], [0, i], [28, i]]) c.set(x, 0, z, bank)
  for (let z = 0; z <= 10; z++) for (let x = 13; x <= 15; x++) if (!(z >= 9 && x !== 14)) c.set(x, 0, z, P.path) // the gate road to the fire
  for (let z = 12; z <= 16; z++) c.set(14, 0, z, P.path)

  // --- Pass 2: block-out and structure ------------------------------------------------------------------------------
  // The stake wall: panels of four stakes between taller posts on the sides and back, the back corners raised; the
  // front ragged and lower over its fire-step. A dripstone point on every stake.
  const underGate = (x, z) => z === 0 && x >= G.x0 && x <= G.x1
  const backCorner = (x, z) => z === 28 && (x === 0 || x === 28)
  const isPost = (x, z) => (z === 0 || z === 28 ? x : z) % W.panel === 0
  const height = (i, x, z) => {
    if (underGate(x, z)) return 3
    if (inTower(x, z)) return 4
    if (stable && x === 28 && z >= S.z0 - 1 && z <= S.z1 + 1) return 7
    if (backCorner(x, z)) return W.cornerY
    if ((z === 28 && (x === 1 || x === 27)) || (z === 27 && (x === 0 || x === 28))) return W.cornerY - 1
    if (z === 0) return isPost(x, z) || x === 0 || x === 28 ? W.frontY + 1 : W.frontY
    return isPost(x, z) ? W.postY : W.panelY
  }
  // bark-on stakes in panels, a fresh-cut (stripped) post between each four, and now and then a stripped repair
  const stakeAt = (i, x, z) => (x === 0 && z === 7) ? P.stake
    : isPost(x, z) || hash(z, x) % 9 === 0 ? `stripped_${P.stake}` : P.stake
  const frontStep = (x, z) => z === 1 && ((x >= 5 && x <= 10) || (x >= 18 && x <= 23))
  stakeWall(c, W, { stake: stakeAt, tip: P.tip, bank, step: P.step }, {
    height,
    tipped: (i, x, z) => !underGate(x, z) && !inTower(x, z) && !backCorner(x, z),
    skip: (x, z) => (z === 0 && x >= G.way[0] && x <= G.way[1]) || (keep && z === 28 && x >= K.body.x0 && x <= K.body.x1),
    stepAt: frontStep,
  })
  for (const [x, rot] of [[0, 2], [28, 14]]) c.set(x, W.cornerY + 1, 28, P.skull, { rotation: String(rot) }) // the corner posts' trophies

  // The wall-walk: x 1 and x 27 from z 5 back, z 27 across the back; left out where the stable and the keep stand
  const runs = [
    { cells: range(WK.from, 26).map(z => [1, z]), along: 'z' },
    { cells: range(WK.from, 26).filter(z => !underStable(z)).map(z => [27, z]), along: 'z' },
    { cells: range(1, 27).filter(x => !underKeep(x)).map(x => [x, 27]), along: 'x' },
  ]
  const stageAt = (x, z) => MASSES.stages.find(s => (s.along === 'z' ? x === s.wall + s.inward && z >= s.l0 && z <= s.l1
    : z === s.wall + s.inward && x >= s.l0 && x <= s.l1))
  for (const run of runs) for (const seg of splitRuns(run.cells, run.along)) {
    wallWalk(c, seg, WK.y, { deck: plank, post: P.stake, brace: `${P.wood}_stairs` }, {
      every: WK.every, along: run.along,
      wide: (x, z) => { const s = stageAt(x, z); return s ? (s.along === 'z' ? [x + s.inward, z] : [x, z + s.inward]) : null },
    })
  }
  // the fighting stages: the stakes behind cut level as a screen, a little gable over the walk on posts, a lamp
  for (const s of MASSES.stages) {
    const { hang } = fightingStage(c, { along: s.along, wall: s.wall, l0: s.l0, l1: s.l1, inward: s.inward, deckY: WK.y, screenTop: s.screenTop, bay: 4 },
      { screen: P.stake, post: `stripped_${P.stake}`, roof: P.timberRoof, board: plank })
    c.set(hang[0], hang[1], hang[2], 'chain', { axis: 'y', waterlogged: 'false' })
    lantern(hang[0], hang[1] - 1, hang[2], true)
  }
  // ladders up to the walk, each on a post of its own
  for (const [x, z, facing, px] of MASSES.walkLadders) {
    for (let y = 1; y < WK.y; y++) c.set(px, y, z, P.stake, { axis: 'y' })
    if (!c.get(px, WK.y, z)) c.set(px, WK.y, z, plank)
    for (let y = 1; y <= WK.y; y++) c.set(x, y, z, 'ladder', { facing, waterlogged: 'false' })
  }

  // The gatehouse: log cheeks, the gates two high, a lintel, a 45-degree gable over the gateway
  for (const x of [G.x0 + 1, G.x1 - 1]) for (let z = G.z0; z <= G.z1; z++) for (let y = 1; y <= 3; y++) c.set(x, y, z, P.stake, { axis: 'y' })
  for (const y of OPENINGS.gate.ys) fenceGates(c, OPENINGS.gate.gates, y, OPENINGS.gate.z, `${P.wood}_fence_gate`, 'south')
  for (let x = G.way[0]; x <= G.way[1]; x++) for (const z of [G.z0, G.z1]) c.set(x, 3, z, stripped, { axis: 'x' })
  gateGable(c, { x0: G.x0, x1: G.x1, z0: G.z0, z1: G.z1, eaveY: G.eaveY }, P.timberRoof,
    { infill: plank, king: P.pole, cap: P.timberRoof.slab })
  for (let x = G.x0 + 1; x <= G.x1 - 1; x++) c.set(x, G.eaveY, 1, P.pole, { axis: 'x' }) // the tie beam the lanterns hang from
  lantern(13, 3, 1, true); lantern(15, 3, 1, true)

  // Watchtowers (II): on legs over the front corners, the woodpiles under them
  if (towers) for (const t of MASSES.towers) {
    const apex = legTower(c, t, {
      leg: P.stake, deck: plank, joist: stripped, board: plank,
      rail: fence, brace: `${P.wood}_stairs`, roof: P.timberRoof, cap: P.timberRoof.slab, hatch: `${P.wood}_trapdoor`,
    })[0]
    // the base boarded on its yard sides into a woodshed, a door in its back wall to the ladder
    const west = t.outward.includes('west')
    const inX = west ? t.x1 : t.x0
    for (let y = 1; y <= t.deckY - 2; y++) {
      for (let x = t.x0 + 1; x < t.x1; x++) c.set(x, y, t.z1, plank)
      for (let z = t.z0 + 1; z < t.z1; z++) c.set(inX, y, z, plank)
    }
    for (let z = t.z0 + 1; z < t.z1; z++) c.set(inX, t.deckY - 1, z, stripped, { axis: 'z' }) // the side joist
    door(c, [(t.x0 + t.x1) / 2, 1, t.z1], 'north', 'south', { wood: P.wood })
    // the platform: a lantern on a chain from the hat, a barrel of arrows, a log to sit on; a red pennant on the apex
    const mx = (t.x0 + t.x1) / 2, inner = west ? t.x1 - 1 : t.x0 + 1
    pennant(c, apex, { post: P.stake, fence, banner: P.pennant, rotation: 8 })
    for (let y = t.topY + 1; y < apex[1]; y++) c.set(mx, y, 2, 'chain', { axis: 'y', waterlogged: 'false' })
    lantern(mx, t.topY, 2, true)
    barrel(inner, t.deckY + 1, t.z0 + 1)
    c.set(inner, t.deckY + 1, t.z1 - 1, stripped, { axis: 'x' })
  }

  // The stable (II): a stepped lean-to on the east wall, posts and a boarded header on the yard side, two stalls
  if (stable) {
    const ys = stepLeanTo(c, { low: S.low, high: S.high, z0: S.z0, z1: S.z1, eaveY: S.eaveY, over: 0 },
      { full: plank, slab: P.timberRoof.slab, board: plank })
    for (const z of S.posts) for (let y = 1; y < ys[S.low]; y++) c.set(S.low, y, z, P.stake, { axis: 'y' })
    for (const [za, zb] of S.stalls) {
      for (let z = za; z <= zb; z++) {
        for (let y = 3; y < ys[S.low]; y++) c.set(S.low, y, z, plank)
        c.set(S.low, 1, z, `${P.wood}_fence_gate`, { facing: 'east', open: 'false', in_wall: 'false', powered: 'false' })
        c.set(S.low, 2, z, `${P.wood}_trapdoor`, { facing: 'west', half: 'top', open: 'true', powered: 'false', waterlogged: 'false' }) // the half-door's top leaf, swung open
      }
    }
    for (let x = S.low + 1; x <= S.high - 1; x++) c.set(x, 1, 15, fence) // the stall divider
    c.set(27, 1, 15, 'hay_block', { axis: 'y' }); c.set(27, 2, 15, 'hay_block', { axis: 'x' }) // the manger's hay
    for (const z of [13, 17]) c.set(27, 1, z, 'water_cauldron', { level: '3' })
    for (let x = S.low + 1; x <= S.high; x++) for (let z = S.z0 + 1; z <= S.z1 - 1; z++) { const hay = x === S.high || hash(x, z) % 5 === 0; c.set(x, 0, z, hay ? 'hay_block' : 'packed_mud', hay ? { axis: 'y' } : {}) }
    lantern(25, 5, 13, true); lantern(25, 5, 17, true)
  }

  // The keep (III): robbed stone, its back wall one in from the fort's back edge so the edge carries buttresses
  if (keep) {
    const B = K.body, R = K.room
    for (let x = B.x0 - 1; x <= B.x1 + 1; x++) for (let z = B.z0 - 1; z <= 28; z++) c.set(x, 0, z, stone)
    for (let x = R.x0; x <= R.x1; x++) for (let z = R.z0; z <= R.z1; z++) c.set(x, 0, z, P.keepFloor)
    for (let y = 1; y <= K.wallTop; y++) for (let x = B.x0; x <= B.x1; x++) for (let z = B.z0; z <= B.z1; z++)
      if (x === B.x0 || x === B.x1 || z === B.z0 || z === B.z1) c.set(x, y, z, stone)
    quoins(c, [[B.x0, B.z0], [B.x1, B.z0], [B.x0, B.z1], [B.x1, B.z1]], 1, K.wallTop, P.quoin[0], P.quoin[1])
    for (let x = R.x0; x <= R.x1; x++) for (let z = R.z0; z <= R.z1; z++) c.set(x, K.floorY, z, P.keepFloor)
    // a battered foot (stairs leaning on the wall) on the sides and the front, the door's 3 x 3 left open
    for (let z = B.z0; z <= B.z1; z++) { stair(c, B.x0 - 1, 1, z, P.keepStairs, 'east'); stair(c, B.x1 + 1, 1, z, P.keepStairs, 'west') }
    for (let x = B.x0 - 1; x <= B.x1 + 1; x++) {
      if (x >= 12 && x <= 16) continue
      const corner = x === B.x0 - 1 || x === B.x1 + 1
      c.set(x, 1, B.z0 - 1, P.keepStairs, { facing: 'south', half: 'bottom', shape: corner ? (x < 14 ? 'outer_left' : 'outer_right') : 'straight', waterlogged: 'false' })
    }
    // the back (z 28): clasping buttresses at the corners and two piers, the battered foot between them
    for (const [x, top] of K.buttresses) buttress(c, x, 28, 1, top, stone, P.keepStairs, 'north')
    for (const x of [10, 12, 16, 18]) stair(c, x, 1, 28, P.keepStairs, 'north')
    // the jetty: corbels under it on every side, a dark oak sill ring and a band of boards between posts, one block out
    for (let z = B.z0; z <= B.z1; z++) { stair(c, B.x0 - 1, K.wallTop, z, P.keepStairs, 'east', 'top'); stair(c, B.x1 + 1, K.wallTop, z, P.keepStairs, 'west', 'top') }
    for (let x = B.x0 - 1; x <= B.x1 + 1; x++) stair(c, x, K.wallTop, B.z0 - 1, P.keepStairs, 'south', 'top')
    for (let x = B.x0 + 1; x <= B.x1 - 1; x++) stair(c, x, K.wallTop, 28, P.keepStairs, 'north', 'top')
    jettyBand(c, { x0: B.x0, z0: B.z0, x1: B.x1, z1: B.z1, y: K.jettyY, bay: 3, back: 1 },
      { sill: `${P.trim}_log`, post: `${P.trim}_log`, board: plank, floorId: plank })
    // the roof: a 45-degree dark oak gable across the jetty, its front gable over the yard on a one-block overhang;
    // a window in each gable (the loft's light)
    const roof = gableRoof(c, { ...K.roof, along: [K.roof.along[0] - 1, K.roof.along[1]] }, P.keepRoof)
    for (const line of [K.roof.along[0], K.roof.along[1]]) {
      gableWall(c, 'z', line, K.roof.across, K.roof.eaveY, roof.under, (u, y) => {
        if (u === 14 && (y === 12 || y === 13)) return 'glass_pane'
        if (u === 14 || u === 11 || u === 17) return [`${P.trim}_log`, { axis: 'y' }]
        return plank
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
    // the postern: deep-set, through the back wall into a stone porch, a bretesche corbelled out over it
    const pd = OPENINGS.postern, po = K.porch
    for (const x of [po.x0, po.x1]) for (let y = 1; y <= po.top + 1; y++) c.set(x, y, 28, 'stone_bricks')
    c.set(14, po.top, 28, 'chiseled_stone_bricks')
    c.clear(14, 1, 27); c.clear(14, 2, 27)
    door(c, pd.at, pd.facing, 'south', { wood: P.wood })
    bretesche(c, { x0: K.bretesche.x0, x1: K.bretesche.x1, z: 27, out: 28, y: K.bretesche.y },
      { corbel: P.keepStairs, board: `${P.trim}_planks`, slit: 'iron_bars', lid: P.keepRoof.slab })
    c.clear(14, K.bretesche.y + 1, 27) // the chamber looks out through the bretesche's slit
    for (const [x, y, z, inward] of OPENINGS.loops) { arrowLoop(c, x, y, z, inward, P.keepStairs); c.set(x, y, z, 'iron_bars'); c.set(x, y + 1, z, 'iron_bars') }
    for (const [x, y, z] of OPENINGS.backSlits) { arrowLoop(c, x, y, z, 'north', P.keepStairs); c.set(x, y, z, 'iron_bars'); c.set(x, y + 1, z, 'iron_bars') }
    for (const [x, y, z] of OPENINGS.chamberWindows) for (const yy of [y, y + 1]) c.set(x, yy, z, 'iron_bars')
    const st = OPENINGS.stair
    for (const cell of st.well) c.clear(...cell)
    st.cells.forEach(([x, y, z]) => { for (let yy = 1; yy < y; yy++) c.set(x, yy, z, stone); stair(c, x, y, z, `${P.wood}_stairs`, st.rises) })
    for (let z = 19; z <= 22; z++) c.set(11, 6, z, fence) // the well's rail
    const ll = OPENINGS.loftLadder
    for (let y = ll.y0; y <= ll.y1; y++) c.set(ll.x, y, ll.z, 'ladder', { facing: ll.facing, waterlogged: 'false' })
  }

  // --- Pass 4: depth and detail -------------------------------------------------------------------------------------
  // The fire: a stone ring, a spit across it, log benches either side, a lantern post at each side of the yard
  const [fx, fy, fz] = MASSES.fire.at
  c.set(fx, fy, fz, 'campfire', { facing: 'south', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  stair(c, fx, fy, fz - 1, 'cobblestone_stairs', 'south'); stair(c, fx, fy, fz + 1, 'cobblestone_stairs', 'north')
  for (const [dx, dz] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) slab(c, fx + dx, fy, fz + dz, 'cobblestone_slab')
  spit(c, fx - 1, fx + 1, fz, fy, fence)
  logStack(c, [[11, 1, 10], [11, 1, 11], [11, 1, 12], [17, 1, 10], [17, 1, 11], [17, 1, 12]], P.stake, 'z')
  for (const x of [9, 19]) { c.set(x, 1, 12, fence); c.set(x, 2, 12, fence); lantern(x, 3, 12, false) }
  c.set(20, 1, 11, 'grindstone', { face: 'floor', facing: 'west' }) // where the blades are kept sharp

  // Tents: one undyed canvas, a grey hem, a red pennant on each ridge's front end
  for (const t of MASSES.tents) {
    const { ridgeY } = poleTent(c, { a0: t.a0, a1: t.a1, l0: t.l0, l1: t.l1, y0: 1, back: t.back },
      { canvas: P.canvas, pole: P.pole, fence, hem: P.hem, flap: `${P.wood}_trapdoor` })
    const mid = (t.a0 + t.a1) / 2
    const front = t.back === 'l0' ? t.l1 + 1 : t.l0 - 1, shut = t.back === 'l0' ? t.l0 : t.l1, dir = t.back === 'l0' ? 1 : -1
    pennant(c, [mid, ridgeY, front], { post: P.pole, fence, banner: P.pennant, rotation: t.back === 'l0' ? 0 : 8 })
    barrel(mid - 1, 1, shut + dir)
    if (t.furnished) { // the West tent, made up: two bedrolls, the kit barrel, a lantern on the ridge pole
      bedroll(c, [mid - 1, 1, shut + 2 * dir], dir > 0 ? '+z' : '-z', 3, P.bedroll)
      bedroll(c, [mid + 1, 1, shut + dir], dir > 0 ? '+z' : '-z', 3, P.bedroll)
      lantern(mid, ridgeY - 1, shut + dir, true)
    } else bedroll(c, [mid + 1, 1, shut + dir], dir > 0 ? '+z' : '-z', 2, P.bedroll)
  }
  // The King's pavilion (I, II): where his keep will stand; the same canvas over a black skirt, his standard before it
  if (!keep) {
    const pv = MASSES.pavilion
    const canvas = (x, y) => (y === 1 ? P.kingSkirt : P.canvas)
    const { ridgeY } = poleTent(c, { a0: pv.a0, a1: pv.a1, l0: pv.l0, l1: pv.l1, y0: 1, back: pv.back },
      { canvas, pole: P.pole, fence: `${P.trim}_fence`, hem: P.hem })
    for (let i = 0; pv.a0 + i < 14; i++) for (let x = pv.a0 + i + 1; x < pv.a1 - i; x++) c.set(x, 1 + i, pv.l0, canvas(x, 1 + i))
    for (const x of [13, 15]) for (const y of [1, 2])
      c.set(x, y, pv.l0, `${P.trim}_trapdoor`, { facing: x === 13 ? 'east' : 'west', half: 'bottom', open: 'true', powered: 'false', waterlogged: 'false' })
    pennant(c, [14, ridgeY, pv.l0 - 1], { post: P.pole, fence: `${P.trim}_fence`, banner: P.kingBanner, rotation: 8 })
    for (let z = pv.l0 + 1; z <= 23; z++) c.set(14, 1, z, P.runner)
    for (const x of [11, 17]) barrel(x, 1, 24)
    bedroll(c, [15, 1, 24], '-z', 3, { mat: P.kingHem, pillow: P.bedroll.pillow })
    lantern(14, ridgeY - 1, 23, true)
    warStandard(c, [11, 1, 18], { logHeight: 2, fenceHeight: 2, arm: [1, 0], rotation: 8 },
      { log: P.pole, fence: `${P.trim}_fence`, banner: P.kingBanner, lantern: 'lantern' })
  }
  // Woodpiles in the front corners (the towers' legs stand over them from II)
  logStack(c, [[2, 1, 1], [3, 1, 1], [3, 2, 1], [3, 1, 2]], P.stake, 'x')
  logStack(c, [[25, 1, 1], [26, 1, 1], [25, 2, 1], [25, 1, 2]], P.stake, 'x')
  // The front yard: archery butts by the west wall, a horse trough by the east
  const Bu = MASSES.butts
  for (const [x, z] of Bu.targets) { c.set(x, 1, z, fence); c.set(x, 2, z, 'target', { power: '0' }) }
  c.set(Bu.bale[0], 1, Bu.bale[1], 'hay_block', { axis: 'x' })
  horseTrough(c, MASSES.trough.water, 1, stripped)
  // The back-west corner: a merchant's cart the band took on the road, its cover torn, a crate fallen off it
  const Ca = MASSES.cart
  for (const hub of Ca.wheels) cartWheel(c, hub, 'z', { rim: { full: plank, stairs: `${P.wood}_stairs` }, hubLog: stripped })
  cartBed(c, { ...Ca.bed, along: 'x' }, { beam: stripped, deck: plank })
  coverHoop(c, { x0: Ca.bed.x0, x1: Ca.bed.x1, z: Ca.bed.z0, y0: Ca.bed.y + 1, top: Ca.bed.y + 3, slump: 'south', steps: 2 },
    { rib: fence, cloth: P.canvas }, (x, y, z) => (x === Ca.bed.x0 + 2 && z > Ca.bed.z0) || (x === Ca.bed.x0 + 1 && z === Ca.bed.z1))
  barrel(Ca.bed.x1 + 1, 1, Ca.bed.z0 + 1, 'east')
  for (const x of [Ca.bed.x0, Ca.bed.x0 + 1, Ca.bed.x1]) barrel(x, Ca.bed.y + 1, Ca.bed.z0 + 1) // the loot still under the cover
  // The back-east corner: a woodpile against the walk, barrels of loot
  logStack(c, [[22, 1, 26], [23, 1, 26], [24, 1, 26], [22, 2, 26], [23, 2, 26]], P.stake, 'x')
  for (const [x, y, z] of [[22, 1, 21], [23, 1, 21], [22, 2, 21], [22, 1, 22]]) barrel(x, y, z)

  // --- Pass 5: interior (III, the keep) -----------------------------------------------------------------------------
  if (keep) {
    // The throne hall: the throne on its dais between two braziers, the treasure beside it, the postern behind
    for (let x = 12; x <= 16; x++) { for (const z of [23, 24]) c.set(x, 1, z, P.dais); stair(c, x, 1, 22, P.daisStairs, 'south') }
    stair(c, 14, 2, 24, `${P.trim}_stairs`, 'north')
    for (const x of [13, 15]) c.set(x, 2, 24, `${P.trim}_fence`)
    for (const x of [12, 16]) c.set(x, 2, 24, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
    for (let y = 1; y <= 4; y++) c.set(14, y, 25, `${P.trim}_log`, { axis: 'y' })
    c.set(14, 4, 24, 'black_wall_banner', { facing: 'north' })
    for (let z = 19; z <= 21; z++) { c.set(14, 1, z, P.runner); if (z <= 20) { c.set(13, 1, z, P.runnerEdge); c.set(15, 1, z, P.runnerEdge) } }
    const [tx, ty, tz] = PINS.treasure
    c.set(tx, ty, tz, 'chest', { facing: 'west', type: 'single', waterlogged: 'false' })
    barrel(18, 1, 23)
    c.set(18, 1, 25, 'raw_gold_block'); barrel(17, 1, 25); c.set(18, 2, 25, 'gold_block')
    // the feasting table down the east side: a trestle of top slabs, benches either side, candles on it
    for (let z = 19; z <= 21; z++) {
      slab(c, 17, 1, z, P.keepRoof.slab, 'top')
      stair(c, 16, 1, z, `${P.wood}_stairs`, 'west'); stair(c, 18, 1, z, `${P.wood}_stairs`, 'east')
    }
    c.set(17, 2, 20, 'candle', { candles: '3', lit: 'true', waterlogged: 'false' })
    for (const z of [19, 23]) c.set(18, 3, z, 'red_wall_banner', { facing: 'west' })
    // the west side: the stair up (a lantern at its foot), barrels of arms by the postern
    barrel(10, 1, 25); barrel(10, 1, 26, 'east'); barrel(11, 1, 26)
    for (const [x, z] of [[12, 20], [16, 22], [12, 26], [16, 26], [14, 19], [10, 18]]) lantern(x, 4, z, true)
    // The King's chamber: his map table and stool, his loot, his bed of furs, a rug, a writing board
    c.set(12, 6, 25, 'cartography_table'); stair(c, 12, 6, 24, `${P.trim}_stairs`, 'north')
    for (const [x, y, z] of [[18, 6, 26], [18, 7, 26], [17, 6, 26], [18, 6, 25]]) barrel(x, y, z)
    bedroll(c, [16, 6, 26], '-z', 3, { mat: P.kingHem, pillow: P.bedroll.pillow })
    for (const x of [16, 17]) slab(c, x, 6, 18, P.keepRoof.slab, 'top') // his writing board under the window, a stool
    stair(c, 16, 6, 19, `${P.trim}_stairs`, 'south'); c.set(17, 7, 18, 'candle', { candles: '2', lit: 'true', waterlogged: 'false' })
    bedroll(c, [15, 6, 26], '-z', 3, { mat: P.kingHem, pillow: P.bedroll.pillow })
    for (let x = 12; x <= 16; x++) for (let z = 20; z <= 22; z++) c.set(x, 6, z, x === 12 || x === 16 ? P.runnerEdge : P.runner)
    lantern(14, 8, 21, true); lantern(14, 8, 25, true); lantern(17, 8, 19, true)
    // The war loft under the roof: the guard's bedrolls along the eaves, their arms by the back gable, a lantern on a
    // chain from the ridge
    for (const [x, z] of [[10, 20], [10, 24], [18, 23]]) bedroll(c, [x, 10, z], '+z', 3, P.bedroll)
    for (const [x, y, z] of [[12, 10, 27], [16, 10, 27], [16, 11, 27], [17, 10, 27]]) barrel(x, y, z)
    for (let y = 14; y <= 16; y++) c.set(14, y, 22, 'chain', { axis: 'y', waterlogged: 'false' })
    lantern(14, 13, 22, true)
    // the door's lanterns and the King's banner over it
    lantern(12, 3, 16, true); lantern(16, 3, 16, true)
    c.set(12, 4, 16, `${P.trim}_fence`); c.set(16, 4, 16, `${P.trim}_fence`)
    c.set(14, 4, 16, 'black_wall_banner', { facing: OPENINGS.banner.facing })
  }

  check(c, level)
  return c
}

const range = (a, b) => Array.from({ length: b - a + 1 }, (_, i) => a + i)
// cells in a line, split where a gap breaks the run
function splitRuns(cells, along) {
  const out = []
  let cur = []
  for (const cell of cells) {
    const prev = cur[cur.length - 1]
    const k = along === 'x' ? 0 : 1
    if (prev && cell[k] !== prev[k] + 1) { out.push(cur); cur = [] }
    cur.push(cell)
  }
  if (cur.length) out.push(cur)
  return out
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
