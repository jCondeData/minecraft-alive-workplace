// The Watermill, the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP 49) in passes
// (block-out and structure, the water and the machine, openings, depth and detail, interior, ground) and returns the
// structure for the minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts
// recorded) for tools/blueprints/rebuild.py. CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, MACHINE, OPENINGS, FURNITURE, SACKS, OUTSIDE } from './plan.mjs'
import {
  Canvas, mix, stair, plinth, floor, post, posts, beamRing, wallPanel, window, door, doorway, gableRoof, gableWall,
  quoins, flight, stairwell, railingRun, flowerBed, lampPost, furnitureParts, kitchenParts,
  raisedPond, overshotWheel, driveToMillstone, hurst, lucam, sluiceGate,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = mix(P.stone, 31)
  const wet = mix(P.wet, 7)
  const M = MASSES.mill, L = MASSES.lucam, G = MASSES.granary, PD = MASSES.pond, PT = MASSES.pit, X = MACHINE
  const plaster = mix(P.infill, 5)

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // The granary wing first (the mill's east wall is drawn over its west side): stone to the floor, timber above
  // (no fill under the granary's and the mill's floors at y 1: the ship step left the hidden layer out, 50.5)
  for (let y = 0; y <= 1; y++) {
    wallPanel(c, [G.x0 + 1, G.z0], [G.x1, G.z0], y, y, stone)
    wallPanel(c, [G.x0 + 1, G.z1], [G.x1, G.z1], y, y, stone)
    wallPanel(c, [G.x1, G.z0], [G.x1, G.z1], y, y, stone)
  }
  floor(c, G.x0 + 1, G.z0 + 1, G.x1 - 1, G.z1 - 1, 1, P.granaryFloor)
  wallPanel(c, [G.x0 + 1, G.z0], [G.x1 - 1, G.z0], 2, 4, plaster)
  wallPanel(c, [G.x0 + 1, G.z1], [G.x1 - 1, G.z1], 2, 4, plaster)
  wallPanel(c, [G.x1, G.z0], [G.x1, G.z1], 2, 4, plaster)
  for (const z of [G.z0, G.z1]) post(c, G.x1, z, 2, 4, P.frame)
  beamRing(c, G.x0, G.z0, G.x1, G.z1, G.beamY, P.frame)

  // The mill house: a stone undercroft and ground storey (the floor at y 1 over a solid fill), the beam ring at y 5
  for (const [a, b] of [[[M.x0, M.z0], [M.x1, M.z0]], [[M.x0, M.z1], [M.x1, M.z1]], [[M.x0, M.z0], [M.x0, M.z1]], [[M.x1, M.z0], [M.x1, M.z1]]])
    wallPanel(c, a, b, 0, M.wallTop, stone)
  floor(c, M.x0 + 1, M.z0 + 1, M.x1 - 1, M.z1 - 1, M.floorY, P.floor)
  beamRing(c, M.x0, M.z0, M.x1, M.z1, M.beamY, P.frame)
  floor(c, M.x0 + 1, M.z0 + 1, M.x1 - 1, M.z1 - 1, M.beamY, P.floor) // the grain loft's floor
  // the grain loft: a timber-framed storey on the stone one, dark oak posts over white plaster, a top plate at y 9
  const U = M.upper
  for (const [axis, line, at] of [['x', M.z0, U.frontPosts], ['x', M.z1, U.backPosts], ['z', M.x0, U.sidePosts], ['z', M.x1, U.sidePosts]]) {
    const [a0, a1] = axis === 'x' ? [M.x0, M.x1] : [M.z0, M.z1]
    wallPanel(c, axis === 'x' ? [a0, line] : [line, a0], axis === 'x' ? [a1, line] : [line, a1], U.y0, U.y1, plaster)
    posts(c, axis, line, at, U.y0, U.y1, P.frame)
  }
  beamRing(c, M.x0, M.z0, M.x1, M.z1, U.plate, P.frame)

  // The pond terrace against the mill's back wall (its front ring is the mill wall and the launder's mouth)
  raisedPond(c, { x0: PD.x0, x1: PD.x1, z0: PD.z0, z1: PD.z1, waterY: PD.waterY, open: [[2, 12], [3, 12], [4, 12], [5, 12], [6, 12]] },
    { wall: wet, curb: P.dressed, fill: P.fill })
  // The pit and the launder's piers: solid stone under the water line, the pit head closed
  c.fill(PT.x0, 0, PT.z0, PT.x1, 1, PT.z1, wet)
  for (const z of [7, 10, 11, 12]) c.set(1, 2, z, wet)
  c.set(1, 3, 7, wet)

  // Roofs: the granary's 45-degree gable first (its ridge runs into the loft's east wall, under the main eave), then
  // the mill's 45-degree roof wherever the granary's isn't
  const wing = gableRoof(c, G.roof, P.roof, (x, y, z) => !c.get(x, y, z) && x >= 12)
  const main = gableRoof(c, M.roof, P.roof, (x, y, z) => !c.get(x, y, z))

  // Gables: the mill's in dark oak framing over white plaster; the granary's in plaster with a post at its apex
  // the gable apexes over the top plate in dark boards (a weatherboarded mill gable), the lucam's roof against the front one
  const millGable = () => P.gableBoards
  gableWall(c, 'z', M.z0, [M.x0, M.x1], U.plate + 1, main.under, millGable)
  gableWall(c, 'z', M.z1, [M.x0, M.x1], U.plate + 1, main.under, millGable)
  gableWall(c, 'x', G.x1, G.roof.across.map((v, i) => v + (i ? -1 : 1)), G.beamY + 1, wing.under, (u, y) => (u === 7 && y === 8 ? [P.frame, { axis: 'y' }] : plaster))
  // the lucam out of the front gable (before the barge boards, which stop at its roof)
  const lucamRidge = lucam(c, L, { post: P.frame, infill: plaster, face: P.gableBoards, floor: `${P.trim}_planks`, roof: P.roof, bracket: `${P.trim}_stairs`, wood: P.wood })
  // its ridge carried up into the main roof's overhang over it (no slot between the two)
  for (let y = lucamRidge + 1; y < main.under[L.x]; y++) for (let z = M.roof.along[0]; z < L.z0 + L.depth; z++) if (!c.get(L.x, y, z)) c.set(L.x, y, z, P.roof.full)

  // --- Pass 2: the water and the machine -------------------------------------------------------------------------
  const w = X.wheel
  overshotWheel(c, { ...w, wheel: w.at }, {
    pit: wet,
    side: (x, y, z) => [P.launderSide, { axis: 'z' }],
    bed: () => P.launderBed,
  })
  driveToMillstone(c, X.shafts, X.gearbox, w.axle)
  hurst(c, X.gearbox, 'west', P.hurst)
  sluiceGate(c, X.sluice, P.wood, 'north')

  // --- Pass 3: openings ------------------------------------------------------------------------------------------
  const md = OPENINGS.millDoor
  door(c, md.at, md.facing, md.out, { wood: P.wood, step: md.steps[0], stepStair: P.dressedStairs })
  stair(c, ...md.steps[1], P.dressedStairs, 'south')
  c.set(md.steps[0][0], 0, md.steps[0][2], P.dressed) // under the upper step
  const gd = OPENINGS.granaryDoor
  door(c, gd.at, gd.facing, gd.out, { wood: P.wood, step: gd.steps[0], stepStair: P.dressedStairs })
  stair(c, ...gd.steps[1], P.dressedStairs, 'south')
  c.set(gd.steps[0][0], 0, gd.steps[0][2], P.dressed)
  doorway(c, OPENINGS.innerDoor.at, OPENINGS.innerDoor.height)
  const pots = {} // 50.5's ship step: no sill pots (see Pass 5)
  for (const wd of OPENINGS.windows)
    window(c, wd.cells, wd.out, wd.dress, { glass: P.glass, wood: P.wood, sill: P.dressedStairs, pots: pots[wd.name] ?? [] })
  const st = OPENINGS.stairs
  stairwell(c, st.well)
  flight(c, st.flight, st.rises, P.wood, P.floor)
  railingRun(c, st.rail, P.wood)

  // --- Pass 4: depth and detail -----------------------------------------------------------------------------------
  // dressed corners on the stone storey, a lintel with a keystone over the door
  quoins(c, [[M.x0, M.z0], [M.x1, M.z0], [M.x0, M.z1], [M.x1, M.z1]], 0, M.wallTop, P.dressed, P.dressedAlt)
  quoins(c, [[G.x1, G.z0], [G.x1, G.z1]], 0, 1, P.dressed, P.dressedAlt)
  for (const x of [6, 8]) c.set(x, 4, M.z0, P.dressed)
  c.set(7, 4, M.z0, P.key)
  // the perron: stone cheeks either side of the steps, a lamp on each
  for (const x of [6, 8]) for (const z of [0, 1]) for (let y = 0; y <= 1; y++) c.set(x, y, z, P.dressed)
  for (const [x, y, z] of OUTSIDE.cheekLamps) lampPost(c, x, y, z, `${P.wood}_fence`, 1)
  // (no barge boards: under these 45-degree rakes they double the steps into a saw-tooth edge)
  // a lantern under the granary's eave over its door
  c.set(gd.at[0], 4, gd.at[2] - 1, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // --- Pass 5: interior (the plan's furniture) -----------------------------------------------------------------------
  const parts = { ...furnitureParts, ...kitchenParts }
  for (const f of FURNITURE) parts[f.part](c, f)
  // (the grain and flour sacks are left out: the ship step trimmed them, with the yard's hay and flower bed, the sill
  // pots and the pond terrace's steps, so the mill is built within its builder's working day; 50.5)

  // --- Pass 6: ground ---------------------------------------------------------------------------------------------
  c.set(...OUTSIDE.composter, 'composter', { level: '0' })
  lampPost(c, ...OUTSIDE.wheelLamp, `${P.wood}_fence`, 2)
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `watermill_cut${cut}` : 'watermill' } }
}
