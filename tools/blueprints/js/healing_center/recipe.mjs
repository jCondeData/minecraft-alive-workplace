// The Healing Center (the Spring Infirmary), the recipe: assembles plan.mjs's level (LEVEL=1..4) from the parts
// library (../parts) in passes (block-out and structure, roof, openings, depth and detail, interior, ground, cellar)
// and returns the structure for the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot
// the parts recorded) for tools/blueprints/rebuild.py, and out/cellar.txt for Level IV.
// Build every level: build.sh. CUT=<y> cuts everything above y (the drawing's y); ABOVE=1 drops the basement (renders).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZES, BASEMENT, PALETTE, MASSES, OPENINGS, FURNITURE, OUTSIDE } from './plan.mjs'
import {
  Canvas, LiftedCanvas, mix, stair, floor, post, beamRing, window, door, doorway, hood, flowerBed, lampPost, brackets,
  railingRun, flight, cellar, raisedBed, tree, wallBench, furnitureParts,
} from '../parts/index.mjs'
import { unionGableRoof, gableFill, framedGableWindow, springPool } from '../parts/healing_spring.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

const ring = (x0, z0, x1, z1) => {
  const out = []
  for (let x = x0; x <= x1; x++) for (let z = z0; z <= z1; z++) if (x === x0 || x === x1 || z === z0 || z === z1) out.push([x, z])
  return out
}

