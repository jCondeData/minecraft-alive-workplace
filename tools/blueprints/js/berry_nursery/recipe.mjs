// Berry Nursery (the Rill Garden), the recipe: draws plan.mjs from the shared parts library (../parts, ROADMAP 49) in
// passes (block-out and structure, openings, depth and detail, interior, ground and light) and returns the structure
// for the minecraft-builder skill's runner. LEVEL=1 (berry_nursery) or 2 (berry_nursery_2: Level I grown, the
// glasshouse added). Also writes furniture.json (the furniture spots the parts recorded) for tools/blueprints/rebuild.py.
// Build both levels: ./build.sh; CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { LEVELS, PALETTE, SEED_HOUSE, GARDEN, GLASSHOUSE, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, fairMix as mix, stair, slab, plinth, floor, beamRing, wallPanel, window, door, gableRoof, gableWall, bargeTrim, quoins, brackets,
  louvre, hatchLadder, flowerBed, benchRun, chimney, fieldPlot, composter, furnitureParts, doubleChest,
  gardenWall, loftHoist,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level) {
  const c = new Canvas(LEVELS[level].size)
  const P = PALETTE
  const stone = mix(P.stone, 11)
  const walk = mix(P.walk, 5)
  const infill = mix(P.infill, 3)
  const S = SEED_HOUSE, W = S.walls, R = S.room, G = GARDEN
  const frame = (axis = 'y') => [P.frame, { axis }]
  const put = (x, y, z, b) => { const [id, props] = Array.isArray(b) ? b : [b, {}]; c.set(x, y, z, id, props) }

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // Seed House: a stone plinth, brick ground storey with stone quoins, a dark oak beam ring, the loft floor
  plinth(c, W.x0, W.z0, W.x1, W.z1, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], 1, S.wallTop, P.brick)
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, S.wallTop, P.brick)
  wallPanel(c, [W.x0, W.z0], [W.x0, W.z1], 1, S.wallTop, P.brick)
  wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, S.wallTop, P.brick)
  quoins(c, [[W.x0, W.z0], [W.x1, W.z0], [W.x0, W.z1], [W.x1, W.z1]], 1, S.wallTop, P.quoin, P.brick)
  beamRing(c, W.x0, W.z0, W.x1, W.z1, S.beamY, P.frame)
  floor(c, R.x0, R.z0, R.x1, R.z1, S.beamY, P.loftFloor)

  // The roof: one broad slate gable facing the street (ridge along z), jettied a block out over the porch on brackets
  const H = S.hoist
  for (let x = W.x0; x <= W.x1; x++) put(x, S.beamY, H.face, frame('x')) // the jetty's beam
  const main = gableRoof(c, S.roof, P.roof)
  // the gable faces, timbered: corner posts, studs, the loft window on the axis under its lintel, a rail beside it
  const face = (u, y) => {
    if (u === W.x0 || u === W.x1 || u === 4 || u === 8) return frame()
    if (u === 6) return y === 8 ? frame('x') : infill // the window goes in with the openings
    if ((u === 5 || u === 7) && y === 7) return frame('x')
    return infill
  }
  gableWall(c, 'z', H.face, [W.x0, W.x1], S.beamY + 1, main.under, face)
  gableWall(c, 'z', W.z1, [W.x0, W.x1], S.beamY + 1, main.under, face)
  brackets(c, [[W.x0, S.beamY - 1, H.face], [5, S.beamY - 1, H.face], [7, S.beamY - 1, H.face], [W.x1, S.beamY - 1, H.face]], `${P.trim}_stairs`, 'south')
  // the long sides' knee walls between the beam and the eaves, a post at each end
  for (let z = W.z0; z < W.z1; z++) for (const x of [W.x0, W.x1]) put(x, S.beamY + 1, z, z === W.z0 ? frame() : infill)

  // --- Pass 2: openings -------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor, bd = OPENINGS.backDoor
  door(c, fd.at, fd.facing, fd.out, { wood: P.wood, step: fd.step, stepStair: 'stone_brick_stairs' })
  door(c, bd.at, bd.facing, bd.out, { wood: P.wood })
  for (const w of OPENINGS.windows) {
    const dresses = w.dress ?? [null]
    for (const d of dresses) window(c, w.cells, w.out, d, { glass: P.glass, wood: P.wood, pots: w.pots ?? [], sill: 'stone_brick_stairs' })
  }
  const L = OPENINGS.ladder
  hatchLadder(c, L.x, L.z, L.y0, L.top, L.facing, P.wood)

  // --- Pass 3: depth and detail -----------------------------------------------------------------------------------
  loftHoist(c, [6, 9, 0], 'north', 1, 1, P.frame) // the seed loft's hoist over the gable's window
  bargeTrim(c, 'z', S.roof.along[0], S.roof.across, main.under, `${P.trim}_stairs`) // 45 degrees: stairs
  bargeTrim(c, 'z', S.roof.along[1], S.roof.across, main.under, `${P.trim}_stairs`)
  // the drying vent on the ridge over the walk
  louvre(c, S.vent.cx, S.vent.cz, S.vent.y, { base: `${P.trim}_planks`, post: `${P.trim}_fence`, roof: P.roof, finial: 'lightning_rod' })

  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  for (const f of FURNITURE) {
    if (f.level && f.level > level) continue
    if (f.part === 'doubleChest') { doubleChest(c, f.pos, f.facing, f.along); continue }
    if (f.part === 'slabTop') { c.free(f.pos); slab(c, ...f.pos, `${f.wood}_slab`, 'top'); c.spot('counter', f.pos, f.facing, [[0, 0, 0]], f.wood); continue }
    if (f.part === 'bale') { c.free(f.pos); c.set(...f.pos, 'hay_block', { axis: 'y' }); continue }
    furnitureParts[f.part](c, f)
  }

  // --- Pass 5: the Rill Garden: ground, beds, the composter, the wall --------------------------------------------
  // the paths round the beds (packed mud) and the walk (stone) from the back door to the back wall
  floor(c, 1, G.paths.cross, 11, G.paths.back, 0, P.path)
  for (let z = G.paths.cross; z <= G.paths.back; z++) c.set(G.paths.walk, 0, z, walk)
  const B = G.beds
  const rill = new Set(G.rill.cells.map(([x, z]) => `${x},${z}`))
  const stones = new Set(G.stones.map(([x, z]) => `${x},${z}`))
  fieldPlot(c, { x0: B.x0, x1: B.x1, z0: B.z0, z1: B.z1 }, {
    water: (x, z) => rill.has(`${x},${z}`),
    footing: (x, z) => x === G.composter[0] && z === G.composter[2] ? 'chiseled_stone_bricks'
      : stones.has(`${x},${z}`) ? 'polished_andesite' : x === G.paths.walk ? walk(x, 0, z) : null,
  })
  composter(c, { pos: G.composter })
  const tops = gardenWall(c, G.wall, 0, { course: P.brick, parapet: P.brickWall, cap: { id: P.cap } },
    { piers: G.piers, pierHigh: 1, courses: 1 })
  for (const [x, z] of G.stubs) { c.set(x, 0, z, P.brick); c.set(x, 1, z, P.brickWall) }
  for (const [x, y, z] of tops) if (z === G.rill.z || (level === 1 && x === 6)) c.set(x, y, z, 'lantern', { hanging: 'false', waterlogged: 'false' })
  for (const b of OUTSIDE.benches) benchRun(c, { x0: b.x0, x1: b.x1, y: 1, z: b.z, facing: 'north' }, { seat: `${P.wood}_stairs`, spot: true, wood: P.wood })

  // the front: flower beds either side of the porch, a stone apron round the house, the porch's paving
  for (const b of OUTSIDE.flowerBeds) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  for (let x = 1; x <= 11; x++) if (!c.get(x, 0, 1)) c.set(x, 0, 1, x >= H.x0 && x <= H.x1 ? walk(x, 0, 1) : stone(x, 0, 1))
  for (let x = H.x0; x <= H.x1; x++) c.set(x, 0, 0, walk(x, 0, 0))
  for (let z = W.z0; z <= W.z1; z++) for (const x of [W.x0 - 1, W.x1 + 1]) if (!c.get(x, 0, z)) c.set(x, 0, z, stone(x, 0, z))
  // light at each door: under the jetty, under a beam end at the back
  c.set(6, S.beamY - 1, H.face, 'lantern', { hanging: 'true', waterlogged: 'false' })
  c.set(6, S.beamY, W.z1 + 1, P.frame, { axis: 'z' }) // a beam end out over the back door, its lantern under it
  c.set(6, S.beamY - 1, W.z1 + 1, 'lantern', { hanging: 'true', waterlogged: 'false' })

  if (level >= 2) glasshouse(c, P, walk, stone, infill)
  return c
}

