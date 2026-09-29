// Renders blueprint .nbt files to PNGs from a few angles (headless Chromium + Lodestone).
//   node shoot.mjs <out-dir> <file.nbt>... [--views front,back,top] [--size 900x600]
import { createServer } from 'vite'
import { createReadStream } from 'node:fs'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'

const here = path.dirname(fileURLToPath(import.meta.url))
const args = process.argv.slice(2)
const outDir = args.shift()
let views = ['front', 'back']
let size = [960, 640]
const files = []
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--views') views = args[++i].split(',')
  else if (args[i] === '--size') size = args[++i].split('x').map(Number)
  else files.push(path.resolve(args[i]))
}
const VIEWS = {
  front: [25, 12], back: [205, 12], left: [-65, 12], right: [115, 12], top: [45, 60], low: [15, 6], side: [90, 15],
}
const packRoot = path.join(here, 'node_modules/@mattzh72/lodestone/assets/default-pack')
const byName = new Map(files.map(f => [path.basename(f), f]))
const server = await createServer({
  root: here, appType: 'spa', clearScreen: false, logLevel: 'error',
  plugins: [{
    name: 'files',
    configureServer(s) {
      s.middlewares.use('/nbt/', (req, res) => {
        const f = byName.get(decodeURIComponent(req.url.replace(/^\/+/, '').split('?')[0]))
        if (!f) { res.statusCode = 404; res.end(); return }
        res.setHeader('Content-Type', 'application/octet-stream'); createReadStream(f).pipe(res)
      })
      s.middlewares.use('/pack/', async (req, res) => {
        const f = path.join(packRoot, decodeURIComponent(new URL(req.url, 'http://x').pathname))
        try { await fs.access(f); res.setHeader('Content-Type', f.endsWith('.png') ? 'image/png' : 'application/json'); createReadStream(f).pipe(res) }
        catch { res.statusCode = 404; res.end() }
      })
    },
  }],
  server: { host: '127.0.0.1', port: 0, fs: { strict: false } },
})
await server.listen()
const base = server.resolvedUrls.local[0]
await fs.mkdir(outDir, { recursive: true })
const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] })
const page = await browser.newPage({ viewport: { width: size[0], height: size[1] } })
page.on('pageerror', e => console.error('page error', e.message))
for (const f of files) {
  const name = path.basename(f, '.nbt')
  await page.goto(`${base}?nbt=/nbt/${encodeURIComponent(path.basename(f))}`)
  await page.waitForFunction(() => document.title === 'READY' || document.title.startsWith('ERROR'), null, { timeout: 180000 })
  if ((await page.title()).startsWith('ERROR')) { console.error(name, await page.title()); continue }
  for (const v of views) {
    const [yaw, pitch] = VIEWS[v] ?? v.split(':').map(Number)
    await page.evaluate(([y, p]) => window.view(y, p, window.defaultDist), [yaw, pitch])
    await page.waitForTimeout(150)
    const out = path.join(outDir, `${name}_${v.replace(':', '_')}.png`)
    await page.screenshot({ path: out })
    console.log(out)
  }
}
await browser.close()
await server.close()