export function draw(level) {
  const c = BASEMENT[level] ? new LiftedCanvas(SIZES[level], BASEMENT[level]) : new Canvas(SIZES[level])
  const P = PALETTE
  const stone = mix(P.stone, 7)
  const S = MASSES.stem, H = MASSES.hall, W = MASSES.wing
  const two = level >= 2, three = level >= 3, four = level >= 4
  const owns = [] // wall rectangles drawn so far: a later mass never overwrites a shared wall

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // Dispensary (the stem): a stone plinth on the wall line, a smooth stone floor, plaster walls, dark posts, a beam
  {
    const { x0, x1, z0, z1 } = S.walls
    for (const [x, z] of ring(x0, z0, x1, z1)) c.set(x, 0, z, stone)
    floor(c, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, P.floor)
    for (const [x, z] of ring(x0, z0, x1, z1)) for (let y = 1; y <= S.top; y++) c.set(x, y, z, P.plaster)
    for (const x of S.frontPosts) post(c, x, z0, 1, S.top, P.frame)
    for (const x of S.backPosts) post(c, x, z1, 1, S.top, P.frame)
    for (const z of S.sidePosts) { post(c, x0, z, 1, S.top, P.frame); post(c, x1, z, 1, S.top, P.frame) }
    beamRing(c, x0, z0, x1, z1, S.beamY, P.frame)
    // the jetty: the front gable stands a block out over the door on a sill beam and four brackets
    brackets(c, S.frontPosts.map(x => [x, S.beamY, z0 - 1]), `${P.trim}_stairs`, 'south')
    for (let x = x0 + 1; x <= x1 - 1; x++) c.set(x, S.beamY + 1, z0 - 1, P.frame, { axis: 'x' })
    // a tie beam across the hall over the counters, the lamps hang from it
    for (let x = x0 + 1; x <= x1 - 1; x++) c.set(x, S.beamY, 7, P.frame, { axis: 'x' })
    owns.push(S.walls)
  }
  // Spring Hall (II): the taller cross range behind, sharing the Dispensary's back wall
  if (two) {
    const { x0, x1, z0, z1 } = H.walls
    const shared = (x, z) => z === z0 && x >= S.walls.x0 && x <= S.walls.x1
    for (const [x, z] of ring(x0, z0, x1, z1)) if (!shared(x, z)) c.set(x, 0, z, stone)
    floor(c, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, P.hallFloor)
    for (const [x, z] of ring(x0, z0, x1, z1)) {
      if (shared(x, z)) continue
      for (let y = 1; y <= H.top; y++) c.set(x, y, z, P.plaster)
    }
    for (const x of H.frontPosts) post(c, x, z0, 1, H.top, P.frame)
    for (const x of H.backPosts) post(c, x, z1, 1, H.top, P.frame)
    for (const z of H.sidePosts) { post(c, x0, z, 1, H.top, P.frame); post(c, x1, z, 1, H.top, P.frame) }
    // the beam ring, but not over the Dispensary's back wall (its gable stands there)
    for (const [x, z] of ring(x0, z0, x1, z1)) {
      if (shared(x, z) && x !== S.walls.x0 && x !== S.walls.x1) continue
      const corner = (x === x0 || x === x1) && (z === z0 || z === z1)
      c.set(x, H.beamY, z, P.frame, { axis: corner || shared(x, z) ? 'y' : (z === z0 || z === z1) ? 'x' : 'z' })
    }
    // two tie beams along the hall, the lamps over the pool and the tea table hang from them
    for (const x of [8, 17]) for (let z = z0 + 1; z <= z1 - 1; z++) c.set(x, H.beamY, z, P.frame, { axis: 'z' })
    owns.push(H.walls)
  }
  // Consulting Room (III): the low east wing beside the front, sharing the Dispensary's east wall and the hall's front
  if (three) {
    const { x0, x1, z0, z1 } = W.walls
    const shared = (x, z) => x === x0 || z === z1
    for (const [x, z] of ring(x0, z0, x1, z1)) if (!shared(x, z)) c.set(x, 0, z, stone)
    floor(c, x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, P.floor)
    for (const [x, z] of ring(x0, z0, x1, z1)) {
      if (shared(x, z)) continue
      for (let y = 1; y <= W.top; y++) c.set(x, y, z, P.plaster)
    }
    for (const x of W.frontPosts) if (x !== x0) post(c, x, z0, 1, W.top, P.frame)
    for (const z of W.sidePosts) if (z !== z1) post(c, x1, z, 1, W.top, P.frame)
    for (const [x, z] of ring(x0, z0, x1, z1)) {
      if (shared(x, z)) continue
      const corner = x === x1 && z === z0
      c.set(x, W.beamY, z, P.frame, { axis: corner ? 'y' : z === z0 ? 'x' : 'z' })
    }
    // the stem's east wall over the wing's beam is now inside: plaster stays; the wing's sill under its gable
    c.set(x0, W.beamY, z0, P.frame, { axis: 'y' })
  }

  // --- Pass 2: the roof, its gables and its lanterns --------------------------------------------------------------
  // the Dispensary's roof with a gabled wall dormer on each long side (the east one goes into the wing's roof in III),
  // the Spring Hall's with two gables on its long back wall, the Consulting Room's
  const masses = [{ ...S.roof, along: two ? S.alongII : S.alongI }, S.dormerW]
  if (!three) masses.push(S.dormerE)
  if (two) masses.push(H.roof, ...H.backGables)
  if (three) masses.push(W.roof)
  const h = unionGableRoof(c, masses, P.roof)
  const frameAt = (axis) => [P.frame, { axis }]
  // Dispensary front gable (the jetty, z = 1): plaster with two studs, the framed window in the middle
  gableFill(c, range(2, 10).map(x => [x, 1]), S.beamY + 2, h, (x, y) => ((x === 3 || x === 9 || (x === 6 && y >= 10)) ? frameAt('y') : P.plaster))
  // Dispensary back gable (z = 11): in Level I the back of the house, later the wall between it and the Spring Hall
  gableFill(c, range(two ? 1 : 2, two ? 11 : 10).map(x => [x, S.walls.z1]), S.beamY + 1, h, (x, y) => (
    (x === 1 || x === 11) && y === H.beamY ? frameAt('y') : (x === 4 || x === 8) && !two ? frameAt('y') : P.plaster))
  if (!two) c.set(6, 8, S.walls.z1, P.glass) // a little gable light at the back
  if (two) {
    // the Spring Hall's gables, west and east, plaster with two studs either side of the window
    for (const gx of [H.walls.x0, H.walls.x1])
      gableFill(c, range(H.walls.z0 + 1, H.walls.z1 - 1).map(z => [gx, z]), H.beamY + 1, h, (x, y, z) => ((z === 12 || z === 18) ? frameAt('y') : P.plaster))
    // where the hall's front wall rises past the stem's eave (x 12): fill to the roof
    gableFill(c, range(H.walls.x0 + 11, H.walls.x1).map(x => [x, H.walls.z0]), H.beamY + 1, h, () => P.plaster)
  }
  // the dormers' gables, flush with the side walls
  gableFill(c, range(3, 9).map(z => [S.walls.x0, z]), S.beamY + 1, h, () => P.plaster)
  if (!three) gableFill(c, range(3, 9).map(z => [S.walls.x1, z]), S.beamY + 1, h, () => P.plaster)
  // the two gables on the Spring Hall's long back wall (the west one over the back door in III)
  if (two) gableFill(c, [...range(3, 9), ...range(11, 17)].map(x => [x, H.walls.z1]), H.beamY + 1, h, () => P.plaster)
  if (three) {
    // the Consulting Room's front gable (z = 2), flush with the walls under it
    gableFill(c, range(W.walls.x0 + 1, W.walls.x1 - 1).map(x => [x, W.walls.z0]), W.beamY + 1, h, (x, y) => ((x === 13 || x === 17) ? frameAt('y') : P.plaster))
  }
  // one framed window in every gable
  for (const [axis, line, u0, u1, y, out, from, until = 9] of OPENINGS.gableWindows)
    if (level >= from && level <= until) framedGableWindow(c, axis, line, u0, u1, y, out, { glass: P.glass, frame: P.frame, sill: P.sill, h })
  // the vent on the Dispensary's ridge: a fence under a slab of the roof's own wood
  const [vx, vz] = S.vent
  c.set(vx, h(vx, vz) + 1, vz, `${P.trim}_fence`)
  c.set(vx, h(vx, vz) + 2, vz, P.roof.slab, { type: 'bottom', waterlogged: 'false' })

  // --- Pass 3: openings ------------------------------------------------------------------------------------------
  for (const w of OPENINGS.windows) {
    if (level < w.level) continue
    const dressed = w.until === undefined || level <= w.until
    for (const p of w.cells) c.set(...p, P.glass)
    // a stone ledge one out with a pot of herbs or flowers in each pane's place, on the street front and the garden side
    const shown = w.out === 'north' || (w.out === 'south' && w.cells[0][2] === H.walls.z1)
    if (!dressed || !shown) continue
    const pots = w.cells.filter(p => p[1] === Math.min(...w.cells.map(q => q[1]))).map((p, i) => P.pots[(i + w.cells[0][0] + w.cells[0][2]) % P.pots.length])
    window(c, w.cells, w.out, 'sill', { glass: P.glass, wood: P.trim, sill: P.sill, pots })
  }
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, fd.out, { wood: P.door, step: fd.step, stepStair: P.skirt })
  hood(c, fd.at[0], S.top, fd.at[2] - 1, P.roof)
  if (two) doorway(c, OPENINGS.hallDoorway.at, 2)
  if (three) {
    const bd = OPENINGS.backDoor
    door(c, bd.at, bd.facing, bd.out, { wood: P.door, step: bd.step, stepStair: P.skirt })
    hood(c, bd.at[0], S.top, bd.at[2] + 1, P.roof)
    for (const at of OPENINGS.wingDoorways) doorway(c, at, 2)
    // jambs where the doorways cut into the old windows beside them
    for (const [x, z] of [[11, 4], [17, 11]]) for (let y = 2; y <= 3; y++) c.set(x, y, z, P.frame, { axis: 'y' })
  }

  // --- Pass 4: depth: the skirt, a course of stone stairs under the street fronts and the garden side, carrying the ledges
  const rows = [[S.walls.z0 - 1, S.walls.x0 - 1, S.walls.x1 + 1, 'south']]
  if (two) rows.push([H.walls.z0 - 1, S.walls.x1 + 1, H.walls.x1 + 1, 'south'], [H.walls.z1 + 1, H.walls.x0 - 1, H.walls.x1 + 1, 'north'])
  if (three) rows.push([W.walls.z0 - 1, W.walls.x0, W.walls.x1 + 1, 'south'])
  const G = MASSES.garth
  for (const [z, xa, xb, facing] of rows) for (let x = xa; x <= xb; x++) {
    if (!c.inside(x, 0, z) || c.get(x, 0, z)) continue
    if (three && z >= G.z0 && x >= G.x0 && x <= G.x1) continue // the garth's ground takes the hall's back skirt
    if (three && z === H.walls.z0 - 1 && x <= W.walls.x1) continue // inside the Consulting Room
    stair(c, x, 0, z, P.skirt, facing)
  }

  // --- Pass 5: interior (the plan's furniture) ----------------------------------------------------------------------
  for (const f of FURNITURE) {
    if (level < f.level) continue
    if (f.part === 'wallBench') { wallBench(c, f.cellsXZ, f.y, f.facing, `${f.wood}_stairs`, f.wood); continue }
    if (f.part === 'brewingStand') { c.free(f.pos); c.set(...f.pos, 'brewing_stand', { has_bottle_0: 'false', has_bottle_1: 'false', has_bottle_2: 'false' }); continue }
    if (f.part === 'herbShelf') { furnitureParts.shelf(c, f); c.set(f.pos[0], f.pos[1] + 1, f.pos[2], f.plant); continue }
    furnitureParts[f.part](c, f)
  }
  // the Spring: a rimmed pool of warm water under the hall's ridge lamp, a pot of azalea on each corner of its rim
  if (two) {
    springPool(c, 10, 13, 16, 17, 1, P.rim)
    for (const [x, z] of [[10, 13], [16, 13], [10, 17], [16, 17]]) c.set(x, 2, z, 'potted_azalea_bush')
  }

  // --- Pass 6: ground ------------------------------------------------------------------------------------------------
  for (const b of OUTSIDE.flowerBeds) if (level >= b.level) flowerBed(c, b.x0, b.x1, b.z, b.plants)
  for (const [x, y, z] of OUTSIDE.lampPosts) lampPost(c, x, y, z, `${P.trim}_fence`, 2)
  if (three) {
    const G = MASSES.garth
    // the garth: a lawn, a path from the back door to the gate, a low wall on a stone course, raised herb beds
    for (let x = G.x0; x <= G.x1; x++) for (let z = G.z0; z <= G.z1; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, P.lawn, { snowy: 'false' })
    for (let z = G.z0 + 1; z <= G.z1; z++) c.set(G.gate[0], 0, z, P.path)
    for (const [x, z] of [[G.x0, G.z0], [G.x0, G.z1], [G.x1, G.z1], [G.x1, G.z0]]) c.set(x, 0, z, stone)
    for (let z = G.z0; z <= G.z1; z++) { c.set(G.x0, 0, z, stone); c.set(G.x1, 0, z, stone) }
    for (let x = G.x0; x <= G.x1; x++) c.set(x, 0, G.z1, stone)
    railingRun(c, [[G.x0, 1, G.z0], [G.x0, 1, G.z1], [G.x1, 1, G.z1], [G.x1, 1, G.z0]], null, 'mossy_stone_brick_wall')
    c.set(G.gate[0], 1, G.gate[1], `${P.trim}_fence_gate`, { facing: 'south', open: 'false', in_wall: 'true', powered: 'false' })
    for (const b of OUTSIDE.herbBeds) {
      const inside = raisedBed(c, { x0: b.x0, x1: b.x1, z0: b.z0, z1: b.z1, y: 0 }, { rim: stone, cap: 'stone_brick_slab', soil: P.lawn })
      inside.forEach(([x, z], i) => {
        if (b.tree && x === b.tree[0] && z === b.tree[1]) return
        c.set(x, 1, z, b.plants[i % b.plants.length])
      })
      if (b.tree) {
        const [tx, tz] = b.tree
        c.set(tx, 0, tz, 'rooted_dirt')
        const leaves = []
        for (let dx = -2; dx <= 2; dx++) for (let dz = -1; dz <= 1; dz++) for (let y = 4; y <= 5; y++)
          if (Math.abs(dx) + Math.abs(dz) + (y - 4) <= 2 && c.inside(tx + dx, y, tz + dz)) leaves.push([tx + dx, y, tz + dz])
        for (const dy of [6]) leaves.push([tx, dy, tz])
        tree(c, [[tx, 1, tz], [tx, 2, tz], [tx, 3, tz], [tx, 4, tz]], leaves, 'oak_log', (x, y, z) => ((x + z + y) % 3 ? 'flowering_azalea_leaves' : 'azalea_leaves'))
      }
    }
    lampPost(c, 12, 0, 24, `${P.trim}_fence`, 2)
  }

  // --- Pass 7: the Cistern (IV) --------------------------------------------------------------------------------------
  let dug = null
  if (four) {
    const C = MASSES.cistern, F = OPENINGS.cellarFlight
    dug = cellar(c, C.x0, C.z0, C.x1, C.z1, C.depth, mix(P.cellarWall, 9), P.cellarFloor)
    for (const p of F.well) c.clear(...p)
    flight(c, F.steps, F.rises, 'stone_brick', stone, -C.depth + 1)
    for (let z = 13; z <= 16; z++) c.clear(2, 1, z)
    railingRun(c, [[3, 1, 13], [3, 1, 17], [2, 1, 17]], P.trim)
    springPool(c, 10, 13, 16, 17, -3, P.rim)
    for (const [x, z] of [[10, 13], [16, 13]]) c.set(x, -2, z, 'potted_fern')
    lampPost(c, ...OUTSIDE.cellarLampPost, `${P.trim}_fence`, 1)
    // the vault's ribs: corbels of upside-down stairs along both long walls at every hall post
    for (const x of [4, 8, 12, 16]) {
      stair(c, x, -1, 12, 'stone_brick_stairs', 'north', 'top')
      stair(c, x, -1, 18, 'stone_brick_stairs', 'south', 'top')
    }
  }
  return { c, dug }
}

function range(a, b) { const out = []; for (let i = a; i <= b; i++) out.push(i); return out }

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const { c, dug } = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  fs.mkdirSync(path.join(here, 'out'), { recursive: true })
  if (dug) fs.writeFileSync(path.join(here, 'out', 'cellar.txt'), dug.join(','))
  const cut = process.env.CUT
  let canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const b = BASEMENT[level]
  if (process.env.ABOVE && b) { // renders only: the build above the ground
    const a = new Canvas(SIZES[level])
    for (const [k, v] of canvas.cells) { const [x, y, z] = k.split(',').map(Number); if (y >= b) a.cells.set(`${x},${y - b},${z}`, v) }
    canvas = a
  }
  const id = level === 1 ? 'healing_center' : `healing_center_${level}`
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
