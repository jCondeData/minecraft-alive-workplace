# Roadmap

Work top to bottom. **Builders are the top priority**, then the other work jobs, then trainers,
then Pokémon partners. Each item should land with gametests. Tick the box in the same commit that
finishes it, and add a line to `CHANGELOG.md`.

Owner decisions are recorded in **Design decisions** at the bottom. Don't change them without asking.

---

## Milestone 1 — Builders

### Done (0.1.0)
- [x] Builder profession + **Builder's Bench** workstation (jobless villagers claim it like vanilla job blocks)
- [x] **Blueprint** item: right-click the ground to place it (it faces you), sneak-right-click the ground to turn it, sneak-right-click the air to pick it back up; particle outline while held
- [x] Hand a placed blueprint to a builder → build site saved with the world
- [x] Builder AI: clears the site, fetches materials from any storage within 8 blocks of the bench (Fabric transfer API, so modded storage works), builds bottom-up, then does decorations (torches, doors, beds…), returns the blueprint
- [x] Handles doors/beds/tall plants, rotation, block-entity data (signs, banners) without copying chest contents
- [x] Waits for missing materials and tells the owner exactly what is missing; sneak-right-click a builder for status
- [x] Hops to a spot it can stand on when it can't walk somewhere (walled in, over a gap)
- [x] Picks up where it left off after night, panic, chunk unload or server restart
- [x] Starter blueprints: Starter Cottage, Market Stall, Lookout Tower — sold by builders (levels 1–3)
- [x] Gamerules `workplaceFreeMaterials`, `workplaceBuildDelay`; `/workplace` command (blueprints, sites, cancel)
- [x] Builders work a longer shift (1000–11000) and sleep at night

### Done (0.2.0)
- [x] **Blueprint Table** block + screen: browse the server's blueprint library, see size and materials, take a blueprint for a Blank Blueprint (paper + blue dye); taking the same blueprint again is how you copy one
- [x] **Import `.litematic` and `.schem`** (Sponge/WorldEdit v1–v3) and `.nbt` → stored as structure templates in `<world>/generated/aliveworkplace/structures/`; old block names upgraded by the data fixer; unknown modded blocks become air and are counted; `/workplace import` for a server folder
- [x] **Upload from the client** at the Blueprint Table (30 KB pieces, 8 MB limit, `workplaceAllowUploads` gamerule, ops always allowed)
- [x] **Materials list** on the Blueprint Table screen
- [x] Library only lists real blueprints (ours, uploads, Structure Block saves), not other mods' world-generation pieces

