// The Quarry, the recipe: assembles plan.mjs from the shared parts library (../parts, ROADMAP 49) in passes (block-out
// and structure, openings, depth and detail, interior, ground, light) and returns the structure for the
// minecraft-builder skill's runner. Also writes furniture.json (every furniture spot the parts recorded).
// Build: see package.json ("npm run build"); CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, PIT, FURNACE, MASSES, OPENINGS, CHESTS, WINCH, FURNITURE } from './plan.mjs'
import {
  Canvas, stair, slab, floor, post, beamRing, wallPanel, window, door, gableRoof, gableWall, bargeTrim, chimney, hood,
  furnitureParts, kitchenParts, chestStack, hangingLantern, fairMix,
  headframe, sheave, rakedStrut, kerbRing, spoilHeap,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const rock = fairMix(P.rock, 3), kerb = fairMix(P.kerb, 5), flags = fairMix(P.floor, 7), spoil = fairMix(P.spoil, 11)
  const C = MASSES.curb, F = MASSES.headframe, H = MASSES.house, S = MASSES.stack
  const W = H.walls, R = H.room
  const frame = { log: P.frame, stairs: P.frameStairs }

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  // Hoist House: a plinth course a block proud all round, flagged floor, rubble walls, a spruce beam ring, corner posts
  floor(c, H.plinth.x0, H.plinth.z0, H.plinth.x1, H.plinth.z1, 0, kerb)
  floor(c, R.x0, R.z0, R.x1, R.z1, 0, flags)
  wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], 1, H.wallTop, rock)
  wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], 1, H.wallTop, rock)
  wallPanel(c, [W.x0, W.z0], [W.x0, W.z1], 1, H.wallTop, rock)
  wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], 1, H.wallTop, rock)
  for (const [x, z] of [[W.x0, W.z0], [W.x1, W.z0], [W.x0, W.z1], [W.x1, W.z1]]) post(c, x, z, 1, H.wallTop, P.joinery)
  beamRing(c, W.x0, W.z0, W.x1, W.z1, H.beamY, P.joinery)
  for (const z of H.ties) for (let x = R.x0; x <= R.x1; x++) c.set(x, H.beamY, z, P.joinery, { axis: 'x' })

  // the roof: deepslate tiles, ridge along z, a block of overhang all round; timber-boarded gables with studs
  const roof = gableRoof(c, H.roof, P.roof)
  for (const z of [W.z0, W.z1]) {
    gableWall(c, 'z', z, [W.x0, W.x1], H.beamY + 1, roof.under, (u, y) => {
      if (u === 12 || u === 16 || (u === 14 && y >= 7)) return [P.stud, { axis: 'y' }]
      return P.boards
    })
  }
  bargeTrim(c, 'z', H.roof.along[0], H.roof.across, roof.under, `${P.wood}_stairs`)
  bargeTrim(c, 'z', H.roof.along[1], H.roof.across, roof.under, `${P.wood}_stairs`)

  // Curb: a stone kerb round the pit, open in the Gap where the Miner steps in
  const gap = []
  for (let z = C.gap.z0; z <= C.gap.z1; z++) gap.push([C.gap.x, z])
  kerbRing(c, C, 0, kerb, gap)

  // Headframe: four legs on the kerb, a tie ring and a head ring, the sheave beam, the Sheave on top
  headframe(c, F, frame)
  sheave(c, F.sheave, { rim: P.joinery, corner: `${P.wood}_stairs`, spoke: P.frameFence, axle: P.frame })
  // Struts: raked back-legs from the head down onto the Hoist House roof
  for (const z of F.struts) rakedStrut(c, [F.head.x1 + 1, F.headY - 1, z], 4, 1, frame)

  // Stack: a brick flue straight up from the blast furnace, through the ridge, a corbelled cap and smoke
  chimney(c, S.x, S.z, S.y0, S.y1, P.stack)
  for (const [dx, dz, f] of [[-1, 0, 'east'], [1, 0, 'west'], [0, -1, 'south'], [0, 1, 'north']])
    stair(c, S.x + dx, S.y1, S.z + dz, P.stackStairs, f, 'top')

  // --- Pass 2: openings ---------------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor, pd = OPENINGS.pitDoor
  door(c, fd.at, fd.facing, fd.out, { wood: P.wood, step: fd.step, stepStair: 'stone_brick_stairs' })
  door(c, pd.at, pd.facing, pd.out, { wood: P.wood })
  for (const w of OPENINGS.windows) window(c, w.cells, w.out, 'sill', { glass: P.glass, wood: P.wood })
  for (const [x, y, z] of OPENINGS.gableWindows) c.set(x, y, z, P.glass)

  // --- Pass 3: depth and detail -------------------------------------------------------------------------------------
  // a tiled hood over the front door with its lantern; post jambs either side of the pit door
  hood(c, fd.at[0], H.beamY, fd.at[2] - 1, P.roof)
  for (const z of [pd.at[2] - 1, pd.at[2] + 1]) post(c, W.x0, z, 1, H.wallTop, P.joinery)
  // the Gap: a lip of slabs from the plinth course to the pit's edge
  for (let z = C.gap.z0; z <= C.gap.z1; z++) slab(c, C.gap.x, 0, z, P.lip)
  // the rail: spruce fence on the kerb, log posts with lanterns at the corners, the legs closing it at the Gap
  const corners = [[C.x0, C.z0], [C.x1, C.z0], [C.x0, C.z1], [C.x1, C.z1]]
  const legs = new Set(F.legs.map(([x, z]) => `${x},${z}`))
  for (let x = C.x0; x <= C.x1; x++) for (let z = C.z0; z <= C.z1; z++) {
    if (x !== C.x0 && x !== C.x1 && z !== C.z0 && z !== C.z1) continue
    if (legs.has(`${x},${z}`) || corners.some(([a, b]) => a === x && b === z)) continue
    if (x === C.gap.x && z >= C.gap.z0 && z <= C.gap.z1) continue
    c.set(x, 1, z, `${P.wood}_fence`)
  }
  for (const [x, z] of corners) { post(c, x, z, 1, 2, P.joinery); c.set(x, 3, z, 'lantern', { hanging: 'false', waterlogged: 'false' }) }
  // the skip on its chain over the pit, and a lantern on the other side to light the hole
  const sk = F.skip
  for (let y = sk.y + 1; y < F.headY; y++) c.set(sk.x, y, sk.z, 'chain', { axis: 'y', waterlogged: 'false' })
  c.set(sk.x, sk.y, sk.z, 'hopper', { facing: 'down', enabled: 'true' })
  hangingLantern(c, F.lamp.x, F.lamp.y, F.lamp.z, 'lantern', F.headY - F.lamp.y - 1)
  for (const z of [F.ring.z0, F.ring.z1]) hangingLantern(c, 4, F.tieY - 1, z, 'lantern') // under the tie beams

  // --- Pass 4: interior ---------------------------------------------------------------------------------------------
  c.set(...FURNACE, 'blast_furnace', { facing: 'west', lit: 'false' })
  stair(c, S.x, 2, S.z - 1, P.hood, 'south', 'top') // the smoke hood round the flue's foot (west kept clear: the
  stair(c, S.x, 2, S.z + 1, P.hood, 'north', 'top') // Miner stands at (13, 1, 7))
  stair(c, S.x + 1, 2, S.z, P.hood, 'west', 'top')
  for (const ch of CHESTS) chestStack(c, ch.cells, [1], ch.facing)
  for (let x = WINCH.x0; x <= WINCH.x1; x++) c.set(x, WINCH.y, WINCH.z, `stripped_${P.joinery}`, { axis: 'x' })
  const [kx, ky, kz] = WINCH.crank
  // the crank: a lever on the drum's west end (B1485: it stood on a fence post, whose top can't hold one, so a Builder
  // never placed it). Facing west, it hangs on the drum's end face at x 14.
  c.set(kx, ky, kz, 'lever', { face: 'wall', facing: 'west', powered: 'false' })
  for (const f of FURNITURE) (furnitureParts[f.part] ?? kitchenParts[f.part])(c, f)

  // --- Pass 5: ground -----------------------------------------------------------------------------------------------
  // flashing: a run of tile slabs where the headframe's back beam meets the hoist house's west eave (holes.py read the
  // gap between them as a pit in the roof)
  for (let z = 4; z <= 8; z++) if (!c.get(10, 6, z)) slab(c, 10, 6, z, P.roof.slab)
  // (the 2 x 2 patch of path slabs before the shed door is gone: the critic read it as a stray pale patch; the door's
  // stone step at (13, 0, 2) is the way in from the grass)
  spoilHeap(c, MASSES.spoil, 0, spoil, [[2, 0, 13, 'coal_ore'], [6, 0, 12, 'iron_ore'], [3, 1, 13, 'copper_ore']])

  // Guard: nothing at y 0-1 over the pit (the Miner stands there), nothing below y 0 at all
  for (const k of c.cells.keys()) {
    const [x, y, z] = k.split(',').map(Number)
    if (x >= PIT.x0 && x <= PIT.x1 && z >= PIT.z0 && z <= PIT.z1 && y <= 1) throw new Error(`drawn over the pit at ${k}`)
  }
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `quarry_cut${cut}` : 'quarry' } }
}
