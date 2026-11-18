// The Inn (Version B, ROADMAP 49: the Arcade Inn), the recipe: assembles plan.mjs from the shared parts library (../parts)
// in passes (block-out and structure, roofs and gables, openings, depth and detail, interior, ground and light) for
// LEVEL = 1..3, each level the one before plus what it adds: II the coach gate, the stable and the walled yard, III the
// beer cellar under the taproom (a LiftedCanvas: the file holds it at the bottom; above the ground III is II exactly,
// but for the hatch). Writes furniture.json (the furniture spots) and, for III, out/cellar.txt. All three: build.sh.
//   LEVEL=2 node "$MC_BUILDER/scripts/run-build.mjs" recipe.mjs --out-dir out --data-version 3955   (CUT=<y>: a cutaway)
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { IDS, SIZES, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  LiftedCanvas, mix, stair, slab, plinth, floor, post, beamRing, wallPanel, window, door, gableRoof, gableWall, bargeTrim,
  flight, railingRun, furnitureParts, cellar, hatchLadder, quoins, lampPost, arcade, moulding, skirt, bench, coachGate,
  stall, jettyBrackets, pitchDormer,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const P = PALETTE
const plaster = mix(P.infill, 21), partition = mix(P.partition, 22)
const stone = mix(P.stone, 5), base = mix(P.plinth, 9), steps = mix(P.steps, 4), cellarWall = mix(P.cellarWall, 3)
const yardStone = mix([['cobblestone', 5], ['andesite', 2], ['coarse_dirt', 1], ['stone', 2]], 13) // nothing that falls
const range = (a, b) => Array.from({ length: b - a + 1 }, (_, i) => a + i)
const lantern = (c, x, y, z) => c.set(x, y, z, 'lantern', { hanging: 'true', waterlogged: 'false' })

// --- Level I: the Arcade Inn ---------------------------------------------------------------------------------------
function levelOne(c) {
  const T = MASSES.taproom, U = MASSES.upper, A = MASSES.arcade, Ch = MASSES.chimney

  // Pass 1: block-out and structure. The stone taproom, the guest floor over it jettied out to z = 1, the beam rings.
  plinth(c, T.x0, T.z0, T.x1, T.z1, base)
  floor(c, T.x0 + 1, T.z0 + 1, T.x1 - 1, T.z1 - 1, 0, P.floor)
  for (const [a, b] of [[[T.x0, T.z0], [T.x1, T.z0]], [[T.x0, T.z1], [T.x1, T.z1]], [[T.x0, T.z0], [T.x0, T.z1]], [[T.x1, T.z0], [T.x1, T.z1]]])
    wallPanel(c, a, b, 1, T.top, stone)
  quoins(c, [[T.x0, T.z0], [T.x1, T.z0], [T.x0, T.z1], [T.x1, T.z1]], 1, T.top, P.quoin, P.dressed)
  beamRing(c, U.x0, U.z0, U.x1, U.z1, U.floorY, P.frame) // the sill beams: the jetty's front one rides on brackets
  floor(c, U.x0 + 1, U.z0 + 1, U.x1 - 1, U.z1 - 1, U.floorY, P.floor)
  for (const [a, b] of [[[U.x0, U.z0], [U.x1, U.z0]], [[U.x0, U.z1], [U.x1, U.z1]], [[U.x0, U.z0], [U.x0, U.z1]], [[U.x1, U.z0], [U.x1, U.z1]]])
    wallPanel(c, a, b, U.floorY + 1, U.top, plaster)
  for (const x of A.posts) { post(c, x, U.z0, U.floorY + 1, U.top, P.frame); post(c, x, U.z1, U.floorY + 1, U.top, P.frame) }
  for (const z of [U.z0, 6, U.z1]) { post(c, U.x0, z, U.floorY + 1, U.top, P.frame); post(c, U.x1, z, U.floorY + 1, U.top, P.frame) }
  beamRing(c, U.x0, U.z0, U.x1, U.z1, U.beamY, P.frame)
  floor(c, U.x0 + 1, U.z0 + 1, U.x1 - 1, U.z1 - 1, U.beamY, P.ceiling)

  // Pass 2: the roof and its gables. One slate roof, ridge along z, overhanging a block on every side.
  const roof = gableRoof(c, MASSES.roof, P.roof)
  const gable = back => (u, y) => {
    if ((u === 3 || u === 9) && y <= 11) return [P.frame, { axis: 'y' }]
    if (y === 12 && u >= 4 && u <= 8) return [P.frame, { axis: 'x' }]
    if (u === 6) return [P.frame, { axis: 'y' }] // the king post, through the collar to the ridge
    return plaster
  }
  gableWall(c, 'z', U.z0, [0, 12], U.beamY + 1, roof.under, gable(false))
  gableWall(c, 'z', U.z1, [0, 12], U.beamY + 1, roof.under, gable(true))
  bargeTrim(c, 'z', 0, [0, 12], roof.under, `${P.trim}_stairs`)
  bargeTrim(c, 'z', 12, [0, 12], roof.under, `${P.trim}_stairs`)
  lantern(c, 6, roof.ridgeY - 1, 0) // the inn's light, hung under the gable's peak
  const D = OPENINGS.dormer
  pitchDormer(c, { ...D, main: x => roof.under[x] ?? MASSES.roof.eaveY - 1 }, { roof: P.roof, frame: P.frame, infill: plaster, glass: P.glass })

  // the chimney: the inglenook's fire in the west wall, the flue up the wall line, a broad stack through the eave
  c.set(...Ch.fire, 'campfire', { facing: 'east', lit: 'true', signal_fire: 'false', waterlogged: 'false' })
  for (let y = 2; y <= U.beamY; y++) c.set(Ch.x, y, 6, stone)
  for (let y = U.beamY + 1; y < Ch.top; y++) for (const z of Ch.zs) c.set(Ch.x, y, z, stone)
  stair(c, Ch.x, Ch.top, 5, 'stone_brick_stairs', 'south')
  stair(c, Ch.x, Ch.top, 7, 'stone_brick_stairs', 'north')
  c.set(Ch.x, Ch.top, 6, P.dressed)
  c.set(Ch.x, Ch.top + 1, 6, 'campfire', { facing: 'north', lit: 'true', signal_fire: 'false', waterlogged: 'false' })

  // Pass 3: openings. The door, every window shuttered, the stair and its well, the upper rooms' plaster partitions.
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood })
  for (const w of OPENINGS.windows) if (w.from <= 1) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })
  for (const p of OPENINGS.gableWindows) c.set(...p, P.glass)
  const st = OPENINGS.stairs
  for (const [x, y, z] of st.well) c.clear(x, y, z)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  railingRun(c, OPENINGS.railing, P.wood)
  for (const p of OPENINGS.partitions) {
    wallPanel(c, p.from, p.to, U.floorY + 1, U.top - 1, partition)
    const axis = p.from[0] === p.to[0] ? 'z' : 'x'
    wallPanel(c, p.from, p.to, U.top, U.top, (x, y, z) => P.frame)
    for (let x = Math.min(p.from[0], p.to[0]); x <= Math.max(p.from[0], p.to[0]); x++)
      for (let z = Math.min(p.from[1], p.to[1]); z <= Math.max(p.from[1], p.to[1]); z++) c.set(x, U.top, z, P.frame, { axis })
  }
  for (const [x, z] of OPENINGS.partitionPosts) post(c, x, z, U.floorY + 1, U.top, P.frame)
  for (const d of OPENINGS.roomDoors) door(c, d.at, d.facing, d.facing, { wood: P.wood })

  // Pass 4: depth and detail. The arcade and its pentice, the jetty's brackets, a moulding round the guest floor and a
  // skirt round the taproom's foot (the shutters stand on them), the benches, the banner on its post.
  arcade(c, { x0: A.x0, x1: A.x1, z: A.z, posts: A.posts, beamY: A.beamY },
    { post: P.frame, beam: P.frame, bracket: `${P.trim}_stairs`, roof: P.roof, paving: P.paving, step: P.arcadeStep })
  jettyBrackets(c, A.posts.map(x => [x, A.beamY, U.z0]), `${P.trim}_stairs`, 'south')
  moulding(c, range(0, 12).map(z => [0, z]), U.floorY, `${P.trim}_stairs`, 'east')
  moulding(c, range(0, 12).map(z => [12, z]), U.floorY, `${P.trim}_stairs`, 'west')
  moulding(c, range(1, 11).map(x => [x, 12]), U.floorY, `${P.trim}_stairs`, 'north')
  skirt(c, range(T.z0, T.z1).map(z => [0, z]), 1, base, steps, 'east')
  skirt(c, range(T.z0, T.z1).map(z => [12, z]), 1, base, steps, 'west')
  skirt(c, range(T.x0, T.x1).map(x => [x, 12]), 1, base, steps, 'north')
  for (const [x, y, z, pot] of OPENINGS.sills) { stair(c, x, y, z, `${P.trim}_stairs`, 'north', 'top'); c.set(x, y + 1, z, pot) }
  for (const b of OUTSIDE.benches) bench(c, b, `${P.wood}_stairs`, 'north')
  for (const p of OUTSIDE.arcadeLamps) lantern(c, ...p)
  for (const w of OPENINGS.windows) {
    const pot = OUTSIDE.pots[w.name]
    if (pot) w.cells.filter(([, y]) => y === Math.min(...w.cells.map(q => q[1]))).forEach(([x, y, z], i) => c.set(x, y, z - 1, pot[i]))
  }
  const bn = OUTSIDE.banner
  c.set(bn.post[0], 0, bn.post[2], P.dressed)
  for (let i = 0; i < bn.height; i++) c.set(bn.post[0], bn.post[1] + i, bn.post[2], `${P.wood}_fence`)
  c.set(bn.post[0], bn.post[1] + bn.height, bn.post[2], `${bn.colour}_banner`, { rotation: String(bn.rotation) })

  // Pass 5: interior. The bar (pinned cells), the casks, the inglenook's mantel, then every planned piece.
  for (const z of [5, 6, 8, 9]) { c.set(8, 1, z, P.bar); slab(c, 8, 2, z, `${P.trim}_slab`) }
  c.set(8, 1, 7, 'aliveworkplace:shop_counter', { facing: 'west' })
  for (const z of [5, 6]) for (const y of [1, 2]) c.set(10, y, z, 'barrel', { facing: 'west', open: 'false' })
  c.set(9, 1, 10, 'barrel', { facing: 'north', open: 'false' })
  c.set(10, 1, 8, 'cauldron')
  for (const z of [5, 6, 7]) slab(c, 2, 3, z, 'stone_brick_slab', 'top') // the mantel shelf over the fire
  for (const z of [5, 7]) c.set(2, 4, z, 'candle', { candles: '3', lit: 'true', waterlogged: 'false' })
  c.set(2, 6, 10, 'potted_fern') // the stair head's corner
  for (const f of FURNITURE) {
    furnitureParts[f.part](c, f)
    if (f.candle) c.set(f.pos[0], f.pos[1] + 1, f.pos[2], 'candle', { candles: '2', lit: 'true', waterlogged: 'false' })
  }
}

