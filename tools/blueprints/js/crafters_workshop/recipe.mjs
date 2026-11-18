// Crafter's Workshop (crafters_workshop, ROADMAP 50.7), the recipe: assembles plan.mjs (the Saw-Tooth Shop) from the
// shared parts library (../parts) in passes (block-out and structure, openings, depth and detail, interior, ground,
// light) and returns the structure for the minecraft-builder skill's runner. Also writes furniture.json (every
// furniture spot the parts recorded) for tools/blueprints/rebuild.py.
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, TEETH, MASSES, OPENINGS, FURNITURE, OUTSIDE, MIDDLE } from './plan.mjs'
import {
  Canvas, mix, stair, plinth, floor, beamRing, wallPanel, window, door, railing, furnitureParts, doubleChest, lampOn,
} from '../parts/index.mjs'
import { sawHeights, sawtoothRoof, clerestory, twinFlue, proudPosts, wallShelf } from '../parts/crafters_sawtooth.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

const parts = {
  ...furnitureParts,
  lampOn,
  wallShelf,
  anvil(c, f) { c.free(f.pos); c.set(...f.pos, f.block, { facing: f.facing }) },
  doubleChest(c, f) { doubleChest(c, f.pos, f.facing, f.along) },
}

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = mix(P.stone, 11)
  const H = MASSES.hall, W = H.walls, R = H.room
  const { h, faces } = sawHeights(TEETH.from, TEETH.teeth, TEETH.rise, TEETH.eaveY)
  const S = MASSES.stack
  const inStack = (x, z) => x === S.x && S.z.includes(z)

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // the footing: a stone apron under the hall and a block round it (the posts and sills stand on it), the floor
  plinth(c, 0, 0, 12, 10, stone)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, P.floor)
  // brick walls to y 4, the beam ring at y 5
  wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], 1, H.wallTop, P.walls)
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, H.wallTop, P.walls)
  wallPanel(c, [W.x0, W.z0], [W.x0, W.z1], 1, H.wallTop, P.walls)
  wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, H.wallTop, P.walls)
  beamRing(c, W.x0, W.z0, W.x1, W.z1, H.beamY, P.frame)
  // stone piers a block proud of the brick at the corners and under the back gutters
  for (const [x, z] of H.piers) for (let y = 1; y <= H.pierTop; y++) c.set(x, y, z, mix(P.stack, 5))

  // the glazed faces of the teeth: each on its beam, studs at the gables
  // (the gable studs in brick, so each tooth's end reads as one brick face over the beam, not a checkerboard)
  for (const g of MASSES.clerestories) clerestory(c, g, { glass: P.glass, frame: P.walls, beam: P.frame })

  // the gables front and back under the teeth: brick, a dark oak stud under each face (drawn by clerestory)
  for (const z of [W.z0, W.z1]) for (let x = W.x0; x <= W.x1; x++)
    for (let y = H.beamY + 1; y < h[x]; y++) if (!c.get(x, y, z)) c.set(x, y, z, P.walls)

  // the stack: the forge's twin flue outside the west wall, its back through the wall in stone
  twinFlue(c, S.x, S.z, 0, S.top, mix(P.stack, 5), { band: P.dressed, bandY: S.bandY })
  for (const z of S.z) for (let y = 1; y <= H.beamY; y++) c.set(W.x0, y, z, mix(P.stack, 5))

  // the teeth: three 45-degree slopes climbing east, copper in the valleys, overhanging front and back
  // the first tooth starts on the west wall (x 1), not a step past it; the copper runs in the valleys only, its ends
  // over the gables in the tiles (the critic's notes: a stray step past the wall, a checkerboard at the teeth's ends)
  sawtoothRoof(c, { axis: 'x', h, along: TEETH.along, rise: 'east' }, P.roof,
    { gutter: P.gutter, eaveY: TEETH.eaveY, keep: (x, y, z) => !inStack(x, z) && x >= W.x0 })
  for (const x of faces.slice(0, -1).map(u => u + 1)) for (const z of TEETH.along) stair(c, x, h[x], z, P.roof.stairs, 'east')

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step, stepStair: 'stone_brick_stairs' })
  OPENINGS.windows.forEach((w, i) => window(c, w.cells, w.out, 'sill', {
    glass: P.glass, wood: P.wood, sill: `${P.trim}_stairs`, pots: [OUTSIDE.pots[i % 4], OUTSIDE.pots[(i + 1) % 4]],
  }))

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  // the pentice porch on the stone apron: spruce posts, a deepslate lean-to under the teeth's front overhang,
  // railings in the side bays and the door bay open
  const Po = MASSES.porch
  for (let x = Po.x0; x <= Po.x1; x++) {
    stair(c, x, 3, Po.z0, P.pentice, 'south')
    stair(c, x, 4, Po.z1, P.pentice, 'south')
  }
  proudPosts(c, Po.posts.map(x => [x, Po.z0]), 1, 2, P.frame)
  for (const [a, b] of Po.rails) railing(c, Array.from({ length: b - a + 1 }, (_, i) => [a + i, 1, Po.z0]), P.wood)
  c.set(...OUTSIDE.doorLamp, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // copper rods on the three tops at the front (no trim under the rakes: the zig-zag reads cleaner bare)
  for (const x of faces) c.set(x, h[x] + 1, TEETH.rakes[0], P.finial, { facing: 'up', powered: 'false', waterlogged: 'false' })

  // the guild's banner on the middle tooth's front, over the porch
  const bn = OUTSIDE.banner
  c.set(...bn.at, `${bn.colour}_wall_banner`, { facing: bn.facing })

  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  // the forge breast: stone over the two furnaces up to the beam, a hood of upside-down stairs in front of it
  for (const z of S.z) for (let y = 2; y <= H.wallTop; y++) c.set(R.x0, y, z, mix(P.stack, 5))
  for (const z of S.z) stair(c, R.x0 + 1, 3, z, 'stone_brick_stairs', 'west', 'top')
  for (const f of FURNITURE) parts[f.part](c, f)

  // --- Pass 5: ground -----------------------------------------------------------------------------------------------
  // the timber stock in the middle of the room (the critic's note: the middle was bare): boards laid on the floor
  for (const [x, y, z] of MIDDLE.stock) c.set(x, y, z, MIDDLE.log, { axis: 'z' })
  const wp = OUTSIDE.woodpile
  for (let x = wp.x0; x <= wp.x1; x++) c.set(x, 1, wp.z, 'spruce_log', { axis: 'x' })
  c.set(wp.x0 + 1, 2, wp.z, 'spruce_log', { axis: 'x' })
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `crafters_workshop_cut${cut}` : 'crafters_workshop' } }
}
