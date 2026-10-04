# Tools and commands

Moved out of CLAUDE.md (2026-09-29). The short list stays in CLAUDE.md; the details are here.

## Build files, outputs and Stonecutter

For mod work use the **minecraft-mod-engineer** skill (not the older minecraft-mod-dev). The build follows its
multi-version layout (ROADMAP, Milestone 19): **Stonecutter**, one Gradle node per Minecraft version under
`versions/<mc>/`, built from the one `src/`. Today there is one node, **1.21.1** (the Cobbleverse pack's, and the
`vcsVersion`: the checked-in sources are written for it); a 26.x node is on hold until the owner says go.
- Files: `settings.gradle.kts` (nodes), `stonecutter.gradle.kts` (constants, swaps), `stonecutter.properties.toml` (the
  mod's version, each node's Fabric API, optional mods, compat-test mods), `build.gradle.kts` (shared by every node),
  `gradle.properties` (Gradle options, `loomx.loom_version`).
- Outputs per node: jar `versions/1.21.1/build/libs/alive-workplace-<version>+1.21.1.jar`, test logs
  `versions/1.21.1/build/run/gameTest/logs/latest.log` and `.../run/compatGameTest/...`, dev runs in `versions/1.21.1/run`.
  Node tasks: `./gradlew :1.21.1:runClient` etc.; a bare `./gradlew build` builds every node.
- Optional mods: the whole of `compat/cobblemon/` and `compat/cobbledollars/` (and their lines in `compat/Compat`) sit
  inside `//? if cobblemon {` … `//?}` (constants from `deps.compat.<mod>` in the toml), so a node without the mod
  doesn't compile them. **Only `//` comments inside guarded code** (no `/* */` or `/** */`: commenting a block out
  would break). RCT is reflection and needs no guard.
- **Every `@GameTest` has a swap line above it**: `//$ gametest AREA`, `//$ gametest_ticks AREA '400'`,
  `//$ gametest_batch AREA '"name"'` or `//$ gametest_ticks_batch AREA '400' '"name"'` (arguments that aren't plain
  names in single quotes). On 1.21.1 the swap writes exactly the annotation below it; newer nodes get Fabric's.
- **Round trip before committing:** `./gradlew "Refresh active project"` must leave `git diff` unchanged (if not, a guard
  or swap line doesn't match), and the active project must be the vcsVersion (`./gradlew "Reset active project"`).

## Commands

- `./gradlew build` — every node: compile + `checkLayers` + jar + gametests (+ compat gametests on 1.21.1). CI runs exactly this
- `./gradlew runGameTest` — only the gametests (~10 s of game time, ~1 min total)
- `./gradlew runCompatGameTest` — gametests in `src/compattest` with Chipped, Rechiseled, Supplementaries, Cobblemon, Repurposed Structures, CobbleDollars, Mega Showdown (+ Accessories, owo-lib), and the pack's
  Handcrafted, Beautify, CobbleFurnies, Carved Wood, Moar Concrete, Sophisticated Storage, Tom's Storage, and Cobbleworkers (not in the pack) (+ libraries)
  installed from Modrinth maven (`tests.compat_mods` in the toml's 1.21.1 section; bundled jars are unpacked into
  `versions/1.21.1/build/compat-nested`).
  Part of `build`. Add a mod from the pack here when adding support for it. Nested jars are unpacked recursively
  (Cobblemon → Fabric Language Kotlin → Kotlin libraries); owo-sentinel is skipped (it refuses to load next to owo-lib).
  `CompatTestSetup` fires Architectury's server-starting event for the game test server (Architectury only fires it
  for dedicated/integrated servers, and Mega Showdown sets up on it). The Kotlin Gradle plugin is applied only so Loom remaps
  Kotlin metadata in Cobblemon; without it Cobblemon crashes in dev with `ClassNotFoundException: net.minecraft.class_…`.
