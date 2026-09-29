# Working on Alive Workplace

Fabric mod for **Minecraft 1.21.1** (Mojang mappings, Java 21, Fabric API 0.116.17). Villagers do
real jobs; the first and most important job is the **Builder**, who builds blueprints.
The owner (Jesse) does not write code: sessions are expected to work autonomously from
`ROADMAP.md`, keep the build green, and explain results in plain language.

## Session checklist
1. `git pull`, read `ROADMAP.md` (priorities + owner decisions) and the latest `CHANGELOG.md` entries.
2. Pick the **next unchecked roadmap item(s)** in order. Keep each change reviewable (one feature per commit/PR).
3. Implement with **gametests** for any behaviour (see `src/gametest`). Pure-logic checks can use `FabricGameTest.EMPTY_STRUCTURE`.
   In both test areas (`big_area`, `build_area`) helper **y = 1 is the floor**: put blocks and villagers at y = 2 (a villager spawned
   at y = 1 is inside the floor and suffocates within ~200 ticks; older compat tests that finish quickly still use y = 1).
   Tests in one batch run side by side 5 blocks apart, and entities that wander outside a test area survive into later
   batches at the same spot. A test that can be disturbed by neighbours (guards, long builds) gets `batch = "<its name>"`
   and calls `Leftovers.clear(helper)` first. Blocks built higher than the test area (`build_area` is 8 tall,
   `big_area` 18) are never cleared either: tall builds belong in `big_area`. To hunt a flaky test, a temporary `@GameTestGenerator` returning a dozen
   copies of it (each in its own batch) shows the failure rate in one run.
4. Run `./gradlew build` — this compiles, packages and runs every gametest on a headless server. **Never push a red build.**
5. Tick the roadmap box, add a `CHANGELOG.md` line under *Unreleased*, commit, push to `main`.
6. If blocked or a decision belongs to the owner, write it under *Notes / blocked* in `ROADMAP.md` and move on to the next item.

## Commands
- `./gradlew build` — compile + jar + gametests (CI runs exactly this)
- `./gradlew runGameTest` — only the gametests (~10 s of game time, ~1 min total)
- `./gradlew runCompatGameTest` — gametests in `src/compattest` with Chipped, Rechiseled, Supplementaries, Cobblemon, Repurposed Structures, CobbleDollars, Mega Showdown (+ Accessories, owo-lib), and the pack's
  Handcrafted, Beautify, CobbleFurnies, Carved Wood, Moar Concrete, Sophisticated Storage, Tom's Storage, and Cobbleworkers (not in the pack) (+ libraries)
  installed from Modrinth maven (`compatMods` in `build.gradle`; bundled jars are unpacked into `build/compat-nested`).
  Part of `build`. Add a mod from the pack here when adding support for it. Nested jars are unpacked recursively
  (Cobblemon → Fabric Language Kotlin → Kotlin libraries); owo-sentinel is skipped (it refuses to load next to owo-lib).
  `CompatTestSetup` fires Architectury's server-starting event for the game test server (Architectury only fires it
  for dedicated/integrated servers, and Mega Showdown sets up on it). The Kotlin Gradle plugin is applied only so Loom remaps
  Kotlin metadata in Cobblemon; without it Cobblemon crashes in dev with `ClassNotFoundException: net.minecraft.class_…`.
- `./gradlew genSources` — decompiled Minecraft sources for reading vanilla code; they land in
  `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-common-*/**/**-sources.jar` (unzip and grep)
- `python3 tools/blueprints/generate.py` — regenerates starter blueprints + test fixtures (needs `pip install nbtlib`)
- `python3 tools/textures/generate.py` — regenerates textures (Pillow)
- `tools/screenshots/run.sh` — renders the real client headless (Xvfb) and saves screenshots + a timelapse GIF
  of builders at work; use it to check anything visual and to show the owner progress.
  `SCENE=table` shows the Blueprint Table screens, `SCENE=preview` the ghost preview and the status above a builder,
  `SCENE=gallery` every starter blueprint (front and back), `SCENE=village WORKSHOP_WEIGHT=200` one village of each type with workshops (`HOUSE_WEIGHT=60` for the other houses),
  `SCENE=quarry` a miner digging out a block of stone, `SCENE=forest` a lumberjack felling and replanting four trees, `SCENE=orchard` an orchard keeper picking (adds Cobblemon for apricorns and berries), `SCENE=farm` a farmer working a field, `SCENE=fish` a fisherman with a bobber out, `SCENE=carpenter` a carpenter making a builder's woodwork, `SCENE=chef` a chef cooking, `SCENE=porter` a porter carrying a miner's goods to the storehouse (and the requests board), `SCENE=mail` the mailbox screen and a postman delivering, `SCENE=guard` a close-up of a guard in armor, then fighting three husks (`SCENE=guard_pokemon`: with a Machop and a Dratini from a pasture joining in), `SCENE=staff` every workstation with its villager (then `python3 tools/screenshots/make_gif.py`), `SCENE=missing` a placed blueprint's "still missing" tooltip, `SCENE=tutor` the Move Tutor's lesson screen, `SCENE=trader` a Pokémon Trader's offers `SCENE=shop` the CobbleDollars shop screen, `SCENE=battle` a player battling a Master trainer whose Ampharos Mega Evolves (adds Mega Showdown too: `-Pmega=true`; the scene picks the player's moves, so it is also the end-to-end check that trainer battles work — the game test server can't play a battle out) and `SCENE=smith` a Ball Smith and an Orchard Keeper at work (these and `guard_pokemon` add Cobblemon and CobbleDollars to the client: `-Pcobblemon=true`). `DEBUG=true` logs
  builder/miner decisions. Long scenes take >10 min: start run.sh in the background and poll.
  Never `pkill -f`/`pgrep -f` a pattern that also appears in your own command line (it kills your shell).