### Done (0.3.0)
- [x] **Foundations**: builds on uneven ground get the gap under their floor filled down to the ground (dirt under grass, the floor's own block when it is a plain block, else cobblestone); `workplaceFoundationDepth` gamerule (12, 0 = off)
- [x] **See-through preview**: holding a placed blueprint shows the building as translucent blocks; blocks already built disappear from it (the particle outline stays for the footprint and front edge)
- [x] **Status above the builder's head**: build name, percentage, progress bar and what they are doing / waiting for (server sends it once a second to nearby players)

### Next
- [x] Materials list in the blueprint tooltip (hold Shift)
- [x] "What's still missing" in the tooltip of a placed blueprint: every 2 s while it's in a player's inventory the
  server compares its plan (blocks already in place skipped, foundation included) with the chests by the nearest
  Builder's Bench within 48 blocks and stores the shortfall in a synced, unsaved component (`SupplyReport`); after
  hand-over the builder's own status and sneak-click list cover it
- [x] **Build queue**: hand a busy builder up to 5 more blueprints; they do them in order (queued sites reserve their spot, show in `/workplace sites` and can be cancelled)
- [x] **Crews**: idle builders with a bench within 48 blocks of a build help out (up to 3 per site): they work ahead of the lead builder on the same stage, fetch from the lead's chests, pass spare materials to each other, and go home when it's done (`workplaceBuildersHelp` gamerule)
- [x] **Deconstruct**: sneak-give a placed blueprint to a builder and they take that building down (decorations first, then top-down), returning exactly what each block cost to the supply chests; blocks that differ from the blueprint and anything that holds items are left standing
- [x] **Permissions**: a builder works for whoever hires it first (hands it a blueprint); only that player, their friends (`/workplace friend add|remove|list`) and ops can give it blueprints or cancel its builds; hired builders only help their employer's and friends' builds (`workplaceBuilderOwnership` gamerule)
- [x] **Builder levels**: 1 XP per 5 blocks placed + 10 per finished build, vanilla level thresholds; level-ups unlock the next trades; each level is faster (Master takes 40% of the base time per block)
- [x] **More blueprints**: Healing Center (level 4, holds a Cobblemon Healing Machine when Cobblemon is installed) and Supply Shop (level 5)
- [x] Ground levelling around the site: a LANDSCAPE stage after DECORATION digs natural ground (dirt/sand/stone/
  gravel/clay/terracotta/snow, grass and ferns; not trees, flowers, farmland, paths, block entities) out of a ring
  `workplaceLevelGround` blocks wide (default 2, 0 = off) from the floor up to 6 blocks, then fills holes at floor
  level with dirt up to 3 deep (never water); it skips holes rather than wait for dirt
- [x] Per-blueprint opt-out of levelling: right-click the air with a blueprint to switch it (`BlueprintData.levelGround`,
  default on, carried to `BuildSite` and saved as `no_level_ground`); shown in the tooltip
- [x] Potted plants (cost a flower pot + the plant)
- [x] **Building-mod support** (tested with the real mods in `src/compattest`): Chipped and Rechiseled variants can be built from the plain block (free conversions, like their workbench/chisel); Supplementaries blocks build correctly (way signs cost the fence and the sign, rope knots the rope and the fence), and no blueprint data can hand out items, mobs or locked safes (containers never get contents)
- [x] Fluids: still water/lava (any fluid with a bucket) is a DECORATION step costing its filled bucket; the empty
  bucket (the item's crafting remainder) goes back in the bag; flowing liquid is skipped; waterlogging is still stripped
- [x] Entities in templates: item frames, glow item frames, paintings and armor stands (not markers) are read from
  structure NBT, .litematic (region Entities, relative to the region Position) and .schem (relative to the minimum
  corner), cleaned of contents/UUID/invulnerability (`BlueprintEntities`), written back into imported templates, and
  put up in `Builders.finish` for their item from the chests, rotated like a structure template (`BuildEntities`);
  counted in materials and the "still missing" report
- [x] **Builder's workshop** in village generation (all 5 village types, weight 3 in the house pools ≈ every other village): a Builder's Bench, a supply chest (`aliveworkplace:chests/village_builders_workshop`, sometimes a starter blueprint) and a villager who takes the bench
- [x] Our houses in modded villages: the Cobbleverse pack (1.7.42) has **Repurposed Structures** (no Towns and Towers),
  so the workshop and staffed houses join its 11 overworld village house pools (`VillageHouses.MODDED_HOUSE_POOLS`,
  closest vanilla style each; not crimson/warped/ocean); it uses vanilla jigsaw names; compat-tested with RS 7.5.21
- [x] Config file (`config/aliveworkplace.json`, `WorkplaceConfig`): supply radius, max site distance, guard/lumberjack/
  orchard/fisher/partner radii, postman range, CobbleDollars per emerald; written with defaults, clamped, applied at
  startup (per-world tuning stays in the gamerules; the texts that say "within 8 blocks" still say 8)
- [x] Upgrades by name (`BlueprintUpgrades`: `<name>_2` upgrades `<name>`, `_3` upgrades `_2`…); builds finished by
  builders are remembered per dimension (`BuildSiteManager.finished`, forgotten when taken down); an upgrade clicked
  onto one snaps to its placement; the builder clears what changed and keeps what matches. Starter Cottage II ships
  (second storey)
- [x] Upgrades for every starter build (Market Stall II: a second stall with a Shop Counter; Lookout Tower II: a Guard
  Post, a bell and a pointed roof; Healing Center II: a ward with four beds; Supply Shop II: a storeroom and bedroom
  upstairs); a builder who finishes a build with an upgrade adds its blueprint to their trades (`UpgradeOffers`, 6–32
  emeralds by size) and tells the owner
- [x] Third tier that grows sideways: Starter Cottage III (a kitchen wing east of Cottage II with a roof terrace and a
  door onto it from the upper floor); gametest builds it over a finished Cottage II with a mound where the wing goes.
  Found and fixed on the way: clearing left the wall where a new door's top half goes, so the door was skipped
- [x] Third tiers for the other starter builds, each adding a job: Market Stall III (a storeroom shed), Lookout Tower III
  (a guardhouse: second Guard Post, two bunks), Healing Center III (a berry garden with a Fruit Basket), Supply Shop III
  (a post office annex with a Postal Desk). The gallery scene also shoots each build from behind (`31_*_back.png`)

## Milestone 2 — Other work jobs
- [x] **Miner** (Miner's Bench workstation, Quarry Marker item): digs the marked area out layer by layer with pickaxes from the chests (tool tier and durability count; waits for a new one), drops everything off in the chests near the bench, lights the pit with torches from the chests, leaves blocks touching lava/water, containers and anything too hard, never goes below 5 above the world floor; levels up like builders; trades coal/ores, sells markers, torches and pickaxes
- [x] Stairs out of the pit (`QuarrySite.isStep`): pits at least 3 × 3 × 3 keep one step per layer, each one along the
  wall from the one above and a block lower, spiralling down from the corner nearest the bench; loose steps (sand,
  gravel, torches) are dug and filled, gaps in the pit wall filled with cobblestone/stone from the bag (topped up
  from the chests); quarries started before this keep digging without stairs
- [x] Smelting helper (`work/Furnaces.tend`, at every drop-off and at the end): furnaces/blast furnaces within the supply radius
  of the bench get raw ores and ore blocks (`c:raw_materials`, `c:ores`, if that furnace has a recipe) a stack at a
  time and coal/charcoal (up to 16) from the chests; results go into the chests; other items in a furnace are left
  alone. Furnaces no longer count as supply containers (things could land in their input slot)
- [x] Strip mines (the marker's last depth choice, `QuarryData.STRIP_MINE`): 2-high tunnels along the longer side on
  every third row, joined by a cross tunnel at the end nearest the bench (`QuarrySite.isTunnel`); the rock between is
  kept except blocks in `c:ores`; no stairs
- [x] Strip mines up to 64 long (`QuarryData.MAX_TUNNEL`; 32 wide) and at set heights down a ladder shaft
  (`QuarryData.STRIP_LEVELS` 16 / -16 / -53 after "strip mine here" on the marker; `QuarrySite` phases SHAFT → PIT →
  LADDERS): the shaft comes down at the cross tunnel's corner on the bench's side, ladders on the wall across the
  tunnels; below each step made solid first (caves), water/lava beside it sealed with filler; ladders from the chests
  (waits, "needs ladders"); given up (tunnels still dug) if something undiggable is in the way
- [ ] Miner follow-ups: a lit shaft (torches on the wall opposite the ladders), a choice of height by typing it
- [x] **Lumberjack** (Chopping Block workstation): fells natural trees within 16 blocks of the block (only trees: at least 4 natural leaves, trunk on dirt; player-placed logs, builds and quarries are left alone), clears the leaves, replants a sapling of the same wood, keeps up to 16 saplings of each kind and stores the rest in the chests near the block; axes from those chests wear out (waits for a new one); levels up like builders; trades sticks/apples, sells logs, saplings and an iron axe
- [x] Tree farms (`TreeFarms`, a Field Marker given to a lumberjack, attachment `TREE_FARM`): saplings from the bag/chests
  planted on dirt in a grid 3 apart (dark oak: 2 × 2 squares 4 apart), trees in the farm felled even beyond the 16-block
  search; status and `/workplace cancel <uuid>`. 2 × 2 trunks already replant all four base spots (gametest with a dark
  oak). Huge nether fungi: stems on nylium with a wart-block cap (within 4 of the stem) count as trees, fungus replanted
- [x] Lumberjack follow-ups: mangroves (their roots count as ground; `Trees.replantSpots` finds a spot near the old
  trunk when it won't take a sapling, waterlogged propagules in the water over mud), azalea trees replanted as an
  azalea (their leaves give them away), cherry trees tested; bone meal from the chests for the farm's saplings and
  the ones they replanted when there's nothing to fell (16 at most per sapling); chopping from the lowest log anyone
  can stand in reach of, unreachable trees skipped until the next one falls. Workers may stand in shallow water and
  on waterlogged blocks (`Walker.canStand`)
- [ ] Lumberjack follow-ups: stripping logs on request, charcoal from the furnaces
- [x] **Farmer upgrade** (Field Marker item, any vanilla Farmer): give a farmer a marked field (up to 32×32, within 48 blocks of their composter) and they harvest ripe crops (any `CropBlock`, so modded crops too; nether wart, pumpkins/melons off a stem, sugar cane above the bottom block), plant the same crop straight back, sow empty farmland/soul sand with seeds from the chests near the composter (the crop next to it, else what there is most of), till bare dirt/grass with a hoe from the chests, and store the harvest in those chests; their vanilla routine is paused while the field needs work; longer shift like our workers; `/workplace cancel <farmer uuid>` (clickable in the status) stops it
- [x] Farmer: bone meal from the chests (task FERTILIZE, only when nothing else needs doing; crops, stems, cocoa, berry
  bushes, never grass; up to 16 kept in the bag); sweet berries, cocoa, glow berries (and Cobblemon fruit) in a field
  are picked with `orchard/Fruit` and left to grow again (villagers never stand in berry bushes: `Walker.canStand`)
- [x] Village farms: a blank Field Marker given to a farmer adopts the farm by their composter (`Fields.farmNear`: the
  farmland nearest it within 16, flood-filled across water channels, up to 32 × 32). Fully automatic adoption without a
  marker is left out on purpose: it would change every village's farmers (see Notes)
- [x] **Fisher** (any vanilla Fisherman): hand one a fishing rod and they fish the nearest still water within 16 blocks of their barrel (standing on the shore), reeling in the vanilla fishing loot (fish and junk, no treasure), storing every fifth catch in the barrel and chests next to it; rods wear out and spares come from those containers; vanilla routine paused while working; longer shift; `/workplace cancel <fisherman uuid>` stops it
- [x] Fishers smoke the catch: at every drop-off `work/Furnaces.tend` loads raw fish (`#fishes` with a recipe for that
  furnace type) into smokers/furnaces near the barrel, with coal/charcoal, and brings the cooked fish out
- [x] A bobber on the water (`fish/FishingBobber`, entity `aliveworkplace:fishing_bobber`, never saved): cast with each
  throw, dips a moment before the bite, gone on reeling in; the client draws the vanilla bobber picture and a line to
  the rod (`BobberRenderer`); `SCENE=fish` screenshots it
- [ ] Fisher follow-ups: fishing from boats
- [x] **Courier / hauler** (done by Postmen): a Delivery Note marks a source and a destination container (plus an
  optional list of items to carry; by default everything but tools, weapons and armor); a postman takes up to 4
  routes and runs them whenever there's no mail, carrying a bagful per trip and taking back what doesn't fit; a blank
  note ends all routes and returns the notes

## Milestone 2b — Village life (roles from MyNPCs, rebuilt as villager jobs)
The owner pointed at *My NPCs* (MIT): admin-configured NPCs with roles. We don't copy its approach (op-made NPCs,
setup screens); we take the roles that fit a friends' Cobblemon server and make them jobs villagers take at a
workstation, like the rest of the mod.
- [x] **Postman + Mailboxes** (their Mailman): a Mailbox belongs to whoever places it (their newest one is their
  address; owner, friends and ops can open it); its screen posts the outgoing row to a named player; a Postman
  (Postal Desk workstation) collects parcels from mailboxes within 64 blocks of the desk and delivers to recipients
  on the round; anything else is handed in and arrives at the next dawn (`PostOffice` saved data; the flag shows
  there's mail; comparators read it)
- [x] Letters (a "Letter:" line on the mailbox screen: the parcel gets a written book from the sender with it on the page;
  a letter can go on its own) and tracking (`/workplace mail`, `Mail.tracking`: each parcel to or from you and whether
  it's waiting for pickup, in a postman's bag or in the night mail; **[Track]** after posting)
- [x] Parcel lockers: right-clicking any Postal Desk hands a player every parcel handed in for them (`Mail.collectAtDesk`;
  IN_TRANSIT parcels, which for a player with no mailbox wait there for good); a dawn message tells them; tracking says
  "waiting at the post office"
- [x] **Guard** (Guard Post workstation): fights monsters (not creepers) within 24 blocks of the post at any hour
  (combat is in their CORE activity, and they never panic), with the best weapon and armor from the chests near the
  post; 40 health, heals between fights, +10% damage per level, XP per kill; night-watch schedule (patrol evening
  to mid-morning, sleep until early afternoon); never targets players, villagers, golems, animals, pets or Pokémon
- [x] Guards answer the village bell (`GuardRally`): a listener first in their CORE package takes vanilla's
  HEARD_BELL_TIME memory (so they never hide) and rallies them to the nearest bell (meeting POI within 40) for
  90 s; their combat centres on the bell meanwhile, patrol waits
- [x] Guards with bows: a bow from the chests goes in the off hand; creepers become foes (shot from 7+ blocks,
  backing away), fliers and anything farther than 4.5 blocks get arrows (no ammo needed, arrows can't be picked up),
  never with someone else near the line of fire; arrow kills count
- [x] Guards use crossbows (`Guards.isBow` takes any bow or crossbow; a crossbow outranks a bow, so the guard swaps;
  faster, straighter, critical bolts every 30 ticks)
- [x] Guard follow-ups: armor shown on the villager model (a render layer fitted to the villager: helmet raised to the taller head, shoulder pieces on the folded arms)
- [x] **Nurse** (Nurse Station workstation): right-click with an empty hand (sneak to trade) to get full health and
  harmful effects cleared, and with Cobblemon installed the whole party healed (not mid-battle); per-player
  cooldown of a minute, shorter as the nurse levels; at work they also heal hurt villagers and iron golems nearby
  (guards!); sells healing potions, honey, golden apples/carrots. Cobblemon is compile-only (`compat/cobblemon`),
  tested with the real Cobblemon 1.7.3 in `runCompatGameTest`
- [x] Nurse Station in the Healing Center blueprint; Shop Counter in the Supply Shop blueprint
- [x] ~~Healing Machine as a second Nurse workstation~~: not possible — Cobblemon already makes a villager at a Healing
  Machine its own Nurse (POI `cobblemon:nurse`), and a block can only be one kind of workstation. Our Nurse Station
  stays the Nurse's workstation (the Healing Center blueprint has both, so it gets both nurses)
- [x] **Shopkeeper** (their Item Trader): the Shop Counter (owned by whoever places it; owner, friends and ops open
  it) is a 9×2 price list — top slot what one sale hands over, below it the price (any item); the Shopkeeper's
  offers are rebuilt from the chests near the counter whenever someone talks to them (sold out = no stock), a sale
  takes the goods out of those chests and puts the payment in; workers never use counters or mailboxes as supply
  chests (`PrivateContainer`)
- [x] Shopkeeper: with CobbleDollars, right-click opens a shop screen (`ChoiceMenu`): emerald prices paid in
  CobbleDollars (×100) straight to the owner (`ShopLedger` holds them for offline owners until they join), other
  prices in items; sneak for the vanilla trade screen. Sales log (last 20) on the counter, shown on sneak-right-click
- [x] Price Tags (`PriceTagItem`, renamed in an anvil to a number): a column priced in CobbleDollars directly; without
  CobbleDollars (and on the vanilla trade screen) it costs the same in emeralds, rounded up. The shopkeeper's overhead
  already counts sales
- [x] Price Tags set without an anvil: right-click one for a `ChoiceMenu` of −1000/−100/−10/−1/+1/+10/+100/+1000
  buttons (`PriceTagItem.setPrice` writes the number as its name, like the anvil)
- [x] **Ferryman** (their Teleporter): Travel Posts (named in an anvil) form one network (`TravelNetwork` saved
  data); right-clicking a post, placing it or talking to its ferryman adds it to the posts you know; a Ferryman sells
  Travel Tickets to every other post you know for 1 emerald per 256 blocks (1–16; 8 across dimensions); a ticket
  works within 16 blocks of any post and lands you next to its post
- [x] Ferryman: with CobbleDollars, right-click shows destinations with fares in CobbleDollars (×100), two clicks buy the ticket
- [x] Ferry houses in village generation (weight 4, every village): the post joins the network when first used
  (right-click, or its ferryman), named from a list of village names seeded by its position
- [ ] Ferryman follow-ups: a boat ride animation
- [x] **Bard** (Music Stand workstation): a morning set (1000–3500) and an evening set (9000–12500) at the stand,
  playing the music discs from the chests nearby in turn (discs stay in the chest; "Now playing" like a jukebox) or,
  with none, a made-up pentatonic harp tune with a bass beat; sells note blocks, goat horns and a few discs
- [x] Keep worksites loaded while their employer is online (their Chunk Loader): builds and quarries whose owner is
  online keep the chunks of the site, the workstation and its chests, and the worker's own chunk loaded (expiring
  tickets renewed every 5 s; capped per job); gamerule `workplaceKeepWorkLoaded` (on)
- Cobblemon roles go to the Cobblemon milestones: Pokémon Trainer (Milestone 3), Move Tutor and Pokémon Trader (Milestone 4).
- Not planned: Banker, Item Giver, Dialogue editor, Puppet, Spawner, Follower/Companion — tools for admins building
  adventure maps rather than jobs for villagers.

## Milestone 3 — Trainers (Cobblemon; optional dependency)
- [x] Optional Cobblemon integration layer: `compat/cobblemon` is the only code touching Cobblemon classes, called only
  when it's loaded; Cobblemon 1.7.3 is compile-only and runs in `runCompatGameTest`
- [x] **Trainer** profession + **Training Post** workstation: right-click (empty hand) to battle; a real Cobblemon battle
  (`TrainerBattleActor` + `BattleRegistry.startBattle`, gen 9 singles) against the trainer's team
- [x] Trainer posts generate in villages: a **trainer's house** (Training Post, targets, a bed, a villager spawn) in all
  5 village types at weight 6, plus **guard houses** (3, with a chest of starting gear), **clinics** (2) and **post
  offices** (2) — same 7×8×8 shell as the builder's workshop (`staffed_house` in `tools/blueprints/generate.py`)
- [x] 5 tiers = villager level, Novice → Master: team 2/3/4/5/6 at lv 5–12 / 15–25 / 30–42 / 50–65 / 80–100; basic
  Pokémon for beginners, fully evolved at the top, never legendary/mythical/ultra beast/paradox; the team is fixed per
  trainer and tier (seeded by the villager); Novice uses Cobblemon's random AI, higher tiers `StrongBattleAI(tier)`
- [x] Trained teams: IVs 15+/25+/31 from Journeyman/Expert/Master; Expert and Master get 252 Atk-or-SpA/252 Spe/4 HP
  EVs, Adamant or Modest, and a held item from a list for that side (`CobblemonTrainers.train`, its own seed so the
  species don't change); RCT capping now lowers the level of the same Pokémon instead of re-creating it
- [x] Master movesets (`CobblemonTrainers.pickMoves`): four attacks on the trained side (physical for Adamant, special
  for Modest) from level-up/TM/tutor/egg moves, power × accuracy, STAB ×1.5, repeated types halved; charge/recharge/
  self-KO/situational moves left out
- [ ] Master extras: Mega Evolution if Mega Showdown allows
- [x] Trainers **level up when you battle them** (+5 XP a battle, +3 more when they win; vanilla level thresholds);
  levels are the villager's, so shared server-wide
- [x] Rewards: **CobbleDollars** (100/250/500/1000/2500 by tier, via `/cobbledollars give`), once per in-game day per
  player per trainer; emeralds when CobbleDollars isn't installed; **no badges, no gyms**
- [x] **Trainer Leader** (Leader's Podium: gold, a Training Post and polished andesite): battles at Expert strength from
  the start (Master once they reach level 5), pays 3× a trainer's prize, one challenge per player per in-game day;
  if a village has several, only the most experienced within 64 blocks takes challenges; no badge
- [x] ~~RCT API~~: Cobblemon's own `TrainerBattleActor` battles for any villager, no RCT needed
- [x] Leader's halls in village generation (weight 5, so most villages grow one — the owner wants a leader per
  village; several are fine, the most experienced takes challenges); Pokémon houses only when Cobblemon is installed
- [x] Tune levels against the pack's RCT level caps: RCT 0.18.1 (in the pack) exposes `LevelUtils.levelCap(Player)`;
  read by reflection (`compat/rct/RctLevelCaps`, no dependency), a tier's ceiling is cap −6/−3/0/+3/+5; Pokémon above
  it are re-created at the ceiling (same species). Not compat-tested with RCT itself (it spawns trainers around test
  players); the scaling is tested with a given cap, and the fallback without RCT

## Milestone 4 — Pokémon partners & Cobblemon jobs
- [x] **Pokémon partners**, the Cobblemon way: Pokémon in a Pasture Block within 16 blocks of a workstation help when
  their type suits the job (`work/Partners`: builder Fighting/Rock/Steel, miner Ground/Rock/Steel, lumberjack
  Grass/Bug/Fighting, orchard Grass/Bug/Flying, farmer Grass/Ground/Water, fisher Water/Ice, nurse
  Fairy/Normal/Psychic), −15% work time each, up to 3; shown overhead and in the status message. Pastures instead of
  a follow-a-villager Pokémon: no new UI, and the Pokémon stay safe in the owner's PC
- [x] Air mail: a Flying-type partner by the Postal Desk delivers a handed-in parcel for outside the round at once
  (instead of with the dawn mail)
- [x] Fire-type partners at the furnaces (`Furnaces.blaze`): each time a worker tends a furnace/smoker, each Fire-type
  Pokémon pastured near the workstation (up to 3) smelts 8 of the worker's goods in it at once, fuel-free, straight
  into the chests (only while there's room)
- [ ] Partner follow-ups: guards fighting beside their Pokémon
  Fighting/Dragon types
- [ ] Pokémon-themed blueprints that come with staff: Pokémon Center (Nurse), Poké Mart (Clerk, sells for CobbleDollars), Berry Farm, Fossil Lab
- [x] **Orchard Keeper** (Fruit Basket workstation): picks ripe sweet berries, glow berries, cocoa and, with Cobblemon,
  apricorns and berry plants within 16 blocks (plants stay and regrow; berry plants through Cobblemon's own harvest, so
  yields and mulch work as for players), stores the harvest in the chests by the basket; reaches 6 blocks up with a
  picking pole; `Walker` no longer stands workers in berry bushes, fire, powder snow or cobwebs. Trades fruit
  (apricorns, Cobblemon berries and apricorn seeds with Cobblemon)
- [x] Orchard houses in village generation (weight 2, every village: a Fruit Basket, a harvest chest, an indoor berry bed)
- [x] Cobblemon mints: a Farmer's field handles them (they're crops: harvested for leaves and replanted); compat-tested
- [x] Orchards (`Orchards`, a Field Marker given to an Orchard Keeper, attachment `ORCHARD`): seeds from the bag or
  chests (sweet berries; with Cobblemon berries and apricorn seeds) planted like a player would (`BlockItem.place`, so
  berry block entities are set up) in a grid 2 apart (apricorns 3), each kind where it can grow (berries need farmland);
  fruit in the orchard is picked beyond the 16-block search. Shared marker plumbing in `work/AreaJobs` (tree farms too)
- [x] Orchard keepers till for berries: with a hoe in hand (fetched from the chests) grass/dirt/path in the orchard is
  tilled into farmland for a Cobblemon berry (`Orchards.needsTilling`; berries keep their farmland from drying out)
- [ ] Orchard follow-ups: glow berries under ceilings
- [x] **Ball Smith** (Ball Workbench): makes Poké Balls from the apricorns and ball metals in the chests with
  Cobblemon's own crafting recipes (`smith/BallRecipes`: results in `#cobblemon:poke_balls` using a
  `tier_N_poke_ball_materials` metal; copper 1 … diamond 4, up to the smith's level; never the Master Ball), taking
  turns between kinds and stopping at 64 of a kind; Steel/Fire partners help
- [x] Ball workshops in village generation (weight 2, Cobblemon only: a Ball Workbench and a chest of copper and dye)
- [x] Ball Smith orders (`BallSmiths`, attachment `BALL_ORDERS`): sneak-right-click opens a `ChoiceMenu` of every ball
  kind; picked ones glow and are the only ones made; none picked = anything (as before)
- [x] Chef (Kitchen Stove, `craft/ChefWork` on the crafter's fetch-cook-deliver loop with `Crafting.Kind.KITCHEN`: smoker +
  crafting + Cobblemon's Campfire Pot recipes found by recipe type id, no Cobblemon classes): cooks `Chefs.menu` in turn
  from its chests and the village's storehouses, up to 16 of each, a batch of up to 8 (halving to what there's makings
  for); a kitchen in village generation; Fire/Normal partners
- [x] Fossil Scientist (Fossil Lab; `fossil/`): a fossil in hand (+ the other half in the off hand for Galar ones) and 8
  emeralds / 800 CD → a `Revival` in the villager's `FOSSIL_REVIVALS` attachment, worked off at the lab in WORK
  (`REVIVE_TICKS` 3600, shorter by level/partners), delivered to the owner's party/PC when done and they're online;
  Cobblemon's `Fossils` data and `PokemonProperties.create` (`compat/cobblemon/CobblemonFossils`), `FOSSIL_REVIVED`
  posted; a fossil lab in village generation (Cobblemon only)
- [x] Cobbleworkers compatibility (compat-tested with Cobbleworkers 2.0.5): courier routes can start or end at a
  Pasture Block (`work/Pastures`: every container within 8 blocks, where Cobbleworkers' Pokémon deposit); pastured
  Pokémon count as partners whether or not they work for Cobbleworkers. Cobbleworkers only deposits into chests,
  barrels and gilded chests, so mailboxes and shop counters are safe from it
- [x] **Move Tutor** (Tutor's Desk workstation): right-click (empty hand) opens a lesson screen (`work/ChoiceMenu`, a
  server-side six-row chest of buttons, no client screen needed): the party on top, the chosen Pokémon's tutor/TM/egg
  moves it can't use yet below, paged; click twice to pay emeralds and teach (into the moveset if there's room, else
  the benched moves it can swap in). Grade 1–5 by power (≤50/70/85/100/more; status 3; egg +1), price 3/6/10/16/24;
  a tutor teaches up to their level and gains 2 + grade XP a lesson
- [x] Move Tutor prices in CobbleDollars (100 per emerald: 300–2,400) through `work/Money` (CobbleDollars balance via
  `compat/cobbledollars/CobbleDollarsBank`, the only code touching it; falls back to emeralds without the mod or if
  its API changes); trainer prizes use it too; compat-tested with CobbleDollars 2.0.0 Beta-5.1 (the pack's version)
- [x] Tutors in village generation (a school, weight 2, Cobblemon only)
- [x] **Pokémon Trader** (Trade Board workstation): the day's offers (seeded by trader and day, the same for everyone):
  their Pokémon (young ones for beginners, fully evolved at the top; never legendary/mythical/ultra beast/paradox) for
  any of yours of a type they don't have, level ≥ theirs − 5; 1/2/2/3/3 offers at lv 5–15 / 15–25 / 25–35 / 35–50 /
  50–70, 1 in 10 shiny at Master; one trade per player per trader per in-game day; the Pokémon you give must be
  tradeable, its held item comes back; 6 XP a trade. Sneak for item trades (balls, candies, ability capsule)
- [x] Traders in village generation (a trade hall, weight 2, Cobblemon only)
- [x] Special requests: Expert/Master traders add a daily fourth offer that wants one evolution line by name (first
  forms that evolve, never legendary etc.), level ≥ tier minimum − 10, for a Pokémon at the top of their range,
  shiny 1 in 4

## Milestone 6 — The village as one (owner, 2026-09-28)
Villagers work as a unit: what one worker makes, the others can use. No particular building is required anywhere
(packs add their own villages): a "village" is workers whose workstations are near each other.
- [x] Village farmers take on the farm by their composter by themselves (`Fields.adoptOwnFarm`, every 200 ticks, gamerule
  `workplaceVillageFarms`) once there's a chest by the composter; the harvest goes to those chests, but they keep ~36
  food points on them (wheat baked into bread) that vanilla shares out, so the village still breeds. A player stopping
  it sets `NO_AUTO_FARM`; a player's own field (marker) delivers everything to the chests as before
- [x] Shared chests (`work/Village`): workers whose workstations are within `villageRadius` (48) share supply chests — same
  employer or a friend of the giver's owner; unhired village workers with each other. Builders fetch materials (and
  count them as not missing), miners pickaxes, lumberjacks axes, farmers seeds and hoes, fishers rods from a
  village-mate's chests when their own have none; output stays in the maker's chests. Off in gametests
  (`-Dfabric-api.gametest`); the village tests switch it on in batches of their own
- [x] **Porter** + **Storehouse** block (the porter's workstation; the chests around it are the village store): hauls
  surplus into the store and delivers what workers are missing; storehouse blueprints in many styles, any blueprint
  with the block counts; villages sometimes generate one
  - [x] Storehouse block (block entity with the placer as owner; a builder's build belongs to the build's owner; the
    porter's employer follows the storehouse), Porter job (`store/`), `PorterWork` hauling each job's goods
    (`Porters.keeps`: what the job needs stays) from village-mates' stashes worth ≥16 items; 9 stacks a trip +3 a level,
    +3 a Fighting/Normal partner. Double chests now listed once by `SupplyContainers.find`
  - [x] Storehouse blueprints (starter builds `storehouse`, `_2`, `_3`; the porter sells the first) and a storehouse house in
    every village style (weight 3, `village_storehouse` loot in one chest)
  - [x] Deliveries: not needed, workers fetch from the storehouse themselves (see Requests)
- [x] Requests: workers post what they're missing, producers do that first, a board at the storehouse shows it
  (`work/Requests`: builders' and miners' come from their saved site, the others post while they wait; `StorehouseBoard`
  is the Storehouse's right-click screen, a click moves the player's items into that worker's chests; lumberjacks fell
  wanted kinds first, `Requests.wantedLogs` maps planks/stairs/... to their log by name). Porter deliveries aren't needed:
  workers fetch from the storehouse themselves
- [x] Carpenter/Mason: turns logs and cobblestone into building blocks for the builders' requests (`craft/`: `Crafting`
  plans with the game's recipes, up to two steps down; `CrafterWork` serves any builder waiting nearby using only chests that
  builder can reach and only what its build doesn't still need (`Builders.remainingNeed`); Carpenter = new job and
  workstation, crafting-table recipes; vanilla Masons upgraded with stonecutter recipes via `UpgradedJob`; a carpenter's
  workshop in village generation)

## Milestone 5 — Release
- [ ] Publish on Modrinth and CurseForge as **Alive Workplace** (Minecraft brand rules: don't lead the name with "Minecraft")
- [x] Screenshots/GIFs through the README, and an at-a-glance table of every job (workstation, what goes in the chests,
  what to hand them); the README is the guide (how to build, how to import blueprints) rather than a separate wiki
- [x] Compat tests with the pack's (1.7.42) building mods — Handcrafted, Beautify, CobbleFurnies, Carved Wood, Moar
  Concrete (every block has a cost; a sample of each gets built) — and storage: Sophisticated Storage (fixed: counts
  used `Long.MAX_VALUE`, which it reads as 0), Tom's Simple Storage (network blocks skipped as supply chests).
  Lucky's Cozyhome can't be loaded in the Mojang-mapped dev environment (its `getItems()` clashes on remap), so it's
  untested; Chipped and Supplementaries aren't in the pack but stay tested
- [x] Boot test in the full Cobbleverse 1.7.42 pack (`tools/packtest/run.sh`: all 136 mods + this jar on a real
  Fabric server): starts clean, commands and templates work, a Repurposed Structures birch village generated with
  a trainer's house and a post office
- [x] Performance with many builders in the full pack (`PERF=true tools/packtest/run.sh`: `/workplace benchmark`, only
  registered with `-Daliveworkplace.benchmark=true`, fills 40 plots with 80 busy workers; `tick query` before/after and
  a JFR profile read by `tools/packtest/perf.py`). Found and fixed: pathfinding retried every tick after a failed path
  (54% of the server thread → 23%; `Walker.requestWalk` waits 20 ticks), lumberjacks' tree search and per-tick flood
  fill (7% → 1%). 80 workers: 2 ms → ~7 ms a tick; our own code ~12% of that
- [ ] A play session in the pack's client (owner)

---

## Design decisions (from the owner)
- Target **Fabric 1.21.1** to match the Cobbleverse pack. The mod is needed on both server and clients.
- **Builders are the priority.** Giving villagers jobs is the core of the mod.
- Trainers: normal villagers get a Trainer job at different levels; they **level up as you battle them** (like trading); range from really easy to extremely difficult; **no badges and no gym leaders**; each village gets a **Trainer Leader** that starts difficult and pays money only.
- Cobblemon integration must be optional.
- License: GPL-3.0-or-later (lets us adapt MineColonies code, which is GPL-3.0-or-later, with attribution).
- Builds from the internet come in as files (.litematic/.schem/.nbt); we don't scrape sites. Only ship our own original builds.
- Villagers act as a cohesive unit (Milestone 6). Automatic where possible; keyed on blocks, never on a particular
  structure, because packs add their own villages. Village farmers harvest into nearby chests.
- *My NPCs* does some of what we want "in a different way" the owner doesn't love: rebuild the roles that fit the pack
  as villager jobs (Milestone 2b), not as admin-configured NPCs.

## Notes / blocked
- (autonomous sessions: write anything you could not finish or need the owner to decide here)