- `./gradlew runCompatGameTest -Pcobblemon18=true` — the same suite with Cobblemon 1.8.1 instead of 1.7.3 (ROADMAP 28.2;
  the mod still compiles against 1.7.3). The swaps and any mod left out are `tests.cobblemon18` in
  `stonecutter.properties.toml`. `COBBLEMON18=true tools/screenshots/run.sh` films a Pokémon scene with 1.8.1, and a
  hand-started showcase run with the `cobblemon18` box ticked films every Pokémon scene with it (never published).
  The nightly runs the suite both ways (job `compat-cobblemon18`). 1.8-only features ask `work/PokemonFeatures`.
- `PERF_STACK=true SCENE=<name> tools/screenshots/run.sh` — the screenshot client with Cobbleverse's performance mods
  (Sodium, Lithium, C2ME, FerriteCore, ModernFix, EntityCulling, ImmediatelyFast, Krypton, ScalableLux; the pack's
  versions, `tests.screenshot_perf`), for ROADMAP 25.4. The server side already has them in `tools/packtest/run.sh`.
- `./gradlew :1.21.1:genSources` — decompiled Minecraft sources for reading vanilla code; they land in
  `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-common-*/**/**-sources.jar` (unzip and grep)
- `python3 tools/agent/sessions.py` — claims, landing, review packages, QA verification and roadmap bookkeeping when
  several sessions work at once (`docs/agent/sessions.md`); `python3 tools/agent/test_sessions.py` tests it on
  throwaway local repos.
- `python3 tools/agent/usage.py` — what a session's AI usage cost and where it went; `--log` records a run on the
  `usage` branch, `--report` sums the lanes' runs (cost per run, per lane, per item).
- `python3 tools/review/sheet.py` — puts screenshots on one labelled sheet for a review package (`docs/agent/review.md`).
- `python3 tools/blueprints/generate.py` — regenerates starter blueprints + test fixtures (needs `pip install nbtlib`).
  The kit (`Build`, roofs, windows, frames, texture mixes, `finish()` for stair corners and fence joins) is `kit.py`, the
  starter builds `starter.py`, the village houses `village.py`. **Read `tools/blueprints/STYLE.md` before drawing a build.**
- `tools/blueprints/render/preview.sh front,back starter_cottage "village_house('plains', kitchen)"` — renders builds to
  PNGs in seconds (Lodestone in headless Chromium; `npm install` in that folder first) into `build/blueprint-renders`