- `tools/packtest/run.sh` — boots a real Cobbleverse server (every pack mod, production Fabric) with the newest
  `build/libs` jar, generates a vanilla and a Repurposed Structures village and looks for our workstations. Needs
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

## Layout (`src/main/java/io/github/jcondedata/aliveworkplace/`)
- `registry/` — blocks, items, data components, attachments, profession/POI/schedule, gamerules, trades
- `blueprint/` — `Blueprint` (format-independent model), `BlueprintLibrary` (backed by the vanilla
  structure template manager), `BlueprintItem`, `BlueprintOutline` (particle preview), `StarterBlueprints`,
  `BlueprintUpgrades` (`<name>_2` upgrades `<name>`; finished builds are remembered in `BuildSiteManager`)
- `build/` — the builder: `BuildPlan` (ordered steps per stage), `BuildSite` + `BuildSiteManager`
  (per-dimension saved data), `BuilderWork` (the villager Behavior that does the work), `Builders`
  (hand-over, status, finish, cancel), `MaterialRules` (block → item cost, stage, "is this done"),
  `SupplyContainers` (chests near the bench via Fabric transfer API), `BuilderPackages`, `BuilderEvents`
- `mine/` — the miner: `QuarryMarkerItem`/`QuarryData`, `QuarrySite` + `QuarrySiteManager`, `MinerWork`, `Miners`
- `farm/` — the farmer upgrade (vanilla Farmers): `FieldMarkerItem`/`FieldData`, `FieldJob` (attachment), `FieldWork`,
  `FarmerPackages` (our work first, vanilla's routine wrapped in `work/Gated`), `Fields`
- `guard/` — guards: `GuardCombat` (in their CORE package, any activity), `GuardRally` (answering the bell), `GuardPatrol` (WORK: gear up, patrol),
  `Guards` (who is a foe, damage, extra health); `VillagerPanicTriggerMixin` keeps them from panicking
- `shop/` — player shops: `ShopCounterBlock`/`ShopCounterBlockEntity` (price list, sales log), `Shops` (offers from stock, sales,
  the CobbleDollars shop screen), `ShopLedger` (CobbleDollars owed to offline owners);
  mixins on `Villager.mobInteract` (refresh offers) and `AbstractVillager.notifyTrade` (move the goods and payment)
- `travel/` — travel posts and ferrymen: `TravelNetwork` (saved data), `TravelPostBlock`, `TravelTicketItem`, `Ferrymen`
- `trainer/` — Pokémon trainers: `Trainers` (tiers, prizes, XP); the battles live in `compat/cobblemon/CobblemonTrainers`
- `tutor/` — Move Tutors: `Tutors` (grades, prices, XP); lessons and the screen live in `compat/cobblemon/CobblemonTutors`
- `trader/` — Pokémon Traders: `PokemonTraders` (one trade a day, XP); offers and the swap live in `compat/cobblemon/CobblemonTraders`
- `bard/` — bards: `BardWork` (discs from the chests, or a made-up tune)
- `nurse/` — nurses: `Nurses` (treating players), `NurseWork` (healing villagers nearby)
- `compat/cobblemon/` — the only code that touches Cobblemon classes; call it only when `isModLoaded("cobblemon")`;
  trainers battle through `VillagerTrainerActor` (entity-backed: Pokémon sent out beside the villager), `CobblemonMegas`
  (Mega Stones and the Mega-Evolving AI, with Mega Showdown by item id)
- `compat/cobbledollars/` — the only code touching CobbleDollars (balances); use it through `work/Money` (CobbleDollars or emeralds)
- `compat/rct/` — Radical Cobblemon Trainers' level cap, by reflection (no dependency at all)
- `mail/` — mailboxes and postmen: `MailboxBlock`/`MailboxBlockEntity`/`MailboxMenu` (screen in client `MailboxScreen`),
  `PostOffice` (saved data: addresses, parcels, desks, dawn delivery), `Parcel`, `Mail` (send packet), `PostmanWork`
- `smelt/` — the smelter upgrade (vanilla Armorers, through `UpgradedJob`): `SmelterWork` (tend the blast furnace, fetch ore
  and fuel from the village, iron armor for the guards), `Smelters` (hiring with coal, what they keep)
- `mend/` — `MendingWork`: vanilla Weaponsmiths mending worn gear (with `craft/WeaponsmithWork`: swords for guards)
- `fish/` — the fisher upgrade (vanilla Fishermen, hired with a fishing rod): `FisherWork`, `Fishers`
- `wood/` — the lumberjack: `Trees` (what counts as a natural tree), `LumberjackWork`, `LumberjackPackages`
- `store/` — the porter: `StorehouseBlock`/`StorehouseBlockEntity` (owner), `Porters` (what each job keeps, owner sync),
  `PorterWork` (haul goods from village-mates' chests to the storehouse), `PorterPackages`, `StorehouseBoard` (the
  requests board: the Storehouse's right-click screen)
- `craft/` — carpenters, masons and chefs: `Crafting` (plans from the game's recipes, two steps down; `KITCHEN` adds the
  smoker's and Cobblemon's Campfire Pot recipes by type id), `CrafterWork` (fetch, craft, deliver for a waiting builder;
  also run for vanilla Masons through `UpgradedJob`), `ChefWork`/`Chefs` (cook the menu into the stove's chests), `ToolsmithWork` (vanilla Toolsmiths: tools for the
  village's tool requests), `FletcherWork` (vanilla Fletchers: bows and spectral arrows for guards), `CarpenterPackages`
- `fossil/` — Fossil Scientists (with Cobblemon): `Revival` (saved on the villager), `FossilScientists` (hand-over, payment,
  delivery), `FossilWork`; Cobblemon's fossil data in `compat/cobblemon/CobblemonFossils`
- `smith/` — the ball smith: `BallRecipes` (Cobblemon ball recipes by tag and tier), `BallSmithWork`, `BallSmithPackages`
- `orchard/` — the orchard keeper: `Fruit` (what's ripe, picking it), `OrchardWork`, `OrchardPackages`; Cobblemon apricorns and
  berry plants in `compat/cobblemon/CobblemonOrchard`
- `work/` — shared by all jobs: `Village` (workers near each other share chests; off in gametests unless a test turns
  it on), `Requests` (what workers are waiting for: the board, lumberjacks' wanted wood), `Walker` (movement + reach), `WorkerStatus` (overhead status for jobs without a saved site), `Jobs.employ`,
  `ChoiceMenu` (a server-side chest screen of buttons: menus without client code), `DeskPackages` (WORK for jobs players visit),
  `Partners` (pastured Pokémon speeding up a job; the lookup is `compat/cobblemon/CobblemonPartners`), `Pastures` (a Pasture Block as a courier stop),
  `Gated`/`UpgradedJob` (vanilla jobs with extra work), `Hiring` (sneak-right-click a vanilla upgrade with its item), `PrivateContainer` (never a supply chest), `KeepLoaded` (chunk tickets)
- `world/` — our houses in village generation (`VillageHouses`: builder's workshops, guard houses, clinics, post offices;
  with Cobblemon trainer's houses, leader's halls, schools, trade halls; the NBT comes from `tools/blueprints/generate.py`,
  which must keep exactly one job block per house — a vanilla one would give the villager the wrong job)
- `mixin/` — swaps in the builder/miner WORK packages and schedule for our professions; accessors
- `command/` — `/workplace`
- `WorkplaceConfig` — `config/aliveworkplace.json` (radii, postman range, CobbleDollars per emerald); the tunable
  distances are non-final statics (`SupplyContainers.RADIUS`, `Guards.RADIUS`, …) that it sets at startup

## How the builder works (keep these invariants)
- All progress lives in `BuildSite` (saved). `BuilderWork` must stay restartable at any tick.
- Stages: CLEAR (top-down) → FOUNDATION → STRUCTURE (bottom-up) → DECORATION (things that need support) → LANDSCAPE
  (natural ground around the build dug away / holes filled; never waits for materials) → DONE. FOUNDATION and
  LANDSCAPE lists depend on the terrain, so a site loaded mid-stage restarts that list (done steps are skipped).
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
