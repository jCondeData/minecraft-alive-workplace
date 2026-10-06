# Code layout

Moved out of CLAUDE.md (2026-09-29) to keep that file short. Update this map when you add or move a package.

## Packages under (`src/main/java/io/github/jcondedata/aliveworkplace/`)
- `registry/` — blocks, items, data components, attachments, profession/POI/schedule, gamerules, trades
- `blueprint/` — `Blueprint` (format-independent model), `BlueprintLibrary` (backed by the vanilla
  structure template manager), `BlueprintItem`, `BlueprintOutline` (particle preview), `StarterBlueprints`,
  `BlueprintUpgrades` (`<name>_2` upgrades `<name>`; finished builds are remembered in `BuildSiteManager`;
  `build/Upkeep` repairs them when blocks go missing),
  `ScanToolItem` (survival capture: two corners → `scans/<player>/<name>`), mirroring via `BlueprintData.mirrored` (style screen),
  `BlueprintStyles` (styles from `data/*/blueprint_styles/*.json`: a styled blueprint is the id
  `aliveworkplace:styled/<style>/<ns>/<path>`, which `BlueprintLibrary` resolves by swapping the base's blocks),
  `StylePicker` (sneak-right-click the air with a blueprint)
- `build/` — the builder: `BuildPlan` (ordered steps per stage), `BuildSite` + `BuildSiteManager`
  (per-dimension saved data), `BuilderWork` (the villager Behavior that does the work), `Builders`
  (hand-over, status, finish, cancel), `MaterialRules` (block → item cost, stage, "is this done"),
  `SupplyContainers` (chests near the bench, through `platform/ItemStores`: Fabric's transfer API), `BuilderPackages`, `BuilderEvents`
- `build/Paths`, `build/PathWork` — the dirt path a builder lays from a finished building to the bell or Village Hall
- `mine/` — the miner: `QuarryMarkerItem`/`QuarryData`, `QuarrySite` + `QuarrySiteManager`, `MinerWork`, `Miners`
- `farm/` — the farmer upgrade (vanilla Farmers): `FieldMarkerItem`/`FieldData`, `FieldJob` (attachment), `FieldWork`,
  `FarmerPackages` (our work first, vanilla's routine wrapped in `work/Gated`), `Fields`
- `guard/` — guards: `VillageRaids` (monster raids on hall villages at night; `raidArea` widens where guards fight),
  `Gates` (the fence gates of finished Gatehouses/Palisade Gates shut at night; the builds are in `defence.py`),
  `GuardCombat` (in their CORE package, any activity), `GuardRally` (answering the bell), `GuardPatrol` (WORK: gear up, patrol),
  `Guards` (who is a foe, damage, extra health), `Mercenaries` (hired at the hall till dawn); `VillagerPanicTriggerMixin` keeps them from panicking
- `shop/` — player shops: `ShopCounterBlock`/`ShopCounterBlockEntity` (price list, sales log), `Shops` (offers from stock, sales,
  the CobbleDollars shop screen), `ShopLedger` (CobbleDollars owed to offline owners);
  mixins on `Villager.mobInteract` (refresh offers) and `AbstractVillager.notifyTrade` (move the goods and payment)
- `travel/` — travel posts and ferrymen: `TravelNetwork` (saved data), `TravelPostBlock`, `TravelTicketItem`, `Ferrymen`
- `trainer/` — Pokémon trainers: `Trainers` (tiers, prizes, XP); the battles live in `compat/cobblemon/CobblemonTrainers`
- `tutor/` — Move Tutors: `Tutors` (grades, prices, XP); lessons and the screen live in `compat/cobblemon/CobblemonTutors`
- `trader/` — Pokémon Traders: `PokemonTraders` (one trade a day, XP); offers and the swap live in `compat/cobblemon/CobblemonTraders`
- `bard/` — bards: `BardWork` (discs from the chests, or a made-up tune)
- `nurse/` — nurses: `Nurses` (treating players), `NurseWork` (healing villagers nearby)
- `platform/` — what the mod needs from its loader (`Platform`, `Attachment`, `ItemStores`); `platform/fabric/` is the
  Fabric side and the only place (with `compat/` and `mixin/`) that may use Fabric API
- `mc/` — version adapters, one small static method per Minecraft behaviour that changes between versions (see Layers)
- `compat/Compat` — turns on the integrations that are installed (`init`, with a tested version range each; a failure
  leaves that integration off); the rest of the mod reaches them only through extension points (`work/Extension`:
  `PokemonPartners`, `Bank`, `orchard/PokemonFruit`, `fossil/FossilLab`, `ranch/DaycareDesk`, `trainer/TrainerBattles`,
  `trader/PokemonTrades`, `tutor/MoveLessons`, `nurse/PokemonHealing`), which give a fallback when nothing fills them
  and turn themselves off on a `LinkageError`
- `compat/cobblemon/` — the only code that touches Cobblemon classes (`CobblemonCompat` fills the extension points);
  trainers battle through `VillagerTrainerActor` (entity-backed: Pokémon sent out beside the villager), `CobblemonMegas`
  (Mega Stones and the Mega-Evolving AI, with Mega Showdown by item id)
- `compat/cobbledollars/` — the only code touching CobbleDollars (balances); `CobbleDollarsCompat` fills `work/Bank`, used by `work/Money` (CobbleDollars or emeralds)
- `compat/rct/` — Radical Cobblemon Trainers' level cap, by reflection (no dependency at all)
- `mail/` — mailboxes and postmen: `MailboxBlock`/`MailboxBlockEntity`/`MailboxMenu` (screen in client `MailboxScreen`),
  `PostOffice` (saved data: addresses, parcels, desks, dawn delivery), `Parcel`, `Mail` (send packet), `PostmanWork`
- `smelt/` — the smelter upgrade (vanilla Armorers, through `UpgradedJob`): `SmelterWork` (tend the blast furnace, fetch ore
  and fuel from the village, iron armor for the guards), `Smelters` (hiring with coal, what they keep)
- `mend/` — `MendingWork`: vanilla Weaponsmiths mending worn gear (with `craft/WeaponsmithWork`: swords for guards)
- `ranch/` — animals around a workstation: `RanchWork` (collect drops, breed up to a cap, the job's own tending),
  `ShepherdWork` (vanilla Shepherds: shearing, incl. pastured Pokémon), `HerderWork` (vanilla Butchers: milk, eggs, culling when hired),
  `RancherWork` (the Rancher at a Feed Trough: taming, saddling, armoring and breeding horses; grooming pastured Pokémon)
- `scribe/` — `EnchantWork`: vanilla Librarians with an Enchanting Table enchanting the workers' gear (books for builders: `craft/ScribeWork`)
- `flower/` — the florist (Flower Stand block): `FloristWork` (bone meal on the garden, picking, filling flower pots)
- `bee/` — the beekeeper (Apiary block): `BeekeeperWork` (harvest full hives with bottles or shears, plant flowers, breed bees)
- `hall/` — the Village Hall: `VillageHallBlock`/`VillageHallBlockEntity` (the village's name), `VillageHalls` (census of
  everyone within `RADIUS`, nearest hall by POI), `VillageHallScreen` (a `ChoiceMenu`: numbers, then every villager),
  `VillageNeeds` (meals from the store, beds, safety → wellbeing → the work pace in `BuilderLevels.delay`),
  `VillageGrowth` (a baby a day at most with a free bed, food and wellbeing), `VillageQuests` (quests for players, kept
  in the hall's block entity; the hall screen's quests page), `VillageRanks` (Hamlet to City, and the perks each rank gives), `MarketDays` (weekly traders at a finished Market Square), `Caravans` (the saved list of every hall in a dimension,
  trade routes, goods on the road), `Chronicle` (what happened, kept in the hall; `Chronicle.record(level, pos, kind, text)` writes to the nearest hall),
  `Decorations` (finished decoration blueprints near the
  hall → beauty → wellbeing; the builds are `StarterBlueprints.DECORATIONS`, drawn in `tools/blueprints/decor.py`),
  `HallPages` (the hall screen's page row, below), `Seasons` (the village calendar, below)
- **Adding a page to the Village Hall** (ROADMAP 22.5; every expansion that gives the hall a page does this): one call at
  start-up, `HallPages.register(id, tab, header, content)`. `tab` and `header` return the icon for the page row and for
  the top of the page (`(level, hall) -> ItemStack`, a named icon with lore); `content` fills the page's rows from
  `VillageHallScreen.FIRST_ROW` (slot 18) to slot 53 (`(menu, level, hall, viewer) -> menu.button(slot, icon, action)`).
  The hall draws the tab in the third row (`VillageHallScreen.PAGE_ROW`, slots 18-26, in registration order, up to
  `HallPages.MAX` = 9), and the page with a back button (slot 0), the header (slot 4) and a divider. The calendar is the
  first page (`Seasons.init`). Test a page with `VillageHallScreen.forTest` and `menu.press(HallPages.slot(id), player)`
  (see `HallPagesGameTests`); the hall's screenshot scene shows the row.
- **The village calendar** (ROADMAP 22.6): `Seasons` — four seasons of `Seasons.DAYS` days (config `seasonDays`,
  default 16), the same for the whole world (the overworld's day), each with a festival on its middle day.
  `Seasons.today(level)` is the date; expansions listen with `Seasons.onNewDay`, `onNewSeason` and `onFestival` (each
  fires once per day, saved, so a reload doesn't repeat it). Nothing in the mod changes with the seasons on its own.
- `people/` — villagers as people: `Names` (first names for villagers in a hall's village, given in
  `VillageNeeds.check`), `Traits` (one or two per villager from the UUID; read by `BuilderLevels`, `Walker`, `Guards`,
  `VillageNeeds`; off in gametests unless a test turns them on), `Sickness` (falling ill in the hall's round, half pace;
  cured by `nurse/NurseWork` with a remedy), `Families` (parents on babies; grown children take up the family trade),
  `Moods` (each villager's mood from their day, in `BuilderLevels.delay`), `Diet` (the last meals; variety lifts moods)
- `research/` — the Scholar (Scholar's Desk): `Research` (the tree, kept in the Village Hall; bonuses read by
  `VillageNeeds`, `Guards`, `Partners`, `Schools`), `ScholarWork`, `ResearchScreen`; `research/*` blueprints are hidden
  from the Blueprint Table (`BlueprintLibrary.isWorldgenPiece`)
- `grave/` — graves and the Undertaker: `GraveBlock`/`GraveBlockEntity` (the villager's NBT), `Graves` (left on death,
  revival), `UndertakerWork`
- `inn/` — the Innkeeper (Inn Counter): `InnkeeperWork` (a traveller each morning), `Innkeepers` (arrivals, the hire
  screen, departures), `Traveller` (attachment); hired travellers start at their level through `Schools.headStart`
- `school/` — the Teacher (Teacher's Desk): `TeacherWork` (calls the children in, lessons), `Schools` (schooled
  children start their first job as Apprentices, through `VillagerMixin` on `setVillagerData`)
- `explore/` — `ExplorerWork`: vanilla Cartographers on expeditions (food and a weapon from the chests, finds from the
  `explorer/*` loot tables, the Cobblemon one behind a `fabric:load_conditions`), `Explorers` (food/weapon rules, maps to
  places in the `explorer_maps` structure tag)
- `brew/` — `AlchemistWork`: vanilla Clerics brewing healing/regeneration/strength for the guards (`Guards.drink`)
- `sift/` — the Sifter (the Sieve): `SifterWork` (what comes out is the `sifting/<block>` loot tables)
- `fish/` — the fisher upgrade (vanilla Fishermen, hired with a fishing rod): `FisherWork`, `Fishers`
- `wood/` — the lumberjack: `Trees` (what counts as a natural tree), `LumberjackWork`, `LumberjackPackages`
- `store/` — the porter: `StorehouseBlock`/`StorehouseBlockEntity` (owner), `Porters` (what each job keeps, owner sync),
  `PorterWork` (haul goods from village-mates' chests to the storehouse), `PorterPackages`, `StorehouseBoard` (the
  requests board: the Storehouse's right-click screen), `DropBoxBlock` (porters empty it into the store), `StockOrders`
  (keep N of X in the store: crafters fill them in `CrafterWork.chooseOrder`)
- `craft/` — carpenters, masons and chefs: `Crafting` (plans from the game's recipes, two steps down; `KITCHEN` adds the
  smoker's and Cobblemon's Campfire Pot recipes by type id), `CrafterWork` (fetch, craft, deliver for a waiting builder;
  vanilla Masons run `MasonWork`: stonecutting plus crushing and glass), `ChefWork`/`Chefs` (cook the menu into the stove's chests), `TinkererWork` (Tinkerers at the Tinker's Bench: redstone/iron parts in tag `aliveworkplace:tinkering`,
  raw ore fired first with `Crafting.Kind.WORKSHOP`; iron golems mended between jobs), `ToolsmithWork` (vanilla Toolsmiths: tools for the
  village's tool requests), `FletcherWork` (vanilla Fletchers: bows and spectral arrows for guards), `DyerWork` (vanilla
  Leatherworkers: coloured things and concrete for builders), `CarpenterPackages`
- `compost/` — Composters (the Compost Bin): `CompostWork` (scraps → bone meal)
- `nether/` — Netherworkers (the Nether Brazier): `Netherworkers` (the trip: away in the portal — invisible, brain paused
  by `VillagerMixin.customServerAiStep`, no damage — then back with loot by kit), `NetherworkerWork` (pack, walk to
  the portal, unpack); `Builders.lightPortals` lights empty frames in a finished build
- `fossil/` — Fossil Scientists (with Cobblemon): `Revival` (saved on the villager), `FossilScientists` (hand-over, payment,
  delivery), `FossilWork`; Cobblemon's fossil data in `compat/cobblemon/CobblemonFossils`
- `smith/` — the ball smith: `BallRecipes` (Cobblemon ball recipes by tag and tier), `BallSmithWork`, `BallSmithPackages`
- `orchard/` — the orchard keeper: `Fruit` (what's ripe, picking it), `OrchardWork`, `OrchardPackages`; Cobblemon apricorns and
  berry plants in `compat/cobblemon/CobblemonOrchard`
- `story/` — the quest engine (M31, 31.2): `QuestFiles` (loader of `data/<ns>/quests/<group>/<id>.json`), `Objectives` and
  `Rewards` (one small record per type, read from and saved as its JSON), `Quest` (an open quest: a snapshot of its file with
  progress, helpers, posted and due), `Stories` (the `aliveworkplace_stories` saved data per dimension, keyed by hall; the
  board's morning post, hand-ins, kills, battles, waits; moves the hall's old `quests` in). `hall/VillageQuests` passes its
  public calls on to it. Its conditions are in `rules/` (`food_below`, `cobblemon`, `chance`, `quest_done`, `not`)
- `work/` — shared by all jobs: `Village` (workers near each other share chests; off in gametests unless a test turns
  it on with `Leftovers.village(helper, 48)`, which turns it off again when the test ends), `Requests` (what workers are waiting for: the board, lumberjacks' wanted wood), `Walker` (movement + reach), `WorkerStatus` (overhead status for jobs without a saved site), `Jobs.employ`,
  `ChoiceMenu` (a server-side chest screen of buttons: menus without client code), `DeskPackages` (WORK for jobs players visit),
  `Partners` (pastured Pokémon speeding up a job; the lookup is `compat/cobblemon/CobblemonPartners`), `Pastures` (a Pasture Block as a courier stop),
  `Gated`/`UpgradedJob` (vanilla jobs with extra work), `Hiring` (sneak-right-click a vanilla upgrade with its item), `PrivateContainer` (never a supply chest), `KeepLoaded` (chunk tickets)
- `camp/` — the Settler's Wagon (`SettlersWagonItem.makeCamp`: places `camp/settlers_camp` at once, two settlers, the
  first employed at the bench)
- `world/` — our houses in village generation (`VillageHouses`: builder's workshops, guard houses, clinics, post offices;
  with Cobblemon trainer's houses, leader's halls, schools, trade halls; the NBT comes from `tools/blueprints/generate.py`,
  which must keep exactly one job block per house — a vanilla one would give the villager the wrong job)
- `mixin/` — swaps in the builder/miner WORK packages and schedule for our professions; accessors
- `command/` — `/workplace`
- `WorkplaceConfig` — `config/aliveworkplace.json` (radii, postman range, CobbleDollars per emerald); the tunable
  distances are non-final statics (`SupplyContainers.RADIUS`, `Guards.RADIUS`, …) that it sets at startup

## Classes not described above yet

Generated from the code at 0.137.0 plus the work of 2026-09-29 evening: classes the map above doesn't mention. When
you touch one, add a line for it to its package above, and remove it from this list.

- `(root)`: AliveWorkplace
- `bard/`: BardPackages
- `bee/`: BeekeeperPackages
- `block/`: BuildersBenchBlock
- `blueprint/`: BlueprintEntities, Blueprints, PreviewNetworking, ShapePlannerItem, Shapes, SupplyReport
- `blueprint/io/`: BlockStateReader, BlueprintFiles, BlueprintFormatException, BlueprintImporter, LitematicReader, SpongeSchematicReader
- `build/`: BlockEntityData, BlueprintSupplies, BuildEntities, BuilderBag, BuilderJob, BuilderStatusSync, ChiselingFamilies, Employer, Friends, MaterialFamilies, ModdedBlocks, UpgradeOffers
- `client/`: AliveWorkplaceClient, BlueprintPreviewRenderer, BlueprintTableScreen, BlueprintTooltip, BobberRenderer, BuilderStatusRenderer, GuardArmorLayer
- `client/mixin/`: VillagerModelMixin
- `command/`: Benchmark, WorkplaceCommand
- `compat/cobbledollars/`: CobbleDollarsBank
- `compat/cobblemon/`: CobblemonDaycare, CobblemonNurse
- `compat/rct/`: RctLevelCaps
- `fish/`: FishingBobber
- `flower/`: FloristPackages
- `fossil/`: FossilPackages
- `grave/`: UndertakerPackages
- `guard/`: BanditCamps, Cavalry, Escorts, GuardEscort, GuardPackages, GuardPartners, PatrolMapItem, RallyBannerItem, TrainingDummyBlock, Warding
- `hall/`: Festivals, Treasury, VillageAdvice, VillageLedgerItem, VillageMaps, VillageProtection
- `inn/`: InnkeeperPackages
- `mail/`: DeliveryNoteItem, PostmanPackages, Postmen, RouteData
- `mc/`: Boats, Chat, Damage, Ids, Interact, Lookup, Nbt, Players, Recipes, Reg, Rules, package-info
- `mine/`: MinerPackages
- `mixin/`: AbstractVillagerMixin, AxeItemAccessor, BrewingStandAccessor, EntityAccessor, ExplosionMixin, StructureTemplatePoolAccessor, VillagerAccessor, VillagerGoalPackagesMixin, VillagerSeatMixin
- `nurse/`: NursePackages
- `orchard/`: Orchards
- `people/`: Chatter, Couples, Homes
- `platform/fabric/`: AliveWorkplaceFabric, FabricAttachment, FabricItemStores, FabricPlatform
- `ranch/`: Daycare, PokemonChores, RancherPackages
- `registry/`: ModAttachments, ModBlocks, ModComponents, ModEntities, ModGameRules, ModItems, ModTrades, ModVillagers
- `research/`: ScholarPackages
- `school/`: TeacherPackages
- `shop/`: PriceTagItem, ShopkeeperPackages
- `sift/`: SifterPackages
- `smith/`: BallSmiths
- `store/`: DropBoxBlockEntity
- `table/`: BlueprintTableBlock, TablePayloads, TableServer
- `trainer/`: TrainerPackages
- `travel/`: FerryRides, FerrymanPackages, TicketData
- `wood/`: TreeFarms
- `work/`: AreaJobs, Furnaces
