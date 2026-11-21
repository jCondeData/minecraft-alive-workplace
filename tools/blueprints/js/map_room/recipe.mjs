// Map Room, the recipe: assembles plan.mjs (the Lantern Tower, round 3) from the shared parts library (../parts) in
// passes (block-out and structure, openings, depth and detail, interior, ground) and returns the structure for the
// minecraft-builder skill's runner. Also writes furniture.json. CUT=<y> builds a cutaway with everything above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE, MASSES, OPENINGS, FURNITURE } from './plan.mjs'
import {
  Canvas, mix, floor, wallPanel, window, door, boxPyramidRoof, shelfWall, furnitureParts, stair,
  corbelRing, compassRose, lookoutRail, ladderShaft, batter, flowerBed,
} from '../parts/index.mjs'
import { cornerPiers, stairPlinth, towerBrackets, mapWall } from '../parts/tower_depth.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))

export function draw() {
  const c = new Canvas(SIZE)
  const P = PALETTE, M = MASSES
  const stone = mix(P.stone, 11)
  const S = M.shaft, R = M.room, F = M.footing, D = M.deck, N = M.lantern, Q = M.piers, H = M.porch

  // --- Pass 1: block-out and structure -------------------------------------------------------------------------
  c.fill(F.x0, 0, F.z0, F.x1, 0, F.z1, P.fill) // the footing under shaft and piers
  for (const [a, b] of [[[S.x0, S.z0], [S.x1, S.z0]], [[S.x0, S.z1], [S.x1, S.z1]], [[S.x0, S.z0], [S.x0, S.z1]], [[S.x1, S.z0], [S.x1, S.z1]]])
    wallPanel(c, a, b, S.y0, S.y1, stone)
  for (const y of M.floors) floor(c, R.x0, R.z0, R.x1, R.z1, y, P.floor)
  const isPier = cornerPiers(c, Q.x0, Q.z0, Q.x1, Q.z1, Q.y0, Q.y1, P.pier, P.pierAlt)
  // the crown: a corbel table on the pier line between the piers, the deck two proud of the wall all round
  corbelRing(c, Q.x0, Q.z0, Q.x1, Q.z1, M.corbelY, P.corbel, isPier)
  floor(c, D.x0, D.z0, D.x1, D.z1, M.deckY, P.deck)
  // the gallery rail with log posts at the corners, mid-sides and either side of the balcony
  const L0 = M.lookout.y0, L1 = M.lookout.y1
  lookoutRail(c, D.x0, D.z0, D.x1, D.z1, L0, L1, { post: P.post, fence: `${P.wood}_fence` })
  const galleryPosts = [[D.x0, 8], [D.x1, 8], [5, D.z1], [3, D.z0], [7, D.z0]]
  for (const [x, z] of galleryPosts) for (let y = L0; y <= L1; y++) c.set(x, y, z, P.post, { axis: 'y' })
  // the lantern: log posts at its corners and mullions, glass between
  for (let y = L0; y <= L1; y++) {
    for (let x = N.x0; x <= N.x1; x++) for (const z of [N.z0, N.z1]) c.set(x, y, z, P.glass)
    for (let z = N.z0; z <= N.z1; z++) for (const x of [N.x0, N.x1]) c.set(x, y, z, P.glass)
    for (const [x, z] of [[N.x0, N.z0], [N.x1, N.z0], [N.x0, N.z1], [N.x1, N.z1], [3, N.z0], [7, N.z0], [5, N.z1], [N.x0, 8], [N.x1, 8]])
      c.set(x, y, z, P.post, { axis: 'y' })
  }
  // the wall plate over the lantern and across the balcony's ceiling, under the roof's first ring in
  for (let x = N.x0; x <= N.x1; x++) for (const z of [N.z0 - 1, N.z0, N.z1]) c.set(x, M.roof.y, z, P.beam, { axis: 'x' })
  for (let z = N.z0 + 1; z < N.z1; z++) for (const x of [N.x0, N.x1]) c.set(x, M.roof.y, z, P.beam, { axis: 'z' })
  const apex = boxPyramidRoof(c, M.roof, P.roof)

  // --- Pass 2: openings -----------------------------------------------------------------------------------------
  const fd = OPENINGS.frontDoor
  door(c, fd.at, fd.facing, 'north', { wood: P.wood })
  const gd = OPENINGS.galleryDoor
  door(c, gd.at, gd.facing, 'north', { wood: P.wood })
  for (const w of OPENINGS.windows) {
    window(c, w.cells, w.out, w.dress, { glass: P.glass, wood: P.wood, sill: P.sill })
    if (w.dress === 'shutters') window(c, w.cells, w.out, 'sill', { glass: P.glass, wood: P.wood, sill: P.sill })
  }
  const LD = OPENINGS.ladder
  ladderShaft(c, LD.x, LD.z, LD.y0, LD.y1, LD.facing)

  // --- Pass 3: depth and detail ---------------------------------------------------------------------------------
  // belt courses one proud of the wall at each floor line, between the piers, and a dark band in the wall behind
  for (const y of M.belts) {
    corbelRing(c, Q.x0, Q.z0, Q.x1, Q.z1, y, P.belt, isPier)
    for (let x = S.x0 + 1; x < S.x1; x++) for (const z of [S.z0, S.z1]) if (!c.name(x, y, z).includes('pane')) c.set(x, y, z, P.band)
    for (let z = S.z0 + 1; z < S.z1; z++) for (const x of [S.x0, S.x1]) if (!c.name(x, y, z).includes('pane')) c.set(x, y, z, P.band)
  }
  // the battered foot between the piers on the sides and back (the porch covers the front)
  batter(c, Q.x0, Q.z0, Q.x1, Q.z1, 1, P.skirt, (x, z) => isPier(x, z) || z === Q.z0)
  // timber brackets: a second tier one further out under the deck's edge (on alternate cells and under every
  // gallery post), and knee braces under the eave either side of each mid gallery post: arches round the lookout
  const under = []
  for (let x = D.x0 + 1; x < D.x1; x++) if (x % 2 === 1) { under.push([x, M.corbelY, D.z0]); under.push([x, M.corbelY, D.z1]) }
  for (let z = D.z0 + 1; z < D.z1; z++) if (z % 2 === 0) { under.push([D.x0, M.corbelY, z]); under.push([D.x1, M.corbelY, z]) }
  for (const [x, , z] of under) {
    const toward = z === D.z0 ? 'south' : z === D.z1 ? 'north' : x === D.x0 ? 'east' : 'west'
    towerBrackets(c, [[x, M.corbelY, z]], toward, `${P.wood}_stairs`)
  }
  const knee = (x, z, along) => towerBrackets(c, [[x, L1, z]], along, `${P.wood}_stairs`)
  for (const [x, z] of galleryPosts) {
    if (z === D.z0 || z === D.z1) { knee(x - 1, z, 'east'); knee(x + 1, z, 'west') } else { knee(x, z - 1, 'south'); knee(x, z + 1, 'north') }
  }
  // the cartographer's colours flown on the front piers' faces, under the jetty
  for (const x of [Q.x0, Q.x1]) c.set(x, 10, Q.z0 - 1, `${P.map[0]}_wall_banner`, { facing: 'north' })
  c.set(apex[0], apex[1] + 1, apex[2], P.vane, { facing: 'up', powered: 'false', waterlogged: 'false' })

  // the entrance: a three-wide, two-step stair plinth with cheeks, up to a porch between the front piers
  const PL = M.plinth
  stairPlinth(c, PL.x0, 0, PL.z, PL.width, PL.steps, 'south', P.skirt, P.fill, P.cheek)
  c.fill(H.x0, 0, H.z0, H.x1, 0, H.z1, P.fill)
  c.fill(H.x0, H.y, H.z0, H.x1, H.y, H.z1, P.porchDeck)
  for (const x of [H.x0, H.x1]) for (let y = H.y + 1; y <= H.topY; y++) c.set(x, y, H.z0, P.post, { axis: 'y' })
  for (const x of [H.x0, H.x1]) for (let z = H.z0 + 1; z <= H.z1; z++) c.set(x, H.y + 1, z, `${P.wood}_fence`)
  for (let x = H.x0; x <= H.x1; x++) for (let z = H.z0; z <= H.z1; z++) stair(c, x, H.topY + 1, z, P.roof.stairs, 'south')

  // --- Pass 4: interior (the plan's furniture) --------------------------------------------------------------------
  compassRose(c, 5, M.floors[0], 8, P.rose)
  const parts = {
    ...furnitureParts,
    jobBlock: (cv, f) => { cv.free(f.pos); cv.set(...f.pos, f.block) },
    shelves: (cv, f) => shelfWall(cv, f.cells, f.y0, f.y1, 'bookshelf'),
    mapWall: (cv, f) => mapWall(cv, f.pos, f.along, f.facing, P.map, f.wood),
  }
  for (const f of FURNITURE) parts[f.part](c, f)

  // --- Pass 5: ground -------------------------------------------------------------------------------------------
  for (const z of [1, 2]) { flowerBed(c, 0, 2, z, ['cornflower', 'blue_orchid', 'oxeye_daisy']); flowerBed(c, 8, 10, z, ['oxeye_daisy', 'cornflower', 'blue_orchid']) }
  return c
}

export default function build(api) {
  const c = draw()
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `map_room_cut${cut}` : 'map_room' } }
}