// --- Level II: the coaching inn ------------------------------------------------------------------------------------
function levelTwo(c) {
  const S = MASSES.stable, G = MASSES.gate, Y = MASSES.yard
  // the back door from the taproom to the yard, a step down where the skirt was
  const bd = OPENINGS.backDoor
  c.clear(bd.at[0], 1, 12)
  door(c, bd.at, bd.facing, 'south', { wood: P.wood })

  // the yard and the passage: cobbles at y 0 (level with the floors), a wall round it
  for (let x = Y.x0; x <= Y.x1; x++) for (let z = Y.z0; z <= Y.z1; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, yardStone)
  for (let x = G.x0 + 1; x < S.x0; x++) for (let z = 0; z < Y.z0; z++) c.set(x, 0, z, yardStone)
  railingRun(c, [[Y.x0, 1, Y.z0], [Y.x0, 1, Y.z1], [Y.x1, 1, Y.z1], [Y.x1, 1, Y.z0]], null, 'cobblestone_wall')

  // the stable: a stone course, spruce boards in a dark frame, a beam ring, its own smaller tiled gable to the street,
  // stood two blocks clear of the inn's eave so the two roofs read as two (o: its x from the plan's first sketch at 16)
  const o = S.x0 - 16, mid = S.x0 + 3
  plinth(c, S.x0, S.z0, S.x1, S.z1, base)
  floor(c, S.x0 + 1, S.z0 + 1, S.x1 - 1, S.z1 - 1, 0, 'coarse_dirt')
  for (const [a, b] of [[[S.x0, S.z0], [S.x1, S.z0]], [[S.x0, S.z1], [S.x1, S.z1]], [[S.x0, S.z0], [S.x0, S.z1]], [[S.x1, S.z0], [S.x1, S.z1]]]) {
    wallPanel(c, a, b, 1, 1, base)
    wallPanel(c, a, b, 2, S.top, P.boards)
  }
  for (const x of [16, 18, 20, 22]) for (const z of [S.z0, S.z1]) post(c, x + o, z, 1, S.top, P.frame)
  for (const z of [5, 8]) for (const x of [S.x0, S.x1]) post(c, x, z, 1, S.top, P.frame)
  beamRing(c, S.x0, S.z0, S.x1, S.z1, S.beamY, P.frame)
  const sr = gableRoof(c, S.roof, P.roof)
  const sg = (u, y) => (u === mid && y === 8 ? [P.frame, { axis: 'y' }] : P.boards)
  gableWall(c, 'z', S.z0, S.roof.across, S.beamY + 1, sr.under, sg)
  gableWall(c, 'z', S.z1, S.roof.across, S.beamY + 1, sr.under, sg)
  bargeTrim(c, 'z', S.roof.along[0], S.roof.across, sr.under, `${P.trim}_stairs`)
  bargeTrim(c, 'z', S.roof.along[1], S.roof.across, sr.under, `${P.trim}_stairs`)
  // openings: the stable doors front and back, the hayloft door over the front one with its hoist lantern, barred windows
  door(c, OPENINGS.stableDoor.at, 'south', 'north', { wood: P.wood })
  door(c, OPENINGS.stableBackDoor.at, 'north', 'south', { wood: P.wood })
  door(c, OPENINGS.hayDoor.at, 'south', 'north', { wood: P.wood })
  c.set(mid, 8, 1, `${P.wood}_fence`); lantern(c, mid, 7, 1)
  for (const z of [S.z0, S.z1]) for (const x of [17, 21]) for (const y of [2, 3]) c.set(x + o, y, z, 'iron_bars')
  for (const x of [S.x0, S.x1]) for (const z of [3, 4, 6, 7, 9, 10]) c.set(x, 3, z, 'iron_bars')
  c.set(mid, 7, S.z1, 'iron_bars')
  // inside: three stalls on the east wall, a hayloft over the back half, its ladder, lanterns
  for (const [z0, z1, dv] of [[3, 4, 5], [6, 7, 8], [9, 10, null]]) stall(c, { back: S.x1, dir: -1, z0, z1, divider: dv }, { fence: `${P.wood}_fence` })
  floor(c, S.x0 + 1, 7, S.x1 - 1, S.z1 - 1, S.beamY, P.floor)
  for (let y = 1; y <= S.beamY; y++) c.set(17 + o, y, 10, 'ladder', { facing: 'east', waterlogged: 'false' })
  for (const [x, z] of [[20, 9], [21, 9], [21, 10], [20, 10], [21, 8]]) c.set(x + o, S.beamY + 1, z, 'hay_block', { axis: 'y' })
  c.set(20 + o, S.beamY + 2, 10, 'hay_block', { axis: 'y' })
  for (const z of [3, 4]) c.set(17 + o, 1, z, 'barrel', { facing: 'up', open: 'false' })
  lantern(c, mid, 4, 4); for (let y = 5; y < sr.ridgeY; y++) c.set(mid, y, 4, 'chain', { axis: 'y', waterlogged: 'false' })
  lantern(c, 18 + o, 4, 9)

  // the coach gate across the passage, its beam on the jetty's corner bracket and the stable's corner post
  coachGate(c, { x0: G.x0, x1: G.x1, z: G.z, beamY: 4, capCells: range(G.x0 + 1, G.x1 - 2) },
    { post: P.frame, beam: P.frame, bracket: `${P.trim}_stairs`, cap: `${P.trim}_slab` })
  lantern(c, (G.x0 + G.x1) / 2, 3, G.z)

  // the yard: a woodpile on the west wall, a trough and hay by the stable's back door, a bench, a lamp post
  const wp = OUTSIDE.woodpile
  for (let z = wp.z0; z <= wp.z1; z++) c.set(wp.x, 1, z, 'spruce_log', { axis: 'x' })
  for (let z = wp.z0 + 1; z < wp.z1; z++) c.set(wp.x, 2, z, 'spruce_log', { axis: 'x' })
  for (const p of OUTSIDE.hay) c.set(...p, 'hay_block', { axis: 'y' })
  for (const p of OUTSIDE.trough) c.set(...p, 'cauldron')
  bench(c, [[4, 1, 17], [5, 1, 17], [6, 1, 17]], `${P.wood}_stairs`, 'north')
  lampPost(c, OUTSIDE.yardLamp[0], 1, OUTSIDE.yardLamp[2], `${P.wood}_fence`, 2)
  // a carrier's cart left in the yard: a slab bed on trapdoor wheels, a barrel on it, its shaft down on the cobbles
  const ct = OUTSIDE.cart
  for (let x = ct.x; x <= ct.x + 1; x++) for (let z = ct.z; z <= ct.z + 2; z++) slab(c, x, 1, z, `${P.wood}_slab`, 'top')
  for (const [x, f] of [[ct.x - 1, 'west'], [ct.x + 2, 'east']]) c.set(x, 1, ct.z + 1, `${P.trim}_trapdoor`, { facing: f, half: 'bottom', open: 'true', powered: 'false', waterlogged: 'false' })
  c.set(ct.x, 2, ct.z + 1, 'barrel', { facing: 'up', open: 'false' })
  for (const x of [ct.x, ct.x + 1]) slab(c, x, 1, ct.z - 1, `${P.wood}_slab`, 'bottom')
}

