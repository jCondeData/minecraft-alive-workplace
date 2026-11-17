// The Sawmill, the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP 49) in passes
// (block-out and structure, the machine and its water, openings, depth and detail, interior, ground) and returns the
// structure for the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts
// recorded) for tools/blueprints/rebuild.py. CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, MACHINE, OPENINGS, FURNITURE, OUTSIDE, RACK } from './plan.mjs'
import {
  Canvas, mix, stair, slab, plinth, floor, post, beamRing, wallPanel, window, door, gableRoof, gableWall,
  furnitureParts, kitchenParts, hoist, timberRack, lampPost, beltRun, cogTrain, upSaw, wheelDrop, flume, fallChute, cistern,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = mix(P.stone, 49)
  const S = MASSES.shed, L = MASSES.lodge, T = MASSES.tank, M = MACHINE

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // The race: a solid stone wall west of the shed carrying the flume, the wheel pit before it
  plinth(c, 0, 3, 1, 8, stone)
  c.fill(0, 1, 6, 1, 4, 8, stone)
  // The shed: a stone plinth, boards inside, a stone wheel wall on the west, board walls between spruce posts
  plinth(c, S.x0, S.z0, S.x1, S.z1, stone)
  floor(c, S.x0 + 1, S.z0 + 1, S.x1 - 1, S.z1 - 1, 0, P.floor)
  wallPanel(c, [S.x0, S.z0], [S.x0, S.z1], 1, S.beamY, stone) // the wheel wall, stone to the eaves against the spray
  wallPanel(c, [S.x1, S.z0], [S.x1, S.z1], 1, S.wallTop, P.boards)
  wallPanel(c, [S.x0, S.z1], [S.x1, S.z1], 1, S.wallTop, P.boards)
  for (const z of [S.z0, 7, S.z1]) post(c, S.x1, z, 1, S.wallTop, P.frame)
  for (const x of [5]) post(c, x, S.z1, 1, S.wallTop, P.frame)
  beamRing(c, S.x0, S.z0, S.x1, S.z1, S.beamY, P.beam)
  for (let z = S.z0; z <= S.z1; z++) c.set(S.x0, S.beamY, z, stone) // the stone wall's top course
  for (const z of [4, 7]) for (let x = S.x0 + 1; x < S.x1; x++) c.set(x, S.beamY, z, P.beam, { axis: 'x' }) // tie beams
  // The lodge: boards between spruce corner posts on a stone plinth, oak floor
  plinth(c, L.x0 + 1, L.z0, L.x1, L.z1, stone)
  floor(c, L.x0 + 1, L.z0 + 1, L.x1 - 1, L.z1 - 1, 0, P.lodgeFloor)
  wallPanel(c, [L.x0 + 1, L.z0], [L.x1, L.z0], 1, L.wallTop, P.boards)
  wallPanel(c, [L.x0 + 1, L.z1], [L.x1, L.z1], 1, L.wallTop, P.boards)
  wallPanel(c, [L.x1, L.z0], [L.x1, L.z1], 1, L.wallTop, P.boards)
  for (const [x, z] of [[L.x1, L.z0], [L.x1, L.z1], [L.x1, 4]]) post(c, x, z, 1, L.wallTop, P.frame)

  // Roofs: the shed's gable along z first, the lodge's cross gable only where nothing is drawn yet
  const main = gableRoof(c, S.roof, P.roof)
  const wing = gableRoof(c, L.roof, P.roof, (x, y, z) => !c.get(x, y, z) && x > S.x1)
  // Gables: the shed's front and back in vertical boards with a frame stud, the lodge's east end and its west end
  // where it meets the shed's roof
  const board = (u, y) => (u === 5 && y >= 7 ? [P.frame, { axis: 'y' }] : P.boards)
  gableWall(c, 'z', S.z0, S.roof.across, S.beamY + 1, main.under, board)
  gableWall(c, 'z', S.z1, S.roof.across, S.beamY + 1, main.under, board)
  gableWall(c, 'x', L.x1, L.roof.across, L.wallTop + 1, wing.under, () => P.boards)
  for (let z = L.roof.across[0] + 1; z < L.roof.across[1]; z++)
    for (let y = S.roof.eaveY + 1; y < wing.under[z]; y++) if (!c.get(S.x1, y, z)) c.set(S.x1, y, z, P.boards)

  // --- Pass 2: the machine and its water -------------------------------------------------------------------------
  c.set(...M.wheel.at, 'create:water_wheel', { facing: M.wheel.facing })
  wheelDrop(c, M.drop.x, M.drop.z, M.drop.top, M.drop.bottom, M.drop.axis)
  cogTrain(c, M.cogs.cells, M.cogs.axis)
  for (const s of M.saws) upSaw(c, s.at, 'x', s.flipped)
  beltRun(c, M.belt.cells, M.belt.facing)
  c.set(...M.depot, 'create:depot')
  // The tank on its legs at the back corner, braced; the flume's head in a notch in its face
  cistern(c, T.x0, T.z0, T.w, T.d, T.y0, T.y1, { ...P.tank, hoops: T.hoops })
  for (const [x, y, z] of [[1, 3, 9], [1, 4, 11], [0, 4, 10]]) c.set(x, y, z, `${P.wood}_fence`) // braces between the legs, under the tank
  flume(c, M.flume, { walls: P.race, bed: P.bed })
  c.clear(...M.flume.notch)
  fallChute(c, M.chute.at, M.chute.top, M.chute.bottom, { walls: M.chute.walls, sump: M.chute.sump }, P.race)
  c.set(0, 1, 4, P.race); c.set(0, 1, 3, P.race); c.set(1, 1, 3, P.race) // the wheel pit's low curb
  // a hood over the wheel off the race wall's face
  for (const x of [0, 1]) { c.set(x, 5, 4, P.roof.full); stair(c, x, 5, 3, P.roof.stairs, 'south') }

  // --- Pass 3: openings ------------------------------------------------------------------------------------------
  const m = OPENINGS.mouth
  wallPanel(c, [3, 1], [7, 1], 1, S.wallTop, P.boards)
  for (const [x, y, z, hinge] of m.doors) door(c, [x, y, z], m.facing, 'north', { wood: P.wood, hinge })
  for (const [x, , z] of m.doors) c.set(x, 0, z, P.floor)
  post(c, 7, 1, 1, S.wallTop, P.frame)
  const it = OPENINGS.intake
  door(c, it.at, it.facing, 'south', { wood: P.wood })
  for (let x = 3; x <= 5; x++) c.set(x, 3, it.at[2], P.beam, { axis: 'x' }) // a lintel over the log door
  const ld = OPENINGS.lodgeDoor
  door(c, ld.at, ld.facing, 'north', { wood: P.wood, step: [ld.at[0], 0, 0], stepStair: 'cobblestone_stairs' })
  const id = OPENINGS.innerDoor
  for (let y = id.at[1]; y < id.at[1] + id.height; y++) c.clear(id.at[0], y, id.at[2])
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood })

  // --- Pass 4: depth and detail ----------------------------------------------------------------------------------
  hoist(c, [5, 7, 1], 'z', -1, 2, 2, P.beam) // the hoist beam out of the front gable over the mouth
  // (no barge boards: under these 45-degree rakes they doubled the steps into a saw-tooth edge)
  // a bench along the shed's east wall for the sawn boards: slabs on the floor by the depot
  for (const z of [2, 3]) slab(c, 7, 1, z, `${P.wood}_slab`, 'bottom')
  timberRack(c, RACK, P.frame, 'z') // the offcut rack against the east wall

  // --- Pass 5: interior (the plan's furniture) --------------------------------------------------------------------
  const parts = {
    ...furnitureParts,
    jobBlock(c, f) { c.free(f.pos); c.set(...f.pos, f.block) },
    chest(c, f) { kitchenParts.pantry(c, f) },
  }
  for (const f of FURNITURE) parts[f.part](c, f)

  // --- Pass 6: ground ---------------------------------------------------------------------------------------------
  // (no drawn path: y = 0 stands on the ground, so a path there would be a step up; the yard keeps its grass)
  for (const s of OUTSIDE.logStacks) {
    const cells = []
    for (let x = s.x0; x <= s.x1; x++) for (let y = 0; y < s.high; y++) cells.push([x, y, s.z])
    timberRack(c, cells, s.log, 'x')
  }
  for (const [x, z] of OUTSIDE.boards) slab(c, x, 0, z, `${P.wood}_slab`, 'bottom')
  for (const [x, z] of OUTSIDE.lamps) lampPost(c, x, 0, z, `${P.wood}_fence`, 2)
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `sawmill_cut${cut}` : 'sawmill' } }
}
