// Sifting Shed (Levels I and II, "the Tipple"), the recipe: assembles plan.mjs from the shared parts library (../parts)
// in passes (block-out and structure, roof, openings, depth and detail, the stations, ground and light) and returns the
// structure for the minecraft-builder skill's runner. LEVEL=1 or 2 picks the level; Level II is Level I drawn again
// with the incline gallery and the second station added, so every cell of I it keeps stays the same block.
// Also writes furniture.json (no spots: the brief asks for none). Build: ./build.sh (CUT=<y> for a cutaway).
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { LEVEL, SIZE, PALETTE, MASSES, STATIONS, OPENINGS, HEAPS, OUTSIDE } from './plan.mjs'
import {
  Canvas, fairMix, post, beamRing, wallPanel, gableRoof, gableWall, bargeTrim, catslideRoof, window, stair, slab,
  trapdoor, door, hood, hangingLantern, lampPost, trestle, chuteFunnel, heap, inclineGallery, sieveRack,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw(level = LEVEL) {
  const c = new Canvas(SIZE)
  const P = PALETTE
  const stone = fairMix(P.stone, 7)
  const ground = fairMix(P.ground, 3)
  const L = MASSES.legs, F = MASSES.loft, B = MASSES.bays

  // --- Pass 1: block-out and structure ----------------------------------------------------------------------------
  // the undercroft's trodden floor and the bays' floor (y 0), the way in from the street
  for (let x = F.x0; x <= B.x0 + 2; x++) for (let z = F.z0; z <= F.z1; z++) c.set(x, 0, z, ground(x, 0, z))
  // the screen room: the legs (footings and logs) with rubble walls between them, up to the loft's beam
  const legs = L.xs.flatMap(x => L.zs.map(z => [x, z]))
  wallPanel(c, [F.x0, F.z0], [F.x1, F.z0], 1, L.topY, stone)
  wallPanel(c, [F.x0, F.z1], [F.x1, F.z1], 1, L.topY, stone)
  wallPanel(c, [F.x0, F.z0], [F.x0, F.z1], 1, L.topY, stone)
  wallPanel(c, [F.x1, F.z0], [F.x1, F.z1], 1, L.topY, stone)
  trestle(c, legs, L.footY, L.topY, { footing: stone, log: P.frame, brace: P.brace })
  // the loft: beam ring (its floor's edge) at y 5, floor boards inside, frame posts, board walls, the top beam ring
  beamRing(c, F.x0, F.z0, F.x1, F.z1, F.floorY, P.frame)
  c.fill(F.x0 + 1, F.floorY, F.z0 + 1, F.x1 - 1, F.floorY, F.z1 - 1, P.floor)
  wallPanel(c, [F.x0, F.z0], [F.x1, F.z0], F.wallY0, F.wallY1, P.boards)
  wallPanel(c, [F.x0, F.z1], [F.x1, F.z1], F.wallY0, F.wallY1, P.boards)
  wallPanel(c, [F.x0, F.z0], [F.x0, F.z1], F.wallY0, F.wallY1, P.boards)
  wallPanel(c, [F.x1, F.z0], [F.x1, F.z1], F.wallY0, F.wallY1, P.boards)
  for (const z of F.posts.side) for (const x of [F.x0, F.x1]) post(c, x, z, F.wallY0, F.wallY1, P.frame)
  for (const x of F.studs) for (const z of [F.z0, F.z1]) post(c, x, z, F.wallY0, F.wallY1, P.frame)
  beamRing(c, F.x0, F.z0, F.x1, F.z1, F.beamY, P.frame)

  // --- Pass 2: roofs ----------------------------------------------------------------------------------------------
  const main = gableRoof(c, MASSES.roof, P.roof)
  for (const zl of [F.z0, F.z1]) {
    gableWall(c, 'z', zl, [F.x0, F.x1], F.beamY + 1, main.under, (u, y) => {
      if (u === 4 && y >= 12) return [P.frame, { axis: 'y' }] // the king post over the gable window
      if ((u === 3 || u === 5) && y <= 11) return [P.frame, { axis: 'y' }] // studs either side of it
      return P.boards
    })
  }
  bargeTrim(c, 'z', MASSES.roof.along[0], MASSES.roof.across, main.under, `${P.wood}_slab`)
  bargeTrim(c, 'z', MASSES.roof.along[1], MASSES.roof.across, main.under, `${P.wood}_slab`)
  // the bays' lean-to: a slate pent from the loft's beam down to its eave at x 10, between the legs' front and back lines
  const lean = catslideRoof(c, { axis: 'z', across: [F.x1, B.x1], ridge: F.x1, along: [B.z0, B.z1], ridgeY: B.ridgeY }, P.roof,
    (x) => x > F.x1)
  for (const z of B.posts.zs) post(c, B.posts.x, z, 1, lean.under[B.posts.x] - 1, P.frame)
  for (const z of B.posts.zs) c.set(B.posts.x, 0, z, stone(B.posts.x, 0, z))
  for (const z of [B.z0, B.z1]) for (let x = F.x1 + 1; x < B.posts.x; x++) c.set(x, 1, z, P.boards) // the bays' end boards

  // --- Pass 3: openings -------------------------------------------------------------------------------------------
  for (const w of OPENINGS.windows) {
    if (w.level && w.level !== level) continue
    window(c, w.cells, w.out, w.cells.length > 1 ? 'shutters' : null, { glass: P.glass, wood: P.wood })
  }
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood, step: fd.step })
  hood(c, fd.at[0], L.topY, fd.at[2] - 1, P.roof)
  const bd = OPENINGS.backDoor
  if (level >= bd.level) door(c, bd.at, bd.facing, 'south', { wood: P.wood })
  // the tipping door: a pair of board hatches (closed trapdoors, hung upright) in the front wall
  for (const [x, y, z] of OPENINGS.loftHatch.cells) trapdoor(c, x, y, z, `${P.wood}_trapdoor`, 'south', 'bottom', true)

  // --- Pass 4: depth and detail ------------------------------------------------------------------------------------
  // the hoist: the beam's end under the ridge at the front, a chain and lantern hanging from it over the street
  const h = OUTSIDE.hoist
  c.set(h.beam[0], h.beam[1], h.beam[2], P.frame, { axis: 'z' })
  hangingLantern(c, h.lantern[0], h.lantern[1], h.lantern[2], 'lantern', h.beam[1] - h.lantern[1] - 1)
  // the chute: a hatch in the loft floor over the sieve and the funnel under it
  const s1 = STATIONS[0]
  chuteFunnel(c, s1.cauldron[0], F.floorY, s1.cauldron[2], { hatch: `${P.wood}_trapdoor`, stairs: `${P.wood}_stairs`, corner: P.floor })
  for (const [x, y, z] of OUTSIDE.pile) c.set(x, y, z, P.heaps.gravel) // and by the sieve below
  // gravel waiting in the loft beside the hatch (on the floor boards)
  for (const [x, z] of [[3, 6], [5, 6], [3, 2]]) c.set(x, F.floorY + 1, z, P.heaps.gravel)
  // the sieves drying on the west rail
  const r = OUTSIDE.rack
  sieveRack(c, { x: r.x, zs: r.zs, railY: r.railY }, r.sieves, { rail: `${P.wood}_fence`, sieve: `${P.wood}_trapdoor` })
  for (const z of r.zs) c.set(r.x, 0, z, stone(r.x, 0, z))

  // --- Pass 5: the stations and the heaps ---------------------------------------------------------------------------
  for (const s of STATIONS) {
    if (s.level > level) continue
    c.set(...s.cauldron, 'cauldron')
    c.set(...s.chest, 'chest', { facing: s.chestFacing, type: 'single', waterlogged: 'false' })
  }
  for (const hp of HEAPS) {
    if (hp.level && hp.level > level) continue
    const y0 = hp.y ?? 1
    for (const [x, z] of hp.base) if (y0 > 0 && !c.get(x, y0 - 1, z)) c.set(x, y0 - 1, z, stone(x, y0 - 1, z))
    heap(c, y0, hp.base, hp.crown, P.heaps[hp.block])
  }

  // --- Pass 6: Level II, the incline gallery and the second sieve at its foot -----------------------------------------
  if (level >= 2) {
    const G = MASSES.gallery
    const gd = OPENINGS.galleryDoor.cells[0]
    door(c, gd, 'south', 'south', { wood: P.wood }) // the loft's back window becomes the gallery's door
    // the gallery's trestle legs under its middle, footings in the ground (the second chest stands against one)
    trestle(c, [[G.x0 + 1, G.legsZ], [G.x1 - 1, G.legsZ]], 0, inclineFloor(G, G.legsZ) - 1, { footing: stone, log: P.frame, brace: P.brace })
    inclineGallery(c, G, {
      deck: `${P.wood}_stairs`, deckFull: P.floor, fill: stone, rail: `${P.wood}_fence`, post: P.frame, roof: P.roof, roofTop: false, eaves: false,
    })
    // the landing under the loft's eaves: a joist under its deck, board cheeks either side of the door
    for (let x = G.x0 + 1; x <= G.x1 - 1; x++) c.set(x, G.deckTop - 1, G.zTop, P.frame, { axis: 'x' })
    for (const x of [G.x0 + 1, G.x1 - 1]) for (let y = G.deckTop + 1; y <= G.deckTop + 2; y++) c.set(x, y, G.zTop, P.boards)
    // the foot: trodden ground round the second sieve and under the gallery's open middle
    for (let x = G.x0 + 1; x <= G.x1; x++) for (let z = G.zTop; z <= G.legsZ; z++) if (!c.get(x, 0, z)) c.set(x, 0, z, ground(x, 0, z))
    // a lantern under the deck over the second sieve, and one at the incline's foot
    hangingLantern(c, 3, inclineFloor(G, 9) - 1, 9, 'lantern', 0)
    lampPost(c, G.x1 + 1, 1, G.zFoot, `${P.wood}_fence`)
    c.set(G.x1 + 1, 0, G.zFoot, stone(G.x1 + 1, 0, G.zFoot))
  }

  // --- Pass 7: ground and light ---------------------------------------------------------------------------------------
  const lp = OUTSIDE.lamp
  c.set(lp.at[0], 0, lp.at[2], stone(lp.at[0], 0, lp.at[2]))
  lampPost(c, lp.at[0], lp.at[1], lp.at[2], lp.post)
  // a lantern hung under the loft floor inside the door
  hangingLantern(c, 4, F.floorY - 1, 2, 'lantern', 0)
  return c
}

function inclineFloor(G, z) { return G.deckTop - (z - G.zTop) }

export default function build(api) {
  const c = draw()
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + c.spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  const id = LEVEL === 1 ? 'sifting_shed' : `sifting_shed_${LEVEL}`
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