// --- Level III: the beer cellar ------------------------------------------------------------------------------------
function levelThree(c) {
  const C = MASSES.cellar
  const dug = cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, cellarWall, P.cellarFloor)
  hatchLadder(c, C.ladder.x, C.ladder.z, -C.depth + 1, 0, C.ladder.facing, P.wood)
  // vault haunches along the long walls, casks stacked on the west wall and the east, a chest and a tun at the back
  for (let x = 2; x <= 10; x++) { stair(c, x, -1, 3, 'stone_brick_stairs', 'north', 'top'); stair(c, x, -1, 10, 'stone_brick_stairs', 'south', 'top') }
  for (let z = 4; z <= 9; z++) for (const y of [-3, -2]) c.set(2, y, z, 'barrel', { facing: 'east', open: 'false' })
  for (let z = 6; z <= 9; z++) for (const y of [-3, -2]) c.set(10, y, z, 'barrel', { facing: 'west', open: 'false' })
  c.set(6, -3, 9, 'chest', { facing: 'north', type: 'single', waterlogged: 'false' })
  c.set(5, -3, 9, 'barrel', { facing: 'up', open: 'false' }); c.set(7, -3, 9, 'barrel', { facing: 'up', open: 'false' })
  for (const [x, z] of [[4, 5], [8, 5], [4, 8], [8, 8], [6, 6]]) lantern(c, x, -1, z)
  return dug
}

export function draw(level) {
  const c = new LiftedCanvas(SIZES[level], BASEMENT[level] ?? 0)
  levelOne(c)
  if (level >= 2) levelTwo(c)
  const dug = level >= 3 ? levelThree(c) : null
  return { c, dug }
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  fs.mkdirSync(path.join(here, 'out'), { recursive: true })
  if (dug) fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(','))
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${IDS[level]}_cut${cut}` : IDS[level] } }
}
