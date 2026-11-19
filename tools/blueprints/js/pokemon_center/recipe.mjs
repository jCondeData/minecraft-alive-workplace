// The Pokémon Center ("The Red Dome"), the recipe: assembles plan.mjs from the shared parts library (../parts) in passes
// (block-out and structure, openings, depth and detail, interior, ground) and returns the structure for the
// minecraft-builder skill's runner. LEVEL=1|2 picks the level (default 1): II draws I first and grows out of it (the
// Trade Hall wing, the garden, the pavilion), so II keeps I's cells but for the few it opens (the arch, the back door).
// Writes furniture.json (the level's furniture spots). Build: sh build.sh; CUT=<y> builds a cutaway above y removed.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { SIZE, PALETTE as P, DRUM, FIXED, OPENINGS, FURNITURE } from './plan.mjs'
import {
  Canvas, mix, stair, slab, roundCells, railCells, towardCentre, wallPanel, window, door, gableRoof, gableWall,
  bargeTrim, glazedLantern, hood, furnitureParts, railingRun, roundDais, discCells, flowerBed, lampPost,
  healingMachine, pcTower, pastureBlock, shopCounter, deskRun, ringDome,
} from '../parts/index.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const key = (x, z) => `${x},${z}`

/** the clinic's furniture of one level; the desk part draws a run of counters */
export function furnish(c, list, P) {
  for (const f of list) {
    if (f.part === 'desk') deskRun(c, f.cells, f.facing, { base: `${f.wood}_planks`, top: 'smooth_stone_slab', wood: f.wood })
    else furnitureParts[f.part](c, f)
  }
}

// --- Level I: the Red Dome -------------------------------------------------------------------------------------------
function levelOne(c) {
  const { cx, cz, r } = DRUM
  const stone = mix(P.stone, 3), floorMix = mix(P.floor, 5)
  const drum = railCells(cx, cz, r) // the wall ring
  const ringSet = new Set(drum.map(([x, z]) => key(x, z)))
  const area = roundCells(cx, cz, r).filter(([x, z]) => !ringSet.has(key(x, z))).concat(drum)
  const areaSet = new Set(area.map(([x, z]) => key(x, z)))
  const inside = (x, z) => areaSet.has(key(x, z)) && !ringSet.has(key(x, z))
  const outer = railCells(cx, cz, r + 1).concat(roundCells(cx, cz, r + 1)).filter(([x, z]) => !areaSet.has(key(x, z)))
  const outerCells = [...new Map(outer.map(p => [key(...p), p])).values()]

  // Pass 1: block-out and structure. The skirt (stairs rising to the drum), the floor, the drum, the band, the cornice
  for (const [x, z] of outerCells) stair(c, x, 0, z, P.skirt, towardCentre(x, z, cx, cz))
  for (const [x, z] of area) c.set(x, 0, z, ringSet.has(key(x, z)) ? stone : floorMix)
  for (const [x, z] of drum) {
    for (let y = 1; y <= DRUM.wallTop; y++) c.set(x, y, z, P.wall)
    c.set(x, DRUM.bandY, z, P.band)
    c.set(x, DRUM.corniceY, z, P.trim)
  }
  // the cornice's lip: upside-down white stairs on the drum's outside, backs to it (a moulding the dome's eave sits over)
  for (const [x, z] of outerCells) stair(c, x, DRUM.corniceY, z, P.trimStairs, towardCentre(x, z, cx, cz), 'top')
  // the octagon's eight corners (where a flat face turns onto a diagonal) in white diorite: quoins that draw its facets
  for (const [x, z] of DRUM.vertices) for (let y = 1; y <= DRUM.wallTop; y++) c.set(x, y, z, P.trim)

  // the dome: red outside, a white vault over the hall, the eye on top
  const roof = P.roof
  const eye = [cx, cz]
  ringDome(c, { cx, cz, H: DRUM.dome, lined: r - 1 }, roof, P.lining)
  const cap = glazedLantern(c, eye[0], eye[1], DRUM.eyeY, { post: P.trim, glass: P.glass, cap: roof, finial: 'lightning_rod' })
  c.clear(cx, DRUM.eyeY - 1, cz)
  for (let y = DRUM.eyeY - 1; y >= DRUM.domeY; y--) if (c.get(cx, y, cz)) c.clear(cx, y, cz) // the eye: open down into the hall

  // Pass 2: openings. The shop front: the door, glass either side of it and a pane over it, white piers at its ends
  const fd = OPENINGS.frontDoor
  door(c, fd.at, 'south', 'north', { wood: P.wood, step: fd.step, stepStair: P.skirt })
  c.set(fd.at[0], 3, fd.at[2], P.glass)
  for (const [x, z] of OPENINGS.shopFront) for (let y = 1; y <= 3; y++) c.set(x, y, z, P.glass)
  for (const [x, y0, z, out] of OPENINGS.windows) window(c, [[x, y0, z], [x, y0 + 1, z]], out, 'sill', { glass: P.glass, sill: P.trimStairs })

  // Pass 3: depth. The canopy over the door: two white piers on the skirt, the red band carried out over them as a
  // fascia, the cornice's lip on it, lanterns under it either side of the door
  for (const x of [3, 9]) { c.set(x, 0, 0, stone); for (let y = 1; y <= 3; y++) c.set(x, y, 0, P.trim) }
  for (let x = 3; x <= 9; x++) c.set(x, DRUM.bandY, 0, P.band)
  for (const x of [4, 8]) c.set(x, 3, 0, 'lantern', { hanging: 'true', waterlogged: 'false' })

  // Pass 4: interior. The desk with the machine in it and the PC at its end, then the plan's furniture
  furnish(c, FURNITURE.filter(f => f.level === 1 && f.part !== 'hangingLamp'), P)
  healingMachine(c, FIXED.machine, 'north')
  pcTower(c, FIXED.pc, 'west')
  for (const f of FURNITURE.filter(f => f.level === 1 && f.part === 'hangingLamp')) furnitureParts.hangingLamp(c, { ...f, chain: cap[1] - 1 - f.pos[1] })
  return { inside, area }
}

