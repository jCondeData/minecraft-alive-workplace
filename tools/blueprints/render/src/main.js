import { mat4, vec3 } from 'gl-matrix'
import { NbtFile, Structure, ThreeStructureRenderer, loadDefaultPackResources } from '@mattzh72/lodestone'
import './style.css'

const originalWarn = console.warn.bind(console)
console.warn = (...args) => {
  if (args.length === 1 && String(args[0]).includes('does not exist')) return
  originalWarn(...args)
}

const DAY = {
  direction: vec3.fromValues(0.35, 0.85, 0.25),
  intensity: 1.15,
  ambientIntensity: 0.62,
  fillIntensity: 0.32,
  rimIntensity: 0.35,
  exposure: 1.08,
  sky: { zenithColor: [0.24, 0.48, 0.84], horizonColor: [0.78, 0.9, 1.0], groundColor: [0.18, 0.2, 0.22], stars: { enabled: false } },
  fog: { color: [0.78, 0.86, 0.94], density: 0.00012, heightFalloff: 0.001 },
}

function param(name, fallback) {
  return new URLSearchParams(window.location.search).get(name) ?? fallback
}

async function loadStructure(url) {
  const response = await fetch(url)
  if (!response.ok) throw new Error(`Failed to fetch ${url}: ${response.status}`)
  return Structure.fromNbt(NbtFile.read(new Uint8Array(await response.arrayBuffer())).root)
}

// The build on a lawn: PAD blocks of grass round it, one layer under its floor.
const PAD = Number(param('pad', 4))

function onLawn(build) {
  const [w, h, d] = build.getSize()
  const out = new Structure([w + PAD * 2, h + 1, d + PAD * 2])
  for (let x = 0; x < w + PAD * 2; x++) {
    for (let z = 0; z < d + PAD * 2; z++) {
      out.addBlock([x, 0, z], 'minecraft:grass_block', { snowy: 'false' })
    }
  }
  for (const b of build.getBlocks()) {
    const name = b.state.getName().toString()
    if (name === 'minecraft:air' || name === 'minecraft:structure_void' || name === 'minecraft:jigsaw') continue
    out.addBlock([b.pos[0] + PAD, b.pos[1] + 1, b.pos[2] + PAD], name, { ...b.state.getProperties() }, b.nbt)
  }
  return out
}

async function main() {
  const canvas = document.getElementById('preview')
  document.querySelector('.hud')?.remove()
  document.querySelector('.viewer-actions')?.remove()
  const build = await loadStructure(param('nbt', ''))
  const structure = onLawn(build)
  const pack = await loadDefaultPackResources({ baseUrl: new URL('/pack/', window.location.href).toString() })
  const size = structure.getSize()
  const renderer = new ThreeStructureRenderer(canvas, structure, pack.resources, {
    chunkSize: 16, drawDistance: 1000, useInvisibleBlockBuffer: false, asyncBuild: true, asyncChunkBuildTimeMs: 50, sunlight: DAY,
  })
  const rect = canvas.getBoundingClientRect()
  renderer.setViewport(0, 0, rect.width, rect.height, 1)
  await renderer.whenReady()

  // yaw 0 looks at the front (z = 0 side, north) from the front; pitch looks down.
  const center = vec3.fromValues(size[0] / 2, Math.max(1, size[1] / 2.6), size[2] / 2)
  window.view = (yawDeg, pitchDeg, dist) => {
    const view = mat4.create()
    mat4.translate(view, view, [0, 0, -dist])
    mat4.rotateX(view, view, pitchDeg * Math.PI / 180)
    mat4.rotateY(view, view, (yawDeg + 180) * Math.PI / 180)
    mat4.translate(view, view, [-center[0], -center[1], -center[2]])
    const inverse = mat4.create()
    mat4.invert(inverse, view)
    renderer.lookAt(vec3.fromValues(inverse[12], inverse[13], inverse[14]), center)
    renderer.drawStructure()
  }
  window.defaultDist = Math.max(16, Math.max(size[0], size[1], size[2]) * 1.0)
  window.view(Number(param('yaw', 30)), Number(param('pitch', 22)), Number(param('dist', window.defaultDist)))
  document.getElementById('loading').classList.add('hidden')
  document.title = 'READY'
}

main().catch(error => {
  console.error(error)
  document.getElementById('loading').textContent = error.message
  document.title = 'ERROR ' + error.message
})
