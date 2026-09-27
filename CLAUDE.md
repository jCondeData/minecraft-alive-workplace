# Working on Alive Workplace

Fabric mod for **Minecraft 1.21.1** (Mojang mappings, Java 21, Fabric API 0.116.17). Villagers do
real jobs; the first and most important job is the **Builder**, who builds blueprints.
The owner (Jesse) does not write code: sessions are expected to work autonomously from
`ROADMAP.md`, keep the build green, and explain results in plain language.

## Session checklist
1. `git pull`, read `ROADMAP.md` (priorities + owner decisions) and the latest `CHANGELOG.md` entries.
2. Pick the **next unchecked roadmap item(s)** in order. Keep each change reviewable (one feature per commit/PR).
3. Implement with **gametests** for any behaviour (see `src/gametest`). Pure-logic checks can use `FabricGameTest.EMPTY_STRUCTURE`.
4. Run `./gradlew build` — this compiles, packages and runs every gametest on a headless server. **Never push a red build.**
5. Tick the roadmap box, add a `CHANGELOG.md` line under *Unreleased*, commit, push to `main`.
6. If blocked or a decision belongs to the owner, write it under *Notes / blocked* in `ROADMAP.md` and move on to the next item.

## Commands
- `./gradlew build` — compile + jar + gametests (CI runs exactly this)
- `./gradlew runGameTest` — only the gametests (~10 s of game time, ~1 min total)
- `./gradlew runCompatGameTest` — gametests in `src/compattest` with Chipped, Rechiseled, Supplementaries (+ libraries)
  installed from Modrinth maven (`compatMods` in `build.gradle`; bundled jars are unpacked into `build/compat-nested`).
  Part of `build`. Add a mod from the pack here when adding support for it.
- `./gradlew genSources` — decompiled Minecraft sources for reading vanilla code; they land in
  `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-common-*/**/**-sources.jar` (unzip and grep)
- `python3 tools/blueprints/generate.py` — regenerates starter blueprints + test fixtures (needs `pip install nbtlib`)
- `python3 tools/textures/generate.py` — regenerates textures (Pillow)
- `tools/screenshots/run.sh` — renders the real client headless (Xvfb) and saves screenshots + a timelapse GIF
  of builders at work; use it to check anything visual and to show the owner progress.
  `SCENE=table` shows the Blueprint Table screens, `SCENE=preview` the ghost preview and the status above a builder,
  `SCENE=gallery` every starter blueprint, `SCENE=village WORKSHOP_WEIGHT=200` one village of each type with workshops,
  `SCENE=quarry` a miner digging out a block of stone, `SCENE=forest` a lumberjack felling and replanting four trees (then `python3 tools/screenshots/make_gif.py`). `DEBUG=true` logs
  builder/miner decisions. Long scenes take >10 min: start run.sh in the background and poll.
  Never `pkill -f`/`pgrep -f` a pattern that also appears in your own command line (it kills your shell).
- If Maven Central answers **429**, wait ~20 s and retry; it is rate limiting, not a real failure.

## Layout (`src/main/java/io/github/jcondedata/aliveworkplace/`)
- `registry/` — blocks, items, data components, attachments, profession/POI/schedule, gamerules, trades
- `blueprint/` — `Blueprint` (format-independent model), `BlueprintLibrary` (backed by the vanilla
  structure template manager), `BlueprintItem`, `BlueprintOutline` (particle preview), `StarterBlueprints`
- `build/` — the builder: `BuildPlan` (ordered steps per stage), `BuildSite` + `BuildSiteManager`
  (per-dimension saved data), `BuilderWork` (the villager Behavior that does the work), `Builders`
  (hand-over, status, finish, cancel), `MaterialRules` (block → item cost, stage, "is this done"),
  `SupplyContainers` (chests near the bench via Fabric transfer API), `BuilderPackages`, `BuilderEvents`
- `mine/` — the miner: `QuarryMarkerItem`/`QuarryData`, `QuarrySite` + `QuarrySiteManager`, `MinerWork`, `Miners`
- `wood/` — the lumberjack: `Trees` (what counts as a natural tree), `LumberjackWork`, `LumberjackPackages`
- `work/` — shared by all jobs: `Walker` (movement + reach), `WorkerStatus` (overhead status for jobs without a saved site), `Jobs.employ`
- `world/` — village builder's workshops (`VillageHouses`)
- `mixin/` — swaps in the builder/miner WORK packages and schedule for our professions; accessors
- `command/` — `/workplace`

## How the builder works (keep these invariants)
- All progress lives in `BuildSite` (saved). `BuilderWork` must stay restartable at any tick.
- Stages: CLEAR (top-down) → STRUCTURE (bottom-up) → DECORATION (things that need support) → DONE.
  Steps that can't be done yet are deferred once, then skipped (counted in `skipped`).
- "Done" checks use `MaterialRules.matches`, which ignores neighbour-dependent properties.
- Blueprint conventions: front is the template's z=0 side; y=0 sits on the clicked block's top.
- Builders never break containers or the bench while clearing, and never copy container contents.

## Rules
- Player-visible text goes through `assets/aliveworkplace/lang/en_us.json`.
- Art is original (draw it in `tools/textures/generate.py`); starter builds are original.
- Code from GPL-3.0(-or-later) projects such as MineColonies may be adapted **with attribution in the file header**.
  Don't copy code from All-Rights-Reserved mods.
- Support for other building mods goes by block/item/tag ids or their data files (`ModdedBlocks`, `MaterialFamilies`),
  never by their classes, and gets a compat test.
- Cobblemon/RCT/CobbleDollars support must be **optional**: put it in a separate package loaded only when
  `FabricLoader.isModLoaded("cobblemon")`, add them as `modCompileOnly`, never a hard `depends`.
- Don't break existing saves: new saved fields need defaults; don't rename registry ids.
- Commit messages: short imperative subject, then what/why.

## Releasing
Pushing tags is not allowed from the dev environment, so CI does it: bump `mod_version` in
`gradle.properties` and move the *Unreleased* notes in `CHANGELOG.md` under a `## X.Y.Z — date` heading
in the same commit. The first green build on `main` with a new version creates tag `vX.Y.Z` and a
GitHub Release with the jar (pre-release while 0.x). Release after each session that adds something
players can try (bump the minor version: 0.2.0, 0.3.0, …; patch for fixes only).
