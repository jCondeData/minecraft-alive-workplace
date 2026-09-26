# Screenshots & timelapses (dev only)

`tools/screenshots/run.sh` renders the real game client on a virtual display (Xvfb + Mesa software
OpenGL), so an autonomous session can *see* the mod: textures, models, and builders at work.

What it does:
1. Generates a flat world `run/shots` with a dev server (first run only).
2. Launches `./gradlew runScreenshots` — the client with the dev-only harness in `src/devclient`
   (`ScreenshotHarness`). The harness stages three builders building the starter blueprints, saves
   `01_start`, `02_builder_closeup`, `frame_###` every 30 ticks, then `03_finished_wide`,
   `04_cottage`, `05_market_stall`, `06_lookout_tower`, and quits.
3. Stitches the frames into `run/screenshots/timelapse.gif`.

Edit `ScreenshotHarness#stage` to stage a different scene (e.g. a new job). The devclient source set
is never packaged into the mod jar.