// --- Level II: the Trade Hall and the garden --------------------------------------------------------------------------
function levelTwo(c) {
  const stone = mix(P.stone, 4), floorMix = mix(P.floor, 6)
  const W = { x0: 12, x1: 18, z0: 2, z1: 10 }
  const empty = (x, y, z) => !c.get(x, y, z)

  // Pass 1: the wing's skirt, floor, walls, band; its 45-degree red gable roof (ridge along z, gable to the street)
  for (let x = W.x0 - 1; x <= W.x1 + 1; x++) for (let z = W.z0 - 1; z <= W.z1 + 1; z++) {
    const edge = x === W.x1 + 1 || z === W.z0 - 1 || z === W.z1 + 1
    if (!empty(x, 0, z)) continue
    if (edge) stair(c, x, 0, z, P.skirt, x === W.x1 + 1 ? 'west' : z === W.z0 - 1 ? 'south' : 'north')
    else c.set(x, 0, z, x <= W.x0 || x === W.x1 || z === W.z0 || z === W.z1 ? stone : floorMix)
  }
  const ring = (y, id) => {
    wallPanel(c, [W.x0, W.z0], [W.x1, W.z0], y, y, id); wallPanel(c, [W.x0, W.z1], [W.x1, W.z1], y, y, id)
    wallPanel(c, [W.x0, W.z0], [W.x0, W.z1], y, y, id); wallPanel(c, [W.x1, W.z0], [W.x1, W.z1], y, y, id)
  }
  for (let y = 1; y <= 3; y++) ring(y, P.wall)
  ring(4, P.band)
  for (const [x, z] of [[W.x0, W.z0], [W.x1, W.z0], [W.x0, W.z1], [W.x1, W.z1]]) for (let y = 1; y <= 3; y++) c.set(x, y, z, P.trim)
  const roof = { axis: 'z', across: [W.x0 - 1, W.x1 + 1], along: [W.z0 - 1, W.z1 + 1], eaveY: 5, steep: false }
  const wr = gableRoof(c, roof, P.roof, empty)
  for (const line of [W.z0, W.z1]) gableWall(c, 'z', line, [W.x0, W.x1], 5, wr.under, (u, y) => (u === 15 && y === 6 ? P.glass : u === 15 && y === 7 ? P.trim : P.wall))
  for (const line of [W.z0 - 1, W.z1 + 1]) bargeTrim(c, 'z', line, roof.across, wr.under, P.trimStairs)
  // the cornice carried along the wing's eaves (the dome's white ring runs on round the wing)
  for (let z = W.z0; z <= W.z1; z++) c.set(W.x1, 5, z, P.trim)

  // Pass 2: openings. The wing's shop front (as the drum's), its side and back windows, its back door; the arch from
  // the lobby (the drum's east window opened to the floor); the Nurse's back door to the garden
  door(c, OPENINGS.wingDoor.at, 'south', 'north', { wood: P.wood, step: OPENINGS.wingDoor.step, stepStair: P.skirt })
  c.set(15, 3, W.z0, P.glass)
  for (const x of [13, 14, 16, 17]) for (let y = 1; y <= 3; y++) c.set(x, y, W.z0, P.glass)
  for (const z of [4, 8]) window(c, [[W.x1, 2, z], [W.x1, 3, z]], 'east', 'sill', { glass: P.glass, sill: P.trimStairs })
  for (const x of [13, 17]) window(c, [[x, 2, W.z1], [x, 3, W.z1]], 'south', 'sill', { glass: P.glass, sill: P.trimStairs })
  door(c, OPENINGS.wingBackDoor.at, 'north', 'south', { wood: P.wood, step: [15, 0, W.z1 + 1], stepStair: P.skirt })
  for (const [x, z] of OPENINGS.arch) { c.clear(x, 1, z); c.clear(x, 2, z); c.set(x, 3, z, P.trim) }
  c.set(11, 0, 6, stone); c.set(12, 0, 6, stone)
  const bd = OPENINGS.backDoor.at
  c.clear(bd[0], 3, bd[2]) // the window's upper pane goes with its sill: a door and a pane over it
  c.clear(bd[0], 1, bd[2] + 1)
  door(c, bd, 'north', 'south', { wood: P.wood, step: [bd[0], 0, bd[2] + 1], stepStair: P.skirt })
  c.set(bd[0], 3, bd[2], P.glass)

  // Pass 3: depth. Lanterns at the wing's doors under the roof's eave; a hanging lantern at the Nurse's door
  hood(c, 15, 4, W.z0 - 1, P.roof)
  hood(c, 15, 4, W.z1 + 1, P.roof)

  // Pass 4: interior: the Shop Counter between its two counters, the plan's furniture
  furnish(c, FURNITURE.filter(f => f.level === 2), P)
  shopCounter(c, FIXED.shopCounter, 'north')

  // Pass 5: the garden. A paved path from each back door, a birch fence round it with posts at every corner, flower
  // beds, the pavilion: a round stone dais under a little red dome on four white posts, the Pasture Block in it (a
  // canopy two out from the block at most: an open shrine, not a room the weather gets into)
  const [px, , pz] = FIXED.pasture
  const pave = mix(P.paving, 9)
  roundDais(c, px, pz, 8, 0, pave, P.skirt)
  for (const [dx, dz] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) for (let y = 1; y <= 3; y++) c.set(px + dx, y, pz + dz, P.post)
  ringDome(c, { cx: px, cz: pz, H: [6, 5, 4] }, P.roof)
  c.set(px, 4, pz, 'lantern', { hanging: 'true', waterlogged: 'false' })
  pastureBlock(c, FIXED.pasture, 'north')
  const walk = (cells) => { for (const [x, z] of cells) if (empty(x, 0, z)) slab(c, x, 0, z, 'stone_brick_slab') }
  const line = (a, b) => { const out = []; for (let x = Math.min(a[0], b[0]); x <= Math.max(a[0], b[0]); x++) for (let z = Math.min(a[1], b[1]); z <= Math.max(a[1], b[1]); z++) out.push([x, z]); return out }
  walk([...line([6, 13], [6, 16]), ...line([6, 16], [7, 16]), ...line([15, 12], [15, 16]), ...line([12, 16], [15, 16]), ...line([9, 22], [9, 24])])
  railingRun(c, [[0, 0, 12], [0, 0, 25], [19, 0, 25], [19, 0, 12]], null, P.fence)
  for (const x of [8, 9, 10]) c.clear(x, 0, 25) // the garden's gate to the fields
  flowerBed(c, 1, 4, 24, ['poppy', 'oxeye_daisy', 'red_tulip', 'white_tulip'])
  flowerBed(c, 14, 18, 24, ['white_tulip', 'poppy', 'azure_bluet', 'red_tulip'])
  flowerBed(c, 1, 3, 14, ['allium', 'poppy'])
  for (const [x, z] of [[1, 18], [18, 18]]) lampPost(c, x, 0, z, P.fence, 2)
  // two benches facing the pavilion
  for (const [x, z, facing] of [[4, 19, 'east'], [14, 19, 'west']]) for (const dz of [-1, 0, 1]) {
    stair(c, x, 0, z + dz, `${P.wood}_stairs`, facing === 'east' ? 'west' : 'east')
    c.spot('bench', [x, 0, z + dz], facing, [[0, 0, 0]], P.wood)
  }
}

export function draw(level = 1) {
  const c = new Canvas(SIZE[level])
  levelOne(c)
  if (level >= 2) levelTwo(c)
  return c
}

export default function build(api) {
  const level = Number(process.env.LEVEL ?? 1)
  const id = level === 1 ? 'pokemon_center' : `pokemon_center_${level}`
  const c = draw(level)
  const spots = c.spots.map(s => ({ ...s, cells: s.cells.map(([x, y, z]) => [x - s.pos[0], y - s.pos[1], z - s.pos[2]]) }))
  fs.writeFileSync(path.join(here, 'furniture.json'), '{"spots": [\n' + spots.map(s => '  ' + JSON.stringify(s)).join(',\n') + '\n]}\n')
  const cut = process.env.CUT
  const canvas = cut !== undefined ? c.cutAbove(Number(cut)) : c
  return { structure: canvas.toStructure(api), metadata: { name: cut !== undefined ? `${id}_cut${cut}` : id } }
}