// --- Level II: the Glass Wing at the end of the walk, and the pergola that links it to the seed house ---------------
function glasshouse(c, P, walk, stone, infill) {
  const GH = GLASSHOUSE, Wl = GH.walls, frame = { axis: 'y' }
  const sill = (x, y, z, toward) => stair(c, x, y, z, P.sill, toward, 'top')
  // the old back wall: its middle (x 2..10) becomes the wing's front kerb; the corner piers and the ends stay
  for (let x = Wl.x0; x <= Wl.x1; x++) { for (let y = 1; y <= 4; y++) c.clear(x, y, Wl.z0); c.set(x, 1, Wl.z0, P.brick) }
  // the kerb all round: brick to y 1, a stone sill course at y 2
  for (let z = Wl.z0; z <= Wl.z1; z++) for (let x = Wl.x0; x <= Wl.x1; x++) {
    if (!(x === Wl.x0 || x === Wl.x1 || z === Wl.z0 || z === Wl.z1)) continue
    c.set(x, 0, z, P.brick); c.set(x, 1, z, P.brick); c.set(x, 2, z, P.quoin)
  }
  // the posts, proud of the glass: each on a brick pier and a stone sill block, dark oak up to the eaves; sill noses between
  for (let z = Wl.z0; z <= Wl.z1; z++) for (const [x, toward] of [[Wl.x0 - 1, 'east'], [Wl.x1 + 1, 'west']]) {
    if (!GH.posts.includes(z)) { if (z > Wl.z0) sill(x, 2, z, toward); continue }
    c.set(x, 0, z, P.brick); c.set(x, 1, z, P.brick); c.set(x, 2, z, P.quoin)
    for (let y = 3; y <= 4; y++) c.set(x, y, z, P.frame, frame)
  }
  // the roof: the seed house's 45-degree section again, ridge in line with its ridge (y 10 at x 6)
  const stackAt = (x, y, z) => x === GH.stack.x && z === GH.stack.z
  const roof = gableRoof(c, GH.roof, P.roof, (x, y, z) => !stackAt(x, y, z))
  const [l0, l1] = GH.roof.along
  // one unbroken run of glass down each slope between the end rafters, under a three-wide slate ridge cap
  for (let z = Wl.z0 + 1; z < Wl.z1; z++) for (let x = Wl.x0; x <= Wl.x1; x++) if (x !== 6) c.set(x, roof.under[x], z, P.glass)
  for (const z of [Wl.z0, Wl.z1]) for (let x = Wl.x0; x <= Wl.x1; x++) if (x !== 6) stair(c, x, roof.under[x], z, `${P.trim}_stairs`, x < 6 ? 'east' : 'west')
  for (let z = l0; z <= l1; z++) for (const [x, f] of [[5, 'east'], [7, 'west']]) stair(c, x, roof.ridgeY, z, P.roof.stairs, f)
  bargeTrim(c, 'z', l0, GH.roof.across, roof.under, `${P.trim}_stairs`)
  bargeTrim(c, 'z', l1, GH.roof.across, roof.under, `${P.trim}_stairs`)
  // the side walls: glass from the sill to the eaves
  for (let z = Wl.z0; z <= Wl.z1; z++) for (const x of [Wl.x0, Wl.x1]) for (let y = 3; y < roof.under[x]; y++)
    c.set(x, y, z, z === Wl.z1 ? P.frame : P.glass, z === Wl.z1 ? frame : {})
  // the front end glazed between dark oak studs (x 4, x 8), the door on the axis; the back end boarded like the seed
  // house's gables (white daub between dark oak studs) round the brick flue
  gableWall(c, 'z', Wl.z0, [Wl.x0 + 1, Wl.x1 - 1], 3, roof.under, (u, y) => (u === 4 || u === 8) ? [P.mullion, frame] : P.glass)
  gableWall(c, 'z', Wl.z1, [Wl.x0 + 1, Wl.x1 - 1], 3, roof.under, (u, y) => u === 6 ? P.brick : (u === 4 || u === 8) ? [P.frame, frame] : infill)
  c.clear(6, 2, Wl.z0)
  door(c, GH.door, 'south', 'north', { wood: P.wood })
  // the floor: the walk to the stove, beds either side, the dipping tank, the potting benches along the back
  floor(c, Wl.x0 + 1, Wl.z0 + 1, Wl.x1 - 1, Wl.z1 - 1, 0, P.path)
  for (let z = Wl.z0; z < Wl.z1 - 1; z++) c.set(6, 0, z, walk(6, 0, z))
  const tank = new Set(GH.tank.cells.map(([x, z]) => `${x},${z}`))
  const B = GH.beds
  fieldPlot(c, { x0: B.x0, x1: B.x1, z0: B.z0, z1: GH.tank.z }, {
    water: (x, z) => tank.has(`${x},${z}`),
    footing: (x, z) => (x === 6 ? walk(x, 0, z) : null),
  })
  for (const [x, z] of GH.benches) {
    furnitureParts.counter(c, { pos: [x, 1, z], facing: 'north', topSlab: true, top: `${P.wood}_slab`, wood: P.wood })
    if (GH.benchPots[x]) c.set(x, 3, z, GH.benchPots[x])
  }
  furnitureParts.chest(c, { pos: GH.chest, facing: 'north' })
  // the stove at the end of the walk under a brick hood that runs back into the flue
  c.set(...GH.stove, 'furnace', { facing: 'north', lit: 'false' })
  for (let y = 2; y <= 3; y++) c.set(GH.stove[0], y, GH.stove[2], P.brick)
  stair(c, GH.stove[0], 4, GH.stove[2], 'brick_stairs', 'north')
  // its stack outside: on a brick base stepped in to it, up past the ridge to a stone band and a slab cap
  const S = GH.stack
  for (let y = 0; y <= S.top; y++) c.set(S.x, y, S.z, P.brick)
  for (const [dx, top] of [[1, 2]]) for (const d of [-1, 1]) {
    for (let y = 0; y <= top; y++) c.set(S.x + d * dx, y, S.z, P.brick)
    stair(c, S.x + d * dx, top + 1, S.z, 'brick_stairs', d < 0 ? 'east' : 'west')
  }
  c.set(S.x, S.top + 1, S.z, P.quoin)
  slab(c, S.x, S.top + 2, S.z, 'stone_brick_slab', 'bottom')
  // lamps hanging from the glass over the beds
  for (const x of [4, 8]) c.set(x, roof.under[x] - 1, 18, 'lantern', { hanging: 'true', waterlogged: 'false' })
  // the pergola over the walk: beams from the seed house's back wall to the wing's front, joists across with leaves
  const PG = GH.pergola
  for (const x of PG.x) for (let z = PG.z0; z <= PG.z1; z++) c.set(x, PG.y, z, P.frame, { axis: 'z' })
  for (const z of PG.joists) {
    for (let x = PG.x[0]; x <= PG.x[1]; x++) c.set(x, PG.y + 1, z, P.frame, { axis: 'x' })
    for (const x of [PG.x[0], PG.x[1]]) c.set(x, PG.y + 2, z, 'flowering_azalea_leaves', { persistent: 'true', distance: '7', waterlogged: 'false' })
  }
  for (const z of PG.joists) c.set(6, PG.y, z, 'lantern', { hanging: 'true', waterlogged: 'false' }) // under each joist
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const id = LEVELS[level].id
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