- `python3 tools/textures/generate.py [recipe…]` — draws every texture from its recipe in `tools/textures/art` (one
  module per set; the pixel-art skill's library is copied in `tools/textures/pxlib`, with `lint.py` and `preview.py`)
- `tools/screenshots/run.sh` — renders the real client headless (Xvfb) and saves screenshots + a timelapse GIF
  of builders at work into `versions/1.21.1/run/screenshots`; use it to check anything visual and to show the owner progress.
  `SCENE=table` shows the Blueprint Table screens, `SCENE=preview` the ghost preview and the status above a builder,
  `SCENE=gallery` every starter blueprint (front and back; `SCENE=workshops` the Tinker's Workshops and Nether Gates), `SCENE=decor` the decorations, `SCENE=defences` the walls and gates, `SCENE=camp` a Settler's Wagon's camp, `SCENE=styles` the Cottage II and Stone House II in every style, `SCENE=village WORKSHOP_WEIGHT=200` one village of each type with workshops (`HOUSE_WEIGHT=60` for the other houses),
  `SCENE=quarry` a miner digging out a block of stone, `SCENE=forest` a lumberjack felling and replanting four trees, `SCENE=orchard` an orchard keeper picking (adds Cobblemon for apricorns and berries), `SCENE=farm` a farmer working a field, `SCENE=fish` a fisherman with a bobber out, `SCENE=extras` a fisher out in a boat, a guard on horseback and a ferry ride (third person), `SCENE=carpenter` a carpenter making a builder's woodwork, `SCENE=chef` a chef cooking, `SCENE=porter` a porter carrying a miner's goods to the storehouse (and the requests board), `SCENE=hall` the Village Hall and its screen, `SCENE=mail` the mailbox screen and a postman delivering, `SCENE=guard` a close-up of a guard in armor, then fighting three husks and sparring with a Training Dummy (`SCENE=guard_pokemon`: with a Machop and a Dratini from a pasture joining in), `SCENE=staff` every workstation with its villager (then `python3 tools/screenshots/make_gif.py`), `SCENE=missing` a placed blueprint's "still missing" tooltip, `SCENE=tutor` the Move Tutor's lesson screen, `SCENE=trader` a Pokémon Trader's offers `SCENE=shop` the CobbleDollars shop screen, `SCENE=battle` a player battling a Master trainer whose Ampharos Mega Evolves (adds Mega Showdown too: `-Pmega=true`; the scene picks the player's moves, so it is also the end-to-end check that trainer battles work — the game test server can't play a battle out) and `SCENE=smith` a Ball Smith and an Orchard Keeper at work (these and `guard_pokemon` add Cobblemon and CobbleDollars to the client: `-Pcobblemon=true`). `DEBUG=true` logs
  builder/miner decisions. `GUI_SCALE=4` (or 3) films any scene at that GUI scale in a 1920x1080 window (24.4: screens at scales 2 and 4). Long scenes take >10 min: start run.sh in the background and poll.
  Never `pkill -f`/`pgrep -f` a pattern that also appears in your own command line (it kills your shell).
  More scenes, one per job or screen that had none (`src/devclient/.../JobScenes.java`): `beekeeper`, `florist`,
  `scholar` (and the research screen), `sifter`, `tinkerer`, `composter`, `netherworker`, `undertaker`, `innkeeper`
  (and the hire screen), `teacher`, `rancher`, `mason`, `dyer`, `nurse`, `smelter`, `toolsmith`, `weaponsmith`,
  `fletcher`, `shepherd`, `herder`, `alchemist`, `scribe`, `explorer`, `bard`, `dropbox` (the Drop Box and its screen),
  `fossil` (Cobblemon); screens: `shapes`, `style_menu` (with the Mirror button), `counter` (the Shop Counter's prices
  and the owner's sales log), `ferry_menu`, `scan` (the Scan Tool marks a hut and saves it), `config` (the settings
  screen Mod Menu opens; it puts the run's config file back afterwards), `words` (every new or reworded message in
  chat, a page at a time, with example values), and with
  Cobblemon `partners_engine` (a pastured Machop carries the builder's planks to the work and back), `partners_land` (a
  Machamp carries the builder's beams, a Wartortle waters the farmer's patch), `partners_forge` (a Pidgeotto takes
  the air mail up out of sight, a Charmander breathes fire into the blast furnace), `partners_all` (twelve more
  workers' partners at their work, one still each), `daycare`, `smith_orders`, `leader` (a Trainer Leader's challenge),
  `pokemon_center` (both tiers, then the nurse heals the team in her Healing Machine). `python3 tools/showcase/scenes.py list`
  lists them all.
- **The nightly showcase** (`.github/workflows/showcase.yml`, ROADMAP 22.4). Every night at about 10:40 PM Central,
  GitHub runs every scene in `tools/showcase/scenes.py` (the catalog: each scene's job, and the 2-4 stills the page
  shows with their labels) in parallel jobs of about half an hour, judges each, and publishes
  https://jcondedata.github.io/minecraft-alive-workplace/: a GIF from start to end and the stills per scene, grouped by
  job, with a PASS or FAIL. No Claude session is involved.
  - A scene passes only if it recorded at least one check and all of them passed (`Showcase.check` in the harness: the
    job visibly did its work), and no picture looks broken: villagers stuck in walls and text shown as a raw
    translation key (the harness's `Showcase` class watches for both), missing-texture magenta, blank or missing stills,
    a crash or running over time (`tools/showcase/process.py`). Missing textures or models in the client log count for
    the whole run.
  - Failures open or update the `nightly-tests` issue, with the command that reproduces each scene.
  - The harness writes `run/screenshots/showcase.json` and small GIF frames (`screenshots/gif/`) for every scene.
  - **A new scene:** stage it in `ScreenshotHarness` or, simpler, in `JobScenes` (a job scene: stage the set and say
    when the job is done; a screen scene: open a menu, then the slots to point at). Call `Showcase.check`, and add it to
    the catalog: `python3 tools/showcase/scenes.py check` (run by the workflow) fails if the two disagree.
  - **Try it locally:** `python3 tools/showcase/shard.py --scenes "sifter hall" --out build/showcase`, then
    `python3 tools/showcase/page.py --results build/showcase --site build/showcase-site` and open its `index.html`.
  - **Filming one scene on demand** (ROADMAP 22.8). A push that changes the harness, `tools/screenshots` or
    `tools/showcase`, on any branch including `main`, films only the scenes it added or changed
    (`python3 tools/showcase/scenes.py changed <before> HEAD` says which: lines inside a scene's own method in
    `ScreenshotHarness`, inside its `SCENES.put(…)` in `JobScenes`, a scene class of its own such as
    `PartnersLandScene.java` with the harness's field and dispatch for it, or its entry in the catalog). Shared code (a
    helper such as `pointAt`, `Showcase`, the tools, the workflow) films every scene. One scene takes about 15 minutes.
    So a new scene keeps to its own lines: its class (or method), its dispatch, its catalog entry with `cobblemon=True`
    if it needs Cobblemon (`run.sh` reads that from the catalog: no edit there). Anything it needs in `Showcase` or a
    shared helper goes in an earlier push of its own.
    To film scenes by hand: Actions › showcase › Run workflow, with `scenes` = `hall tutor` (empty: all).
  - **Reading the result:** open the run (Actions › showcase, the newest run for your commit; or the GitHub MCP tool
    `actions_list` with `list_workflow_runs` and `resource_id: showcase.yml`). The plan job's summary says which scenes it
    filmed (`Scenes: partners_forge`; `all` when shared code changed); the page job's summary has each scene's PASS or
    FAIL with the reasons (`get_job_logs` on the page job, or the run page in a browser); the `showcase-site` artifact is the
    page itself (the GIF and stills per scene). Only a full run on `main` (the nightly one, or a hand-started run with no
    `scenes`) publishes the page and files the `nightly-tests` issue.
  - The shards are balanced with the times in the published page's `showcase.json` (`scenes.py matrix`).
- `tools/packtest/run.sh` — boots a real Cobbleverse server (every pack mod, production Fabric) with the newest
  `versions/1.21.1/build/libs` jar (run it on the system's Java 21, not the JDK 25 Gradle uses), generates a vanilla and a Repurposed Structures village and looks for our workstations. Needs
  ~6 GB RAM, ~5 min; don't run it alongside a Gradle build (memory). `PERF=true PLOTS=40` is the performance mode:
  `/workplace benchmark` (registered only with `-Daliveworkplace.benchmark=true`) fills an area with busy workers,
  `tick query` gives tick times before/after, and `tools/packtest/perf.py` reads a JFR profile of the server thread
  (share in our code by job, villager pathfinding). Workers set walk targets through `Walker.requestWalk`, which waits
  after a failed path: re-asking every tick made pathfinding over half the server's time.
- Tests that grow trees with a vanilla feature pass a fixed `RandomSource` (see `LumberjackGameTests.shapes()`): with
  the level's random, a huge fungus grows twice as tall one time in twelve and CI failed on a shape nobody had seen.
- CI logs are readable without a token: `curl -sL https://api.github.com/repos/jCondeData/minecraft-alive-workplace/actions/jobs/<job id>/logs`
  (job ids from `.../actions/runs/<run id>/jobs`).
- If Maven Central answers **429**, wait ~20 s and retry; it is rate limiting, not a real failure.

