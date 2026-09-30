# Working on Alive Workplace

Fabric mod for **Minecraft 1.21.1** (Mojang mappings, Java 21, Fabric API 0.116.17). Villagers do real jobs; the
flagship is the **Builder**, who builds blueprints. The owner (Jesse) doesn't write code. Sessions work
autonomously from `ROADMAP.md`, keep `main` green, and explain results in plain language, with pictures.

The goal now is a polished public **1.0** (see ROADMAP.md): builders first, then visuals, then performance.

## Skills (use them; they hold the details this file leaves out)

- **minecraft-mod-engineer:** all mod code, builds, versions, compat, publishing (not the older minecraft-mod-dev).
- **minecraft-mod-tester:** independent testing. Every finished item goes through a tester subagent (below).
- **minecraft-pixel-art:** every texture, item, outfit and GUI sprite, drawn the way vanilla draws its kind (a book
  like vanilla's books, a tool like its tools). No art outside it. Recipes: `tools/textures/art`.
- **minecraft-architect:** every build and blueprint, checked against `tools/blueprints/STYLE.md` in a render.

## The session loop

Several sessions may work at once: the owner's chat and scheduled night runs. `tools/agent/sessions.py` keeps them
apart, and `docs/agent/sessions.md` explains how (read it once per session). Call yourself `chat`, or
`night-<MMDD>-<HHMM>` (UTC start time) in a scheduled run.

**Start:**
1. `git fetch`, then `python3 tools/agent/sessions.py status --as <you>`. Check the latest nightly test run and any
   open `nightly-tests` issue (how: `docs/agent/sessions.md`, "Health first"); anything red is a bug.
2. Read the top of ROADMAP.md ("How to work this file"), its Bugs, and its Notes with the latest handoffs.
3. Read `git log --oneline -15 origin/main` and the *Unreleased* part of CHANGELOG.md.
4. Set up Java once per container (below).
5. Run `./gradlew --max-workers=1 :1.21.1:runGameTest` on the newest `main`. If something already fails, add it with
   `sessions.py bug` and fix it first.

**Work:**
1. `sessions.py claim <id> --as <you>` for the item `status` named. You're now on the branch `item/<id>`. Push it at
   least every 30 minutes, with commit messages that say what's done and what's next: a session can be cut off at
   any time, and the next one continues from your branch.
2. Read the code you will change before changing it. Never guess at an API, a method name or a file you haven't
   opened. For Minecraft and Fabric APIs, use the engineer skill's `api.py` or `genSources`.
3. Build to the item's **Done when**. If it's unclear or impossible as written, run
   `sessions.py pause <id> --as <you> --blocked "owner: <question>" --note "<what's done>"`, ask in your next
   message, and take the next item. Don't invent the spec.
4. Write GameTests for the behaviour (conventions below). Tests serve the feature:
   - if a test seems wrong, say so in the Notes;
   - never special-case code just to pass a test;
   - never weaken or delete a test to get green.

**Finish each item:**
1. Run `./gradlew --max-workers=1 build`. It must be green.
2. Hand the change to a fresh tester subagent (Agent tool), using the handoff prompt from the minecraft-mod-tester
   skill's `references/automation.md`: tier Check, range = `origin/main..HEAD`, spec = the item's text and its Done
   when.
   - Fix what it finds and hand back, 3 rounds at most.
   - A failing bug test lands only together with its fix.
   - Still failing after 3 rounds: `sessions.py pause <id> --as <you> --blocked "tester: <why>" --note "<what's
     left>"`. The work stays on its branch; `main` never sees it.
3. If a player can see or feel the change, playtest it with the bot and make the review package
   (`docs/agent/review.md`), with a GIF of anything that moves. If not, there's no package: land with `--no-review`.
4. Add a CHANGELOG line under *Unreleased* and commit. Stage files by name, not with `git add -A`: stray files such
   as uncommitted bug tests must not ride along.
5. `sessions.py land <id> --as <you>`: it merges the newest `main` in, builds again, ticks the item
   `(review: pending)` and pushes to `main`. Run it in the background and poll it, since the build takes minutes
   (`docs/agent/sessions.md`). Then send the review package straight away.
6. Take the next item. Don't wait for the review.

**End of session**, or when the context gets long:
- Land what's green; `sessions.py pause` what isn't. Leave nothing unpushed.
- `sessions.py handoff "<in progress, next, traps>" --as <you>`.

The next session starts fresh from these files, so write down anything it needs to know. When compacting, keep the
list of modified files, the current item and its Done when, and the test commands.

## Reporting to the owner

- Show, don't tell: screenshots, GIFs and numbers from this session's tool results.
- Before sending a status, check each claim against a tool result from this session. Anything not run is "not
  tested", never "works".
- Keep it short: what changed from a player's point of view, what you want him to judge, and the tester's verdict
  and risks.
- He wants to be hands-off. He judges looks from screenshots and behaviour from GIFs; work with nothing to see is
  accepted without him (`land --no-review`) and only listed.
- Ask only for what is his to decide: design choices, anything destructive or public, and the release channel.
  Everything else: decide, write the decision in the Notes, and carry on.
- Before ending a turn, read your last paragraph. If it promises work ("next I'll…"), do that work now or put it in
  the Notes.

## Layers (enforced by `./gradlew checkLayers`, part of `build`)

- **Core** is everything outside `platform/fabric/`, `compat/` and `mixin/`, and follows the engineer skill's rules.
  Core may import only Minecraft, the JDK, `com.mojang`, Gson/Guava, JetBrains annotations, slf4j, JOML, fastutil and
  our own packages. Not `compat.*` or `platform.fabric.*`: that keeps the mod portable. The check also refuses
  Fabric's attachment methods.
- **The loader only through `platform/`.** `Platform.get()` covers:
  - events (`onServerTick`, `onUseEntity`, …);
  - packets;
  - reload listeners;
  - registration (POIs, trades, game rules, creative tabs);
  - menus;
  - `Attachment`: data saved on villagers, declared in `registry/ModAttachments`;
  - `ItemStores`: chests and modded storage.
- **Other mods only through `compat/`** and extension points (`work/Extension`), which fall back when nothing fills
  them. `Compat.init` fills them after `isModLoaded`.
- Cobblemon, RCT and CobbleDollars are optional: their classes live only in `compat/<mod>/`, they are
  `modCompileOnly` and listed in `suggests`, and they are never a hard `depends`. The mod must run without them.
- **Minecraft APIs that change between versions go through `mc/`** (`Chat`, `Nbt`, `Damage`, `Ids`, `Lookup`,
  `Recipes`, `Reg`, …). Version switches (`//?`) only go in `mc/` and `platform/`.
- Stonecutter: one node today (1.21.1, the `vcsVersion`). Every `@GameTest` has a `//$ gametest…` swap line above it.
  `./gradlew "Refresh active project"` must leave `git diff` unchanged before committing.
- Guarded code (`//? if cobblemon {`) uses only `//` comments: a `/* */` inside it breaks when Stonecutter comments the
  block out.

## Commands (details and every screenshot scene: `docs/agent/tools.md`)

Set up Java once per container:
`source <minecraft-mod-engineer skill>/scripts/setup_env.sh`. Then prefix Gradle commands with
`export JAVA_HOME=/root/.local/jdk-25 PATH=/root/.local/jdk-25/bin:$PATH;` (use the path setup_env prints).

This container has 7 GB of RAM:
- Always pass `--max-workers=1`.
- Only one Minecraft process at a time (a build, the pack test or the screenshot client).
- Never `pkill -f` a pattern that also appears in your own command line: it kills your shell.

Commands:
- `./gradlew build`: every node: compile, `checkLayers`, jar, GameTests, compat GameTests. CI runs exactly this.
- `./gradlew runGameTest`: only the GameTests, about 1.5 min.
- `./gradlew runCompatGameTest`: with the pack's optional mods.
- `tools/screenshots/run.sh` with `SCENE=<name>`: the real client under Xvfb, with screenshots and GIFs. This is
  the bot for review packages. Runs longer than 10 min go in the background; poll them.
- `tools/packtest/run.sh`: a real Cobbleverse server with the built jar, about 5 min, on Java 21.
  `PERF=true PLOTS=40` is the benchmark.
- Build tools:
  - `python3 tools/blueprints/generate.py`: blueprints (read `STYLE.md` first);
  - `tools/blueprints/render/preview.sh`: build renders;
  - `python3 tools/textures/generate.py [recipe…]`: draws every texture from its recipe in `tools/textures/art`
    (the pixel-art skill's library is copied in `tools/textures/pxlib`, with `lint.py` and `preview.py`).
- Tester scripts: `tools/modtest/` (`scope.py`, `inventory.py`, `langcheck.py`, `mutate.py`, `results.py`,
  `logaudit.py`), with `allow.txt` (log messages that are expected, each with its reason) and `baseline.json` (the
  suite's tests by name; only a Full run updates it).
- `.github/workflows/nightly.yml` runs the heavy checks on GitHub every night at about 10 PM Central (5 suite runs for
  flakes, the log audit, the pack boot and soak, screenshot scenes) and opens a `nightly-tests` issue when anything
  fails.
- If Maven Central answers **429**, wait 20 s and retry.

## GameTest conventions

- Test areas: `build_area` (8 tall), `big_area` (18 tall) and `huge_area` (30×30×30). Helper **y = 1 is the floor**:
  put blocks and villagers at y = 2, because a villager at y = 1 is inside the floor and suffocates within about 200
  ticks.
- Tests in one batch run side by side, 5 blocks apart.
  - Entities that wander outside a test area survive into later batches at the same spot.
  - A long or disturbable test gets `batch = "<its name>"` and calls `Leftovers.clear(helper)` first.
  - Blocks built above an area's height are never cleared, so tall builds go in `big_area` or `huge_area`.
- Pure-logic checks can use `FabricGameTest.EMPTY_STRUCTURE`.
- Randomness gets a fixed `RandomSource`. With the level's random, CI once failed on a tree shape nobody had seen.
- Register new test classes in `src/gametest/resources/fabric.mod.json`.
- To check a test isn't flaky, use the tester skill's repeat generator. On 1.21.1, `attempts` doesn't repeat tests
  in our headless runs.

## How the builder works (keep these invariants)

- All progress lives in `BuildSite` (saved). `BuilderWork` must stay restartable at any tick.
- Stages: CLEAR (top-down) → FOUNDATION → STRUCTURE (bottom-up) → DECORATION → LANDSCAPE (never waits for
  materials) → DONE.
  - FOUNDATION and LANDSCAPE depend on the terrain, so a site loaded mid-stage restarts that list; steps already done
    are skipped.
  - Steps that can't be done yet are deferred once, then skipped (counted in `skipped`).
- "Done" checks use `MaterialRules.matches`, which ignores properties that depend on neighbours.
- Blueprint conventions: the front is the template's z=0 side; y=0 sits on the top of the clicked block.
- Builders never break containers or the bench while clearing, and never copy container contents.

## Rules

- Player-visible text goes through `assets/aliveworkplace/lang/en_us.json`. Run `langcheck.py` when you add some.
- **Don't break existing saves.** The mod runs on the owner's live server: new saved fields need defaults, and
  registry ids are never renamed.
- Code from GPL-3.0(-or-later) projects such as MineColonies may be adapted **with attribution in the file header**.
  Never copy from All-Rights-Reserved mods.
- Support for other building mods goes by block/item/tag ids or their data files, never by their classes, and gets a
  compat test.
- Builds are original, or openly licensed and credited.
- Stay in scope. A bug you notice outside the item goes into ROADMAP "Bugs", not into this change.
- Commit messages: a short imperative subject, then what and why.

## Releasing

CI publishes, because pushing tags isn't allowed from the dev environment. Only the owner's chat releases, so two
sessions never bump the version. On a fresh `main` (`git switch main && git pull`), bump `mod.version` in
`stonecutter.properties.toml` and move the *Unreleased* notes in CHANGELOG.md under `## X.Y.Z — date` in the same
commit, build, and push (if the push is refused: pull, build, push). The first green build on `main` with a new
version tags `vX.Y.Z` and creates the GitHub Release (a pre-release while 0.x).
- **When:** when no item on `main` is pending review or vetoed, and something new has been accepted since the last
  release (ROADMAP). Or when the owner says `release`.
- **Before the bump:** the tester's Full tier.
- Bump the minor version for features, the patch version for fixes only.
- Store-page publishing (Modrinth, CurseForge) waits for the owner's release-channel decision (ROADMAP 26.1).
